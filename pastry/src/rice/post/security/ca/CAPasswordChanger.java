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
import rice.p2p.util.*;

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
      
      System.out.print("Please enter the username (the part before the @ in your email address): ");
      BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
      String userid = input.readLine();
      
      String oldpass = CAKeyGenerator.fetchPassword("Please enter the old password");
      String password = CAKeyGenerator.getPassword();
      
      changePassword(userid, oldpass, password);
      
      // force exit despite potential daemon threads
      System.exit(0);
        
    } catch (Exception e) {
      System.out.println("Exception occured during construction " + e + " " + e.getMessage());
      e.printStackTrace();
    }
  }
  
  public static void changePassword(String username, String oldpass, String newpass) throws Exception {
    File file = new File(username + ".epost");
    PostCertificate cert = CACertificateGenerator.readCertificate(file);
    KeyPair pair = CACertificateGenerator.readKeyPair(file, oldpass);
    File tmp = new File(username + ".epost.tmp");
    file.renameTo(tmp);
      
    CACertificateGenerator.writeFile(cert, pair, newpass, file);
    tmp.delete();
  }
}
