package rice.post.security;

import java.security.*;
import java.security.cert.*;
import java.security.spec.*;
import java.util.*;
import java.math.*;
import java.io.*;
import javax.crypto.*;
import javax.crypto.spec.*;

import rice.post.*;

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
    * The length of DES keys
   */
  public static final int SYMMETRIC_KEY_LENGTH = 8;

  /**
    * The name of the hash function.
   */
  public static final String HASH_ALGORITHM = "MD5";

  /**
    * The key pair used to sign data.
   */
  private KeyPair keyPair;

  /**
    * The public key of the certificate authority.
   */
  private PublicKey caPublicKey;

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
    * A random object
   */
  private Random random;

  /**
    * Contructs a SecurityService given a user's keyPair.
   *
   * @param keyPair The user's key pair to use for data encryption
   * @param caPublicKey The public key of the certificate authority
   */
  public SecurityService(KeyPair keyPair, PublicKey caPublicKey) throws SecurityException {
    this.keyPair = keyPair;
    this.caPublicKey = caPublicKey;

    // Add a provider for RSA encryption
    Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

    this.random = new Random();

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
    * Returns this user's public key.
   */
  public PublicKey getPublicKey() {
    return keyPair.getPublic();
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
      synchronized (cipherDES) {
        DESKeySpec DESkey = new DESKeySpec(key);
        SecretKeySpec secretKey = new SecretKeySpec(DESkey.getKey(), "DES");
        cipherDES.init(Cipher.ENCRYPT_MODE, secretKey);

        return cipherDES.doFinal(data);
      }
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
      synchronized (cipherDES) {
        DESKeySpec DESkey = new DESKeySpec(key);
        SecretKeySpec secretKey = new SecretKeySpec(DESkey.getKey(), "DES");
        cipherDES.init(Cipher.DECRYPT_MODE, secretKey);

        return cipherDES.doFinal(data);
      }
    } catch (InvalidKeyException e) {
      throw new SecurityException("InvalidKeyException decrypting object: " + e);
    } catch (IllegalBlockSizeException e) {
      throw new SecurityException("IllegalBlockSizeException decrypting object: " + e);
    } catch (BadPaddingException e) {
      throw new SecurityException("BadPaddingException decrypting object: " + e);
    }
  }

  /**
    * Utility method for signing a block of data with the user's private key
   *
   * @param data The data
   * @return The signature
   */
  public PostSignature sign(byte[] data) throws SecurityException {
    return sign(data, keyPair.getPrivate());
  }

  /**
    * Utility method for signing a block of data with the user's private key
   *
   * @param data The data
   * @return The signature
   */
  private PostSignature sign(byte[] data, PrivateKey key) throws SecurityException {
    try {
      if ((data == null) || (key == null)) {
        throw new SecurityException("Attempt to use null data or key for signature:" + data + " " + key);
      }

      synchronized (signature) {
        signature.initSign(key);

        signature.update(hash(data));

        return new PostSignature(signature.sign());
      }
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
   * @param sig The proposed signature
   * @return Whether or not the sig matches.
   */
  public boolean verify(byte[] data, PostSignature sig) throws SecurityException {
    return verify(data, sig, keyPair.getPublic());
  }

  /**
    * Utility method for verifying a signature
   *
   * @param data The data to verify
   * @param sig The proposed signature
   * @param key The key to verify against
   * @return Whether or not the sig matches.
   */
  public boolean verify(byte[] data, PostSignature sig, PublicKey key) throws SecurityException {
    try {
      synchronized (signature) {
        signature.initVerify(key);

        signature.update(hash(data));

        return signature.verify(sig.getSignature());
      }
    } catch (InvalidKeyException e) {
      throw new SecurityException("InvalidKeyException verifying object: " + e);
    } catch (SignatureException e) {
      throw new SecurityException("SignatureException verifying object: " + e);
    }
  }

  /**
    * Utility method for verifying a certificate
   *
   * @param caKey The key to verify against (CA's pub key)
   * @param address The address of the entity
   * @param key The key to of the entity
   * @param certificate The proposed certificate
   * @return Whether or not the certificate matches.
   */
  public boolean verifyCertificate(PublicKey caKey, PostCertificate certificate) throws SecurityException {
    try {
      byte[] keyByte = serialize(certificate.getKey());
      byte[] addressByte = serialize(certificate.getAddress());

      byte[] all = new byte[addressByte.length + keyByte.length];
      System.arraycopy(addressByte, 0, all, 0, addressByte.length);
      System.arraycopy(keyByte, 0, all, addressByte.length, keyByte.length);

      return verify(all, certificate, caKey);
    } catch (IOException e) {
      throw new SecurityException("InvalidKeyException verifying object: " + e);
    }
  }

  /**
    * Utility method for verifying a signature
   *
   * @param address The address of the entity
   * @param key The key to of the entity
   * @param caKey The private key of the CA
   * @return Whether or not the sig matches.
   */
  public PostCertificate generateCertificate(PostEntityAddress address, PublicKey key, PrivateKey caKey) throws SecurityException {
    try {
      byte[] keyByte = serialize(key);
      byte[] addressByte = serialize(address);

      byte[] all = new byte[addressByte.length + keyByte.length];
      System.arraycopy(addressByte, 0, all, 0, addressByte.length);
      System.arraycopy(keyByte, 0, all, addressByte.length, keyByte.length);

      return new PostCertificate(address, key, sign(all, caKey).getSignature());
    } catch (IOException e) {
      throw new SecurityException("InvalidKeyException verifying object: " + e);
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
      synchronized (cipherRSA) {
        cipherRSA.init(Cipher.ENCRYPT_MODE, key);

        return cipherRSA.doFinal(data);
      }
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
      synchronized (cipherRSA) {
        cipherRSA.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());

        return cipherRSA.doFinal(data);
      }
    } catch (InvalidKeyException e) {
      throw new SecurityException("InvalidKeyException decrypting object: " + e);
    } catch (IllegalBlockSizeException e) {
      throw new SecurityException("IllegalBlockSizeException decrypting object: " + e);
    } catch (BadPaddingException e) {
      throw new SecurityException("BadPaddingException decrypting object: " + e);
    }
  }

  public byte[] generateKeyDES() {
    // pick random key
    byte[] key = new byte[SYMMETRIC_KEY_LENGTH];
    random.nextBytes(key);

    return key;
  }


  /**
    * Private utility method for converting a long into a byte[]
   *
   * @param input The log to convert
   * @return a byte[] representation
   */
  public byte[] getByteArray(long input) {
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
  public long getLong(byte[] input) {
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
    SecurityService security = new SecurityService(pair, null);

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

    PostSignature testStringSig = security.sign(testStringByte);

    if (security.verify(testStringByte, testStringSig)) {
      System.out.println("[ PASSED ]");
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Input: \t" + testString);
      System.out.println("    Length:\t" + testStringByte.length);
      System.out.println("    Sig Len:\t" + testStringSig.getSignature().length);
    }

    System.out.print("    Testing signing and verification (phase 2)\t\t");

    testStringSig.getSignature()[0]++;

    if (! security.verify(testStringByte, testStringSig)) {
      System.out.println("[ PASSED ]");
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Input: \t" + testString);
      System.out.println("    Length:\t" + testStringByte.length);
      System.out.println("    Sig Len:\t" + testStringSig.getSignature().length);
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
