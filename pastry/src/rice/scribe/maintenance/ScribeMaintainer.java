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

package rice.scribe.maintenance;

import rice.pastry.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.pastry.messaging.*;
import rice.scribe.*;
import rice.scribe.maintenance.*;
import rice.scribe.messaging.*;
import java.io.*;
import java.util.*;
import java.lang.*;

/**
 * This is responsible for all the Scribe tree maintainance 
 * activities corresponding to all the topics in this local node.
 *
 * 
 * @version $Id$
 * @author Atul Singh
 * @author Animesh Nandi
 */
public class ScribeMaintainer 
{
    private Scribe m_scribe = null;

    public ScribeMaintainer( Scribe scribe) {
	m_scribe = scribe;
    }

    /**
     * Sends heartbeat messages to this local node's children for all the 
     * topics on this local scribe node. This method should be invoked 
     * periodically by the driver with the same frequency in all nodes.
     * In addition to initiating sending of heartbeat messages from this
     * local node, this method also implicitly notifies the local node that
     * it should expect a heartbeat message from its parents for all the 
     * topics on this local node. So, if it fails to receive a threshold 
     * value of such heartbeat messages from any parent for a particular
     * topic, a tree repair event is triggered for that topic.
     */
    public void scheduleHB() {
	Topic topic;
	NodeId topicId ;
	Credentials cred ;
	SendOptions opt ;
	ScribeMessage msgh ; 
	Set childrenSet;
	Vector children;
	int i,j;
	Vector topicVector;
	
	topicVector = m_scribe.getTopics();
	i = 0;
	while( i < topicVector.size()){
	    topic = (Topic)topicVector.elementAt(i);
	    i++;

	    /**
	     * Incrementing the heartBeatsMissed counter is based on 
	     * on the assumption that when we are told to issue a 
	     * heartbeat , the other nodes in the system are also
	     * told to issue a heartbeat. This time synchroniation
	     * assumption is perfectly fine in the simulator where we 
	     * can keep track of all the nodes. Even in the distributed
	     * environment this assumption is realistic since we take
	     * actions of Tree Repair after a time (given by a 
	     * treeRepairThreshold * heartbeatperiod) which should be
	     * greater than the 
	     * (max latency between any two nodes + heartbeatperiod)
	     * and the heartbeatperiod defines the maximum time offset 
	     * in scheduling of a heartbeat events in any two nodes in 
	     * the system. 
	     */
	    topic.m_heartBeatsMissed ++;

	    /**
	     * Start a tree repair for this topic. Note that the 
	     * heartBeatsMissed value for the diferent topics are going
	     * to be different since the parents are different for the
	     * different topics. 
	     */
	    if(topic.m_heartBeatsMissed > m_scribe.getTreeRepairThreshold()){
		scheduleTR(topic);
	    }
	}


	/**
	 * We send this heartbeat message to the set of distinct children 
	 * for this node.
	 * By this, we avoid sending multiple HeartBeat messages to a 
	 * single node if it our child for multiple topics.
	 */

	Vector distinctChildren = m_scribe.getDistinctChildren();
	i = 0;
	Vector listOfAlreadySentNodes = m_scribe.getAlreadySentHBNodes();
	// Now, clear this vector.
	m_scribe.clearAlreadySentHBNodes();

	while(i < distinctChildren.size()){
	    NodeHandle child = (NodeHandle)distinctChildren.elementAt(i);
	    i++;
	    if( !listOfAlreadySentNodes.contains((NodeId)child.getNodeId())){
		cred = m_scribe.getCredentials();
		opt = m_scribe.getSendOptions();
		msgh = m_scribe.makeHeartBeatMessage( cred );
		msgh.setData((Serializable)m_scribe.getFingerprintForChildTopics(child));
		
		if( !m_scribe.routeMsgDirect( child, msgh, cred, opt ) ) {
		
		    //if we are here, the child didnt respond so it is discarded
		    Vector topicsForChild = (Vector)m_scribe.getTopicsForChild((NodeHandle)child);
		    int k = 0;
		    while( k < topicsForChild.size()){
			topicId = (NodeId)topicsForChild.elementAt(k);
			topic = (Topic) m_scribe.getTopic(topicId);
			topic.removeChild( child, null );
			k++;
		    }
		}
	    }
	}
    }    
    
   
    /**
     * Starts a tree repair for a particular topic in the local node
     */
    public void scheduleTR(Topic topic) {
	NodeId topicId ;
	Credentials cred ;
	SendOptions opt ;
	ScribeMessage msgs, msgu ; 

	if(topic.isTopicManager()) {
	    // This node need not start a tree repair since it is the topic manager for the topic (currently the root for the topic's multicast tree).
	    return;
	}

	topicId = topic.getTopicId();
	cred = m_scribe.getCredentials();
	opt = m_scribe.getSendOptions();
	
	// Now, we should set the parent for this topic to null, as 
	// most probably we are going to get a new parent for this
	// topic. So, if we have a non-null parent right now, we
	// should send a UNSUBSCRIBE message to that non-null parent.

	NodeHandle prev_parent;
	prev_parent = topic.getParent();
	if( prev_parent != null){
	    msgu = m_scribe.makeUnsubscribeMessage( topicId, cred);
	    //m_scribe.routeMsgDirect(prev_parent, msgu, cred, opt);
	}
	topic.setParent(null);

	msgs = m_scribe.makeSubscribeMessage( topicId, cred);
	topic.postponeParentHandler();

	// Inform all interested applications
	IScribeApp[] apps = topic.getApps();
	for ( int i=0; i<apps.length; i++ ) {
	    apps[i].faultHandler( msgs, prev_parent );
	}

	// now, send a subscribe message
//	if(prev_parent != null)
//	    System.out.println("DEBUG :: TREE REPAIR FOR "+topicId+" previous parent "+prev_parent.getNodeId()+" at "+(int)System.currentTimeMillis()+" at node "+m_scribe.getNodeId());
//	else
//	    System.out.println("DEBUG :: TREE REPAIR FOR "+topicId+" previous parent is null, at "+(int)System.currentTimeMillis()+ " at node "+m_scribe.getNodeId());
	m_scribe.routeMsg( topicId, msgs, cred, opt );

    }
    
}





