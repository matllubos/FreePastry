/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate 

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/
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
