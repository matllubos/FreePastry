package rice.post.log;

import java.security.*;

import rice.post.*;

/**
 * This class represents the object which sits at the address of the user's
 * PostUserAddress and points to the most recent entry in the log.  This object
 * is also signed by the user, and contains the user's public key for other users
 * in the system to read.
 */
public class LogHead {

  /**
   * Constructor for LogHead.
   *
   * @param user The user whom this LogHead is for
   * @param key The user's public key.
   * @param top The current top entry in the log (can be null if log is empty)
   */
  public LogHead(PostUserAddress user, PublicKey key, LogEntry top) {
  }
    
  /**
   * Returns the user to whom this log head belongs
   *
   * @return The user who owns this LogHead.
   */
  public PostUserAddress getUserAddress() {
    return null;
  }
    
  /**
   * Returns the public key of the user who owns this log head
   *
   * @return The public key of the user who owns this LogHead.
   */
  public PublicKey getPublicKey() {
    return null;
  }
    
  /**
   * Returns a reference to the top entry in the user's log
   *
   * @return A reference to the topmost entry in the user's log
   */
  public LogEntryReference getTopEntry() {
    return null;
  }
}

