package rice.email;

import rice.*;
import java.util.*;
import rice.email.*;
import rice.email.messaging.*;
import rice.email.log.*;
import rice.post.*;
import rice.post.log.*;
import rice.post.storage.*;

/**
 * Represents a notion of a folder in the email service.
 * @author Joe Montgomery
 */
public class Folder {
  // maximum entry limit for our primitive snapshot policy
  public static final int COMPRESS_LIMIT = 5;

  // name of the folder
  private String _name;

  // the underlying log of the Folder
  private EmailLog _log;

  // the storage service used by the Folder to fetch log contents
  private StorageService _storage;

  // the post service used by the Folder to create new logs
  private Post _post;

  // the cache of children
  private Hashtable _children;

   
  /**
   * Constructs an empty Folder.
   * @param name The name of the folder.
   */
  public Folder(String name) {
    _name = name;
    _children = new Hashtable();
  }

  /**
   * Constructs a Folder from a log and a storage service.
   *
   * @param log the Log which contains the data for the Folder.
   * @param storage the storage service used to get log data from PAST.
   */
  public Folder(EmailLog log, Post post) {
    if (log.getName() instanceof String) {
      _name = (String) log.getName();
    } else {
      _name = "Root";
    }

    _log = log;
    _post = post;
    _storage = post.getStorageService();
    _children = new Hashtable();
  }

  /**
   * Sets the post service of this Folder.
   * @param post the new post service for this Folder
   */
  public void setPost(Post post) {
    _post = post;
    _log.setPost(post);
  }

  /**
   * Used to read the contents of the Folder and build up the array
   * of Emails stored by the Folder.
   * @param command the work to perform after this call
   */
  private void readContents(Continuation command) {
    // create the state for this process
    Vector state = new Vector();
    
    // save the user's command, and make a new command to carry out.
    FolderReadContentsTask preCommand = new FolderReadContentsTask(state, command);
    
    // begin retreiving the contents and building up the list of Emails stored in the Folder
    _log.getTopEntry(preCommand);
  }

