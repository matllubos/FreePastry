package rice.email;

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
  public Folder(Log log, StorageService storage) {
    _name = (String) log.getName();
    _log = log;
    _storage = storage;
  }

  /**
   * Used to read the contents of the Folder and build up the array
   * of Emails stored by the Folder.
   */
  private Email[] readContents() throws PostException {
    Vector contents = new Vector();
    boolean finished = false;
    LogEntryReference top = _log.getTopEntry();
    LogEntry topEntry = null;
    try {
      topEntry = (LogEntry)_storage.retrieveContentHash(top);
    } catch (StorageException s) {
      // JM do something sensible here
    }
    int uncompressedEntries = 0;
    
    // walk through the log and build up the contents of the folder
    while (!finished) {
      // deal with the current LogEntry
      if (topEntry instanceof InsertMailLogEntry) {
        contents.add(((InsertMailLogEntry)topEntry).getEmail());
      }
      else if (topEntry instanceof DeleteMailLogEntry) {
        contents.remove(((DeleteMailLogEntry)topEntry).getEmail());
      }
      else if (topEntry instanceof SnapShotLogEntry) {
        Email[] rest = ((SnapShotLogEntry)topEntry).getEmails();
        for (int i = 0; i < rest.length; i++) {
          contents.add(rest[i]);
        }
        if (((SnapShotLogEntry)topEntry).isEnd()) {
          finished = true;
        }
      }
      else {
        // throw some manner of error?
        finished = true;
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
      uncompressedEntries += 1;
    }
    snapShotUpdate(uncompressedEntries, (Email[])contents.toArray() );
    return (Email[])contents.toArray();
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
  private void snapShotUpdate(int entries, Email[] contents) {
    // if the number of entries is greater than the compression limit,
    // add a new snapshot
    if (entries > COMPRESS_LIMIT) {
      try {
	_log.addLogEntry(new SnapShotLogEntry(contents));
      } catch (StorageException s) {
	// JM do something sensible here
      }
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
  public Email[] getMessages() throws PostException {
    return readContents();
  }

  /**
    * Appends an email to this Folder.
   *
   * @param email The email to insert.
   */
  public void addMessage(Email email) throws PostException {
    try {
      _log.addLogEntry(new InsertMailLogEntry(email));
    } catch (StorageException e) {
      throw new PostException(e.getMessage());
    }
  }

  /**
    * Deletes a message from this Folder.
   *
   * @param email The email to delete.
   */
  public void removeMessage(Email email) throws PostException {
    try {
      _log.addLogEntry(new DeleteMailLogEntry(email)); 
    } catch (StorageException e) {
      throw new PostException(e.getMessage());
    }
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
  public Folder createChildFolder(String name) throws PostException {
    try {
      Log log = new Log(name, _log.getLocation());
      _log.addChildLog(log);
      _log.addLogEntry(new InsertFolderLogEntry(name));
      return new Folder(log, _storage);
    } catch (StorageException e) {
      throw new PostException(e.getMessage());
    }
  }

  /**
   * Returns the selected Folder.  The Folder is selected by its name;
   * if the Folder does not exist an exception is thrown.
   * 
   * @param name the name of the Folder to return
   * @return the selected child Folder
   */
  public Folder getChildFolder(String name) throws PostException {
    try {
      Log log = (Log)_storage.retrieveSigned(_log.getChildLog(name));
      return new Folder(log, _storage);
    } catch (StorageException e) {
      throw new PostException(e.getMessage());
    }
  }

  /**
   * Returns the names of the child Folders of the current Folder.
   *
   * @return an array of the names of the child Folders
   */
  public String[] getChildren() throws PostException {
    return (String[])_log.getChildLogNames();
  }

  /**
   * Deletes a folder from the user's mailbox.
   *
   * @param name The name of the folder to delete.
   */
  public void removeFolder(String name) throws PostException {
    try {
      _log.removeChildLog(name);
      _log.addLogEntry(new DeleteFolderLogEntry(name));
    } catch (StorageException e) {
      throw new PostException(e.getMessage());
    }
  }

  /**
   * Return all the events that happened after the arrival
   * of the given email in the Folder.  
   * JM Problem, does not have access to folder creation/deletion information.
   *
   * @param target the email to act as the signal to stop
   * @return the array of recent events
   */
  public EmailEvent[] getPartialEventLog(Email target) {
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
  }  
  
  
  /**
   * Return all the events that have happened so far in this Folder.
   *
   * @return the complete array of events
   */
  public EmailEvent[] getCompleteEventLog()  {
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
  }  
}
