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

import rice.pastry.client.*;
import rice.pastry.security.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.pastry.*;
import rice.pastry.direct.*;
import rice.pastry.dist.*;
import rice.pastry.leafset.*;

import java.util.*;
import java.security.*;

import java.io.*;

import rice.scribe.messaging.*;
import rice.scribe.security.*;
import rice.scribe.maintenance.*;

/**
 * @(#) Scribe.java
 *
 * This is the Scribe Object which implements the 
 * IScribe interface (the external Scribe API).
 *
 * @version $Id$
 *
 * @author Romer Gil
 * @author Eric Engineer
 * @author Atul Singh
 * @author Animesh Nandi
 */
public class Scribe extends PastryAppl implements IScribe
{
    /*
     * Member fields
     */

    /**
     * The set of IScribeApp's that have registered to this Scribe substrate.
     */
    protected Vector m_apps;

    /**
     * This flag is set to true when this Scribe substrate is ready. 
     * The Scribe substrate is ready when the underlying Pastry Node is ready.
     */
    private boolean m_ready;

   

    /**
     * Set of topics on the local scribe node. 
     */
    public HashMap m_topics = null;
    

    /**
     * The threshold value for triggering tree repair for a topic. 
     * Tree repair for a topic is triggered after we have missed this
     * threshold value of heartbeat messages from the parent for the topic.
     * This threshold value can be set using the setTreeRepairThreshold
     * method and its value retreived using the getTreeRepairThreshold 
     * method. Default value is 2.
     */
    private int m_treeRepairThreshold = 2;

    

    /**
     * Hashtable having mapping from a child -> list of topics for 
     * which this child is in local node's children list for that topic.
     * In this way, this table will have only distinct children and for
     * each child we have the list of topics for which it is a child.
     * Used to identify distinct chidren when sending HeartBeat messages,
     * so that we dont send a node multiple HeartBeat messages when it is
     * child for more than one topic.
     */
    protected Hashtable m_distinctChildrenTable;


    /**
     * Hashtable having mapping from a parent node -> list of topics for 
     * which the parent node is local node's parent in the corresponding
     * multicast tree for that topic. Whenever local node receives a
     * HeartBeat message from a node, it calls the postponeParentHandler 
     * method for all topics for which that node is its parent.
     * By having this data structure locally, we avoid sending lists
     * of topics for which a node is parent in the HeartBeat message (this 
     * would cause the Heartbeat message to grow very large).
     */
    protected Hashtable m_distinctParentTable;




    /**
     * Optimization on number of HeartBeat Messages sent. Each node keeps 
     * track of the distinct children to which it has sent a PUBLISH message 
     * in last HeartBeat period. Thus in the scheduleHB() method which denotes
     * the end of the previous HeartBeatPeriod, it need not send an explicit
     * HeartBeat message to these 'marked' children, since a PUBLISH message
     * is an implicit keep-alive message from the parent that it is alive.
     */
    protected Set m_alreadySentHBNodes;


    /**
     * The AckOnSubscribe switch if true activates the immediate sending 
     * of ACK by a node when it receives a SUBSCRIBE message from its
     * child for a topic. Otherwise, this immediate ACK is not sent.
     * This flag is set to TRUE.
     * Setting it to false will cause several more messages in the form of
     * MessageRequestToParent and MessageReplyFromParent to be exchanged 
     * between the child node and the parent node, when the child node 
     * receives a HeartBeat message from the parent. NOTE that if the HeartBeat
     * message maintained a list of topics for which the child node(receiver of
     * heartbeat message) was its child, then the AckOnSubscribe switch could
     * have been set to false and these extra messages in the form of
     * MessageRequestToParent and MessageReplyFromParent could have been got 
     * rid of. But due to scalability issues of sending the list of topics in
     * the HeartBeat message we have adopted the current approach of setting 
     * the AckOnSubscribe switch to TRUE and additionally having the 
     * MessageRequestToParent and MessageReplyFromParent type of message.
     */
    public  boolean m_ackOnSubscribeSwitch = true;


    /**
     * The receiver address for the scribe system.
     */
    protected static Address m_address = new ScribeAddress();

    /**
     * The SendOptions object to be used for all messaging through Pastry.
     */
    protected SendOptions m_sendOptions = null;

    /**
     * The Credentials object to be used for all messaging through Pastry.
     */
    protected static Credentials m_credentials = null;

    /**
     * Handles all the security verification.
     */
    protected IScribeSecurityManager m_securityManager = null;

    /**
     * The ScribeMaintainer object handles all the tree maintenance
     * activities like the sending of heartbeat messages and issuing 
     * tree repair events for all the topics residing on the local node.
     */
    public ScribeMaintainer m_maintainer;


    /**
     * The time period determining Scribe Tree Maintenance Activity
     * frequency
     */
    public static int m_scribeMaintFreq = 10; // seconds


    /**
     * The set of applications which are interested in getting notified
     * whenever the local node becomes an intermediate node for a topic.
     */
    public Set m_scribeObservers = null;


    private static class ScribeAddress implements Address {
	private int myCode = 0x8aec747c;
	
	public int hashCode() { return myCode; }

	public boolean equals(Object obj) {
	    return (obj instanceof ScribeAddress);
	}
    }

    /* These objects are values corresponding to the keys in the 
     * m_DistinctChildrenTable and the m_DistinctParentTable data 
     * structures maintained by the local Scribe node.
     */
    private static class HashTableEntry {

	/* The list of topics for which the key for this HashTableEntry is
	 * a parent or a child of the local node, depending on whether it is 
	 * being used for the m_DistinctParentTable or the 
	 * m_DistinctChildrenTable respectively.
	 */
	private Vector topicList;


