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
  public static final int COMPRESS_LIMIT = 500;
  
  // the static name of the root folder
  public static String ROOT_FOLDER_NAME = "Root";

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
   * Constructs a Folder from a log and a storage service.
   *
   * @param log the Log which contains the data for the Folder.
   * @param storage the storage service used to get log data from PAST.
   */
  public Folder(EmailLog log, Post post, KeyPair pair) {
    this.keyPair = pair;

    _log = log;
    _post = post;
    _storage = post.getStorageService();
    _children = new Hashtable();
    
    _log.setKeyPair(keyPair);
    _log.setPost(_post);
  }
  
  /**
   * Returns whether or not this folder is the root of the email hierarchy
   *
   * @return Whether or not this folder is the root
   */
  public boolean isRoot() {
    return getName().equals(ROOT_FOLDER_NAME);
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
    if (_log.getName() instanceof String) 
      return (String) _log.getName();
    else 
      return ROOT_FOLDER_NAME;
  }  
  
  /**
   * Changes the name of this folder.  Should ONLY be used for
   * single changes - does NOT change a hierarchy.
   *
   * @param name The new name to use
   * @param command the work to perform after this call.
   */
  public void setName(String name, Continuation command) {
    _log.setName(name, command); 
  }
  
  /**
   * Returns the list of subscriptions in the log
   *
   * @param command the work to perform after this call.
   * @return The subscriptions
   */
  public void getSubscriptions(Continuation command) {
    _log.getSubscriptions(command);
  }
  
  /**
   * Adds a subscriptions to the log
   *
   * @param command the work to perform after this call.
   * @param sub The subscription to add
   */
  public void addSubscription(String sub, Continuation command) {
    _log.addSubscription(sub, command);
  }
  
  /**
   * Adds a subscriptions to the log
   *
   * @param command the work to perform after this call.
   * @param sub The subscription to add
   */
  public void removeSubscription(String sub, Continuation command) {
    _log.removeSubscription(sub, command);
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
   * This method returns a list of all the handles stored in the folder or
   * any subfolders.
   *
   * Returns a PastContentHandle[] containing all of 
   * the handles in to the provided continatuion.
   */
  public void getContentHashReferences(final Set set, Continuation command) {
    getMessageReferences(set, new StandardContinuation(command) {
      public void receiveResult(Object o) {
        getLogReferences(set, new StandardContinuation(parent) {
          public void receiveResult(Object o) {
            getChildReferences(set, new StandardContinuation(parent) {
              public void receiveResult(Object o) {
                parent.receiveResult(Boolean.TRUE);
              }
            });
          }
        });
      }
    });
  }
  
  protected void getMessageReferences(final Set set, Continuation command) {
    getMessages(new StandardContinuation(command) {
      public void receiveResult(Object o) {
        StoredEmail[] emails = (StoredEmail[]) o;
        
        for (int i=0; i<emails.length; i++) 
          emails[i].getEmail().getContentHashReferences(set);
        
        parent.receiveResult(Boolean.TRUE);
      }
    });
  }
   
  protected void getLogReferences(final Set set, Continuation command) {
    if (_log.getTopEntryReference() != null) {
      System.out.println("Adding top ref " + _log.getTopEntryReference());
      set.add(_log.getTopEntryReference());
    }
    
    _log.getTopEntry(new StandardContinuation(command) {
      LogEntry top = null;
      
      public void receiveResult(Object o) {
        if (o == null) {
          parent.receiveResult(Boolean.TRUE);
          return;
        }

        LogEntry entry = (LogEntry) o;
                
        while (entry.hasPreviousEntry()) {
          if ((top != null) && (top.equals(entry))) {
            parent.receiveResult(Boolean.TRUE);
            return;
          }

          if (entry instanceof SnapShotLogEntry) {
            SnapShotLogEntry sEntry = (SnapShotLogEntry) entry;
          
            if (sEntry.getTopEntry() == null) {
              parent.receiveResult(Boolean.TRUE);
              return;
            } else {
              top = sEntry.getTopEntry();
            }
          } 
          
          if (entry.getPreviousEntryReference() != null) {
            System.out.println("Adding next ref " + entry.getPreviousEntryReference());
            set.add(entry.getPreviousEntryReference());
          }

          LogEntry cached = entry.getCachedPreviousEntry();
          if (cached == null)
            break;
          else
            entry = cached;
        }
        
        if (entry.hasPreviousEntry()) 
          entry.getPreviousEntry(this);
        else 
          parent.receiveResult(Boolean.TRUE);
      }
    });
  }
  
  protected void getChildReferences(final Set set, Continuation command) {
    final Object[] names = _log.getChildLogNames();
    
    Continuation children = new StandardContinuation(command) {
      int index = 0;
      
      public void receiveResult(Object o) {
        final Continuation thisOne = this;
        
        if (index < names.length) {
          getChildFolder((String) names[index], new StandardContinuation(parent) {
            public void receiveResult(Object o) {
              index++;
              
              if (o != null) {
                ((Folder) o).getContentHashReferences(set, thisOne);
              } else {
                thisOne.receiveResult(Boolean.TRUE);
              }
            }
          });
        } else {
          parent.receiveResult(Boolean.TRUE);
        }
      }
    };
    
    children.receiveResult(null);
  }
  
  /**
   * This method is periodically invoked by Post in order to get a list of
   * all mutable data which the application is interested in.
   *
   * The applications should return a PostData[] containing all of 
   * the data The application is still interested in to the provided continatuion.
   */
  public void getLogs(final Set set, Continuation command) {
    set.add(_log);
    
    final Object[] names = _log.getChildLogNames();
    
    Continuation children = new StandardContinuation(command) {
      int index = 0;
      
      public void receiveResult(Object o) {
        final Continuation thisOne = this;
        
        if (index < names.length) {
          getChildFolder((String) names[index], new StandardContinuation(parent) {
            public void receiveResult(Object o) {
              index++;

              if (o != null) {
                ((Folder) o).getLogs(set, thisOne);
              } else {
                thisOne.receiveResult(Boolean.TRUE);
              }
            }
          });
        } else {
          parent.receiveResult(Boolean.TRUE);
        }
      }
    };
    
    children.receiveResult(null);
  }
    
  /**
   * Updates an Email (flags)
   *
   * @param email The email to update.
   * @param command the work to perform after this call.
   */
  public void updateMessage(StoredEmail email, Continuation command) {
    _log.incrementEntries();
    _log.addLogEntry(new UpdateMailLogEntry(email), new StandardContinuation(command) {
      public void receiveResult(final Object result) {
        createSnapShot(new StandardContinuation(parent) {
          public void receiveResult(Object o) {
            parent.receiveResult(result);
          }
        });
      }
    });
  }
  
  /**
   * Updates a list of Emails (flags)
   *
   * @param email The emails to update.
   * @param command the work to perform after this call.
   */
  public void updateMessages(StoredEmail[] emails, Continuation command) {
    _log.incrementEntries();
    _log.addLogEntry(new UpdateMailsLogEntry(emails), new StandardContinuation(command) {
      public void receiveResult(final Object result) {
        createSnapShot(new StandardContinuation(parent) {
          public void receiveResult(Object o) {
            parent.receiveResult(result);
          }
        });
      }
    });
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
    addMessage(email, new Flags(), System.currentTimeMillis(), new Continuation() {
      public void receiveResult(Object o) {
        command.receiveResult(o);
      }
      
      public void receiveException(Exception e) {
        _log.decrementRecent();
        command.receiveException(e);
      }
    });
  }  

  /**
   * Appends an email to this Folder with the specified flags set.
   * Creates a new StoredEmail instance with the given email.
   * Sets all flags to false for the new email in the folder
   *
   * @param email The email to insert.
   * @param flags The flags to insert the email with
   * @param internaldate The date to insert the email with
   * @param command the work to perform after this call
   */
  public void addMessage(final Email email, final Flags flags, final long internaldate, final Continuation command) {
    _log.incrementExists();
    _log.incrementEntries();
    email.setStorage(_storage);
    email.storeData(new StandardContinuation(command) {
      public void receiveResult(Object o) {
        StoredEmail storedEmail = new StoredEmail(email, _log.getNextUID(), flags, internaldate);
        _log.addLogEntry(new InsertMailLogEntry(storedEmail), new Continuation() {
          public void receiveResult(final Object result) {
            createSnapShot(new StandardContinuation(parent) {
              public void receiveResult(Object o) {
                command.receiveResult(result);
              }
            });
          }
          
          public void receiveException(Exception e) {
            _log.decrementExists();
            parent.receiveException(e);
          }
        });
      }
    });
  }  
  
  /**
   * Appends an email to this Folder with the specified flags set.
   * Creates a new StoredEmail instance with the given email.
   * Sets all flags to false for the new email in the folder.
   *
   * NOTE: This method assumes that all of the emails have already stored 
   * thier data.
   *
   * @param email The email to insert.
   * @param command the work to perform after this call
   */
  public void addMessages(final Email[] emails, final Flags[] flags, final long[] internaldates, final Continuation command) {
    _log.incrementExists(emails.length);
    _log.incrementEntries();

    StoredEmail[] storedEmails = new StoredEmail[emails.length];
    
    for (int i=0; i<storedEmails.length; i++) 
      storedEmails[i] = new StoredEmail(emails[i], _log.getNextUID(), flags[i], internaldates[i]);
    
    _log.addLogEntry(new InsertMailsLogEntry(storedEmails), new Continuation() {
      public void receiveResult(final Object result) {
        createSnapShot(new StandardContinuation(command) {
          public void receiveResult(Object o) {
            command.receiveResult(result);
          }
        });
      }
      
      public void receiveException(Exception e) {
        _log.decrementExists(emails.length);
        command.receiveException(e);
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
    _log.incrementEntries();
    _log.addLogEntry(new DeleteMailLogEntry(email), new StandardContinuation(command) {
      public void receiveResult(final Object result) {
        createSnapShot(new StandardContinuation(parent) {
          public void receiveResult(Object o) {
            parent.receiveResult(result);
          }
        });
      }
      
      public void receiveException(Exception e) {
        _log.incrementExists();
        parent.receiveException(e);
      }
    });
  }
  
  /**
   * Deletes a list of messages from this Folder.
   *
   * @param emails The emails to delete.
   * @param command the remaining work to carry out
   */
  public void removeMessages(final StoredEmail[] email, Continuation command) {
    _log.decrementExists(email.length);
    _log.incrementEntries();
    _log.addLogEntry(new DeleteMailsLogEntry(email), new StandardContinuation(command) {
      public void receiveResult(final Object result) {
        createSnapShot(new StandardContinuation(parent) {
          public void receiveResult(Object o) {
            parent.receiveResult(result);
          }
        });
      }
      
      public void receiveException(Exception e) {
        _log.incrementExists(email.length);
        parent.receiveException(e);
      }
    });
  }  
  
  /**
   * Creates a new child of the given name.  The current Folder
   * is the parent.
   *
   * @param name the name of the new child Folder
   * @param command the work to perform after this call
   */
  public void createChildFolder(String name, Continuation command) {
    Object[] children = _log.getChildLogNames();
    Arrays.sort(children);
    
    if (Arrays.binarySearch(children, name) >= 0)
      command.receiveException(new IllegalArgumentException("Folder " + name + " already exists."));
    
    final EmailLog newLog = new EmailLog(name, _storage.getRandomNodeId(), _post, keyPair);
    _log.addChildLog(newLog, new StandardContinuation(command) {
      public void receiveResult(Object o) {
        Folder result = new Folder(newLog, _post, keyPair);
        _children.put(result.getName(), result);

        parent.receiveResult(result);
      }
    });
  }
  
  /**
   * Adds an existing folder as a child folder of this folder
   *
   * @param folder The folder to add
   * @param command The command to call once the add is done
   */
  public void addChildFolder(final Folder folder, Continuation command) {
    Object[] children = _log.getChildLogNames();
    Arrays.sort(children);
    
    if (Arrays.binarySearch(children, folder.getName()) >= 0)
      command.receiveException(new IllegalArgumentException("Folder " + folder.getName() + " already exists."));
    
    _log.addChildLog(folder._log, new StandardContinuation(command) {
      public void receiveResult(Object o) {
        _children.put(folder.getName(), folder);
        parent.receiveResult(new Boolean(true));
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
   * Creates and inserts a snapshot for the current folder
   *
   * @param command The command to run once the result is received
   */
  public void createSnapShot(Continuation command) {
    final int entries = _log.getEntries();

    if (entries >= COMPRESS_LIMIT) {
      _log.getTopEntry(new StandardContinuation(command) {
        public void receiveResult(Object o) {
          final LogEntry entry = (LogEntry) o;
      
          getMessages(new StandardContinuation(parent) {
            public void receiveResult(Object o) {
              if (_log.getEntries() == entries) {
                _log.resetEntries();
                _log.addLogEntry(new SnapShotLogEntry((StoredEmail[]) o, entry), parent);
              } else {
                System.out.println("INFO: Was unable to create snapshot - other log entry in progress.  Not bad, but a little unexpected.");
                parent.receiveResult(new Boolean(true));
              }
            }
          });
        }
      });
    } else {
      command.receiveResult(new Boolean(true));
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
      private LogEntry top = null;
      
      protected void insert(StoredEmail email) {
        Integer uid = new Integer(email.getUID());
        
        if ((! seen.contains(uid)) && (! deleted.contains(uid))) {
          email.getEmail().setStorage(_storage);
          seen.add(uid);
          emails.add(email);
        }
      }
      
      protected void delete(StoredEmail email) {
        deleted.add(new Integer(email.getUID()));
      }

      public void receiveResult(Object o) {
        EmailLogEntry entry = (EmailLogEntry) o;
        boolean finished = false;
        
        if (entry != null) {
          if ((top != null) && (entry.equals(top))) {
            finished = true;
          } else if (entry instanceof InsertMailLogEntry) {
            insert(((InsertMailLogEntry) entry).getStoredEmail());
          } else if (entry instanceof InsertMailsLogEntry) {
            StoredEmail[] inserts = ((InsertMailsLogEntry) entry).getStoredEmails();
            
            for (int i=0; i<inserts.length; i++) 
              insert(inserts[i]);
          } else if (entry instanceof DeleteMailLogEntry) {
            delete(((DeleteMailLogEntry) entry).getStoredEmail());
          } else if (entry instanceof DeleteMailsLogEntry) {
            StoredEmail[] deletes = ((DeleteMailsLogEntry) entry).getStoredEmails();
            
            for (int i=0; i<deletes.length; i++) 
              delete(deletes[i]);
          } else if (entry instanceof UpdateMailLogEntry) {
            insert(((UpdateMailLogEntry) entry).getStoredEmail());
          } else if (entry instanceof UpdateMailsLogEntry) {
            StoredEmail[] updates = ((UpdateMailsLogEntry) entry).getStoredEmails();
            
            for (int i=0; i<updates.length; i++) 
              insert(updates[i]);            
          } else if (entry instanceof SnapShotLogEntry) {
            System.out.println("FOUND SNAPSHOT - TOP ENTRY IS " + ((SnapShotLogEntry) entry).getTopEntry());
            StoredEmail[] rest = ((SnapShotLogEntry) entry).getStoredEmails();

            for (int i = 0; i < rest.length; i++)
              insert(rest[i]);

            if (top == null)
              top = ((SnapShotLogEntry) entry).getTopEntry();
            
            if (top == null)
              finished = true;
          }
        } else {
          finished = true;
        }

        if (finished) {
          // now, sort the list (by UID)
          Collections.sort(emails);
          StoredEmail[] result = (StoredEmail[]) emails.toArray(new StoredEmail[0]);
          System.out.println("SETTING EXISTS TO BE " + result.length + " TOP " + top + " FINISHED " + finished);

          if (_log.getBufferSize() == 0) 
            _log.setExists(result.length);
          
          parent.receiveResult(result);
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





