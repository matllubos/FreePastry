package rice.post.log;

import java.io.*;
import java.security.*;
import java.util.*;

import rice.*;
import rice.Continuation.*;
import rice.p2p.commonapi.*;
import rice.post.*;
import rice.post.storage.*;
import rice.post.security.*;

/**
 * Class which represents an encrypted and coalesced log in the POST system.
 * This class is designed so that applications can simply use this as the
 * log head, instead of the Log class, and the contents of the log will
 * automatically be encrypted and coalesced into the provided number of
 * discrete fragments.
 * 
 * @version $Id$
 */
public class CoalescedLog extends EncryptedLog {
  
  // the number of log entries to coalece into a single entry
  public static int COALESCE_NUM = 50;
  
  // the encrypted set of yet-to-be coalesced log entries
  protected transient byte[] cipherPending;
  
  // the decrypted list of yet-to-be-coalesed log entries
  protected transient CoalescedLogEntry pending;
  
  /**
   * Constructs a Log for use in POST, with the provided number of 
   * coalesced log entries.
   *
   * @param name Some unique identifier for this log
   * @param location The location of this log in PAST
   */
  public CoalescedLog(Object name, Id location, Post post, KeyPair keyPair) {
    super(name, location, post, keyPair);
    
    resetPending();
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
  public void addLogEntry(final LogEntry entry, final Continuation command) {
    pending.appendEntry(entry);
    regenerateCipherPending();
    
    if (pending.getNumEntries() == pending.getEntries().length) {
      final CoalescedLogEntry temp = pending;
      resetPending();
      
      super.addLogEntry(temp, new ErrorContinuation(command) {
        public void receiveException(Exception e) {
          temp.removeEntry(entry);
          pending = temp;
          command.receiveException(e);
        }
      });
    } else {
      sync(new ErrorContinuation(command) {
        public void receiveException(Exception e) {
          pending.removeEntry(entry);
          command.receiveException(e);
        }
      });
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
    if (pending.contains(entry)) 
      command.receiveResult(Boolean.TRUE);
    else
      super.getLogEntryReferences(set, entry, command);
  }
  
  /**
   * This method returns a reference to the most recent entry in the log,
   * which can then be used to walk down the log.
   *
   * @return A reference to the top entry in the log.
   */
  public void getTopEntry(Continuation command) {
    pending.getPreviousEntry(null, command);
  }
  
  /**
   * This method returns the *actual* top entry in the log.
   *
   * @return A reference to the top entry in the log.
   */
  public void getActualTopEntry(Continuation command) {
    super.getTopEntry(command);
  }
  
  /**
   * Method which resets the nuber of entries to be zero.
   */
  protected void resetPending() {
    pending = new CoalescedLogEntry(new LogEntry[COALESCE_NUM]);
    regenerateCipherPending();
    
    pending.setParent(new PhantomLogEntry());
    pending.setPost(post);
  }
  
  /**
   * Method which regenerates the ciphertext of the number of log 
   * entries
   */
  protected void regenerateCipherPending() {
    try {
      byte[] data = SecurityUtils.serialize(pending);
      cipherPending = SecurityUtils.encryptSymmetric(data, key);
    } catch (IOException e) {
      System.out.println("Exception " + e + " thrown while serializing/encrypting pending " + pending);
    }
  }
  
  /**
   * Method which deserializes the encrypted yet-to-be-coalesed log entries
   * once the key has been retrieved.
   */
  protected void retrievePending() {
    if (cipherPending != null) {
      try {
        byte[] data = SecurityUtils.decryptSymmetric(cipherPending, key);
        pending = (CoalescedLogEntry) SecurityUtils.deserialize(data);
        pending.setParent(new PhantomLogEntry());
      } catch (IOException e) {
        System.out.println("Exception " + e + " thrown while deserializing/decrypting pending " + pending);
      } catch (ClassNotFoundException e) {
        System.out.println("Exception " + e + " thrown while deserializing/decrypting pending " + pending);
      }
    } else {
      System.out.println("Found null cipher pending - resetting pending on log " + this);
      resetPending();
    }
  }
  
  /**
   * Method which deserializes the list of yet-to-be-coalesed entries when
   * the coalesced log is read off of the wire.
   *
   * @param keyPair The keypair for this log
   */
  public void setKeyPair(KeyPair keyPair) {
    super.setKeyPair(keyPair);
   
    retrievePending();
  }
  
  /**
   * Internal method for writing out this data object
   *
   * @param oos The current output stream
   */
  private void writeObject(ObjectOutputStream oos) throws IOException {
    oos.defaultWriteObject();
    
    oos.writeInt(cipherPending.length);
    oos.write(cipherPending);
  }
  
  /**
    * Internal method for reading in this data object
   *
   * @param ois The current input stream
   */
  private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
    ois.defaultReadObject();
    
    if (cipherPending == null) {
      cipherPending = new byte[ois.readInt()];
      ois.readFully(cipherPending, 0, cipherPending.length);
    }
  }
  
  /**
    * Internal class for bookkeeping log entries
   */
  protected class PhantomLogEntry extends LogEntry {
    
    /**
     * Constructor which takes the wrapped entry
     */
    public PhantomLogEntry() {
    }
    
    /**
     * Returns the reference to the previous entry in the log
     *
     * @return A reference to the previous log entry
     */
    public LogEntryReference getPreviousEntryReference() {
      return topEntryReference;
    }
    
    /**
     * Returns whether or not this log entry has a previous log entry
     *
     * @return Whether or not this log entry has a previous
     */
    public boolean hasPreviousEntry() {
      return (topEntryReference != null);
    }    
    
    /**
     * Returns the cached previous entry, if it exists and is in memory.
     * Otherwise, it returns null.
     *
     * @return The cached previous entry
     */
    public LogEntry getCachedPreviousEntry() {
      if (topEntry == null)
        return null;
      else
        return ((EncryptedLogEntry) topEntry).entry;
    }
      
    /**
     * Method which redirects the getPreviousEntry back
     * to the Coalesed entry
     *
     * @param command The command to return the result to.
     */
    public void getPreviousEntry(Continuation command) {
      getActualTopEntry(command);
    }
  }

  public String toString() {
    return "CoalescedLog[" + name + "]";
  }
}

