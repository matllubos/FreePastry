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

  public static String getArg(String[] args, String argType) {
    for (int i = 0; i < args.length; i++) {
      if (args[i].startsWith(argType)) {
        if (args.length > i+1) {
          String ret = args[i+1];
          if (!ret.startsWith("-"))
            return ret;
        } 
      } 
    } 
    return null;
  }

  public static boolean getFlagArg(String[] args, String argType) {
    for (int i = 0; i < args.length; i++) {
      if (args[i].startsWith(argType)) {
        return true;
      } 
    } 
    return false;
  }

  /**
   * The main program for the CertificateGenerator class
   *
   * @param args The command line arguments
   */
  public static void main(String[] args) {
    String base_address = getArg(args,"-baseaddr");
    String userid = getArg(args,"-username");
    String password = getArg(args,"-password");
    String ring = getArg(args,"-ring");
    String caDirectory = getArg(args,"-ca");
    boolean web = getFlagArg(args,"-web");
    String webPrefix = getArg(args,"-webprefix");
    
    boolean done = false;
    try {
      BufferedReader input = new BufferedReader(new InputStreamReader(System.in));  
      if (!web)
        System.out.println("POST Certificate Generator");

      File f = new File(caDirectory,"ca.keypair.enc");      
      FileInputStream fis = new FileInputStream(f);
      ObjectInputStream ois = new XMLObjectInputStream(new BufferedInputStream(new GZIPInputStream(fis)));

      if (!web)
        System.out.print("    Reading in encrypted keypair\t\t\t\t");
      byte[] cipher = (byte[]) ois.readObject();
      if (!web)
        System.out.println("[ DONE ]");

      String pass = null;
      byte[] key = null;
      byte[] data = null;

      if (caDirectory != null) {
        File pwFile = new File(caDirectory,"pw"); 
        if (pwFile.exists()) {
          FileInputStream fis2 = new FileInputStream(pwFile);
          Reader r = new BufferedReader(new InputStreamReader(fis2));
          StreamTokenizer st = new StreamTokenizer(r);
          st.nextToken();
          pass = st.sval;
        }
      }

      try {
        if (pass == null) 
          pass = CAKeyGenerator.fetchPassword("Please enter the password");
        
        if (!web)
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
      if (!web)
        System.out.println("[ DONE ]");
      while(!done) {
      
        if (base_address == null) {
          System.out.print("Please enter the base address ["+default_base_address+"]: ");
          base_address = input.readLine();
          if (base_address.equals("")) {
            base_address = default_base_address;
          }
          default_base_address = base_address;
        }
              
        if (userid == null) {
          System.out.print("Please enter the new username (@"+base_address+"): ");
          userid = input.readLine();
        }
        
        if (ring == null) {        
          System.out.print("Please enter the ring name ["+default_ring+"]: ");
          ring = input.readLine();
        }
                
        if (ring.equals(""))
          ring = default_ring;
        
        default_ring = ring;
  
        if (!web)
          System.out.print("    Generating new key pair\t\t\t\t\t");
        KeyPair pair = SecurityUtils.generateKeyAsymmetric();
        if (!web)
          System.out.println("[ DONE ]");
  
        IdFactory realFactory = new PastryIdFactory();
        Id ringId = realFactory.buildId(ring);
        byte[] ringData = ringId.toByteArray();
        
        for (int i=0; i<ringData.length - MultiringNodeCollection.BASE; i++) 
          ringData[i] = 0;
        
        ringId = realFactory.buildId(ringData);
        
        PostUserAddress address = new PostUserAddress(new MultiringIdFactory(ringId, realFactory), userid + "@"+base_address);
        if (!web)
          System.out.print("    Generating the certificate " + address.getAddress() + "\t");
        PostCertificate certificate = CASecurityModule.generate(address, pair.getPublic(), caPair.getPrivate());
        if (!web)
          System.out.println("[ DONE ]");
        
        File certFile = null;
        FileOutputStream fos;
        String randomDirectory = null;
        if (web) {
//          Random rand = new Random();
          SecureRandom rand = new SecureRandom();
          randomDirectory = "d"+rand.nextInt(Integer.MAX_VALUE);
          File randDir = new File(randomDirectory);
          if (!randDir.mkdir()) {
            System.out.println("Could not create directory "+randomDirectory+" "+randDir.getAbsolutePath()+" "+randDir.getAbsolutePath()); 
          }
          certFile = new File(randomDirectory,userid + ".certificate");
          fos = new FileOutputStream(certFile);
        } else {
          fos = new FileOutputStream(userid + ".certificate");
        }
        
        ObjectOutputStream oos = new XMLObjectOutputStream(new BufferedOutputStream(new GZIPOutputStream(fos)));
  
        if (!web)
          System.out.print("    Writing out certificate to '" + userid + ".certificate'\t\t");
        oos.writeObject(certificate);
  
        oos.flush();
        oos.close();
        if (!web)
          System.out.println("[ DONE ]");
  
        if (password == null) {
          System.out.println("    Getting password to encrypt keypair with\t\t\t\t");
          password = CAKeyGenerator.getPassword();
        }
          
        if (!web)
          System.out.print("    Encrypting keypair\t\t\t\t\t\t");
        key = SecurityUtils.hash(password.getBytes());
        data = SecurityUtils.serialize(pair);
        cipher = SecurityUtils.encryptSymmetric(data, key);
        if (!web)
          System.out.println("[ DONE ]");
  
        File keyFile = null;
        if (web) {
          keyFile = new File(randomDirectory, userid + ".keypair.enc");
          fos = new FileOutputStream(keyFile);
        } else {
          fos = new FileOutputStream(userid + ".keypair.enc");          
        }
        oos = new XMLObjectOutputStream(new BufferedOutputStream(new GZIPOutputStream(fos)));
  
        if (!web)
          System.out.print("    Writing out encrypted keypair\t\t\t\t");
        oos.writeObject(cipher);
  
        oos.flush();
        oos.close();
        if (!web)
          System.out.println("[ DONE ]");
        if (web) {
          System.out.println("Certificate available at <A HREF=\""+webPrefix+""+randomDirectory+"/"+certFile.getName()+"\">"+certFile.getName()+"</A>");
          System.out.println("Keypair available at <A HREF=\""+webPrefix+""+randomDirectory+"/"+keyFile.getName()+"\">"+keyFile.getName()+"</A>");
          done = true;
        } else {
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
      }        
    } catch (Exception e) {
      System.out.println("Exception occured during construction " + e + " " + e.getMessage());
      e.printStackTrace();
    }
    System.exit(0);
  }
}
