package rice.email;

import rice.*;
import java.util.*;
import rice.email.messaging.*;
import rice.email.log.*;
import rice.post.*;
import rice.post.log.*;
import rice.post.storage.*;

/**
 * Represents a notion of a folder in the email service.
 */
public class Folder {
  // maximum entry limit for our primitive snapshot policy
  private static final int COMPRESS_LIMIT = 100;

  // name of the folder
  private String _name;

  // the log of the Folder
  private Log _log;

  // the storage service used by the Folder to fetch log contents
  private StorageService _storage;

  // the post service used by the Folder to create new logs
  private Post _post;

  /**
   * Constructs an empty Folder.
   * @param name The name of the folder.
   */
  public Folder(String name) {
    _name = name;
  }

  /**
   * Constructs a Folder from a log and a storage service.
   *
   * @param log the Log which contains the data for the Folder.
   * @param storage the storage service used to get log data from PAST.
   */
  public Folder(Log log, Post post) {
    _name = (String) log.getName();
    _log = log;
    _post = post;
    _storage = post.getStorageService();
  }

  /**
   * Used to read the contents of the Folder and build up the array
   * of Emails stored by the Folder.
   */
  private void readContents(Continuation command) throws PostException {
    // create the state for this process
    Vector state = new Vector();
    // get the ref to the top entry
    LogEntryReference top = _log.getTopEntry();
    // save the user's command, and make a new command to carry out.
    FolderReadContentsTask preCommand = new FolderReadContentsTask(state, command);
    // begin retreiving the contents and building up the list of Emails stored in the Folder
    _storage.retrieveContentHash(top, preCommand);
  }

  /**
   * Handles the snapShot policy of the Folder.  The current policy is
   * to check to see if more than 100 entries need to be read to build
   * up the contents of the Folder.  If more than 100 entries need to
   * be read, a new SnapShot is entered.
   * 
   * @param count the number of entries in the log that need to be
   * read before the complete Folder contents can be returned.
   * @param state the current contents of the Folder
   */
  private void snapShotUpdate(int entries, Email[] contents, Continuation command) {
    // if the number of entries is greater than the compression limit,
    // add a new snapshot
    // return the final value to the user's continuation
    command.receiveResult(contents);
    
    if (entries > COMPRESS_LIMIT) {
	_log.addLogEntry(new SnapShotLogEntry(contents), command);
    } 
    else {
      command.receiveResult((Object)contents);
    }
  }

  /**
   * Returns the name of this folder
   *
   * @return The name of the folder
   */
  public String getName() {
    return _name;
  }

  /**
   * Returns the Emails contained in this Folder.
   *
   * @return the stored Emails
   */
  public void getMessages(Continuation command) throws PostException {
    readContents(new FolderNullTask(command));
  }

  /**
    * Appends an email to this Folder.
   *
   * @param email The email to insert.
   */
  public void addMessage(Email email) throws PostException {
    Continuation command = new FolderNullTask(null);
    _log.addLogEntry(new InsertMailLogEntry(email), command);
  }

  /**
    * Deletes a message from this Folder.
   *
   * @param email The email to delete.
   */
  public void removeMessage(Email email) throws PostException {
    Continuation command = new FolderNullTask(null);
    _log.addLogEntry(new DeleteMailLogEntry(email), command); 
  }

  /**
   * Moves a message from this folder into a another, given folder.
   * This means adding the message to the destination folder, and
   * removing the message from this folder.
   *
   * @param email The email to move.
   * @param srcFolder The source folder for the message.
   * @param destFolder The destination folder for the message.
   */
  public void moveMessage(Email email, Folder folder) throws PostException {
    folder.addMessage(email);
    removeMessage(email);
  }

  /**
   * Creates a new child of the given name.  The current Folder
   * is the parent.
   *
   * @param name the name of the new child Folder
   * @return the newly created child Folder
   */   
  public void createChildFolder(String name, Continuation command) throws PostException {
    // make the log to add
    Log log = new Log(name, _log.getLocation(), _post);
    // make the entry to insert after the new log has been added
    LogEntry entry = new InsertFolderLogEntry(name);
    // make the continuation to perform after adding the log.  This takes in the entry to insert
    // and the log to insert it into.  
    Continuation preCommand = new FolderAddLogEntryTask(entry, _log, command);
    _log.addChildLog(log, preCommand);    
  }

  /**
   * Deletes a folder from the user's mailbox.
   *
   * @param name The name of the folder to delete.
   */
  public void removeFolder(String name) throws PostException {
    // make the continuation to perform after removing the log
    Continuation command = new FolderNullTask(null);
    // make the entry to insert after the log has been deleted
    LogEntry entry = new DeleteFolderLogEntry(name);
    // make the continuation to perform after adding the log.  This takes in the entry to insert
    // and the log to insert it into.  
    Continuation preCommand = new FolderAddLogEntryTask(entry, _log, command);
    _log.removeChildLog(name, preCommand);
  }

