package rice.post.security.ca;

import java.io.*;
import java.net.*;
import java.util.zip.*;
import java.security.*;

import java.util.*;

import rice.p2p.commonapi.*;
import rice.p2p.multiring.*;

import rice.post.*;
import rice.post.security.*;

import rice.pastry.commonapi.*;
import rice.serialization.*;

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
      ObjectInputStream ois = new XMLObjectInputStream(new BufferedInputStream(new GZIPInputStream(fis)));

      System.out.print("    Reading in encrypted keypair\t\t\t\t");
      byte[] cipher = (byte[]) ois.readObject();
      System.out.println("[ DONE ]");

      String pass;
      byte[] key = null;
      byte[] data = null;

      try {
        pass = CAKeyGenerator.fetchPassword("Please enter the password");
        
        System.out.print("    Decrypting keypair\t\t\t\t\t\t");
        key = SecurityUtils.hash(pass.getBytes());
        data = SecurityUtils.decryptSymmetric(cipher, key);
      }catch (SecurityException e) {
        System.out.println("Incorrect Password! Exiting...");
        System.exit(-1);
      }
      
      KeyPair caPair = (KeyPair) SecurityUtils.deserialize(data);
      System.out.println("[ DONE ]");
    
      System.out.print("Please enter the new username (@dosa.cs.rice.edu): ");
      BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
      String userid = input.readLine();
      
      System.out.print("Please enter the ring name [Rice]: ");
      String ring = input.readLine();
      
      if (ring.equals(""))
        ring = "Rice";

      System.out.print("    Generating new key pair\t\t\t\t\t");
      KeyPair pair = SecurityUtils.generateKeyAsymmetric();
      System.out.println("[ DONE ]");

      IdFactory realFactory = new PastryIdFactory();
      Id ringId = realFactory.buildId(ring);
      byte[] ringData = ringId.toByteArray();
      
      for (int i=0; i<ringData.length - MultiringNodeCollection.BASE; i++) 
        ringData[i] = 0;
      
      ringId = realFactory.buildId(ringData);
      
      PostUserAddress address = new PostUserAddress(new MultiringIdFactory(ringId, realFactory), userid + "@dosa.cs.rice.edu");
      System.out.print("    Generating the certificate " + address.getAddress() + "\t");
      PostCertificate certificate = CASecurityModule.generate(address, pair.getPublic(), caPair.getPrivate());
      System.out.println("[ DONE ]");

      FileOutputStream fos = new FileOutputStream(userid + ".certificate");
      ObjectOutputStream oos = new XMLObjectOutputStream(new BufferedOutputStream(new GZIPOutputStream(fos)));

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
      oos = new XMLObjectOutputStream(new BufferedOutputStream(new GZIPOutputStream(fos)));

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
