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
import rice.scribe.security.*;
import rice.scribe.maintenance.*;

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

public class DistScribeRegrTestApp implements IScribeApp
{
    protected PastryNode m_pastryNode ;
    public Scribe m_scribe;
    public Vector listOfTopics;
    public NodeId topicId;
    public int m_appIndex;
    public static int m_appCount = 0;
    public int lastSeqNum = -1;

    /**
     * The hashtable maintaining mapping from topicId to log object
     * maintained by this application for that topic.
     */
    public Hashtable m_logTable = null;

    /**
     * The SendOptions object to be used for all messaging through Pastry
     */
    protected SendOptions m_sendOptions = null;

    /**
     * The SendOptions object to be used for all messaging through Pastry
     */
    protected static Credentials m_credentials = null;

    public DistScribeRegrTestApp( PastryNode pn,  Scribe scribe, Credentials cred ) {
	m_scribe = scribe;
	m_credentials = cred;
	m_pastryNode = pn;
	listOfTopics = new Vector();
	m_sendOptions = new SendOptions();
	m_credentials = new PermissiveCredentials();
	m_logTable = new Hashtable();
	m_appIndex = m_appCount ++;
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
     * up-call invoked by scribe when a node is added to the multicast tree.
     */
    public void subscribeHandler( ScribeMessage msg ) {
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
    public void publish( NodeId topicId, Object data ) {
	m_scribe.publish( topicId, data, m_credentials );
    }
    
    /**
     * direct call to scribe for subscribing to a topic from the current node.
     */    
    public void subscribe( NodeId topicId ) {
	m_scribe.subscribe( topicId, this, m_credentials );
    }
    
    /**
     * direct call to scribe for unsubscribing a  topic from the current node
     * The topic is chosen randomly if null is passed and topics exist.
     */    
    public void unsubscribe(NodeId topicId) {
	DistTopicLog topicLog;
	topicLog = (DistTopicLog)m_logTable.get(topicId);
	topicLog.setUnsubscribed(true);
	System.out.println(m_scribe.getNodeId()+" Unsubscribing from topic "+topicId);
	m_scribe.unsubscribe( topicId, this, m_credentials );
    }
    
    /**
     * Returns the credentials of this client.
     *
     * @return the credentials.
     */
    public Credentials getCredentials() { return m_credentials; }
    
}







