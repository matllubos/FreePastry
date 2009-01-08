/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
package rice.post.log;

import java.io.*;
import java.security.*;
import java.util.*;

import rice.*;
import rice.Continuation.*;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.*;
import rice.post.*;
import rice.post.storage.*;

/**
 * Class which represents a log in the POST system.  Clients can use
 * this log in order to get lists of sublogs, or walk backwards down
 * all of the entries.  Log classes are stored in PAST at specific
 * locations, and are updated whenever a change is made to the log.
 *
 * This class also provides the proper synchronization so that quick calls to
 * the addLogEntry() method do not overwrite each other.
 * 
 * @version $Id$
 */
@SuppressWarnings("unchecked")
public class Log implements PostData {
  
  static final long serialVersionUID = -2375808799970141618L;

  /**
   * The location of this log in PAST.
   */
  protected Id location;

  /**
   * Some unique identifier to name this log.
   */
  protected Object name;

  /**
   * A map of the names of the child logs to their references.
   */
  protected HashMap children;

  /**
   * The current local POST service.  Transient: changes depending
   * on where the log is being used.
   */
  protected transient Post post;

  /**
   * A vector of ongoing buffered tasks - in case two addLogEntries() are
   * called in quick succession.  Note that the first element in the buffer
   * is the current outstanding task.
   */
  protected transient Vector buffer;

  /**
   * A reference to the most recent entry in this log.
   * deprecated, use the topEntryReferences[] array below
   */
  protected LogEntryReference topEntryReference;
  
  /**
   * Number of topEntryReferences to keep around. 
   */
  protected final static int N_TOP_ENTRIES = 4;
  
  /**
   * References to the last n entries in this log.
   */
  protected LogEntryReference topEntryReferences[];

  /**
   * The most recent entry in this log.
   */
  protected transient LogEntry topEntry;

  /**
   * A cache of references to our children
   */
  protected transient HashMap childrenCache;
  
  protected transient Logger logger;
  
  /**
   * Constructs a Log for use in POST
   *
   * @param name Some unique identifier for this log
   * @param location The location of this log in PAST
   */
  public Log(Object name, Id location, Post post) {
    this.name = name;
    this.location = location;

    children = new HashMap();
    childrenCache = new HashMap();

    setPost(post);
    buffer = new Vector(); 
  }
  
  /**
   * Returns whether or not this log should be cached
   *
   * @return Whether or not this log should be cached
   */
  public boolean cache() {
    return true;
  }
  
  /**
   * @return The location of this Log in PAST.
   */
  public Id getLocation() {
    return location;
  }

  /**
   * @return The name of this Log.
   */
  public Object getName() {
    return name;
  }
  
