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

package rice.scribe.messaging;

import rice.pastry.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.pastry.messaging.*;
import rice.scribe.*;
import rice.scribe.maintenance.*;

import java.io.*;
import java.util.*;
/**
 *
 * HeartBeatMessage is used whenever a Scribe nodes wishes let its children 
 * know that it is still alive, so that the children need not do any repairing
 * of the multicast tree. We send a single HeartBeat Message to a node when
 * it is our child for more than one topic, thereby avoiding sending multiple
 * HeartBeat messages to same node.
 *
 * @version $Id$ 
 *
 * @author Romer Gil 
 * @author Atul Singh
 * @author Animesh Nandi
 */


public class MessageHeartBeat extends ScribeMessage implements Serializable
{

    /**
     * Constructor
     *
     * @param addr the address of the scribe receiver.
     * @param source the node generating the message.
     * @param c the credentials associated with the mesasge.
     */
    public 
	MessageHeartBeat( Address addr, NodeHandle source, 
			  Credentials c ) {
	super( addr, source, null, c );

	// we pass null for topicId, as a child will find out 
	//for which topics topics this node is its parent and 
	//will do processing accordingly

    }
    
   

    /**
     * This method is called whenever the scribe node receives a message for 
     * itself and wants to process it. The processing is delegated by scribe 
     * to the message. If a node has a common parent for some number of topics,
     * then it is going to receive a common HeartBeat message for all those
     * topics. So, it will find out all topics for which this message's source
     * is its parent and consider this message as a HeartBeat message for all
     * those topics. 
     * 
     * @param scribe the scribe application.
     * @param tp the dummy topic ( = null), used because MessageHeartBeat
     *           extends ScribeMessage. Instead, we use a local hashtable
     *           having mapping from parent node -> list of topics for which
     *           it is our parent.
     */
    public void 
	handleDeliverMessage( Scribe scribe, Topic tp ) {
	Credentials cred = scribe.getCredentials();
	SendOptions opt = scribe.getSendOptions();
	int i = 0;
	NodeId topicId;
	Topic topic;
	Vector topics = scribe.getTopicsForParent(m_source);
	NodeId parentFingerprint;
	NodeId localFingerprint;
	ScribeMessage msg; 

	
	parentFingerprint = (NodeId)getData();
	localFingerprint = scribe.getFingerprintForParentTopics(m_source);

	
	if(!parentFingerprint.equals(localFingerprint)) {
	    /*
	     * Now, there is inconsistency in parent-child relationship
	     * from the view of parent and child, so the child requests
	     * for the entire list of topics for which the parent is
	     * having this node as its child.
	     */
	    msg = scribe.makeRequestToParentMessage( cred );
	    scribe.routeMsgDirect( m_source, msg, cred, opt );
	}
	else {
	    if( topics != null){
		while( i < topics.size()){
		    topicId = (NodeId)topics.elementAt(i);
		    i++;
		    topic = scribe.getTopic(topicId);
		    // take note of the parent for this topic and tell the failure 
		    // handler that the parent is ok
		    if( topic.getParent() != m_source)
			System.out.println("Error:: Inconsistency in distinctParentTable found"); 
		    topic.postponeParentHandler();
		    // if waiting to find parent, now send unsubscription msg
		    if ( topic.isWaitingUnsubscribe() ) {
			scribe.unsubscribe( topic.getTopicId(), null, cred );
			topic.waitUnsubscribe( false );
		    }
		}
	    }
	}
    }
    


    /**
     * This method is called whenever the scribe node forwards a message in 
     * the scribe network. The processing is delegated by scribe to the 
     * message.
     * 
     * @param scribe the scribe application.
     * @param topic the Topic is null here
     * @return true if the message should be routed further, false otherwise.
     */
    public boolean 
	handleForwardMessage( Scribe scribe, Topic topic ) {

	if( m_source.getNodeId().equals( scribe.getNodeId() ) ) {
	    return true;
	}

	return true;
    }


    public String toString() {
	return new String( "HEARTBEAT MSG:" + m_source );
    }
}

