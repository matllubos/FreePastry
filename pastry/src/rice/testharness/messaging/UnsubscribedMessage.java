package rice.testharness.messaging;

import rice.pastry.*;
import rice.pastry.messaging.*;

import rice.testharness.*;

/**
 * A message in the TestHarness system informing the
 * node in charge that the local node has unsubscribed from
 * the TestHarness SCRIBE group.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class UnsubscribedMessage extends TestHarnessMessage {

  /**
   * The source of the message.
   */
  protected NodeId _source;

  /**
   * Constructor
   */
  public UnsubscribedMessage(NodeId source) {
    super();
    _source = source;
  }

  public String toString() {
    return "UnsubscribedMessage: " + _source;
  }

  public NodeId getSource() {
    return _source;
  }

}
