package rice.testharness.messaging;

import rice.p2p.commonapi.NodeHandle;

/**
 * A StartTest message in the TestHarness system.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class StartTestMessage extends TestHarnessMessage {

  /**
   * The content of the message as a byte array.
   */
  protected String _runName;

  protected NodeHandle[] _nodes;

  /**
   * Constructor
   */
  public StartTestMessage(String runName, NodeHandle[] nodes) {
    super();
    _runName = runName;
    _nodes = nodes;
  }

  public String toString() {
    return "StartTestMessage: " + _runName;
  }

  public String getRunName() {
    return _runName;
  }

  public NodeHandle[] getNodes() {
    return _nodes;
  }
}
