package rice.post.storage;

import java.security.*;
import java.security.spec.*;
import java.util.*;
import java.math.*;
import java.io.*;
import javax.crypto.*;
import javax.crypto.spec.*;

import rice.*;
import rice.p2p.past.*;
import rice.post.*;
import rice.post.security.*;
import rice.pastry.*;
import rice.pastry.multiring.*;
import rice.pastry.standard.*;
import rice.pastry.security.*;

/**
 * This class represents a service which stores data in PAST.  This
 * class supports two types of data: content-hash blocks and private-key
 * signed blocks.  This class will automatically format and store data,
 * as well as retrieve and verify the stored data.
 * 
 * @version $Id$
 */
public class StorageService {

  /**
   * The address of the user running this storage service.
   */
  private PostEntityAddress entity;
  
  /**
   * The PAST service used for distributed persistant storage.
   */
  private Past past;
  
  /**
   * Security service to handle all encryption tasks.
   */
  private SecurityService security;

  /**
   * The credentials used to store data.
   */
  private Credentials credentials;

  /**
   * Stored data waiting for verification
   */
  private Hashtable pendingVerification;

  /**
   * Generates random node ids
   */
  private RandomNodeIdFactory factory;
  
  /**
   * Contructs a StorageService given a PAST to run on top of.
   *
   * @param past The PAST service to use.
   * @param credentials Credentials to use to store data.
   * @param security SecurityService to handle all security related tasks
   */
  public StorageService(PostEntityAddress address, Past past, Credentials credentials, SecurityService security) {
    this.entity = address;
    this.past = past;
    this.credentials = credentials;
    this.security = security;

    if (entity.getAddress() instanceof RingNodeId) {
      factory = new RandomRingNodeIdFactory(((RingNodeId) entity.getAddress()).getRingId());
    } else {
      factory = new RandomNodeIdFactory();
    }
    
    pendingVerification = new Hashtable();
  }

  public Id getRandomNodeId() {
    return factory.generateNodeId();
  }

  /**
   * Stores a PostData in the PAST storage system, in encrypted state,
   * and returns a pointer and key to the data object.
   *
   * This first encrypts the PostData using it's hash value as the
   * key, and then stores the ciphertext at the value of the hash of
   * the ciphertext.
   *
   * Once the data has been stored, the command.receiveResult() method
   * will be called with a ContentHashReference as the argument.
   *
   * @param data The data to store.
   * @param command The command to run once the store has completed.
   */
  public void storeContentHash(PostData data, Continuation command) {
    StoreContentHashTask task = new StoreContentHashTask(data, command);
    task.start();
  }

  /**
   * This method retrieves a given PostDataReference object from the
   * network. This method also performs the verification checks and
   * decryption necessary.
   *
   * Once the data has been stored, the command.receiveResult() method
   * will be called with a PostData as the argument.
   *
   * @param reference The reference to the PostDataObject
   * @param command The command to run once the store has completed.
   */
  public void retrieveContentHash(ContentHashReference reference, Continuation command) {
    RetrieveContentHashTask task = new RetrieveContentHashTask(reference, command);
    task.start();
  }


  /**
    * Stores a PostData in the PAST store by signing the content and
   * storing it at a well-known location. This method also includes
   * a timestamp, which dates this update.
   *
   * Once the data has been stored, the command.receiveResult() method
   * will be called with a SignedReference as the argument.
   *
   * @param data The data to store
   * @param location The location where to store the data
   * @param command The command to run once the store has completed.
   */
  public void storeSigned(PostData data, Id location, Continuation command) {
    StoreSignedTask task = new StoreSignedTask(data, location, command);
    task.start();
  }

  /**
   * This method retrieves a previously-stored private-key signed
   * block from PAST.  This method also does all necessary verification
   * checks and fetches the content from multiple locations in order
   * to prevent version-rollback attacks.
   *
   * Once the data has been stored, the command.receiveResult() method
   * will be called with a PostData as the argument.
   *
   * @param location The location of the data
   * @param command The command to run once the store has completed.
   */
  public void retrieveAndVerifySigned(SignedReference reference, Continuation command) {
    retrieveAndVerifySigned(reference, security.getPublicKey(), command);
  }

