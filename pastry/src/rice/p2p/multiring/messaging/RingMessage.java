
package rice.p2p.multiring.messaging;

import rice.p2p.commonapi.*;
import rice.p2p.multiring.*;
import rice.p2p.scribe.*;

/**
 * @(#) RingMessage.java
 *
 * This class the abstraction of a message used internally by the multiring hierarchy.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class RingMessage implements Message, ScribeContent {
  
  // serialver for backward compatibility
  private static final long serialVersionUID = -7097995807488121199L;

  /**
   * The target of this ring message
   */
  protected RingId id;
  
  /**
   * The internal message to be sent
   */
  protected Message message;
  
  /**
   * The name of the application which sent this message
   */
  protected String application;

  /**
   * Constructor which takes a unique integer Id
   *
   * @param id The unique id
   * @param source The source address
   * @param dest The destination address
   */
  public RingMessage(RingId id, Message message, String application) {
    this.id = id;
    this.message = message;
    this.application = application;
  }
  
  /**
   * Method which should return the priority level of this message.  The messages
   * can range in priority from 0 (highest priority) to Integer.MAX_VALUE (lowest) -
   * when sending messages across the wire, the queue is sorted by message priority.
   * If the queue reaches its limit, the lowest priority messages are discarded.  Thus,
   * applications which are very verbose should have LOW_PRIORITY or lower, and
   * applications which are somewhat quiet are allowed to have MEDIUM_PRIORITY or
   * possibly even HIGH_PRIORITY.
   *
   * @return This message's priority
   */
  public int getPriority() {
    return message.getPriority();
  }

  /**
   * Method which returns this messages'  id
   *
   * @return The id of this message
   */
  public RingId getId() {
    return id;
  }

  /**
   * Method which returns this messages' internal message
   *
   * @return The internal message of this message
   */
  public Message getMessage() {
    return message;
  }

  /**
   * Method which returns this messages' applicaiton name
   *
   * @return The application name of this message
   */
  public String getApplication() {
    return application;
  }
}

