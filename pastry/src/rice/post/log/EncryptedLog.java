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
 * Class which represents an encrypted log in the POST system.  This
 * class is designed so that applications can simply use this as the
 * log head, instead of the Log class, and the contents of the log will
 * automatically be encrypted (using a randomly-generated DES key).
 * 
 * @version $Id$
 */
public class EncryptedLog extends Log {

  // the key which is used to encrypt and decrypt this log's entries
  protected transient byte[] key;

  // the ciphertext of the key encrypted under the public key
  protected transient byte[] cipherKey;
  
  /**
   * Constructs a Log for use in POST
   *
   * @param name Some unique identifier for this log
   * @param location The location of this log in PAST
   */
  public EncryptedLog(Object name, Id location, Post post, KeyPair keyPair) {
    super(name, location, post);

    initializeKey(keyPair);
  }

  /**
   * Method which initializes the key, and constructs the cipherKey variables
   */
  private void initializeKey(KeyPair keyPair) {
    key = SecurityUtils.generateKeySymmetric();

    cipherKey = SecurityUtils.encryptAsymmetric(key, keyPair.getPublic());
  }

  /**
   * Method which retrieves the key from the ciphertext
   */
  private void retrieveKey(KeyPair keyPair) {
    key = SecurityUtils.decryptAsymmetric(cipherKey, keyPair.getPrivate());
  }

  /**
   * Sets the local key pair, which allows this log to begin reading it's log entries
   *
   * @param keyPair The keypair used to decrypt this log's key
   */
  public void setKeyPair(KeyPair keyPair) {
    retrieveKey(keyPair);
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
  public void addLogEntry(LogEntry entry, Continuation command) {
    super.addLogEntry(new EncryptedLogEntry(entry, key), command);
  }

  /**
   * This method returns a reference to the most recent entry in the log,
   * which can then be used to walk down the log.
   *
   * @return A reference to the top entry in the log.
   */
  public void getTopEntry(Continuation command) {
    super.getTopEntry(new StandardContinuation(command) {
      public void receiveResult(Object o) {
        if (o != null) {
          ((EncryptedLogEntry) o).setKey(key);
          parent.receiveResult(((EncryptedLogEntry) o).getEntry());
        } else {
          parent.receiveResult(null);
        }
      }
    });
  }
  
  /**
   * Internal method for writing out this data object
   *
   * @param oos The current output stream
   */
  private void writeObject(ObjectOutputStream oos) throws IOException {
    oos.defaultWriteObject();
    
    oos.writeInt(cipherKey.length);
    oos.write(cipherKey);
  }
  
  /**
    * Internal method for reading in this data object
   *
   * @param ois The current input stream
   */
  private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
    ois.defaultReadObject();
    
    if (cipherKey == null) {
      cipherKey = new byte[ois.readInt()];
      ois.readFully(cipherKey, 0, cipherKey.length);
    }
  }

  public String toString() {
    return "EncryptedLog[" + name + "]";
  }
}

