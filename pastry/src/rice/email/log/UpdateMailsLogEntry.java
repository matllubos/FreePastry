package rice.email.log;

import rice.post.log.*;
import rice.email.*;

/**
 * Adds the flags to the log entry
 * @author 
 */
public class UpdateMailsLogEntry extends EmailLogEntry {
  
  StoredEmail[] _storedEmails;
  
  /**
  * Constructor for InsertMailEntry.  For the given email, creates an
   * entry which can be used in a log chain. 
   *
   * @param email the email to store
   */
  public UpdateMailsLogEntry(StoredEmail[] emails) {
    _storedEmails = emails; 
    
  }
  
  /**
    * Returns the email which this log entry references
   *
   * @return The email inserted
   */
  public StoredEmail[] getStoredEmails() {
    return _storedEmails;
  }
  
}
