package rice.post.storage;

import java.security.*;
import java.security.spec.*;
import java.util.*;
import java.math.*;
import java.io.*;
import javax.crypto.*;
import javax.crypto.spec.*;

import rice.past.*;
import rice.storage.*;
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
   * Stored data waiting for verification
   */
  private Hashtable pendingVerification;
  
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

    pendingVerification = new Hashtable();
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
      
      ContentHashData chd = new ContentHashData(cipherText);
      
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
      StorageObject so = past.lookup(reference.getLocation());

      if (so == null) {
        return null;
      }
      
      // TO DO: fetch from multiple locations to prevent rollback attacks
      ContentHashData chd = (ContentHashData) so.getOriginal();
      byte[] keyBytes = reference.getKey().getEncoded();
      
      byte[] cipherText = chd.getData();
      byte[] plainText = security.decryptDES(cipherText, keyBytes);
      Object data = security.deserialize(plainText);
      
      // Verify hash(cipher) == location
      byte[] hashCipher = security.hash(cipherText);
      byte[] loc = reference.getLocation().copy();
      if (!Arrays.equals(hashCipher, loc)) {
        throw new StorageException("Hash of cipher text does not match location.");
      }
      
      // Verify hash(plain) == key
      byte[] hashPlain = security.hash(plainText);
      if (!Arrays.equals(hashPlain, keyBytes)) {
        throw new StorageException("Hash of retrieved content does not match key.");
      }
      
      return (PostData) data;
    }
    catch (ClassCastException cce) {
      throw new StorageException("ClassCastException while retrieving data: " + cce);
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
      byte[] timestamp = security.getByteArray(System.currentTimeMillis());

      SignedData sd = new SignedData(plainText, timestamp);

      sd.setSignature(security.sign(sd.getDataAndTimestamp()));


      if (past.exists(location)) {
        // Store the signed data in PAST starting a new thingy
        past.update(location, sd, credentials);
      } else {
        // Store the signed data in PAST starting a new thingy
        past.insert(location, sd, credentials);
      }
        
      return data.buildSignedReference(location);
    } catch (IOException ioe) {
      System.out.println("IOException " + ioe + " occured during storage attempt.");
      ioe.printStackTrace();
      throw new StorageException("IOException while storing data: " + ioe + " " + ioe.getMessage());
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
  public PostData retrieveAndVerifySigned(SignedReference reference) throws StorageException {
    PostData pd = retrieveSigned(reference);
    verifySigned(pd, security.getPublicKey());

    return pd;
  }
  
  /**
   * This method retrieves a previously-stored block from PAST which was
   * signed using the private key matching the given public key.
   * This method also does all necessary verification
   * checks and fetches the content from multiple locations in order
   * to prevent version-rollback attacks.
   *
   * @param location The location of the data
   * @param publicKey The public key matching the private key used to sign the data
   * @return The data
   */
  public PostData retrieveAndVerifySigned(SignedReference reference, PublicKey publicKey)
    throws StorageException
  {
    PostData pd = retrieveSigned(reference);
    verifySigned(pd, publicKey);

    return pd;
  }    

  /**
   * This method retrieves a previously-stored block from PAST which was
   * signed using the private key. THIS METHOD EXPLICITLY DOES NOT PERFORM
   * ANY VERIFICATION CHECKS ON THE DATA.  YOU MUST CALL verifySigned() IN
   * ORDER TO VERIFY THE DATA.  This is provided for the case where the
   * cooresponding key is located in the data.
   *
   * @param location The location of the data
   * @return The data
   */
  public PostData retrieveSigned(SignedReference reference) throws StorageException {
    try {
      // TO DO: fetch from multiple locations to prevent rollback attacks
      StorageObject so = past.lookup(reference.getLocation());

      if (so == null) {
        return null;
      }

      SignedData sd = null;

      if (so.getUpdates().size() == 0) {
        sd = (SignedData) so.getOriginal();
      } else {
        Vector updates = so.getUpdates();
        sd = (SignedData) updates.elementAt(updates.size() - 1);
      }
      
      byte[] plainText = sd.getData();
      Object data = security.deserialize(plainText);

      pendingVerification.put(data, sd);
      
      return (PostData) data;
    }
    catch (ClassCastException cce) {
      throw new StorageException("ClassCastException while retrieving data: " + cce);
    }
    catch (IOException ioe) {
      throw new StorageException("IOException while retrieving data: " + ioe);
    }
    catch (ClassNotFoundException cnfe) {
      throw new StorageException("ClassNotFoundException while retrieving data: " + cnfe);
    }
  }

  /**
   * This method retrieves a previously-stored block from PAST which was
   * signed using the private key. THIS METHOD EXPLICITLY DOES NOT PERFORM
   * ANY VERIFICATION CHECKS ON THE DATA.  YOU MUST CALL verifySigned() IN
   * ORDER TO VERIFY THE DATA.  This is provided for the case where the
   * cooresponding key is located in the data.
   *
   * @param location The location of the data
   * @return The data
   */
  public void verifySigned(PostData data, PublicKey key) throws StorageException {
    SignedData sd = (SignedData) pendingVerification.remove(data);
    
    // Verify signature
    if ((sd == null) || (! security.verify(sd.getDataAndTimestamp(), sd.getSignature(), key))) {
      printArray(sd.getData());
      printArray(sd.getTimestamp());
      printArray(sd.getSignature());
      throw new StorageException("Signature of retrieved data is not correct.");
    }
  }

  /**
   * Stores a PostData in the PAST storage system, in encrypted state,
   * and returns a pointer and key to the data object.
   *
   * This method first generates a random key, uses this key to encrypt
   * the data, and then stored the data under the key of it's content-hash.
   *
   * @param data The data to store.
   * @return A pointer and key to the data.
   */
  public SecureReference storeSecure(PostData data) throws StorageException {
    try {
      byte[] plainText = security.serialize(data);

      byte[] key = security.generateKeyDES();

      byte[] cipherText = security.encryptDES(plainText, key);
      byte[] loc = security.hash(cipherText);

      NodeId location = new NodeId(loc);
      SecretKeySpec secretKey = new SecretKeySpec(key, "DES");

      SecureData sd = new SecureData(cipherText);

      // Store the content hash data in PAST
      past.insert(location, sd, credentials);

      return data.buildSecureReference(location, secretKey);
    }
    catch (IOException ioe) {
      throw new StorageException("IOException while storing data: " + ioe);
    }
  }

  /**
   * This method retrieves a given SecureReference object from the
   * network. This method also performs the verification checks and
   * decryption necessary.
   *
   * @param reference The reference to the PostDataObject
   * @return The corresponding PostData object
   */
  public PostData retrieveSecure(SecureReference reference) throws StorageException {
    try {
      StorageObject so = past.lookup(reference.getLocation());

      if (so == null) {
        return null;
      }
      
      SecureData sd = (SecureData) so.getOriginal();
      byte[] keyBytes = reference.getKey().getEncoded();

      byte[] cipherText = sd.getData();
      byte[] plainText = security.decryptDES(cipherText, keyBytes);
      Object data = security.deserialize(plainText);

      // Verify hash(cipher) == location
      byte[] hashCipher = security.hash(cipherText);
      byte[] loc = reference.getLocation().copy();
      if (!Arrays.equals(hashCipher, loc)) {
        throw new StorageException("Hash of cipher text does not match location.");
      }

      return (PostData) data;
    }
    catch (ClassCastException cce) {
      throw new StorageException("ClassCastException while retrieving data: " + cce);
    }
    catch (IOException ioe) {
      throw new StorageException("IOException while retrieving data: " + ioe);
    }
    catch (ClassNotFoundException cnfe) {
      throw new StorageException("ClassNotFoundException while retrieving data: " + cnfe);
    }
  }
    
  private void printArray(byte[] array) {
    for (int i=0; i<array.length; i++) {
      System.out.print(Byte.toString(array[i]));
    }

    System.out.println();
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