  /**
   * Sets the name of this log.  If this log has a parent, it MUST be removed and
   * re-added to the parent for this change to work.  Otherwise, BAD BAD BAD things
   * will happen.  You've been warned.
   *
   * @param name The new name
   * @param command The command to run once done
   */
  public void setName(Object name, Continuation command) {
    this.name = name;
    sync(command);
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
   *
   * Once this method is finished, it will call the command.receiveResult()
   * method with the current log as the argument, or it may call
   * receiveExcception if an exception occurred.
   *
   * @param command The command to run once done
   */
  protected void sync(Continuation command) {
    post.getStorageService().storeSigned(this, location, new StandardContinuation(command) {
      public void receiveResult(Object o) {
        parent.receiveResult(new Boolean(true)); 
      }
    });
  }
  
  /**
   * This method adds a child log to this log, essentially forming a tree
   * of logs.
   *
   * Once this method is finished, it will call the command.receiveResult()
   * method with the new Log (ready to go), or it may call
   * receiveExcception if an exception occurred.
   *
   * @param log The log to add as a child.
   * @param command The command to run once done
   */
  public void addChildLog(final Log log, Continuation command) {
    childrenCache.put(log.getName(), log);
    post.getStorageService().storeSigned(log, log.getLocation(), new StandardContinuation(command) {
      public void receiveResult(Object o) {
        children.put(log.getName(), o);
        sync(new StandardContinuation(parent) {
          public void receiveResult(Object o) {
            parent.receiveResult(log);
          }
        });
      }
    });
  }

  /**
   * This method removes a child log from this log.
   *
   * Once this method is finished, it will call the command.receiveResult()
   * method with a Boolean value indicating success, or it may call
   * receiveException if an exception occurred.
   *
   * @param log The log to remove
   * @param command The command to run once done
   */
  public void removeChildLog(Object name, Continuation command) {
    children.remove(name);
    sync(command);
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
   * This method returns a the specific child log of
   * this log, given the child log's name.
   *
   * @param name The name of the log to return.
   * @param command The command to run once done.
   */
  public void getChildLog(Object name, Continuation command) {
    LogReference ref = (LogReference) children.get(name);

    if (ref == null) {
      command.receiveResult(null);
      return;
    }

    if (childrenCache.get(name) != null) {
      command.receiveResult(childrenCache.get(name));
      return;
    }

    post.getStorageService().retrieveSigned(ref, new StandardContinuation(command) {
      public void receiveResult(Object o) {
        if (o != null) {
          Log log = (Log) o;
          log.setPost(post);

          if (log.cache()) 
            childrenCache.put(log.getName(), log);
        }
        
        parent.receiveResult(o);
      }
    });
  }

  /**
   * This method appends an entry into the user's log, and updates the pointer 
   * to the top of the log to reflect the new object. This method returns a 
   * LogEntryReference which is a pointer to the LogEntry in PAST. Note that 
   * this method reinserts this Log into PAST in order to reflect the addition.
   *
   * Once this method is finished, it will call the command.receiveResult()
   * method with a LogEntryReference for the new entry, or it may call
   * receiveExcception if an exception occurred.
   *
   * @param entry The log entry to append to the log.
   * @param command The command to run once done
   */
  public void addLogEntry(LogEntry entry, final Continuation command) {
    final AddLogEntryTask task = new AddLogEntryTask(entry, null);
    
    Continuation comm = new Continuation() {
      public void receiveResult(Object o) {
        AddLogEntryTask alet = null;
        
        synchronized (buffer) {
          if ((buffer.size() > 0) && (buffer.get(0) == task)) {
            buffer.remove(0);
            
            if (buffer.size() > 0) {
              alet = (AddLogEntryTask) buffer.get(0);
            }
            
            command.receiveResult(o);
          }
        }
        
        if (alet != null)
          alet.start();
      }

      public void receiveException(Exception e) {
        AddLogEntryTask alet = null;
        
        synchronized (buffer) {
          if ((buffer.size() > 0) && (buffer.get(0) == task)) {
            buffer.remove(0);
            
            if (buffer.size() > 0) {
              alet = (AddLogEntryTask) buffer.get(0);
            }
            
            command.receiveException(e);
          }
        }
        
        if (alet != null)
          alet.start();
      }
    };
    
    task.setCommand(comm);
    
    boolean go = false;
    
    synchronized (buffer) {
      buffer.add(task);
      
      if (buffer.size() == 1)
        go = true;
    }

    if (go)
      task.start();
  }
    
  /**
   * Returns the reference to the most recent log entry
   *
   * @return The most recent log entry reference
   */
  public LogEntryReference getTopEntryReference() {
    if (topEntryReferences != null) {
      return topEntryReferences[0];
    } else {
      return topEntryReference;
    }
  }
  
  /**
   * This method returns a reference to the most recent entry in the log,
   * which can then be used to walk down the log.
   *
   * @return A reference to the top entry in the log.
   */
  public void getTopEntry(Continuation command) {
    getRealTopEntry(command);   
  }
  
  /**
   * Internal method to get the *actual* top entry
   *
   * @return The top entry which is actually an entry
   */
  protected final void getRealTopEntry(Continuation command) {
    if ((topEntry == null) && ((topEntryReferences != null) || (topEntryReference != null))) {
      LogEntryReference top;
        if (topEntryReferences != null) {
          top = topEntryReferences[0];
        } else {
          top = topEntryReference;
       }
      post.getStorageService().retrieveContentHash(top, new StandardContinuation(command) {
        public void receiveResult(Object o) {
          try {
            topEntry = (LogEntry) o;
            topEntry.setPost(post);
            parent.receiveResult(topEntry);
          } catch (ClassCastException e) {
            parent.receiveException(e);
          }
        }
      });
    } else {
      command.receiveResult(topEntry);
    }
  }
  
  /**
   * This method returns a list of all the handles stored in the folder or
   * any subfolders.
   *
   * Returns a PastContentHandle[] containing all of 
   * the handles in to the provided continatuion.
   */
  public void getLogEntryReferences(final Set set, final LogEntry entry, Continuation command) {
    if ((topEntryReferences == null) && (topEntryReference == null)) {
      command.receiveResult(Boolean.TRUE);
    } else {
      if (topEntryReferences != null) {
        set.add(topEntryReferences[0]);
      } else {
        set.add(topEntryReference);
      }

      getRealTopEntry(new StandardContinuation(command) {
        public void receiveResult(Object o) {
          LogEntry thisEntry = (LogEntry) o;
          
          if (thisEntry == null) {
            if (logger == null) logger = post.getEnvironment().getLogManager().getLogger(Log.class, null);
            if (logger.level <= Logger.WARNING) logger.log("Log entry was unexpectedly null - returning prematurely...");
            parent.receiveResult(Boolean.TRUE);
          } else if (((entry != null) && (thisEntry.contains(entry))) ||
                     (thisEntry.getPreviousEntryReference() == null)) {
            parent.receiveResult(Boolean.TRUE);
          } else {
            set.add(thisEntry.getPreviousEntryReference());
            thisEntry.getRealPreviousEntry(this);
          }
        }
      });
    }
  }

  /**
   * Builds a LogReference object to this log, given a location.
   * Used by the StorageService when storing the log.
   *
   * @param location The location of this object.
   * @return A LogReference to this object
   */
  public SignedReference buildSignedReference(Id location) {
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
 public ContentHashReference buildContentHashReference(Id[] location, byte key[][]) {
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
  public SecureReference buildSecureReference(Id location, byte key[]) {
    throw new IllegalArgumentException("Logs are only stored as signed blocks.");
  }

  /**
   * Custom readObject to allow the buffer to be initialized
   * upon deserialization
   *
   * @param ois The object input stream
   */
  private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
    ois.defaultReadObject();

    buffer = new Vector();
    childrenCache = new HashMap();
  }

  /**
   * This class encapsulates the logic needed to add a log entry to
   * the current log.
   */
  protected class AddLogEntryTask implements Continuation {

    public static final int STATE_1 = 1;
    public static final int STATE_2 = 2;

    private LogEntry entry;
    private LogEntryReference[] previousTopReferences;
    private LogEntry previousTop;
    private Continuation command;
    private int state;
    
    /**
     * This construct will build an object which will call the given
     * command once processing has been completed, and will provide
     * a result.
     *
     * @param entry The log entry to add
     * @param command The command to call
     */
    protected AddLogEntryTask(LogEntry entry, Continuation command) {
      this.entry = entry;
      this.command = command;
    }
    
    public void setCommand(Continuation command) {
      this.command = command;
    }

    public void start() {
      if (topEntryReferences == null) {
        previousTopReferences = new LogEntryReference[1];
        previousTopReferences[0] = topEntryReference;
      } else {
        previousTopReferences = topEntryReferences;
      }
      previousTop = topEntry;
      state = STATE_1;
      entry.setPost(post);
      entry.setUser(post.getEntityAddress());
      if (logger == null) logger = post.getEnvironment().getLogManager().getLogger(Log.class, null);
      if (logger.level <= Logger.INFO) logger.log("setting PreviousEntryReferences on entry with: "+topEntryReferencesToString());
      entry.setPreviousEntryReferences(previousTopReferences);
      entry.setPreviousEntry(topEntry);
      post.getStorageService().storeContentHash(entry, this);
    }

    private void startState1(LogEntryReference reference) {
      // shift around the arrays
      int newlength = 1;
      if (topEntryReferences != null) {
        newlength = topEntryReferences.length + 1;
      }
        if (newlength > N_TOP_ENTRIES)
          newlength = N_TOP_ENTRIES;
      LogEntryReference[] temp = new LogEntryReference[newlength];
      for (int i = 1; i < newlength; i++) {
        temp[i] = topEntryReferences[i-1];
      }
      temp[0] = reference;
      topEntryReferences = temp;
      topEntryReference = reference;
      topEntry = entry;
      state = STATE_2;
      sync(this);
    }

    private void startState2() {
      command.receiveResult(topEntry);
    }

    public void receiveResult(Object o) {
      switch(state) {
        case STATE_1:
          startState1((LogEntryReference) o);
          break;
        case STATE_2:
          startState2();
          break;
        default:
          command.receiveException(new StorageException("Received unexpected state on addLogEntry: " + state));
          break;
      }
    }

    /**
      * Called when a previously requested result causes an exception
     *
     * @param result The exception caused
     */
    public void receiveException(Exception result) {
      topEntryReferences = previousTopReferences;
      topEntryReference = previousTopReferences[0];
      topEntry = previousTop;
      command.receiveException(result);
    }
  }

  private String topEntryReferencesToString() {
    StringBuffer result = new StringBuffer();
    result.append("[ ");

      if (topEntryReferences != null) {
        for (int i=0; i<topEntryReferences.length; i++) {
          if (topEntryReferences[i] == null) {
            result.append("null ");
          } else {
            result.append(topEntryReferences[i].toString());
          }
        }
      }
    
    result.append(" ]");
    
    return result.toString();
  }
  
  
  public String toString() {
    return "Log[" + name + "]";
  }
}

