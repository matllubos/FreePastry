package rice.email.log;

import java.security.*;
import java.util.*;

import rice.*;
import rice.Continuation.*;
import rice.email.*;
import rice.p2p.commonapi.*;
import rice.post.*;
import rice.post.log.*;
import rice.post.storage.*;

/**
 * This represents the head of an email log, representing
 * a folder.
 *
 * @author Alan Mislove
 */
public class EmailLog extends CoalescedLog {

  // the default initial UID
  public static int DEFAULT_UID = 1;
  
  // serial UID, for compatibility
  static final long serialVersionUID = -3520357124395782138L;
  
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
  
  // the list of subscription for this log (only used if this is the root)
  private Vector subscriptions;
  
  // the most recent snapshot for this log (null if unsupported)
  private ContentHashReference snapshot;
  
  // the most recent snapshot array for this log (null if unsupported)
  private ContentHashReference[] snapshots;
  
  // the cached version of the most recent snapshot
  private transient SnapShot cachedSnapshot;
  
  // the cached versions of the snapshot array
  private transient SnapShot[] cachedSnapshots;

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
    subscriptions = new Vector();
  }
  
  /**
   * Returns the reference to the most recent snapshot
   *
   * @return The most recent log snapshot ref
   */
  public ContentHashReference getSnapshotReference() {
    return snapshot;
  }
  
  /**
   * Returns the reference to the most recent snapshot array
   *
   * @return The most recent log snapshot ref
   */
  public ContentHashReference[] getSnapshotReferences() {
    return snapshots;
  }
  
  /**
   * Returns the reference to the most recent log entry
   *
   * @return The most recent log entry reference
   */
  public LogEntryReference getTopEntryReference() {
    return topEntryReference;
  }
  
  /**
   * Returns the list of subscriptions in the log
   *
   * @param command the work to perform after this call.
   * @return The subscriptions
   */
  public void getSubscriptions(Continuation command) {
    if (subscriptions == null) {
      command.receiveResult(new String[0]);
    } else {      
      command.receiveResult(subscriptions.toArray(new String[0]));
    }
  }
  
  /**
   * Adds a subscriptions to the log
   *
   * @param command the work to perform after this call.
   * @param sub The subscription to add
   */
  public void addSubscription(String sub, Continuation command) {
    if (subscriptions == null)
      subscriptions = new Vector();
    
    if (! subscriptions.contains(sub))
      subscriptions.add(sub);
    
    sync(command);
  }
  
  /**
   * Adds a subscriptions to the log
   *
   * @param command the work to perform after this call.
   * @param sub The subscription to add
   */
  public void removeSubscription(String sub, Continuation command) {
    if (subscriptions == null)
      subscriptions = new Vector();
    
    if (subscriptions.contains(sub))
      subscriptions.remove(sub);
    
    sync(command);
  }
  
  /**
   * Returns the number elements in the new entry buffer
   *
   * @return The number of pending adds
   */
  public int getBufferSize() {
    return buffer.size();
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
   * Sets the newest snapshot
   *
   * @param snapshot The snapshot
   */
  public void setSnapshot(final SnapShot[] newsnapshots, Continuation command) {
    if (newsnapshots.length > 0) {
      post.getStorageService().storeContentHash(newsnapshots[0], new StandardContinuation(command) {
        int i = 0;
        ContentHashReference[] result = new ContentHashReference[newsnapshots.length];

        public void receiveResult(Object o) {
          result[i++] = (ContentHashReference) o;
          
          if (i == newsnapshots.length) { 
            resetEntries();
            EmailLog.this.snapshot = null;
            EmailLog.this.cachedSnapshot = null;
            EmailLog.this.snapshots = result;
            EmailLog.this.cachedSnapshots = newsnapshots;
            sync(parent);
          } else {
            post.getStorageService().storeContentHash(newsnapshots[i], this);
          }
        }
      });
    } else {
      command.receiveResult(Boolean.TRUE);
    }
  }
  
  /**
   * Returns the most recent snapshot reference
   *
   * @command The command to return the continuation to
   */
  public void getSnapshot(Continuation command) {
    if (cachedSnapshot != null) {
      command.receiveResult(new SnapShot[] {cachedSnapshot});
    } else if (cachedSnapshots != null) {
      command.receiveResult(cachedSnapshots);
    } else if (snapshot != null) {
      post.getStorageService().retrieveContentHash(snapshot, new StandardContinuation(command) {
        public void receiveResult(Object o) {
          cachedSnapshot = (SnapShot) o;
          
          parent.receiveResult(o);
        }
      });
    } else if (snapshots != null) {
      post.getStorageService().retrieveContentHash(snapshots[0], new StandardContinuation(command) {
        SnapShot[] result = new SnapShot[snapshots.length];
        int i = 0;
        
        public void receiveResult(Object o) {
          result[i++] = (SnapShot) o;
          
          if (i == snapshots.length) {
            cachedSnapshots = result;
            parent.receiveResult(result);
          } else {
            post.getStorageService().retrieveContentHash(snapshots[i], this);
          }
        }
      });
    } else {
      command.receiveResult(null);
    }
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
   * Sets the number of messages which exist in this folder
   *
   * @param num The new number of messages
   */
  public void setExists(int num) {
    numExist = num;
  }
  
  /**
   * Increments the number of messages which exist in this folder
   */
  public void incrementExists() {
    numExist++;
  }

  /**
   * Increments the number of messages which exist in this folder
   *
   * @param num The number to increment by
   */
  public void incrementExists(int num) {
    numExist += num;
  }

  /**
   * Decrements the number of messages which exist in this folder
   */
  public void decrementExists() {
    numExist--;
  }
  
  /**
   * Decrements the number of messages which exist in this folder
   *
   * @param num The number to increment by
   */
  public void decrementExists(int num) {
    numExist -= num;
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
