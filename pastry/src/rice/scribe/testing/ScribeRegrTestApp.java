package rice.scribe.testing;


import rice.pastry.*;
import rice.pastry.security.*;
import rice.scribe.*;
import rice.scribe.messaging.*;
import rice.pastry.direct.*;

import java.util.*;


public class ScribeRegrTestApp implements IScribeApp
{
    
    private Credentials m_credentials;
    private Scribe m_scribe;
    MRTracker m_tracker = new MRTracker();
    
    public ScribeRegrTestApp( PastryNode node, Credentials cred, NetworkSimulator simulator ) {
	m_scribe = new Scribe( node, this, cred, simulator );
	m_credentials = cred;
    }

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

    public void receiveMessage( ScribeMessage msg ) {
	System.out.println( "Node: " + m_scribe.getNodeId() + 
			    " received a message " );
	m_tracker.receivedMessage( msg.getTopicId() );
    }

    public void forwardHandler( ScribeMessage msg ) {
	System.out.println( "Node: " + m_scribe.getNodeId() + 
			    " forwarding message" );
    }
    
    public void faultHandler( ScribeMessage msg ) {
	System.out.println( "Node: " + m_scribe.getNodeId() + 
			    " detected broken multicast tree" );
	System.out.println( m_scribe.getNodeHandle() + "-----------------------------------------------------------" );
    }

    public void subscribeHandler( ScribeMessage msg ) {
	System.out.println( "Node: " + m_scribe.getNodeId() + 
			    " adopted "+msg.getSource()+" to its multicast tree" );
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

    public void create( NodeId topicId ) {
	System.out.println( m_scribe.getNodeHandle() + " sending create req");
	m_scribe.create( topicId, m_credentials );
    }

    public void publish( NodeId topicId ) {
	System.out.println( m_scribe.getNodeHandle() + " sending pub req");
	m_scribe.publish( topicId, null, m_credentials );
    }

    public void subscribe( NodeId topicId ) {
	System.out.println( m_scribe.getNodeHandle() + " sending sub req"+topicId);
	m_scribe.subscribe( topicId, m_credentials );
	m_tracker.setSubscribed( topicId, true );
    }

    public void unsubscribe( NodeId topicId ) {
	System.out.println( m_scribe.getNodeHandle() + " sending uns req");
	m_scribe.unsubscribe( topicId, m_credentials );
    }
    
    public void putTopic( NodeId tid ) {
	m_tracker.putTopic( tid );
    }
}

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

