package rice.testharness.messaging;

import rice.pastry.*;
import rice.pastry.messaging.*;

import rice.testharness.*;

/**
 * A message in the TestHarness system
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class CollectResultsResponseMessage extends TestHarnessMessage {

  /**
   * The source of the message.
   */
  protected NodeHandle _source;

  protected String _runName;

  protected String _results;

  /**
   * Constructor
   */
  public CollectResultsResponseMessage(NodeHandle source, String runName, String results) {
    super();
    _source = source;
    _runName = runName;
    _results = results;
  }

  public String toString() {
    return "CollectResultsResponseMessage: " + _source + ", " + _runName;
  }

  public NodeHandle getSource() {
    return _source;
  }

  public String getRunName() {
    return _runName;
  }

  public String getResults() {
    return _results;
  }
}
