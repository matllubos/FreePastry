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

	/* If the node is already up, then the application should itself
	 * start the maintenance thread "here", otherwise its started in 
	 * notifyReady().
	 */
	if(pn.isReady()){
	    if(thePastryNode instanceof DistPastryNode) {
		new Thread(new DistScribeMaintenanceThread(this, 10)).start();
	    } 
	}
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
     * This is called when the underlying pastry node is ready. With regard to 
     * Scribe, we start the tree maintenance thread when the pastry network is a 
     * Distributed network instead of a simulate network.
     */
    public void notifyReady() {
	if(thePastryNode instanceof DistPastryNode) {
	    new Thread(new DistScribeMaintenanceThread(this, 10)).start();
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
     */
    public void scheduleHB(){
	m_maintainer.scheduleHB();
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
     * Creates a topic if the credentials are valid.  Nodes must then subscribe
     * to this topic in order to get information published to it.
     *
     * @param    cred
     * the credentials of the entity creating the topic  
     *
     * @param    topicID       
     * the ID of the topic to be created
     *
     */
    public void create( NodeId topicId, Credentials cred) {
	ScribeMessage msg = makeCreateMessage( topicId, cred);

	this.routeMsg( topicId, msg, cred, m_sendOptions );
    }
    
    /**
     * Subscribe to topic specified by topicId. When a node becomes subscribed 
     * to a topic, it receives all messages published to it.
     *
     * @param    cred   
     * the credentials of the entity subscribing to the topic
     *
     * @param    topicID        
     * the ID of the topic to subscribe to.
     *
     * @param    subscriber
     * The application subscribing to the topic
     */
    public void subscribe( NodeId topicId, IScribeApp subscriber, 
			   Credentials cred ) {
	Topic topic = (Topic) m_topics.get( topicId );
	
	if ( topic == null ) {
	    topic = new Topic( topicId, this );
	    // add topic to known topics
	    topic.addToScribe();
	}
	
	// Register application as a subscriber for this topic
	topic.subscribe( subscriber );

	// If we already have a parent, we dont send a subscribe mesg.
	if( topic.getParent() == null){
	    ScribeMessage msg = makeSubscribeMessage( topicId, cred);
	    topic.postponeParentHandler();
	    this.routeMsg( topicId, msg, cred, m_sendOptions );
	}
    }
    
    /**
     * Unsubscribe from a topic.  After a node is unsubscribed from a topic, it
     * will no longer receive messages from the topic.
     *
     * @param    cred    
     * the credentials of the entity unsubscribing from the topic
     *
     * @param    topicID        
     * the ID of the topic to be unsubscribed from.
     *
     * @param    subscriber
     * The application unsubscribing from the topic. Use null if 
     * not directly called by application
     */
    public void unsubscribe( NodeId topicId, IScribeApp subscriber, Credentials cred ) {
	Topic topic = (Topic) m_topics.get( topicId );
	
	// If topic unknown, must give an error
	if ( topic == null ) {
	    return;
	}
	
	// unregister application as subscriber for this topic
	if (subscriber != null)
	    topic.unsubscribe( subscriber );

	// send unsubscribe message if no more applications registered
	if ( !topic.hasSubscribers() ) {
	    ScribeMessage msg = makeUnsubscribeMessage( topicId, cred );
	    this.routeMsgDirect( thePastryNode.getLocalHandle(), msg, cred,
				 m_sendOptions );
	}
    }
    
    /**
     * Publish information to a topic.  Data will be delivered to ALL nodes 
     * that are subscribed to the topic. The message will trickle from
     * the root of the multicast tree for the topic DOWN the tree, with each
     * node sending this message to its children for the topic.
     *
     * @param   cred    
     * the credentials of the entity publishing to the topic.
     *
     * @param   topicID         
     * the ID of the topic to publish to.
     *
     * @param   obj
     * The information that is to be published.
     * This should be serializable.
     */
    public void publish( NodeId topicId, Object obj, Credentials cred ) {
	ScribeMessage msg = makePublishMessage( topicId, cred );

	msg.setData( obj );
	this.routeMsg( topicId, msg, cred, m_sendOptions );
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
			topic.setParent(null);
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
        return new MessageAckOnSubscribe( m_address, this.thePastryNode.getLocalHandle(), tid, c);
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

	synchronized(m_topics){
		Iterator it = m_topics.values().iterator();		
		while( it.hasNext()){
		    topicVector.add((Topic)it.next());
		}
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
	synchronized(m_distinctChildrenTable){
	    set = m_distinctChildrenTable.keySet();
	    Iterator it = set.iterator();
	    while(it.hasNext()){
		result.addElement((NodeHandle)it.next());
	    }
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
	synchronized(m_distinctChildrenTable){
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
	
	synchronized(m_distinctChildrenTable){
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
	synchronized(m_distinctChildrenTable){
	    entry = (HashTableEntry)m_distinctChildrenTable.get(child);
	    topics_clone = (Vector)entry.topicList.clone();
	}
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
	synchronized(m_distinctParentTable){
	    set = m_distinctParentTable.keySet();
	    Iterator it = set.iterator();
	    while(it.hasNext()){
		result.addElement((NodeHandle)it.next());
	    }
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
	synchronized(m_distinctParentTable){
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

	synchronized(m_distinctParentTable){
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
	synchronized(m_distinctParentTable){
	    entry = (HashTableEntry)m_distinctParentTable.get(parent);
	    topics_clone = (Vector)entry.topicList.clone();
	}
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
	synchronized(m_distinctParentTable){
	    entry = (HashTableEntry)m_distinctParentTable.get(parent);
	    fingerprint = entry.getFingerprint();
	}
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
	synchronized(m_distinctChildrenTable){
	    entry = (HashTableEntry)m_distinctChildrenTable.get(child);
	    fingerprint = entry.getFingerprint();
	}
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
	synchronized(m_alreadySentHBNodes){
	    result = m_alreadySentHBNodes.add((NodeId)childId);
	}
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

	synchronized(m_alreadySentHBNodes){
	    Iterator it = m_alreadySentHBNodes.iterator();
	    while(it.hasNext()){
		childId = (NodeId) it.next();
		if( ! list.contains(childId))
		    list.addElement(childId);
	    }
	}
	return list;
    }

    /** 
     * The set is cleared so that the set contains
     * only those nodes to which Publish message was sent in
     * last HeartBeat period.
     */
    public void clearAlreadySentHBNodes(){
	synchronized(m_alreadySentHBNodes){
	    m_alreadySentHBNodes.clear();
	}
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
    
}












