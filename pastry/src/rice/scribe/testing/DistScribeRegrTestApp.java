/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate 

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
import rice.pastry.client.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.pastry.messaging.*;
import rice.pastry.direct.*;
import rice.pastry.leafset.*;

import rice.scribe.*;
import rice.scribe.messaging.*;
import rice.scribe.testing.*;

import java.util.*;
import java.security.*;
import java.io.*;

/**
 * @(#) DistScribeRegrTestApp.java
 *
 * an application used by the  maintenance test suite for Scribe
 * @version $Id$
 * @author Atul Singh
 * @author Animesh Nandi 
 */

public class DistScribeRegrTestApp extends PastryAppl implements IScribeApp
{
    protected PastryNode m_pastryNode ;
    public Scribe m_scribe;
    public int m_appIndex;
    public static int m_appCount = 0;

    // This random number generator is used for choosing the random probability of
    // unsubscribing to topics
    public Random m_rng = null; 


    /**
     * The hashtable maintaining mapping from topicId to log object
     * maintained by this application for that topic.
     */
    public Hashtable m_logTable = null;

    /**
     * The receiver address for the DistScribeApp system.
     */
    protected static Address m_address = new DistScribeRegrTestAppAddress();

    /**
     * The SendOptions object to be used for all messaging through Pastry
     */
    protected SendOptions m_sendOptions = null;

    /**
     * The Credentials object to be used for all messaging through Pastry
     */
    protected static Credentials m_credentials = null;
    
    //  this determines the frequency with which the DistScribeRegrTest message is 
    // delivered to this testerapplication.
    private int m_testFreq ;

    // This keeps track of the topicIds to which this node subscribes.
    public Vector m_topics ;

    // This is only required to determine the total virtual nodes on this host, so that
    // we can unsubscribe some fraction of them.
    public DistScribeRegrTest m_driver;

    private static class DistScribeRegrTestAppAddress implements Address {
	private int myCode = 0x8abc748c;
	
	public int hashCode() { return myCode; }

	public boolean equals(Object obj) {
	    return (obj instanceof DistScribeRegrTestAppAddress);
	}
    }


    public DistScribeRegrTestApp( PastryNode pn,  Scribe scribe, Credentials cred, DistScribeRegrTest driver ) {
	super(pn);
	m_driver = driver;
	m_scribe = scribe;
	m_credentials = cred;
	if(cred == null) {
	    m_credentials = new PermissiveCredentials();
	}
	m_pastryNode = pn;
	m_topics = new Vector();
	m_sendOptions = new SendOptions();
	m_logTable = new Hashtable();
	m_appIndex = m_appCount ++;
	m_rng = new Random(PastrySeed.getSeed() + m_appIndex);

	// This sets the periodic rate at which the DistScribeRegrTest Messages will be 
	// invoked.
	m_testFreq = Scribe.m_scribeMaintFreq;

	// This is done at the end of the constructor so that all the 
	// variables are initialized before we get the call to scribeIsReady()
	m_scribe.registerApp(this);
    }



    public void scribeIsReady() {
	 int i;
	 NodeId topicId;
	 DistTopicLog topicLog;

	 System.out.println("I am up " + m_scribe.getNodeId());

	 // Create topicIds
	 for (i=0; i< DistScribeRegrTest.NUM_TOPICS; i++) {
	     topicId = DistScribeRegrTest.generateTopicId(new String("Topic " + i));
	     m_topics.add(topicId);
	     m_logTable.put(topicId, new DistTopicLog());
	 }
	 
	 // Subscribe to the the topicIds created
	 for (i=0; i< DistScribeRegrTest.NUM_TOPICS; i++) {
	     topicId = (NodeId)m_topics.elementAt(i);
	     create(topicId);
	     join(topicId);

	     // We need to set the initial lastRecvTime corresponding to 
	     // messages for this topic as the current time 
	     topicLog = (DistTopicLog) m_logTable.get(topicId);
	     topicLog.setLastRecvTime(System.currentTimeMillis());
	 }
	 
	 // Trigger the periodic invokation of DistScribeRegrTest message
	 m_pastryNode.scheduleMsgAtFixedRate(makeDistScribeRegrTestMessage(m_credentials), 0 , m_testFreq*1000);
    }
    
    public Scribe getScribe() {
	return m_scribe;
    }

