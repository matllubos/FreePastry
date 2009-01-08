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
package rice.post.log;

import java.io.*;
import java.security.*;
import java.util.*;

import rice.*;
import rice.Continuation.*;
import rice.p2p.commonapi.*;
import rice.p2p.util.*;
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
@SuppressWarnings("unchecked")
public class EncryptedLog extends Log {

  private static final long serialVersionUID = 369232753739190225L;

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

  public EncryptedLog(Object name, Id location, Post post, KeyPair keyPair, byte[] cipherKey) {
    super(name, location, post);

    this.cipherKey = cipherKey;
    retrieveKey(keyPair);
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

