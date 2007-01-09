/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
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
  public static void main(String[] args) {
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
        
        for (int i=0; i<ringData.length - env.getParameters().getInt("p2p_multiring_base"); i++) 
          ringData[i] = 0;
        
        ringId = realFactory.buildId(ringData);
        
        PostUserAddress address = new PostUserAddress(new MultiringIdFactory(ringId, realFactory), userid + "@"+base_address, env);
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
