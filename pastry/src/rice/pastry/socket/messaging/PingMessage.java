
package rice.pastry.socket.messaging;

import java.io.*;
import java.net.*;

import rice.pastry.socket.*;
import rice.pastry.*;

/**
 * Class which represents a "ping" message sent through the
 * socket pastry system.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class PingMessage extends DatagramMessage {

  /**
   * Constructor
   */
  public PingMessage(SourceRoute outbound, SourceRoute inbound) {
    super(outbound, inbound);
  }
  
  public String toString() {
    return "PingMessage";
  }
}
