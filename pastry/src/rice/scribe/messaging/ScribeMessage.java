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

import rice.scribe.*;
import rice.scribe.maintenance.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.*;

import java.io.*;

/**
 *
 * This is an abstract implementation of a Scribe message object.
 *
 * @version $Id$ 
 * @author Romer Gil 
 */


public abstract class ScribeMessage extends Message implements Serializable
{
    /**
     * This is the information inside the message.
     * Should be serializable.
     */
    private Object m_data;

    /**
     * The ID of the topic that this message refers to.
     * Should be serializable.
     */
    protected NodeId m_topicId;

    /**
     * The ID of the source of this message.
     * Should be serializable.
     */
    protected NodeHandle m_source;

    /**
     * Constructor
     *
     * @param addr the address of the scribe receiver.
     * @param source the node generating the message.
     * @param topicId the topic to which this message refers to.
     * @param c the credentials associated with the mesasge.
     */
    public ScribeMessage( Address addr, NodeHandle source, 
			  NodeId tid, Credentials c ) {
	super( addr, c );
	m_topicId = tid;
	m_source = source;
    }
     
    /**
     * This method is called whenever the scribe node receives a message for 
     * itself and wants to process it. The processing is delegated by scribe 
     * to the message.
     * 
     * @param scribe the scribe application
     * @param topic the topic within the scribe application
     */
    public abstract void 
	handleDeliverMessage( Scribe scribe, Topic topic );
    
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
    public abstract boolean 
	handleForwardMessage( Scribe scribe, Topic topic );

    /**
     * Sets the data contained in the message.
     *
     * @param data the data contained in the message.
     */
    public void setData( Object data ) { m_data = data; }


    /**
     * Gets the data inside of the ScribeMessage object.
     * 
     * @return the data contained in the message.
     */
    public Object getData() { return m_data; }

    /**
     * Returns the topicId associated with the message.
     *
     * @return topicId to which the message was posted.
     */
    public NodeId getTopicId() { return m_topicId; }

    /**
     * Returns the nodeId of the node that generated the message.
     *
     * @return the handle to the source node.
     */
    public NodeHandle getSource() { return m_source; }

    public abstract String toString();
}








