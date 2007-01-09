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
package rice.email.log;

import java.security.*;
import java.util.*;

import org.jfree.chart.labels.StandardContourToolTipGenerator;

import rice.*;
import rice.Continuation.*;
import rice.email.*;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.*;
import rice.post.*;
import rice.post.log.*;
import rice.post.storage.*;

/**
 * This represents the head of an email log, representing
 * a folder.
 *
 * @author Alan Mislove
 */
public class EmailLog extends CoalescedLog {

  // the default initial UID
  public static int DEFAULT_UID = 1;
  
  // serial UID, for compatibility
  static final long serialVersionUID = -3520357124395782138L;
  
  // the next available UID for this folder
  private int nextUID;

  // the number of messages currently in this folder
  private int numExist;

  // the number of recent messages in this folder
  private int numRecent;

  // the number of log entries since a snapshot
  private int numEntries;

  // the creation time of this email log
  private long creation; 
  
  // the list of subscription for this log (only used if this is the root)
  private Vector subscriptions;
  
  // the most recent snapshot for this log (null if unsupported)
  private ContentHashReference snapshot;
  
  // the most recent snapshot array for this log (null if unsupported)
  private ContentHashReference[] snapshots;
  
  // the cached version of the most recent snapshot
  private transient SnapShot cachedSnapshot;
  
  // the cached versions of the snapshot array
  private transient SnapShot[] cachedSnapshots;

  /**
   * Constructor for SnapShot.
   *
   * @param name The name of this log
   * @param location The location where this log is stored
   * @param post The current local post service
   */
  public EmailLog(Object name, Id location, Post post, KeyPair pair) {
    super(name, location, post, pair);

    nextUID = DEFAULT_UID;
    creation = post.getEnvironment().getTimeSource().currentTimeMillis();
    numExist = 0;
    numRecent = 0;
    numEntries = 0;
    subscriptions = new Vector();
  }
  
  /**
   * This constructor should only be used for constructing a reconciled log
   * 
   * @param unreconciled
   * @param base
   * @param pair
   */
  protected EmailLog(EmailLog unreconciled, KeyPair pair) {
    super(unreconciled.name,unreconciled.location,unreconciled.post,pair,unreconciled.cipherKey);
    creation = post.getEnvironment().getTimeSource().currentTimeMillis();
    subscriptions = (Vector)unreconciled.subscriptions.clone();
    numRecent = 0;
    numExist = 0; // Folder must fix this up
    nextUID = DEFAULT_UID;
  }
  
  /**
   * Returns the reference to the most recent snapshot
   *
   * @return The most recent log snapshot ref
   */
  public ContentHashReference getSnapshotReference() {
    return snapshot;
  }
  
  /**
   * Returns the reference to the most recent snapshot array
   *
   * @return The most recent log snapshot ref
   */
  public ContentHashReference[] getSnapshotReferences() {
    return snapshots;
  }
    
  /**
   * Returns the list of subscriptions in the log
   *
   * @param command the work to perform after this call.
   * @return The subscriptions
   */
  public void getSubscriptions(Continuation command) {
    if (logger == null) logger = post.getEnvironment().getLogManager().getLogger(EmailLog.class, null);
    if (subscriptions == null) {
      if (logger.level <= Logger.FINER) logger.log("EmailLog: getSubscriptions: subscriptions are null");
      command.receiveResult(new String[0]);
    } else {      
      if (logger.level <= Logger.FINER) {
        logger.log("EmailLog: getSubscriptions: ");
        Iterator i = subscriptions.iterator();
        while (i.hasNext()) {
          logger.log("  subscription "+i.next());
          
        }
        logger.log("EmailLog: all Subscriptions");
      }
      command.receiveResult(subscriptions.toArray(new String[0]));
    }
  }
  
  /**
   * Adds a subscriptions to the log
   *
   * @param command the work to perform after this call.
   * @param sub The subscription to add
   */
  public void addSubscription(String sub, Continuation command) {
    if (subscriptions == null)
      subscriptions = new Vector();
    
    if (! subscriptions.contains(sub))
      subscriptions.add(sub);
    
    sync(command);
  }
  