  /**
   * This method retrieves a previously-stored block from PAST which was
   * signed using the private key matching the given public key.
   * This method also does all necessary verification
   * checks and fetches the content from multiple locations in order
   * to prevent version-rollback attacks.
   *
   * Once the data has been stored, the command.receiveResult() method
   * will be called with a PostData as the argument.
   *
   * @param location The location of the data
   * @param publicKey The public key matching the private key used to sign the data
   * @param command The command to run once the store has completed.
   */
  public void retrieveAndVerifySigned(SignedReference reference, PublicKey publicKey, Continuation command) {
    RetrieveAndVerifySignedTask task = new RetrieveAndVerifySignedTask(reference, publicKey, command);
    task.start();
  }    

  /**
   * This method retrieves a previously-stored block from PAST which was
   * signed using the private key. THIS METHOD EXPLICITLY DOES NOT PERFORM
   * ANY VERIFICATION CHECKS ON THE DATA.  YOU MUST CALL verifySigned() IN
   * ORDER TO VERIFY THE DATA.  This is provided for the case where the
   * cooresponding key is located in the data.
   *
   * Once the data has been stored, the command.receiveResult() method
   * will be called with a PostData as the argument.
   *
   * @param location The location of the data
   * @param command The command to run once the store has completed.
   */
  public void retrieveSigned(SignedReference reference, Continuation command) {
    RetrieveSignedTask task = new RetrieveSignedTask(reference, command);
    task.start();
  }

  /**
   * This method verifies a signed block of data with the given public key.
   *
   * @param location The location of the data
   * @return The data
   */
  public boolean verifySigned(PostData data, PublicKey key) {
    SignedData sd = (SignedData) pendingVerification.remove(data);

    // Verify signature
    if ((sd == null) || (! security.verify(sd.getDataAndTimestamp(), sd.getSignature(), key))) {
      System.out.println("Verification failed of signed block:");
      printArray(sd.getData());
      printArray(sd.getTimestamp());
      return false;
    }

    return true;
  }

  /**
   * Stores a PostData in the PAST storage system, in encrypted state,
   * and returns a pointer and key to the data object.
   *
   * This method first generates a random key, uses this key to encrypt
   * the data, and then stored the data under the key of it's content-hash.
   *
   * Once the data has been stored, the command.receiveResult() method
   * will be called with a SecureReference as the argument.
   *
   * @param data The data to store.
   * @param command The command to run once the store has completed.
   */
  public void storeSecure(PostData data, Continuation command) {
    StoreSecureTask task = new StoreSecureTask(data, command);
    task.start();
  }

  /**
   * This method retrieves a given SecureReference object from the
   * network. This method also performs the verification checks and
   * decryption necessary.
   *
   * Once the data has been stored, the command.receiveResult() method
   * will be called with a PostData as the argument.
   *
   * @param reference The reference to the PostDataObject
   * @param command The command to run once the store has completed.
   */
  public void retrieveSecure(SecureReference reference, Continuation command) {
    RetrieveSecureTask task = new RetrieveSecureTask(reference, command);
    task.start();
  }

  private void printArray(byte[] array) {
    for (int i=0; i<array.length; i++) {
      System.out.print(Byte.toString(array[i]));
    }

    System.out.println();
  }
  
  /* ----- TASK CLASSES ----- */
  
  /**
   * Class which is reposible for handling a single StoreContentHash
   * task.
   */
  protected class StoreContentHashTask implements Continuation {

    private PostData data;
    private Continuation command;
    private Id location;
    private Key key;
    
    /**
     * This contructs creates a task to store a given data and call the
     * given command when the data has been stored.
     *
     * @param data The data to store
     * @param command The command to run once the data has been stored
     */
    protected StoreContentHashTask(PostData data, Continuation command) {
      this.data = data;
      this.command = command;
    }

    /**
     * Starts this task running.
     */
    protected void start() {
      try {
        byte[] plainText = security.serialize(data);
        byte[] hash = security.hash(plainText);
        byte[] cipherText = security.encryptDES(plainText, hash);
        byte[] loc = security.hash(cipherText);

        if (entity.getAddress() instanceof RingNodeId) {
          location = new RingNodeId(new NodeId(loc), ((RingNodeId) entity.getAddress()).getRingId());
        } else {
          location = new Id(loc);
        }
        
        key = new SecretKeySpec(hash, "DES");

        ContentHashData chd = new ContentHashData(location, cipherText);

        // Store the content hash data in PAST
        past.insert(chd, this);

        // Now we wait until PAST calls us with the receiveResult
        // and then we return the address
      } catch (IOException e) {
        command.receiveException(e);
      }
    }

