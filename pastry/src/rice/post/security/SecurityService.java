package rice.post.security;

import java.security.*;
import java.security.spec.*;
import java.util.*;
import java.math.*;
import java.io.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/**
 * This class is a utility for performing security-related operations
 * and it stores the user's key pair for encryption and signing
 * purposes.
 *
 * @version $Id$
 */
public class SecurityService {

  /**
  * The name of the symmetric cipher to use.
   */
  public static final String ASYMMETRIC_ALGORITHM = "RSA";
  
  /**
   * The name of the symmetric cipher to use.
   */
  public static final String SYMMETRIC_ALGORITHM = "DES/ECB/PKCS5Padding";
  
  /**
   * The name of the signature algorithm to use.
   */
  public static final String SIGNATURE_ALGORITHM = "SHA1withRSA";

  /**
   * The name of the hash function.
   */
  public static final String HASH_ALGORITHM = "MD5";

  /**
   * The key pair used to sign data.
   */
  private KeyPair keyPair;

  /**
   * The cipher used to encrypt/decrypt data using DES
   */
  private Cipher cipherDES;

  /**
    * The cipher used to encrypt/decrypt data using RSA
   */
  private Cipher cipherRSA;

  /**
   * The signature used for verification and signing data.
   */
  private Signature signature;
  
  /**
   * Contructs a SecurityService given a user's keyPair.
   *
   * @param keyPair The user's key pair to use for data encryption
   */
  public SecurityService(KeyPair keyPair) throws SecurityException {
    this.keyPair = keyPair;

    // Add a provider for RSA encryption
    Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    
    try {
      cipherDES = Cipher.getInstance(SYMMETRIC_ALGORITHM);
      cipherRSA = Cipher.getInstance(ASYMMETRIC_ALGORITHM);
      signature = Signature.getInstance(SIGNATURE_ALGORITHM);
    } catch (NoSuchAlgorithmException e) {
      throw new SecurityException("NoSuchAlgorithmException on construction: " + e);
    } catch (NoSuchPaddingException e) {
      throw new SecurityException("NoSuchPaddingException on construction: " + e);
    }
  }

