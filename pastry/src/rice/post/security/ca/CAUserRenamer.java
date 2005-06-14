package rice.post.security.ca;

import java.io.*;
import java.net.*;
import java.util.zip.*;
import java.security.*;

import java.util.*;

import rice.environment.Environment;
import rice.p2p.commonapi.*;
import rice.p2p.multiring.*;

import rice.post.*;
import rice.post.security.*;

import rice.pastry.commonapi.*;
import rice.p2p.util.*;

/**
 * This class starts generates a new certificate for the given username using
 * the provided CA keypair.
 *
 * @version $Id$
 * @author amislove
 */
public class CAUserRenamer {
  
  public static String default_base_address = "rice.epostmail.org";
  public static String default_ring = "Rice";
  
  public static KeyPair getKeyPair(String username) throws Exception {
    FileInputStream fis = new FileInputStream(username + ".keypair.enc");
    ObjectInputStream ois = new XMLObjectInputStream(new BufferedInputStream(new GZIPInputStream(fis)));
    
    byte[] cipher = (byte[]) ois.readObject();
    
    String pass = CAKeyGenerator.fetchPassword("Please enter the " + username + "'s password");
      
    byte[] key = SecurityUtils.hash(pass.getBytes());
    byte[] data = SecurityUtils.decryptSymmetric(cipher, key);
    
    return (KeyPair) SecurityUtils.deserialize(data);
  }
  
  /**
    * The main program for the CertificateGenerator class
   *
   * @param args The command line arguments
   */
  public static void main(String[] args) throws IOException{
    boolean done = false;
    Environment env = new Environment();
    try {
      System.out.println("POST User Renamer");
      
      System.out.print("    Reading in CA's keypair\t\t\t\t");
      KeyPair caPair = getKeyPair("ca");
      System.out.println("[ DONE ]");
      
      while (!done) {
        System.out.print("Please enter the old username (@dosa.cs.rice.edu): ");
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        String oldUserid = input.readLine();
        
        String pass = CAKeyGenerator.fetchPassword("Please enter " + oldUserid + "'s password");
        
        System.out.print("    Reading in " + oldUserid + "'s keypair\t\t\t\t");
        KeyPair pair = CACertificateGenerator.readKeyPair(new File(oldUserid + ".epost"), pass);
        System.out.println("[ DONE ]");
                                                          
        (new File(oldUserid + ".epost")).renameTo(new File(oldUserid + ".epost.old"));

        System.out.print("Please enter the new base address ["+default_base_address+"]: ");
        String base_address = input.readLine();
        if (base_address.equals("")) 
          base_address = default_base_address;
      
        default_base_address = base_address;
        
        System.out.print("Please enter the new username (@"+base_address+"): ");
        String userid = input.readLine();
        
        System.out.print("Please enter the new ring name ["+default_ring+"]: ");
        String ring = input.readLine();
        
        if (ring.equals(""))
          ring = default_ring;
        
        default_ring = ring;
        
        IdFactory realFactory = new PastryIdFactory(env);
        Id ringId = realFactory.buildId(ring);
        byte[] ringData = ringId.toByteArray();
        
        for (int i=0; i<ringData.length - MultiringNodeCollection.BASE; i++) 
          ringData[i] = 0;
        
        ringId = realFactory.buildId(ringData);
        
        PostUserAddress address = new PostUserAddress(new MultiringIdFactory(ringId, realFactory), userid + "@"+base_address);
        System.out.print("    Generating the certificate " + address.getAddress() + "\t");
        PostCertificate certificate = CASecurityModule.generate(address, pair.getPublic(), caPair.getPrivate());
        System.out.println("[ DONE ]");
                
        System.out.print("    Writing out certificate to '" + userid + ".certificate'\t\t");
        CACertificateGenerator.writeFile(certificate, pair, pass, new File(userid + ".epost"));
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