  /**
   * Returns the selected Folder.  The Folder is selected by its name;
   * if the Folder does not exist an exception is thrown.
   * 
   * @param name the name of the Folder to return
   * @return the selected child Folder
   */
  public void getChildFolder(String name, Continuation command) throws PostException {    
    FolderGetLogTask preCommand = new FolderGetLogTask(command);
    _storage.retrieveSigned(_log.getChildLog(name), preCommand);
  }

  /**
   * Returns the names of the child Folders of the current Folder.
   *
   * @return an array of the names of the child Folders
   */
  public String[] getChildren(Continuation command) throws PostException {
    return (String[])_log.getChildLogNames();    
  }

  /**
   * Return all the events that happened after the arrival
   * of the given email in the Folder.  
   * JM Problem, does not have access to folder creation/deletion information.
   *
   * @param target the email to act as the signal to stop
   * @return the array of recent events
   */
  public void getPartialEventLog(Email target, Continuation command) {
    /*
      returns EmailEvent[]
    // setup the control vars
    Vector events = new Vector();
    boolean finished = false;
    // get the top entry in the log
    LogEntryReference top = _log.getTopEntry();
    LogEntry topEntry = null;
    try {
      topEntry = (LogEntry)_storage.retrieveContentHash(top);
    } catch (StorageException s) {
      // JM do something sensible here
    }
    Email currentEmail = null;
    
    // read off and store each entry until the given email is found
    while (!finished) {
      if (topEntry instanceof InsertMailLogEntry) {
	currentEmail = ((InsertMailLogEntry)topEntry).getEmail();
	events.add(new InsertMailEvent(currentEmail));
	// if we have reaced the target email, stop going through the log
	if (currentEmail.equals(target)) {
	  finished = true;
	}
      }
      else if (topEntry instanceof DeleteMailLogEntry) {
	events.add(new DeleteMailEvent(((DeleteMailLogEntry)topEntry).getEmail()));
      }
      
      // try to move to the next LogEntry
      top = topEntry.getPreviousEntry();
      if (top == null) {
        finished = true;
      }
      else {
	try {
	  topEntry = (LogEntry)_storage.retrieveContentHash(top);
	} catch (StorageException s) {
	  // JM do something sensible here
	}	
      }
    }
    return (EmailEvent[])events.toArray();
    */
  }  
  
  
  /**
   * Return all the events that have happened so far in this Folder.
   *
   * @return the complete array of events
   */
  public void getCompleteEventLog(Continuation command)  {
    /*
      returns EmailEvent[]
    // setup the control vars
    Vector events = new Vector();
    boolean finished = false;
    // get the top entry in the log
    LogEntryReference top = _log.getTopEntry();
    LogEntry topEntry = null;
    try {
      topEntry = (LogEntry)_storage.retrieveContentHash(top);
    } catch (StorageException s) {
      // JM do something sensible here
    }

    // read off and store each entry
    while (!finished) {
      if (topEntry instanceof InsertMailLogEntry) {
	events.add(new InsertMailEvent(((InsertMailLogEntry)topEntry).getEmail()));
      }
      else if (topEntry instanceof DeleteMailLogEntry) {
	events.add(new DeleteMailEvent(((DeleteMailLogEntry)topEntry).getEmail()));
      }
      if (topEntry instanceof InsertFolderLogEntry) {
	events.add(new InsertFolderEvent(((InsertFolderLogEntry)topEntry).getName()));
      }
      else if (topEntry instanceof DeleteFolderLogEntry) {
	events.add(new DeleteFolderEvent(((DeleteFolderLogEntry)topEntry).getName()));
      }

      // try to move to the next LogEntry
      top = topEntry.getPreviousEntry();
      if (top == null) {
        finished = true;
      }
      else {
	try {
	  topEntry = (LogEntry)_storage.retrieveContentHash(top);
	} catch (StorageException s) {
	  // JM do something sensible here
	}
      }
    }
    return (EmailEvent[])events.toArray();
    */
  }  

  /**
   * Reads through each of the nodes in the log up until the end or a snapshot node.  Returns the final
   * compilation of each of these nodes.
   */
  protected class FolderReadContentsTask implements Continuation {
    Vector _contents;
    Continuation _command;

    /**
     * Constructs a FolderReadContentsTask.
     */
    public FolderReadContentsTask(Vector state, Continuation command) {
      _contents = state;
      _command = command;
    }

    /**
     * Starts the processing of this task.
     */
    public void start() {
      // JM I don't believe anything needs to be done here
    }

