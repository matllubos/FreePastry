package rice.testharness.messaging;

import rice.p2p.commonapi.*;

import rice.testharness.*;

/**
 * A message in the TestHarness system informing the
 * node in charge that the local node has subscribed to
 * the TestHarness SCRIBE group.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class SubscribedMessage extends TestHarnessMessage {

  /**
   * The source of the message.
   */
  protected NodeHandle _source;

  /**
   * Constructor
   */
  public SubscribedMessage(NodeHandle source) {
    super();
    _source = source;
  }

  public String toString() {
    return "SubscribedMessage: " + _source;
  }

  public NodeHandle getSource() {
    return _source;
  }

}