	/* This is the Bitwise Exclusive OR of the topics in topicList above.
	 * Fingerprints are matched when the child node receives a HeartBeat
	 * message from a parent node. In order to remove inconsistent views
	 * of parent-child relationships as seen from the parent and the child,
	 * we match the fingerprint sent in the HeartBeat message (which is the
	 * fingerprint corresponding to the child node as key in the
	 * m_DistinctChildren table in the parent node) against the fingerprint
	 * corresponding to the parent node as key in the m_DistinctParentTable
	 * in the child node.
	 * Inconsistent views can arise when a MessageAckOnSubscribe is lost or
	 * a failure to receive HeartBeat messages in time or loss of other 
	 * types of messages.
	 * On a fingerprint mismatch,the child node sends a
	 * MessageRequestToParent to the parent node requesting for the list
	 * of topics for which the parent node has it as a child.
	 */
        private NodeId fingerprint ;

	public HashTableEntry() {
	    fingerprint = new NodeId();
	    topicList = new Vector();
	}
    

	public void removeTopicId(NodeId topicId) {
	    if( topicList.contains(topicId)){
		topicList.removeElement(topicId);
		fingerprint.xor(topicId);
	    }
	}

	public void addTopicId(NodeId topicId) {
	    if( !topicList.contains(topicId)){
		topicList.addElement(topicId);
		fingerprint.xor(topicId);
	    }
	}

	public int size() {
	    return topicList.size();
	}
	
	public NodeId getFingerprint() {
	    return fingerprint;
	}
   
 }


    /**
     * Constructor.
     *
     * @param pn the pastry node that client will attach to.
     *
     * @param cred the credentials associated with this scribe object.
     *
     */
    public Scribe( PastryNode pn, Credentials cred) {
	super( pn );
	m_ready = pn.isReady();
	m_topics = new HashMap();
	m_sendOptions = new SendOptions();
	m_securityManager = new PSecurityManager();
	m_maintainer = new ScribeMaintainer( this);
	m_credentials = cred;
	if( cred == null ) {
	    m_credentials = new PermissiveCredentials();
	}
	m_distinctChildrenTable = new Hashtable();
	m_distinctParentTable = new Hashtable();
	m_alreadySentHBNodes = new HashSet();
	m_apps = new Vector();
	m_scribeObservers = new HashSet();
    }

    /**
     * The tree repair event for a particular topic in Scribe is triggered
     * when a node misses a certain threshold number of heartbeat 
     * messages from its parent for the topic. This threshold value can be 
     * set using this method.
     *
     * @param    value
     * The value for the tree repair threshold.
     */
    public void setTreeRepairThreshold(int value) {
	m_treeRepairThreshold = value;
    }


    /**
     * Gets the tree repair threshold value.
     *
     * @return the tree repair threshold value
     */
    public int getTreeRepairThreshold() {
	return m_treeRepairThreshold;
    }

    /** 
     * Returns true is the Scribe substrate is ready. The Scribe substrate is
     * ready when underlying PastryNode is ready.
     */
    public boolean isReady() {
	return m_ready;
    }



    /** 
     * Registers the IScribeApp to the Scribe substrate. This is 
     * required for the IScribeApp to get the upcall scribeIsReady()
     * notifying it that the underlying Scribe substrate is ready. The 
     * IScribeApp should call create, join, leave, multicast
     * only after they are notified that the underlying Scribe substrate
     * is ready, otherwise these operations fail.
     */
    public void registerApp(IScribeApp app) {
	if(isReady())
	    app.scribeIsReady();
	m_apps.add(app);
    }

    /** 
     * Registers the application that implements the IScribeObserver 
     * interface. Whenever a topic is implicitly created, (i.e. a node 
     * receives SUBSCRIBE message for a topic it hasnt subscribed to, 
     * thereby creating a topic data structure at local node) these registered
     * applications would be notified.
     *
     * @param app The application interested in getting notified whenever
     *            a topic is implicitly created.
     */
    public void registerScribeObserver(IScribeObserver app) {
	m_scribeObservers.add(app);
    }
    


    /**
     * This is called when the underlying pastry node is ready. With regard to 
     * Scribe, we schedule the periodic tree maintenance activities when the 
     * pastry network is a Distributed network instead of a simulate network.
     */
    public void notifyReady() {
	Credentials cred;
	cred = new PermissiveCredentials();
	//System.out.println("notifyReady called for Scribe application on" + getNodeId()); 
	
	m_ready = true;
	/**
	 * Notify IScribeApp's that were previously waiting for the 
	 * Scribe substrate to be ready.
	 */

	// m_apps could be null since the notifyReady() can be called even
	// before the execution of the constructor of this class is complete.
	if(m_apps != null) {
	    Vector tmp_apps = new Vector(m_apps);
	    Iterator it = tmp_apps.iterator();
	    while (it.hasNext())
		((IScribeApp)(it.next())).scribeIsReady();
	}

	// Schedule the periodic tree maintenance activities.
	if(thePastryNode instanceof DistPastryNode) {
	    thePastryNode.scheduleMsgAtFixedRate(makeScribeMaintenanceMessage(cred), m_scribeMaintFreq*1000, m_scribeMaintFreq*1000);
	}
    }
    
    
    
