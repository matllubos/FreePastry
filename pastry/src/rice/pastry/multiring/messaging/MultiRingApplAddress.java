package rice.pastry.multiring.messaging;

import rice.pastry.messaging.Address;

/**
 * The application address for MultiRingAppl.
 * This class follows the Singleton pattern.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class MultiRingApplAddress implements Address {

  /**
   * The only instance of MultiRingApplAddress ever created.
   */
  private static MultiRingApplAddress _instance;

  /**
   * Code representing address.
   */
  private int _code = 0x5ac98bb1;

  /**
   * Returns the single instance of MultiRingApplAddress.
   */
  public static MultiRingApplAddress instance() {
    if(null == _instance) {
      _instance = new MultiRingApplAddress();
    }
    return _instance;
  }

  /**
   * Private constructor for singleton pattern.
   */
  private MultiRingApplAddress() {}

  /**
   * Returns the code representing the address.
   */
  public int hashCode() { return _code; }

  /**
   * Determines if another object is equal to this one.
   * Simply checks if it is an instance of MultiRingApplAddress
   * since there is only one instance ever created.
   */
  public boolean equals(Object obj) {
    return (obj instanceof MultiRingApplAddress);
  }
}
