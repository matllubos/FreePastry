package rice.email.log;

import rice.post.log.*;
import rice.email.*;

/**
 * Stores an email in the LogEntry chain.  Holds the email and a pointer
 * to the next LogEntry.
 * @author Joe Montgomery
 */
public class InsertMailLogEntry extends LogEntry {
  
  StoredEmail _storedEmail;
    
  /**
   * Constructor for InsertMailEntry.  For the given email, creates an
   * entry which can be used in a log chain. 
   *
   * @param email the email to store
   */
  public InsertMailLogEntry(StoredEmail email) {
    _storedEmail = email;
  }
  
  /**
   * Returns the email which this log entry references
   *
   * @return The email inserted
   */
  public StoredEmail getStoredEmail() {
    return _storedEmail;
  }
}






