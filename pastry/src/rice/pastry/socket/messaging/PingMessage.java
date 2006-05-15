
package rice.pastry.socket.messaging;

import java.io.*;
import java.net.*;

import rice.environment.Environment;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.InputBuffer;
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
  
  static final long serialVersionUID = -1831848738223899227L;
  public static final short TYPE = 8;
  
  /**
   * Constructor
   */
  public PingMessage(/*SourceRoute outbound, SourceRoute inbound, */long start) {
    super(/*outbound, inbound,*/ start);
  }
  
  public PingMessage(InputBuffer buf) throws IOException {
    super(buf);
  }

  public String toString() {
    return "PingMessage";
  }

  public short getType() {
    return TYPE;
  }

}
