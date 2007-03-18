/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 

package rice.p2p.scribe;

import rice.*;
import rice.environment.Environment;
import rice.p2p.commonapi.*;
import rice.p2p.scribe.rawserialization.*;

/**
 * @(#) Scribe.java
 *
 * This interface is exported by all instances of Scribe.  
 *
 * @version $Id$
 * @author Alan Mislove
 */
public interface Scribe extends Destructable {

  // ***************** Membership functions messages **************
  /**
   * Subscribes the given client to the provided topic.  Any message published
   * to the topic will be delivered to the Client via the deliver() method.
   *
   * @param topic The topic to subscribe to
   * @param client The client to give messages to
   */
  public void subscribe(Topic topic, ScribeClient client);

  /**
   * Subscribes the given client to the provided topic.  Any message published
   * to the topic will be delivered to the Client via the deliver() method.
   *
   * @param topic The topic to subscribe to
   * @param client The client to give messages to
   * @param content The content to include in the subscribe
   */
  public void subscribe(Topic topic, ScribeClient client, ScribeContent content);
  public void subscribe(Topic topic, ScribeClient client, RawScribeContent content);

  /**
   * Subscribes the given client to the provided topic.  Any message published
   * to the topic will be delivered to the Client via the deliver() method.
   *
   * @param topic The topic to subscribe to
   * @param client The client to give messages to
   * @param content The content to include in the subscribe
   * @param hint The first hop of the message ( Helpful to implement a centralized solution)
   */
  public void subscribe(Topic topic, ScribeClient client, ScribeContent content, NodeHandle hint);
  public void subscribe(Topic topic, ScribeClient client, RawScribeContent content, NodeHandle hint);
  /**
   * Unsubscribes the given client from the provided topic. 
   *
   * @param topic The topic to unsubscribe from
   * @param client The client to unsubscribe
   */
  public void unsubscribe(Topic topic, ScribeClient client);

  /**
   * Adds a child to the given topic
   * 
   * @param topic The topic to add the child to
   * @param child The child to add
   */
  public void addChild(Topic topic, NodeHandle child);

  /**
   * Removes a child from the given topic
   *
   * @param topic The topic to remove the child from
   * @param child The child to remove
   */
  public void removeChild(Topic topic, NodeHandle child);
  
  // ***************** Messaging functions ****************
  /**
   * Publishes the given message to the topic.
   *
   * @param topic The topic to publish to
   * @param content The content to publish
   */
  public void publish(Topic topic, ScribeContent content);
  public void publish(Topic topic, RawScribeContent content);

  /**
   * Anycasts the given content to a member of the given topic
   *
   * @param topic The topic to anycast to
   * @param content The content to anycast
   */
  public void anycast(Topic topic, ScribeContent content);
  public void anycast(Topic topic, RawScribeContent content);

  /**
   * Anycasts the given content to a member of the given topic
   * 
   * The hint helps us to implement centralized algorithms where the hint is the 
   * cachedRoot for the topic. Additionally it enables us to do more fancy 
   * anycasts that explore more portions of the Scribe tree
   *
   * @param topic The topic to anycast to
   * @param content The content to anycast
   * @param hint the first hop of the Anycast
   */
  public void anycast(Topic topic, ScribeContent content, NodeHandle hint);
  public void anycast(Topic topic, RawScribeContent content, NodeHandle hint);
     
  // *********************** Query functions *********************  
  /**
   * Returns whether or not this Scribe is the root for the given topic
   *
   * @param topic The topic in question
   * @return Whether or not we are currently the root
   */
  public boolean isRoot(Topic topic);

  /**
   * Returns the list of children for a given topic
   *
   * @param topic The topic to return the children of
   * @return The children of the topic
   */
  public NodeHandle[] getChildren(Topic topic);

  /**
   * Returns the parent node for a given topic
   * 
   * @param myTopic The topic to return the parent of
   * @return The parent of the topic
   */
  public NodeHandle getParent(Topic myTopic);
  
  /**
   * Returns the list of topics the given client is subscribed
   * to.
   *
   * @param client The client in question
   * @return The list of topics
   */
  public Topic[] getTopics(ScribeClient client);

  /**
   * This returns the topics for which the parameter 'parent' is a Scribe tree
   * parent of the local node
   */
  public Topic[] topicsAsParent(NodeHandle parent);

  /**
   * This returns the topics for which the parameter 'child' is a Scribe tree
   * child of the local node
   */
  public Topic[] topicsAsChild(NodeHandle child);
  
  
  public int numChildren(Topic topic);

  /**
   *  Returns true if there is a TopicManager object corresponding to this topic
   */
  public boolean containsTopic(Topic myTopic);

  public boolean containsChild(Topic myTopic, NodeHandle child);
  
  // ********************* Application management functions **************
  /**
   * Returns the current policy for this scribe object
   * 
   * @return The current policy for this scribe
   */
  public ScribePolicy getPolicy();

  /**
   * Sets the current policy for this scribe object
   *
   * @param policy The current policy for this scribe
   */
  public void setPolicy(ScribePolicy policy);

  public Environment getEnvironment();

  public void destroy();

  public void setContentDeserializer(ScribeContentDeserializer deserializer);
  public ScribeContentDeserializer getContentDeserializer();

}

