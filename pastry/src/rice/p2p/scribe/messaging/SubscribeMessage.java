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

package rice.p2p.scribe.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;

/**
 * @(#) SubscribeMessage.java The subscribe message.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class SubscribeMessage extends AnycastMessage {

  /**
   * The original subscriber
   */
  protected NodeHandle subscriber;

  /**
   * The previous parent
   */
  protected Id previousParent;

  /**
   * The id of this message
   */
  protected int id;

  /**
   * Constructor which takes a unique integer Id
   *
   * @param source The source address
   * @param topic The topic
   * @param id The UID for this message
   * @param content The content
   */
  public SubscribeMessage(NodeHandle source, Topic topic, int id, ScribeContent content) {
    this(source, topic, null, id, content);
  }

  /**
   * Constructor which takes a unique integer Id
   *
   * @param source The source address
   * @param topic The topic
   * @param id The UID for this message
   * @param content The content
   * @param previousParent The parent on this topic who died
   */
  public SubscribeMessage(NodeHandle source, Topic topic, Id previousParent, int id, ScribeContent content) {
    super(source, topic, content);

    this.id = id;
    this.subscriber = source;
    this.previousParent = previousParent;
  }

  /**
   * Returns the node who is trying to subscribe
   *
   * @return The node who is attempting to subscribe
   */
  public NodeHandle getSubscriber() {
    return subscriber;
  }

  /**
   * Returns the node who is trying to subscribe
   *
   * @return The node who is attempting to subscribe
   */
  public Id getPreviousParent() {
    return previousParent;
  }

  /**
   * Returns this subscribe lost message's id
   *
   * @return The id of this subscribe lost message
   */
  public int getId() {
    return id;
  }

  /**
   * Returns a String represneting this message
   *
   * @return A String of this message
   */
  public String toString() {
    return "[SubscribeMessage " + topic + " subscriber " + subscriber + " ID " + id + "]";
  }

}

