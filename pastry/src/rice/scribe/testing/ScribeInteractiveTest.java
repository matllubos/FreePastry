/*************************************************************************

"Free Pastry" Peer-to-Peer Application Development Substrate 

Copyright 2002, Rice University. All rights reserved. 

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/


package rice.scribe.testing;

import rice.pastry.*;
import rice.pastry.join.*;
import rice.pastry.direct.*;
import rice.pastry.messaging.*;
import rice.scribe.*;
import rice.pastry.security.*;

import java.util.*;
import java.io.*;


/**
 * @(#) ScribeInteractiveTest.java
 *
 * An interactive tester app for Scribe.
 * @version $Id$
 * @author Romer Gil
 */
public class ScribeInteractiveTest
{
    private DirectPastryNodeFactory m_factory;
    private NetworkSimulator m_simulator;
    
    private LinkedList m_scribeNodes;
    private LinkedList m_scribeApps;
    public TreeMap m_scribeNodesSorted;
    //    private Vector m_rtApps;
    
    private Random rng;
    
    public Message m_lastMsg;
    public NodeId.Distance m_lastDist;
    private MRTracker m_tracker;
    
    // constructor
    
    public ScribeInteractiveTest() {
	m_simulator = new EuclideanNetwork();
	m_factory = new DirectPastryNodeFactory(m_simulator);
	
	m_scribeNodes = new LinkedList();
	m_scribeApps = new LinkedList();
	m_scribeNodesSorted = new TreeMap();
	m_tracker = new MRTracker();
	//	rtApps = new Vector();
	rng = new Random();
    }
    
    public void makeScribeNode() {

	NodeHandle bootstrap = null;
	try {
	    PastryNode lastnode = (PastryNode) m_scribeNodes.getLast();
	    bootstrap = lastnode.getLocalHandle();
	} catch (NoSuchElementException e) {
	}

	PastryNode pnode = m_factory.newNode(bootstrap);

	Credentials cred = new PermissiveCredentials();
	Scribe scribe = new Scribe(pnode, cred);

	BasicScribeRegrTestApp app = new BasicScribeRegrTestApp( pnode,scribe,0, cred );

	m_scribeNodes.add(pnode);
	m_scribeApps.add(app);
	m_scribeNodesSorted.put(app.getNodeId(),app);
	
	//	RegrTestApp rta = new RegrTestApp(pn,this);
	//	rtApps.addElement(rta);
	
    }

    public boolean simulate() { 
	return m_simulator.simulate(); 
    }
    
    /**
     * Main entry point for the interactive tester. The commands entered are 
     * all (or at least most) of the form 'command node# topic#'.
     */
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

    /* Basically a big switch/case for reading the input and acting accordingly
     */
    private boolean parseInput( String in ) {
	StringTokenizer tokened = new StringTokenizer( in, " \t\n" );
	if( !tokened.hasMoreTokens() ) {
	    return false;
	}

	String token = tokened.nextToken();
	BasicScribeRegrTestApp app;
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
	    app = (BasicScribeRegrTestApp)m_scribeApps.get( node );

	    topicId = app.generateTopicId( token );
	    app.create( topicId);
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
	    app = (BasicScribeRegrTestApp)m_scribeApps.get( node );

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
	    app = (BasicScribeRegrTestApp)m_scribeApps.get( node );

	    topicId = app.generateTopicId( token );
	    app.subscribe( topicId);
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
	    app = (BasicScribeRegrTestApp)m_scribeApps.get( node );

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
