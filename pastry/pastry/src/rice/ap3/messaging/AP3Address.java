package rice.ap3.messaging;

import rice.pastry.messaging.Address;

/**
 * @(#) AP3Address.java
 *
 * The receiver address of the AP3 system. It is the
 * address that Pastry uses to deliver messages to AP3.
 * This class follows the Singleton pattern.
 *
 * @version $Id$
 * @author Gaurav Oberoi
 */
public class AP3Address implements Address {

  /**
   * The only instance of AP3Address ever created.
   */
  private static AP3Address _instance;

  /**
   * Code representing address.
   */
  private int _code = 0xabcdef44;

  /**
   * Returns the single instance of AP3Address.
   */
  public static AP3Address instance() {
    if(null == _instance) {
      _instance = new AP3Address();
    } 
    return _instance;
  }

  /**
   * Private constructor for singleton pattern.
   */
  private AP3Address() {}

  /**
   * Returns the code representing the address.
   */
  public int hashCode() { return _code; }
  
  /**
   * Determines if another object is equal to this one.
   * Simply checks if it is an instance of AP3Address
   * since there is only one instance ever created.
   */
  public boolean equals(Object obj) {
    return (obj instanceof AP3Address);
  }
}