    /**
     * up-call invoked by scribe when a publish message is 'delivered'.
     */
    public void receiveMessage( ScribeMessage msg ) {
	NodeId topicId;
	DistTopicLog topicLog;
	
	topicId = (NodeId) msg.getTopicId();
	System.out.println("Node "+m_scribe.getNodeId()+ "appindex=" + m_appIndex + " received seqno "+((Integer)msg.getData()).intValue()+" for topic "+topicId);
	processLog(topicId,((Integer)msg.getData()).intValue());
    }

    public void processLog(NodeId topicId, int new_seqno){
	int last_seqno_recv;
	DistTopicLog topicLog;
	
	topicLog = (DistTopicLog)m_logTable.get(topicId);

	if( topicLog.getUnsubscribed()){
	    System.out.println("\nWARNING :: "+m_scribe.getNodeId()+" Received a message for a topic "+topicId+" for which I have UNSUBSCRIBED \n");
	    return;
	}

	last_seqno_recv = topicLog.getLastSeqNumRecv();
	topicLog.setLastSeqNumRecv(new_seqno);
	topicLog.setLastRecvTime(System.currentTimeMillis());

	if(last_seqno_recv == -1 || new_seqno == -1)
	    return;
	
	/**
	 * Check for out-of-order sequence numbers and then
      	 * check if the missing sequence numbers were due to tree-repair.
	 */
	
	if(last_seqno_recv > new_seqno){
	    System.out.println("\nWARNING :: "+m_scribe.getNodeId()+" Received a LESSER sequence number than last-seen for topic "+topicId + "\n");
	}
	else if(last_seqno_recv == new_seqno){
	    System.out.println("\nWARNING :: "+m_scribe.getNodeId()+" Received a DUPLICATE sequence number for topic "+topicId + "\n");
	}
	else if( (new_seqno - last_seqno_recv - 1) > m_scribe.getTreeRepairThreshold()){
	    System.out.println("\nWARNING :: "+m_scribe.getNodeId()+" Missed MORE THAN TREE-REPAIR THRESHOLD number of sequence numbers  for topic "+topicId + "\n");
	}
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
    public void faultHandler( ScribeMessage msg, NodeHandle parent ) {
	/*
	  System.out.println("Node:" + getNodeId() + " App:"
	  + m_app + " handling fault: " + msg);
	*/
    }
    
    /**
     * up-call invoked by scribe when a node is added/removed to the multicast tree.
     */
    public void subscribeHandler( NodeId topicId, NodeHandle child, boolean wasAdded, Object obj ) {
	/*
	  System.out.println("Node:" + getNodeId() + " App:"
	  + m_app + " child subscribed: " + msg);
	*/
    }

    /**
     * direct call to scribe for creating a topic from the current node.
     */
    public void create( NodeId topicId ) {
	m_scribe.create( topicId, m_credentials);
    }
    
    /**
     * direct call to scribe for publishing to a topic from the current node.
     */    
    public void multicast( NodeId topicId, Object data ) {
	m_scribe.multicast( topicId, data, m_credentials );
    }

    /**
     * direct call to scribe for anycasting to a topic from the current node.
     */    
    public void anycast( NodeId topicId, Object data ) {
	m_scribe.anycast( topicId, data, m_credentials );
    }
    
    /**
     * direct call to scribe for subscribing to a topic from the current node.
     */    
    public void join( NodeId topicId ) {
	m_scribe.join( topicId, this, m_credentials );
    }
    
    /**
     * direct call to scribe for unsubscribing a  topic from the current node
     * The topic is chosen randomly if null is passed and topics exist.
     */    
    public void leave(NodeId topicId) {
	DistTopicLog topicLog;
	topicLog = (DistTopicLog)m_logTable.get(topicId);
	topicLog.setUnsubscribed(true);
	System.out.println(m_scribe.getNodeId()+" Unsubscribing from topic "+topicId);
	m_scribe.leave( topicId, this, m_credentials );
    }
    
    public Credentials getCredentials() { 
	return m_credentials;
    }
    

    public Address getAddress() {
	return m_address;
    }

    public void messageForAppl(Message msg) {
	DistScribeRegrTestMessage tmsg = (DistScribeRegrTestMessage)msg;
	tmsg.handleDeliverMessage( this);
    }

    /**
     * Makes a DistScribeRegrTest message.
     *
     * @param c the credentials that will be associated with the message
     * @return the DistScribeRegrTestMessage
     */
    private Message makeDistScribeRegrTestMessage(Credentials c) {
	return new DistScribeRegrTestMessage( m_address, c );
    }
    
}








