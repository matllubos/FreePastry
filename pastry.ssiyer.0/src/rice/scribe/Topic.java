package rice.scribe;

import java.util.*;
import rice.pastry.*;
import rice.scribe.maintenance.*;

/**
 * Data structure used by a node to represent a topic
 *
 * @author Romer Gil
 * @author Eric Engineer
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
     *  node's children in this topic's multicast subtree
     * rooted at the local node.
     */
    protected Set m_children = new HashSet();
    
    /** 
     * Local node's parent in this topics multicast
     * subtree rooted at the local node.
     */
    protected NodeHandle m_parent = null;
    
    /** 
     * Flag indicating if the local node subscribes to this topic
     */
    protected boolean m_subscribed = false;
    
    /** 
     * Flag indicating if the local node is waiting to be unsubscribed
     * (b/c still does not know parent)
     */
    protected boolean m_wantUnsubscribe = false;

    /**
     * Indicates whether this node is manager of this topic
     */
    protected boolean m_topicManager = false;

    /**
     *
     */
    protected HashMap m_topics = null;

    /**
     *
     */
    protected ScribeScheduler m_scheduler = null;

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
	m_topics = scribe.m_topics;
	m_scheduler = scribe.m_scheduler;

	m_scheduler.scheduleHB( this );
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
     * @return true if the child was already in the Set of children.
     */
    public boolean addChild( NodeHandle child ) {
	return m_children.add( child );
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
	return m_children.remove( child );
    }
    
    /** 
     * Indicates if the local node has children associated with this topic.
     *
     * @return true if the local node has children associated with this topic.
     */
    public boolean hasChildren() {
	return !m_children.isEmpty();
    }
    
    /** 
     * Gets children in this topic's multicast subtree
     * rooted at the local node.
     *
     * @return set of NodeHandle objects.
     */
    public Set getChildren() {
	return m_children;
    }
    
    /** 
     * Sets the local node's parent in this topic's multicast
     * subtree rooted at this node.
     * 
     * @param parent the node to be the parent.
     */
    public void setParent( NodeHandle parent ) {
	m_parent = parent;
    }
    
    /** 
     * Returns the owner-node's parent in this topic's multicast
     * subtree rooted at this node.
     *
     * @return the parent node.
     */
    public NodeHandle getParent() {
	return m_parent;
    }
    
    /** 
     * Sets flag indicating if the local node is subscribed to this topic.
     *
     * @param subscribed value to set flag
     */
    public void subscribe( boolean subscribed ) {
	m_subscribed = subscribed;
    }
    
    /** 
     * If the local node is subscribed to this topic
     *
     * @return true if local node is subscribed to this topic.
     */
    public boolean isSubscribed() {
	return m_subscribed;
    }
    
    /** 
     * Sets flag indicating if the local node is waiting to be unsubscribed
     * from this topic (because it does not yet know its parent)
     *
     * @param subscribed value to set flag
     */
    public void waitUnsubscribe( boolean wait ) {
	m_wantUnsubscribe = wait;
    }
    
    /** 
     * If the local node is waiting to be unsubscribed from this topic.
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
	    m_scheduler.cancelTR( this );
	}
	else if( m_topicManager ) {
	    m_scheduler.scheduleTR( this );
	}

	m_topicManager = topicMgr;
    }

    /**
     * Return boolean indicating if the node is topic manager for the current
     * topic
     *
     * @return true if topic manager, false otherwise
     */
    public boolean isTopicManager() {
	return m_topicManager;
    }

    /**
     * Restarts the parent handler timer to indicate that we know the topic's
     * parent is still in the multicast tree. 
     */
    public void restartParentHandler() {
	m_scheduler.scheduleTR( this );
    }

    /**
     * Creates a topic reference on the current Scribe node. This method is 
     * is called by ScribeMessage objects. 
     *
     */
    public void addToScribe() {
	m_topics.put( m_topicId, this );
    }

    /**
     * Removes the topic reference on the current Scribe node. This method is 
     * is called by ScribeMessage objects. 
     *
     * @param t the topic to be removed from this node.
     */
    public void removeFromScribe() {
	m_topics.remove( m_topicId );
    }
}
