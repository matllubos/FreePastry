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

  public static String default_base_address = "rice.epostmail.org";
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
  
  public static void print(boolean web, String string) {
    if (! web)
      System.out.print(string);
  }  
  
  public static void println(boolean web, String string) {
    print(web, string + "\n");
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
      println(web, "POST Certificate Generator");
      BufferedReader input = new BufferedReader(new InputStreamReader(System.in));  

      File f = new File(caDirectory,"ca.keypair.enc");      
      FileInputStream fis = new FileInputStream(f);
      ObjectInputStream ois = new XMLObjectInputStream(new BufferedInputStream(new GZIPInputStream(fis)));

      print(web, "    Reading in encrypted keypair\t\t\t\t");
      byte[] cipher = (byte[]) ois.readObject();

      println(web, "[ DONE ]");

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
        
        print(web, "    Decrypting keypair\t\t\t\t\t\t");
        key = SecurityUtils.hash(pass.getBytes());
        data = SecurityUtils.decryptSymmetric(cipher, key);
      } catch (SecurityException e) {
        throw new IOException("Password for CA keypair was incorrect.");
      }
      
      KeyPair caPair = (KeyPair) SecurityUtils.deserialize(data);
      println(web, "[ DONE ]");
      
      
      while(!done) {
      
        if (base_address == null) {
          print(web, "Please enter the base address ["+default_base_address+"]: ");
          base_address = input.readLine();
         
          if (base_address.equals("")) 
            base_address = default_base_address;
          
          default_base_address = base_address;
        }
              
        if (userid == null) {
          print(web, "Please enter the new username (@"+base_address+"): ");
          userid = input.readLine();
        }
        
        if (ring == null) {        
          print(web, "Please enter the ring name ["+default_ring+"]: ");
          ring = input.readLine();
        }
                
        if (ring.equals(""))
          ring = default_ring;
        
        default_ring = ring;
  
        print(web, "    Generating new key pair\t\t\t\t\t");
        KeyPair pair = SecurityUtils.generateKeyAsymmetric();
        println(web, "[ DONE ]");
  
        IdFactory realFactory = new PastryIdFactory();
        Id ringId = realFactory.buildId(ring);
        byte[] ringData = ringId.toByteArray();
        
        for (int i=0; i<ringData.length - MultiringNodeCollection.BASE; i++) 
          ringData[i] = 0;
        
        ringId = realFactory.buildId(ringData);
        
        PostUserAddress address = new PostUserAddress(new MultiringIdFactory(ringId, realFactory), userid + "@"+base_address);
        
        print(web, "    Generating the certificate " + address.getAddress() + "\t");
        PostCertificate certificate = CASecurityModule.generate(address, pair.getPublic(), caPair.getPrivate());
        println(web, "[ DONE ]");
        
        if (password == null) {
          println(web, "    Getting password to encrypt keypair with\t\t\t\t");
          password = CAKeyGenerator.getPassword();
        }
        
        String filename = userid + ".epost";
        File dir = new File(".");
        if (web) {
          dir = new File("certificates/d" + (new SecureRandom()).nextInt(Integer.MAX_VALUE));
          
          if (! dir.mkdir()) 
            throw new IOException("Could not create directory " + dir); 
        }
  
        print(web, "    Writing out certificate to '" + userid + ".epost'\t\t");
        
        writeFile(certificate, pair, password, new File(dir, filename));
  
        println(web, "[ DONE ]");
  
        if (web) {
          System.out.println("<a href=\""+webPrefix+dir.getName()+"/"+filename+"\">"+filename+"</a>");
          done = true;
        } else {
          print(web, "Create another key? y/n [No]: ");
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
  
  public static void writeFile(PostCertificate cert, KeyPair keypair, String password, File file) throws IOException {
    ObjectOutputStream oos = null;
    
    try {
      oos = new XMLObjectOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(file))));
      oos.writeObject(cert);
    
      byte[] cipher = SecurityUtils.encryptSymmetric(SecurityUtils.serialize(keypair), SecurityUtils.hash(password.getBytes()));
      oos.writeInt(cipher.length);
      oos.write(cipher);
    } finally {
      oos.close();
    }
  }
  
  public static PostCertificate readCertificate(File file) throws IOException, ClassNotFoundException {
    ObjectInputStream ois = null;
    
    try {
      ois = new XMLObjectInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(file))));
    
      return (PostCertificate) ois.readObject();
    } finally {
      ois.close();
    }
  }
  
  public static KeyPair readKeyPair(File file, String password) throws IOException, SecurityException, ClassNotFoundException {
    ObjectInputStream ois = null;
    
    try {
      ois = new XMLObjectInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(file))));
      ois.readObject();
    
      int length = ois.readInt();
      byte[] cipher = new byte[length];
      ois.readFully(cipher);
    
      return (KeyPair) SecurityUtils.deserialize(SecurityUtils.decryptSymmetric(cipher, SecurityUtils.hash(password.getBytes())));
    } finally {
      ois.close();
    }
  }
  
  public static void updateFile(File certificate, File keypair, File file) throws IOException, ClassNotFoundException {
    ObjectInputStream ois = null;
    ObjectOutputStream oos = null;
    PostCertificate cert = null;
    byte[] cipher = null;

    try {
      ois = new XMLObjectInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(certificate))));
      cert = (PostCertificate) ois.readObject();
    } finally {
      ois.close();
    }

    try {
      ois = new XMLObjectInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(keypair))));
      cipher = (byte[]) ois.readObject();
    } finally {
      ois.close();
    }
    
    try {
      oos = new XMLObjectOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(file))));
      oos.writeObject(cert);
      oos.writeInt(cipher.length);
      oos.write(cipher);
    } finally {
      oos.close();
    }
  }
}
