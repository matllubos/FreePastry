
package rice.p2p.commonapi;

import java.io.*;

/**
 * @(#) Message.java
 *
 * This interface is an abstraction of a message which is sent through
 * the common API-based system.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Peter Druschel
 */
public interface Message extends Serializable {
  
  // different priority levels
  public static final byte MAX_PRIORITY = 0;
  public static final byte HIGH_PRIORITY = 5;
  public static final byte MEDIUM_HIGH_PRIORITY = 10;
  public static final byte MEDIUM_PRIORITY = 15;
  public static final byte MEDIUM_LOW_PRIORITY = 20;
  public static final byte LOW_PRIORITY = 25;

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
  public byte getPriority();
  
}


