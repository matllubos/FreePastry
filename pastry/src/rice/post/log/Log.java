package rice.post.log;

import java.security.*;
import java.util.*;

import rice.*;
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
     * A vector of ongoing buffered tasks
     */
    protected transient Vector buffer;  

    protected transient boolean bufferFlag;

  /**
   * A reference to the most recent entry in this log.
   */
  protected LogEntryReference topEntryReference;

  /**
    * The most recent entry in this log.
   */
  protected transient LogEntry topEntry;
  
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

    setPost(post);
    buffer = new Vector();
    System.out.println ("Created log: " + this.toString());
    bufferFlag = false;
    System.out.println("LN30: " + bufferFlag);    
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
    SyncTask task = new SyncTask(command);
    task.start();
  }
  
  /**
   * This method adds a child log to this log, essentially forming a tree
   * of logs.
   *
   * Once this method is finished, it will call the command.receiveResult()
   * method with a LogReference for the new log, or it may call
   * receiveExcception if an exception occurred.
   *
   * @param log The log to add as a child.
   * @param command The command to run once done
   */
  public void addChildLog(Log log, Continuation command) {
      System.out.println("Added new childlog: " + log.toString());
    AddChildLogTask task = new AddChildLogTask(log, command);
    task.start();
  }

  /**
   * This method removes a child log from this log.
   *
   * Once this method is finished, it will call the command.receiveResult()
   * method with a Boolean value indicating success, or it may call
   * receiveExcception if an exception occurred.
   *
   * @param log The log to remove
   * @param command The command to run once done
   */
  public void removeChildLog(Object name, final Continuation command) {
    children.remove(name);

    Continuation c = new Continuation() {
      public void receiveResult(Object o) {
        command.receiveResult(new Boolean(true));
      }

      public void receiveException(Exception e) {
        command.receiveException(e);
      }
    };
    
    sync(c);
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
   * @deprecated Use the getChildLog(name, continuation) - Log metadata needs to be set up
   * @param name The name of the log to return.
   * @return A reference to the requested log, or null if the name
   * is unrecognized.
   */
  public LogReference getChildLog(Object name) {
    return (LogReference) children.get(name);
  }

  /**
   * This method returns a the specific child log of
   * this log, given the child log's name.
   *
   * @param name The name of the log to return.
   * @param command The command to run once done.
   */
  public void getChildLog(Object name, final Continuation command) {
    LogReference ref = (LogReference) children.get(name);

    if (ref == null) {
      command.receiveResult(null);
        return;
    }

    Continuation fetch = new Continuation() {
      public void receiveResult(Object o) {
        Log log = (Log) o;
        log.setPost(post);

        command.receiveResult(o);
      }

      public void receiveException(Exception e) {
        command.receiveException(e);
      }
    };

    post.getStorageService().retrieveSigned(ref, fetch);
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
  	
	  if (buffer == null) {
	      System.out.println("Null buffer. creating a new one");
	      buffer = new Vector();
	      bufferFlag = false;
	  }
	//     if (bufferFlag) {
//  	  System.out.println("DEBUG: BufferFlag: " + bufferFlag + " " + this.toString());
	  Continuation comm = new Continuation() {
		  public void receiveResult(Object o) {
		      try {
			  command.receiveResult(o);
			  if (!(buffer.isEmpty())) {
			      System.out.println("DEBUG: In while, Size: " +  buffer.size());
			      AddLogEntryTask alet = (AddLogEntryTask) buffer.firstElement();
			      System.out.println(alet);
			      buffer.remove(alet);
			      System.out.println("DEBUG: removed, size: "  + buffer.size());
			      alet.start();
			  }

			  System.out.println("Receive result: " + ((LogEntry)o).toString());
			  bufferFlag = false;
			
		      }catch (Exception e) {
			  command.receiveException(e);
		      }   
		  }
	         
		  public void receiveException(Exception e) {
		      command.receiveException(e);
		  }
	      };
	 
   if (bufferFlag) {
	  System.out.println("DEBUG: BufferFlag: " + bufferFlag + " " + this.toString());
	  System.out.println("SIZE: " + this.buffer.size());
	  AddLogEntryTask alet2 = new AddLogEntryTask(entry, comm);
	  this.buffer.add(alet2);
	System.out.println("SIZE: " + this.buffer.size());  
	  System.out.println("DEBUG: added task to buffer " + entry);
	  }
        else { 
	  System.out.println("DEBUG: No need of buffer: running task " + entry + " Size: " + buffer.size() + " " + this.toString());
	  AddLogEntryTask task = new AddLogEntryTask(entry, comm);
	  bufferFlag = true;
	  task.start();
      }
  }
    
  /**
   * This method returns a reference to the most recent entry in the log,
   * which can then be used to walk down the log.
   *
   * @return A reference to the top entry in the log.
   */
  public void getTopEntry(final Continuation command) {
    if ((topEntry == null) && (topEntryReference != null)) {
      Continuation fetch = new Continuation() {
        public void receiveResult(Object o) {
          try {
            topEntry = (LogEntry) o;
            topEntry.setPost(post);
            command.receiveResult(topEntry);
          } catch (ClassCastException e) {
            command.receiveException(e);
          }
        }

        public void receiveException(Exception e) {
          command.receiveException(e);
        }
      };

      post.getStorageService().retrieveContentHash(topEntryReference, fetch);
    } else {
      command.receiveResult(topEntry);
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
    //  public ContentHashReference buildContentHashReference(Id location, Key key) {
 public ContentHashReference buildContentHashReference(Id location, byte key[]) {
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
   * This class encapsulates the logic needed to add a child log to
   * the current log.
   */
  protected class AddChildLogTask implements Continuation {

    public static final int STATE_1 = 1;
    public static final int STATE_2 = 2;
    
    private Log log;
    private LogReference reference;
    private Continuation command;
    private int state;

    /**
     * This construct will build an object which will call the given
     * command once processing has been completed, and will provide
     * a result.
     *
     * @param log The log to add
     * @param command The command to call
     */
    protected AddChildLogTask(Log log, Continuation command) {
      this.log = log;
      this.command = command;
    }

    /**
     * Starts the process to add the child log.
     */
    public void start() {
      state = STATE_1;
      post.getStorageService().storeSigned(log, log.getLocation(), this);
    }

    private void startState1(LogReference reference) {
      this.reference = reference;
      children.put(log.getName(), reference);

      state = STATE_2;
      SyncTask task = new SyncTask(this);
      task.start();
    }

      private void startState2() {
      command.receiveResult(reference);
    }

    /**
     * Receives the result of a command.
     */
    public void receiveResult(Object o) {
      switch(state) {
        case STATE_1:
          startState1((LogReference) o);
          break;
        case STATE_2:
          startState2();
          break;
        default:
          command.receiveException(new StorageException("Received unexpected state: " + state));
          break;
      }
    }

    /**
      * Called when a previously requested result causes an exception
     *
     * @param result The exception caused
     */
    public void receiveException(Exception result) {
      command.receiveException(result);
    }
  }

  /**
   * This class encapsulates the logic needed to add a log entry to
   * the current log.
   */
  protected class AddLogEntryTask implements Continuation {

    public static final int STATE_1 = 1;
    public static final int STATE_2 = 2;

    private LogEntry entry;
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

    public void start() {
	System.out.println("DEBUG: In start: BufferFlag = " + bufferFlag + " " + entry);
	
      state = STATE_1;
      entry.setPost(post);
      entry.setUser(post.getEntityAddress());
      entry.setPreviousEntryReference(topEntryReference);
      System.out.println("set PEF for entry: " + topEntryReference);
      entry.setPreviousEntry(topEntry);
      post.getStorageService().storeContentHash(entry, this);
	
    }

    private void startState1(LogEntryReference reference) {
	System.out.println("DEBUG: In state1 " + entry);
      topEntryReference = reference;
      System.out.println("TEF: "  + topEntryReference);      
      topEntry = entry;
      state = STATE_2;
      SyncTask task = new SyncTask(this);
      task.start();
    }

    private void startState2() {
	System.out.println("DEBUG: In state2 " + entry + " Size: " + buffer.size());
	//bufferFlag = false;
	//command.receiveResult(topEntry);
	//  if (!(buffer.isEmpty())) { 
//  		    System.out.println("DEBUG: In while, Size: " +  buffer.size());
//  	    AddLogEntryTask alet = (AddLogEntryTask) buffer.firstElement();
//  	    System.out.println(alet);
//  	    buffer.remove(alet);
//  	    System.out.println("DEBUG: removed, size: "  + buffer.size());
//  	    alet.start();
//  	}
//  	bufferFlag = false;
	command.receiveResult(topEntry);
	System.out.println("done " + bufferFlag + " " + entry + " Size: " + buffer.size());
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
      command.receiveException(result);
    }
  }

  /**
   * This class encapsulates the logic needed to sync
   * the current log on the network.
   */
  protected class SyncTask implements Continuation {

    private Continuation command;
    
    /**
     * This construct will build an object which will call the given
     * command once processing has been completed, and will provide
     * a result.
     *
     * @param command The command to call
     */
    protected SyncTask(Continuation command) {
      this.command = command;
    }

    public void start() {
      post.getStorageService().storeSigned(Log.this, location, this);
    }

    public void receiveResult(Object o) {
      command.receiveResult(Log.this);
    }

    /**
      * Called when a previously requested result causes an exception
     *
     * @param result The exception caused
     */
    public void receiveException(Exception result) {
      command.receiveException(result);
    }
  }

  public String toString() {
    return "Log[" + name + "]";
  }
}

