package rice.email;

import java.security.*;
import java.util.*;

import rice.*;
import rice.Continuation.*;
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

  // the keypair used to encrypt the log
  private KeyPair keyPair;

   
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
  public Folder(EmailLog log, Post post, KeyPair pair) {
    if (log.getName() instanceof String) {
      _name = (String) log.getName();
    } else {
      _name = "Root";
    }

    this.keyPair = pair;

    _log = log;
    _post = post;
    _storage = post.getStorageService();
    _children = new Hashtable();
    
    _log.setKeyPair(keyPair);
    _log.setPost(_post);
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
   * Returns the name of this folder
   *
   * @return The name of the folder
   */
  public String getName() {
    return _name;
  }

  /**
   * Returns the next UID that will be assigned to an incoming message.
   *
   * @return The next UID that will be assigned.
   */
  public int getNextUID() {
    return _log.peekNextUID();
  }

  /**
   * Returns the number of messages which exist in this folder
   *
   * @return The number of messages which exists in the folder
   */
  public int getExists() {
    return _log.getExists();
  }

  /**
   * Returns the number of messages which are recent in this folder
   *
   * @return The number of messages which are recent in the folder
   */
  public int getRecent() {
    return _log.getRecent();
  }

  /**
   * Returns the time (in milliseconds) that this email log was created.
   *
   * @return The creation time
   */
  public long getCreationTime() {
    return _log.getCreationTime();
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
   * Appends an email to this Folder, with default (no) flags set.
   * Creates a new StoredEmail instance with the given email.
   * Sets all flags to false for the new email in the folder
   *
   * @param email The email to insert.
   * @param command the work to perform after this call
   */
  public void addMessage(final Email email, final Continuation command) {
    _log.incrementRecent();
    addMessage(email, new Flags(), command);
  }  

  /**
   * Appends an email to this Folder with the specified flags set.
   * Creates a new StoredEmail instance with the given email.
   * Sets all flags to false for the new email in the folder
   *
   * @param email The email to insert.
   * @param command the work to perform after this call
   */
  public void addMessage(final Email email, final Flags flags, final Continuation command) {
    _log.incrementExists();
    email.setStorage(_storage);
    email.storeData(new StandardContinuation(command) {
      public void receiveResult(Object o) {
        StoredEmail storedEmail = new StoredEmail(email, _log.getNextUID(), flags);
        _log.addLogEntry(new InsertMailLogEntry(storedEmail), command);
      }
    });
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
  public void moveMessage(final StoredEmail email, final Folder folder, Continuation command) {
    removeMessage(email, new StandardContinuation(command) {
      public void receiveResult(Object o) {
        folder.addMessage(email.getEmail(), parent);
      }
    });
  }

  /**
   * Deletes a message from this Folder.
   *
   * @param email The email to delete.
   * @param command the remaining work to carry out
   */
  public void removeMessage(StoredEmail email, Continuation command) {
    _log.decrementExists();
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
    final EmailLog newLog = new EmailLog(name, _storage.getRandomNodeId(), _post, keyPair);
    _log.addChildLog(newLog, new StandardContinuation(command) {
      public void receiveResult(Object o) {
        Folder result = new Folder(newLog, _post, keyPair);
        _children.put(_name, result);

        parent.receiveResult(result);
      }
    });
  }

  /**
   * Deletes a folder from the user's mailbox.
   *
   * @param name The name of the folder to delete.
   * @param command the work to perform after this call
   */
  public void removeFolder(String name, Continuation command) {
    _children.remove(name);
    _log.removeChildLog(name, command);
  }

  /**
   * Returns the selected Folder.  The Folder is selected by its name;
   * if the Folder does not exist an exception is thrown.
   *
   * @param name the name of the Folder to return
   * @param command the work to perform after this call
   */
  public void getChildFolder(final String name, Continuation command) {
    if (_children.get(name) == null) {
      _log.getChildLog(name, new StandardContinuation(command) {
        public void receiveResult(Object o) {
          if (o == null) {
            parent.receiveResult(null);
            return;
          }

          Folder result = new Folder((EmailLog) o, _post, keyPair);
          _children.put(name, result);
          parent.receiveResult(result);
        }
      });
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
   * Returns the Emails contained in this Folder.
   *
   * @param command the work to perform after this call
   * @return the stored Emails
   */
  public void getMessages(Continuation command) {
    _log.getTopEntry(new StandardContinuation(command) {
      private Vector emails = new Vector();
      private HashSet seen = new HashSet();
      private HashSet deleted = new HashSet();

      public void receiveResult(Object o) {
        LogEntry entry = (LogEntry) o;
        boolean finished = false;
        
        if (entry != null) {
          if (entry instanceof InsertMailLogEntry) {
            InsertMailLogEntry ientry = (InsertMailLogEntry) entry;
            Integer uid = new Integer(ientry.getStoredEmail().getUID());

            if ((! seen.contains(uid)) && (! deleted.contains(uid))) {
              ientry.getStoredEmail().getEmail().setStorage(_storage);
              seen.add(uid);
              emails.add(ientry.getStoredEmail());
            }
          } else if (entry instanceof DeleteMailLogEntry) {
            DeleteMailLogEntry dentry = (DeleteMailLogEntry) entry;
            Integer uid = new Integer(dentry.getStoredEmail().getUID());

            deleted.add(uid);
          } else if (entry instanceof UpdateMailLogEntry) {
            UpdateMailLogEntry uentry = (UpdateMailLogEntry) entry;
            Integer uid = new Integer(uentry.getStoredEmail().getUID());

            if ((! seen.contains(uid)) && (! deleted.contains(uid))) {
              uentry.getStoredEmail().getEmail().setStorage(_storage);
              seen.add(uid);
              emails.add(uentry.getStoredEmail());
            }
          } else if (entry instanceof SnapShotLogEntry) {
            StoredEmail[] rest = ((SnapShotLogEntry) entry).getStoredEmails();

            for (int i = 0; i < rest.length; i++) {
              Integer uid = new Integer(rest[i].getUID());
              
              if ((! seen.contains(uid)) && (! deleted.contains(uid))) {
                rest[i].getEmail().setStorage(_storage);
                emails.add(rest[i]);
              }
            }

            finished = true;
          }
        } else {
          finished = true;
        }

        if (finished) {
          // now, sort the list (by UID)
          Collections.sort(emails);
          parent.receiveResult(emails.toArray(new StoredEmail[0]));
        } else {
          entry.getPreviousEntry(this);
        }
      }
    });
  }

  public String toString() {
    return getName();
  }
 }





