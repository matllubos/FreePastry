package rice.scribe.testing;

import rice.pastry.*;
import rice.pastry.security.*;
import rice.scribe.*;
import rice.scribe.messaging.*;
import rice.pastry.direct.*;

import java.util.*;

/**
 * Application used by the Regression test suites. It runs over a Scribe node
 * but it is notified from the whole network of what topics have been created
 * so that it can verify correctness after execution.
 *
 * @author Romer Gil
 */
public class ScribeRegrTestApp implements IScribeApp
{
    
    private Credentials m_credentials;
    private Scribe m_scribe;
    MRTracker m_tracker = new MRTracker();
    int m_app = 0; // used to id the app within a node
    Random m_rng = new Random();
    
    public ScribeRegrTestApp( PastryNode node, Scribe scribe, int app, Credentials cred ) {
	m_scribe = scribe;
	m_credentials = cred;
	m_app = app;
    }

    /**
     * This makes sure that if the current app was subscribed to a topic,
     * it received all the messages published to it.
     */
    public boolean verifyApplication( List topics, MRTracker trk, MRTracker trkAfter ) {
	Iterator it = topics.iterator();
	NodeId tid;

	boolean ok = true;

	while( it.hasNext() ) {
	    tid = (NodeId)it.next();
	    
	    if (!m_tracker.knows(tid)) continue;
	    
	    int shouldReceive = trk.getMessagesReceived( tid );
	    int received = m_tracker.getMessagesReceived( tid );
	    if( m_tracker.isSubscribed( tid ))
		shouldReceive += trkAfter.getMessagesReceived( tid );
	    
	    if ( shouldReceive != received ) {
		System.out.print( "**** Node:" + getNodeId() + " App:"
				  + m_app + " does *NOT* verify topic " + tid );
		ok = false;
	    }
	    else
		System.out.print( "Node:" + getNodeId() + "App:"
				  + m_app +  " verifies topic " + tid);
	    
	    System.out.println( " Should receive: " + shouldReceive 
				+ " Actually received: " + received );
	}
	return ok;
    }


    /**
     * up-call invoked by scribe when a publish message is 'delivered'.
     */
    public void receiveMessage( ScribeMessage msg ) {
	m_tracker.receivedMessage( msg.getTopicId() );
	/*
	System.out.println("Node:" + getNodeId() + " App:" 	
				+ m_app + " received msg: " + msg); 
	*/
    }

    /**
     * up-call invoked by scribe when a publish message is forwarded through
     * the multicast tree.
     */
    public void forwardHandler( ScribeMessage msg ) {
	/*
	System.out.println("Node:" + getNodeId() + " App:"
                                + m_app + " forwarding: "+ msg);
	*/
    }
    
    /**
     * up-call invoked by scribe when a node detects a failure from its parent.
     */
    public void faultHandler( ScribeMessage msg ) {
	/*
	System.out.println("Node:" + getNodeId() + " App:"
                                + m_app + " handling fault: " + msg);
	*/
    }

    /**
     * up-call invoked by scribe when a node is added to the multicast tree.
     */
    public void subscribeHandler( ScribeMessage msg ) {
	/*
	System.out.println("Node:" + getNodeId() + " App:"
                                + m_app + " child subscribed: " + msg);
	*/
    }

    public NodeId generateTopicId( String topicName ) {
	return m_scribe.generateTopicId( topicName );
    }

    public NodeId getNodeId() {
	return m_scribe.getNodeId();
    }

    /**
     * direct call to scribe for creating a topic from the current node.
     */
    public void create( NodeId topicId ) {
	/*
	System.out.println("Node:" + getNodeId() + " App:"
                                + m_app + " creating topic " + topicId );
	*/

	m_scribe.create( topicId, m_credentials );
    }

    /**
     * direct call to scribe for publishing to a topic from the current node.
     */    
    public void publish( NodeId topicId ) {
	/*
	System.out.println("Node:" + getNodeId() + " App:"
                                + m_app + " publishing on topic" + topicId );
	*/
	m_scribe.publish( topicId, null, m_credentials );
    }

    /**
     * direct call to scribe for subscribing to a topic from the current node.
     */    
    public void subscribe( NodeId topicId ) {
	/*
	System.out.println("Node:" + getNodeId() + " App:"
                                + m_app + " subscribing to topic " + topicId);
	*/
	m_scribe.subscribe( topicId, this, m_credentials );
	m_tracker.setSubscribed( topicId, true );
    }

    /**
     * direct call to scribe for unsubscribing a  topic from the current node
     * The topic is chosen randomly if null is passed and topics exist.
     */    
    public void unsubscribe(NodeId topicId) {
	if (topicId == null) {
	    NodeId[] topics = m_tracker.getSubscribedTopics();
	    if (topics.length == 0) return;
	    int tid = m_rng.nextInt(topics.length);
	    topicId = topics[tid];
	}
	/*
	System.out.println("Node:" + getNodeId() + " App:"
                                + m_app + " unsubscribing from topic " + topicId);
	*/
	m_scribe.unsubscribe( topicId, this, m_credentials );
	m_tracker.setSubscribed( topicId, false );
    }
    
    /**
     * Let's the current node know about a topic created in the whole scribe
     * network. This is not used for any other purposes than just verifying
     * after the regr test that all messages to all topics where received.
     */
    public void putTopic( NodeId tid ) {
	//	m_tracker.putTopic( tid );
    }
}







