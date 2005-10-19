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
class EncryptedLogEntry extends LogEntry {

  // the enclosed log entry
  protected transient LogEntry entry;

  // the key used to store this entry
  protected transient byte[] key;

  // the encrypted contained log entry
  protected byte[] cipherEntry;
  
  /**
   * Constructs a LogEntry
   */
  public EncryptedLogEntry(LogEntry entry, byte[] key) {
    this.entry = entry;
    this.key = key;

    initializeEntry();
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
}

