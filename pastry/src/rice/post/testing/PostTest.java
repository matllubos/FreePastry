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

package rice.post.testing;

import java.util.*;
import java.security.*;

import rice.*;

import rice.p2p.commonapi.*;
import rice.p2p.past.*;

import rice.persistence.*;

import rice.pastry.*;
import rice.pastry.commonapi.*;
import rice.pastry.direct.*;
import rice.pastry.standard.*;
import rice.pastry.security.*;

import rice.post.*;
import rice.post.security.*;
import rice.post.security.ca.*;

import rice.scribe.*;

/**
 * Provides regression testing setup for applications written on top of
 * Post.  
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public abstract class PostTest {

  // ----- VARAIBLES -----
  
  // the collection of nodes which have been created
  protected PastryNode[] nodes;

  // the collection of PAST
  protected PastImpl[] pasts;

  // the collection of Scribe nodes
  protected Scribe[] scribes;

  // the collection of POST nodes
  protected PostImpl[] posts;

  // the collection of user addresses
  protected PostUserAddress[] addresses;

  // the collection of user certificates
  protected PostCertificate[] certificates;


  // ----- PASTRY VARIABLES -----

  // the factory for creating pastry nodes
  protected PastryNodeFactory factory;

  // the factory for creating random node ids
  protected NodeIdFactory idFactory;

  // the network simulator we are running on top of
  protected NetworkSimulator simulator;

  // ----- POST VARIABLES -----

  // the keypair used by everyone
  protected KeyPair keyPair;
  

  // ----- STATIC FIELDS -----

  // the number of nodes to create
  public static int NUM_NODES = 20;

  // the instance name to use
  public static String INSTANCE_NAME = "PostTest";

  // the factory for protocol-specific ids
  protected static IdFactory FACTORY = new PastryIdFactory();

  // the replication factor for Past
  protected static int REPLICATION_FACTOR = 4;

  // the pastry credentials
  protected static Credentials CREDENTIALS = new PermissiveCredentials();


  // ----- TESTING SPECIFIC FIELDS -----

  // the text to print to the screen
  public static final String SUCCESS = "SUCCESS";
  public static final String FAILURE = "FAILURE";

  // the width to pad the output
  protected static final int PAD_SIZE = 60;
  
  
  // ----- EXTERNALLY AVAILABLE METHODS -----
  
  /**
   * Constructor, which takes no arguments and sets up the
   * factories in preparation for node creation.
   */
  public PostTest() {
    idFactory = new RandomNodeIdFactory();
    simulator = new SphereNetwork();
    factory = new DirectPastryNodeFactory(idFactory, simulator);
    keyPair = SecurityUtils.generateKeyAsymmetric();

    nodes = new PastryNode[NUM_NODES];
    pasts = new PastImpl[NUM_NODES];
    scribes = new Scribe[NUM_NODES];
    posts = new PostImpl[NUM_NODES];
    addresses = new PostUserAddress[NUM_NODES];
    certificates = new PostCertificate[NUM_NODES];
  }

  /**
   * Method which starts the creation of nodes
   */
  public void start() {
    for (int i=0; i<NUM_NODES; i++) {
      nodes[i] = createNode(i);

      simulate();

      posts[i] = createPostNode(i, nodes[i]);

      processNode(i);

      simulate();

      System.out.println("Created node " + i + " with id " + ((PastryNode) nodes[i]).getNodeId());
    }

    System.out.println("\nTest Beginning\n");
    
    runTest();
  }

  /**
   * Method which simulates message passing
   */
  public void simulate() {
    while (simulator.simulate()) {}
  }


  // ----- INTERNAL METHODS -----

  /**
   * Method which creates a single node, given it's node
   * number
   *
   * @param num The number of creation order
   * @return The created node
   */
  protected PastryNode createNode(int num) {
    if (num == 0) {
      return factory.newNode(null);
    } else {
      return factory.newNode(getBootstrap());
    }
  }

  /**
   * Gets a handle to a bootstrap node.
   *
   * @return handle to bootstrap node, or null.
   */
  protected rice.pastry.NodeHandle getBootstrap() {
    return nodes[0].getLocalHandle();
  }

  /**
   * Method which creates a single post node, given it's node
   * number and pastry node
   *
   * @param num The number of creation order
   * @param node The node
   * @return The created node
   */
  protected PostImpl createPostNode(int num, PastryNode node) {
    try {
      StorageManager sm = new StorageManager(FACTORY,
                                             new MemoryStorage(FACTORY),
                                             new LRUCache(new MemoryStorage(FACTORY), 1000000));

      PastImpl past = new PastImpl(node, sm, REPLICATION_FACTOR, INSTANCE_NAME);
      pasts[num] = past;

      Scribe scribe = new Scribe(node, CREDENTIALS);
      scribes[num] = scribe;

      PostUserAddress address = new PostUserAddress("USER" + num);
      addresses[num] = address;

      PostCertificate certificate = CASecurityModule.generate(address, keyPair.getPublic(), keyPair.getPrivate());
      certificates[num] = certificate;

      return new PostImpl(node, past, scribe, address, keyPair, certificate, keyPair.getPublic(), INSTANCE_NAME);
    } catch (PostException e) {
      System.out.println("EXCEPTION " + e + " thrown while in createPostNode.");
      return null;
    }
  }

  // ----- METHODS TO BE PROVIDED BY IMPLEMENTATIONS -----

  /**
   * Method which should process the given newly-created node
   *
   * @param num The number o the node
   * @param node The newly created node
   */
  protected abstract void processNode(int num);

  /**
   * Method which should run the test - this is called once all of the
   * nodes have been created and are ready.
   */
  protected abstract void runTest();
  

  // ----- TESTING UTILITY METHODS -----

  /**
   * Method which prints the beginning of a test section.
   *
   * @param name The name of section
   */
  protected final void sectionStart(String name) {
    System.out.println(name);
  }

  /**
   * Method which prints the end of a test section.
   */
  protected final void sectionDone() {
    System.out.println();
  }

  /**
   * Method which prints the beginning of a test section step.
   *
   * @param name The name of step
   */
  protected final void stepStart(String name) {
    System.out.print(pad("  " + name));
  }

  /**
   * Method which prints the end of a test section step, with an
   * assumed success.
   */
  protected final void stepDone() {
    stepDone(SUCCESS);
  }

  /**
   * Method which prints the end of a test section step.
   *
   * @param status The status of step
   */
  protected final void stepDone(String status) {
    stepDone(status, "");
  }

  /**
   * Method which prints the end of a test section step, as
   * well as a message.
   *
   * @param status The status of section
   * @param message The message
   */
  protected final void stepDone(String status, String message) {
    System.out.println("[" + status + "]");

    if ((message != null) && (! message.equals(""))) {
      System.out.println("     " + message);
    }

    if(status.equals(FAILURE))
      System.exit(0);
  }

  /**
   * Method which prints an exception which occured during testing.
   *
   * @param e The exception which was thrown
   */
  protected final void stepException(Exception e) {
    System.out.println("\nException " + e + " occurred during testing.");

    e.printStackTrace();
    System.exit(0);
  }

  /**
   * Method which pads a given string with "." characters.
   *
   * @param start The string
   * @return The result.
   */
  private final String pad(String start) {
    if (start.length() >= PAD_SIZE) {
      return start.substring(0, PAD_SIZE);
    } else {
      int spaceLength = PAD_SIZE - start.length();
      char[] spaces = new char[spaceLength];
      Arrays.fill(spaces, '.');

      return start.concat(new String(spaces));
    }
  }

  /**
   * Throws an exception if the test condition is not met.
   */
  protected final void assertTrue(String intention, boolean test) {
    if (!test) {
      stepDone(FAILURE, "Assertion '" + intention + "' failed.");
    }
  }

  /**
   * Thows an exception if expected is not equal to actual.
   */
  protected final void assertEquals(String description,
                                    Object expected,
                                    Object actual) {
    if (!expected.equals(actual)) {
      stepDone(FAILURE, "Assertion '" + description +
               "' failed, expected: '" + expected +
               "' got: " + actual + "'");
    }
  }
  

  // ----- COMMAND LINE PARSING METHODS -----
  
  /**
   * process command line args
   */
  protected static void parseArgs(String args[]) {
    // process command line arguments

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-help")) {
        System.out.println("Usage: PostTest [-nodes n]");
        System.exit(1);
      }
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-nodes") && i+1 < args.length) {
        int p = Integer.parseInt(args[i+1]);
        if (p > 0) NUM_NODES = p;
        break;
      }
    }
  }
}