    /**
     * Sends heartbeat messages to this local node's children for all the 
     * topics on this local scribe node. This method should be invoked 
     * periodically by the driver with the same frequency in all nodes.
     * In addition to initiating sending of heartbeat messages from this
     * local node, this method also implicitly notifies the local node that
     * it should expect a heartbeat message from its parents for all the 
     * topics on this local node. So, if it fails to receive a threshold 
     * value of such heartbeat messages from any parent for a particular
     * topic, a tree repair event is triggered for that topic.
     *
     */
    public void scheduleHB(){
	if(!isReady())
	    return;
	m_maintainer.scheduleHB();
	return;
    }
    
    /**
     * Returns true if the local node is currently the 
     * root(the node that is closest to the topicId) of the topic.
     * 
     * @return true if the local node is currently the root for the topic.
     */
    public boolean isRoot(NodeId topicId) {
	return isClosest(topicId);
    }


    /**
     * Creates a group/topic if the credentials are valid. Nodes must then join
     * this group in order to get information multicast to it.
     *
     * @param    cred
     * The credentials of the entity creating the group  
     *
     * @param    topicID       
     * The ID of the group to be created
     *
     * @return true if the operation was successful, false if the operation
     *         failed because the underlying Scribe substrate was not ready. 
     */
    public boolean create( NodeId topicId, Credentials cred) {
	if(!isReady()) return false;
	ScribeMessage msg = makeCreateMessage( topicId, cred);
	this.routeMsg( topicId, msg, cred, m_sendOptions );
	return true;
    }
    

    /**
     * Joins a multicast group/topic.  When a node joins a multicast group,
     * it receives all messages multicast to that group.
     *
     *
     * @param    topicID        
     * The ID of the group to join to
     *
     * @param    subscriber
     * The application joining the group
     *
     * @param    cred
     * The credentials of the entity joining the group
     *
     * @return true if the operation was successful, false if the operation
     *         failed because the underlying Scribe substrate was not ready. 
     *
     */
    public boolean join( NodeId topicId, IScribeApp subscriber, 
			   Credentials cred ) {
	return join(topicId, subscriber, cred, null);
    }



    /**
     * Joins a multicast group/topic.  When a node joins a multicast group,
     * it receives all messages multicast to that group. An application can
     * specify additional data to be sent with the SUBSCRIBE message.
     *
     * @param    cred
     * The credentials of the entity joining the group
     *
     * @param    topicID        
     * The ID of the group to join to
     *
     * @param    subscriber
     * The application joining the group
     *
     * @param obj
     * Additional data to be passed with the SUBSCRIBE msg, specific to
     * an application. Should be serializable.
     *
     * @return true if the operation was successful, false if the operation
     *         failed because the underlying Scribe substrate was not ready. 
     *
     */
    public boolean join( NodeId topicId, IScribeApp subscriber, 
			   Credentials cred, Serializable obj ) {
	if(!isReady()) return false;
	Topic topic = (Topic) m_topics.get( topicId );
	
	if ( topic == null ) {
	    topic = new Topic( topicId, this );
	    // add topic to known topics
	    topic.addToScribe();
	}
	
	// Register application as a subscriber for this topic
	topic.subscribe( subscriber );

	if( topic.getState() == Topic.CREATED){
	    ScribeMessage msg = makeSubscribeMessage( topicId, cred);
	    topic.postponeParentHandler();
	    msg.setData(obj);
	    topic.setState(Topic.JOINING);
	    this.routeMsg( topicId, msg, cred, m_sendOptions );
	}

	return true;
    }


    /**
     * Leaving a multicast group/topic. After a node leaves a group, it
     * will no longer receive messages multicast to this group.
     *
     * @param    cred
     * The credentials of the entity leaving the group
     *
     * @param    topicID        
     * The ID of the group to leave
     *
     * @param    subscriber
     * The application leaving the group.  Use null if 
     * not directly called by an application.
     *
     * @return true if the operation was successful, false if the operation
     *         failed because the underlying Scribe substrate was not ready. 
     */
    public boolean leave( NodeId topicId, IScribeApp subscriber, Credentials cred ) {
	if(!isReady()) return false;
	Topic topic = (Topic) m_topics.get( topicId );
	
	// If topic unknown, must give an error
	if ( topic == null ) {
	    return false;
	}
	
	// unregister application as subscriber for this topic
	if (subscriber != null)
	    topic.unsubscribe( subscriber );

	// send unsubscribe message if no more applications registered
	if ( !topic.hasSubscribers() ) {
	    //System.out.println("Sending unsubscribe messages since no more applications at "+this.getNodeId());
	    ScribeMessage msg = makeUnsubscribeMessage( topicId, cred );
	    this.routeMsgDirect( thePastryNode.getLocalHandle(), msg, cred,
				 m_sendOptions );
	}
	return true;
    }
    

    /**
     * Multicast information to a group/topic.  Data will be delivered 
     * to All nodes that have joined the group.  The message will trickle from
     * the root of the multicast tree for the group DOWN the tree, with each
     * node sending this message to its children for the group.
     *
     * @param   cred
     * The credentials of the entity multicasting to the group
     *
     * @param   topicID         
     * The ID of the group to multicast.
     *
     * @param   obj           
     * The information that is to be multicast.
     * This should be serializable.
     *
     * @return true if the operation was successful, false if the operation
     *         failed because the underlying Scribe substrate was not ready. 
     */
    public boolean multicast( NodeId topicId, Serializable obj, Credentials cred ) {
	if(!isReady()) return false;
	ScribeMessage msg = makePublishMessage( topicId, cred );
	msg.setData( obj );
	this.routeMsg( topicId, msg, cred, m_sendOptions );
	return true;
    }


