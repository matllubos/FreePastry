
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
public class IPAddressResponseMessage extends DatagramMessage {
  public static final short TYPE = 3;

  protected InetSocketAddress address;
  
  /**
   * Constructor
   */
  public IPAddressResponseMessage(InetSocketAddress address, long start) {
    super(start);
    
    this.address = address;
  }
  
  public IPAddressResponseMessage(InputBuffer buf) throws IOException {
    super(buf.readLong());

    byte[] addr = new byte[4];
    buf.read(addr);
    
    this.address = new InetSocketAddress(InetAddress.getByAddress(addr), buf.readInt());
  }

  public InetSocketAddress getAddress() {
    return address;
  }
  
  public String toString() {
    return "IPAddressResponseMessage";
  }

  public short getType() {
    return TYPE;
  }

  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeLong(start);
    byte[] addr = address.getAddress().getAddress();
    buf.write(addr,0,addr.length);
    buf.writeInt(address.getPort());
  }
}
