
package rice.pastry.socket.messaging;

import java.net.*;
import java.io.*;

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
      
  /**
  * Constructor
  */
  public PingResponseMessage(SourceRoute outbound, SourceRoute inbound, long start) {
    super(outbound, inbound);
    
    this.start = start;
  }
  
  public String toString() {
    return "PingResponseMessage";
  }
}