  /**
   * Adds a subscriptions to the log
   *
   * @param command the work to perform after this call.
   * @param sub The subscription to add
   */
  public void removeSubscription(String sub, Continuation command) {
    if (subscriptions == null)
      subscriptions = new Vector();
    
    if (subscriptions.contains(sub))
      subscriptions.remove(sub);
    
    sync(command);
  }
  
  /**
   * Returns the number elements in the new entry buffer
   *
   * @return The number of pending adds
   */
  public int getBufferSize() {
    return buffer.size();
  }

  /**
   * Returns the number of log entries since a snapshot
   *
   * @return The number of log entries since a snapshot
   */
  public int getEntries() {
    return numEntries;
  }

  /**
   * Increments the number of entries since a snapshot
   */
  public void incrementEntries() {
    numEntries++;
  }

  /**
   * Resets the number of entries since a snapshot
   */
  public void resetEntries() {
    numEntries = 0;
  }
  
  /**
   * Sets the newest snapshot
   *
   * @param snapshot The snapshot
   */
  public void setSnapshot(final SnapShot[] newsnapshots, Continuation command) {
    if (newsnapshots.length > 0) {
      post.getStorageService().storeContentHash(newsnapshots[0], new StandardContinuation(command) {
        int i = 0;
        ContentHashReference[] result = new ContentHashReference[newsnapshots.length];

        public void receiveResult(Object o) {
          result[i++] = (ContentHashReference) o;
          
          if (i == newsnapshots.length) { 
            resetEntries();
            EmailLog.this.snapshot = null;
            EmailLog.this.cachedSnapshot = null;
            EmailLog.this.snapshots = result;
            EmailLog.this.cachedSnapshots = newsnapshots;
            sync(parent);
          } else {
            post.getStorageService().storeContentHash(newsnapshots[i], this);
          }
        }
      });
    } else {
      command.receiveResult(Boolean.TRUE);
    }
  }
  
  /**
   * Returns the most recent snapshot reference
   *
   * @command The command to return the continuation to
   */
  public void getSnapshot(Continuation command) {
    if (cachedSnapshot != null) {
      command.receiveResult(new SnapShot[] {cachedSnapshot});
    } else if (cachedSnapshots != null) {
      command.receiveResult(cachedSnapshots);
    } else if (snapshot != null) {
      post.getStorageService().retrieveContentHash(snapshot, new StandardContinuation(command) {
        public void receiveResult(Object o) {
          cachedSnapshot = (SnapShot) o;
          
          parent.receiveResult(new SnapShot[] {cachedSnapshot});
        }
      });
    } else if (snapshots != null) {
      post.getStorageService().retrieveContentHash(snapshots[0], new StandardContinuation(command) {
        SnapShot[] result = new SnapShot[snapshots.length];
        int i = 0;
        
        public void receiveResult(Object o) {
          result[i++] = (SnapShot) o;
          
          if (i == snapshots.length) {
            cachedSnapshots = result;
            parent.receiveResult(result);
          } else {
            post.getStorageService().retrieveContentHash(snapshots[i], this);
          }
        }

        public void receiveException(Exception e) {
          Logger logger = post.getEnvironment().getLogManager().getLogger(EmailLog.class, null);
          if (logger.level <= Logger.WARNING) logger.logException("WARNING: Received exception " + e + " while reading snapshots - skipping for now.  This is bad.",e);
          receiveResult(new SnapShot(new StoredEmail[0], null));
        }
      });
    } else {
      command.receiveResult(null);
    }
  }
  
