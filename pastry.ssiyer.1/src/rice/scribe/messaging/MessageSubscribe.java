package rice.scribe.messaging;

import rice.pastry.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.pastry.messaging.*;
import rice.scribe.*;
import rice.scribe.security.*;
import rice.scribe.maintenance.*;

import java.io.*;

/**
 * MessageSubscribe is used whenever a Scribe node wants to subscrube itself 
 * to a topic. 
 * 
 * @author Romer Gil 
 * @author Eric Engineer
 */


public class MessageSubscribe extends ScribeMessage implements Serializable
{
    /**
     * Constructor
     *
     * @param addr the address of the scribe receiver.
     * @param source the node generating the message.
     * @param topicId the topic to which this message refers to.
     * @param c the credentials associated with the mesasge.
     */
    public 
	MessageSubscribe( Address addr, NodeHandle source, 
			  NodeId tid, Credentials c ) {
	super( addr, source, tid, c );
    }
    
    /**
     * This method is called whenever the scribe node receives a message for 
     * itself and wants to process it. The processing is delegated by scribe 
     * to the message.
     * 
     * @param scribe the scribe application.
     * @param topic the topic within the scribe application.
     */
    public void 
	handleDeliverMessage( Scribe scribe, Topic topic ) {
	//we know that we are topic manager because we received the topic msg
	topic.topicManager( true );

	if( !scribe.getSecurityManager().
	   verifyCanSubscribe( m_source, m_topicId ) ){

	    //bad permissions from source node
	    return;
	}

	if( topic == null ) {
	    /*If the topic is unknown just create it. It could be that the old
	      manager failed and its children want to repair the tree.
	     */
	    topic = new Topic( m_topicId, scribe );
	    topic.addToScribe();
	} 

	if ( m_source.getNodeId().equals( scribe.getNodeId() ) ) {
	    //	    topic.subscribe( true );
	}
	else {
	    topic.addChild( m_source );

	    //make the subscribe handler upcall to the scribe app
	    IScribeApp app = scribe.getScribeApp();
	    app.subscribeHandler( this );
	}
    }
    
    /**
     * This method is called whenever the scribe node forwards a message in 
     * the scribe network. The processing is delegated by scribe to the 
     * message.
     * 
     * @param scribe the scribe application.
     * @param topic the topic within the scribe application.
     *
     * @return true if the message should be routed further, false otherwise.
     */
    public boolean 
	handleForwardMessage(Scribe scribe, Topic topic ) {
	NodeId topicId = m_topicId;
	NodeHandle nhandle = m_source;
	Credentials cred = scribe.getCredentials();
	SendOptions opt = scribe.getSendOptions();
	
	if( !scribe.getSecurityManager().
	    verifyCanSubscribe( m_source, m_topicId ) ) {

	    //bad permissions from source node
	    return false;
	}

	if( m_source.getNodeId().equals( scribe.getNodeId() ) ) {
	    //This should not happen, we should probably throw an error
	    return true;
	}
	else {
	}
	
	if ( topic == null ) {
	    topic = new Topic( topicId, scribe );
	    
	    // add topic to known topics
	    topic.addToScribe();
	    
	    ScribeMessage msg = scribe.makeSubscribeMessage( m_topicId, cred );
	    
	    //set local node as msg src
	    //m_source = scribe.getNodeHandle();
	    
	    topic.restartParentHandler();

	    // join multicast tree by routing subscribe message thru pastry
	    scribe.routeMsg( m_topicId, msg, cred, opt );
	}
	
	// make the source a child for this topic
	topic.addChild( nhandle );

	IScribeApp app = scribe.getScribeApp();
	app.subscribeHandler( this );
	
	// stop routing the original message
	return false;
    }

    public String toString() {
	return new String( "SUBSCRIBE MSG:" + m_source );
    }
}

