package rice.testharness.messaging;

import rice.p2p.commonapi.NodeHandle;

/**
 * A CollectResultsMessage message in the TestHarness system.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class CollectResultsMessage extends TestHarnessMessage {

  /**
   * The content of the message as a byte array.
   */
  protected String _runName;

  protected NodeHandle _source;

  /**
   * Constructor
   */
  public CollectResultsMessage(String runName, NodeHandle source) {
    super();
    _runName = runName;
    _source = source;
  }

  public String toString() {
    return "CollectResultsMessage: " + _runName;
  }

  public String getRunName() {
    return _runName;
  }

  public NodeHandle getSource() {
    return _source;
  }
}
