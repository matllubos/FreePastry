
package rice.pastry.socket;

import java.io.*;
import java.net.*;
import java.util.*;

import rice.pastry.*;
import rice.pastry.dist.*;
import rice.pastry.messaging.*;

/**
 * Class which represets a source route to a remote IP address.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class EpochInetSocketAddress implements Serializable {
  
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
    return ((epoch == ((EpochInetSocketAddress) o).epoch) && address.equals(((EpochInetSocketAddress) o).address));
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
}


