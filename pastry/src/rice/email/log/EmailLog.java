package rice.email.log;

import java.security.*;

import rice.p2p.commonapi.*;
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

  // the number of messages currently in this folder
  private int numExist;

  // the number of recent messages in this folder
  private int numRecent;

  // the number of log entries since a snapshot
  private int numEntries;

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
    numExist = 0;
    numRecent = 0;
    numEntries = 0;
  }

  /**
   * Returns the number of log entries since a snapshot
   *
   * @return The number of log entries since a snapshot
   */
  public int getEntries() {
    return numEntries;
  }

  /**
   * Increments the number of entries since a snapshot
   */
  public void incrementEntries() {
    numEntries++;
  }

  /**
   * Resets the number of entries since a snapshot
   */
  public void resetEntries() {
    numEntries = 0;
  }

  /**
   * Returns the number of messages which exist in this folder
   *
   * @return The number of messages which exists in the folder
   */
  public int getExists() {
    return numExist;
  }

  /**
   * Increments the number of messages which exist in this folder
   */
  public void incrementExists() {
    numExist++;
  }

  /**
   * Decrements the number of messages which exist in this folder
   */
  public void decrementExists() {
    numExist--;
  }

  /**
   * Returns the number of messages which are recent in this folder
   *
   * @return The number of messages which are recent in the folder
   */
  public int getRecent() {
    return numRecent;
  }

  /**
   * Increments the number of messages which exist in this folder
   */
  public void incrementRecent() {
    numRecent++;
  }

  /**
  * Decrements the number of messages which exist in this folder
   */
  public void decrementRecent() {
    numRecent--;
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
