package rice.testharness.messaging;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;

import rice.testharness.*;

import rice.scribe.*;

import java.net.*;
import java.io.*;
import java.util.*;
import java.lang.reflect.*;

/**
 * A InitTest message in the TestHarness system.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class InitTestMessage extends TestHarnessMessage {

  /**
   * The content of the message as a byte array.
   */
  protected String _testName;
  protected String _runName;

  /**
   * Constructor
   */
  public InitTestMessage(String runName, String testName) {
    super();
    _testName = testName;
    _runName = runName;
  }

  public String toString() {
    return "InitTestMessage: " + _testName + " " + _runName;
  }

  public String getRunName() {
    return _runName;
  }

  public String getTestName() {
    return _testName;
  }
}
