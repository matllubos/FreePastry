
package rice.pastry.socket.messaging;

import java.io.*;
import java.net.*;

import rice.environment.Environment;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.pastry.socket.*;
import rice.pastry.*;

/**
* Class which represents a request for the external visible IP address
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class IPAddressRequestMessage extends DatagramMessage {
  
  public static final short TYPE = 2;
  
  /**
   * Constructor
   */
  public IPAddressRequestMessage(long start) {
    super(start);
  }
  
  public IPAddressRequestMessage(InputBuffer buf) throws IOException {
    super(buf.readLong());
  }

  public String toString() {
    return "IPAddressRequestMessage";
  }

  public short getType() {
    return TYPE;
  }

  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeLong(start);
  }
}
