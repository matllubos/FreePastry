package rice.email.log;

import rice.pastry.Id;
import rice.post.*;
import rice.post.log.*;

/**
 * This represents the head of an email log, representing
 * a folder.
 *
 * @author Alan Mislove
 */
public class EmailLog extends Log {

  // the default initial UID
  public static int DEFAULT_UID = 1;
  
  // the next available UID for this folder
  private int nextUID;

  /**
   * Constructor for SnapShot.
   *
   * @param name The name of this log
   * @param location The location where this log is stored
   * @param post The current local post service
   */
  public EmailLog(Object name, Id location, Post post) {
    super(name, location, post);

    nextUID = DEFAULT_UID;
  }

  /**
   * Returns the next available UID, and increments the UID counter.
   *
   * @return The next UID.
   */
  public int getNextUID() {
    return nextUID++;
  }
}
