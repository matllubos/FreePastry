
package rice.p2p.replication.messaging;

import rice.p2p.commonapi.*;
import rice.p2p.replication.*;

/**
 * @(#) ReminderMessage.java
 *
 * This class represents a reminder for the replication to intiate maintaince.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class ReminderMessage extends ReplicationMessage {
  
  /**
   * Constructor which takes a unique integer Id
   *
   * @param source The source address
   */
  public ReminderMessage(NodeHandle source) {
    super(source);
  }
  
}