    /**
     * Anycast to a group/topic. Data will be delivered to 'ANY' one node
     * which has joined the group. The handling of anycast message is 
     * left to the application. Default implementation of anycast message
     * implements DFS of the tree, looking for nodes in the tree which 
     * can satisfy the request of the anycast message.
     *
     * @param   groupID         
     * The ID of the group to anycast.
     *
     * @param   obj           
     * The information that is to be included in anycast message.
     * This should be serializable.
     *
     * @param   cred
     * The credentials of the entity anycasting to the group.
     *
     * @return true if the operation was successful, false if the operation
     *         failed because the underlying Scribe substrate was not ready.
     */
    public boolean anycast( NodeId groupID, Serializable obj, Credentials cred ){
	if(!isReady()) return false;
	//System.out.println("Sending anycast message from "+getNodeId());
	ScribeMessage msg = makeAnycastMessage( groupID, cred );
	msg.setData( obj );
	this.routeMsg( groupID, msg, cred, m_sendOptions );
	return true;
    }


    /**
     * An application can create sub-classes of MessageAnycast type of
     * messages and write their own handling functions so as to do
     * some application-specific predicate searching. Also, by this mean
     * they can modify the default DFS satisfying their own conditions and
     * predicates.
     *
     * @param   groupID         
     * The ID of the group to anycast.
     *
     * @param   anycastMessage           
     * The anycast message created by application specific to its needs,
     * can decide how the message is going to be routed in the anycast 
     * tree, how the message is going to be handled and so on.
     *
     * @param   cred
     * The credentials of the entity anycasting to the group.
     *     
     * @return true if the operation was successful, false if the operation
     *         failed because the underlying Scribe substrate was not ready.
     * 
     */
    public boolean anycast(NodeId groupID, MessageAnycast anycastMessage, Credentials cred){
	if(!isReady()) return false;
	this.routeMsg( groupID, anycastMessage, cred, m_sendOptions );
	return true;
    }


    /**
     * Generate a unique id for the topic, which will determine its rendezvous
     * point.  This is a helper method. Applications can use their own 
     * methods to generate TopicId.
     *
     * @param topicName 
     * the name of the topic (unique to the local node)
     *
     * @return the Topic id.
     */
    public NodeId generateTopicId( String topicName ) { 
	MessageDigest md = null;

	try {
	    md = MessageDigest.getInstance( "SHA" );
	} catch ( NoSuchAlgorithmException e ) {
	    System.err.println( "No SHA support!" );
	}
	//if(m_ready)
	    //System.out.println("DEBUG :: Scribe is ready at"+getNodeId()+" , topic is "+topicName);
	md.update( topicName.getBytes() );
	byte[] digest = md.digest();
	
	NodeId newId = new NodeId( digest );
	
	return newId;
    }


    /**
     * Returns the address of this client. 
     *
     * @return the address.
     */
    public Address getAddress() { return m_address; }


    /**
     * Returns the credentials of this client.
     *
     * @return the credentials.
     */
    public Credentials getCredentials() { return m_credentials; }
    

    /**
     * Called by pastry when a message arrives for this client.
     *
     * @param msg the message that is arriving.
     */
    public void messageForAppl( Message msg ) {
	ScribeMessage smsg = (ScribeMessage)msg;

	NodeId topicId = smsg.getTopicId();
	Topic topic = (Topic)m_topics.get( topicId );
	//if(topic == null)
	//System.out.println("DEBUG :: Topic is null for topic "+topicId+" at "+getNodeId());
	//System.out.println("DEBUG :: Received msg at "+getNodeId()+" for topic "+topicId);
	smsg.handleDeliverMessage( this, topic );
    }



    /**
     * Called by pastry when a message is enroute and is passing through this 
     * node.  
     *
     * @param msg the message that is passing through.
     * @param target the final destination node id.
     * @param nextHop the next hop for the message.
     * @param opt the send options the message was sent with.
     *
     * @return true 
     * if the message should be routed, false if the message should be 
     * cancelled.
     */
    public boolean enrouteMessage( Message msg, NodeId target, 
				  NodeId nextHop, SendOptions opt ) {
	ScribeMessage smsg = (ScribeMessage)msg;

	NodeId topicId = smsg.getTopicId();
	Topic topic = (Topic)m_topics.get( topicId );
	
	//System.out.println( "Node: " + getNodeId() + " enroute" );
	//System.out.println( "Node: " + target + " enroute to target" );
	//System.out.println( "Node: " + nextHop + " enroute nextHop" );
	//System.out.println(" Node "+getNodeId() +" received "+smsg);
	//System.out.println("Topic "+ topic + " for topicId "+topicId);
	return smsg.handleForwardMessage( this, topic );
    }

