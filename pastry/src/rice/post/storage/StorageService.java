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
import rice.pastry.*;
import rice.pastry.security.*;

/**
 * This class represents a service which stores data in PAST.  This
 * class supports two types of data: content-hash blocks and private-key
 * signed blocks.  This class will automatically format and store data,
 * as well as retrieve and verify the stored data.
 */
public class StorageService {
  
  // The name of the symmetric cipher
  public static String SYMMETRIC_ALGORITHM = "DES/ECB/PKCS5Padding";
  
  // The name of the signature algorithm
  public static String SIGNATURE_ALGORITHM = "SHA1withRSA";

  // the past service
  private PASTService past;

  // the credentials to use
  private Credentials credentials;

  // the keypair to use to sign stuff
  private KeyPair keyPair;

  // the cipher used to encrypt/decrypt stuff
  private Cipher cipher;

  // the signature used for verification and signing
  private Signature signature;
  
  /**
   * Contructs a StorageService given a PAST to run on
   * top of.
   *
   * @param past The PAST service to use.
   */
  public StorageService(PASTService past, Credentials credentials, KeyPair keyPair) {
    this.past = past;
    this.credentials = credentials;
    this.keyPair = keyPair;

    try {
      cipher = Cipher.getInstance(SYMMETRIC_ALGORITHM);
      signature = Signature.getInstance(SIGNATURE_ALGORITHM);
    } catch (NoSuchAlgorithmException e) {
      System.out.println("NoSuchAlgorithmException on construction: " + e);
    } catch (NoSuchPaddingException e) {
      System.out.println("NoSuchPaddingException on construction: " + e);
    } 
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
  public ContentHashReference storeContentHash(PostData data) {
    byte[] plainText = serialize(data);
    byte[] hash = hash(plainText);
    byte[] cipherText = DESencrypt(plainText, hash);
    byte[] loc = hash(cipherText);

    NodeId location = new NodeId(loc);
    SecretKeySpec secretKey = new SecretKeySpec(hash, "DES");

    ContentHashData chd = new ContentHashData(cipherText, null);
    
    past.insert(location, chd, credentials);

    return data.buildContentHashReference(location, secretKey);
  }

  /**
   * The method retrieves a given PostDataReference object from the
   * network. This method also performs the verification checks and
   * decryption necessary.
   *
   * @param reference The reference to the PostDataObject
   * @return The corresponding PostData object
   */
  public PostData retrieveContentHash(ContentHashReference reference) {
    // TO DO - verify hashes, classes, last class
    ContentHashData chd = (ContentHashData) past.lookup(reference.getLocation()).getOriginal();

    byte[] cipherText = chd.getData();
    byte[] plainText = DESdecrypt(cipherText, reference.getKey().getEncoded());
    Object data = deserialize(plainText);

    return (PostData) data;
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
  public SignedReference storeSigned(PostData data, NodeId location) {
    byte[] plainText = serialize(data);
    long timestamp = System.currentTimeMillis();
    byte[] signature = sign(plainText, timestamp);

    SignedData sd = new SignedData(plainText, timestamp, signature, null);

    past.insert(location, sd, credentials);

    return data.buildSignedReference(location);   
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
  public PostData retrieveSigned(SignedReference reference) {
    // TO DO - verify signature, last class, etc...
    SignedData sd = (SignedData) past.lookup(reference.getLocation()).getOriginal();

    byte[] plainText = sd.getData();
    Object data = deserialize(plainText);

    return (PostData) data;
  }

  /**
   * Private utility method for serializing an object
   *
   * @param o The object to serialize
   * @return The byte[] of the object
   */
  private byte[] serialize(Object o) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);

      oos.writeObject(o);
      oos.flush();

      return baos.toByteArray();
    } catch (IOException e) {
      System.out.println("IOException serializing object: " + e);
      return null;
    }
  }

  /**
   * Private utitliy method for deserializing an object
   *
   * @param data The data to deserialize
   * @return The object
   */
  private Object deserialize(byte[] data) {
    try {
      ByteArrayInputStream bais = new ByteArrayInputStream(data);
      ObjectInputStream ois = new ObjectInputStream(bais);

      return ois.readObject();
    } catch (IOException e) {
      System.out.println("IOException deserializing object: " + e);
      return null;
    } catch (ClassNotFoundException e) {
      System.out.println("ClassNotFoundException deserializing object: " + e);
      return null;
    }
  }

