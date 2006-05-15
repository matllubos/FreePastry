
package rice.p2p.replication.manager.messaging;

import rice.p2p.commonapi.*;

/**
 * @(#) ReminderMessage.java
 *
 * This class represents a reminder for the replication to intiate maintaince.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class ReminderMessage implements Message {
  
  /**
   * Constructor which takes a unique integer Id
   *
   * @param source The source address
   */
  public ReminderMessage() {
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
  public byte getPriority() {
    return MEDIUM_PRIORITY;
  }
  
}

