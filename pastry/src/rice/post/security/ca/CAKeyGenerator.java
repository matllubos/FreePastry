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

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

import rice.post.*;
import rice.post.security.*;
import rice.p2p.util.*;

/**
 * This class starts generates a new keypair for the certificate authority, asks
 * for a password, and encrypts the keypair under the hash of the password into
 * the provided filename.
 *
 * @version $Id$
 * @author amislove
 */
public class CAKeyGenerator {

  /**
   * The minimum length of a password.
   */
  public static int MIN_PASSWORD_LENGTH = 4;

  /**
   * Returns a echo-off password from the command line
   *
   * @return The Password value
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  public static String getPassword() throws IOException {
    String pass1 = fetchPassword("Please enter a password");

    if (pass1 == null) {
      System.out.println("Password must not be null.");
      return getPassword();
    }

    if (pass1.length() < MIN_PASSWORD_LENGTH) {
      System.out.println("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
      return getPassword();
    }

    String pass2 = fetchPassword("Please confirm the password");

    if (!pass1.equals(pass2)) {
      System.out.println("Passwords do not match.");
      return getPassword();
    }

    return pass1;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param prompt DESCRIBE THE PARAMETER
   * @return DESCRIBE THE RETURN VALUE
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  public static String fetchPassword(final String prompt) throws IOException {
 /*   System.out.print(prompt + ": ");
//    return password.getPassword();
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    return reader.readLine(); */
    JFrame frame = new JFrame("Password Input");
    JPanel panel = new JPanel(new GridLayout(2, 2));
    JPasswordField field = new JPasswordField();
    JLabel label = new JLabel(prompt, SwingConstants.LEFT);
    JButton button = new JButton("Enter");
    panel.add(label);
    panel.add(field);
    panel.add(new JLabel("", SwingConstants.LEFT));
    panel.add(button);
    frame.getContentPane().add(panel);
    frame.setSize(400,75);
    frame.setResizable(false);
    frame.setVisible(true);

    field.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent arg0) {
        synchronized(prompt) {
          prompt.notify();  
        }
      }
    });


    button.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        synchronized (prompt) {
          prompt.notify();
        }
      }
    });
    
    synchronized (prompt) {
      if (field.getPassword().length == 0) 
        try {
          prompt.wait();
        } catch (Exception e) {
        }
    }
    
    frame.setVisible(false);
    return new String(field.getPassword());
  }

  /**
   * The main program for the CertificateAuthorityKeyGenerator class
   *
   * @param args The command line arguments
   */
  public static void main(String[] args) {
    try {
      System.out.println("POST Certificate Authority Key Generator");

      System.out.print("    Generating new key pair\t\t\t\t\t");
      KeyPair pair = SecurityUtils.generateKeyAsymmetric();
      System.out.println("[ DONE ]");

      System.out.println("    Getting password to encrypt keypair with\t\t\t\t");
      String password = getPassword();

      System.out.print("    Encrypting keypair\t\t\t\t\t\t");
      byte[] key = SecurityUtils.hash(password.getBytes());
      byte[] data = SecurityUtils.serialize(pair);
      byte[] cipher = SecurityUtils.encryptSymmetric(data, key);
      System.out.println("[ DONE ]");

      FileOutputStream fos = new FileOutputStream("ca.keypair.enc");
      ObjectOutputStream oos = new XMLObjectOutputStream(new BufferedOutputStream(new GZIPOutputStream(fos)));

      System.out.print("    Writing out encrypted keypair\t\t\t\t");
      oos.writeObject(cipher);

      oos.flush();
      oos.close();
      System.out.println("[ DONE ]");

      fos = new FileOutputStream("ca.publickey");
      oos = new XMLObjectOutputStream(new BufferedOutputStream(new GZIPOutputStream(fos)));

      System.out.print("    Writing out public key\t\t\t\t\t");
      oos.writeObject(pair.getPublic());

      oos.flush();
      oos.close();
      System.out.println("[ DONE ]");
    } catch (Exception e) {
      System.out.println("Exception occured during construction " + e + " " + e.getMessage());
      e.printStackTrace();
    }
  }
}
