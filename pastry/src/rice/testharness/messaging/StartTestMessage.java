package rice.testharness.messaging;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;

import rice.testharness.*;

import rice.scribe.*;

import java.net.*;
import java.io.*;
import java.util.*;

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

  protected NodeId[] _nodes;

  /**
   * Constructor
   */
  public StartTestMessage(String runName, NodeId[] nodes) {
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

  public NodeId[] getNodes() {
    return _nodes;
  }
}
