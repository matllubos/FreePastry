package rice.email.log;

import rice.post.log.*;
import rice.email.*;

/**
 * An abstract class for all email log entry types
 *
 * @author Alan Mislove
 */
public abstract class EmailLogEntry extends LogEntry {

  /**
   * Constructor for EmailLogEntry.  Takes in the number of log entries, including
   * this one, since a snap shot.
   *
   * @param num The number of log entries since the last snapshot
   */
  public EmailLogEntry() {
  }
}

