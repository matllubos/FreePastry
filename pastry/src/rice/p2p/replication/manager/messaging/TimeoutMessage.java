
package rice.p2p.replication.manager.messaging;

import rice.p2p.commonapi.*;

/**
 * @(#) TimeoutMessage.java
 *
 * This class represents a timeout for a client fetch call
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class TimeoutMessage implements Message {
  
  /**
   * THe unique id of this message
   */
  protected Id id;
  
  /**
   * Constructor which takes a unique integer Id
   *
   * @param uid The uid
   */
  public TimeoutMessage(Id id) {
    this.id = id;
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
    return MEDIUM_PRIORITY;
  }
  
  /**
   * Returns the id of this message
   *
   * @return The id of this message
   */
  public Id getId() {
    return id;
  }
}

