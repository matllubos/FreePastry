package rice.email.proxy;

import rice.post.*;
import rice.post.security.*;

import java.util.*;
import java.io.*;
import java.net.*;
import java.security.*;

/**
 * This class starts generates a new keypair for the certificate
 * authority, asks for a password, and encrypts the keypair under
 * the hash of the password into the provided filename.
 */
public class CertificateAuthorityKeyGenerator {

  public static int MIN_PASSWORD_LENGTH = 4;
  
  protected static String fetchPassword(String prompt) throws IOException {
    System.out.print(prompt + ": ");
    return password.getPassword();
  }
  
  public static String getPassword() throws IOException {
    String pass1 = fetchPassword("Please enter a password");

    if (pass1 == null) {
      System.out.println("Password must not be null.");
      return getPassword();
    }
  
    if (pass1.length() < MIN_PASSWORD_LENGTH) {
      System.out.println("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
      return getPassword();
    }

    String pass2 = fetchPassword("Please confirm the password");

    if (! pass1.equals(pass2)) {
      System.out.println("Passwords do not match.");
      return getPassword();
    }

    return pass1;
  }
  
  public static void main (String[] args) {
    try {
      System.out.println("POST Certificate Authority Key Generator");

      System.out.print("    Build a key pair generator\t\t\t\t\t");
      KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
      System.out.println("[ DONE ]");

      System.out.print("    Generating new key pair\t\t\t\t\t");
      KeyPair pair = kpg.generateKeyPair();
      System.out.println("[ DONE ]");

      System.out.println("    Getting password to encrypt keypair with\t\t\t\t");
      String password = getPassword();

      System.out.print("    Encrypting keypair\t\t\t\t\t\t");
      SecurityService security = new SecurityService(null, null);
      
      byte[] key = security.hash(password.getBytes());
      byte[] data = security.serialize(pair);
      byte[] cipher = security.encryptDES(data, key);
      System.out.println("[ DONE ]");
      
      FileOutputStream fos = new FileOutputStream("ca.keypair.enc");
      ObjectOutputStream oos = new ObjectOutputStream(fos);


      System.out.print("    Writing out encrypted keypair\t\t\t\t");
      oos.writeObject(cipher);

      oos.flush();
      oos.close();
      System.out.println("[ DONE ]");

      fos = new FileOutputStream("ca.publickey");
      oos = new ObjectOutputStream(fos);

      System.out.print("    Writing out public key\t\t\t\t\t");
      oos.writeObject(pair.getPublic());

      oos.flush();
      oos.close();
      System.out.println("[ DONE ]");
    } catch (Exception e) {
      System.out.println("Exception occured during construction " + e + " " + e.getMessage());
      e.printStackTrace();
    }
  }
}