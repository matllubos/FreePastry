
package rice.p2p.replication.messaging;

import rice.p2p.commonapi.*;
import rice.p2p.replication.*;

/**
 * @(#) ReplicationMessage.java
 *
 * This class the abstraction of a message used internally by replication.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public abstract class ReplicationMessage implements Message {
  
  // serialver for backward compatibility
  private static final long serialVersionUID = 2121558100279943464L;
  
  // the source of this message
  protected NodeHandle source;
  
  /**
   * Constructor which takes a unique integer Id
   *
   * @param source The source address
   * @param topic The topic
   */
  protected ReplicationMessage(NodeHandle source) {
    this.source = source;
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
    * Method which returns this messages' source address
   *
   * @return The source of this message
   */
  public NodeHandle getSource() {
    return source;
  }
  
}

