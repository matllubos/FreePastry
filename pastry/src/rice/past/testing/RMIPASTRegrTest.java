package rice.past.testing;

import rice.past.*;
import rice.past.messaging.*;

import rice.pastry.*;
import rice.pastry.rmi.*;
import rice.pastry.standard.*;
import rice.pastry.security.*;

import rice.storage.*;
import rice.storage.testing.*;

import ObjectWeb.Persistence.*;

import java.util.*;
import java.net.*;
import java.rmi.Naming;
import java.rmi.RMISecurityManager;
import java.io.Serializable;

/**
 * Provides regression testing for the PAST service using RMI.
 *
 * @version $Id$
 * @author Charles Reis
 */

public class RMIPASTRegrTest {
  private PastryNodeFactory factory;
  private Vector pastrynodes;
  private Vector pastNodes;
  
  private Random rng;
  private RandomNodeIdFactory idFactory;
  
  private static int numNodes = 10;
  private static int k = 3;  // replication factor
  
  private static int port = 5009;
  private static String bshost = "localhost";
  private static int bsport = 5009;
  
  public RMIPASTRegrTest() {
    factory = new RMIPastryNodeFactory(port);
    pastrynodes = new Vector();
    pastNodes = new Vector();
    rng = new Random(5);
    idFactory = new RandomNodeIdFactory();
  }
  
  /**
   * Gets a handle to a bootstrap node. First tries localhost, to see
   * whether a previous virtual node has already bound itself. Then it
   * tries nattempts times on bshost:bsport.
   *
   * @return handle to bootstrap node, or null.
   */
  protected NodeHandle getBootstrap() {
    RMIRemoteNodeI bsnode = null;
    try {
      bsnode = (RMIRemoteNodeI)Naming.lookup("//:" + port + "/Pastry");
    } catch (Exception e) {
      System.out.println("Unable to find bootstrap node on localhost");
    }
    
    int nattempts = 3;
    
    // if bshost:bsport == localhost:port then nattempts = 0.
    // waiting for ourselves is not harmful, but pointless, and denies
    // others the usefulness of symmetrically waiting for us.
    
    if (bsport == port) {
      InetAddress localaddr = null, connectaddr = null;
      String host = null;
      
      try {
        host = "localhost"; localaddr = InetAddress.getLocalHost();
        connectaddr = InetAddress.getByName(host = bshost);
      } catch (UnknownHostException e) {
        System.out.println("[rmi] Error: Host unknown: " + host);
        nattempts = 0;
      }
      
      if (nattempts != 0 && localaddr.equals(connectaddr))
        nattempts = 0;
    }
    
    for (int i = 1; bsnode == null && i <= nattempts; i++) {
      try {
        bsnode = (RMIRemoteNodeI)Naming.lookup("//" + bshost
                                                 + ":" + bsport
                                                 + "/Pastry");
      } catch (Exception e) {
        System.out.println("Unable to find bootstrap node on "
                             + bshost + ":" + bsport
                             + " (attempt " + i + "/" + nattempts + ")");
      }
      
      if (i != nattempts)
        pause(1000);
    }
    
    NodeId bsid = null;
    if (bsnode != null) {
      try {
        bsid = bsnode.getNodeId();
      } catch (Exception e) {
        System.out.println("[rmi] Unable to get remote node id: " + e.toString());
        bsnode = null;
      }
    }
    
    RMINodeHandle bshandle = null;
    if (bsid != null)
      bshandle = new RMINodeHandle(bsnode, bsid);
    
    return bshandle;
  }

  /**
   * process command line args, set the RMI security manager, and start
   * the RMI registry. Standard gunk that has to be done for all RMI apps.
   */
  private static void doRMIinitstuff(String args[]) {
    // process command line arguments
    
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-help")) {
        System.out.println("Usage: RMIPASTRegrTest [-port p] [-bootstrap host[:port]] [-nodes n] [-help]");
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
      if (args[i].equals("-nodes") && i+1 < args.length) {
        int n = Integer.parseInt(args[i+1]);
        if (n > 0) numNodes = n;
        break;
      }
    }
    
    // set RMI security manager
    
    if (System.getSecurityManager() == null)
      System.setSecurityManager(new RMISecurityManager());
    
    // start RMI registry
    
    try {
      java.rmi.registry.LocateRegistry.createRegistry(port);
    } catch (Exception e) {
      System.out.println("Error starting RMI registry: " + e);
    }
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
    
    // Insert file
    System.out.println("TEST: RouteRequest: Inserting file with key: " + remoteId);
    assertTrue("RouteRequest", "Insert of file should succeed",
               local.insert(remoteId, file, null));
    
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
    
    // Insert file
    System.out.println("TEST: PASTFunctions: Inserting file with key: " + fileId);
    assertTrue("PASTFunctions", "Insert of file should succeed",
               local.insert(fileId, file, userCred));
    
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
   * Usage: RMIPASTTest [-port p] [-bootstrap host[:port]] [-nodes n] [-help]
   */
  public static void main(String args[]) {

    doRMIinitstuff(args);
    RMIPASTRegrTest pastTest = new RMIPASTRegrTest();
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
