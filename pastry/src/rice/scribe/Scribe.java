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
import rice.pastry.leafset.*;

import java.util.*;
import java.security.*;

import rice.scribe.messaging.*;
import rice.scribe.security.*;
import rice.scribe.maintenance.*;

/**
 * @(#) Scribe.java
 *
 * This is the Scribe Object which implements the IScribe interface 
 * (the external Scribe API).
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
     * each child, the list of topics in which it is a child.
     * Used to identify distinct chidren when sending HeartBeat messages,
     * so that we dont send a node multiple HeartBeat messages when it is
     * child for more than one topic.
     */

    protected Hashtable m_distinctChildrenTable;


    /**
     * Hashtable having mapping from a parent node -> list of topics for 
     * which the parent node is local node's parent in their corresponding
     * multicast trees. Whenever local node receives a HeartBeat message
     * from a node, it checks out all topics for which that node is its
     * parent. Then for all those topics, it postpones the parentHandler.
     * By having this data structure locally, we avoid sending lists
     * of topics for which a node is parent, which may be very large.
     *
     */
    protected Hashtable m_distinctParentTable;




    /**
     * Optimization on number of HeartBeat Messages sent. Each node keeps track
     * of children to which it has sent a publish message in last HeartBeat
     * period. When this HeartBeat period expires, it need not send an 
     * explicit HeartBeat message to these 'marked' children, as publish message
     * is an implicit keep-alive message from parent that it is alive.
     */
    protected Set m_alreadySentHBNodes;


    /**
     * The AckOnSubscribe switch for this scribe.
     * It  activates the immediate sending of 
     * ACK by a node when it receives a SUBSCRIBE message
     * from its child for a topic. Otherwise, this 
     * immediate ACK is not sent.
     * Default is true.
     * 
     */
    public  boolean m_ackOnSubscribeSwitch = true;


    /**
     * The receiver address for the scribe system.
     */
    protected static Address m_address = new ScribeAddress();

    /**
     * The SendOptions object to be used for all messaging through Pastry
     */
    protected SendOptions m_sendOptions = null;

    /**
     * The SendOptions object to be used for all messaging through Pastry
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

    private static class HashTableEntry {
	private Vector topicList;
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
     * Send heartbeat messages to its children for all the topics on this
     * local scribe node. This method should be called by the driver 
     * periodically. The failure of receive a threshold value of such
     * heartbeat messages from the parent for a particular topic triggers
     * a tree repair for that topic.
     */
    public void scheduleHB(){
	m_maintainer.scheduleHB();
    }

    /**
     * Returns true if the local node is currently the 
     * root(the node that is closest to the topicId) of the topic.
     * 
     * @return true if the local node is currently the root for the topic
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

	ScribeMessage msg = makeSubscribeMessage( topicId, cred);
	topic.postponeParentHandler();
	this.routeMsg( topicId, msg, cred, m_sendOptions );
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
     * that are subscribed to the topic and will be routed through ALL the 
     * nodes in the multicast tree.
     *
     * @param   cred    
     * the credentials of the entity publishing to the topic.
     *
     * @param   topicID         
     * the ID of the topic to publish to.
     *
     * @param   obj
     * the information that is to be published.
     */
    public void publish( NodeId topicId, Object obj, Credentials cred ) {
	ScribeMessage msg = makePublishMessage( topicId, cred );

	msg.setData( obj );
	this.routeMsg( topicId, msg, cred, m_sendOptions );
    }

    /**
     * Generate a unique id for the topic, which will determine its rendez-vous
     * point.
     *
     * @param topicName 
     * the name of the topic
     *
     * @return the topic id.
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

	if( wasAdded ) {
	    //if the node was added we must check if the new node should be
	    //topic manager for any of our topics.
	    Topic topic;
	    NodeId topicId, myNodeId;
	    Credentials c = m_credentials;
	    Vector topicVector = new Vector();
	    int i = 0;

	    myNodeId = this.getNodeId();

	    topicVector = getTopics();
	    
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
			ScribeMessage msg = makeSubscribeMessage( topicId, c);
			topic.postponeParentHandler();
			this.routeMsg( topicId, msg, c, m_sendOptions );
		    }
		}
	    }
	}
	else {
	    //We do not need to do anything when a node drops.
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
     *
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
     *
     * @return the ScribeMessage.
     */
    public ScribeMessage makePublishMessage( NodeId tid, Credentials c ) {
	return new MessagePublish( m_address, this.thePastryNode.getLocalHandle(), tid, c );
    }

    /**
     * Makes a heart-beat message using the current Pastry node as the source.
     *
     * @param tid the topic id the message reffers to.
     * @param c the credentials that will be associated with the message
     *
     * @return the ScribeMessage.
     */
    public ScribeMessage makeHeartBeatMessage( NodeId tid, Credentials c ) {
	return new MessageHeartBeat( m_address, this.thePastryNode.getLocalHandle(), tid, c);
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
     * @param tid is null 
     * @param c the credentials that will be associated with the message
     * @return the ScribeMessage.
     */
    public ScribeMessage makeRequestToParentMessage( NodeId tid, Credentials c ) {
        return new MessageRequestToParent( m_address, this.thePastryNode.getLocalHandle(), tid, c);
    }

     /**
     * Makes a ReplyFromParent message using the current Pastry node as the 
     * source.
     *
     * @param tid is null 
     * @param c the credentials that will be associated with the message
     * @return the ScribeMessage.
     */
    public ScribeMessage makeReplyFromParentMessage( NodeId tid, Credentials c ) {
        return new MessageReplyFromParent( m_address, this.thePastryNode.getLocalHandle(), tid, c);
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

	/**
	 * See if we already have it as a child for some other
	 * topic , if yes then add this topicId to list of topics
	 * for which this node is  our child.
	 */
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
    

    public void removeChildForTopic(NodeHandle child, NodeId topicId){
	/**
	 * See if we already have it as a child for some other
	 * topic , if yes then remove this topicId from the list of topics
	 * for which this node is  our child.
	 */
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
     * is a children.
     * @param child  The NodeHandle of child node.
     *
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
     * in multicast tree of all topics.
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
     * @param parent  The NodeHandle of parent node.
     *
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
     * has already send a HeartBeat message. (actually an 
     * implicit HeartBeat in form of Publish message)
     * @param childId NodeId of child.
     *
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
     * was sent (in form of Publish message) in last HeartBeat
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
     * The set is made empty so that the set contains
     * only those nodes to which Publish message was sent in
     * last HeartBeat period.
     */
    public void clearAlreadySentHBNodes(){
	synchronized(m_alreadySentHBNodes){
	    m_alreadySentHBNodes.clear();
	}
	return;
    }

    public NodeHandle getLocalHandle() {
	return thePastryNode.getLocalHandle();
    }


}