    /**
     * Called by pastry when the leaf set changes.
     *
     * @param nh the handle of the node that entered or left the leafset.
     * @param wasAdded true if the node was added, false if it was removed.
     */
    public void leafSetChange( NodeHandle nh, boolean wasAdded ) {
	NodeId nid = nh.getNodeId();
	Topic topic;
	NodeId topicId, myNodeId;
	Credentials c = m_credentials;
	Vector topicVector = new Vector();
	int i = 0;

	myNodeId = this.getNodeId();
	
	topicVector = getTopics();
	

	if( wasAdded ) {
	    //if the node was added we must check if the new node should be
	    //topic manager for any of our topics.
	    
	    while( i < topicVector.size() ) {
		topic = (Topic)topicVector.elementAt(i);
		i++;
		//for all topics, if we are manager then we do more processing
		if( topic.isTopicManager() ) {
		    topicId = topic.getTopicId();
		    if (!isRoot(topicId)) {
			//We have got a new topic manager.
			topic.topicManager( false );

			//send a subscribe message
			if( topic.getParent() == null) {
			    ScribeMessage msg = makeSubscribeMessage( topicId, c);
			    topic.postponeParentHandler();
			    // FIX --Need to propogate any application specific
			    // data to new root, so need to call 
			    // faultHandler().
			    IScribeApp[] apps = topic.getApps();
			    for( int l = 0; l < apps.length; l++){
				apps[l].faultHandler(msg, null);
			    }
			    this.routeMsg( topicId, msg, c, m_sendOptions );
			}
		    }
		}
	    }
	}
	else {
	    
	    while( i < topicVector.size() ) {
		topic = (Topic)topicVector.elementAt(i);
		i++;
		topicId = topic.getTopicId();
		// for all topics, if we are the current root, but
		// before this leafSet change we were not the
		// topicManager for this topic, we become the new
		// topicManager.
		if (isRoot(topicId)) {
		    if( !topic.isTopicManager() ) {
			//We are the  new topic manager.
			topic.topicManager( true );
			NodeHandle prev_parent;
			prev_parent = topic.getParent();
			if( prev_parent != null){
			    ScribeMessage msgu = makeUnsubscribeMessage( topicId, c);
			    this.routeMsgDirect(prev_parent, msgu, c, m_sendOptions);
			}
			//System.out.println("DEBUG :: Scribe -- setting parent to null for topic"+topic.getTopicId()+ " at "+getNodeId());
			topic.setParent(null);
			IScribeApp[] apps = topic.getApps();
			for( int l = 0; l < apps.length; l++){
			    apps[l].isNewRoot(topic.getTopicId());
			}
		    }
		}
	    }
	}
    }



    /**
     * Returns the handle of the local node on which this Scribe 
     * application resides.
     *
     * @return the node handle.
     */
    public NodeHandle getNodeHandle() {
	return thePastryNode.getLocalHandle();
    }


    /**
     * Returns the send options in the Scribe system.
     *
     * @return the send options.
     */
    public SendOptions getSendOptions() {
	return m_sendOptions;
    }


    /**
     * Returns the security manager from the Scribe node.
     *
     * @return the security manager.
     */
    public IScribeSecurityManager getSecurityManager() {
	return m_securityManager;
    }


    /**
     * Makes a subscribe message using the current Pastry node as the source.
     *
     * @param tid the topic id the message refers to.
     * @param c the credentials that will be associated with the message
     * @return the ScribeMessage.
     */
    public ScribeMessage makeSubscribeMessage( NodeId tid, Credentials c) {
	return new MessageSubscribe( m_address, this.thePastryNode.getLocalHandle(), tid, c );
    }

    /**
     * Makes an unsubscribe message using the current Pastry node as the 
     * source.
     *
     * @param tid the topic id the message reffers to.
     * @param c the credentials that will be associated with the message
     * @return the ScribeMessage.
     */
    public ScribeMessage makeUnsubscribeMessage( NodeId tid, Credentials c ) {
	return new MessageUnsubscribe( m_address, this.thePastryNode.getLocalHandle(), tid, c );
    }

    /**
     * Makes a create message using the current Pastry node as the source.
     *
     * @param tid the topic id the message reffers to.
     * @param c the credentials that will be associated with the message
     *
     * @return the ScribeMessage.
     */
    public ScribeMessage makeCreateMessage( NodeId tid, Credentials c) {
	return new MessageCreate( m_address, this.thePastryNode.getLocalHandle(), tid, c );
    }

    /**
     * Makes a publish message using the current Pastry node as the source.
     *
     * @param tid the topic id the message reffers to.
     * @param c the credentials that will be associated with the message
     * @return the ScribeMessage.
     */
    public ScribeMessage makePublishMessage( NodeId tid, Credentials c ) {
	return new MessagePublish( m_address, this.thePastryNode.getLocalHandle(), tid, c );
    }

     /**
     * Makes a anycast message using the current Pastry node as the source.
     *
     * @param tid the topic id the message reffers to.
     * @param c the credentials that will be associated with the message
     * @return the ScribeMessage.
     */
    public ScribeMessage makeAnycastMessage( NodeId tid, Credentials c ) {
	return new MessageAnycast( m_address, this.thePastryNode.getLocalHandle(), tid, c );
    }


    /**
     * Makes a heart-beat message using the current Pastry node as the source.
     *
     * @param c the credentials that will be associated with the message
     * @return the ScribeMessage.
     */
    public ScribeMessage makeHeartBeatMessage( Credentials c ) {
	return new MessageHeartBeat( m_address, this.thePastryNode.getLocalHandle(), c);
    }

    /**
     * Makes a AckOnSubscribe message using the current Pastry node as the 
     * source.
     *
     * @param tid the topic id the message reffers to.
     * @param c the credentials that will be associated with the message
     * @return the ScribeMessage.
     */
    public ScribeMessage makeAckOnSubscribeMessage( NodeId tid, Credentials c ) {
        return new MessageAckOnSubscribe( m_address, this.thePastryNode.getLocalHandle(), tid, c, null);
    }
    
    /**
     * Makes a AckOnSubscribe message using the current Pastry node as the 
     * source. Also takes in a serializable data.
     *
     * @param tid the topic id the message reffers to.
     * @param c the credentials that will be associated with the message
     * @param data the data to be shipped along the message
     * @return the ScribeMessage.
     */
    public ScribeMessage makeAckOnSubscribeMessage( NodeId tid, Credentials c, Serializable data ) {
        return new MessageAckOnSubscribe( m_address, this.thePastryNode.getLocalHandle(), tid, c, data);
    }
    /**
     * Makes a RequestToParent message using the current Pastry node as the 
     * source.
     *
     * @param c the credentials that will be associated with the message
     * @return the ScribeMessage.
     */
    public ScribeMessage makeRequestToParentMessage( Credentials c ) {
        return new MessageRequestToParent( m_address, this.thePastryNode.getLocalHandle(),  c);
    }

