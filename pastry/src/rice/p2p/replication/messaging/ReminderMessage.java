
package rice.p2p.replication.messaging;

import java.io.IOException;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
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
  public static final short TYPE = 1;
  
  /**
   * Constructor which takes a unique integer Id
   *
   * @param source The source address
   */
  public ReminderMessage(NodeHandle source) {
    super(source);
  }
  
  /***************** Raw Serialization ***************************************/
  public short getType() {
    return TYPE;
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    throw new RuntimeException("serialize() not supported in MaintenanceMessage"); 
  }  
}

