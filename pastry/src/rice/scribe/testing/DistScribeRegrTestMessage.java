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
import rice.pastry.routing.*;
import rice.pastry.messaging.*;
import rice.scribe.*;
import rice.scribe.security.*;
import rice.scribe.maintenance.*;
import rice.scribe.messaging.*;

import java.io.*;
import java.util.*;

/**
 *
 * DistScribeRegrTestMessage is used by the dist regression test application on the Scribe
 * layer to do publishing on some topics, etc. This message will be delivered to this 
 * application periodically so that the periodic testing activities can be accomplished.
 * 
 * @version $Id$ 
 * 
 * @author Atul Singh 
 * @author Animesh Nandi
 */


public class DistScribeRegrTestMessage extends Message implements Serializable
{
    /**
     * Constructor
     *
     * @param addr the address of the distScribeRegrTestApp receiver.
     * @param c the credentials associated with the mesasge.
     */
    public 
	DistScribeRegrTestMessage( Address addr, Credentials c) {
	super( addr, c );
    }
    
    /**
     * This method is called whenever the pastry node receives a message for the
     * DistScribeRegrTestApp.
     * 
     * @param scribeApp the DistScribeRegrTestApp application.
     */
    public void handleDeliverMessage( DistScribeRegrTestApp scribeApp) {
	int i;
	NodeId topicId;
	DistTopicLog topicLog;
	int threshold = scribeApp.m_scribe.getTreeRepairThreshold();
	int seq_num = -1;
	int count = 1;
	int lastRecv;
	NodeHandle parent;
	boolean result;

	result = distinctChildrenTableConsistencyTest(scribeApp.m_scribe);
	if(result == false)
	    System.out.println("Distinct Children Table consistency test FAILED at node" + scribeApp.m_scribe.getNodeId());

	result = distinctParentTableConsistencyTest(scribeApp.m_scribe);
	if(result == false)
	    System.out.println("Distinct Parent Table consistency test FAILED at node" + scribeApp.m_scribe.getNodeId());
	
	for (i=0; i< DistScribeRegrTest.NUM_TOPICS; i++) {
	    topicId = (NodeId) scribeApp.m_topics.elementAt(i);

	    parent = scribeApp.m_scribe.getParent(topicId);
	    topicLog = (DistTopicLog) scribeApp.m_logTable.get(topicId);
	    seq_num = topicLog.getSeqNumToPublish();
	    count = topicLog.getCount();
	    if( scribeApp.m_scribe.isRoot(topicId)){
		scribeApp.multicast(topicId, new Integer(seq_num));
		/*
		 * We play safe in publishing the '-1' so that all nodes
		 * which were doing a tree repair while the new root
		 * came up can still see the demarcation of '-1'
		 */
		if(count < threshold*2){
		    count++;
		}
		else
		    seq_num ++;
	    }
	    else {
		count = 1;
		seq_num = -1;
	    }
	    topicLog.setCount(count);
	    topicLog.setSeqNumToPublish(seq_num);
	    
	    /* We unsubscribe with a probability of 0.1 after we have received
	     * a sequence number 'UNSUBSCRIBE_LIMIT' for a topic.
	     */
	    int allowed = (int)( DistScribeRegrTest.fractionUnsubscribedAllowed * scribeApp.m_driver.localNodes.size());
	    synchronized( DistScribeRegrTest.LOCK){
		if( DistScribeRegrTest.getNumUnsubscribed(topicId) < allowed){
		    if(! topicLog.getUnsubscribed()){
			lastRecv = topicLog.getLastSeqNumRecv();
			if(lastRecv > DistScribeRegrTest.UNSUBSCRIBE_LIMIT){
			    int n = scribeApp.m_rng.nextInt((int)(1/DistScribeRegrTest.UNSUBSCRIBE_PROBABILITY));
			    if( n == 0){
				scribeApp.leave(topicId);
				DistScribeRegrTest.incrementNumUnsubscribed(topicId);
			    }
			}
		    }
		}
	    }
	    
	    
	    if(! topicLog.getUnsubscribed()){
		long currentTime = System.currentTimeMillis();
		long prevTime = topicLog.getLastRecvTime();
		int diff = (int)((currentTime - prevTime)/ 1000.0);
		
		

		if( diff > DistScribeRegrTest.IDLE_TIME) {
		    System.out.println("\nWARNING :: "+scribeApp.m_scribe.getNodeId()+" DID NOT  Receive a message on the topic "+topicId + " for "+diff+" secs , it current parent =" + parent + "\n");
		}
		
	    }
	}
    }
    

