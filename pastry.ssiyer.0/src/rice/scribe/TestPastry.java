package rice.scribe;

import rice.pastry.*;
import rice.pastry.direct.*;
import rice.pastry.standard.*;
import rice.pastry.join.*;
import rice.pastry.client.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;

import java.util.*;
import java.security.*;

/**
 * TestPastry
 *
 * a regression test suite for pastry.
 *
 * @author andrew ladd
 * @author peter druschel
 */

public class TestPastry {
    private DirectPastryNodeFactory m_factory;
    private NetworkSimulator m_simulator;
    
    private LinkedList m_nodes;
    private LinkedList m_on;
    
    // constructor
    
    public TestPastry() {
	m_nodes = new LinkedList();
	m_on = new LinkedList();
	m_factory = new DirectPastryNodeFactory();

	m_simulator = m_factory.getNetworkSimulator();
    }
    
    public void makeScribeNode() {
	PastryNode pnode = new PastryNode(m_factory);
	System.out.println("created " + pnode);

	TestPastryApp app = new TestPastryApp( pnode );
	//		RegrTestApp app = new RegrTestApp( pnode );

	m_on.addLast( pnode );
	m_nodes.addLast(app);
	int n = m_nodes.size();
	
	if (n > 1) {
	    TestPastryApp otherApp = (TestPastryApp)m_nodes.get(n - 2);
	    //	    RegrTestApp otherApp = (RegrTestApp)m_nodes.get(n - 2);
	    PastryNode other = (PastryNode)m_on.get(n-2);
	    //	    System.out.println( "From " + other );
	    pnode.receiveMessage(new InitiateJoin(other));
	}
    }

    public NodeId generateNodeId( String topicName ) { 
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

    public void sendMsgFrom( int node, NodeId targetId ) {
	if( node < 0 || node >= m_nodes.size() ) {
	    System.out.println( "Array out of bounds in sendMsgFrom" );
	    return;
	}

	TestPastryApp app = (TestPastryApp)m_nodes.get( node );
	//	RegrTestApp app = (RegrTestApp)m_nodes.get( node );
	System.out.println( "Trying to send message to key: " + targetId );
	System.out.println( "From " + app.getPastryNode() );

	app.sendMsg( targetId );
    }

    public void printNodeId( int node ) {
	if( node < 0 || node >= m_nodes.size() ) {
	    System.out.println( "Array out of bounds in sendMsgFrom" );
	    return;
	}

	TestPastryApp app = (TestPastryApp)m_nodes.get( node );
	//	RegrTestApp app = (RegrTestApp)m_nodes.get( node );
	System.out.println( "Printing node: " + app.getNodeId() );
    }

    public NodeId getNodeId( int index ) {
	TestPastryApp app = (TestPastryApp)m_nodes.get( index );
	return app.getNodeId();
    }

    public boolean simulate() { return m_simulator.simulate(); }

     public static void main(String args[]) {
	TestPastry tp = new TestPastry();
	int i;
	
	int n = 200;
	
	for (i=0; i<n; i++) {
	    tp.makeScribeNode();
	    while (tp.simulate());
	}

	NodeId targetId = tp.generateNodeId( "Romer" );

	
	for( i = 0; i < n; i++ ) {
	    tp.printNodeId( i );
	}

	
	tp.sendMsgFrom( 199, targetId );

	tp.sendMsgFrom( 198, tp.getNodeId(199) );
	while (tp.simulate());
	
	System.out.println(n + " nodes constructed");
	System.gc();
    }
}

