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

  public static String default_base_address = "dosa.cs.rice.edu";
  public static String default_ring = "Rice";

  /**
   * The main program for the CertificateGenerator class
   *
   * @param args The command line arguments
   */
  public static void main(String[] args) {
    boolean done = false;
    try {
      BufferedReader input = new BufferedReader(new InputStreamReader(System.in));  
      System.out.println("POST Certificate Generator");

      File f = new File("ca.keypair.enc");      
      FileInputStream fis = new FileInputStream(f);
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
        e.printStackTrace();
        System.out.println("Key file was "+f.getAbsolutePath());
        System.exit(-1);
      }
      
      KeyPair caPair = (KeyPair) SecurityUtils.deserialize(data);
      System.out.println("[ DONE ]");
      while(!done) {
      
        System.out.print("Please enter the base address ["+default_base_address+"]: ");
        String base_address = input.readLine();
        if (base_address.equals("")) {
          base_address = default_base_address;
        }
        default_base_address = base_address;
      
        System.out.print("Please enter the new username (@"+base_address+"): ");
        String userid = input.readLine();
        
        System.out.print("Please enter the ring name ["+default_ring+"]: ");
        String ring = input.readLine();
        
        if (ring.equals(""))
          ring = default_ring;
        
        default_ring = ring;
  
        System.out.print("    Generating new key pair\t\t\t\t\t");
        KeyPair pair = SecurityUtils.generateKeyAsymmetric();
        System.out.println("[ DONE ]");
  
        IdFactory realFactory = new PastryIdFactory();
        Id ringId = realFactory.buildId(ring);
        byte[] ringData = ringId.toByteArray();
        
        for (int i=0; i<ringData.length - MultiringNodeCollection.BASE; i++) 
          ringData[i] = 0;
        
        ringId = realFactory.buildId(ringData);
        
        PostUserAddress address = new PostUserAddress(new MultiringIdFactory(ringId, realFactory), userid + "@"+base_address);
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

        System.out.print("Create another key? y/n [No]: ");
        String another = input.readLine();
        
        if (another.equals(""))
          another = "No";
        
        if (another.startsWith("n") || another.startsWith("N")) {
          done = true;
        } else {
          done = false;
        }
        

      }        
    } catch (Exception e) {
      System.out.println("Exception occured during construction " + e + " " + e.getMessage());
      e.printStackTrace();
    }
    System.exit(0);
  }
}
