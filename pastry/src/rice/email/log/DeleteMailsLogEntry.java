package rice.email.log;

import rice.post.log.*;
import rice.email.*;

/**
 * An anti-emails node, serves to cancel out the mail node that it
 * matches up to.
 * @author Joe Montgomery
 */
public class DeleteMailsLogEntry extends EmailLogEntry {
  StoredEmail[] _storedEmails;
  
  /**
   * Constructor for DeleteMailLogEntry.  For the given array of emails, creates a node which serves
   * as a marker that the previous occurences of the emails in the chain
   * should be disregarded.
   *
   * @param email the email to store
   */
  public DeleteMailsLogEntry(StoredEmail[] emails) {
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

