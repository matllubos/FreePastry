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
import java.security.*;

/**
 * @(#) BasicScribeRegrTest.java
 *
 * A basic scribe regression test suite for Scribe. It tests if the basic 
 * operations such as create, join, multicast, leave are working fine.
 *
 * @author Romer Gil
 * @author Eric Engineer 
 */
public class BasicScribeRegrTest
{
    private DirectPastryNodeFactory m_factory;
    private NetworkSimulator m_simulator;
    
    private LinkedList m_pastryNodes;
    private LinkedList m_scribeApps;

    private Vector m_topics;
    private MRTracker m_tracker;
    private MRTracker m_tracker2;
    
    private Random rng;
    
    public Message m_lastMsg;
    public NodeId.Distance m_lastDist;
    
    // constructor
    
    public BasicScribeRegrTest() {
	m_simulator = new EuclideanNetwork();
	m_factory = new DirectPastryNodeFactory(m_simulator);
	
	m_pastryNodes = new LinkedList();
	m_scribeApps = new LinkedList();

	rng = new Random();
	m_tracker = new MRTracker();
	m_tracker2 = new MRTracker(); // keep track after unsubscribe
	m_topics = new Vector();
    }
    
    public void makeScribeNode(int apps) {
	
	// create the Pastry node
	NodeHandle bootstrap = null;
	try {
	    PastryNode lastnode = (PastryNode)m_pastryNodes.getLast();
	    bootstrap = lastnode.getLocalHandle();
	} catch (NoSuchElementException e) {
	}
	PastryNode pnode = m_factory.newNode(bootstrap);
	m_pastryNodes.add(pnode);
	
	// Create the node's instance of Scribe
	Credentials credentials = new PermissiveCredentials();
	Scribe scribe = new Scribe( pnode, credentials);
	
	// create the node's apps
	for (int i=0; i<apps; i++) {
	    Credentials cred = new PermissiveCredentials();	
	    BasicScribeRegrTestApp app = new BasicScribeRegrTestApp( pnode, scribe, i, cred );
	    m_scribeApps.add(app);
	}
    }

    public boolean simulate() { 
	return m_simulator.simulate(); 
    }
    
    /**
     * Main entry point for the basic scribe regression test suite.
     *
     * @return true if all the tests PASSED
     */
    public static boolean start() {
	BasicScribeRegrTest st = new BasicScribeRegrTest();
	int n, a, m, t, i;


	System.out.println(" \n\n BasicScribeRegrTest : which tests if the create, join, multicast, leave operations of Scribe work is about to START  \n");

	//n #nodes, a #apps per node, m #messages per topic, t #topics
	n = 100;
	a = 5;
	m = 10;
	t = 5;


	
	for( i = 0; i < n; i++ ) {
	    st.makeScribeNode(a);
	    while (st.simulate());
	}

	System.gc();

	boolean ok = st.doTheTesting(m,t);

	System.out.println("\n" + n + " nodes constructed with " + a 
			   + " applications per node.\n" + t 
			   + " topics created, and " + m 
			   + " messages sent per topic.");
	String out = "\nBASIC SCRIBE REGRESSION TEST - ";
	out += ok ? "PASSED" : "FAILED (see output above for details)";
	System.out.println(out);

	return ok;

    }

    /*
     * Returns true if all the tests PASSED
     */
    public boolean doTheTesting( int msgs, int topics ) {
	int i, j, k, subs, topic, app;

	NodeId tid;
	int totalApps = m_scribeApps.size();

	//create 'topics' number of topics, from randomly selected apps
	for( i = 0; i < topics; i++ ) {
	    tid = generateTopicId( new String( "ScribeTest" + i ) );
	    app = rng.nextInt( totalApps );    
	    create( app, tid );
	    while(simulate());	

	    //let app know about the topic. For the regr.This has 
	    //nothing to do with the actual scribe api is just stuff that is 
	    //done to verify correctness.
	    for ( j = 0; j < totalApps; j++ ) {	
	    	BasicScribeRegrTestApp a = (BasicScribeRegrTestApp) m_scribeApps.get( j );
	    	a.putTopic( tid );
	    }
	
	    //now add the topics to the regr test. again just for testing. 
	    m_topics.add( tid );
	    m_tracker.setSubscribed( tid, true );
	    m_tracker2.setSubscribed( tid, true ); 

	    //now subscribe a random number of apps to the topic we are 
	    //lookin at
	    subs = rng.nextInt( totalApps );
	    for( j = 0; j < subs; j++ ) {
		join( rng.nextInt( totalApps ), tid);
		while(simulate());
	    }
	}

	//start publishing stuff to all the topics, selected at random.
	for( i = 0; i < topics; i++ ) {
	    for( j = 0; j < msgs; j++ ) {
		topic = rng.nextInt( m_topics.size() );
		app = rng.nextInt( totalApps );
		tid = (NodeId)m_topics.get( topic );
		multicast( app, tid );
		m_tracker.receivedMessage( tid );
		while(simulate());
	    }
	}

	// unsubscribe a random number of apps from random topics
	int unsubs = rng.nextInt( totalApps );
	for ( i = 0; i < unsubs; i++ ) {
	    leave( rng.nextInt( totalApps ));
	    while(simulate());
	}

	//start publishing stuff to all the topics, selected at random.
        for( i = 0; i < topics; i++ ) {
            for( j = 0; j < msgs; j++ ) {
                topic = rng.nextInt( m_topics.size() );
                app = rng.nextInt( totalApps );
                tid = (NodeId)m_topics.get( topic );
                multicast( app, tid );
		// store in second tracker instead
                m_tracker2.receivedMessage( tid );
                while(simulate());
            }
        }


	//verifying that what we sent was received (nothing more and nothing 
	//less) by the correct nodes 
	boolean ok = true;
	for( i = 0; i < totalApps; i++ ) {
	    BasicScribeRegrTestApp a = (BasicScribeRegrTestApp)m_scribeApps.get( i );
	    ok = a.verifyApplication( m_topics, m_tracker, m_tracker2) && ok;
	}
	return ok;
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
    private void multicast( int app, NodeId tid ) {
	BasicScribeRegrTestApp a = (BasicScribeRegrTestApp)m_scribeApps.get( app );
	a.multicast( tid );
    }

    //subscribe one of the suite apps to topic tid
    private void join( int app, NodeId tid ) {
	BasicScribeRegrTestApp a = (BasicScribeRegrTestApp)m_scribeApps.get( app );
	a.join( tid);
    }

    //unsubscribe one of the suite apps from a random currently subscribed topic tid 
    private void leave( int app ) {
	BasicScribeRegrTestApp a = (BasicScribeRegrTestApp)m_scribeApps.get( app );
	a.leave(null);
    }

    //create a topic tid from a given app.
    private void create( int app, NodeId tid ) {
	BasicScribeRegrTestApp a = (BasicScribeRegrTestApp)m_scribeApps.get( app );
	a.create( tid );
    }
}










