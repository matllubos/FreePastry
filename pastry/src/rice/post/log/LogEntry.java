package rice.post.log;

import java.security.*;

import rice.pastry.*;
import rice.post.storage.*;

/**
 * Abstract class for all entries in the log. Each application using post should
 * implement a class hierarchy of log entries relevant to the semantics of that
 * system.
 * 
 * @version $Id$
 */
public abstract class LogEntry implements PostData {

  // the previous entry in the log
  private LogEntryReference previousEntry;
  
  /**
   * Constructs a LogEntry
   */
  public LogEntry() {
  }

  /**
   * Sets the reference to the previous entry in the log
   *
   * @param ref A reference to the previous log entry
   */
  public void setPreviousEntry(LogEntryReference ref) {
    previousEntry = ref;
  }
  
  /**
   * Returns the reference to the previous entry in the log
   *
   * @return A reference to the previous log entry
   */
  public LogEntryReference getPreviousEntry() {
    return previousEntry;
  }

  /**
   * This method is not supported (you CAN NOT store a log entry as a
   * public-key signed block).
   *
   * @param location The location of this object.
   * @throws IllegalArgument Always
   */
  public SignedReference buildSignedReference(NodeId location) {
    throw new IllegalArgumentException("Log entries are only stored as content-hash.");
  }

  /**
   * Builds a LogEntryReference object to this log, given a location and
   * the encryption key
   *
   * @param location The location of the stored data
   * @param key The key used to encrypt this object
   * @return A LogEntryReference to this object
   */
  public ContentHashReference buildContentHashReference(NodeId location, Key key) {
    return new LogEntryReference(location, key);
  }

  /**
   * This method is not supported (you CAN NOT store a log as a
   * secure block).
   *
   * @param location The location of the data
   * @param key The for the data
   * @throws IllegalArgumentException Always
   */
  public SecureReference buildSecureReference(NodeId location, Key key) {
    throw new IllegalArgumentException("Log entries are only stored as content-hash blocks.");
  }  
}

