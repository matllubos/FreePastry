package rice.p2p.util.testing;

import rice.p2p.util.*;
import java.io.*;
import java.math.*;

import java.security.*;
import java.security.cert.*;
import java.security.spec.*;
import java.util.*;
import java.util.zip.*;
import javax.crypto.*;
import javax.crypto.spec.*;

public class SecurityUtilsUnit {

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

    KeyPair pair = SecurityUtils.generateKeyAsymmetric();
    KeyPair pair2 = SecurityUtils.generateKeyAsymmetric();
    System.out.println("[ DONE ]");

    System.out.print("    Building cipher\t\t\t\t\t");

    System.out.println("[ DONE ]");
    System.out.println("-------------------------------------------------------------");
    System.out.println("  Running Tests");

    System.out.print("    Testing hexadecimal conversion\t\t\t");

    byte[] testHexBytes = new byte[] {(byte) 0xa7, (byte) 0xb3, (byte) 0x00, (byte) 0x12, (byte) 0x4e};
    String result = MathUtils.toHex(testHexBytes);
    
    if (result.equals("a7b300124e")) {
      System.out.println("[ PASSED ]");
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Input: \t" + testHexBytes);
      System.out.println("    Output:\t" + result);
    }
    
    System.out.print("    Testing long conversion\t\t\t\t");
    long testLong = Long.parseLong("0123456789ABCDEF", 16);

    byte[] testLongByte = MathUtils.getByteArray(testLong);

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
    byte[] testStringByte = SecurityUtils.serialize(testString);
    String testStringOutput = (String) SecurityUtils.deserialize(testStringByte);

    if (testStringOutput.equals(testString)) {
      System.out.println("[ PASSED ]");
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Input: \t" + testString);
      System.out.println("    Output:\t" + testStringOutput);
    }

    System.out.print("    Testing hashing\t\t\t\t\t");
    byte[] testStringHash = SecurityUtils.hash(testStringByte);

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

    byte[] key = SecurityUtils.generateKeySymmetric();
    byte[] testStringCipherText = SecurityUtils.encryptSymmetric(testStringByte, key);
    byte[] testStringPlainText = SecurityUtils.decryptSymmetric(testStringCipherText, key);

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

    byte[] testStringSig = SecurityUtils.sign(testStringByte, pair.getPrivate());

    if (SecurityUtils.verify(testStringByte, testStringSig, pair.getPublic())) {
      System.out.println("[ PASSED ]");
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Input: \t" + testString);
      System.out.println("    Length:\t" + testStringByte.length);
      System.out.println("    Sig Len:\t" + testStringSig.length);
    }

    System.out.print("    Testing signing and verification (phase 2)\t\t");

    testStringSig[0]++;

    if (! SecurityUtils.verify(testStringByte, testStringSig, pair.getPublic())) {
      System.out.println("[ PASSED ]");
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Input: \t" + testString);
      System.out.println("    Length:\t" + testStringByte.length);
      System.out.println("    Sig Len:\t" + testStringSig.length);
    }

    System.out.print("    Testing asymmetric encryption\t\t\t");

    byte[] testStringEncrypted = SecurityUtils.encryptAsymmetric(testStringByte, pair.getPublic());
    byte[] testStringDecrypted = SecurityUtils.decryptAsymmetric(testStringEncrypted, pair.getPrivate());
    
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
    byte[] hmac = SecurityUtils.hmac(hmacKey.getBytes(), hmacText.getBytes());
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
    byte[] hmac2 = SecurityUtils.hmac(hmacKey2.getBytes(), hmacText2.getBytes());
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

    byte[] keySym = SecurityUtils.generateKeySymmetric();
      
    if (Arrays.equals(SecurityUtils.encryptAsymmetric(keySym, pair.getPublic()), 
                      SecurityUtils.encryptAsymmetric(keySym, pair2.getPublic()))) {
      System.out.println("[ FAILED ]");
      System.out.println("    Input: \t" + MathUtils.toHex(keySym));
      System.out.println("    Output 1: \t" + MathUtils.toHex(SecurityUtils.encryptAsymmetric(keySym, pair.getPublic())));
      System.out.println("    Output 2: \t" + MathUtils.toHex(SecurityUtils.encryptAsymmetric(keySym, pair2.getPublic())));
    } else {
      System.out.println("[ PASSED ]");
    }

    System.out.println("-------------------------------------------------------------");
  }
}