package rice.email.log;

import rice.post.log.*;
import rice.email.*;

/**
 * Stores an email in the LogEntry chain.  Holds the email and a pointer
 * to the next LogEntry.
 * @author Joe Montgomery
 */
public class InsertMailLogEntry extends LogEntry {
  Email _email;
    
  /**
   * Constructor for InsertMailEntry.  For the given email, creates an
   * entry which can be used in a log chain. 
   *
   * @param email the email to store
   */
  public InsertMailLogEntry(Email email) {
    _email = email;
  }
  
  /**
   * Returns the email which this log entry references
   *
   * @return The email inserted
   */
  public Email getEmail() {
    return _email;
  }
}
