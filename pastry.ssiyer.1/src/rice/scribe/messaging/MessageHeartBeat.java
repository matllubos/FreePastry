package rice.scribe.messaging;

import rice.pastry.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.pastry.messaging.*;
import rice.scribe.*;
import rice.scribe.maintenance.*;

import java.io.*;

/**
 * HeartBeatMessage is used whenever a Scribe nodes wishes let its children 
 * know that it is still alive, so that the children need not do any repairing
 * of the multicas tree
 * 
 * @author Romer Gil 
 */


public class MessageHeartBeat extends ScribeMessage implements Serializable
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
	MessageHeartBeat( Address addr, NodeHandle source, 
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
	Credentials cred = scribe.getCredentials();
	SendOptions opt = scribe.getSendOptions();
	
	System.out.println("##########################################MESSAGEHEARTBEAT"
			   +"##########################################");

	if ( topic != null ) {
	    // take note of the parent for this topic and tell the failure 
	    // handler that the parent is ok
	    topic.setParent( m_source );
	    topic.restartParentHandler();

	    // if waiting to find parent, now send unsubscription msg
	    if ( topic.isWaitingUnsubscribe() ) {
		scribe.unsubscribe( topic.getTopicId(), cred );
		topic.waitUnsubscribe( false );
	    }
	}
	else {
	    ScribeMessage msg = 
		scribe.makeUnsubscribeMessage( m_topicId, cred );

	    NodeHandle nhandle = m_source ;
	    scribe.routeMsgDirect( nhandle, msg, cred, opt );
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
	handleForwardMessage( Scribe scribe,Topic topic ) {

	if( m_source.getNodeId().equals( scribe.getNodeId() ) ) {
	    return true;
	}

	return true;
    }

    public String toString() {
	return new String( "HEARTBEAT MSG:" + m_source );
    }
}

