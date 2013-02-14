package rice.email.log;

import rice.post.log.*;
import rice.email.*;

/**
 * Serves as a summary of the log chain up to the current point.  Lets
 * the email reader display the current emails without having to read
 * through the entire chain.
 */
public class SnapShotLogEntry extends LogEntry {
  // stores the emails of the current folder
  private Email[] _emails;
    
  /**
   * Constructor for SnapShot.  For the given email, creates an
   * entry which can be used in a log chain.  The next field is the
   * next LogNode in the chain.
   *
   * @param email the email to store
   */
  public SnapShotLogEntry(Email[] emails) {
    _emails = emails;
  }

  /**
   * Returns all of the emails that the SnapShot contains.
   * @return the valid emails at the point of the SnapShot
   */
  public Email[] getEmails() {
    return _emails;
  }
      
  /**
   * Returns whether this is the last node in the current snapshot.
   * Is provided in case the snapshot covers multiple nodes.
   * @return whether this is the last snapshot node.
   */
  public boolean isEnd() {
    return true;
  }
}
