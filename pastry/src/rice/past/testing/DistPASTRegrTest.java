package rice.past.testing;

import rice.past.*;
import rice.past.messaging.*;

import rice.pastry.*;
import rice.pastry.dist.*;
import rice.pastry.standard.*;
import rice.pastry.security.*;

import rice.storage.*;
import rice.storage.testing.*;

import ObjectWeb.Persistence.*;

import java.util.*;
import java.net.*;
import java.io.Serializable;

/**
 * Provides regression testing for the PAST service using distributed nodes.
 *
 * @version $Id$
 * @author Charles Reis
 */

public class DistPASTRegrTest {
  private DistPastryNodeFactory factory;
  private Vector pastrynodes;
  private Vector pastNodes;

  private Random rng;
  private RandomNodeIdFactory idFactory;

  private static int numNodes = 10;
  private static int k = 3;  // replication factor

  private static int port = 5009;
  private static String bshost;
  private static int bsport = 5009;

  private static int protocol = DistPastryNodeFactory.PROTOCOL_WIRE;

  static {
    try {
      bshost = InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      System.out.println("Error determining local host: " + e);
    }
  }

  public DistPASTRegrTest() {
    idFactory = new RandomNodeIdFactory();
    factory = DistPastryNodeFactory.getFactory(idFactory,
                                               protocol,
                                               port);
    pastrynodes = new Vector();
    pastNodes = new Vector();
    rng = new Random(5);
  }

  /**
   * Gets a handle to a bootstrap node.
   *
   * @return handle to bootstrap node, or null.
   */
  protected NodeHandle getBootstrap() {
    InetSocketAddress address = new InetSocketAddress(bshost, bsport);
    return factory.getNodeHandle(address);
  }

  public synchronized void pause(int ms) {
    System.out.println("waiting for " + (ms/1000) + " sec");
    try { wait(ms); } catch (InterruptedException e) {}
  }



  /* ---------- Setup methods ---------- */

  /**
   * Creates a pastryNode with a PASTService running on it.
   */
  protected PASTService makePASTNode() {
    PastryNode pn = factory.newNode(getBootstrap());
    pastrynodes.add(pn);

    PersistenceManager pm = new DummyPersistenceManager();
    StorageManager sm = new StorageManagerImpl(pm);

    PASTServiceImpl past = new PASTServiceImpl(pn, sm);
    past.DEBUG = true;
    pastNodes.add(past);
    System.out.println("created " + pn);

    return past;
  }

  /**
   * Creates the nodes used for testing.
   */
  protected void createNodes() {
    for (int i=0; i < numNodes; i++) {
      makePASTNode();
    }
  }

  /**
   * Sets up the environment for regression tests.
   */
  protected void initialize() {
    createNodes();

    // Give nodes a chance to initialize
    System.out.println("DEBUG ---------- Waiting for all nodes to be ready");
    pause(3000);

    Enumeration nodes = pastrynodes.elements();
    while (nodes.hasMoreElements()) {
      PastryNode node = (PastryNode) nodes.nextElement();
      while (!node.isReady()) {
        System.out.println("DEBUG ---------- Waiting for node to be ready");
        pause(1000);
      }
    }
  }


  /* ---------- Testing utility methods ---------- */

  /**
   * Throws an exception if the test condition is not met.
   */
  protected void assertTrue(String name, String intention, boolean test)
    throws TestFailedException
  {
    if (!test) {
      throw new TestFailedException("\nAssertion failed in '" + name +
                                    "'\nExpected: " + intention);
    }
  }

  /**
   * Thows an exception if expected is not equal to actual.
   */
  protected void assertEquals(String name,
                              String description,
                              Object expected,
                              Object actual)
    throws TestFailedException
  {
    if (!expected.equals(actual)) {
      throw new TestFailedException("\nAssertion failed in '" + name +
                                    "'\nDescription: " + description +
                                    "\nExpected: " + expected +
                                    "\nActual: " + actual);
    }
  }


