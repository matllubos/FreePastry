/*************************************************************************

"Free Pastry" Peer-to-Peer Application Development Substrate 

Copyright 2002, Rice University. All rights reserved. 

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/

package rice.scribe;

import java.util.*;
import rice.pastry.*;
import rice.scribe.maintenance.*;

/**
 * @(#) Topic.java
 *
 * Data structure used by a node to represent a topic
 *
 * @version $Id$
 *
 * @author Romer Gil
 * @author Eric Engineer
 * @author Atul Singh
 * @author Animesh Nandi
 */

public class Topic
{
    /* ------------------------------------
     * MEMBER VARIABLES
     * ------------------------------------*/
    
    /** 
     * This topic's identifier
     */
    protected NodeId m_topicId = null;

    /** 
     * Set of NodeHandle objects for the local
     * node's children in this topic's multicast subtree
     * rooted at the local node.
     */
    protected Set m_children = new HashSet();
    
    /** 
     * Local node's parent in this topic's multicast
     * tree
     */
    protected NodeHandle m_parent = null;
    
    
    /** 
     * Flag indicating if the local node is waiting to be unsubscribed
     * (because it still does not know parent)
     */
    protected boolean m_wantUnsubscribe = false;

    /**
     * Indicates whether this node is manager of this topic.
     */
    protected boolean m_topicManager = false;

    /**
     * Set of IScribeApps that have subscribed to this Topic.
     */
    protected Set m_apps = new HashSet();

    /**
     * This keeps track of the number of heartbeat messages missed
     * corresponding to this topic. 
     * 
     * This value is INCREMENTED whenever a scheduleHB() method is
     * invoked on the scribe object. The assumption is that simultaneously
     * the scheduleHB() method is also being invoked on the other nodes
     * including the parent for this topic. 
     *
     * This value is RESET whenever the local node receives a 
     * heartbeat message, publish message or MessageAckOnSubscribe from the
     * parent for the topic.
     */
    public int m_heartBeatsMissed = 0;


    /**
     * The scribe object on which this topic resides.
     */
    public Scribe m_scribe;


    /* -------------------------------------
     * CONSTRUCTORS
     * ------------------------------------- */
    
    /** 
     * Constructs an empty Topic
     * 
     * @param topicId unique id for this topic.
     * @param scribe the scribe system in which the topic resides.
     */
    public Topic( NodeId topicId, Scribe scribe ) {
	m_topicId = topicId;
	m_scribe = scribe;
    }
    
    
    /* -------------------------------------
     * PUBLIC METHODS
     * ------------------------------------- */
    
    /** 
     * Returns the topic's id
     * @return topic's unique identifier
     */
    public NodeId getTopicId() {
	return m_topicId;
    }
    

    /** 
     * Adds a node to the Set of children in
     * this topic's multicast subtree rooted at this node
     * 
     * @param child the node to be added as a child.
     * 
     * @return true if the child was NOT already in the Set of children.
     */
    public boolean addChild( NodeHandle child ) {
	boolean result;
	synchronized(m_children){
	    result = m_children.add( child );
	}
	// Reflect this child in the distinctChildrenTable
	// maintained by scribe.
	m_scribe.addChildForTopic(child, this.getTopicId());
	return result;
    }
    
    /** 
     * Removes a node from the Set of children in
     * this topic's multicast subtree rooted at this node
     *
     * @param child the child node to be removed from the multicast tree.
     *
     * @return true if the child node was in the Set of children.
     */
    public boolean removeChild( NodeHandle child ) {
	boolean result;
	synchronized(m_children){
	    result = m_children.remove( child );
	}
	// Reflect this child in the distinctChildrenTable
	// maintained by scribe.
	m_scribe.removeChildForTopic(child, this.getTopicId());
	return result ;
    }
    
    /** 
     * Indicates if the local node has children associated with this topic.
     *
     * @return true if the local node has children associated with this topic.
     */
    public boolean hasChildren() {
	boolean result;
	synchronized(m_children){
	    result = !m_children.isEmpty();
	}
	return result; 
    }

    /** 
     * This returns the most current view of the children in this 
     * topic's multicast subtree rooted at the local node. 
     * Note that additions and deletions to the returned vector
     * do not affect the actual children set maintained by the node.
     *
     * @return vector of nodeHandle objects.
     */
    public Vector getChildren(){
	Vector children = new Vector();

	// To avoid concurrent modification, every access to m_children
	// is through synchronized primitive.
	synchronized(m_children){
	    Iterator it = m_children.iterator();
	    while(it.hasNext()){
		NodeHandle nhandle = (NodeHandle)it.next();
		children.add(nhandle);
	    }
	}
	return children;
    }    


