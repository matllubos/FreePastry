package rice.scribe.messaging;

import rice.pastry.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.pastry.messaging.*;
import rice.scribe.*;
import rice.scribe.maintenance.*;

import java.io.*;

/**
 * MessageUnsubscribe is used whenever a Scribe node wishes to unsubscribe 
 * from a topic. 
 * 
 * @author Romer Gil 
 * @author Eric Engineer
 */


public class MessageUnsubscribe extends ScribeMessage implements Serializable
{
    /**
     * Contructor
     *
     * @param addr the address of the scribe receiver.
     * @param source the node generating the message.
     * @param topicId the topic to which this message refers to.
     * @param c the credentials associated with the mesasge.
     */
    public 
	MessageUnsubscribe( Address addr, NodeHandle source, 
			    NodeId tid, Credentials c ){
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

	NodeHandle handle = m_source;

	if ( topic != null ) {
	    // remove source node from chilren if it isnt us
	    if( m_source.getNodeId().equals( scribe.getNodeId() ) ) {
		topic.removeChild( handle );
	    }
	    
	    // only if we are not subscribed, if we have no children then send
	    // the unsubscribe message to the parent
	    if ( !topic.isSubscribed() && !topic.hasChildren() ) {
		// tell multicast tree parent to remove local node
		NodeHandle parent = topic.getParent();

		if ( parent != null ) {
		    Credentials cred = scribe.getCredentials();
		    SendOptions opt = scribe.getSendOptions();

		    //make a new message and send this thru scribe
		    ScribeMessage msg = 
			scribe.makeUnsubscribeMessage( m_topicId, cred );
		    msg.setData( this.getData() );

		    // send directly to parent
		    scribe.routeMsgDirect( parent, msg, cred, opt );

		    //we no longer need the topic and is good to remove it
		    topic.removeFromScribe();
		}
		else {
		    // if parent unknown set waiting flag and wait until 
		    // first event arrives
		    topic.waitUnsubscribe( true );
		}
	    }
	}
	else {
	} // if topic unknown, error
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
	handleForwardMessage( Scribe scribe, Topic topic ) {
	return true;
    }

    public String toString() {
	return new String( "UNSUBSCRIBE MSG:" + m_source );
    }
}