     /**
     * Makes a ReplyFromParent message using the current Pastry node as the 
     * source.
     *
     * @param c the credentials that will be associated with the message
     * @return the ScribeMessage.
     */
    public ScribeMessage makeReplyFromParentMessage(  Credentials c ) {
        return new MessageReplyFromParent( m_address, this.thePastryNode.getLocalHandle(), c);
    }


    /**
     * Makes a ScribeMaintenance message using the current Pastry node as the 
     * source.
     *
     * @param c the credentials that will be associated with the message
     * @return the ScribeMessage.
     */
    public ScribeMessage makeScribeMaintenanceMessage(  Credentials c ) {
        return new MessageScribeMaintenance( m_address, this.thePastryNode.getLocalHandle(), c);
    }


    /**
     * Returns the topic object associated with topicId.
     * @param topicId the topic id
     * @return the Topic associated with the topicId
     */
    public Topic getTopic( NodeId topicId) {
	Topic topic = (Topic) m_topics.get( topicId );
	return topic;
    }

    

    /** 
     * Method to access the list of topics this scribe is 
     * responsible for. Returns a vector of topics residing
     * on this node.
     */
    public Vector getTopics(){
	Vector topicVector = new Vector();

	Iterator it = m_topics.values().iterator();		
	while( it.hasNext()){
	    topicVector.add((Topic)it.next());
	}
	return topicVector;
    }


    /**
     * Gets the Vector of distinct children of this local node
     * in multicast tree of all topics.
     * @return Vector of distinct children
     */
    public Vector  getDistinctChildren(){
	Set set;
	Vector result =  new Vector();

	set = m_distinctChildrenTable.keySet();
	Iterator it = set.iterator();
	while(it.hasNext()){
	    result.addElement((NodeHandle)it.next());
	}

	return result;
    }

    /**
     * Adds a child for a topic into the distinctChildrenTable.
     * If this child already exists, then we add this topic into
     * its corresponding list of topics for which it is our child.
     * Otherwise, we create a new entry into the hashtable.
     * 
     * @param child The NodeHandle of Child
     * @param topicId The topicId for the topic for which we are
     *                adding this child
     */
    public void addChildForTopic(NodeHandle child, NodeId topicId){
	    Set set = m_distinctChildrenTable.keySet();
	    HashTableEntry entry;

	    if( set.contains(child)){
		entry = (HashTableEntry)m_distinctChildrenTable.get(child);
		entry.addTopicId(topicId);
	    }
	    else {
		entry = new HashTableEntry();
		entry.addTopicId(topicId);
		m_distinctChildrenTable.put(child, entry);
	    }
    }
    
    /**
     * Removes a child for a topic from the distinctChildrenTable.
     * See if we already have it as a child for some other
     * topic , if yes then remove this topicId from the list of topics
     * for which this node is our child. Otherwise, we remove the entry
     * from the hashtable since the list of topics becomes empty.
     *
     * @param child The NodeHandle of Child
     * @param topicId The topicId for the topic for which we are
     *                removing this child
     */
    public void removeChildForTopic(NodeHandle child, NodeId topicId){
	
	Set set = m_distinctChildrenTable.keySet();
	HashTableEntry entry;
	
	if( set.contains(child)){
	    entry = (HashTableEntry)m_distinctChildrenTable.get(child);
	    entry.removeTopicId(topicId);
	    
	    if(entry.size() == 0)
		m_distinctChildrenTable.remove(child);
	}
	else {
	    // We need not do anything here.
	}
    }


    /**
     * Gets the vector of topicIds for which given node
     * is a child.
     *
     * @param child  The NodeHandle of child node.
     * @return Vector of topicIds for which this node is 
     *         a child.
     */
    public Vector getTopicsForChild(NodeHandle child){
	HashTableEntry entry;
	Vector topics_clone = null;

	if(child == null || !m_distinctChildrenTable.keySet().contains(child))
	    return null;

	entry = (HashTableEntry)m_distinctChildrenTable.get(child);
	topics_clone = (Vector)entry.topicList.clone();

	return topics_clone;
    }


    /**
     * Gets the Vector of distinct parent of this local node
     * in the multicast trees of all topics.
     *
     * @return Vector of distinct parents
     */
    public Vector  getDistinctParents(){
	Set set;
	Vector result =  new Vector();

	set = m_distinctParentTable.keySet();
	Iterator it = set.iterator();
	while(it.hasNext()){
	    result.addElement((NodeHandle)it.next());
	}
	return result;
    }


    /**
     * Removes a parent for a topic fromthe distinctParentTable.
     * See if we already have it as a parent for some other
     * topic , if yes then remove this topicId from the list of topics
     * for which this node is our parent. Otherwise, we remove the entry
     * from the hashtable since the list of topics becomes empty.
     *
     * @param parent The NodeHandle of Parent
     * @param topicId The topicId for the topic for which we are
     *                removing this parent
     */
    public void removeParentForTopic(NodeHandle parent, NodeId topicId){
	
	Set set = m_distinctParentTable.keySet();
	HashTableEntry entry;
	
	if( set.contains(parent)){
	    entry = (HashTableEntry)m_distinctParentTable.get(parent);
	    entry.removeTopicId(topicId);
	    
	    if(entry.size() == 0)
		m_distinctParentTable.remove(parent);
	}
	else {
	    // We need not do anything here.
	}
    }
    

