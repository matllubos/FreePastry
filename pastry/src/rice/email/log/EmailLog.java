package rice.email.log;

import java.security.*;

import rice.pastry.Id;
import rice.post.*;
import rice.post.log.*;

/**
 * This represents the head of an email log, representing
 * a folder.
 *
 * @author Alan Mislove
 */
public class EmailLog extends EncryptedLog {

  // the default initial UID
  public static int DEFAULT_UID = 1;
  
  // the next available UID for this folder
  private int nextUID;

  // the creation time of this email log
  private long creation; 

  /**
   * Constructor for SnapShot.
   *
   * @param name The name of this log
   * @param location The location where this log is stored
   * @param post The current local post service
   */
  public EmailLog(Object name, Id location, Post post, KeyPair pair) {
    super(name, location, post, pair);

    nextUID = DEFAULT_UID;
    creation = System.currentTimeMillis();
  }

  /**
   * Returns the next UID, and doesn't increment the UID counter.
   *
   * @return The next UID.
   */
  public int peekNextUID() {
    return nextUID;
  }

  /**
   * Returns the next available UID, and increments the UID counter.
   *
   * @return The next UID.
   */
  public int getNextUID() {
    return nextUID++;
  }

  /**
   * Returns the time (in milliseconds) that this email log was created.
   *
   * @return The creation time
   */
  public long getCreationTime() {
    return creation;
  }
}