  public void dump() {
    if (logger == null) logger = post.getEnvironment().getLogManager().getLogger(CoalescedLog.class, null);
    if (logger.level <= Logger.WARNING) logger.log("BEGIN DUMPING ENTRIES");
    
    Continuation c = new Continuation() {
      
      public void receiveResult(Object result) {
        EmailLogEntry entry = null;
        try {
          entry = (EmailLogEntry) result;
        } catch (ClassCastException cce) {
          if (logger.level <= Logger.WARNING) logger.logException("CCE; actual class "+result.getClass().getName() + "; toString "+result.toString(),cce);
        }
        
        while (entry != null) {
          if (logger.level <= Logger.WARNING) logger.log("entry: "+entry);
          
          if (entry.hasPreviousEntry()) {
            EmailLogEntry tmp = (EmailLogEntry) entry.getCachedPreviousEntry();
            
            if (tmp != null) {
              entry = tmp;
            } else {
              entry.getPreviousEntry(this);
              return;
            }
          } else {
            // break if there isn't a next entry
            break;
          }
        }
        if (logger.level <= Logger.WARNING) logger.log("END DUMPING ENTRIES");
      }

      public void receiveException(Exception result) {
        if (logger.level <= Logger.WARNING) logger.logException("Received exception while dumping log: ",result);
      }
    };
    
    if (pending != null)
      getTopEntry(c);
    else
      getActualTopEntry(c);
    
    getSnapshot(new Continuation() {
      public void receiveResult(Object result) {
        SnapShot[] shots = (SnapShot[]) result;
        if (shots == null) {
          if (logger.level <= Logger.WARNING) logger.log("getSnapshot returned null");
          return;
        }
        if (logger.level <= Logger.WARNING) logger.log("dump: "+shots.length+" snapshots");
        for (int i=0; i< shots.length; i++) {
          if (logger.level <= Logger.WARNING) logger.log("shot "+i+" topEntry: "+ shots[i].getTopEntry());
        }
      }

      public void receiveException(Exception result) {
        if (logger.level <= Logger.WARNING) logger.logException("Received exception while dumping snapshots: ",result);
      }
    });
  }
  
  // dumps self and children
  public void dumpAll() {
    if (logger == null) logger = post.getEnvironment().getLogManager().getLogger(CoalescedLog.class, null);
    if (logger.level <= Logger.WARNING) logger.log("BEGIN DUMPALL for "+getName());
    dump();
    Object[] names = getChildLogNames();
    for (int i = 0; i < names.length; i++) {
      getChildLog(names[i], new Continuation() {
        public void receiveResult(Object result) {
          EmailLog l = (EmailLog)result;
          l.dumpAll();
        }

        public void receiveException(Exception result) {
          if (logger.level <= Logger.WARNING) logger.logException("Received exception while dumping log",result);
        }
      });
    }
  }

  /**
   * Returns the number of messages which exist in this folder
   *
   * @return The number of messages which exists in the folder
   */
  public int getExists() {
    return numExist;
  }
  
  /**
   * Sets the number of messages which exist in this folder
   *
   * @param num The new number of messages
   */
  public void setExists(int num) {
    numExist = num;
  }
  
  /**
   * Increments the number of messages which exist in this folder
   */
  public void incrementExists() {
    numExist++;
  }

  /**
   * Increments the number of messages which exist in this folder
   *
   * @param num The number to increment by
   */
  public void incrementExists(int num) {
    numExist += num;
  }

  /**
   * Decrements the number of messages which exist in this folder
   */
  public void decrementExists() {
    numExist--;
  }
  
  /**
   * Decrements the number of messages which exist in this folder
   *
   * @param num The number to increment by
   */
  public void decrementExists(int num) {
    numExist -= num;
  }

  /**
   * Returns the number of messages which are recent in this folder
   *
   * @return The number of messages which are recent in the folder
   */
  public int getRecent() {
    return numRecent;
  }

  /**
   * Increments the number of messages which exist in this folder
   */
  public void incrementRecent() {
    numRecent++;
  }

  /**
  * Decrements the number of messages which exist in this folder
   */
  public void decrementRecent() {
    numRecent--;
  }

  /**
   * Returns the next UID, and doesn't increment the UID counter.
   *
   * @return The next UID.
   */
  public int peekNextUID() {
    return nextUID;
  }

  /**
   * Returns the next available UID, and increments the UID counter.
   *
   * @return The next UID.
   */
  public int getNextUID() {
    return nextUID++;
  }

  /**
   * Returns the time (in milliseconds) that this email log was created.
   *
   * @return The creation time
   */
  public long getCreationTime() {
    return creation;
  }

  private static void getCommonParent(final EmailLogEntry aTop, final EmailLogEntry bTop, final Collection aEntries, final Collection bEntries, final Continuation command) {
    if (aTop.equals(bTop)) {
      command.receiveResult(aTop);
    } else {
      if (aTop.compareTo(bTop) > 0  || !bTop.hasPreviousEntry()) {
        if (aTop.hasPreviousEntry()) {
          aEntries.add(aTop);
          aTop.getPreviousEntry(new StandardContinuation(command) {
            public void receiveResult(Object result) {
              getCommonParent((EmailLogEntry)result,bTop,aEntries,bEntries,command);
            }
          });
        } else {
          command.receiveResult(null);
        }
      } else {
        bEntries.add(bTop);
        bTop.getPreviousEntry(new StandardContinuation(command) {
          public void receiveResult(Object result) {
            getCommonParent(aTop,(EmailLogEntry)result,aEntries,bEntries,command);
          }
        });
      }
    }
  }

