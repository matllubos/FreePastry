
package rice.pastry.socket;

import java.io.*;
import java.net.*;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;

/**
 * Class which represets a source route to a remote IP address.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class EpochInetSocketAddress implements Serializable {
  
  /**
   * 
   */
  private static final long serialVersionUID = 2081191512212313338L;

  // a static epoch which indicates an unknown (and unmattering) epoch number
  public static final long EPOCH_UNKNOWN = -1;
    
  // the address
  protected InetSocketAddress address;
  
  // the epoch number of the remote node
  protected long epoch;
  
  /**
   * Constructor - don't use this unless you know what you are doing
   *
   * @param address The remote address
   */
  public EpochInetSocketAddress(InetSocketAddress address) {
    this(address, EPOCH_UNKNOWN);
  }  
  
  /**
   * Constructor
   *
   * @param address The remote address
   * @param epoch The remote epoch
   */
  public EpochInetSocketAddress(InetSocketAddress address, long epoch) {
    this.address = address;
    this.epoch = epoch;
  }  

  /**
   * Returns the hashCode of this source route
   *
   * @return The hashCode
   */
  public int hashCode() {
    return (int) (address.hashCode() ^ epoch);
  }
  
  /**
   * Checks equaltiy on source routes
   *
   * @param o The source route to compare to
   * @return The equality
   */
  public boolean equals(Object o) {
    if (o == null) return false;
    EpochInetSocketAddress that = (EpochInetSocketAddress)o;
    if (this.epoch != that.epoch) return false;
    return (this.address.equals(that.address));
  }
  
  /**
    * Internal method for computing the toString of an array of InetSocketAddresses
   *
   * @param path The path
   * @return THe string
   */
  public String toString() {
    return address.toString() + " [" + epoch + "]";
  }
  
  /**
   * Method which returns the address of this address
   *
   * @return The address
   */
  public InetSocketAddress getAddress() {
    return address;
  }
  
  /**
   * Method which returns the epoch of this address
   *
   * @return The epoch
   */
  public long getEpoch() {
    return epoch;
  }

  /**
   *   EpochInetSocketAddress: (IPV4):
   *   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   *   + InetAddress                                                   +
   *   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   *   + port (int)                                                    +
   *   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   *   + epoch (long)                                                  +
   *   +                                                               +
   *   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   *  
   * @param buf
   * @return
   * @throws IOException
   */
  public static EpochInetSocketAddress build(InputBuffer buf) throws IOException {
    byte[] addrBytes = new byte[4];
    buf.read(addrBytes);
    InetAddress addr = InetAddress.getByAddress(addrBytes);
    int port = buf.readInt();
//    try {
      InetSocketAddress saddr = new InetSocketAddress(addr, port);
      long epoch = buf.readLong();
      return new EpochInetSocketAddress(saddr, epoch);
//    } catch (IllegalArgumentException iae) {
//      throw iae; // just to set a breakpoint 
//    }
  }

  /**
   *   EpochInetSocketAddress: (IPV4):
   *   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   *   + InetAddress                                                   +
   *   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   *   + port (int)                                                    +
   *   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   *   + epoch (long)                                                  +
   *   +                                                               +
   *   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   * 
   * @param buf
   */
  public void serialize(OutputBuffer buf) throws IOException {
    buf.write(address.getAddress().getAddress(),0,4);
    buf.writeInt(address.getPort());
    buf.writeLong(epoch);    
  }
}


