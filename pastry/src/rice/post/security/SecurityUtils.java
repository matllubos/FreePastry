package rice.post.security;
import java.io.*;
import java.math.*;

import java.security.*;
import java.security.cert.*;
import java.security.spec.*;
import java.util.*;
import java.util.zip.*;
import javax.crypto.*;
import javax.crypto.spec.*;

import rice.post.*;
import rice.serialization.*;

/**
 * This class contains a large number of static methods for performing
 * security-related primitives, such as encrypt, decrypt, etc...
 *
 * @version $Id$
 * @author amislove
 */
public class SecurityUtils {

  // ----- STATIC CONFIGURATION FIELDS -----

  /**
   * The name of the asymmetric cipher to use.
   */
  public final static String ASYMMETRIC_ALGORITHM = "RSA/ECB/OAEPPadding";
  public final static String DEPRECATED_ASYMMETRIC_ALGORITHM = "RSA";

  /**
   * The name of the symmetric cipher to use.
   */
  public final static String SYMMETRIC_ALGORITHM = "DES";

  /**
   * The name of the asymmetric generator to use.
   */
  public final static String ASYMMETRIC_GENERATOR = "RSA";

  /**
   * The name of the symmetric cipher to use.
   */
  public final static String SYMMETRIC_GENERATOR = "DES";

  /**
   * The name of the signature algorithm to use.
   */
  public final static String SIGNATURE_ALGORITHM = "SHA1withRSA";

  /**
   * The length of the symmetric keys
   */
  public final static int SYMMETRIC_KEY_LENGTH = 56;
  
  /**
   * The name of the hash function.
   */
  public final static String HASH_ALGORITHM = "SHA1";
  
  /**
   * The name of the hmac function. 
   */
  public final static String HMAC_ALGORITHM = "MD5";
  
  /**
   * The name of the apop function. 
   */
  public final static String APOP_ALGORITHM = "MD5";
  
  /**
   * The length of hmac keys
   */
  public final static int HMAC_KEY_LENGTH = 64;
  
  /**
   * The ipad of hmac keys, as defined in RFC 2195
   */
  public final static byte HMAC_IPAD_BYTE = 0x36;
  
  /**
   * The opad of hmac keys, as defined in RFC 2195
   */
  public final static byte HMAC_OPAD_BYTE = 0x5C;
  
  /**
   * The ipad byte array for use in hmac
   */
  public final static byte[] HMAC_IPAD = new byte[HMAC_KEY_LENGTH];
  
  /**
   * The opad byte array for use in hmac
   */
  public final static byte[] HMAC_OPAD = new byte[HMAC_KEY_LENGTH];
  
  /**
   * The array used for conversion to hexidecimal
   */
  public final static char[] HEX_ARRAY = new char[] {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
  
  /**
   * Initialize the ipad/opad buffers
   */
  static {
    Arrays.fill(HMAC_IPAD, HMAC_IPAD_BYTE);
    Arrays.fill(HMAC_OPAD, HMAC_OPAD_BYTE);
  }

  // ----- STATIC CIPHER OBJECTS -----

  /**
   * The cipher used to encrypt/decrypt data using DES
   */
  private static Cipher cipherSymmetric;

  /**
   * The cipher used to encrypt/decrypt data using RSA
   */
  private static Cipher cipherAsymmetric;
  private static Cipher deprecatedCipherAsymmetric;

  /**
   * The generator used to generate DES keys
   */
  private static KeyGenerator generatorSymmetric;

  /**
   * The generator used to generate RSA keys
   */
  private static KeyPairGenerator generatorAsymmetric;

  /**
   * The signature used for verification and signing data.
   */
  private static Signature signature;

  // ----- STATIC BLOCK TO INITIALIZE THE KEY GENERATORS -----

  static {
    // Add a provider for RSA encryption
    Security.insertProviderAt(new org.bouncycastle.jce.provider.BouncyCastleProvider(), 2);

    try {
      cipherSymmetric = Cipher.getInstance(SYMMETRIC_ALGORITHM);
      cipherAsymmetric = Cipher.getInstance(ASYMMETRIC_ALGORITHM);
      deprecatedCipherAsymmetric = Cipher.getInstance(DEPRECATED_ASYMMETRIC_ALGORITHM);
      generatorSymmetric = KeyGenerator.getInstance(SYMMETRIC_GENERATOR);
      generatorAsymmetric = KeyPairGenerator.getInstance(ASYMMETRIC_GENERATOR);
      signature = Signature.getInstance(SIGNATURE_ALGORITHM);
      
      generatorSymmetric.init(SYMMETRIC_KEY_LENGTH);
    } catch (NoSuchAlgorithmException e) {
      throw new SecurityException("NoSuchAlgorithmException on construction: " + e);
    } catch (NoSuchPaddingException e) {
      throw new SecurityException("NoSuchPaddingException on construction: " + e);
    }
  }

  /**
   * Make the constructor private so no SecurityUtils is ever created.
   */
  private SecurityUtils() {
  }

  /**
   * Utility method for serializing an object to a byte[].
   *
   * @param o The object to serialize
   * @return The byte[] of the object
   * @exception IOException If serialization does not happen properly
   */
  public static byte[] serialize(Object o) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new XMLObjectOutputStream(new BufferedOutputStream(new GZIPOutputStream(baos)));

    oos.writeObject(o);
    oos.flush();
    oos.close();
    
    return baos.toByteArray();
  }