    /**
     * Adds a parent for a topic into the distinctParentTable.
     * If this child already exists, then we add this topic into
     * its corresponding list of topics for which it is our parent.
     * Otherwise, we create a new entry into the hashtable.
     * 
     * @param child The NodeHandle of Parent
     * @param topicId The topicId for the topic for which we are
     *                adding this parent
     */
    public void addParentForTopic(NodeHandle parent, NodeId topicId){

	Set set = m_distinctParentTable.keySet();
	HashTableEntry entry;
	
	if( set.contains(parent)){
	    entry = (HashTableEntry)m_distinctParentTable.get(parent);
	    entry.addTopicId(topicId);
	}
	else {
	    entry = new HashTableEntry();
	    entry.addTopicId(topicId);
	    m_distinctParentTable.put(parent, entry);
	}
	
    }	  

    /**
     * Gets the vector of topicIds for which given node
     * is our parent.
     *
     * @param parent  The NodeHandle of parent node.
     * @return Vector of topicIds for which this node is 
     *         our parent.
     */
    public Vector getTopicsForParent(NodeHandle parent){
	HashTableEntry entry;
	Vector topics_clone = null;
	
	if(parent == null || !m_distinctParentTable.keySet().contains(parent))
	    return null;

	entry = (HashTableEntry)m_distinctParentTable.get(parent);
	topics_clone = (Vector)entry.topicList.clone();

	return topics_clone;
    }


    /* Gets the fingerprint corresponding to a key in the 
     * m_DistinctParentTable.
     *
     * @param parent the key
     * @return the fingerprint corresponding to this key
     */
    public NodeId getFingerprintForParentTopics(NodeHandle parent) {
	HashTableEntry entry;
	NodeId fingerprint;
	
	if(parent == null || !m_distinctParentTable.keySet().contains(parent))
	    return null;

	entry = (HashTableEntry)m_distinctParentTable.get(parent);
	fingerprint = entry.getFingerprint();

	return fingerprint;
    }

    /* Gets the fingerprint corresponding to a key in the 
     * m_DistinctChildrenTable.
     *
     * @param parent the key
     * @return the fingerprint corresponding to this key
     */
    public NodeId getFingerprintForChildTopics(NodeHandle child) {
	HashTableEntry entry;
	NodeId fingerprint;
	
	if(child == null || !m_distinctChildrenTable.keySet().contains(child))
	    return null;

	entry = (HashTableEntry)m_distinctChildrenTable.get(child);
	fingerprint = entry.getFingerprint();

	return fingerprint;
    }
    
    

    /**
     * Adds a child to the list of nodes to which this node
     * has already send a implicit HeartBeat message in the form of 
     * Publish message in this Heartbeat period.
     *
     * @param childId NodeId of child.
     * @return true if child already exists else false
     */
    public boolean addChildToAlreadySentHBNodes(NodeId childId){
	boolean result;

	result = m_alreadySentHBNodes.add((NodeId)childId);
	return result;
    }


    /**
     * Gets the vector of nodes to which an implicit HeartBeat
     * in form of Publish message was sent in last HeartBeat
     * period. 
     *
     * @return Vector of child nodes to which a publish message
     *         was sent in last HeartBeat period.
     */
    public Vector getAlreadySentHBNodes(){
	Vector list = new Vector();
	NodeId childId ;
	
	Iterator it = m_alreadySentHBNodes.iterator();
	while(it.hasNext()){
	    childId = (NodeId) it.next();
	    if( ! list.contains(childId))
		list.addElement(childId);
	}
	return list;
    }

    /** 
     * The set is cleared so that the set contains
     * only those nodes to which Publish message was sent in
     * last HeartBeat period.
     */
    public void clearAlreadySentHBNodes(){
	m_alreadySentHBNodes.clear();
	return;
    }

    /* Gets the local NodeHandle associated with this Scribe node.
     *
     * @return local handle of Scribe node.
     */
    public NodeHandle getLocalHandle() {
	return thePastryNode.getLocalHandle();
    }


    /** 
     * Returns the local node's parent in this topic's multicast
     * tree.
     *
     * @param topicId
     * The id of the topic.
     * @return the parent node.
     */
    public NodeHandle getParent(NodeId topicId) {
	Topic topic;
	NodeHandle parent;
	
	topic = getTopic(topicId);
	if(topic == null) 
	    return null;
	parent = topic.getParent();
	return parent;
    }


     /**
     * Sets the parent for the topic specified by topicId.
     *
     * @param parent The new parent for the topic
     *
     * @param topicId the topic for which this parent is set
     *
     * @return true if operation was successful, false otherwise
     *         
     */
    public boolean setParent(NodeHandle parent, NodeId topicId){
	Topic topic = getTopic(topicId);
	//System.out.println("DEBUG :: Scribe --- caling setParent for topic "+topicId+" to parent "+parent);
	if(topic != null){
	    topic.setParent(parent);
	    return true;
	}
	else
	    return false;
    }


    /** 
     * This returns the most current view of the children in this 
     * topic's multicast subtree rooted at the local node. 
     *
     * @param topicId
     * The id of the topic.
     *
     * @return vector of children nodehandles.
     */
    public Vector getChildren(NodeId topicId) {
	Topic topic;
	Vector children;

	topic = getTopic(topicId);
	if(topic == null)
	    return null;
	children = topic.getChildren();
	return children;
    }