  /**
   * Handles the snapShot policy of the Folder.  The current policy is
   * to check to see if more than 100 entries need to be read to build
   * up the contents of the Folder.  If more than 100 entries need to
   * be read, a new SnapShot is entered.
   *
   * @param entries the number of entries in the log that need to be
   * read before the complete Folder contents can be returned.
   * @param contents the Email[] that needs to be returned
   * @param command the work to perform after this call
   */
  private void snapShotUpdate(int entries, StoredEmail[] contents, Continuation command) {
    // if the number of entries is greater than the compression limit,
    // add a new snapshot
    if (entries > COMPRESS_LIMIT) {
      _log.addLogEntry(new SnapShotLogEntry(contents), command);
      
      // otherwise just return the result
    } 
    else {
	command.receiveResult(contents);
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
   * @param command the work to perform after this call
   * @return the stored Emails
   */
  public void getMessages(Continuation command) {
    readContents(new FolderNullTask(command));
  }

    /**
     * Updates an Email (flags)
     *
     * @param email The email to update.
     * @param command the work to perform after this call.
     */
    public void updateMessage(StoredEmail email, Continuation command) {
	_log.addLogEntry(new UpdateMailLogEntry(email), command);
    }

  /**
   * Appends an email to this Folder.
   * Creates a new StoredEmail instance with the given email.
   * Sets all flags to false for the new email in the folder
   *
   * @param email The email to insert.
   * @param command the work to perform after this call
   */
  public void addMessage(Email email, Continuation command) {
      Flags flags = new Flags();
      StoredEmail storedEmail = new StoredEmail(email, _log.getNextUID(), flags); 
    _log.addLogEntry(new InsertMailLogEntry(storedEmail), command);
  }

  /**
   * Moves a message from this folder into a another, given folder.
   * This means adding the message to the destination folder, and
   * removing the message from this folder.
   *
   * @param email The email to move.
   * @param folder The destination folder for the message.
   * @param command the remaining work to carry out
   */
  public void moveMessage(StoredEmail email, Folder folder, Continuation command) {
    Continuation preCommand = new FolderRemoveMessageTask(email, this, command);
    folder.addMessage(email.getEmail(), preCommand);
  }

  /**
   * Deletes a message from this Folder.
   *
   * @param email The email to delete.
   * @param command the remaining work to carry out
   */
  public void removeMessage(StoredEmail email, Continuation command) {
    _log.addLogEntry(new DeleteMailLogEntry(email), command);
  }

  /**
   * Moves a message from this folder into a another, given folder.
   * This means adding the message to the destination folder, and
   * removing the message from this folder.
   *
   * @param email The email to move.
   * @param folder The destination folder for the message.
   */
  public void moveMessage(StoredEmail email, Folder folder) {
    Continuation command = new FolderRemoveMessageTask(email, this);
    folder.addMessage(email.getEmail(), command);
  }

  /**
   * Deletes a message from this Folder.
   *
   * @param email The email to delete.
   */
  public void removeMessage(StoredEmail email) {
    Continuation command = new FolderNullTask(null);
    _log.addLogEntry(new DeleteMailLogEntry(email), command);
  }


  /**
   * Creates a new child of the given name.  The current Folder
   * is the parent.
   *
   * @param name the name of the new child Folder
   * @param command the work to perform after this call
   */
  public void createChildFolder(String name, Continuation command) {
    // make the log to add
    EmailLog log = new EmailLog(name, _storage.getRandomNodeId(), _post);
    // make the entry to insert after the new log has been added
    LogEntry entry = new InsertFolderLogEntry(name);
    // make the continuation to perform after adding the log.  This takes in the entry to insert
    // and the log to insert it into.
    Continuation preCommand = new FolderAddLogEntryTask(name, entry, log, command);
    _log.addChildLog(log, preCommand);
  }

  /**
   * Deletes a folder from the user's mailbox.
   *
   * @param name The name of the folder to delete.
   */
  public void removeFolder(String name) {
    _children.remove(name);
    
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
   * Deletes a folder from the user's mailbox.
   *
   * @param name The name of the folder to delete.
   * @param command the work to perform after this call
   */
  public void removeFolder(String name, Continuation command) {
    _children.remove(name);
    
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
   * @param command the work to perform after this call
   */
  public void getChildFolder(String name, Continuation command) {
    if (_children.get(name) == null) {
      FolderGetLogTask preCommand = new FolderGetLogTask(name, command);
      _storage.retrieveSigned(_log.getChildLog(name), preCommand);
    } else {
      command.receiveResult(_children.get(name));
    }
  }

  /**
   * Returns the names of the child Folders of the current Folder.
   * @return an array of the names of the child Folders
   */
  public String[] getChildren() {
    Object[] resultO = _log.getChildLogNames();
    String[] result = new String[resultO.length];

    for (int i=0; i<result.length; i++) {
      result[i] = (String) resultO[i];
    }

    return result;
  }

  /**
   * Return all the events that happened after the arrival
   * of the given email in the Folder.
   *
   * @param target the email to act as the signal to stop
   * @param command the work to perform after this call
   */
  public void getPartialEventLog(StoredEmail target, Continuation command) {
    Vector events = new Vector();

    // get the top entry in the log.
    Continuation preCommand = new FolderGetEventsTask(target, events, command);
    _log.getTopEntry(preCommand);
  }


  /**
   * Return all the events that have happened so far in this Folder.
   *
   * @param command the work to perform after this call
   */
  public void getCompleteEventLog(Continuation command)  {
    getPartialEventLog(null, command);
  }

  /**
   * Reads through each of the nodes in the log up until the end or a snapshot node.  Returns the final
   * compilation of each of these nodes.
   */
  protected class FolderReadContentsTask implements Continuation {
    Vector _contents;
    Vector _deleted;
    Continuation _command;

    /**
     * Constructs a FolderReadContentsTask.
     */
    public FolderReadContentsTask(Vector state, Continuation command) {
      _contents = state;
      _command = command;
      _deleted = new Vector();
    }

    /**
     * Takes the result of the fetch, and adds it to the current contents.  If we are finished reading the log,
     * do the snapshot update (which will return this function's value to the user).  If we are not finished,
     * perform another fetch and pass the current state on to the next call.
     */
    public void receiveResult(Object o) {
      LogEntry topEntry = (LogEntry)o;
      boolean finished = false;

      if (topEntry != null) {
        // deal with the current LogEntry.  If the entry is not an email or equivalent, ignore it
        // and go on to the next one
        if (topEntry instanceof InsertMailLogEntry) {
	    if (_contents.contains(((InsertMailLogEntry)topEntry).getStoredEmail())) { //skip entry.
	    }
	    else {
		((InsertMailLogEntry)topEntry).getStoredEmail().getEmail().setStorage(_storage);
		_contents.add(((InsertMailLogEntry)topEntry).getStoredEmail());
	    }
	}
        else if (topEntry instanceof DeleteMailLogEntry) {
          _deleted.add(((DeleteMailLogEntry)topEntry).getStoredEmail());
          System.out.println("Found deleted entry...");
        }

	else if (topEntry instanceof UpdateMailLogEntry) {
	    if (_contents.contains(((UpdateMailLogEntry)topEntry).getStoredEmail())) { //skip entry. updated entry already added
	    }
	    else {
		((UpdateMailLogEntry)topEntry).getStoredEmail().getEmail().setStorage(_storage);
		_contents.add(((UpdateMailLogEntry)topEntry).getStoredEmail());
	    }
	}
	
        else if (topEntry instanceof SnapShotLogEntry) {
	    StoredEmail[] rest = ((SnapShotLogEntry)topEntry).getStoredEmails();
	    for (int i = 0; i < rest.length; i++) {
		rest[i].getEmail().setStorage(_storage);
		_contents.add(rest[i]);
	    }
          if (((SnapShotLogEntry)topEntry).isEnd()) {
	      finished = true;
          }
        }
      }

      else {
	  finished = true;
      }
      
      // if finished, check to see if a new snapshot needs to be entered and return
      if (finished) {
        // first, remove all of the deleted messages from the list
        for (int i=0; i<_deleted.size(); i++) {
          _contents.remove(_deleted.elementAt(i));
        }
        
        // add a snapshot if need be.  The snapShot method will start the user task (i.e. return the result)
        // even if no snapshot is added
        FolderReturnResultTask preCommand = new FolderReturnResultTask(_contents, _command);
        StoredEmail[] resultEmails = new StoredEmail[_contents.size()];
        for (int i = 0; i < resultEmails.length; i++) {
          resultEmails[i] = (StoredEmail)(_contents.get(i));
        }
      //  snapShotUpdate(_contents.size(), resultEmails, _command);
        // otherwise continue building up the contents
        _command.receiveResult(resultEmails);
      } else {
        topEntry.getPreviousEntry(this);
      }
    }

    /**
     * Simply prints out an error message.
     */
    public void receiveException(Exception e) {
      System.out.println("Exception " + e + "  occured while trying to read the LogEntry.");
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
    EmailLog _newLog;
    Continuation _command;
    String _name;
    boolean _isAdded = false;

    /**
      * Constructs a FolderAddLogEntryTask.
     */
    public FolderAddLogEntryTask(LogEntry entry, EmailLog newLog, Continuation command) {
      _entry = entry;
      _newLog = newLog;
      _command = command;
    }
    
    /**
     * Constructs a FolderAddLogEntryTask.
     */
    public FolderAddLogEntryTask(String name, LogEntry entry, EmailLog newLog, Continuation command) {
      _entry = entry;
      _newLog = newLog;
      _command = command;
      _name = name;
      _isAdded = true;
    }

    /**
     * Returns the contents to the given user continuation.
     */
    public void receiveResult(Object o) {
      Folder result = new Folder(_newLog, _post);

      if (_isAdded)
        _children.put(_name, result);
      
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
    boolean _store = false;
    String _name;

    /**
     * Constructs a FolderGetLogTask.
     */
    public FolderGetLogTask(Continuation command) {
      _command = command;
    }

    /**
     * Constructs a FolderGetLogTask.
     */
    public FolderGetLogTask(String name, Continuation command) {
      _command = command;
      _name = name;
      _store = true;
    }

    /**
     * Take the result, form a Folder from it, and returns the Folder to the given user continuation.
     */
    public void receiveResult(Object o) {
      EmailLog log = (EmailLog) o;
      log.setPost(_post);

      Folder f = new Folder(log, _post);

      if (_store) {
        _children.put(_name, f);
      }
      
      _command.receiveResult(f);
    }

    /**
     * Simply prints out the error message.
     */
    public void receiveException(Exception e) {
      System.out.println("Exception " + e + "  occured while trying to return the new Folder\n");
    }
  }


  /**
   * Retrieves a log, and then returns a Folder generated by the retreived log.
   */
  protected class FolderGetEventsTask implements Continuation {
    StoredEmail _target;
    Vector _events;
    Continuation _command;

    /**
     * Constructs a FolderGetEventsTask.
     */
    public FolderGetEventsTask(StoredEmail target, Vector events, Continuation command) {
      _target = target;
      _events = events;
      _command = command;
    }

    /**
     * Take the result, form a Folder from it, and returns the Folder to the given user continuation.
     */
    public void receiveResult(Object o) {
      boolean finished = false;
      LogEntry topEntry = (LogEntry)o;

      if (o != null) {
        // store an event corresponding to the topEntry
        if (topEntry instanceof InsertMailLogEntry) {
          Email currentEmail = ((InsertMailLogEntry)topEntry).getStoredEmail().getEmail();
          _events.add(new InsertMailEvent(currentEmail));
          // if we have reaced the target email, stop going through the log
          if ((_target != null) && (currentEmail.equals(_target))) {
            finished = true;
          }
        }
        else if (topEntry instanceof DeleteMailLogEntry) {
          _events.add(new DeleteMailEvent(((DeleteMailLogEntry)topEntry).getStoredEmail().getEmail()));
        }
        if (topEntry instanceof InsertFolderLogEntry) {
          _events.add(new InsertFolderEvent(((InsertFolderLogEntry)topEntry).getName()));
        }
        else if (topEntry instanceof DeleteFolderLogEntry) {
          _events.add(new DeleteFolderEvent(((DeleteFolderLogEntry)topEntry).getName()));
        }
      } else {
        finished = true;
      }
        
      // if not done, start fetching the next log entry
      if (!finished) {
        topEntry.getPreviousEntry(this);
      } else {
        Event[] resultEvents = new Event[_events.size()];
        for (int i = 0; i < resultEvents.length; i++) {
          resultEvents[i] = (Event)(_events.get(i));
        }
        
        _command.receiveResult(resultEvents);
      }
    }

    /**
     * Simply prints out the error message.
     */
    public void receiveException(Exception e) {
      System.out.println("Exception " + e + "  occured while trying to get the events");
    }
  }


  /**
   * Calls removeMessage once the original continuation finishes
   */
  protected class FolderRemoveMessageTask implements Continuation {
    StoredEmail _email;
    Folder _folder;
    Continuation _command;

    /**
     * Constructs a FolderGetLogTask.
     */
    public FolderRemoveMessageTask(StoredEmail email, Folder folder) {
      _email = email;
      _folder = folder;
      _command = new FolderNullTask(null);
    }

    /**
     * Constructs a FolderGetLogTask.
     */
    public FolderRemoveMessageTask(StoredEmail email, Folder folder, Continuation command) {
      _email = email;
      _folder = folder;
      _command = command;
    }

    /**
     * Starts the processing of this task.
     */
    public void start() {}

    /**
     * Take the result, form a Folder from it, and returns the Folder to the given user continuation.
     */
    public void receiveResult(Object o) {
      _folder.removeMessage(_email, _command);
    }

    /**
     * Simply prints out the error message.
     */
    public void receiveException(Exception e) {
      System.out.println("Exception " + e + "  occured while trying to add a message to a Folder");
    }
  }

  public String toString() {
    return getName();
  }
 }





