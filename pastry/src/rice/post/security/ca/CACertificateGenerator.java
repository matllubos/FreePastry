package rice.post.security.ca;

import java.io.*;
import java.net.*;
import java.security.*;

import java.util.*;

import rice.post.*;
import rice.post.security.*;

/**
 * This class starts generates a new certificate for the given username using
 * the provided CA keypair.
 *
 * @version $Id$
 * @author amislove
 */
public class CACertificateGenerator {

  /**
   * The main program for the CertificateGenerator class
   *
   * @param args The command line arguments
   */
  public static void main(String[] args) {
    try {
      System.out.println("POST Certificate Generator");

      FileInputStream fis = new FileInputStream("ca.keypair.enc");
      ObjectInputStream ois = new ObjectInputStream(fis);

      System.out.print("    Reading in encrypted keypair\t\t\t\t");
      byte[] cipher = (byte[]) ois.readObject();
      System.out.println("[ DONE ]");

      String pass = CAKeyGenerator.fetchPassword("Please enter the password");

      System.out.print("    Decrypting keypair\t\t\t\t\t\t");
      byte[] key = SecurityUtils.hash(pass.getBytes());
      byte[] data = SecurityUtils.decryptSymmetric(cipher, key);

      KeyPair caPair = (KeyPair) SecurityUtils.deserialize(data);
      System.out.println("[ DONE ]");

      System.out.print("Please enter the new username (@rice.edu.post): ");
      BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
      String userid = input.readLine();

      System.out.print("    Generating new key pair\t\t\t\t\t");
      KeyPair pair = SecurityUtils.generateKeyAsymmetric();
      System.out.println("[ DONE ]");

      System.out.print("    Generating the certificate\t\t\t\t\t");
      PostUserAddress address = new PostUserAddress(userid + "@rice.edu.post");
      PostCertificate certificate = CASecurityModule.generate(address, pair.getPublic(), caPair.getPrivate());
      System.out.println("[ DONE ]");

      FileOutputStream fos = new FileOutputStream(userid + ".certificate");
      ObjectOutputStream oos = new ObjectOutputStream(fos);

      System.out.print("    Writing out certificate to '" + userid + ".certificate'\t\t");
      oos.writeObject(certificate);

      oos.flush();
      oos.close();
      System.out.println("[ DONE ]");

      System.out.println("    Getting password to encrypt keypair with\t\t\t\t");
      String password = CAKeyGenerator.getPassword();

      System.out.print("    Encrypting keypair\t\t\t\t\t\t");
      key = SecurityUtils.hash(password.getBytes());
      data = SecurityUtils.serialize(pair);
      cipher = SecurityUtils.encryptSymmetric(data, key);
      System.out.println("[ DONE ]");

      fos = new FileOutputStream(userid + ".keypair.enc");
      oos = new ObjectOutputStream(fos);

      System.out.print("    Writing out encrypted keypair\t\t\t\t");
      oos.writeObject(cipher);

      oos.flush();
      oos.close();
      System.out.println("[ DONE ]");
    } catch (Exception e) {
      System.out.println("Exception occured during construction " + e + " " + e.getMessage());
      e.printStackTrace();
    }
  }
}
