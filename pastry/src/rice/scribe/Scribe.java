package rice.scribe;

import rice.pastry.client.*;
import rice.pastry.security.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.pastry.*;
import rice.pastry.direct.*;

import java.util.*;
import java.security.*;

import rice.scribe.messaging.*;
import rice.scribe.security.*;
import rice.scribe.maintenance.*;

/**
 * Main entry point for the Scribe System
 *
 * @author Romer Gil
 * @author Eric Engineer
 */
public class Scribe extends PastryAppl implements IScribe
{
    /*
     * Member fields
     */
   

    /**
     * Set of topics on the local scribe node. 
     */
    HashMap m_topics = null;

    private static class ScribeAddress implements Address {
	private int myCode = 0x8aec747c;
	
	public int hashCode() { return myCode; }

	public boolean equals(Object obj) {
	    return (obj instanceof ScribeAddress);
	}
    }

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
     *
     */
    ScribeScheduler m_scheduler;

    /**
     * Constructor.
     *
     * @param pn the pastry node that client will attach to.
     */
    public Scribe( PastryNode pn, Credentials cred ) {
	super( pn );
	m_topics = new HashMap();
	m_sendOptions = new SendOptions();
	m_securityManager = new PSecurityManager();
	m_scheduler = new ScribeScheduler( this, 20*1000, 30*1000 );
	m_credentials = cred;
	//	m_scribeApp = app;
	
	if( cred == null ) {
	    m_credentials = new PermissiveCredentials();
	}

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
     */
    public void create( NodeId topicId, Credentials cred ) {
	ScribeMessage msg = makeCreateMessage( topicId, cred );

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
    public void subscribe( NodeId topicId, IScribeApp subscriber, Credentials cred ) {
	Topic topic = (Topic) m_topics.get( topicId );
	
	if ( topic == null ) {
	    topic = new Topic( topicId, this );
	    m_topics.put( topicId, topic );
	}
	
	// Register application as a subscriber for this topic
	topic.subscribe( subscriber );
	topic.restartParentHandler();
	
	ScribeMessage msg = makeSubscribeMessage( topicId, cred );
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
     * @param   msg           
     * the information that is to be published, encapsulated in a Message 
     * object.
     */
    public void publish( NodeId topicId, Object obj, Credentials cred ) {
	ScribeMessage msg = makePublishMessage( topicId, cred );

	msg.setData( obj );
	this.routeMsg( topicId, msg, cred, m_sendOptions );
    }

    /**
     * Generate a unique id for the topic, which will determine its rendez-vous
     * point. THIS STILL NEEDS TO BE IMPLEMENTED!!!
     *
     * @param topicName 
     * the name of the topic
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
     * Returns the credentials of this client. Since all the functions in the 
     * API have a reference to a credentials object this is probably not 
     * necessary.
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
	/*
	System.out.println( "Node: " + getNodeId() + " enroute" );
	System.out.println( "Node: " + target + " enroute to target" );
	System.out.println( "Node: " + nextHop + " enroute nextHop" );
	*/
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
	    Iterator it = m_topics.values().iterator();
	    Topic topic;
	    NodeId topicId, myNodeId;
	    NodeId.Distance myDistance, distance;
	    Credentials c = m_credentials;

	    myNodeId = this.getNodeId();

	    while( it.hasNext() ) {
		topic = (Topic)it.next();
		//for all topics, if we are manager then we do more processing
		if( topic.isTopicManager() ) {
		    topicId = topic.getTopicId();
		    myDistance = myNodeId.distance( topicId );
		    distance = nid.distance( topicId );
		    
		    /*the distance is really the magnitude of the subtraction
		     *of the two nodes. If our distance is greater than the 
		     *new node's then the new node should be manager
		     */
		    if( myDistance.compareTo( distance ) > 0 ) {
			//WE HAVE GOT A NEW TOPIC MANAGER!!! YAY
			topic.topicManager( false );

			//please send a subscribe message
			ScribeMessage msg = makeSubscribeMessage( topicId, c );
			this.routeMsg( topicId, msg, c, m_sendOptions );
		    }
		}
	    }
	}
	else {
	    //I dont think we care if a node drops.
	}
    }

    /**
     * Returns the handle of the node inside the Scribe system.
     *
     * @return the node handle.
     */
    public NodeHandle getNodeHandle() {
	return thePastryNode.getLocalHandle();
    }

    /**
     * Returns the application using the Scribe system.
     *
     * @return the scribe application.
     */
    // public IScribeApp getScribeApp() { 
    //	return m_scribeApp; 
    //}

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
     * @param tid the topic id the message reffers to.
     * @param c the credentials that will be associated with the message
     *
     * @return the ScribeMessage.
     */
    public ScribeMessage makeSubscribeMessage( NodeId tid, Credentials c ) {
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
     *
     * @return the ScribeMessage.
     */
    public ScribeMessage makeCreateMessage( NodeId tid, Credentials c ) {
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
	return new MessageHeartBeat( m_address, this.thePastryNode.getLocalHandle(), tid, c );
    }
}










