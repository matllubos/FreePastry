package rice.scribe.testing;

import rice.pastry.*;
import rice.pastry.join.*;
import rice.pastry.direct.*;
import rice.pastry.messaging.*;
import rice.scribe.*;
import rice.pastry.security.*;

import java.util.*;
import java.io.*;
import java.security.*;

/**
 * ScribeRegrTest
 *
 * a regression test suite for pastry.
 *
 * @author Romer Gil
 */
public class ScribeRegrTest
{
    private DirectPastryNodeFactory m_factory;
    private NetworkSimulator m_simulator;
    
    private LinkedList m_scribeNodes;
    public TreeMap m_scribeNodesSorted;
    //    private Vector m_rtApps;

    private Vector m_topics;
    private MRTracker m_tracker;
    
    private Random rng;
    
    public Message m_lastMsg;
    public NodeId.Distance m_lastDist;
    
    // constructor
    
    public ScribeRegrTest() {
	m_factory = new DirectPastryNodeFactory();
	m_simulator = m_factory.getNetworkSimulator();
	
	m_scribeNodes = new LinkedList();
	m_scribeNodesSorted = new TreeMap();
	//	rtApps = new Vector();
	rng = new Random();
	m_tracker = new MRTracker();
	m_topics = new Vector();
    }
    
    public void makeScribeNode() {
	PastryNode pnode = new PastryNode(m_factory);

	Credentials cred = new PermissiveCredentials();

	ScribeRegrTestApp app = new ScribeRegrTestApp( pnode, cred );

	m_scribeNodes.add(app);
	m_scribeNodesSorted.put(app.getNodeId(),app);
	
	int n = m_scribeNodes.size();
	
	if (n > 1) {
	    ScribeRegrTestApp otherApp = 
		(ScribeRegrTestApp)m_scribeNodes.get(n - 2);
	    PastryNode other = (PastryNode)otherApp.getPastryNode();
	    
	    pnode.receiveMessage(new InitiateJoin(other));
	}
    }

    public boolean simulate() { 
	return m_simulator.simulate(); 
    }
    
    /**
     * Main entry point for the regression test suite.
     */
    public static void main(String args[]) {
	ScribeRegrTest st = new ScribeRegrTest();
	int n, m, t, i;

	//n #nodes, m #messages per topic, t #topics
	n = 100;
	m = 10;
	t = 3;
	
	for( i = 0; i < n; i++ ) {
	    st.makeScribeNode();
	    while (st.simulate());
	}

	st.doTheTesting(n,m,t);

	System.out.println(n + " nodes constructed");
	System.gc();
    }

    public void doTheTesting( int nodes, int msgs, int topics ) {
	int i, j, k, subs, node, topic;
	NodeId tid;
	ScribeRegrTestApp app;

	//create 'topics' number of topics, from randomly selected nodes.
	for( i = 0; i < topics; i++ ) {
	    tid = generateTopicId( new String( "ScribeTest" + i ) );
	    node = rng.nextInt( nodes );
	    create( node, tid );
	    //let all the apps know about the topic. For the regr.This has 
	    //nothing to do with the actual scribe api is just stuff that is 
	    //done to verify correctness.
	    for( j = 0; j < nodes; j++ ) {
		app = (ScribeRegrTestApp)m_scribeNodes.get( j );
		app.putTopic( tid );
	    }
	    //now add the topics to the regr test. again just for testing. 
	    m_topics.add( tid );
	    m_tracker.setSubscribed( tid, true );

	    //now subscribe a random number of nodes to the topic we are 
	    //lookin at
	    subs = rng.nextInt( nodes );
	    for( j = 0; j < subs; j++ ) {
		subscribe( rng.nextInt( nodes ), tid );
	    }
	}

	//start publishing stuff to all the topics, selected at random.
	for( i = 0; i < topics; i++ ) {
	    for( j = 0; j < msgs; j++ ) {
		topic = rng.nextInt( topics );
		node = rng.nextInt( nodes );
		tid = (NodeId)m_topics.get( topic );
		publish( node, tid );
		m_tracker.receivedMessage( tid );
	    }
	}

	//verifying that what we sent was received (nothing more and nothing 
	//less) by the correct nodes 
	for( i = 0; i < nodes; i++ ) {
	    app = (ScribeRegrTestApp)m_scribeNodes.get( i );
	    app.verifyApplication( m_topics, m_tracker );
	}

    }

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

    //publish a msg from one of the test apps that we are keeping in the suite.
    private void publish( int node, NodeId tid ) {
	ScribeRegrTestApp app = (ScribeRegrTestApp)m_scribeNodes.get( node );
	app.publish( tid );
    }

    //subscribe one of the suite apps to topic tid
    private void subscribe( int node, NodeId tid ) {
	ScribeRegrTestApp app = (ScribeRegrTestApp)m_scribeNodes.get( node );
	app.subscribe( tid );
    }

    //unsubscribe one of the suite apps to topic tid
    private void unsubscribe( int node, NodeId tid ) {
	ScribeRegrTestApp app = (ScribeRegrTestApp)m_scribeNodes.get( node );
	app.unsubscribe( tid );
    }

    //create a topic tid from a given app.
    private void create( int node, NodeId tid ) {
	ScribeRegrTestApp app = (ScribeRegrTestApp)m_scribeNodes.get( node );
	app.create( tid );
    }
}
