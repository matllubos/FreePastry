
package rice.p2p.scribe.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;

/**
 * @(#) MaintenanceMessage.java
 *
 * The maintenance message.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class MaintenanceMessage extends ScribeMessage {
  
  /**
   * Constructor 
   *
   * @param id The unique id
   * @param source The source address
   * @param dest The destination address
   */
  public MaintenanceMessage() {
    super(null, null);
  }
    
  public String toString() {
    return "MaintenanceMessage";
  }
  
}

