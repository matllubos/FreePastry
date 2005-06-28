package rice.post.log;

import java.lang.ref.*;
import java.security.*;
import java.util.Arrays;

import rice.*;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.*;
import rice.post.*;
import rice.post.storage.*;

/**
 * Abstract class for all entries in the log. Each application using post should
 * implement a class hierarchy of log entries relevant to the semantics of that
 * system.
 * 
 * @version $Id$
 */
public abstract class LogEntry implements PostData {

  // serialver for backward compatibility
  private static final long serialVersionUID = 2210096794853078256L;
  
  // the user in whose log this entry appears
  protected PostEntityAddress user;
  
  // a reference to the previous entry in the log
  // deprecated; use the array previousEntryReferences[] below
  protected LogEntryReference previousEntryReference;
  
  // a reference to the previous N_LOG_ENTRIES references in the log
  protected LogEntryReference previousEntryReferences[];

  // the previous entry in the log
  private transient SoftReference previousEntry;

  // the local Post service
  protected transient Post post;

  // this logentry's "parent" logentry (if it is, say, wrapped in an encLogEntry)
  private transient LogEntry parent;
  
  /**
   * Constructs a LogEntry
   */
  public LogEntry() {
  }
  
  /**
   * Returns whether or not this coaleseced log entry contains
   * the provided entry
   *
   * @param entry The entry to search for
   * @return Whetehr or not this entry contains it
   */
  protected boolean contains(LogEntry entry) {
    if (entry == null)
      return false;
        
    return this.equals(entry);
  }

  /**
   * Sets the user of this log entry
   *
   * @param user The user who created this entry
   */
  public void setUser(PostEntityAddress user) {
    if (this.user == null) {
      this.user = user;
    } else {
      logException(Logger.SEVERE, "ERROR - Trying to set user on already-set log.",new Exception("Stack Trace"));
    }
  }
  
  /**
   * Sets the reference to the previous entry in the log
   *
   * @param ref A reference to the previous log entry
   */
  public void setPreviousEntryReferences(LogEntryReference[] ref) {
    if (((previousEntryReferences == null) && (previousEntryReference == null)) ||
        ((previousEntryReferences == null) && (previousEntryReference == ref[0])) ||
        Arrays.equals(ref,previousEntryReferences)) {
      previousEntryReferences = ref;
    } else {
      logException(Logger.SEVERE, "ERROR - Trying to set previous ref on already-set log.",new Exception("Stack Trace"));
    }
  }
  
  /**
   * Returns the reference to the previous entry in the log
   *
   * @return A reference to the previous log entry
   */
  public LogEntryReference getPreviousEntryReference() {
    if (parent != null) {
      return parent.getPreviousEntryReference();
    }
    if (previousEntryReferences != null) {
      return previousEntryReferences[0];
    } else {
    	  return previousEntryReference;
    }
  }
  
  /**
   * Returns whether or not this log entry has a previous log entry
   *
   * @return Whether or not this log entry has a previous
   */
  public boolean hasPreviousEntry() {
    if (parent != null)
      return parent.hasPreviousEntry();
    else
      return (previousEntryReferences != null) || (previousEntryReference != null);
  }
  
  /**
   * Returns the reference to the previous entry in the log
   *
   * @return A reference to the previous log entry
   */
  public void getPreviousEntry(final Continuation command) {
    getRealPreviousEntry(command);
  }
  
  /**
   * Returns the cached previous entry, if it exists and is in memory.
   * Otherwise, it returns null.
   *
   * @return The cached previous entry
   */
  public LogEntry getCachedPreviousEntry() {
    if (parent != null) 
      return parent.getCachedPreviousEntry();
    
    return ((previousEntry == null) ? null : (LogEntry) previousEntry.get());
  }

  /**
   * Returns the reference to the previous entry in the log
   *
   * @return A reference to the previous log entry
   */
  protected final void getRealPreviousEntry(final Continuation command) {
    if (parent != null) {
      parent.getPreviousEntry(command);
      return;
    }
    
    LogEntry prev = ((previousEntry == null) ? null : (LogEntry) previousEntry.get());
    
    if ((prev == null) && ((previousEntryReferences != null) || (previousEntryReference != null))) {
      Continuation fetch = new Continuation() {
        public void receiveResult(Object o) {
          try {
            LogEntry prev = (LogEntry) o;
            prev.setPost(post);
            previousEntry = new SoftReference(prev);
            command.receiveResult(prev);
          } catch (ClassCastException e) {
            command.receiveException(e);
          }
        }

        public void receiveException(Exception e) {
          command.receiveException(e);
        }
      };

      if (previousEntryReferences == null) {
        post.getStorageService().retrieveContentHash(previousEntryReference, fetch);
      } else {
        post.getStorageService().retrieveContentHash(previousEntryReferences[0],fetch);
      }
    } else {
      command.receiveResult(prev);
    }
  }

  /**
   * Protected method which sets the post service
   */
  void setPost(Post post) {
    this.post = post;
  }

  /**
   * Method which set's this log entry's parent, if, for example, it is inside an
   * encrypted log entry.
   */
  void setParent(LogEntry parent) {
    this.parent = parent;
  }

  /**
    * Protected method which sets the previous entry in the log.
   *
   */
  void setPreviousEntry(LogEntry entry) {
    if (previousEntry == null) {
      previousEntry = new SoftReference(entry);
    } else {
      log(Logger.SEVERE,"ERROR - Attempting to set a previous entry with an existing one in LogEntry!");
    }
  }

  /**
   * This method is not supported (you CAN NOT store a log entry as a
   * public-key signed block).
   *
   * @param location The location of this object.
   * @throws IllegalArgument Always
   */
  public SignedReference buildSignedReference(Id location) {
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
  public ContentHashReference buildContentHashReference(Id[] location, byte[][] key) {
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
  public SecureReference buildSecureReference(Id location, byte[] key) {
    throw new IllegalArgumentException("Log entries are only stored as content-hash blocks.");
  }
  
  private void log(int level, String m) {
    post.getEnvironment().getLogManager().getLogger(LogEntry.class, null).log(level,m);
  }
  
  private void logException(int level, String m, Throwable t) {
    post.getEnvironment().getLogManager().getLogger(LogEntry.class, null).logException(level,m,t);
  }
  
  
}

