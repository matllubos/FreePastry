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
 */
public class Log implements PostData {

  // our location
  private NodeId location;

  // our name
  private String name;

  // our map of child name -> reference
  private HashMap children;

  // the local POST we are currently on (is transient)
  private transient Post post;

  // the top entry in the log
  private LogEntryReference topEntry;
  
  /**
   * Constructs a Log
   *
   * @param name The name of this log
   * @param location The location of this log in the pastry ring
   */
  public Log(String name, NodeId location) {
    this.name = name;
    this.location = location;

    children = new HashMap();
  }
  
  /**
   * This method returns the location in PAST of this Log.
   *
   * @return The location of this Log.
   */
  public NodeId getLocation() {
    return location;
  }

  /**
    * This method returns the name of this Log.
   *
   * @return The name of this Log.
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the local Post object, if not already set
   *
   * @param post The local post to user
   */
  public void setPost(Post post) {
    this.post = post;
  }

  /**
   * Syncs this log object into the network.
   */
  private void sync() {
    post.getStorageService().storeSigned(this, location);
  }
  
  /**
   * This method adds a log to this log (essentially adding a child
   * log, forming a tree).  
   *
   * @param name An identifier for this child log, such as a string, etc...
   * @param log The log to add.
   */
  public LogReference addChildLog(Log log) {
    LogReference lr = (LogReference) post.getStorageService().storeSigned(log, log.getLocation());
    
    children.put(log.getName(), lr);
    sync();

    return lr;
  }

  /**
   * This method removes a log from this log.
   *
   * @param log The log to remove
   */
  public void removeChildLog(Object name) {
    children.remove(name);
    sync();
  }

  /**
   * This method returns an array of the names of all of the current child
   * logs of this log.
   *
   * @return An array of String, the names of the children of this Log
   */
  public Object[] getChildLogNames() {
    return children.keySet().toArray();
  }

  /**
   * This method returns a reference to a specific child log of
   * this log, given the name.
   *
   * @param name The name of the log to return.
   * @return A reference to the requested log.
   */
  public LogReference getChildLog(Object name) {
    return (LogReference) children.get(name);
  }

  /**
   * This method appends an entry into the user's log, and updates the pointer to
   * the top of the log to reflect the new object. This method returns a LogEntryReference
   * which is a pointer to the LogEntry in PAST. Note that this method reinserts this
   * Log into PAST in order to reflect the addition.
   *
   * @param entry The log entry to append to the log.
   */
  public LogEntryReference addLogEntry(LogEntry entry) {
    entry.setPreviousEntry(topEntry);
    topEntry = (LogEntryReference) post.getStorageService().storeContentHash(entry);
    sync();
    
    return topEntry;
  }

  /**
   * This method retrieves a log entry given a reference to the log entry.
   * This method also performs the appropriate verification checks and decryption
   * necessary.
   *
   * @param reference The reference to the log entry
   * @return The log entry referenced
   */
  public LogEntry retrieveLogEntry(LogEntryReference reference) {
    return (LogEntry) post.getStorageService().retrieveContentHash(reference);
  }

  /**
   * This method returns a reference to the top of this log, which can then be used
   * to walk down the log.
   *
   * @return A reference to the top entry in the log.
   */
  public LogEntryReference getTopEntry() {
    return topEntry;
  }

  /**
   * Builds a LogReference object to this log, given a location.
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
    throw new IllegalArgumentException("Logs are only signed blocks.");
  }
}

