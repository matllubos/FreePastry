package rice.post.log;

import java.security.*;
import java.util.*;

import rice.pastry.*;
import rice.post.*;
import rice.post.storage.*;

/**
 * Class which represents a log in the POST system.  Clients can use
 * this log in order to get lists of sublogs, or walk backwards down
 * all of the entries.  Log classes are stored in PAST at specific
 * locations, and are updated whenever a change is made to the log.
 * 
 * @version $Id$
 */
public class Log implements PostData {

  /**
   * The location of this log in PAST.
   */
  private NodeId location;

  /**
   * Some unique identifier to name this log.
   */
  private Object name;

  /**
   * A map of the names of the child logs to their references.
   */
  private HashMap children;

  /**
   * The current local POST service.  Transient: changes depending
   * on where the log is being used.
   */
  private transient Post post;

  /**
   * The most recent entry in this log.
   */
  private LogEntryReference topEntry;
  
  /**
   * Constructs a Log for use in POST
   *
   * @param name Some unique identifier for this log
   * @param location The location of this log in PAST
   */
  public Log(Object name, NodeId location, Post post) throws StorageException {
    this.name = name;
    this.location = location;

    children = new HashMap();

    if (post != null) {
      setPost(post);
      sync();
    }
  }
  
  /**
   * @return The location of this Log in PAST.
   */
  public NodeId getLocation() {
    return location;
  }

  /**
   * @return The name of this Log.
   */
  public Object getName() {
    return name;
  }

  /**
   * Sets the current local Post service.
   *
   * @param post The current local Post service
   */
  public void setPost(Post post) {
    this.post = post;
  }

  /**
   * Helper method to sync this log object on the network.
   */
  protected void sync() throws StorageException {
    post.getStorageService().storeSigned(this, location);
  }
  
  /**
   * This method adds a child log to this log, essentially forming a tree
   * of logs.
   *
   * @param log The log to add as a child.
   */
  public LogReference addChildLog(Log log) throws StorageException {
    LogReference lr = (LogReference) post.getStorageService().storeSigned(log, log.getLocation());
    
    children.put(log.getName(), lr);
    sync();

    return lr;
  }

  /**
   * This method removes a child log from this log.
   *
   * @param log The log to remove
   */
  public void removeChildLog(Object name) throws StorageException {
    children.remove(name);
    sync();
  }

  /**
   * This method returns an array of the names of all of the current child
   * logs of this log.
   *
   * @return An array of Objects: the names of the children of this Log
   */
  public Object[] getChildLogNames() {
    return children.keySet().toArray();
  }

  /**
   * This method returns a reference to a specific child log of
   * this log, given the child log's name.
   *
   * @param name The name of the log to return.
   * @return A reference to the requested log, or null if the name
   * is unrecognized.
   */
  public LogReference getChildLog(Object name) {
    return (LogReference) children.get(name);
  }

  /**
   * This method appends an entry into the user's log, and updates the pointer 
   * to the top of the log to reflect the new object. This method returns a 
   * LogEntryReference which is a pointer to the LogEntry in PAST. Note that 
   * this method reinserts this Log into PAST in order to reflect the addition.
   *
   * @param entry The log entry to append to the log.
   */
  public LogEntryReference addLogEntry(LogEntry entry) throws StorageException {
    entry.setPreviousEntry(topEntry);
    topEntry = (LogEntryReference) post.getStorageService().storeContentHash(entry);
    sync();
    
    return topEntry;
  }

  /**
   * This method retrieves a log entry given a reference to the log entry.
   * This method also performs the appropriate verification checks and 
   * decryption necessary.
   *
   * @param reference The reference to the log entry
   * @return The log entry referenced
   */
  public LogEntry retrieveLogEntry(LogEntryReference reference) throws StorageException {
    return (LogEntry) post.getStorageService().retrieveContentHash(reference);
  }

  /**
   * This method returns a reference to the most recent entry in the log,
   * which can then be used to walk down the log.
   *
   * @return A reference to the top entry in the log.
   */
  public LogEntryReference getTopEntry() {
    return topEntry;
  }

  /**
   * Builds a LogReference object to this log, given a location.
   * Used by the StorageService when storing the log.
   *
   * @param location The location of this object.
   * @return A LogReference to this object
   */
  public SignedReference buildSignedReference(NodeId location) {
    return new LogReference(location);
  }

  /**
   * This method is not supported (you CAN NOT store a log as a
   * content-hash block).
   *
   * @param location The location
   * @param key
   * @throws IllegalArgumentException Always
   */
  public ContentHashReference buildContentHashReference(NodeId location, Key key) {
    throw new IllegalArgumentException("Logs are only stored as signed blocks.");
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
    throw new IllegalArgumentException("Logs are only stored as signed blocks.");
  }  
}

