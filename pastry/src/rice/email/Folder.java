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
  // name of the folder
  private String _name;

  // the log of the Folder
  private Log _log;

  // the storage service used by the Folder to fetch log contents
  private StorageService _storage;

  // the nodeID of the client's POST
  //private NodeID _nodeId;
  
  /**
   * Constructs an empty Folder.
   * JM I think that this should be disappeared.
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
    _name = log.getName();
    _log = log;
    _storage = storage;
  }

  /**
   * Used to read the contents of the Folder and build up the array
   * of Emails stored by the Folder.
   */
  private Email[] readContents() throws PostException, StorageException {
    Vector contents = new Vector();
    boolean finished = false;
    LogEntryReference top = _log.getTopEntry();
    LogEntry topEntry = (LogEntry)_storage.retrieveContentHash(top);

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
	topEntry = (LogEntry)_storage.retrieveContentHash(top);
      }
    }
    return (Email[])contents.toArray();
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
  public Email[] getMessages() throws PostException, StorageException {
    return readContents();
  }

  /**
   * Appends an email to this Folder.
   *
   * @param email The email to insert.
   */
  public void addMessage(Email email) throws PostException {
    _log.addLogEntry(new InsertMailLogEntry(email));
  }

  /** 
   * Deletes a message from this Folder.
   *
   * @param email The email to delete.
   */
  public void removeMessage(Email email) throws PostException {
    _log.addLogEntry(new DeleteMailLogEntry(email));
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
  
  public Folder createChildFolder(String name) throws PostException {
    Log log = new Log(name, _log.getLocation());    
    _log.addChildLog(log);
    return new Folder(log, _storage);
  }
  
  public Folder getChildFolder(String name) throws PostException, StorageException {
    Log log = (Log)_storage.retrieveSigned(_log.getChildLog(name));
    return new Folder(log, _storage);
  }
  
  public String[] getChildren() throws PostException {
    return (String[])_log.getChildLogNames();
  }
  
  /**
   * Deletes a folder from the user's mailbox.
   *
   * @param name The name of the folder to delete.
   */
  public void removeFolder(String name) throws PostException {
    _log.removeChildLog(name);
  }
}