    /**
     * Called when a previously requested result is now availble.
     *
     * @param result The result of the command.
     */
    public void receiveResult(Object result) {
      if (! (result instanceof Boolean[])) {
        command.receiveException(new IOException("Storage of signed data into Past returned unknown data " + result));
      } else {
        Boolean[] results = (Boolean[]) result;
        boolean error = false;

        for (int i=0; i<results.length; i++) {
          error = error || (results[i] == null) || (! results[i].booleanValue());
        }

        if (! error) {
          command.receiveResult(data.buildContentHashReference(location, key));
        } else {
          command.receiveException(new IOException("Storage of signed data into PAST failed - replicas did not store object."));
        }
      }
    }

    /**
     * Called when a previously requested result causes an exception
     *
     * @param result The exception caused
     */
    public void receiveException(Exception result) {
      command.receiveException(result);
    }
  }

  /**
   * Class which is reposible for handling a single StoreContentHash
   * task.
   */
  protected class RetrieveContentHashTask implements Continuation {

    private ContentHashReference reference;
    private Continuation command;

    /**
     * This contructs creates a task to store a given data and call the
     * given command when the data has been stored.
     *
     * @param reference The reference to retrieve
     * @param command The command to run once the data has been referenced
     */
    protected RetrieveContentHashTask(ContentHashReference reference, Continuation command) {
      this.reference = reference;
      this.command = command;
    }

    /**
      * Starts this task running.
     */
    protected void start() {
      past.lookup(reference.getLocation(), this);

      // Now we wait until PAST calls us with the receiveResult
      // and then we continue processing this call
    }

    /**
      * Called when a previously requested result is now availble.
     *
     * @param result The result of the command.
     */
    public void receiveResult(Object result) {
      try {
        ContentHashData chd = (ContentHashData) result;

        if (chd == null) {
          command.receiveResult(null);
          return;
        }

        // TO DO: fetch from multiple locations to prevent rollback attacks
        byte[] keyBytes = reference.getKey().getEncoded();

        byte[] cipherText = chd.getData();
        byte[] plainText = security.decryptDES(cipherText, keyBytes);
        Object data = security.deserialize(plainText);

        // Verify hash(cipher) == location
        byte[] hashCipher = security.hash(cipherText);
        byte[] loc = reference.getLocation().copy();
        if (! Arrays.equals(hashCipher, loc)) {
          command.receiveException(new StorageException("Hash of cipher text does not match location."));
          return;
        }

        // Verify hash(plain) == key
        byte[] hashPlain = security.hash(plainText);
        if (! Arrays.equals(hashPlain, keyBytes)) {
          command.receiveException(new StorageException("Hash of retrieved content does not match key."));
          return;
        }

        command.receiveResult((PostData) data);
      }
      catch (ClassCastException cce) {
        command.receiveException(new StorageException("ClassCastException while retrieving data: " + cce));
      }
      catch (IOException ioe) {
        command.receiveException(new StorageException("IOException while retrieving data: " + ioe));
      }
      catch (ClassNotFoundException cnfe) {
        command.receiveException(new StorageException("ClassNotFoundException while retrieving data: " + cnfe));
      }
    }

    /**
     * Called when a previously requested result causes an exception
     *
     * @param result The exception caused
     */
    public void receiveException(Exception result) {
      command.receiveException(result);
    }
  }

  /**
   * Class which is reposible for handling a single StoreSigned
   * task.
   */
  protected class StoreSignedTask implements Continuation {

    public static final int STATE_1 = 1;
    public static final int STATE_2 = 2;
    
    private PostData data;
    private Continuation command;
    private Id location;
    private Key key;

    /**
     * This contructs creates a task to store a given data and call the
     * given command when the data has been stored.
     *
     * @param data The data to store
     * @param command The command to run once the data has been stored
     */
    protected StoreSignedTask(PostData data, Id location, Continuation command) {
      this.data = data;
      this.location = location;
      this.command = command;
    }

