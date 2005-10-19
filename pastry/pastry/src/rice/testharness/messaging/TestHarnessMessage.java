package rice.testharness.messaging;

import rice.pastry.messaging.*;

import rice.testharness.*;

/**
 * An abstract class representing any message in the
 * TestHarness system.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public abstract class TestHarnessMessage extends Message {

  /**
   * Constructor
   */
  public TestHarnessMessage() {
    super(TestHarnessAddress.instance());
  }

}
