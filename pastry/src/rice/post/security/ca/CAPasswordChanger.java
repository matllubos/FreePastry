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
 * This class starts changes the password on an existing certificate
 *
 * @version $Id$
 * @author amislove
 */
public class CAPasswordChanger {
  
  /**
  * The main program for the CertificateGenerator class
   *
   * @param args The command line arguments
   */
  public static void main(String[] args) {
    try {
      System.out.println("POST Certificate Password Changer");
      
      System.out.print("Please enter the username (@dosa.cs.rice.edu): ");
      BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
      String userid = input.readLine();
      
      FileInputStream fis = new FileInputStream(userid + ".keypair.enc");
      ObjectInputStream ois = new XMLObjectInputStream(new BufferedInputStream(new GZIPInputStream(fis)));
      
      System.out.print("    Reading in encrypted keypair\t\t\t\t");
      byte[] cipher = (byte[]) ois.readObject();
      System.out.println("[ DONE ]");
      
      String pass;
      byte[] key = null;
      byte[] data = null;
      
      try {
        pass = CAKeyGenerator.fetchPassword("Please enter the old password");
        
        System.out.print("    Decrypting keypair\t\t\t\t\t\t");
        key = SecurityUtils.hash(pass.getBytes());
        data = SecurityUtils.decryptSymmetric(cipher, key);
      }catch (SecurityException e) {
        System.out.println("Incorrect Password! Exiting...");
        System.exit(-1);
      }
      
      KeyPair pair = (KeyPair) SecurityUtils.deserialize(data);
      System.out.println("[ DONE ]");
      
      System.out.println("    Getting password to encrypt keypair with\t\t\t\t");
      String password = CAKeyGenerator.getPassword();
      
      System.out.print("    Encrypting keypair\t\t\t\t\t\t");
      key = SecurityUtils.hash(password.getBytes());
      data = SecurityUtils.serialize(pair);
      cipher = SecurityUtils.encryptSymmetric(data, key);
      System.out.println("[ DONE ]");
      
      FileOutputStream fos = new FileOutputStream(userid + ".keypair.enc");
      ObjectOutputStream oos = new XMLObjectOutputStream(new BufferedOutputStream(new GZIPOutputStream(fos)));
      
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
