package rice.email.log;

import java.util.*;

import rice.post.log.*;
import rice.email.*;

/**
 * Serves as a summary of the log chain up to the current point.  Lets
 * the email reader display the current emails without having to read
 * through the entire chain.
 * @author Joe Montgomery
 */
public class SnapShotLogEntry extends EmailLogEntry {
  
  // stores the emails of the current folder
  private StoredEmail[] _emails;
  
  // the location of the most recent log entry included in this snapshot
  private LogEntry entry;
    
  /**
   * Constructor for SnapShot.  For the given email, creates an
   * entry which can be used in a log chain.  The next field is the
   * next LogNode in the chain.
   *
   * @param email the email to store
   * @param top The top of the current log
   */
  public SnapShotLogEntry(StoredEmail[] emails, LogEntry top) {
    _emails = emails;
    entry = top;
  }

  /**
   * Returns all of the emails that the SnapShot contains.
   *
   * @return the valid emails at the point of the SnapShot
   */
  public StoredEmail[] getStoredEmails() {
    return _emails;
  }
  
  /**
   * Returns the most recent entry in the log, at the time of the snapshot
   *
   * @return The most recent log entry reference
   */
  public LogEntry getTopEntry() {
    return entry;
  }
  
  /**
   * Equals method
   *
   * @param o The object to compare to
   * @return Whether or not we are equal
   */
  public boolean equals(Object o) {
    if (! (o instanceof SnapShotLogEntry))
      return false;
    
    return Arrays.equals(((SnapShotLogEntry) o)._emails, _emails);
  }
  
}
