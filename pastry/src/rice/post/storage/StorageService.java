package rice.post.storage;

import java.security.*;
import java.security.spec.*;
import java.util.*;
import java.math.*;
import java.io.*;
import javax.crypto.*;
import javax.crypto.spec.*;

import rice.*;
import rice.Continuation.*;
import rice.p2p.aggregation.*;
import rice.p2p.commonapi.*;
import rice.p2p.past.*;
import rice.p2p.past.gc.*;

import rice.post.*;
import rice.post.log.*;
import rice.post.security.*;

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
   * The default timeout period of objects (3 weeks)
   */
  protected long timeoutInterval;

  /**
   * The address of the user running this storage service.
   */
  private PostEntityAddress entity;
  
  /**
   * The PAST service used for distributed persistant storage.
   */
  private Past immutablePast;
  
  /**
   * The PAST service used for distributed persistant storage.
   */
  private Past mutablePast;
  
  /**
   * The keyPair used to sign and verify objects
   */
  private KeyPair keyPair;

  /**
   * Stored data waiting for verification
   */
  private Hashtable pendingVerification;
  
  /**
   * The factory for creating ids
   */
  private IdFactory factory;
  
  /**
   * The random number generator
   */
  private Random rng;

  /**
   * Lifetime of log head backups
   */
  private long BACKUP_LIFETIME = 1000 * 60 * 60 * 24;
  
  /**
   * Contructs a StorageService given a PAST to run on top of.
   *
   * @param past The PAST service to use.
   * @param credentials Credentials to use to store data.
   * @param keyPair The keypair to sign/verify data with
   */
  public StorageService(PostEntityAddress address, Past immutablePast, Past mutablePast, IdFactory factory, KeyPair keyPair, long timeoutInterval) {
    this.entity = address;
    this.immutablePast = immutablePast;
    this.mutablePast = mutablePast;
    this.keyPair = keyPair;
    this.factory = factory;
    this.timeoutInterval = timeoutInterval;
    
    rng = new Random();
    pendingVerification = new Hashtable();
  }

  public Id getRandomNodeId() {
    byte[] data = new byte[20];
    rng.nextBytes(data);
    
    return factory.buildId(data);
  }
  
  /**
   * Internal method which returns what the timeout should be for an
   * object inserted now.  Basically, does System.currentTimeMillis() +
   * timeoutInterval.
   *
   * @return The default timeout period for an object inserted now
   */
  protected long getTimeout() {
    return System.currentTimeMillis() + timeoutInterval;
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
   * This method "refreshes" a list of ContentHashReferences, which ensures that
   * all of the referenced objects are not collected by the underlying store.
   * This method should be invoked upon objects which the user is interested in, 
   * but the user did not create (i.e. The parts of an email the user has recevied).
   *
   * @param references The references to refresh
   * @param command The command to run once done
   */
  public void refreshContentHash(ContentHashReference[] references, Continuation command) {
    if (immutablePast instanceof GCPast) {
      Id[] ids = new Id[references.length];
    
      for (int i=0; i<ids.length; i++)
        ids[i] = references[i].getLocation();
      
      ((GCPast) immutablePast).refresh(ids, getTimeout(), command);
    } else {
      command.receiveResult(Boolean.TRUE);
    }
  }
  
  /**
   * This method backs up all of the provided logs by inserting them into the immutable
   * store with appropriate version numbers.
   *
   * @param references The references to refresh
   * @param command The command to run once done
   */
  public void backupLogs(final PostLog log, final Log[] logs, Continuation command) {
    long time = ((long) System.currentTimeMillis() / PostImpl.BACKUP_INTERVAL) * PostImpl.BACKUP_INTERVAL;
    StoreSignedTask task = new StoreSignedTask(keyPair, new GroupData(logs), log.getLocation(), command, immutablePast, time, System.currentTimeMillis() + BACKUP_LIFETIME);
    task.start();
  }
  
  /**
   * This method performs an emergency recovery of the logs by reinserting them into the
   * provided PAST store.
   *
   */
  public static void recoverLogs(GroupData data, final KeyPair keyPair, final Past past, Continuation command) {
    final Log[] logs = (Log[]) data.getData();
    
    Continuation c = new StandardContinuation(command) {
      int i = 0;
      
      public void receiveResult(Object o) {
        if (i < logs.length) {
          StoreSignedTask task = new StoreSignedTask(keyPair, logs[i], logs[i].getLocation(), this, past, GCPast.INFINITY_EXPIRATION);
          task.start();
          i++;
        } else {
          parent.receiveResult(Boolean.TRUE);
        }
      }
    };
    
    c.receiveResult(null);
  }
  
  /**
   * Method which sets the aggregate head, if we are using a Aggregation as
   * the immutable past store.
   *
   * @param log The log to set the aggregate in
   */
  public void setAggregate(PostLog log) {
    if (immutablePast instanceof AggregationImpl) 
      log.setAggregateHead(((AggregationImpl) immutablePast).getHandle());
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
    StoreSignedTask task = new StoreSignedTask(keyPair, data, location, command, mutablePast, GCPast.INFINITY_EXPIRATION);
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
    retrieveAndVerifySigned(reference, keyPair.getPublic(), command);
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
    if ((sd == null) || (! SecurityUtils.verify(sd.getDataAndTimestamp(), sd.getSignature(), key))) {
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
  
  /**
   * This method "refreshes" a list of SecureReferences, which ensures that
   * all of the referenced objects are not collected by the underlying store.
   * This method should be invoked upon objects which the user is interested in, 
   * but the user did not create (i.e. The parts of an email the user has recevied).
   *
   * @param references The references to refresh
   * @param command The command to run once done
   */
  public void refreshSecure(SecureReference[] references, Continuation command) {
    if (immutablePast instanceof GCPast) {
      Id[] ids = new Id[references.length];
      
      for (int i=0; i<ids.length; i++)
        ids[i] = references[i].getLocation();
      
      ((GCPast) immutablePast).refresh(ids, getTimeout(), command);
    } else {
      command.receiveResult(Boolean.TRUE);
    }
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
    private byte[] key;
    
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
        byte[] plainText = SecurityUtils.serialize(data);
        byte[] hash = SecurityUtils.hash(plainText);
        byte[] cipherText = SecurityUtils.encryptSymmetric(plainText, hash);
        byte[] loc = SecurityUtils.hash(cipherText);

        location = factory.buildId(loc);
        
        key = hash;

        final ContentHashData chd = new ContentHashData(location, cipherText);
        final Continuation task = this;

        immutablePast.lookupHandles(chd.getId(), immutablePast.getReplicationFactor()+1, new StandardContinuation(command) {
          public void receiveResult(Object o) {
            PastContentHandle[] handles = (PastContentHandle[]) o;
            
            for (int i=0; i<handles.length; i++) {
              if (handles[i] != null) {
                command.receiveResult(data.buildContentHashReference(location, key));
                return;
              }
            }
            
            // Store the content hash data in PAST
            if (immutablePast instanceof GCPast) 
              ((GCPast) immutablePast).insert(chd, getTimeout(), task);
            else 
              immutablePast.insert(chd, task);
          }
        });

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
        command.receiveException(new IOException("Storage of content hash data into Past returned unknown data " + result));
      } else {
        Boolean[] results = (Boolean[]) result;
        int failed = 0;
        
        for (int i=0; i<results.length; i++) {
          if ((results[i] == null) || (! results[i].booleanValue())) 
            failed++;
        }

        if (failed <= results.length/2) {
          command.receiveResult(data.buildContentHashReference(location, key));
        } else {
          command.receiveException(new IOException("Storage of content hash data into PAST failed - replicas did not store object."));
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
      System.out.println(reference.getLocation() + " " + System.currentTimeMillis() + ": Starting PAST lookup");
      immutablePast.lookup(reference.getLocation(), this);

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
        System.out.println(reference.getLocation() + " " + System.currentTimeMillis() + ": Got return from PAST");
        ContentHashData chd = (ContentHashData) result;
        
        if (chd == null) {
          command.receiveException(new PostException("Content hash data not found in PAST!"));
          return;
        }
        
        // TO DO: fetch from multiple locations to prevent rollback attacks
        byte[] key = reference.getKey();
        
        byte[] cipherText = chd.getData();
        byte[] plainText = SecurityUtils.decryptSymmetric(cipherText, key);
        System.out.println(reference.getLocation() + " " + System.currentTimeMillis() + ": Done decryption");
        Object data = SecurityUtils.deserialize(plainText);
        System.out.println(reference.getLocation() + " " + System.currentTimeMillis() + ": Done deserialization");
        
        // Verify hash(cipher) == location
        byte[] hashCipher = SecurityUtils.hash(cipherText);
        System.out.println(reference.getLocation() + " " + System.currentTimeMillis() + ": Done ciphertext hash");
        byte[] loc = reference.getLocation().toByteArray();
        if (! Arrays.equals(hashCipher, loc)) {
          command.receiveException(new StorageException("Hash of cipher text does not match location."));
          return;
        }
        System.out.println(reference.getLocation() + " " + System.currentTimeMillis() + ": Done ciphertext match");
        
        // Verify hash(plain) == key
        byte[] hashPlain = SecurityUtils.hash(plainText);
        System.out.println(reference.getLocation() + " " + System.currentTimeMillis() + ": Done plaintext hash");
        if (! Arrays.equals(hashPlain, key)) {
          command.receiveException(new StorageException("Hash of retrieved content does not match key."));
          return;
        }
        System.out.println(reference.getLocation() + " " + System.currentTimeMillis() + ": Done plaintext match");
        
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
  protected static class StoreSignedTask implements Continuation {

    public static final int STATE_1 = 1;
    public static final int STATE_2 = 2;
    
    private PostData data;
    private Continuation command;
    private Id location;
    private Key key;
    private KeyPair keyPair;
    private Past past;
    private long time;
    private long expiration;

    /**
     * This contructs creates a task to store a given data and call the
     * given command when the data has been stored.
     *
     * @param data The data to store
     * @param command The command to run once the data has been stored
     */
    protected StoreSignedTask(KeyPair keyPair, PostData data, Id location, Continuation command, Past past, long time) {
      this(keyPair, data, location, command, past, time, GCPast.INFINITY_EXPIRATION);
    }
    
    protected StoreSignedTask(KeyPair keyPair, PostData data, Id location, Continuation command, Past past, long time, long expiration) {
      this.keyPair = keyPair;
      this.data = data;
      this.location = location;
      this.command = command;
      this.past = past;
      this.time = time;
      this.expiration = expiration;
    }

    /**
      * Starts this task running.
     */
    protected void start() {
      try {
        byte[] plainText = SecurityUtils.serialize(data);
        byte[] timestamp = SecurityUtils.getByteArray(time);
        
        SignedData sd = new SignedData(location, plainText, timestamp);

        sd.setSignature(SecurityUtils.sign(sd.getDataAndTimestamp(), keyPair.getPrivate()));

        // Store the signed data in PAST 
        if (past instanceof GCPast)
          ((GCPast)past).insert(sd, expiration, this);
        else
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
        int failed = 0;
        
        for (int i=0; i<results.length; i++) {
          if ((results[i] == null) || (! results[i].booleanValue())) 
            failed++;
        }
        
        if (failed <= results.length/2) {          
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
    private PastContentHandle[] handles;

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
      mutablePast.lookupHandles(reference.getLocation(), mutablePast.getReplicationFactor()+1, this);

      // Now we wait until PAST calls us with the receiveResult
      // and then we continue processing this call
    }

    /**
      * Called when a previously requested result is now availble.
     *
     * @param result The result of the command.
     */
    public void receiveResult(Object result) {
      if (handles == null) {
        handles = (PastContentHandle[]) result;

        if ((handles == null) || (handles.length == 0)) {
          command.receiveResult(null);
          return;
        }
          
        long latest = 0;
        StorageServiceDataHandle handle = null;

        for (int i=0; i<handles.length; i++) {
          StorageServiceDataHandle thisH = (StorageServiceDataHandle) handles[i];

          if ((thisH != null) && (thisH.getTimestamp() > latest)) {
            latest = thisH.getTimestamp();
            handle = thisH;
          }
        }

        if (handle != null) {
          mutablePast.fetch(handle, this);
        } else {
          command.receiveResult(null);
        }
      } else {
        Object data = null;

        try {
          SignedData sd = (SignedData) result;

          if (sd == null) {
            command.receiveResult(null);
            return;
          }

          byte[] plainText = sd.getData();
          data = SecurityUtils.deserialize(plainText);

          pendingVerification.put(data, sd);

          command.receiveResult((PostData) data);
        } catch (IOException ioe) {
          command.receiveException(new StorageException("IOException while retrieving data at " + reference.getLocation() + ": " + ioe));
        } catch (ClassNotFoundException cnfe) {
          command.receiveException(new StorageException("ClassNotFoundException while retrieving data: " + cnfe));
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
    private byte[] key;

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
        key = SecurityUtils.generateKeySymmetric();
        
        byte[] plainText = SecurityUtils.serialize(data);

        byte[] cipherText = SecurityUtils.encryptSymmetric(plainText, key);
        byte[] loc = SecurityUtils.hash(cipherText);

        location = factory.buildId(loc);

        SecureData sd = new SecureData(location, cipherText);

        // Store the content hash data in PAST
        if (immutablePast instanceof GCPast) 
          ((GCPast) immutablePast).insert(sd, getTimeout(), this);
        else 
          immutablePast.insert(sd, this);

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
        command.receiveException(new IOException("Storage of secure data into Past returned unknown data " + result));
      } else {
        Boolean[] results = (Boolean[]) result;
        int failed = 0;
        
        for (int i=0; i<results.length; i++) {
          if ((results[i] == null) || (! results[i].booleanValue())) 
            failed++;
        }
        
        if (failed <= results.length/2) {          
          command.receiveResult(data.buildSecureReference(location, key));
        } else {
          command.receiveException(new IOException("Storage of secure data into PAST failed - replicas did not store object."));
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
      immutablePast.lookup(reference.getLocation(), this);

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

        byte[] key = reference.getKey();

        byte[] cipherText = sd.getData();
        byte[] plainText = SecurityUtils.decryptSymmetric(cipherText, key);
        Object data = SecurityUtils.deserialize(plainText);

        // Verify hash(cipher) == location
        byte[] hashCipher = SecurityUtils.hash(cipherText);
        byte[] loc = reference.getLocation().toByteArray();
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