    /**
     * Check the consistency between distinctChildrenTable maintained
     * by scribe on a node and the children maintained by each Topic on that node.
     *
     * @param scribe the Scribe object residing on local node
     *
     * @return true if test passes, else false
     */
    public boolean distinctChildrenTableConsistencyTest(Scribe scribe){
	Vector children;
	NodeId topicId;
	NodeHandle child;
	Vector topics ;
	int t, l;
	Topic topic;
	Vector topicsForChild;
	Vector distinctChildrenVector;
	boolean result = true;

	topics = scribe.getTopics();

	for( t = 0; t < topics.size(); t++){
	    topic = (Topic) topics.elementAt(t);
	    children = (Vector) topic.getChildren();
	    for( l=0; l < children.size(); l++){
		child = (NodeHandle) children.elementAt(l);
		topicsForChild = (Vector) scribe.getTopicsForChild((NodeHandle)child);
		if(!topicsForChild.contains(topic.getTopicId()))
		    result = false;
	    }
	}
	distinctChildrenVector = (Vector)scribe.getDistinctChildren();
	for( l = 0; l < distinctChildrenVector.size(); l++){
	    child = (NodeHandle) distinctChildrenVector.elementAt(l);
	    topicsForChild = (Vector) scribe.getTopicsForChild((NodeHandle)child);
	    for( t = 0; t < topicsForChild.size(); t++){
		topicId = (NodeId) topicsForChild.elementAt(t);
		topic = scribe.getTopic(topicId);
		children = (Vector) topic.getChildren();
		if( !children.contains(child))
		    result  = false;
	    }
	}
	return result;
    }
    

    /**
     * Check the consistency between distinctParentTable maintained
     * by scribe on a node and the parent maintained by each Topic on that node.
     *
     * @param scribe the Scribe object residing on local node
     *
     * @return true if test passes, else false
     */
    public boolean distinctParentTableConsistencyTest(Scribe scribe){
	NodeId topicId;
	NodeHandle parent;
	Vector topics;
	Topic topic;
	int t, l ;
	Vector topicsForParent;
	boolean result = true;
	Vector distinctParentVector;

	
	topics = scribe.getTopics();
	for( t = 0; t < topics.size(); t++){
	    topic = (Topic) topics.elementAt(t);
	    parent = (NodeHandle) topic.getParent();
	    if( parent != null) {
		topicsForParent = (Vector) scribe.getTopicsForParent((NodeHandle)parent);
		if( !topicsForParent.contains(topic.getTopicId())) 
		    result = false;
	    }
	}
	distinctParentVector = (Vector) scribe.getDistinctParents();
	for( l = 0; l < distinctParentVector.size(); l++) {
	    parent = (NodeHandle) distinctParentVector.elementAt(l);
	    topicsForParent = (Vector)scribe.getTopicsForParent((NodeHandle)parent);
	    for( t = 0; t < topicsForParent.size(); t++){
		topicId = (NodeId) topicsForParent.elementAt(t);
		topic = (Topic) scribe.getTopic(topicId);
		if( !topic.getParent().equals(parent)) 
		    result = false;
	    }
	}
	return result;
    }

    public String toString() {
	return new String( "DIST_SCRIBE_REGR_TEST  MSG:" );
    }
}



