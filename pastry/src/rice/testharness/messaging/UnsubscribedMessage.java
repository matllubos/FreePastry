package rice.testharness.messaging;

import rice.p2p.commonapi.*;

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
  protected NodeHandle _source;

  /**
   * Constructor
   */
  public UnsubscribedMessage(NodeHandle source) {
    super();
    _source = source;
  }

  public String toString() {
    return "UnsubscribedMessage: " + _source;
  }

  public NodeHandle getSource() {
    return _source;
  }

}
