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

import rice.testharness.*;
import rice.testharness.messaging.*;

import java.util.*;
import java.io.*;
import java.net.*;
import java.security.*;


/**
* A test class which picks a number of random node IDs and
* tests a pastry and direct ping to that NodeId.
*
* @version $Id$
*
* @author Alan Mislove
*/

public class PostTest extends Test {

  private Credentials _credentials = new PermissiveCredentials();

  private TestHarness thl;

  private NodeId nodeId;

  private Scribe scribe;

  private PASTService past;

  private Post post;

  private PostUserAddress address;

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
  public PostTest(PrintStream out, PastryNode localNode) {
    super(out, localNode);

    try {
      System.out.println("Post Test Suite");
      System.out.println("------------------------------------------------------------------");
      System.out.println("  Initializing Test");
      System.out.print("    Generating CA key pair\t\t\t\t\t");

      KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
      caPair = kpg.generateKeyPair();
      System.out.println("[ DONE ]");
      
      System.out.print("    Generating user key pair\t\t\t\t\t");
      pair = kpg.generateKeyPair();
      System.out.println("[ DONE ]");

      address = new PostUserAddress("test");                                              

      nodeId = localNode.getNodeId();

      StorageManager storage = new MemoryStorageManager();

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
      System.out.print("    Starting POST service\t\t\t\t\t");
      post = new Post(thePastryNode, past, scribe, address, pair, null, caPair.getPublic());
      System.out.println("[ DONE ]");

      System.out.println("Post test for node " + nodeId + " completed successfully.");
    } catch (Exception e) {
      System.out.println("Exception occured during testing: " + e + " " + e.getMessage());
    }
  }
 
  public Address getAddress() {
    return PostTestAddress.instance();
  }

  public Credentials getCredentials() {
    return _credentials;
  }

  public void messageForAppl(Message msg) {
  }

  public static class PostTestAddress implements Address {

    /**
    * The only instance of DumbTestAddress ever created.
     */
    private static PostTestAddress _instance;

    /**
    * Returns the single instance of TestHarnessAddress.
     */
    public static PostTestAddress instance() {
      if(null == _instance) {
        _instance = new PostTestAddress();
      }
      return _instance;
    }

    /**
      * Code representing address.
     */
    public int _code = 0x98834c66;

    /**
      * Private constructor for singleton pattern.
     */
    private PostTestAddress() {}

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
      return (obj instanceof PostTestAddress);
    }
  }  
}