package rice.post.storage;

import java.security.*;
import java.security.spec.*;
import java.util.*;
import java.math.*;
import java.io.*;
import javax.crypto.*;
import javax.crypto.spec.*;

import rice.past.*;
import rice.post.*;
import rice.post.security.*;
import rice.pastry.*;
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
   * The PAST service used for distributed persistant storage.
   */
  private PASTService past;
  
  /**
   * Security service to handle all encryption tasks.
   */
  private SecurityService security;

  /**
   * The credentials used to store data.
   */
  private Credentials credentials;
  
  /**
   * Contructs a StorageService given a PAST to run on top of.
   *
   * @param past The PAST service to use.
   * @param credentials Credentials to use to store data.
   * @param security SecurityService to handle all security related tasks
   */
  public StorageService(PASTService past, Credentials credentials, SecurityService security) {
    this.past = past;
    this.credentials = credentials;
    this.security = security;
  }

  /**
   * Stores a PostData in the PAST storage system, in encrypted state,
   * and returns a pointer and key to the data object.
   *
   * This first encrypts the PostData using it's hash value as the
   * key, and then stores the ciphertext at the value of the hash of
   * the ciphertext.
   *
   * @param data The data to store.
   * @return A pointer and key to the data.
   */
  public ContentHashReference storeContentHash(PostData data) throws StorageException {
    try {
      byte[] plainText = security.serialize(data);
      byte[] hash = security.hash(plainText);
      byte[] cipherText = security.encryptDES(plainText, hash);
      byte[] loc = security.hash(cipherText);

      NodeId location = new NodeId(loc);
      SecretKeySpec secretKey = new SecretKeySpec(hash, "DES");
      
      ContentHashData chd = new ContentHashData(cipherText, null);
      
      // Store the content hash data in PAST
      past.insert(location, chd, credentials);
      
      return data.buildContentHashReference(location, secretKey);
    }
    catch (IOException ioe) {
      throw new StorageException("IOException while storing data: " + ioe);
    }
  }

  /**
   * This method retrieves a given PostDataReference object from the
   * network. This method also performs the verification checks and
   * decryption necessary.
   *
   * @param reference The reference to the PostDataObject
   * @return The corresponding PostData object
   */
  public PostData retrieveContentHash(ContentHashReference reference) throws StorageException {
    try {
      // TO DO - verify hashes, classes, last class
      //  (Catch a ClassCastException)
      ContentHashData chd = 
        (ContentHashData) past.lookup(reference.getLocation()).getOriginal();
      
      byte[] cipherText = chd.getData();
      byte[] plainText = security.decryptDES(cipherText,
                                             reference.getKey().getEncoded());
      Object data = security.deserialize(plainText);
      
      return (PostData) data;
    }
    catch (IOException ioe) {
      throw new StorageException("IOException while retrieving data: " + ioe);
    }
    catch (ClassNotFoundException cnfe) {
      throw new StorageException("ClassNotFoundException while retrieving data: " + cnfe);
    }
  }
  
  /**
   * Stores a PostData in the PAST store by signing the content and
   * storing it at a well-known location. This method also includes
   * a timestamp, which dates this update.
   *
   * @param data The data to store
   * @param location The location where to store the data
   * @return A reference to the data
   */
  public SignedReference storeSigned(PostData data, NodeId location) throws StorageException {
    try {
      byte[] plainText = security.serialize(data);
      long timestamp = System.currentTimeMillis();
      byte[] signature = security.sign(plainText, timestamp);
      
      SignedData sd = new SignedData(plainText, timestamp, signature, null);
      
      // Store the signed data in PAST
      past.insert(location, sd, credentials);
      
      return data.buildSignedReference(location);
    }
    catch (IOException ioe) {
      throw new StorageException("IOException while storing data: " + ioe);
    }
  }

  /**
   * This method retrieves a previously-stored private-key signed
   * block from PAST.  This method also does all necessary verification
   * checks and fetches the content from multiple locations in order
   * to prevent version-rollback attacks.
   *
   * @param location The location of the data
   * @return The data
   */
  public PostData retrieveSigned(SignedReference reference) throws StorageException {
    try {
      // TO DO - verify signature, last class, etc...
      //  (Catch a ClassCastException)
      SignedData sd = (SignedData) past.lookup(reference.getLocation()).getOriginal();
      
      byte[] plainText = sd.getData();
      Object data = security.deserialize(plainText);
      
      return (PostData) data;
    }
    catch (IOException ioe) {
      throw new StorageException("IOException while retrieving data: " + ioe);
    }
    catch (ClassNotFoundException cnfe) {
      throw new StorageException("ClassNotFoundException while retrieving data: " + cnfe);
    }
  }

  
  /**
   * Tests the storage service.
   */
  public static void main(String[] argv) throws NoSuchAlgorithmException {
    System.out.println("StorageService Test Suite");
    System.out.println("-------------------------------------------------------------");
    System.out.println("  Initializing Tests");
    /*
    System.out.print("    Generating key pair\t\t\t\t\t");

    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    KeyPair pair = kpg.generateKeyPair();
    System.out.println("[ DONE ]");
    
    System.out.print("    Building cipher\t\t\t\t\t");
    StorageService storage = new StorageService(null, null, pair);
    */

    System.out.println("[ DONE ]");
    System.out.println("-------------------------------------------------------------");
    System.out.println("  Running Tests");

    
    
    System.out.println("-------------------------------------------------------------");
  }
}
