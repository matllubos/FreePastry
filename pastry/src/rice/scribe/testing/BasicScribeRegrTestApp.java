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
import rice.pastry.security.*;
import rice.scribe.*;
import rice.scribe.messaging.*;
import rice.pastry.direct.*;

import java.util.*;

import java.io.*;

/**
 * @(#) BasicScribeRegrTestApp.java
 *
 * Application used by the BasicScribeRegrTest test suites.
 * It runs over a Scribe node but it is notified from the 
 * whole network of what topics have been created
 * so that it can verify correctness after execution.
 *
 * @version $Id$
 *
 * @author Romer Gil
 */
public class BasicScribeRegrTestApp implements IScribeApp
{
    
    private Credentials m_credentials;
    private Scribe m_scribe;
    MRTracker m_tracker = new MRTracker();
    int m_app = 0; // used to id the app within a node
    Random m_rng = new Random();
    
    public BasicScribeRegrTestApp( PastryNode node, Scribe scribe, int app, Credentials cred ) {
	m_scribe = scribe;
	m_credentials = cred;
	m_app = app;
	m_scribe.registerApp(this);
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
		System.out.println( " Should receive: " + shouldReceive 
				+ " Actually received: " + received );
		ok = false;
	    }
	    else {
		//System.out.print( "Node:" + getNodeId() + "App:" + m_app +  " verifies topic " + tid);
		//System.out.println( " Should receive: " + shouldReceive + " Actually received: " + received );
	    }
 
	    
	}
	return ok;
    }

    public void scribeIsReady() {
	
    }
    

    /**
     * up-call invoked by scribe when a publish message is 'delivered'.
     */
    public void receiveMessage( ScribeMessage msg ) {
	m_tracker.receivedMessage( msg.getTopicId() );
	// System.out.println("Node:" + getNodeId() + " App:" + m_app + " received msg: " + msg); 
       
    }

    /**
     * up-call invoked by scribe when a publish message is forwarded through
     * the multicast tree.
     */
    public void forwardHandler( ScribeMessage msg ) {
	// System.out.println("Node:" + getNodeId() + " App:" + m_app + " forwarding: "+ msg);
    }

    /**
     * up-call invoked by Scribe when an anycast message is being handled.
     */
    public boolean anycastHandler(ScribeMessage msg){
	return true;
    }
    
    /**
     * up-call invoked by scribe when a node detects a failure from its parent.
     */
    public void faultHandler( ScribeMessage msg, NodeHandle parent ) {
	// System.out.println("Node:" + getNodeId() + " App:" + m_app + " handling fault: " + msg);
    }

    /**
     * up-call invoked by scribe when a node is added/removed to the multicast tree.
     */
    public void subscribeHandler( NodeId topicId, NodeHandle child, boolean wasAdded, Serializable obj ) {
	// System.out.println("Node:" + getNodeId() + " App:" + m_app + " child subscribed: " + msg);
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
	// System.out.println("Node:" + getNodeId() + " App:" + m_app + " creating topic " + topicId );

	m_scribe.create( topicId, m_credentials);
    }

    /**
     * direct call to scribe for publishing to a topic from the current node.
     */    
    public void multicast( NodeId topicId ) {
	// System.out.println("Node:" + getNodeId() + " App:" + m_app + " publishing on topic" + topicId );
	m_scribe.multicast( topicId, null, m_credentials );
    }

    /**
     * direct call to scribe for anycasting to a topic from the current node.
     */    
    public void anycast( NodeId topicId ) {
	// System.out.println("Node:" + getNodeId() + " App:" + m_app + " publishing on topic" + topicId );
	m_scribe.anycast( topicId, null, m_credentials );
    }


    /**
     * direct call to scribe for subscribing to a topic from the current node.
     */    
    public void join( NodeId topicId ) {
	// System.out.println("Node:" + getNodeId() + " App:" + m_app + " subscribing to topic " + topicId);
	m_scribe.join( topicId, this, m_credentials);
	m_tracker.setSubscribed( topicId, true );
    }

    /**
     * direct call to scribe for unsubscribing a  topic from the current node
     * The topic is chosen randomly if null is passed and topics exist.
     */    
    public void leave(NodeId topicId) {
	if (topicId == null) {
	    NodeId[] topics = m_tracker.getSubscribedTopics();
	    if (topics.length == 0) return;
	    int tid = m_rng.nextInt(topics.length);
	    topicId = topics[tid];
	}
	// System.out.println("Node:" + getNodeId() + " App:" + m_app + " unsubscribing from topic " + topicId);
	m_scribe.leave( topicId, this, m_credentials );
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



















