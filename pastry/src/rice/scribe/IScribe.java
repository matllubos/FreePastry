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
import rice.pastry.NodeHandle;

import java.util.Vector;

import java.io.*;

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
     * Registers the IScribeApp to the Scribe substrate. This is 
     * required for the IScribeApp to get the upcall scribeIsReady()
     * notifying it that the underlying Scribe substrate is ready. The 
     * IScribeApp should call create, join, leave, multicast
     * only after they are notified that the underlying Scribe substrate
     * is ready, otherwise these operations fail.
     */
    public void registerApp(IScribeApp app);


    /**
     * Creates a group/topic if the credentials are valid. Nodes must then join
     * this group in order to get information multicast to it.
     *
     * @param    cred
     * The credentials of the entity creating the group  
     *
     * @param    groupID       
     * The ID of the group to be created
     *
     * @return true if the operation was successful, false if the operation
     *         failed because the underlying Scribe substrate was not ready. 
     *
     */
    public boolean create( NodeId groupID, Credentials cred );

    


    /**
     * Joins a multicast group/topic.  When a node joins a multicast group,
     * it receives all messages multicast to that group.
     *
     * @param    cred
     * The credentials of the entity joining the group
     *
     * @param    groupID        
     * The ID of the group to join to
     *
     * @param    subscriber
     * The application joining the group
     *
     * @return true if the operation was successful, false if the operation
     *         failed because the underlying Scribe substrate was not ready. 
     *
     */
    public boolean join( NodeId groupID, IScribeApp subscriber, Credentials cred);

    /**
     * Joins a multicast group/topic.  When a node joins a multicast group,
     * it receives all messages multicast to that group. An application can
     * specify additional data to be sent with the SUBSCRIBE message.
     *
     * @param    cred
     * The credentials of the entity joining the group
     *
     * @param    groupID        
     * The ID of the group to join to
     *
     * @param    subscriber
     * The application joining the group
     *
     * @param obj
     * Additional data to be passed with the SUBSCRIBE msg, specific to
     * an application. Should be serializable.
     *
     * @return true if the operation was successful, false if the operation
     *         failed because the underlying Scribe substrate was not ready. 
     *
     */
    public boolean join( NodeId groupID, IScribeApp subscriber, Credentials cred, Serializable obj);

    
    /**
     * Leaving a multicast group/topic. After a node leaves a group, it
     * will no longer receive messages multicast to this group.
     *
     * @param    cred
     * The credentials of the entity leaving the group
     *
     * @param    groupID        
     * The ID of the group to leave
     *
     * @param    subscriber
     * The application leaving the group.  Use null if 
     * not directly called by an application.
     *
     * @return true if the operation was successful, false if the operation
     *         failed because the underlying Scribe substrate was not ready. 
     */
    public boolean leave( NodeId groupID, IScribeApp subscriber, Credentials cred );



    
    /**
     * Multicast information to a group/topic.  Data will be delivered 
     * to All nodes that have joined the group.  The message will trickle from
     * the root of the multicast tree for the group DOWN the tree, with each
     * node sending this message to its children for the group.
     *
     * @param   cred
     * The credentials of the entity multicasting to the group
     *
     * @param   groupID         
     * The ID of the group to multicast.
     *
     * @param   obj           
     * The information that is to be multicast.
     * This should be serializable.
     *
     * @return true if the operation was successful, false if the operation
     *         failed because the underlying Scribe substrate was not ready.
     */
    public boolean multicast( NodeId groupID, Object obj, Credentials cred );
    



    /**
     * Anycast to a group/topic. Data will be delivered to 'ANY' one node
     * which has joined the group. The handling of anycast message is 
     * left to the application. The applications may do DFS of its subtree
     * to evaluate some predicates to find out a node having desirable
     * properties.
     *
     * @param   groupID         
     * The ID of the group to anycast.
     *
     * @param   obj           
     * The information that is to be included in anycast message.
     * This should be serializable.
     *
     * @param   cred
     * The credentials of the entity anycasting to the group.
     *
     * @return true if the operation was successful, false if the operation
     *         failed because the underlying Scribe substrate was not ready.
     */
    public boolean anycast( NodeId groupID, Object obj, Credentials cred );
    



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
     *
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




    /** 
     * Returns the local node's parent in this topic's multicast
     * tree.
     *
     * @param topicId
     * The id of the topic.
     * @return the parent node.
     */
    public NodeHandle getParent(NodeId topicId);


    /**
     * Sets the parent for the topic specified by topicId.
     *
     * @param parent The new parent for the topic
     *
     * @param topicId the topic for which this parent is set
     *
     * @return true if operation was successful, false otherwise
     */
    public boolean setParent(NodeHandle parent, NodeId topicId);


    /** 
     * This returns the most current view of the children in this 
     * topic's multicast subtree rooted at the local node. 
     *
     * @param topicId
     * The id of the topic.
     *
     * @return vector of children nodehandles.
     */
    public Vector getChildren(NodeId topicId);


    /** 
     * This returns the number of children in this 
     * topic's multicast subtree rooted at the local node. 
     *
     * @param topicId
     * The id of the topic.
     *
     * @return the number of children of local node for this topic.
     */
    public int numChildren(NodeId topicId);




    /**
     * Add a node as a child in the children table for
     * a topic.
     *
     * @param child  the child to be added
     *
     * @param topicId the topic for which this child is added
     *
     * @return true if operation was successful, false otherwise
     *
     */
    public boolean addChild(NodeHandle child, NodeId topicId);



    /**
     * Removes a node as a child from the children table for
     * a topic.
     *
     * @param child  the child to be removed
     *
     * @param topicId the topic for which this child is removed
     *
     * @return true if operation was successful, false otherwise
     *
     */
    public boolean removeChild(NodeHandle child, NodeId topicId);


   
}








