  /**
   * Private utility method for determineing the hash of
   * a byte[]
   *
   * @param input The input
   * @return The hash value
   */
  private byte[] hash(byte[] input) {
    MessageDigest md = null;

    try {
      md = MessageDigest.getInstance( "MD5" );
    } catch ( NoSuchAlgorithmException e ) {
      System.err.println( "No MD5 support!" );
      return null;
    }

    md.update(input);
    return md.digest();
  }

  /**
   * Private utility method for encrypting a block of data
   * with DES
   *
   * @param data The data
   * @param key The key
   * @return The ciphertext
   */
  private byte[] DESencrypt(byte[] data, byte[] key) {
    try {      
      DESKeySpec DESkey = new DESKeySpec(key);
      SecretKeySpec secretKey = new SecretKeySpec(DESkey.getKey(), "DES");
      cipher.init(Cipher.ENCRYPT_MODE, secretKey);

      return cipher.doFinal(data);
    } catch (InvalidKeyException e) {
      System.out.println("InvalidKeyException encrypting object: " + e);
      return null;
    } catch (IllegalBlockSizeException e) {
      System.out.println("IllegalBlockSizeException encrypting object: " + e);
      return null;
    } catch (BadPaddingException e) {
      System.out.println("BadPaddingException encrypting object: " + e);
      return null;
    }
  }

  /**
   * Private utility method for decrypting some data with DES
   *
   * @param data The data to decrypt
   * @param key The key
   * @return The decrypted data
   */
  private byte[] DESdecrypt(byte[] data, byte[] key) {
    try {
      DESKeySpec DESkey = new DESKeySpec(key);
      SecretKeySpec secretKey = new SecretKeySpec(DESkey.getKey(), "DES");
      cipher.init(Cipher.DECRYPT_MODE, secretKey); 

      return cipher.doFinal(data);
    } catch (InvalidKeyException e) {
      System.out.println("InvalidKeyException decrypting object: " + e);
      return null;
    } catch (IllegalBlockSizeException e) {
      System.out.println("IllegalBlockSizeException decrypting object: " + e);
      return null;
    } catch (BadPaddingException e) {
      System.out.println("BadPaddingException decrypting object: " + e);
      return null;
    }
  }

  /**
   * Private utility method for signing a block of data and attached
   * timestamp with the user's private key
   *
   * @param data The data
   * @param time The attached timestamp
   * @return The signature
   */
  private byte[] sign(byte[] data, long time) {
    try {
      signature.initSign(keyPair.getPrivate());

      signature.update(data);
      signature.update(getByteArray(time));

      return signature.sign();
    } catch (InvalidKeyException e) {
      System.out.println("InvalidKeyException signing object: " + e);
      return null;
    } catch (SignatureException e) {
      System.out.println("SignatureException siging object: " + e);
      return null;
    } 
  }

  /**
   * Private utility method for verifying a signature
   *
   * @param data The data to verify
   * @param time The attached timestamp
   * @param sig The proposed signature
   * @return Whether or not the sig matches.
   */
  private boolean verify(byte[] data, long time, byte[] sig) {
    try {
      signature.initVerify(keyPair.getPublic());

      signature.update(data);
      signature.update(getByteArray(time));

      return signature.verify(sig);
    } catch (InvalidKeyException e) {
      System.out.println("InvalidKeyException verifying object: " + e);
      return false;
    } catch (SignatureException e) {
      System.out.println("SignatureException verifying object: " + e);
      return false;
    }   
  }

  /**
   * Private utility method for converting a long into
   * a byte[]
   *
   * @param input The log to convert
   * @return a byte[] representation
   */
  private byte[] getByteArray(long input) {
    byte[] output = new byte[8];

    output[0] = (byte) (0xFF & (input >> 56));
    output[1] = (byte) (0xFF & (input >> 48));
    output[2] = (byte) (0xFF & (input >> 40));
    output[3] = (byte) (0xFF & (input >> 32));
    output[4] = (byte) (0xFF & (input >> 24));
    output[5] = (byte) (0xFF & (input >> 16));
    output[6] = (byte) (0xFF & (input >> 8));
    output[7] = (byte) (0xFF & input);

    return output;
  }

