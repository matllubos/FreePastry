package rice.email.log;

import rice.post.log.*;
import rice.email.*;

/**
 * Stores an email in the LogEntry chain.  Holds the email and a pointer
 * to the next LogEntry.
 */
public class InsertMailLogEntry extends LogEntry {

  /**
   * Constructor for MailEntry.  For the given email, creates an
   * entry which can be used in a log chain.  The next field is the
   * next LogEntry in the chain.
   *
   * @param email the email to store
   * @param prev The reference to previous LogEntry in the chain
   */
  public InsertMailLogEntry(Email email, LogEntryReference prev) {
    super(prev);
  }
  
  /**
   * Returns the email which this log entry references
   *
   * @return The email inserted
   */
  public Email getEmail() {
    return null;
  }
}
