package rice.post.log;

import java.io.*;
import java.security.*;

import rice.*;
import rice.p2p.commonapi.*;
import rice.post.*;
import rice.post.storage.*;
import rice.post.security.*;

/**
 * Class for all entries in the encrypted log.  Applications should *NOT* use
 * or extend this class, as it is internal to the log package.
 * 
 * @version $Id$
 */
final class EncryptedLogEntry extends LogEntry {

  // the enclosed log entry
  protected transient LogEntry entry;

  // the key used to store this entry
  protected transient byte[] key;

  // the encrypted contained log entry
  protected transient byte[] cipherEntry;
  
  /**
   * Constructs a LogEntry
   */
  public EncryptedLogEntry(LogEntry entry, byte[] key) {
    this.entry = entry;
    this.key = key;

    initializeEntry();
  }
  
  /**
   * Returns whether or not this coaleseced log entry contains
   * the provided entry
   *
   * @param entry The entry to search for
   * @return Whetehr or not this entry contains it
   */
  protected boolean contains(LogEntry entry) {
    if ((entry == null) || (this.entry == null))
      return false;
        
    return this.entry.equals(entry);
  }

  /**
   * Method which initializes the key, and constructs the cipherKey variables
   */
  private void initializeEntry() {
    try {
      entry.setParent(this);
      
      byte[] data = SecurityUtils.serialize(entry);
      cipherEntry = SecurityUtils.encryptSymmetric(data, key);
    } catch (IOException e) {
      System.out.println("Exception " + e + " thrown while serializing/encrypting entry " + entry);
    }
  }

  /**
   * Method which retrieves the key from the ciphertext
   */
  private void retrieveEntry() {
    try {
      byte[] data = SecurityUtils.decryptSymmetric(cipherEntry, key);
      entry = (LogEntry) SecurityUtils.deserialize(data);
      entry.setParent(this);
    } catch (IOException e) {
      System.out.println("Exception " + e + " thrown while deserializing/decrypting entry " + entry);
    } catch (ClassNotFoundException e) {
      System.out.println("Exception " + e + " thrown while deserializing/decrypting entry " + entry);
    }
  }
  
  /**
   * Sets the local key, which allows this log entry to decrypt
   *
   * @param key The key used to decrypt this log entry
   */
  public void setKey(byte[] key) {
    if (entry == null) {
      this.key = key;
      retrieveEntry();
    }
  }

  /**
   * Returns the enclosed entry
   *
   * @return The enclosed entry
   */
  public LogEntry getEntry() {
    return entry;
  }

  /**
   * Returns the reference to the previous entry in the log
   *
   * @return A reference to the previous log entry
   */
  public void getPreviousEntry(final Continuation command) {
    Continuation decrypt = new Continuation() {
      public void receiveResult(Object o) {
        if (o != null) {
          EncryptedLogEntry entry = (EncryptedLogEntry) o;
          entry.setKey(key);

          command.receiveResult(entry.getEntry());
        } else {
          command.receiveResult(null);
        }
      }

      public void receiveException(Exception e) {
        command.receiveException(e);
      }
    };

    super.getPreviousEntry(decrypt);
  } 
  
  /**
   * Returns the cached previous entry, if it exists and is in memory.
   * Otherwise, it returns null.
   *
   * @return The cached previous entry
   */
  public LogEntry getCachedPreviousEntry() {
    LogEntry entry = super.getCachedPreviousEntry();
    
    if (entry != null) {
      EncryptedLogEntry eEntry = (EncryptedLogEntry) entry;
      return eEntry.entry;
    } else {
      return null;
    }
  }
  
  /**
   * Internal method for writing out this data object
   *
   * @param oos The current output stream
   */
  private void writeObject(ObjectOutputStream oos) throws IOException {
    oos.defaultWriteObject();
    
    oos.writeInt(cipherEntry.length);
    oos.write(cipherEntry);
  }
  
  /**
   * Internal method for reading in this data object
   *
   * @param ois The current input stream
   */
  private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
    ois.defaultReadObject();
    
    if (cipherEntry == null) {
      cipherEntry = new byte[ois.readInt()];
      ois.readFully(cipherEntry, 0, cipherEntry.length);
    }
  }
}