  /* ---------- Test methods ---------- */

  /**
   * Tests routing a PAST request to a particular node.
   */
  protected void testRouteRequest() throws TestFailedException {
    PASTService local = (PASTService) pastNodes.elementAt(rng.nextInt(numNodes));
    PASTServiceImpl remote = (PASTServiceImpl) pastNodes.elementAt(rng.nextInt(numNodes));
    NodeId remoteId = remote.getPastryNode().getNodeId();
    Persistable file = new DummyPersistable("test file");

    // Check file does not exist
    assertTrue("RouteRequest", "File should not exist before insert",
               !local.exists(remoteId));

    // Insert file
    System.out.println("TEST: RouteRequest: Inserting file with key: " + remoteId);
    assertTrue("RouteRequest", "Insert of file should succeed",
               local.insert(remoteId, file, null));

    // Check file exists
    assertTrue("RouteRequest", "File should exist after insert",
               local.exists(remoteId));

    // Lookup file locally
    StorageObject result = remote.getStorage().lookup(remoteId);
    assertTrue("RouteRequest", "File should be inserted at known node",
               result != null);
    Persistable file2 = result.getOriginal();
    assertEquals("RouteRequest", "Retrieved local file should be the same",
                 file, file2);

  }

  /**
   * Tests inserting, updating, locating, and reclaiming a file.
   */
  protected void testPASTFunctions() throws TestFailedException {
    Credentials userCred = null;
    PASTService local = (PASTService) pastNodes.elementAt(rng.nextInt(numNodes));
    NodeId fileId = idFactory.generateNodeId();
    Persistable file = new DummyPersistable("test file");
    Persistable update = new DummyPersistable("update to file");

    // Try looking up before insert
    StorageObject test = local.lookup(fileId);
    assertTrue("PASTFunctions", "Lookup before insert should fail",
                 test == null);
    assertTrue("PASTFunctions", "File should not exist before insert",
               !local.exists(fileId));

    // Insert file
    System.out.println("TEST: PASTFunctions: Inserting file with key: " + fileId);
    assertTrue("PASTFunctions", "Insert of file should succeed",
               local.insert(fileId, file, userCred));
    assertTrue("PASTFunctions", "File should exist after insert",
               local.exists(fileId));

    // Try to insert again
    assertTrue("PASTFunctions", "Re-insert of file should fail",
               !local.insert(fileId, file, userCred));

    // Check file's presence on network
    int localCount = 0;
    for (int i=0; i < pastNodes.size(); i++) {
      PASTServiceImpl remote = (PASTServiceImpl) pastNodes.elementAt(i);

      // Lookup file remotely
      StorageObject result = remote.lookup(fileId);
      assertTrue("PASTFunctions", "File should always be found remotely",
                 result != null);
      Persistable file2 = result.getOriginal();
      assertEquals("PASTFunctions", "Retrieved file should be the same, node " + i,
                   file, file2);
      Vector updates = result.getUpdates();
      assertEquals("PASTFunctions", "Retrieved file should have no updates, node " + i,
                   new Integer(0), new Integer(updates.size()));

      // Lookup file locally
      result = remote.getStorage().lookup(fileId);
      if (result != null) {
        System.out.println("TEST: Found file locally on node " + i);
        localCount++;
        file2 = result.getOriginal();
        assertEquals("PASTFunctions", "Retrieved local file should be the same, node " + i,
                     file, file2);
      }
    }

    // TO DO: Make this k instead of 1 when ReplicationManager is used
    assertEquals("PASTFunctions", "File should have been found 1 time after insert",
                 new Integer(1), new Integer(localCount));



    // Append to file
    System.out.println("TEST: Appending to file with key: " + fileId);
    assertTrue("PASTFunctions", "File should update successfully",
               local.update(fileId, update, userCred));

    // Make sure updates were found
    for (int i=0; i < pastNodes.size(); i++) {
      PASTServiceImpl remote = (PASTServiceImpl) pastNodes.elementAt(i);

      // Lookup file remotely
      StorageObject result = remote.lookup(fileId);
      assertTrue("PASTFunctions", "File should always be found remotely",
                 result != null);
      Persistable file2 = result.getOriginal();
      assertEquals("PASTFunctions", "Retrieved file should be the same, node " + i,
                   file, file2);
      Vector updates = result.getUpdates();
      assertEquals("PASTFunctions", "Retrieved file should have 1 update, node " + i,
                   new Integer(1), new Integer(updates.size()));
      Persistable update2 = (Persistable) updates.elementAt(0);
      assertEquals("PASTFunctions", "Retrieved update should be the same, node " + i,
                   update, update2);
    }

    // Check file still exists
    assertTrue("PASTFunctions", "File should still exist after updates",
               local.exists(fileId));

    // Reclaim space used by file
    System.out.println("TEST: Reclaiming file with key: " + fileId);
    assertTrue("PASTFunctions", "File should be reclaimed successfully",
               local.delete(fileId, userCred));

    // Make sure file is gone
    for (int i=0; i < pastNodes.size(); i++) {
      PASTServiceImpl remote = (PASTServiceImpl) pastNodes.elementAt(i);

      // Lookup file remotely
      StorageObject result = remote.lookup(fileId);
      assertTrue("PASTFunctions", "File should not be found remotely, node " + i,
                 result == null);
    }

    // Check file does not exist
    assertTrue("PASTFunctions", "File should not exist after delete",
               !local.exists(fileId));
  }