    /**
      * Starts this task running.
     */
    protected void start() {
      try {
        byte[] plainText = security.serialize(data);
        byte[] timestamp = security.getByteArray(System.currentTimeMillis());

        SignedData sd = new SignedData(location, plainText, timestamp);

        sd.setSignature(security.sign(sd.getDataAndTimestamp()));

        // Store the signed data in PAST 
        past.insert(sd, this);

        // Now we wait to make sure that the update or insert worked, and
        // then return the reference.
      } catch (IOException e) {
        command.receiveException(e);
      }
    }

    /**
     * Called when a previously requested result is now availble.
     *
     * @param result The result of the command.
     */
    public void receiveResult(Object result) {
      if (! (result instanceof Boolean[])) {
        command.receiveException(new IOException("Storage of signed data into Past returned unknown data " + result));
      } else {
        Boolean[] results = (Boolean[]) result;
        boolean error = false;

        for (int i=0; i<results.length; i++) {
          error = error || (results[i] == null) || (! results[i].booleanValue());
        }

        if (! error) {
          command.receiveResult(data.buildSignedReference(location));
        } else {
          command.receiveException(new IOException("Storage of signed data into PAST failed - replicas did not store object."));
        }
      }
    }
      
    /**
     * Called when a previously requested result causes an exception
     *
     * @param result The exception caused
     */
    public void receiveException(Exception result) {
      command.receiveException(result);
    }
  }

  /**
   * Class which is reposible for handling a single RetrieveSigned
   * task.
   */
  protected class RetrieveSignedTask implements Continuation {

    private SignedReference reference;
    private Continuation command;

    /**
     * This contructs creates a task to store a given data and call the
     * given command when the data has been stored.
     *
     * @param reference The reference to retrieve
     * @param command The command to run once the data has been referenced
     */
    protected RetrieveSignedTask(SignedReference reference, Continuation command) {
      this.reference = reference;
      this.command = command;
    }

    /**
      * Starts this task running.
     */
    protected void start() {
      past.lookup(reference.getLocation(), this);

      // Now we wait until PAST calls us with the receiveResult
      // and then we continue processing this call
    }

    /**
      * Called when a previously requested result is now availble.
     *
     * @param result The result of the command.
     */
    public void receiveResult(Object result) {
      Object data = null;
      
      try {
        SignedData sd = (SignedData) result;

        if (sd == null) {
          command.receiveResult(null);
          return;
        }
      
        byte[] plainText = sd.getData();
        data = security.deserialize(plainText);

        pendingVerification.put(data, sd);

        command.receiveResult((PostData) data);
      } catch (IOException ioe) {
        command.receiveException(new StorageException("IOException while retrieving data: " + ioe));
      } catch (ClassNotFoundException cnfe) {
        command.receiveException(new StorageException("ClassNotFoundException while retrieving data: " + cnfe));
      }
    }

    /**
      * Called when a previously requested result causes an exception
     *
     * @param result The exception caused
     */
    public void receiveException(Exception result) {
      command.receiveException(result);
    }
  }

  /**
   * Class which is reposible for handling a single RetrieveAndVerifySigned
   * task.
   */
  protected class RetrieveAndVerifySignedTask implements Continuation {

    private SignedReference reference;
    private PublicKey key;
    private Continuation command;

    /**
      * This contructs creates a task to store a given data and call the
     * given command when the data has been stored.
     *
     * @param reference The reference to retrieve
     * @param key The key to verify against
     * @param command The command to run once the data has been referenced
     */
    protected RetrieveAndVerifySignedTask(SignedReference reference, PublicKey key, Continuation command) {
      this.reference = reference;
      this.key = key;
      this.command = command;
    }

    /**
      * Starts this task running.
     */
    protected void start() {
      retrieveSigned(reference, this);

      // Now we wait until PAST calls us with the receiveResult
      // and then we continue processing this call
    }

    /**
      * Called when a previously requested result is now availble.
     *
     * @param result The result of the command.
     */
    public void receiveResult(Object result) {
      if (verifySigned((PostData) result, key)) {
        command.receiveResult(result);
      } else {
        command.receiveException(new SecurityException("Verification of SignedData failed."));
      }
    }

    /**
      * Called when a previously requested result causes an exception
     *
     * @param result The exception caused
     */
    public void receiveException(Exception result) {
      command.receiveException(result);
    }
  }