  /**
   * Utility method for deserializing an object from a byte[]
   *
   * @param data The data to deserialize
   * @return The object
   * @exception IOException If deserialization does not happen properly
   * @exception ClassNotFoundException If the deserialized class is not found
   */
  public static Object deserialize(byte[] data) throws IOException, ClassNotFoundException {
    ByteArrayInputStream bais = new ByteArrayInputStream(data);
    ObjectInputStream ois = new XMLObjectInputStream(new BufferedInputStream(new GZIPInputStream(bais)));

    return ois.readObject();
  }

  /**
   * Utility method for determining the hash of a byte[] using a secure hashing
   * algorithm.
   *
   * @param input The input
   * @return The hash value
   * @exception SecurityException If the hashing does not happen properly
   */
  public static byte[] hash(byte[] input) throws SecurityException {
    MessageDigest md = null;

    try {
      md = MessageDigest.getInstance(HASH_ALGORITHM);
    } catch (NoSuchAlgorithmException e) {
      throw new SecurityException("Hash algorithm not found.");
    }

    md.update(input);
    return md.digest();
  }
  
  /**
   * Utility method for determining the apop of a challenge and password using a secure hashing
   * algorithm.
   *
   * @param password The password
   * @param challenge The challengr
   * @return The hash value
   * @exception SecurityException If the hashing does not happen properly
   */
  public static byte[] apop(byte[] challenge, byte[] password) throws SecurityException {
    MessageDigest md = null;
    
    try {
      md = MessageDigest.getInstance(APOP_ALGORITHM);
    } catch (NoSuchAlgorithmException e) {
      throw new SecurityException("APOP algorithm not found.");
    }
    
    md.update(challenge);
    md.update(password);
    return md.digest();
  }
  
  /**
   * Utility method for determining the hmac of a byte[] and key using a secure hashing
   * algorithm.
   *
   * @param text The text
   * @param key The key
   * @return The hmac value
   * @exception SecurityException If the hmacing does not happen properly
   */
  public static byte[] hmac(byte[] key, byte[] text) throws SecurityException {
    MessageDigest md1 = null, md2 = null;
    
    try {
      md1 = MessageDigest.getInstance(HMAC_ALGORITHM);
      md2 = MessageDigest.getInstance(HMAC_ALGORITHM);
    } catch (NoSuchAlgorithmException e) {
      throw new SecurityException("HMac algorithm not found.");
    }
    
    byte[] realKey = new byte[HMAC_KEY_LENGTH];
    System.arraycopy(key, 0, realKey, 0, (key.length < realKey.length ? key.length : realKey.length));
    
    md1.update(xor(realKey, HMAC_IPAD));
    md1.update(text);
    md2.update(xor(realKey, HMAC_OPAD));
    md2.update(md1.digest());
    return md2.digest();
  }
  
  /**
   * Utility method which xors two given byte arrays, of equal length, and returns the results
   *
   * @param a The first array
   * @param b The second array
   * @return A new byte array, xored
   */
  public static byte[] xor(byte[] a, byte[] b) {
    byte[] result = new byte[a.length];
    
    for (int i=0; i<result.length; i++) 
      result[i] = (byte) (a[i] ^ b[i]);

    return result;
  }
  
