/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

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

package rice.p2p.scribe;

import rice.*;
import rice.p2p.commonapi.*;

/**
 * @(#) Scribe.java
 *
 * This interface is exported by all instances of Scribe.  
 *
 * @version $Id$
 * @author Alan Mislove
 */
public interface Scribe {

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

  /**
   * Unsubscribes the given client from the provided topic. 
   *
   * @param topic The topic to unsubscribe from
   * @param client The client to unsubscribe
   */
  public void unsubscribe(Topic topic, ScribeClient client);

  /**
   * Publishes the given message to the topic.
   *
   * @param topic The topic to publish to
   * @param content The content to publish
   */
  public void publish(Topic topic, ScribeContent content);

  /**
   * Anycasts the given content to a member of the given topic
   *
   * @param topic The topic to anycast to
   * @param content The content to anycast
   */
  public void anycast(Topic topic, ScribeContent content);

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
  
  /**
   * Returns the list of topics the given client is subscribed
   * to.
   *
   * @param client The client in question
   * @return The list of topics
   */
  public Topic[] getTopics(ScribeClient client);
}

