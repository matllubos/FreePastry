
package rice.pastry.socket.messaging;

import java.io.*;
import java.net.*;

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
  
  /**
   * Constructor
   */
  public IPAddressRequestMessage() {
    super(null, null);
  }
  
  public String toString() {
    return "IPAddressRequestMessage";
  }
}