    /**
     * Takes the result of the fetch, and adds it to the current contents.  If we are finished reading the log,
     * do the snapshot update (which will return this function's value to the user).  If we are not finished, 
     * perform another fetch and pass the current state on to the next call.
     */
    public void receiveResult(Object o) {
      LogEntryReference top = null;
      LogEntry topEntry = (LogEntry)o;
      boolean finished = false;
      
      // deal with the current LogEntry
      if (topEntry instanceof InsertMailLogEntry) {
        _contents.add(((InsertMailLogEntry)topEntry).getEmail());
      }
      else if (topEntry instanceof DeleteMailLogEntry) {
        _contents.remove(((DeleteMailLogEntry)topEntry).getEmail());
      }      
      else if (topEntry instanceof SnapShotLogEntry) {
        Email[] rest = ((SnapShotLogEntry)topEntry).getEmails();
        for (int i = 0; i < rest.length; i++) {
          _contents.add(rest[i]);
        }
        if (((SnapShotLogEntry)topEntry).isEnd()) {
          finished = true;
        }
      }
      else {
        // throw some manner of error?
        finished = true;
      }

      // if not finished, try to move to the next LogEntry
      if (!finished) {
	top = topEntry.getPreviousEntry();
	if (top == null) {
	  finished = true;
	}
      }

      // if finished, check to see if a new snapshot needs to be entered and return
      if (finished) {
	// add a snapshot if need be.  The snapShot method will start the user task (i.e. return the result) 
	// even if no snapshot is added
	FolderReturnResultTask preCommand = new FolderReturnResultTask(_contents, _command);
	snapShotUpdate(_contents.size(), (Email[])_contents.toArray(), _command);	
      // otherwise continue building up the contents
      } else {
	FolderReadContentsTask preCommand = new FolderReadContentsTask(_contents, _command);
	_storage.retrieveContentHash(top, preCommand);
      }
    }

    /**
     * Simply prints out an error message.
     */  
    public void receiveException(Exception e) {
      System.out.println("Exception " + e + "  occured while trying to read the contents of LogEntry " + _contents.size());
    }
  }

  /**
   * Returns the contents to the given command.
   */
  protected class FolderReturnResultTask implements Continuation {
    Object _result;
    Continuation _command;

    /**
     * Constructs a FolderReturnResultTask.
     */
    public FolderReturnResultTask(Object result, Continuation command) {
      _result = result;
      _command = command;
    }

    /**
     * Starts the processing of this task.
     */
    public void start() {
      // JM I don't believe anything needs to be done here
    }

    /**
     * Returns the result to the given user continuation.
     */
    public void receiveResult(Object o) {
      _command.receiveResult(_result);
    }

    /**
     * Simply prints out the error message.
     */  
    public void receiveException(Exception e) {
      System.out.println("Exception " + e + "  occured while trying to return the result to the user's task");
    }
  }

  /**
   * Returns the contents to the given command.
   */
  protected class FolderAddLogEntryTask implements Continuation {
    LogEntry _entry;
    Log _newLog;
    Continuation _command;

    /**
     * Constructs a FolderAddLogEntryTask.
     */
    public FolderAddLogEntryTask(LogEntry entry, Log newLog, Continuation command) {
      _entry = entry;
      _newLog = newLog;
      _command = command;
    }

    /**
     * Starts the processing of this task.
     */
    public void start() {
      // JM I don't believe anything needs to be done here
    }

    /**
     * Returns the contents to the given user continuation.
     */
    public void receiveResult(Object o) {
      Folder result = new Folder(_newLog, _post);
      FolderReturnResultTask preCommand = new FolderReturnResultTask(result, _command);
      _log.addLogEntry(_entry, preCommand);
    }

    /**
     * Simply prints out the error message.
     */  
    public void receiveException(Exception e) {
      System.out.println("Exception " + e + "  occured while trying to add a new Log entry to the log");
    }
  }

  /**
   * Task used for when nothing needs to be done after the initial action.
   */
  protected class FolderNullTask implements Continuation {

    Continuation _command;
    /**
     * Constructs a FolderNullTask.
     */
    public FolderNullTask(Continuation command) {
      _command = command;
    }

    /**
     * Starts the processing of this task.
     */
    public void start() {
      // JM I don't believe anything needs to be done here
    }

    /**
     * Returns the contents to the given user continuation.
     */
    public void receiveResult(Object o) {
      if (_command != null) {
	_command.receiveResult(o);
      }
    }

    /**
     * Simply prints out the error message.
     */  
    public void receiveException(Exception e) {
      System.out.println("Exception " + e + "  occured while trying peform work for Null Task");
    }
  }

  /**
   * Retrieves a log, and then returns a Folder generated by the retreived log.
   */
  protected class FolderGetLogTask implements Continuation {
    Continuation _command;

    /**
     * Constructs a FolderGetLogTask.
     */
    public FolderGetLogTask(Continuation command) {
      _command = command;
    }

    /**
     * Starts the processing of this task.
     */
    public void start() {
      // JM I don't believe anything needs to be done here
    }

    /**
     * Take the result, form a Folder from it, and returns the Folder to the given user continuation.
     */
    public void receiveResult(Object o) {      
      Log log = (Log)o;
      _command.receiveResult(new Folder(log, _post));
    }

    /**
     * Simply prints out the error message.
     */  
    public void receiveException(Exception e) {
      System.out.println("Exception " + e + "  occured while trying to return the new Folder");
    }
  }



}
  




