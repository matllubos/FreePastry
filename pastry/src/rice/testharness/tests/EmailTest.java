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

package rice.testharness.tests;

import rice.pastry.client.*;
import rice.pastry.leafset.*;
import rice.pastry.security.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.pastry.*;
import rice.pastry.direct.*;
import rice.pastry.wire.*;
import rice.pastry.standard.*;

import rice.scribe.*;
import rice.scribe.messaging.*;

import rice.past.*;

import rice.storage.*;

import rice.post.*;
import rice.post.security.*;

import rice.email.*;
import rice.email.proxy.*;

import rice.testharness.*;
import rice.testharness.messaging.*;

import java.util.*;
import java.io.*;
import java.net.*;
import java.security.*;


/**
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */

public class EmailTest extends Test {

  private Credentials _credentials = new PermissiveCredentials();

  private TestHarness thl;

  private NodeId nodeId;

  private Scribe scribe;

  private PASTService past;

  private Post post;

  private EmailService email;

  private PostUserAddress address;

  private PostCertificate certificate;

  private KeyPair pair;

  private KeyPair caPair;

  /**
    * Constructor which takes the local node this test is on,
   * an array of all the nodes in the network, and a printwriter
   * to which to write data.
   *
   * @param out The PrintWriter to write test results to.
   * @param localNode The local Pastry node
   * @param nodes NodeHandles to all of the other participating
   *              TestHarness nodes (this test class ignores these)
   */
  public EmailTest(PrintStream out, PastryNode localNode) {
    super(out, localNode);

    try {
      System.out.println("Email Test Suite");
      System.out.println("------------------------------------------------------------------");
      System.out.println("  Initializing Test");
      System.out.print("    Retrieving CA key pair\t\t\t\t\t");

      KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");

      FileInputStream fis = new FileInputStream("capair.txt");
      ObjectInputStream ois = new ObjectInputStream(fis);

      caPair = (KeyPair) ois.readObject();
      
      System.out.println("[ DONE ]");

      WireNodeHandle handle = (WireNodeHandle) localNode.getLocalHandle();

      System.out.print("    Generating user address\t\t\t\t\t");
      address = new PostUserAddress("<test@" + InetAddress.getLocalHost().getHostAddress() + ">");
      System.out.println("[ DONE ]");
      
      System.out.print("    Generating user key pair\t\t\t\t\t");
      pair = kpg.generateKeyPair();
      System.out.println("[ DONE ]");

      System.out.print("    Generating user certificate\t\t\t\t\t");
      SecurityService security = new SecurityService(null, null);
      certificate = security.generateCertificate(address, pair.getPublic(), caPair.getPrivate());
      System.out.println("[ DONE ]");

      nodeId = localNode.getNodeId();

      System.out.print("    Starting StorageManager\t\t\t\t\t");
      StorageManager storage = new MemoryStorageManager();
      System.out.println("[ DONE ]");

      System.out.print("    Starting SCRIBE service\t\t\t\t\t");
      scribe = new Scribe(localNode, _credentials);
      System.out.println("[ DONE ]");

      System.out.print("    Starting PAST service\t\t\t\t\t");
      past = new PASTServiceImpl(localNode, storage);
      System.out.println("[ DONE ]");

    } catch (Exception e) {
      System.out.println("Exception occured during construction " + e + " " + e.getMessage());
      e.printStackTrace();
    }
  }

  /**
    * Method which is called when the TestHarness wants this
   * Test to begin testing.
   */
  public void startTest(final TestHarness thl, NodeId[] nodes) {
    try {
      System.out.println();
      System.out.print("    Starting POST service\t\t\t\t\t");
      post = new Post(thePastryNode, past, scribe, address, pair, certificate, caPair.getPublic());
      System.out.println("[ DONE ]");

      System.out.print("    Starting Email service\t\t\t\t\t");
      email = new EmailService(post);
      System.out.println("[ DONE ]");

      System.out.print("    Starting IMAP server\t\t\t\t\t");
      IMAPProxy imp = new IMAPProxy(InetAddress.getByName("10.0.0.2"), 143);

      BufferedReader r = new BufferedReader(new FileReader("password"));
      String pass = r.readLine();

      imp.attach(email, "dwp", pass);
      System.out.println("[ DONE ]");

      System.out.print("    Starting SMTP server\t\t\t\t\t");
      SMTPProxy smp = new SMTPProxy(InetAddress.getByName("localhost"), 11235);
      System.out.println("[ DONE ]");
      
      smp.attach(email);

      System.err.println("SMTP proxy started.");      

      Thread t = new Thread() {
        public void run() {
          try {
            while (true) {
              post.announcePresence();
              Thread.sleep(2000);
            }
          } catch (InterruptedException e) {
            System.out.println("INTERRUPTED: " + e);
          }
        }
      };

      t.start();

      Thread.sleep(5000);
      
      System.out.println("Post test for node " + nodeId + " completed successfully.");
    } catch (Exception e) {
      System.out.println("Exception occured during testing: " + e + " " + e.getMessage());
    }
  }

  public Address getAddress() {
    return EmailTestAddress.instance();
  }

  public Credentials getCredentials() {
    return _credentials;
  }

  public void messageForAppl(Message msg) {
  }

  public static class EmailTestAddress implements Address {

    /**
    * The only instance of DumbTestAddress ever created.
     */
    private static EmailTestAddress _instance;

    /**
    * Returns the single instance of TestHarnessAddress.
     */
    public static EmailTestAddress instance() {
      if(null == _instance) {
        _instance = new EmailTestAddress();
      }
      return _instance;
    }

    /**
      * Code representing address.
     */
    public int _code = 0x9393784a;

    /**
      * Private constructor for singleton pattern.
     */
    private EmailTestAddress() {}

    /**
      * Returns the code representing the address.
     */
    public int hashCode() { return _code; }

    /**
      * Determines if another object is equal to this one.
     * Simply checks if it is an instance of AP3Address
     * since there is only one instance ever created.
     */
    public boolean equals(Object obj) {
      return (obj instanceof EmailTestAddress);
    }
  }  
}