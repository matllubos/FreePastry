package rice.past.messaging;

import rice.pastry.messaging.Address;

/**
 * @(#) PASTAddress.java
 *
 * The application address for PAST.
 * This class follows the Singleton pattern.
 *
 * @version $Id$
 * @author Charles Reis
 */
public class PASTAddress implements Address {

  /**
   * The only instance of PASTAddress ever created.
   */
  private static PASTAddress _instance;

  /**
   * Code representing address.
   */
  private int _code = 0xcfb32a5d;

  /**
   * Returns the single instance of PASTAddress.
   */
  public static PASTAddress instance() {
    if(null == _instance) {
      _instance = new PASTAddress();
    } 
    return _instance;
  }

  /**
   * Private constructor for singleton pattern.
   */
  private PASTAddress() {}

  /**
   * Returns the code representing the address.
   */
  public int hashCode() { return _code; }
  
  /**
   * Determines if another object is equal to this one.
   * Simply checks if it is an instance of PASTAddress
   * since there is only one instance ever created.
   */
  public boolean equals(Object obj) {
    return (obj instanceof PASTAddress);
  }
}
