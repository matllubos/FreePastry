package rice.scribe.testing;

import rice.pastry.*;
import rice.pastry.security.*;
import rice.scribe.*;
import rice.scribe.messaging.*;
import rice.pastry.direct.*;

import java.util.*;

/**
 * Application used by the Regreesion test suites. It runs over a Scribe node
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
    
    public ScribeRegrTestApp( PastryNode node, Credentials cred ) {
	m_scribe = new Scribe( node, this, cred );
	m_credentials = cred;
    }

    /**
     * This makes sure that if the current app was subscribed to a topic,
     * it received all the messages published to it.
     */
    public boolean verifyApplication( List topics, MRTracker trk ) {
	Iterator it = topics.iterator();
	NodeId tid;

	while( it.hasNext() ) {
	    tid = (NodeId)it.next();
	    if( !m_tracker.isSubscribed( tid ) )
		continue;
	    if( trk.getMessagesReceived( tid ) != 
		m_tracker.getMessagesReceived( tid ) ) {
		System.err.print( "Node:" + getNodeId() + " doesnt verify ");
		int sent, rec;
		sent = trk.getMessagesReceived( tid );
		rec = m_tracker.getMessagesReceived( tid );
		System.err.println( " sent: " + sent + " rec " + rec  + tid );
	    }
	    else {
		System.out.print( "Node:" + getNodeId() + " verifies ");
		int sent, rec;
		sent = trk.getMessagesReceived( tid );
		rec = m_tracker.getMessagesReceived( tid );
		System.out.println( " sent: " + sent + " rec " + rec + tid );
	    }
	}
	return true;
    }

    /**
     * up-call invoked by scribe when a publish message is 'delivered'.
     */
    public void receiveMessage( ScribeMessage msg ) {
	m_tracker.receivedMessage( msg.getTopicId() );
    }

    /**
     * up-call invoked by scribe when a publish message is forwarded through
     * the multicast tree.
     */
    public void forwardHandler( ScribeMessage msg ) {

    }
    
    /**
     * up-call invoked by scribe when a node detects a failure from its parent.
     */
    public void faultHandler( ScribeMessage msg ) {

    }

    /**
     * up-call invoked by scribe when a node is added to the multicast tree.
     */
    public void subscribeHandler( ScribeMessage msg ) {

    }

    public NodeId generateTopicId( String topicName ) {
	return m_scribe.generateTopicId( topicName );
    }

    public PastryNode getPastryNode() {
	return (PastryNode)m_scribe.getNodeHandle();
    }

    public NodeId getNodeId() {
	return m_scribe.getNodeId();
    }

    /**
     * direct call to scribe for creating a topic from the current node.
     */
    public void create( NodeId topicId ) {
	m_scribe.create( topicId, m_credentials );
    }

    /**
     * direct call to scribe for publishing to a topic from the current node.
     */    
    public void publish( NodeId topicId ) {
	m_scribe.publish( topicId, null, m_credentials );
    }

    /**
     * direct call to scribe for subscribing to a topic from the current node.
     */    
    public void subscribe( NodeId topicId ) {
	m_scribe.subscribe( topicId, m_credentials );
	m_tracker.setSubscribed( topicId, true );
    }

    /**
     * direct call to scribe for unsubscribing to a topic from the current node
     */    
    public void unsubscribe( NodeId topicId ) {
	m_scribe.unsubscribe( topicId, m_credentials );
    }
    
    /**
     * Let's the current node know about a topic created in the whole scribe
     * network. This is not used for any other purposes than just verifying
     * after the regr test that all messages to all topics where received.
     */
    public void putTopic( NodeId tid ) {
	m_tracker.putTopic( tid );
    }
}

/**
 * MessagesReceivedTracker. Keeps track of all messages received on all topics
 * in the current node.
 */
class MRTracker 
{
    HashMap m_topics;
    MRTracker() {
	m_topics = new HashMap();
    }
    int getMessagesReceived( NodeId tid ) {
	return ((CSPair)getPair( tid )).getCount();
    }
    void receivedMessage( NodeId tid ) {
	((CSPair)getPair( tid )).receivedMessage();
    }
    void setSubscribed( NodeId tid, boolean is ) {
	CSPair pair = (CSPair)m_topics.get( tid );
	if( pair == null ) {
	    pair = new CSPair();
	    m_topics.put( tid, pair );
	}

	((CSPair)getPair( tid )).setSubscribed(is);
    }
    boolean isSubscribed( NodeId tid ) {
	return ((CSPair)getPair( tid )).isSubscribed();
    }
    private CSPair getPair( NodeId tid ) {
	CSPair pair = (CSPair)m_topics.get( tid );
	if( pair == null ) {
	    throw new Error( "Error in MRTracker" );
	}
	return pair;
    }
    void putTopic( NodeId tid ) {
	m_topics.put( tid, new CSPair() );
    }
}

/**
 * CountSubscribedPair keeps track for a single topic of how many messages have
 * been received from it and whether we are subscribed to it or not. 
 */
class CSPair {
    private int m_count;
    private boolean m_isSubscribed;
    CSPair() {
	m_count = 0;
	m_isSubscribed = false;
    }
    int getCount() { return m_count; }
    void receivedMessage() { m_count++; }
    boolean isSubscribed() { return m_isSubscribed; }
    void setSubscribed( boolean sub ) { m_isSubscribed = sub; }
}

