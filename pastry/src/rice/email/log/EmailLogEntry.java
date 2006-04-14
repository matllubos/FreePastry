package rice.email.log;

import rice.post.log.*;
import rice.email.*;

/**
 * An abstract class for all email log entry types
 *
 * @author Alan Mislove
 */
public abstract class EmailLogEntry extends LogEntry implements Comparable {

  /**
   * Constructor for EmailLogEntry.  Takes in the number of log entries, including
   * this one, since a snap shot.
   *
   * @param num The number of log entries since the last snapshot
   */
  public EmailLogEntry() {
  }
  
  
  public abstract long getInternalDate();

  public abstract int getMaxUID();
  
  /**
   * Note: this comparison method is not compatible with equals
   */
  public int compareTo(Object other) {
    long result =  getInternalDate() - ((EmailLogEntry)other).getInternalDate();
    if (result < 0)
      return -1;
    if (result > 0)
      return 1;
    return 0;
  }
}

