
package rice.pastry.socket.messaging;

import java.net.*;
import java.io.*;

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
public class PingResponseMessage extends DatagramMessage {
      
  public static final short TYPE = 9;

  /**
  * Constructor
  */
  public PingResponseMessage(/*SourceRoute outbound, SourceRoute inbound, */long start) {
    super(/*outbound, inbound, */start);
  }
  
  public PingResponseMessage(InputBuffer buf) throws IOException {
    super(buf);
  }

  public String toString() {
    return "PingResponseMessage";
  }

  public short getType() {
    return TYPE;
  }
}
