package rice.post.security;

import rice.post.*;
import rice.post.security.*;

import java.util.*;
import java.io.*;
import java.net.*;
import java.security.*;

/**
 * This class starts generates a new certificate for the given username
 * using the provided CA keypair.
 */
public class CertificateGenerator {

  public static void main (String[] args) {
    try {
      System.out.println("POST Certificate Generator");

      FileInputStream fis = new FileInputStream("ca.keypair.enc");
      ObjectInputStream ois = new ObjectInputStream(fis);
      
      System.out.print("    Reading in encrypted keypair\t\t\t\t");
      byte[] cipher = (byte[]) ois.readObject();
      System.out.println("[ DONE ]");

      String pass = CertificateAuthorityKeyGenerator.fetchPassword("Please enter the password");

      System.out.print("    Decrypting keypair\t\t\t\t\t\t");
      byte[] key = SecurityService.hash(pass.getBytes());
      byte[] data = SecurityService.decryptDES(cipher, key);

      KeyPair caPair = (KeyPair) SecurityService.deserialize(data);
      System.out.println("[ DONE ]");

      System.out.print("Please enter the new username (@rice.edu.post): ");
      BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
      String userid = input.readLine();

      System.out.print("    Generating new key pair\t\t\t\t\t");
      KeyPair pair = SecurityService.generateKeyRSA();
      System.out.println("[ DONE ]");

      System.out.print("    Generating the certificate\t\t\t\t\t");
      PostUserAddress address = new PostUserAddress(userid + "@rice.edu.post");
      PostCertificate certificate = SecurityService.generateCertificate(address, pair.getPublic(), caPair.getPrivate());
      System.out.println("[ DONE ]");

      FileOutputStream fos = new FileOutputStream(userid + ".certificate");
      ObjectOutputStream oos = new ObjectOutputStream(fos);

      System.out.print("    Writing out certificate to '" + userid + ".certificate'\t\t");
      oos.writeObject(certificate);

      oos.flush();
      oos.close();
      System.out.println("[ DONE ]");      

      System.out.println("    Getting password to encrypt keypair with\t\t\t\t");
      String password = CertificateAuthorityKeyGenerator.getPassword();

      System.out.print("    Encrypting keypair\t\t\t\t\t\t");
      key = SecurityService.hash(password.getBytes());
      data = SecurityService.serialize(pair);
      cipher = SecurityService.encryptDES(data, key);
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