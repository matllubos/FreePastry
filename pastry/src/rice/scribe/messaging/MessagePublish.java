package rice.scribe.messaging;

import rice.pastry.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.pastry.messaging.*;
import rice.scribe.*;
import rice.scribe.security.*;
import rice.scribe.maintenance.*;

import java.io.*;
import java.util.*;

/**
 * PublishMessages is used whenever a Scribe nodes wishes to send events 
 * to a particular topic. The PublishMessage takes care of forwarding itself
 * to all the nodes children of the current node and calling the event handler.
 * 
 * @author Romer Gil 
 * @author Eric Engineer
 */


public class MessagePublish extends ScribeMessage implements Serializable
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
	MessagePublish( Address addr, NodeHandle source, 
			NodeId topicId, Credentials c ) {
	super( addr, source, topicId, c );
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
	handleDeliverMessage( Scribe scribe,Topic topic ) {
	Credentials cred = scribe.getCredentials();
	SendOptions opt = scribe.getSendOptions();
	NodeId topicId;

	if ( topic != null ) {
	    // take note of the parent for this topic and tell the failure 
	    // handler that the parent is ok
	    if( !topic.isTopicManager() ) {
		if(m_source != topic.getParent() && topic.getParent() != null){
		}
		topic.setParent( m_source );
		topic.restartParentHandler();
	    }
	    else {
		if( !scribe.getSecurityManager().
		    verifyCanPublish( m_source, m_topicId ) ) {

		    //bad permissions from publishing node
		    return;
		}
	    }

	    // send message to all children in multicast subtree
	    Iterator it = topic.getChildren().iterator();

	    ScribeMessage msg = scribe.makePublishMessage( m_topicId, cred );
	    msg.setData( this.getData() );
	    //	    m_source = scribe.getNodeHandle();
	    
	    //IScribeApp app = scribe.getScribeApp();
	    //  app.forwardHandler( this );
	    // Inform all interested applications
	    IScribeApp[] apps = topic.getApps();
	    for (int i=0; i<apps.length; i++) {
		apps[i].forwardHandler(this);
	    }
	  

	    while ( it.hasNext() ) {
		NodeHandle handle = (NodeHandle)it.next();
		scribe.routeMsgDirect( handle, msg, cred, opt );
	    }
	    
	    // if waiting to find parent, now send unsubscription msg
	    if ( topic.isWaitingUnsubscribe() ) {
		topicId = topic.getTopicId();
		scribe.unsubscribe( topicId, null, cred );
		topic.waitUnsubscribe( false );
	    }
	    
	    // if local node is subscriber of this topic, pass the event to
	    // the registered applications' event handlers
	    if ( topic.hasSubscribers() ) {
		// scribe.getScribeApp().receiveMessage( this );
		for ( int i=0; i<apps.length; i++ ) {
		    apps[i].receiveMessage( this );
		}		
	    }
	    
	}
	else { 
	    // if topic unknown, we do nothing to disregard it
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
	return true;
    }


    public String toString() {
	return new String( "PUBLISH MSG:" + m_source );
    }
}
