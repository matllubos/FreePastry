package rice.splitstream.messaging;

import rice.pastry.messaging.Address;

/**
 * The application address for SplitStream.
 * This class follows the Singleton pattern.
 *
 * @version $Id$
 * @author Alan Mislove
 *
 * @deprecated This version of SplitStream has been deprecated - please use the version
 *   located in the rice.p2p.splitstream package.
 */
public class SplitStreamAddress implements Address {

  /**
   * The only instance of SplitStreamAddress ever created.
   */
  private static SplitStreamAddress _instance;

  /**
   * Code representing address.
   */
  private int _code = 0x29b53bc0;

  /**
   * Returns the single instance of SplitStreamAddress.
   */
  public static SplitStreamAddress instance() {
    if(null == _instance) {
      _instance = new SplitStreamAddress();
    }
    return _instance;
  }

  /**
   * Private constructor for singleton pattern.
   */
  private SplitStreamAddress() {}

  /**
   * Returns the code representing the address.
   */
  public int hashCode() { return _code; }

  /**
   * Determines if another object is equal to this one.
   * Simply checks if it is an instance of SplitStreamAddress
   * since there is only one instance ever created.
   */
  public boolean equals(Object obj) {
    return (obj instanceof SplitStreamAddress);
  }
}