  /**
   * Utility method for serializing an object to a byte[].
   *
   * @param o The object to serialize
   * @return The byte[] of the object
   */
  public byte[] serialize(Object o) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);

    oos.writeObject(o);
    oos.flush();

    return baos.toByteArray();
  }

  /**
   * Utility method for deserializing an object from a byte[]
   *
   * @param data The data to deserialize
   * @return The object
   */
  public Object deserialize(byte[] data) throws IOException, ClassNotFoundException {
    ByteArrayInputStream bais = new ByteArrayInputStream(data);
    ObjectInputStream ois = new ObjectInputStream(bais);

    return ois.readObject();
  }

  /**
   * Utility method for determining the hash of a byte[] using a secure
   * hashing algorithm.
   *
   * @param input The input
   * @return The hash value
   */
  public byte[] hash(byte[] input) throws SecurityException {
    MessageDigest md = null;

    try {
      md = MessageDigest.getInstance(HASH_ALGORITHM);
    } catch ( NoSuchAlgorithmException e ) {
      throw new SecurityException("Hash algorithm not found.");
    }

    md.update(input);
    return md.digest();
  }

  /**
   * Utility method for encrypting a block of data with DES.
   *
   * @param data The data
   * @param key The key
   * @return The ciphertext
   */
  public byte[] encryptDES(byte[] data, byte[] key) throws SecurityException {
    try {      
      DESKeySpec DESkey = new DESKeySpec(key);
      SecretKeySpec secretKey = new SecretKeySpec(DESkey.getKey(), "DES");
      cipherDES.init(Cipher.ENCRYPT_MODE, secretKey);

      return cipherDES.doFinal(data);
    } catch (InvalidKeyException e) {
      throw new SecurityException("InvalidKeyException encrypting object: " + e);
    } catch (IllegalBlockSizeException e) {
      throw new SecurityException("IllegalBlockSizeException encrypting object: " + e);
    } catch (BadPaddingException e) {
      throw new SecurityException("BadPaddingException encrypting object: " + e);
    }
  }

  /**
   * Utility method for decrypting some data with DES
   *
   * @param data The data to decrypt
   * @param key The key
   * @return The decrypted data
   */
  public byte[] decryptDES(byte[] data, byte[] key) throws SecurityException {
    try {
      DESKeySpec DESkey = new DESKeySpec(key);
      SecretKeySpec secretKey = new SecretKeySpec(DESkey.getKey(), "DES");
      cipherDES.init(Cipher.DECRYPT_MODE, secretKey); 

      return cipherDES.doFinal(data);
    } catch (InvalidKeyException e) {
      throw new SecurityException("InvalidKeyException decrypting object: " + e);
    } catch (IllegalBlockSizeException e) {
      throw new SecurityException("IllegalBlockSizeException decrypting object: " + e);
    } catch (BadPaddingException e) {
      throw new SecurityException("BadPaddingException decrypting object: " + e);
    }
  }

  /**
   * Utility method for signing a block of data and attached
   * timestamp with the user's private key
   *
   * @param data The data
   * @param time The attached timestamp
   * @return The signature
   */
  public byte[] sign(byte[] data, long time) throws SecurityException {
    try {
      signature.initSign(keyPair.getPrivate());

      signature.update(data);
      signature.update(getByteArray(time));

      return signature.sign();
    } catch (InvalidKeyException e) {
      throw new SecurityException("InvalidKeyException signing object: " + e);
    } catch (SignatureException e) {
      throw new SecurityException("SignatureException signing object: " + e);
    } 
  }

  /**
   * Utility method for verifying a signature using the user's public
   * key.
   *
   * @param data The data to verify
   * @param time The attached timestamp
   * @param sig The proposed signature
   * @return Whether or not the sig matches.
   */
  private boolean verify(byte[] data, long time, byte[] sig) throws SecurityException {
    return verify(data, time, sig, keyPair.getPublic());
  }
  
  /**
   * Utility method for verifying a signature
   *
   * @param data The data to verify
   * @param time The attached timestamp
   * @param sig The proposed signature
   * @param key The key to verify against
   * @return Whether or not the sig matches.
   */
  private boolean verify(byte[] data, long time, byte[] sig, PublicKey key) throws SecurityException {
    try {
      signature.initVerify(key);

      signature.update(data);
      signature.update(getByteArray(time));

      return signature.verify(sig);
    } catch (InvalidKeyException e) {
      throw new SecurityException("InvalidKeyException verifying object: " + e);
    } catch (SignatureException e) {
      throw new SecurityException("SignatureException verifying object: " + e);
    }   
  }

  /**
   * Encrypts the given byte[] using the user's public key.
   *
   * TO DO: Check length of input
   *
   * @param data The data to encrypt
   * @return The encrypted data
   */
  public byte[] encryptRSA(byte[] data) throws SecurityException {
    return encryptRSA(data, keyPair.getPublic());
  }

  /**
   * Encrypts the given byte[] using the provided public key.
   *
   * TO DO: Check length of input
   *
   * @param data The data to encrypt
   * @param key The key to encrypt with
   * @return The encrypted data
   */
  public byte[] encryptRSA(byte[] data, PublicKey key) throws SecurityException {
    try {
      cipherRSA.init(Cipher.ENCRYPT_MODE, key);

      return cipherRSA.doFinal(data);
    } catch (InvalidKeyException e) {
      throw new SecurityException("InvalidKeyException encrypting object: " + e);
    } catch (IllegalBlockSizeException e) {
      throw new SecurityException("IllegalBlockSizeException encrypting object: " + e);
    } catch (BadPaddingException e) {
      throw new SecurityException("BadPaddingException encrypting object: " + e);
    }
  }
  
  /**
   * Decrypts the given byte[] using the user's private key.
   *
   * TO DO: Check length of input
   *
   * @param data The data to decrypt
   * @return The decrypted data
   */
  public byte[] decryptRSA(byte[] data) throws SecurityException {
    try {
      cipherRSA.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());

      return cipherRSA.doFinal(data);
    } catch (InvalidKeyException e) {
      throw new SecurityException("InvalidKeyException decrypting object: " + e);
    } catch (IllegalBlockSizeException e) {
      throw new SecurityException("IllegalBlockSizeException decrypting object: " + e);
    } catch (BadPaddingException e) {
      throw new SecurityException("BadPaddingException decrypting object: " + e);
    }
  }
    
  
  /**
   * Private utility method for converting a long into a byte[]
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
   * Private utility method for converting a byte[] into a long
   *
   * @param input The byte[] to convert
   * @return a long representation
   */
  private long getLong(byte[] input) {
    return ((input[0] << 56) | (input[1] << 48) | (input[2] << 40) | (input[3] << 32) |
            (input[4] << 24) | (input[5] << 16) | (input[6] << 8) | input[7]);
  }

  
  /**
   * Tests the security service.
   */
  public static void main(String[] argv) throws NoSuchAlgorithmException, IOException, ClassNotFoundException {
    System.out.println("SecurityService Test Suite");
    System.out.println("-------------------------------------------------------------");
    System.out.println("  Initializing Tests");
    System.out.print("    Generating key pair\t\t\t\t\t");

    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    KeyPair pair = kpg.generateKeyPair();
    System.out.println("[ DONE ]");
    
    System.out.print("    Building cipher\t\t\t\t\t");
    SecurityService security = new SecurityService(pair);

    System.out.println("[ DONE ]");
    System.out.println("-------------------------------------------------------------");
    System.out.println("  Running Tests");

    System.out.print("    Testing long conversion\t\t\t\t");
    long testLong = Long.parseLong("0123456789ABCDEF", 16);
    
    byte[] testLongByte = security.getByteArray(testLong);

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
      System.out.println("    Output:\t" + testLongByte[0] + " " + testLongByte[1] + " "  +                   testLongByte[2] + " "  + testLongByte[3]);
    }
    
    
    System.out.print("    Testing serialization\t\t\t\t");
    String testString = "test";
    byte[] testStringByte = security.serialize(testString);
    String testStringOutput = (String) security.deserialize(testStringByte);

    if (testStringOutput.equals(testString)) {
      System.out.println("[ PASSED ]");
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Input: \t" + testString);
      System.out.println("    Output:\t" + testStringOutput);
    }

    System.out.print("    Testing hashing\t\t\t\t\t");
    byte[] testStringHash = security.hash(testStringByte);

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
    byte[] testStringCipherText = security.encryptDES(testStringByte, key);
    byte[] testStringPlainText = security.decryptDES(testStringCipherText, key);

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
    byte[] testStringSig = security.sign(testStringByte, time);

    if (security.verify(testStringByte, time, testStringSig)) {
      System.out.println("[ PASSED ]");
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Input: \t" + testString);
      System.out.println("    Length:\t" + testStringByte.length);
      System.out.println("    Sig Len:\t" + testStringSig.length);
    }

    System.out.print("    Testing signing and verification (phase 2)\t\t");

    time = time + 100;

    if (! security.verify(testStringByte, time, testStringSig)) {
      System.out.println("[ PASSED ]");
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Input: \t" + testString);
      System.out.println("    Length:\t" + testStringByte.length);
      System.out.println("    Sig Len:\t" + testStringSig.length);
    }   

    System.out.print("    Testing RSA functions\t\t\t\t");

    byte[] testStringEncrypted = security.encryptRSA(testStringByte);
    byte[] testStringDecrypted = security.decryptRSA(testStringEncrypted);

    if (Arrays.equals(testStringByte, testStringDecrypted)) {
      System.out.println("[ PASSED ]");
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Input: \t" + testString);
      System.out.println("    Length:\t" + testStringByte.length);
      System.out.println("    Enc Len:\t" + testStringEncrypted.length);
      System.out.println("    Dec Len:\t" + testStringDecrypted.length);
    }
    

    
    System.out.println("-------------------------------------------------------------");
  }
}
