package rice.email.log;

import rice.post.log.*;
import rice.email.*;

/**
 * An anti-email node, serves to cancel out the mail node that it
 * matches up to.
 * @author Joe Montgomery
 */
public class DeleteMailLogEntry extends LogEntry {
  StoredEmail _storedEmail ;
    
  /**
   * Constructor for DeleteMailLogEntry.  For the given email, creates a node which serves
   * as a marker that the previous occurence of the email in the chain
   * should be disregarded.
   *
   * @param email the email to store
   */
  public DeleteMailLogEntry(StoredEmail email) {
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

