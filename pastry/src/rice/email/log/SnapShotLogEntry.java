package rice.email.log;

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
  private LogEntryReference _top;
    
  /**
   * Constructor for SnapShot.  For the given email, creates an
   * entry which can be used in a log chain.  The next field is the
   * next LogNode in the chain.
   *
   * @param email the email to store
   * @param top The top of the current log
   */
  public SnapShotLogEntry(StoredEmail[] emails, LogEntryReference top) {
    _emails = emails;
    _top = top;
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
  public LogEntryReference getTopEntryReference() {
    return _top;
  }
  
}
