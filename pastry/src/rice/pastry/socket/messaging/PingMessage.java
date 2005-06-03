
package rice.pastry.socket.messaging;

import java.io.*;
import java.net.*;

import rice.environment.Environment;
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
  public PingMessage(SourceRoute outbound, SourceRoute inbound, Environment env) {
    super(outbound, inbound, env);
  }
  
  public String toString() {
    return "PingMessage";
  }
}