  /**
    * Class which is reposible for handling a single StoreSecure
   * task.
   */
  protected class StoreSecureTask implements Continuation {

    private PostData data;
    private Continuation command;
    private Id location;
    private Key key;

    /**
      * This contructs creates a task to store a given data and call the
     * given command when the data has been stored.
     *
     * @param data The data to store
     * @param command The command to run once the data has been stored
     */
    protected StoreSecureTask(PostData data, Continuation command) {
      this.data = data;
      this.command = command;
    }

    /**
      * Starts this task running.
     */
    protected void start() {
      try {
        byte[] plainText = security.serialize(data);

        byte[] keyByte = security.generateKeyDES();

        byte[] cipherText = security.encryptDES(plainText, keyByte);
        byte[] loc = security.hash(cipherText);

        if (entity.getAddress() instanceof RingNodeId) {
          location = new RingNodeId(new NodeId(loc), ((RingNodeId) entity.getAddress()).getRingId());
        } else {
          location = new Id(loc);
        }
        
        key = new SecretKeySpec(keyByte, "DES");

        SecureData sd = new SecureData(location, cipherText);

        // Store the content hash data in PAST
        past.insert(sd, this);

        // Now we wait until PAST calls us with the receiveResult
        // and then we return the address
      } catch (IOException e) {
        command.receiveException(e);
      }
    }
      
    /**
      * Called when a previously requested result is now availble.
     *
     * @param result The result of the command.
     */
    public void receiveResult(Object result) {
      if (! (result instanceof Boolean[])) {
        command.receiveException(new IOException("Storage of signed data into Past returned unknown data " + result));
      } else {
        Boolean[] results = (Boolean[]) result;
        boolean error = false;

        for (int i=0; i<results.length; i++) {
          error = error || (results[i] == null) || (! results[i].booleanValue());
        }

        if (! error) {
          command.receiveResult(data.buildSecureReference(location, key));
        } else {
          command.receiveException(new IOException("Storage of signed data into PAST failed - replicas did not store object."));
        }
      }
    }

    /**
      * Called when a previously requested result causes an exception
     *
     * @param result The exception caused
     */
    public void receiveException(Exception result) {
      command.receiveException(result);
    }
  }

  /**
    * Class which is reposible for handling a single RetrieveSecure
   * task.
   */
  protected class RetrieveSecureTask implements Continuation {

    private SecureReference reference;
    private Continuation command;

    /**
      * This contructs creates a task to store a given data and call the
     * given command when the data has been stored.
     *
     * @param reference The reference to retrieve
     * @param command The command to run once the data has been retrieved
     */
    protected RetrieveSecureTask(SecureReference reference, Continuation command) {
      this.reference = reference;
      this.command = command;
    }

    /**
      * Starts this task running.
     */
    protected void start() {
      past.lookup(reference.getLocation(), this);

      // Now we wait until PAST calls us with the receiveResult
      // and then we continue processing this call
    }

    /**
      * Called when a previously requested result is now availble.
     *
     * @param result The result of the command.
     */
    public void receiveResult(Object result) {
      try {
        SecureData sd = (SecureData) result;

        if (sd == null) {
          command.receiveResult(null);
          return;
        }

        byte[] keyBytes = reference.getKey().getEncoded();

        byte[] cipherText = sd.getData();
        byte[] plainText = security.decryptDES(cipherText, keyBytes);
        Object data = security.deserialize(plainText);

        // Verify hash(cipher) == location
        byte[] hashCipher = security.hash(cipherText);
        byte[] loc = reference.getLocation().copy();
        if (! Arrays.equals(hashCipher, loc)) {
          command.receiveException(new StorageException("Hash of cipher text does not match location."));
          return;
        }

        command.receiveResult((PostData) data);
      }
      catch (ClassCastException cce) {
        command.receiveException(new StorageException("ClassCastException while retrieving data: " + cce));
      }
      catch (IOException ioe) {
        command.receiveException(new StorageException("IOException while retrieving data: " + ioe));
      }
      catch (ClassNotFoundException cnfe) {
        command.receiveException(new StorageException("ClassNotFoundException while retrieving data: " + cnfe));
      }
    }

    /**
      * Called when a previously requested result causes an exception
     *
     * @param result The exception caused
     */
    public void receiveException(Exception result) {
      command.receiveException(result);
    }
  }
}