  /**
   * Utility method which converts a byte[] to a hexidecimal string of characters, in lower case
   *
   * @param text The array to convert
   * @return A string representation
   */
  public static String toHex(byte[] text) {
    StringBuffer buffer = new StringBuffer();
    
    for (int i=0; i<text.length; i++) {
      buffer.append(HEX_ARRAY[0x0f & (text[i] >> 4)]);
      buffer.append(HEX_ARRAY[0x0f & text[i]]);
    }
    
    return buffer.toString(); 
  }

  /**
   * Utility method for encrypting a block of data with symmetric encryption.
   *
   * @param data The data
   * @param key The key
   * @return The ciphertext
   * @exception SecurityException If the encryption does not happen properly
   */
  public static byte[] encryptSymmetric(byte[] data, byte[] key) throws SecurityException {
    try {
      synchronized (cipherSymmetric) {
        key = correctLength(key, SYMMETRIC_KEY_LENGTH);
        SecretKeySpec secretKey = new SecretKeySpec(key, SYMMETRIC_ALGORITHM);
        cipherSymmetric.init(Cipher.ENCRYPT_MODE, secretKey);

        return cipherSymmetric.doFinal(data);
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
   * Utility method for decrypting some data with symmetric encryption.
   *
   * @param data The data to decrypt
   * @param key The key
   * @return The decrypted data
   * @exception SecurityException If the decryption does not happen properly
   */
  public static byte[] decryptSymmetric(byte[] data, byte[] key) throws SecurityException {
    try {
      synchronized (cipherSymmetric) {
        key = correctLength(key, SYMMETRIC_KEY_LENGTH);
        SecretKeySpec secretKey = new SecretKeySpec(key, SYMMETRIC_ALGORITHM);
        cipherSymmetric.init(Cipher.DECRYPT_MODE, secretKey);

        return cipherSymmetric.doFinal(data);
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
   * Utility method for signing a block of data with the a private key
   *
   * @param data The data
   * @param key The key to use to sign
   * @return The signature
   * @exception SecurityException If the signing does not happen properly
   */
  public static byte[] sign(byte[] data, PrivateKey key) throws SecurityException {
    try {
      synchronized (signature) {
        signature.initSign(key);
        signature.update(hash(data));

        return signature.sign();
      }
    } catch (InvalidKeyException e) {
      throw new SecurityException("InvalidKeyException signing object: " + e);
    } catch (SignatureException e) {
      throw new SecurityException("SignatureException signing object: " + e);
    }
  }

  /**
   * Utility method for verifying a signature
   *
   * @param data The data to verify
   * @param sig The proposed signature
   * @param key The key to verify against
   * @return Whether or not the sig matches.
   * @exception SecurityException If the verification does not happen properly
   */
  public static boolean verify(byte[] data, byte[] sig, PublicKey key) throws SecurityException {
    try {
      synchronized (signature) {
        signature.initVerify(key);
        signature.update(hash(data));

        return signature.verify(sig);
      }
    } catch (InvalidKeyException e) {
      throw new SecurityException("InvalidKeyException verifying object: " + e);
    } catch (SignatureException e) {
      throw new SecurityException("SignatureException verifying object: " + e);
    }
  }

  /**
   * Encrypts the given byte[] using the provided public key. TO DO: Check
   * length of input
   *
   * @param data The data to encrypt
   * @param key The key to encrypt with
   * @return The encrypted data
   * @exception SecurityException If the encryption does not happen properly
   */
  public static byte[] encryptAsymmetric(byte[] data, PublicKey key) throws SecurityException {
    try {
      synchronized (cipherAsymmetric) {
        cipherAsymmetric.init(Cipher.ENCRYPT_MODE, key);

        return cipherAsymmetric.doFinal(data);
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
   * Decrypts the given byte[] using the provided private key. TO DO: Check
   * length of input
   *
   * @param data The data to decrypt
   * @param key The private key to use
   * @return The decrypted data
   * @exception SecurityException If the decryption does not happen properly
   */
  public static byte[] decryptAsymmetric(byte[] data, PrivateKey key) throws SecurityException {
    try {
      try {
        // First try normal way of decrypting
        synchronized (cipherAsymmetric) {
          cipherAsymmetric.init(Cipher.DECRYPT_MODE, key);

          return cipherAsymmetric.doFinal(data);
        }
      } catch (BadPaddingException e) {
        // Trying deprecated way of decrypting
        synchronized (deprecatedCipherAsymmetric) {
          deprecatedCipherAsymmetric.init(Cipher.DECRYPT_MODE, key);
          
          return deprecatedCipherAsymmetric.doFinal(data);
        }
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
   * Utility method which will generate a non-weak DES key for applications to
   * use.
   *
   * @return A new, random DES key
   */
  public static byte[] generateKeySymmetric() {
    return generatorSymmetric.generateKey().getEncoded();
  }

  /**
   * Utility method which will generate a non-weak DES key for applications to
   * use.
   *
   * @return A new, random DES key
   */
  public static KeyPair generateKeyAsymmetric() {
    return generatorAsymmetric.generateKeyPair();
  }


  /**
   * Tests the security service.
   *
   * @param argv The command line arguments
   * @exception NoSuchAlgorithmException If the encryption does not happen
   *      properly
   * @exception IOException If the encryption does not happen properly
   * @exception ClassNotFoundException If the encryption does not happen
   *      properly
   */
  public static void main(String[] argv) throws NoSuchAlgorithmException, IOException, ClassNotFoundException {
    System.out.println("SecurityUtils Test Suite");
    System.out.println("-------------------------------------------------------------");
    System.out.println("  Initializing Tests");
    System.out.print("    Generating key pairs\t\t\t\t");

    KeyPair pair = generateKeyAsymmetric();
    KeyPair pair2 = generateKeyAsymmetric();
    System.out.println("[ DONE ]");

    System.out.print("    Building cipher\t\t\t\t\t");

    System.out.println("[ DONE ]");
    System.out.println("-------------------------------------------------------------");
    System.out.println("  Running Tests");

    System.out.print("    Testing hexadecimal conversion\t\t\t");

    byte[] testHexBytes = new byte[] {(byte) 0xa7, (byte) 0xb3, (byte) 0x00, (byte) 0x12, (byte) 0x4e};
    String result = toHex(testHexBytes);
    
    if (result.equals("a7b300124e")) {
      System.out.println("[ PASSED ]");
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Input: \t" + testHexBytes);
      System.out.println("    Output:\t" + result);
    }
    
    System.out.print("    Testing long conversion\t\t\t\t");
    long testLong = Long.parseLong("0123456789ABCDEF", 16);

    byte[] testLongByte = getByteArray(testLong);

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
      System.out.println("    Output:\t" + testLongByte[0] + " " + testLongByte[1] + " " +
        testLongByte[2] + " " + testLongByte[3]);
    }

    System.out.print("    Testing serialization\t\t\t\t");
    String testString = "test";
    byte[] testStringByte = serialize(testString);
    String testStringOutput = (String) deserialize(testStringByte);

    if (testStringOutput.equals(testString)) {
      System.out.println("[ PASSED ]");
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Input: \t" + testString);
      System.out.println("    Output:\t" + testStringOutput);
    }

    System.out.print("    Testing hashing\t\t\t\t\t");
    byte[] testStringHash = hash(testStringByte);

    if ((testStringHash != null) && (testStringHash.length == 20)) {
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

    byte[] key = generateKeySymmetric();
    byte[] testStringCipherText = encryptSymmetric(testStringByte, key);
    byte[] testStringPlainText = decryptSymmetric(testStringCipherText, key);

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

    byte[] testStringSig = sign(testStringByte, pair.getPrivate());

    if (verify(testStringByte, testStringSig, pair.getPublic())) {
      System.out.println("[ PASSED ]");
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Input: \t" + testString);
      System.out.println("    Length:\t" + testStringByte.length);
      System.out.println("    Sig Len:\t" + testStringSig.length);
    }

    System.out.print("    Testing signing and verification (phase 2)\t\t");

    testStringSig[0]++;

    if (!verify(testStringByte, testStringSig, pair.getPublic())) {
      System.out.println("[ PASSED ]");
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Input: \t" + testString);
      System.out.println("    Length:\t" + testStringByte.length);
      System.out.println("    Sig Len:\t" + testStringSig.length);
    }

    System.out.print("    Testing asymmetric encryption\t\t\t");

    byte[] testStringEncrypted = encryptAsymmetric(testStringByte, pair.getPublic());
    byte[] testStringDecrypted = decryptAsymmetric(testStringEncrypted, pair.getPrivate());
    
    if (Arrays.equals(testStringByte, testStringDecrypted)) {
      System.out.println("[ PASSED ]");
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Input: \t" + testString);
      System.out.println("    Length:\t" + testStringByte.length);
      System.out.println("    Enc Len:\t" + testStringEncrypted.length);
      System.out.println("    Dec Len:\t" + testStringDecrypted.length);
    }
    
    System.out.print("    Testing hmac algorithm\t\t\t\t");
    
    String hmacText = "<1896.697170952@postoffice.reston.mci.net>";
    String hmacKey = "tanstaaftanstaaf";
    byte[] hmac = hmac(hmacKey.getBytes(), hmacText.getBytes());
    byte[] hmacResult = new byte[] {(byte) 0xb9, (byte) 0x13, (byte) 0xa6, (byte) 0x02, 
      (byte) 0xc7, (byte) 0xed, (byte) 0xa7, (byte) 0xa4, 
      (byte) 0x95, (byte) 0xb4, (byte) 0xe6, (byte) 0xe7, 
      (byte) 0x33, (byte) 0x4d, (byte) 0x38, (byte) 0x90};
    
    if (Arrays.equals(hmac, hmacResult)) {
      System.out.println("[ PASSED ]");
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Input: \t" + hmacText);
      System.out.println("    Key: \t" + hmacKey);
      System.out.println("    Res Len:\t" + hmac.length);
      System.out.println("    Real Len:\t" + hmacResult.length);
    }
    
    System.out.print("    Testing hmac algorithm again\t\t\t");
    
    String hmacText2 = "<1080369447214@The-Edge.local>";
    String hmacKey2 = "monkey";
    byte[] hmac2 = hmac(hmacKey2.getBytes(), hmacText2.getBytes());
    byte[] hmacResult2 = new byte[] {(byte) 0x9b, (byte) 0xae, (byte) 0x52, (byte) 0xef, 
      (byte) 0x55, (byte) 0x45, (byte) 0x24, (byte) 0x91, 
      (byte) 0x36, (byte) 0x85, (byte) 0x74, (byte) 0x72, 
      (byte) 0x21, (byte) 0xbb, (byte) 0x84, (byte) 0x22};
    
    if (Arrays.equals(hmac2, hmacResult2)) {
      System.out.println("[ PASSED ]");
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Input: \t" + hmacText2);
      System.out.println("    Key: \t" + hmacKey2);
      System.out.println("    Res Len:\t" + hmac2.length);
      System.out.println("    Real Len:\t" + hmacResult2.length);
    }
    
    System.out.print("    Testing asymmetic symmetric key encryption\t\t");

    byte[] keySym = generateKeySymmetric();
      
    if (Arrays.equals(encryptAsymmetric(keySym, pair.getPublic()), encryptAsymmetric(keySym, pair2.getPublic()))) {
      System.out.println("[ FAILED ]");
      System.out.println("    Input: \t" + toHex(keySym));
      System.out.println("    Output 1: \t" + toHex(encryptAsymmetric(keySym, pair.getPublic())));
      System.out.println("    Output 2: \t" + toHex(encryptAsymmetric(keySym, pair2.getPublic())));
    } else {
      System.out.println("[ PASSED ]");
    }

    System.out.println("-------------------------------------------------------------");
  }

  /**
   * Utility method for converting a long into a byte[]
   *
   * @param input The log to convert
   * @return a byte[] representation
   */
  public static byte[] getByteArray(long input) {
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
   * Utility method for converting a byte[] into a long
   *
   * @param input The byte[] to convert
   * @return a long representation
   */
  public static long getLong(byte[] input) {
    return ((((long)input[0]) << 56) & 0xFF00000000000000L) |
           ((((long)input[1]) << 48) & 0x00FF000000000000L) |
           ((((long)input[2]) << 40) & 0x0000FF0000000000L) |
           ((((long)input[3]) << 32) & 0x000000FF00000000L) |
           ((((long)input[4]) << 24) & 0x00000000FF000000L) |
           ((((long)input[5]) << 16) & 0x0000000000FF0000L) |
           ((((long)input[6]) <<  8) & 0x000000000000FF00L) |
           ((((long)input[7])      ) & 0x00000000000000FFL);
  }

  /**
   * Utility method for ensuring the array is of the proper length.  THis
   * method enforces the length by appending 0's or returning a subset of
   * the input array.
   *
   * @param data The input array
   * @param length The length the array should be
   * @return A correct-length array
   */
  private static byte[] correctLength(byte[] data, int length) {
    byte[] result = new byte[length];

    for (int i=0; (i<data.length) && (i<result.length); i++) {
      result[i] = data[i];
    }

    return result;
  }
}