  /**
   * Initializes and runs all regression tests.
   */
  public void runTests() {
    initialize();

    try {
      // Run each test
      testRouteRequest();
      testPASTFunctions();

      // TO DO:
      //  Test permissions (problems with serializability of dummy credentials?)
      //  Test timeout

      System.out.println("\n\nDEBUG-All tests passed!---------------------\n");
    }
    catch (TestFailedException e) {
      System.out.println("\n\nDEBUG-Test Failed!--------------------------\n");
      System.out.println(e.toString());
      System.out.println("\n\n--------------------------------------------\n");
    }
  }

  /**
   * process command line args
   */
  private static void doInitstuff(String args[]) {
    // process command line arguments

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-help")) {
        System.out.println("Usage: DistPASTSearchRegrTest [-port p] [-protocol (rmi|wire)] [-bootstrap host[:port]] [-help]");
        System.exit(1);
      }
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-port") && i+1 < args.length) {
        int p = Integer.parseInt(args[i+1]);
        if (p > 0) port = p;
        break;
      }
    }

    bsport = port;  // make sure bsport = port, if no -bootstrap argument is provided
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-bootstrap") && i+1 < args.length) {
        String str = args[i+1];
        int index = str.indexOf(':');
        if (index == -1) {
          bshost = str;
          bsport = port;
        } else {
          bshost = str.substring(0, index);
          bsport = Integer.parseInt(str.substring(index + 1));
          if (bsport <= 0) bsport = port;
        }
        break;
      }
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-protocol") && i+1 < args.length) {
        String s = args[i+1];

        if (s.equalsIgnoreCase("wire"))
          protocol = DistPastryNodeFactory.PROTOCOL_WIRE;
        else if (s.equalsIgnoreCase("rmi"))
          protocol = DistPastryNodeFactory.PROTOCOL_RMI;
        else
          System.out.println("ERROR: Unsupported protocol: " + s);

        break;
      }
    }
  }


  /**
   * Usage: DistPASTTest [-port p] [-bootstrap host[:port]] [-nodes n] [-protocol (rmi|wire)] [-help]
   */
  public static void main(String args[]) {

    doInitstuff(args);
    DistPASTRegrTest pastTest = new DistPASTRegrTest();
    pastTest.runTests();

  }


  /**
   * Exception indicating that a regression test failed.
   */
  protected class TestFailedException extends Exception {
    protected TestFailedException(String message) {
      super(message);
    }
  }

}
