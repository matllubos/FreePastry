package rice.email.log;

import rice.post.log.*;
import rice.email.*;

/**
 * An anti-email node, serves to cancel out the mail node that it
 * matches up to.
 */
public class MoveMailLogEntry extends LogEntry {

  /**
   * Constructor for DeleteMailLogEntry.  For the given email, creates a node which serves
   * as a marker that the previous occurence of the email in the chain
   * should be disregarded.  The next field is the next LogNode in the chain.
   *
   * @param email The email to store
   * @param folder The destination folder.
   * @param prev The reference to previous LogEntry in the chain
   */
  public MoveMailLogEntry(Email email, Folder folder, LogEntryReference prev) {
    super(prev);
  }
  
  /**
   * Returns the email which this log entry references
   *
   * @return The email to move
   */
  public Email getEmail() {
    return null;
  }
  
  /**
   * Returns the destination folder
   *
   * @return The destination folder of the move
   */
  public Folder getFolder() {
    return null;
  }
}
