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
import rice.scribe.security.*;
import rice.scribe.maintenance.*;

import java.io.*;
import java.util.*;

/**
 *
 * MessageCreate is used whenever a Scribe node wants to create a
 * new topic. The message makes its way to the root (the node currently
 * closest to the topic Id) for the topic and takes care of instantiating 
 * the appropriate data structures on that node to keep track of the topic. 
 *
 * @version $Id$ 
 *
 * @author Romer Gil 
 * @author Eric Engineer
 */


public class MessageCreate extends ScribeMessage implements Serializable
{
    /**
     * Constructor.
     *
     * @param addr the address of the scribe receiver.
     * @param source the node generating the message.
     * @param topicId the topic to which this message refers to.
     * @param c the credentials associated with the mesasge.
     */
    public MessageCreate( Address addr, NodeHandle source, 
			  NodeId topicId, Credentials c) {
	super( addr, source, topicId, c );
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

	if (!scribe.getSecurityManager().verifyCanCreate(m_source,m_topicId)) {
	    //bad permissions from source node
	    return;
	}

	if( topic == null ) {
	    topic = new Topic( m_topicId, scribe );
	    // notify scribeObservers of this event, that a topic
	    // was created.
	    scribe.notifyScribeObservers(m_topicId);
	}
	
	topic.addToScribe();
	topic.topicManager( true );
	
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
	return new String( "CREATE MSG:" + m_source );
    }
}









