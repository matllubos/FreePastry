package rice.testharness.messaging;

import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;
import rice.testharness.*;

/**
 * An abstract class representing any message in the
 * TestHarness system.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public abstract class TestHarnessMessage implements Message, ScribeContent {
  
  public int getPriority() {
    return Message.LOW_PRIORITY;
  }
}
