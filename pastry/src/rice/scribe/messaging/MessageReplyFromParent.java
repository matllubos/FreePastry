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
 * MessageReplyFromParent is a message sent by the PARENT node to a node
 * as a response to a MessageRequestToParent type of message. We send 
 *
 * @version $Id$ 
 * 
 * @author Atul Singh
 * @author Animesh Nandi
 */


public class MessageReplyFromParent extends ScribeMessage implements Serializable
{

    /**
     * Constructor
     *
     * @param addr the address of the scribe receiver.
     * @param source the node generating the message.
     * @param tid is null
     * @param c the credentials associated with the mesasge.
     */
    public 
	MessageReplyFromParent( Address addr, NodeHandle source, 
			  NodeId tid, Credentials c ) {
	super( addr, source, null, c );
    }
    
   

    /**
     * This method is called whenever the scribe node receives a message for 
     * itself and wants to process it. The processing is delegated by scribe 
     * to the message.
     * 
     * @param scribe the scribe application.
     * @param tp the dummy topic ( = null), used because MessageReplyFromParent
     *           extends ScribeMessage.
     */
    public void 
	handleDeliverMessage( Scribe scribe, Topic tp ) {
	int i;

	Credentials cred = scribe.getCredentials();
	SendOptions opt = scribe.getSendOptions();
	Vector topicListFromParent = (Vector)getData();
	Vector topicListLocal = scribe.getTopicsForParent(m_source);
	NodeId topicId;
	Topic topic;

	for(i=0 ; i< topicListFromParent.size(); i++ ) {
	    topicId = (NodeId)topicListFromParent.elementAt(i);
	    topic = scribe.getTopic(topicId);
	    if(topic != null && topic.getParent()== null) {
		// This could be because we missed an MessageAckOnSubscribe.
		topic.setParent(m_source);
		topic.postponeParentHandler();

		// if waiting to find parent, now send unsubscription msg
		if ( topic.isWaitingUnsubscribe() ) {
		    scribe.unsubscribe( topic.getTopicId(), null, cred );
		    topic.waitUnsubscribe( false );
		}
	    }
	    else {
		if(topicListLocal== null || !topicListLocal.contains(topicId)) {
		    ScribeMessage msg = scribe.makeUnsubscribeMessage( topicId, cred );
		    scribe.routeMsgDirect( m_source, msg, cred, opt );
		}
	    }
	}
	if(topicListLocal != null) {    
	    for(i=0 ; i< topicListLocal.size(); i++ ) {
		topicId = (NodeId)topicListLocal.elementAt(i);
		if(topicListFromParent== null || !topicListFromParent.contains(topicId)) {
		    ScribeMessage msg = scribe.makeSubscribeMessage( topicId, cred );
		    topic = scribe.getTopic(topicId);
		    topic.postponeParentHandler();
		    scribe.routeMsg( topicId, msg, cred, opt );     
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
     * @param topic the Topic is null 
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
	return new String( "REQUESTTOPARENT MSG:" + m_source );
    }
}