    /**
     * Whenever the children table for a topic is changed, ( a child is added
     * or removed), this method is invoked to do some handling.
     * If child was added :
     *     if ackOnSubscribe is set, then send AckOnSubscribe mesg to new child
     *
     * If child was dropped:
     *     if there are no applications as well as no child for this topic,
     *     then local topic object is removed and an unsubscribe message is
     *     send.
     * 
     * @param child the child which was added/removed
     *
     * @param topicId the topic for which child was added/removed
     *
     * @param wasAdded true if child was added, false if child was removed.
     *
     * @param msg the ScribeMessage which triggered this action,
     *            it can be SUBSCRIBE/UNSUBSCRIBE msg, or null if application 
     *            on top of Scribe called addChild()/removeChild()
     *
     * @param data the data to be sent with the AckOnSubscribe Message
     */
    public void childObserver(NodeHandle child, NodeId topicId, boolean wasAdded, ScribeMessage pmsg, Serializable data)
    {
	Credentials cred = getCredentials();
	SendOptions opt = getSendOptions();
	Topic topic = getTopic(topicId);

	if(wasAdded){
	    // child was added
	    if( m_ackOnSubscribeSwitch ) {
		/** 
		 * Send a AckOnSubscribeMessage to the new subscriber so that
		 * it can set its parent pointer and reset its parentHandler.
		 */
		
		//System.out.println("DEBUG :: Sending ACK_ON_SUBSCRIBE from "+this.getNodeId()+" for topic "+topicId+" to child "+child.getNodeId());
		ScribeMessage amsg = makeAckOnSubscribeMessage(topicId, cred, data);
		routeMsgDirect( child, amsg, cred, opt );
	    }
	    
	    //notify applications about addition of this child, even if this
	    // addition action was prompted by application
	    IScribeApp[] apps = topic.getApps();
	    for ( int i=0; i<apps.length; i++ ) {
		if(pmsg != null)
		    apps[i].subscribeHandler(topicId, child, true, pmsg.getData());
		else
		    apps[i].subscribeHandler(topicId, child, true, null);

	}
	    
	    
	}
	else {
	    // child was removed

	    // only if we have no subscribing apps & if we have no children
	    // then send the unsubscribe message to the parent
	    if ( !topic.hasSubscribers() && !topic.hasChildren() ) {
		// tell multicast tree parent to remove local node
		NodeHandle parent = topic.getParent();

		if ( parent != null ) {

		    //make a new message and send this thru scribe
		    ScribeMessage msg = makeUnsubscribeMessage( topicId, cred );
		    // msg.setData( this.getData() );

		    // send directly to parent
		    routeMsgDirect( parent, msg, cred, opt );

		    //we no longer need the topic and is good to remove it
		    topic.removeFromScribe();
		}
		else {
		    // if parent unknown then set waiting flag and wait until 
		    // first event arrives

		    // make sure it is not Topic manager
		    if( topic.isTopicManager() ){
			topic.removeFromScribe();
		    }
		    else{
			topic.waitUnsubscribe( true );
		    }
		}
	    }

	    //notify applications about the removal of this child, even if
	    //this child removal action was prompted by some application
	    IScribeApp[] apps = topic.getApps();
	    for ( int i=0; i<apps.length; i++ ) {
		if(pmsg != null)
		    apps[i].subscribeHandler(topicId, child, false, pmsg.getData());
		else
		    apps[i].subscribeHandler(topicId, child, false, null);

	    }
	}


    }
    


    /** 
     * This returns the number of children in this 
     * topic's multicast subtree rooted at the local node. 
     *
     * @param topicId
     * The id of the topic.
     *
     * @return the number of children of local node for this topic
     *         if topic exists, else -1
     */
    public int numChildren(NodeId topicId)
    {
	Vector children = getChildren(topicId);

	if(children != null)
	    return children.size();
	else
	    return -1;
    }


    /**
     * Add a node as a child in the children table for
     * a topic.
     *
     * @param child  the child to be added
     *
     * @param topicId the topic for which this child is added
     *
     * @return true if operation was successful, false otherwise
     *
     */
    public boolean addChild(NodeHandle child, NodeId topicId)
    {
	Topic topic = getTopic(topicId);
       
	if(topic != null)
	    return topic.addChild(child, null);
	else
	    return false;
    }

    /**
     * Add a node as a child in the children table for
     * a topic. Also, takes in a serializable data object
     * to be propogated along with the ACK to the child.
     *
     * @param child  the child to be added
     *
     * @param topicId the topic for which this child is added
     *
     * @param data Serializable data
     *
     * @return true if operation was successful, false otherwise
     *
     */
    public boolean addChild(NodeHandle child, NodeId topicId, Serializable data)
    {
	Topic topic = getTopic(topicId);
       
	if(topic != null)
	    return topic.addChild(child, null, data);
	else
	    return false;
    }


    /**
     * Removes a node as a child from the children table for
     * a topic.
     *
     * @param child  the child to be removed
     *
     * @param topicId the topic for which this child is removed
     *
     * @return true if operation was successful, false otherwise
     *
     */
    public boolean removeChild(NodeHandle child, NodeId topicId)
    {
	Topic topic = getTopic(topicId);
       
	if(topic != null)
	    return topic.removeChild(child, null);
	else
	    return false;
    }

    /**
     * Notifies the applications on top of scribe who are interested
     * in knowing the events of implicit topic creation.
     *
     * @param topicId topicId for which Topic data structure is 
     *        created (implicitly)
     */
    public void notifyScribeObservers(NodeId topicId){
	IScribeObserver app;
	Iterator it = m_scribeObservers.iterator();

	while(it.hasNext()){
	    app = (IScribeObserver)it.next();
	    app.update((Object)topicId);
	}
    }

    /**
     * Returns the pastryNode for this application.
     * 
     * @return the pastryNode
     */
    public PastryNode getPastryNode(){
	return thePastryNode;
    }
}










