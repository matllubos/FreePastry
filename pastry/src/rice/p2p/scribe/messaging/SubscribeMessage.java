
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

