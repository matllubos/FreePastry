package rice.email.log;

import rice.post.log.*;
import rice.email.*;

/**
 * Stores an email in the LogEntry chain.  Holds the email and a pointer
 * to the next LogEntry.
 * @author Joe Montgomery
 */
public class InsertMailLogEntry extends EmailLogEntry {
  
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
  
  /**
   * ToString for this entry
   *
   * @return A String
   */
  public String toString() {
    return "InsertMailLogEntry[" + _storedEmail.getUID() + "]";
  }
  
  /**
    * Equals method
   *
   * @param o The object to compare to
   * @return Whether or not we are equal
   */
  public boolean equals(Object o) {
    if (! (o instanceof InsertMailLogEntry))
      return false;
    
    return ((InsertMailLogEntry) o)._storedEmail.equals(_storedEmail);
  }
}






