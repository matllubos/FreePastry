package rice.scribe.messaging;

import rice.pastry.*;
import rice.pastry.security.*;
import rice.pastry.messaging.*;
import rice.scribe.*;
import rice.scribe.security.*;
import rice.scribe.maintenance.*;

import java.io.*;

/**
 * MessageCreate objects are created whenever a Scribe node wants to create a
 * new topic. The message takes care of instantiating the appropriate data 
 * structures on the current node to keep track of the topic. 
 * 
 * @author Romer Gil 
 * @author Eric Engineer
 */


public class MessageCreate extends ScribeMessage implements Serializable
{
    /**
     * Constructor.
     *
     * @param addr the address of the scribe receiver.
     * @param source the node generating the message.
     * @param topicId the topic to which this message refers to.
     * @param c the credentials associated with the mesasge.
     */
    public MessageCreate( Address addr, NodeHandle source, 
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

	if (!scribe.getSecurityManager().verifyCanCreate(m_source,m_topicId)) {
	    //bad permissions from source node
	    return;
	}

	if( topic == null ) {
	    topic = new Topic( m_topicId, scribe );
	}
	
	topic.addToScribe();
	topic.topicManager( true );
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
	return new String( "CREATE MSG:" + m_source );
    }
}