  /**
   * Private utility method for converting a byte[] into
   * a long
   *
   * @param input The byte[] to convert
   * @return a long representation
   */
  private long getLong(byte[] input) {
    return ((input[0] << 56) | (input[1] << 48) | (input[2] << 40) | (input[3] << 32) |
            (input[4] << 24) | (input[5] << 16) | (input[6] << 8) | input[7]);
  }

  public static void main(String[] argv) throws NoSuchAlgorithmException {
    System.out.println("StorageService Test Suite");
    System.out.println("-------------------------------------------------------------");
    System.out.println("  Initializing Tests");
    System.out.print("    Generating key pair\t\t\t\t\t");

    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    KeyPair pair = kpg.generateKeyPair();
    System.out.println("[ DONE ]");
    
    System.out.print("    Building cipher\t\t\t\t\t");
    StorageService storage = new StorageService(null, null, pair);

    System.out.println("[ DONE ]");
    System.out.println("-------------------------------------------------------------");
    System.out.println("  Running Tests");

    System.out.print("    Testing long conversion\t\t\t\t");
    long testLong = Long.parseLong("0123456789ABCDEF", 16);
    
    byte[] testLongByte = storage.getByteArray(testLong);

    if ((testLongByte[0] == (byte) 0x01) &&
        (testLongByte[1] == (byte) 0x23) &&
        (testLongByte[2] == (byte) 0x45) &&
        (testLongByte[3] == (byte) 0x67) &&
        (testLongByte[4] == (byte) 0x89) &&
        (testLongByte[5] == (byte) 0xAB) &&
        (testLongByte[6] == (byte) 0xCD) &&
        (testLongByte[7] == (byte) 0xEF)) {
      System.out.println("[ PASSED ]");
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Input: \t" + testLong);
      System.out.println("    Output:\t" + testLongByte[0] + " " + testLongByte[1] + " "  + 																		testLongByte[2] + " "  + testLongByte[3]);
    }
    
    
    System.out.print("    Testing serialization\t\t\t\t");
    String testString = "test";
    byte[] testStringByte = storage.serialize(testString);
    String testStringOutput = (String) storage.deserialize(testStringByte);

    if (testStringOutput.equals(testString)) {
      System.out.println("[ PASSED ]");
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Input: \t" + testString);
      System.out.println("    Output:\t" + testStringOutput);
    }

    System.out.print("    Testing hashing\t\t\t\t\t");
    byte[] testStringHash = storage.hash(testStringByte);

    if ((testStringHash != null) && (testStringHash.length == 16)) {
      System.out.println("[ PASSED ]");
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Input: \t" + testString);
      System.out.println("    Output:\t" + testStringHash);

      if (testStringHash != null) {
        System.out.println("    Length:\t" + testStringHash.length);
      }
    }

    System.out.print("    Testing symmetric encryption\t\t\t");
    Random random = new Random();
    
    byte[] key = new byte[8];
    random.nextBytes(key);
    byte[] testStringCipherText = storage.DESencrypt(testStringByte, key);
    byte[] testStringPlainText = storage.DESdecrypt(testStringCipherText, key);

    if (Arrays.equals(testStringByte, testStringPlainText)) {
      System.out.println("[ PASSED ]");
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Input: \t" + testString);
      System.out.println("    Length:\t" + testStringByte.length);
      System.out.println("    Cipher Len:\t" + testStringCipherText.length);
      System.out.println("    Output Len:\t" + testStringPlainText.length);
    }    

    System.out.print("    Testing signing and verification (phase 1)\t\t");

    long time = System.currentTimeMillis();
    byte[] testStringSig = storage.sign(testStringByte, time);

    if (storage.verify(testStringByte, time, testStringSig)) {
      System.out.println("[ PASSED ]");
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Input: \t" + testString);
      System.out.println("    Length:\t" + testStringByte.length);
      System.out.println("    Sig Len:\t" + testStringSig.length);
    }

    System.out.print("    Testing signing and verification (phase 2)\t\t");

    time = time + 100;

    if (! storage.verify(testStringByte, time, testStringSig)) {
      System.out.println("[ PASSED ]");
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Input: \t" + testString);
      System.out.println("    Length:\t" + testStringByte.length);
      System.out.println("    Sig Len:\t" + testStringSig.length);
    }   
    
    
    System.out.println("-------------------------------------------------------------");
  }
}