  // I hate these kinds of methods... all this should go in the classes themselves
  private void replayEntry(HashSet seen, List aEntries, List bEntries, EmailLogEntry current, Continuation command) {

    if (current instanceof DeleteMailLogEntry) {
      // for now toss all deletes out. in the future we can process deletes that exist in both branches
    } else if (current instanceof DeleteMailsLogEntry) {
      // for now toss all deletes out. in the future we can process deletes that exist in both branches
    } else if (current instanceof InsertMailLogEntry) {
      InsertMailLogEntry entry = (InsertMailLogEntry)current;
      if (!seen.contains(entry.getStoredEmail().getEmail())) {
        seen.add(entry.getStoredEmail().getEmail());
        addLogEntry(new InsertMailLogEntry(new StoredEmail(entry.getStoredEmail(),getNextUID())),command);
      }
    } else if (current instanceof InsertMailsLogEntry) {
      InsertMailsLogEntry entry = (InsertMailsLogEntry)current;
      ArrayList newEmails = new ArrayList(entry.getStoredEmails().length);
      for (int i=0; i<entry.getStoredEmails().length; i++) {
        if (!seen.contains(entry.getStoredEmails()[i].getEmail())) {
          seen.add(entry.getStoredEmails()[i].getEmail());
          newEmails.add(new StoredEmail(entry.getStoredEmails()[i],getNextUID()));
        }
      }
      addLogEntry(new InsertMailsLogEntry((StoredEmail[])newEmails.toArray(new StoredEmail[0])),command);
    } else if (current instanceof UpdateMailLogEntry) {
      // for now toss updates out
      // we could accept updates on messages we've seen
      // we could even add messages that we see an update to if we haven't seen them
    } else if (current instanceof UpdateMailsLogEntry) {
      // for now toss updates out
    } else if (current instanceof SnapShotLogEntry) {
      // for now toss out snapshots
      // they should be redundant anyway
    }
  }

  private void merge(final HashSet seen, final List aEntries, final List bEntries, Continuation command) {
    Continuation nextIter = new StandardContinuation(command) {
      public void receiveResult(Object result) {
        merge(seen,aEntries,bEntries,parent);
      }
    };
    
    if (!(aEntries.isEmpty() && bEntries.isEmpty())) {
      command.receiveResult(this);
    } else if (aEntries.isEmpty()) {
      EmailLogEntry b = (EmailLogEntry)bEntries.remove(0);
      replayEntry(seen,aEntries,bEntries,b,nextIter);
    } else if (bEntries.isEmpty()) {
      EmailLogEntry a = (EmailLogEntry)aEntries.remove(0);
      replayEntry(seen,aEntries,bEntries,a,nextIter);
    } else {
      EmailLogEntry a = (EmailLogEntry)aEntries.get(0);
      EmailLogEntry b = (EmailLogEntry)bEntries.get(0);
      if (a.equals(b)) {
        aEntries.remove(0);
        bEntries.remove(0);
        replayEntry(seen,aEntries,bEntries,a,nextIter);
      } else if (a.compareTo(b) < 0) {
        aEntries.remove(0);
        replayEntry(seen,aEntries,bEntries,a,nextIter);
      } else {
        bEntries.remove(0);
        replayEntry(seen,aEntries,bEntries,b,nextIter);
      }
    }
  }

  
  // I hate these kinds of methods... all this should go in the classes themselves
  private void copyEntry(EmailLogEntry src, final Continuation command) {

    if (src instanceof DeleteMailLogEntry) {
      DeleteMailLogEntry entry = (DeleteMailLogEntry)src;
      command.receiveResult(new DeleteMailLogEntry(entry.getStoredEmail()));
    } else if (src instanceof DeleteMailsLogEntry) {
      DeleteMailsLogEntry entry = (DeleteMailsLogEntry)src;
      command.receiveResult(new DeleteMailsLogEntry(entry.getStoredEmails()));
    } else if (src instanceof InsertMailLogEntry) {
      InsertMailLogEntry entry = (InsertMailLogEntry)src;
      command.receiveResult(new InsertMailLogEntry(entry.getStoredEmail()));
    } else if (src instanceof InsertMailsLogEntry) {
      InsertMailsLogEntry entry = (InsertMailsLogEntry)src;
      command.receiveResult(new InsertMailsLogEntry(entry.getStoredEmails()));
    } else if (src instanceof UpdateMailLogEntry) {
      UpdateMailLogEntry entry = (UpdateMailLogEntry)src;
      command.receiveResult(new UpdateMailLogEntry(entry.getStoredEmail()));
    } else if (src instanceof UpdateMailsLogEntry) {
      UpdateMailsLogEntry entry = (UpdateMailsLogEntry)src;
      command.receiveResult(new UpdateMailsLogEntry(entry.getStoredEmails()));
    } else if (src instanceof SnapShotLogEntry) {
      final SnapShotLogEntry entry = (SnapShotLogEntry)src;
      getTopEntry(new StandardContinuation(command) {
        public void receiveResult(Object result) {
          command.receiveResult(new SnapShotLogEntry(entry.getStoredEmails(), (LogEntry)result));
        }
      });
    }
  }

