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


package rice.scribe;

import rice.pastry.security.Credentials;
import rice.pastry.messaging.Message;
import rice.pastry.NodeId;

/**
 * @(#) IScribe.java
 *
 * This interface provides the external Scribe API. All applications
 * built on top of Scribe layer interact with the underlying Scribe object
 * through this API.
 *
 * @version $Id$
 *
 * @author Romer Gil
 * @author Eric Engineer
 * @author Atul Singh
 * @author Animesh Nandi
 */

public interface IScribe
{

    /**
     * Creates a topic if the credentials are valid.  Nodes must then subscribe
     * to this topic in order to get information published to it.
     *
     * @param    cred
     * The credentials of the entity creating the topic  
     *
     * @param    topicID       
     * The ID of the topic to be created
     *
     */
    public void create( NodeId topicID, Credentials cred );

    


    /**
     * Subscribe to a particular topic.  When a node becomes subscribed to a 
     * particular topic, it receives all messages published to that topic.
     *
     * @param    cred
     * The credentials of the entity subscribing to the topic
     *
     * @param    topicID        
     * The ID of the topic to subscribe to.
     *
     * @param    subscriber
     * The application subscribing to the topic
     *
     */
    public void subscribe( NodeId topicID, IScribeApp subscriber, Credentials cred);



    
    /**
     * Unsubscribe from a topic.  After a node is unsubscribed from a topic, it
     * will no longer receive messages from that topic.
     *
     * @param    cred
     * The credentials of the entity unsubscribing from the topic
     *
     * @param    topicID        
     * The ID of the topic to be unsubscribed from.
     *
     * @param    subscriber
     * The application unsubscribing from the topic.  Use null if 
     * not directly called by an application.
     */
    public void unsubscribe( NodeId topicID, IScribeApp subscriber, Credentials cred );



    
    /**
     * Publish information to a topic.  Data will be delivered to All nodes 
     * that are subscribed to the topic.  The message will trickle from
     * the root of the multicast tree for the topic DOWN the tree, with each
     * node sending this message to its children for the topic.
     *
     * @param   cred
     * The credentials of the entity publishing to the topic.
     *
     * @param   topicID         
     * The ID of the topic to publish to.
     *
     * @param   obj           
     * The information that is to be published.
     * This should be serializable.
     */
    public void publish( NodeId topicID, Object obj, Credentials cred );
    


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
    public void scheduleHB();



    /**
     * The tree repair event for a particular topic in Scribe is triggered
     * when a node misses a certain treeRepairThreshold number of heartbeat 
     * messages from its parent for the topic. This threshold value can be 
     * set using this method.
     *
     * @param    value
     * The value for the treeRepairThreshold.
     */
    public void setTreeRepairThreshold(int value);
    
    
    /**
     * Generate a unique id for the topic, which will determine its rendezvous
     * point. This is a helper method. Applications can use their own 
     * methods to generate TopicId.
     *
     * @param topicName 
     * The name of the topic (unique to the local node)
     *
     * @return the TopicId
     */
    public NodeId generateTopicId(String topicName);

}








