    /** 
     * Sets the local node's parent in this topic's multicast
     * tree.
     * 
     * @param parent the node to be the parent.
     */
    public void setParent( NodeHandle parent ) {

	/** 
	 * We are setting the new parent for this topic.
	 * May be we already had a parent for that topic,
	 * so we need to reflect that in our distinctParentTable.
	 * We will remove this topic from list of topics 
	 * corrresponding to previous parent and add a new
	 * entry for new parent (if doesnt exist already) and add
	 * this topic into its corresponding topic list.
	 */
	NodeHandle prev_parent = m_parent;
	Vector topics;

	m_parent = parent;

	if( prev_parent == parent)
	    return;

	if(prev_parent != null)
	    m_scribe.removeParentForTopic(prev_parent, this.getTopicId());
		
	if( m_parent != null)
	    m_scribe.addParentForTopic(m_parent, this.getTopicId());
	
    }
    

    /** 
     * Returns the local node's parent in this topic's multicast
     * tree.
     *
     * @return the parent node.
     */
    public NodeHandle getParent() {
	return m_parent;
    }
    
  
    /**
     * Register an application as a subscriber to this Topic, so 
     * that the application receives events related to the Topic.
     *
     * @param app The application to be registered.
     */
    public void subscribe(IScribeApp app)
    {
	m_apps.add(app);
    }
    
    /**
     * Unregister an application as a Subscriber to this Topic,
     * so that the application no longer receives events regarding
     * this Topic.
     *
     * @param app The application to be unregistered.
     */
    public void unsubscribe(IScribeApp app)
    {
	m_apps.remove(app);
    }
    
    /** 
     * If this topic has any applications registered as subscribers.
     *
     * @return true if there is at least one application subscribed to
     * this topic.
     */
    public boolean hasSubscribers() {
	return m_apps.size() > 0;
    }
    
    /** 
     * Sets flag indicating if the local node is waiting to be unsubscribed
     * from this topic (because it does not yet know its parent)
     *
     * @param wait value to set flag
     */
    public void waitUnsubscribe( boolean wait ) {
	m_wantUnsubscribe = wait;
    }
    
    /** 
     * Returns true if the local node is waiting to be unsubscribed
     * from this topic.
     *
     * @return true 
     * if local node is waiting to be unsubscribed from this topic.
     */
    public boolean isWaitingUnsubscribe()
    {
	return m_wantUnsubscribe;
    }

    /**
     * Sets the flag indicating whether the current node is topic manager
     * for this topic
     *
     * @param topicMgr value of the flag
     */
    public void topicManager( boolean topicMgr ) {

	if( topicMgr ) {
	    ;
	    // No tree repair is required here since it just became the 
	    //root for the topic.
	}
	else if( m_topicManager ) {
	    // When a node was earlier a topic manager(root) for a topic
	    // it did not schedule any tree repair event. Since now it is
	    // no longer the topic manager, it needs to schedule a tree 
	    // repair for this topic.
	    m_scribe.m_maintainer.scheduleTR( this );
	}
	m_topicManager = topicMgr;
    }

    /**
     * Return boolean indicating if the node is topic manager for the
     * current topic.
     *
     * @return true if topic manager, false otherwise
     */
    public boolean isTopicManager() {
	return m_topicManager;
    }

    /**
     * Resets the number of heartbeat messages corresponding to
     * this topic to zero. This method is called when you get a 
     * heartbeat message or a publish message or a MessageAckOnSubscribe.
     */
    public void postponeParentHandler() {
	m_heartBeatsMissed = 0;
    }

    /**
     * Creates a topic reference on the current Scribe node. This
     * method is called by ScribeMessage objects. 
     */
    public void addToScribe() {
	synchronized(m_scribe.m_topics){
	    m_scribe.m_topics.put( m_topicId, this );
	}
    }

    /**
     * Removes this topic reference on the current Scribe node. This
     * method is called by ScribeMessage objects. 
     */
    public void removeFromScribe() {
	synchronized(m_scribe.m_topics){
	    m_scribe.m_topics.remove( m_topicId );
	}
    }

    /**
     * Gets all the applications that wish to receive events regarding 
     * this Topic.
     * @return The applications currently registered with this Topic
     */
    public IScribeApp[] getApps()
    {
	IScribeApp[] apps = new IScribeApp[m_apps.size()]; 
	m_apps.toArray(apps);
	return apps;
    }


}