  private void copyEntries(EmailLogEntry startingEntry, final Continuation command) {
    if (!startingEntry.hasPreviousEntry()) {
      command.receiveResult(null);
    } else {
      startingEntry.getPreviousEntry(new StandardContinuation(command) {
        public void receiveResult(Object result) {
          final EmailLogEntry ent = (EmailLogEntry)result;
          copyEntries(ent,new StandardContinuation(parent) {
            public void receiveResult(Object result) {
              copyEntry(ent, new StandardContinuation(parent) {
                public void receiveResult(Object result) {
                  int uid = ((EmailLogEntry)result).getMaxUID()+1;
                  if (uid > EmailLog.this.nextUID) {
                    EmailLog.this.nextUID = uid;
                  }
                  addLogEntry((LogEntry)result,command);
                }
              });
            }
          });
        } 
      });
    }
  }
  
  public void reconcile(final EmailLog otherLog, final KeyPair keyPair, final Continuation command) {
    Continuation c = new StandardContinuation(command) {
      
      public void receiveResult(Object result) {
        final EmailLogEntry thisLogEntry = (EmailLogEntry)result;
        
        Continuation d = new StandardContinuation(parent) {
          public void receiveResult(Object result) {
            final EmailLogEntry otherLogEntry = (EmailLogEntry)result;
            
            final List theseEntries = new ArrayList();
            final List otherEntries = new ArrayList();
            final HashSet seen = new HashSet();
            getCommonParent(thisLogEntry, otherLogEntry, theseEntries, otherEntries, 
                new StandardContinuation(parent) {
                  public void receiveResult(Object result) {
                    if (result == null) {
                      // oops, these logs have nothing in common -- should probably be an error
                      parent.receiveException(new Exception("trying to reconcile logs that have no common ancestor"));
                    } else {
                      if (otherEntries.isEmpty()) {
                        parent.receiveResult(this);
                      } else if (theseEntries.isEmpty()) {
                        parent.receiveResult(otherLog);
                      } else {
                        EmailLogEntry base = (EmailLogEntry)result;
                        final EmailLog reconciled = new EmailLog(EmailLog.this,keyPair);
                        reconciled.copyEntries(base, new StandardContinuation(parent) {
                          public void receiveResult(Object result) {
                            reconciled.merge(seen, theseEntries, otherEntries, new StandardContinuation(parent) {
                              public void receiveResult(Object result) {
                                final EmailLog reconciled = (EmailLog)result;
                                reconciled.sync(new StandardContinuation(parent) {
                                  public void receiveResult(Object result) {
                                    // XXX check to make sure sync sunk
                                    command.receiveResult(reconciled);
                                  }
                                });
                              }
                            });
                          }
                        });
                      }
                    }
                  }
                });
          }   
        };
        
        if (otherLog.pending != null)
          otherLog.getTopEntry(d);
        else
          otherLog.getActualTopEntry(d);
      }
    };
    
    if (pending != null)
      getTopEntry(c);
    else
      getActualTopEntry(c);
  }
}
