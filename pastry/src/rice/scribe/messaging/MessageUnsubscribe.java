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

/**
 *
 * MessageUnsubscribe is used whenever a Scribe node wishes to unsubscribe 
 * from a topic.
 * 
 * @version $Id$ 
 *
 * @author Romer Gil 
 * @author Eric Engineer
 */


public class MessageUnsubscribe extends ScribeMessage implements Serializable
{
    /**
     * Contructor
     *
     * @param addr the address of the scribe receiver.
     * @param source the node generating the message.
     * @param tid the topic to which this message refers to.
     * @param c the credentials associated with the mesasge.
     */
    public 
	MessageUnsubscribe( Address addr, NodeHandle source, 
			    NodeId tid, Credentials c ){
	super( addr, source, tid, c );
    }
    
    /**
     * This method is called whenever the scribe node receives a message for 
     * itself and wants to process it. The processing is delegated by scribe 
     * to the message.
     * 
     * @param scribe the scribe application.
     * @param topic the topic within the scribe application.
     */
    public void 
	handleDeliverMessage( Scribe scribe, Topic topic ) {

	NodeHandle handle = m_source;

	if ( topic != null ) {
	    // remove source node from children if it isnt us
	    if(!m_source.getNodeId().equals( scribe.getNodeId() ) ) {
		topic.removeChild( handle, this );
	    }
	    // if its us, then that means last application has left
	    // the topic group
	    
	    else {
		// only if we have no subscribing apps & if we have no children
		// then send the unsubscribe message to the parent
		
		if ( !topic.hasSubscribers() && !topic.hasChildren() ) {
		    // tell multicast tree parent to remove local node
		    NodeHandle parent = topic.getParent();
		    
		    if ( parent != null ) {
			Credentials cred = scribe.getCredentials();
			SendOptions opt = scribe.getSendOptions();
			
			//make a new message and send this thru scribe
			ScribeMessage msg = 
			    scribe.makeUnsubscribeMessage( m_topicId, cred );
			msg.setData((Serializable) this.getData() );
			
			// send directly to parent
			scribe.routeMsgDirect( parent, msg, cred, opt );
			
			//we no longer need the topic and is good to remove it
			topic.removeFromScribe();
			
		    }
		    else {
			// if parent unknown then set waiting flag and wait until 
			// first event arrives
			
			// make sure it is not Topic manager
			if( topic.isTopicManager() ){
			    topic.removeFromScribe();
			}
			else{
			    topic.waitUnsubscribe( true );
			}
		    }
		}
	    }
	    
	}
	else {
	} // if topic unknown, error
    }

    /**
     * This method is called whenever the scribe node forwards a message in 
     * the scribe network. The processing is delegated by scribe to the 
     * message.
     * 
     * @param scribe the scribe application.
     * @param topic the topic within the scribe application.
     *
     * @return true if the message should be routed further, false otherwise.
     */
    public boolean 
	handleForwardMessage( Scribe scribe, Topic topic ) {
	return true;
    }

    public String toString() {
	return new String( "UNSUBSCRIBE MSG:" + m_source );
    }
}
