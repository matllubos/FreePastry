package rice.scribe.testing;

import rice.pastry.*;
import rice.pastry.join.*;
import rice.pastry.direct.*;
import rice.pastry.messaging.*;
import rice.scribe.*;
import rice.pastry.security.*;

import java.util.*;
import java.io.*;

public class ScribeInteractiveTest
{
    private DirectPastryNodeFactory m_factory;
    private NetworkSimulator m_simulator;
    
    private LinkedList m_scribeNodes;
    public TreeMap m_scribeNodesSorted;
    //    private Vector m_rtApps;
    
    private Random rng;
    
    public Message m_lastMsg;
    public NodeId.Distance m_lastDist;
    private MRTracker m_tracker;
    
    // constructor
    
    public ScribeInteractiveTest() {
	m_factory = new DirectPastryNodeFactory();
	m_simulator = m_factory.getNetworkSimulator();
	
	m_scribeNodes = new LinkedList();
	m_scribeNodesSorted = new TreeMap();
	m_tracker = new MRTracker();
	//	rtApps = new Vector();
	rng = new Random();
    }
    
    public void makeScribeNode() {
	PastryNode pnode = new PastryNode(m_factory);
	System.out.println(m_scribeNodes.size()+":created " + pnode);

	Credentials cred = new PermissiveCredentials();

	ScribeRegrTestApp app = new ScribeRegrTestApp( pnode, cred, m_simulator );

	m_scribeNodes.add(app);
	m_scribeNodesSorted.put(app.getNodeId(),app);
	
	//	RegrTestApp rta = new RegrTestApp(pn,this);
	//	rtApps.addElement(rta);
	
	int n = m_scribeNodes.size();
	
	if (n > 1) {
	    ScribeRegrTestApp otherApp = 
		(ScribeRegrTestApp)m_scribeNodes.get(n - 2);
	    PastryNode other = (PastryNode)otherApp.getPastryNode();
	    
	    pnode.receiveMessage(new InitiateJoin(other));
	}
	
	//System.out.println("");
    }

    public boolean simulate() { 
	return m_simulator.simulate(); 
    }
    
    public static void main(String args[]) {
	ScribeInteractiveTest st = new ScribeInteractiveTest();
	
	int n = 100;
	
	for (int i=0; i<n; i++) {
	    st.makeScribeNode();
	    while (st.simulate());
	}

	boolean quit = false;
	BufferedReader input
          = new BufferedReader( new InputStreamReader(System.in));
	String command = null;

	while( !quit ) {
	    try {
		command = input.readLine();
	    } 
	    catch( Exception e ) {
		System.out.println( e );
	    }
	    quit = st.parseInput( command );
	    while (st.simulate());
	}

	System.out.println(n + " nodes constructed");
	System.gc();
    }

    private boolean parseInput( String in ) {
	StringTokenizer tokened = new StringTokenizer( in, " \t\n" );
	if( !tokened.hasMoreTokens() ) {
	    return false;
	}

	String token = tokened.nextToken();
	ScribeRegrTestApp app;
	int node;
	NodeId topicId;

	if( token.startsWith( "quit" ) ) {
	    return true;
	}
	else if ( token.startsWith( "cre" ) ) {
	    token = tokened.nextToken();
	    node = getNodeNumber( token );
	    if( node < 0 ) {
		System.out.println( "Bad Node Number" );
		return false;
	    }

	    token = tokened.nextToken( "\n" );
	    app = (ScribeRegrTestApp)m_scribeNodes.get( node );

	    topicId = app.generateTopicId( token );
	    app.create( topicId );
	    m_tracker.setSubscribed( topicId, true );
	}
	else if ( token.startsWith( "pub" ) ) {
	    token = tokened.nextToken();
	    node = getNodeNumber( token );
	    if( node < 0 ) {
		System.out.println( "Bad Node Number" );
		return false;
	    }

	    token = tokened.nextToken( "\n" );
	    app = (ScribeRegrTestApp)m_scribeNodes.get( node );

	    topicId = app.generateTopicId( token );
	    app.publish( topicId );
	    m_tracker.receivedMessage( topicId );
	}
	else if ( token.startsWith( "sub" ) ) {
	    token = tokened.nextToken();
	    node = getNodeNumber( token );
	    if( node < 0 ) {
		System.out.println( "Bad Node Number" );
		return false;
	    }

	    token = tokened.nextToken( "\n" );
	    app = (ScribeRegrTestApp)m_scribeNodes.get( node );

	    topicId = app.generateTopicId( token );
	    app.subscribe( topicId );
	    m_tracker.setSubscribed( topicId, true );
	}
	else if ( token.startsWith( "uns" ) ) {
	    token = tokened.nextToken();
	    node = getNodeNumber( token );
	    if( node < 0 ) {
		System.out.println( "Bad Node Number" );
		return false;
	    }

	    token = tokened.nextToken( "\n" );
	    app = (ScribeRegrTestApp)m_scribeNodes.get( node );

	    topicId = app.generateTopicId( token );
	    app.unsubscribe( topicId );
	}
	
	return false;
    }

    private int getNodeNumber( String st ) {
	int node = -1;

	try {
	    node = Integer.parseInt( st );
	}
	catch( Exception e ) {
	    System.out.println( e );
	}

	if( node >= 0 && node <m_scribeNodesSorted.size()) {
	    return node;
	}
	else {
	    return -1;
	}
    }
}
