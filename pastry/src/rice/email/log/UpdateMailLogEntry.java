package rice.email.log;

import rice.post.log.*;
import rice.email.*;

/**
 * Adds the flags to the log entry
 * @author 
 */
public class UpdateMailLogEntry extends EmailLogEntry {

    StoredEmail _storedEmail;

  /**
   * Constructor for InsertMailEntry.  For the given email, creates an
   * entry which can be used in a log chain. 
   *
   * @param email the email to store
   */
  public UpdateMailLogEntry(StoredEmail email) {
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
    return "UpdateMailLogEntry[" + _storedEmail.getUID() + "]";
  }
  
  /**
   * Equals method
   *
   * @param o The object to compare to
   * @return Whether or not we are equal
   */
  public boolean equals(Object o) {
    if (! (o instanceof UpdateMailLogEntry))
      return false;
    
    return ((UpdateMailLogEntry) o)._storedEmail.equals(_storedEmail);
  }

}
