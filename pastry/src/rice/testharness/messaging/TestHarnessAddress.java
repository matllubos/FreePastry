package rice.testharness.messaging;

import rice.pastry.messaging.Address;

/**
 * The receiver address of the TestHarness system. It is the
 * address that Pastry uses to deliver messages to TestHarness.
 * This class follows the Singleton pattern.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class TestHarnessAddress implements Address {

  /**
   * The only instance of TestHarnessAddress ever created.
   */
  private static TestHarnessAddress _instance;

  /**
   * Code representing address.
   */
  private int _code = 0x58ac73f2;

  /**
   * Returns the single instance of TestHarnessAddress.
   */
  public static TestHarnessAddress instance() {
    if(null == _instance) {
      _instance = new TestHarnessAddress();
    } 
    return _instance;
  }

  /**
   * Private constructor for singleton pattern.
   */
  private TestHarnessAddress() {}

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
    return (obj instanceof TestHarnessAddress);
  }
}
