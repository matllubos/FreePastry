package rice.email.log;

import rice.post.log.*;
import rice.email.*;

/**
 * Stores an list of emails in the LogEntry chain.  Holds the email and a pointer
 * to the next LogEntry.
 * @author Joe Montgomery
 */
public class InsertMailsLogEntry extends EmailLogEntry {
  
  StoredEmail[] _storedEmails;
  
  /**
  * Constructor for InsertMailEntry.  For the given email, creates an
   * entry which can be used in a log chain. 
   *
   * @param email the email to store
   */
  public InsertMailsLogEntry(StoredEmail[] emails) {
    _storedEmails = emails;
  }
  
  /**
    * Returns the emails which this log entry references
   *
   * @return The emails inserted
   */
  public StoredEmail[] getStoredEmails() {
    return _storedEmails;
  }
}






