package rice.ap3.testing;

import rice.ap3.*;
import rice.ap3.messaging.*;
import rice.ap3.routing.*;

import rice.pastry.*;
import rice.pastry.rmi.*;
import rice.pastry.standard.*;

import java.util.*;
import java.net.*;
import java.rmi.Naming;
import java.rmi.RMISecurityManager;

/**
 * Provides regressin testing for the AP3 service using RMI.
 *
 * @version $Id$
 * @author Charlie Reis
 * @author Gaurav Oberoi
 */

public class RMIAP3RegrTest {
  private PastryNodeFactory factory;
  private Vector pastrynodes;
  private Vector ap3Nodes;

  //private AP3TestingClient _originator, _forwarder, _fetcher;
  private AP3TestingClient _nodeA, _nodeB, _nodeC;
  private static final double DEFAULT_FETCH_PROB = -1;  //  Will be ignored by testing clients
  private static final long DEFAULT_TIMEOUT = 20000;  // Very long...

  private Random rng;

  private static int port = 5009;
  private static String bshost = "localhost";
  private static int bsport = 5009;

  public RMIAP3RegrTest() {
    factory = new RMIPastryNodeFactory(port);
    pastrynodes = new Vector();
    ap3Nodes = new Vector();
    rng = new Random();
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
	System.out.println("Usage: RMIAP3RegrTest [-port p] [-bootstrap host[:port]] [-help]");
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
   * Creates a pastryNode, and each one it creates
   * an AP3TestingClient and an AP3TestingService for that client.
   */
  protected AP3TestingClient makeAP3Node() {
    PastryNode pn = factory.newNode(getBootstrap());
    pastrynodes.add(pn);

    AP3TestingClient ap3Client = new AP3TestingClient(pn);
    ap3Nodes.addElement(ap3Client);
    System.out.println("created " + pn);

    return ap3Client;
  }

  /**
   * Creates the nodes used for testing.
   */
  protected void createNodes() {
    _nodeA = makeAP3Node();
    _nodeB = makeAP3Node();
    _nodeC = makeAP3Node();
  }

  /**
   * Initializes a given AP3 node.
   */
  protected void initNode(AP3TestingClient client, double fetchProb, NodeId dest) {
    AP3TestingService service = client.getService();
    service.setFetchProbability(fetchProb);
    service.setDestinationNode(dest);
    service.getAP3RoutingTable().clear();

    // Force nodes to suspend before calling routeMsg
    service.suspendRouteMsg = true;

    client.clearCache();
  }

  /**
   * Sets up the environment for regression tests.
   */
  protected void initialize() {
    createNodes();

    Enumeration nodes = pastrynodes.elements();
    while (nodes.hasMoreElements()) {
      PastryNode node = (PastryNode) nodes.nextElement();
      while (!node.isReady()) {
        pause(1000);
        System.out.println("DEBUG ---------- Waiting for node to be ready");
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
      throw new TestFailedException("\nAssertion failed in '" + name + "'\nExpected: " + intention);
    }
  }

  /**
   * Thows an exception if expected is not equal to actual.
   */
  protected void assertEquals(String name, String description, Object expected, Object actual)
    throws TestFailedException
  {
    if (!expected.equals(actual)) {
      throw new TestFailedException("\nAssertion failed in '" + name + "'\nDescription: " +
				    description + "\nExpected: " + expected + "\nActual: " + actual);
    }
  }


  /* ---------- Test methods ---------- */

  /**
   * Tests that routing table is updated.
   * Simulates nodeA sending a request through nodeB to nodeC.
   */
  protected void testRouteRequest() throws TestFailedException {
    initNode(_nodeB, 0, _nodeC.getService().getNodeId());

    AP3TestingMessage msg = new AP3TestingMessage(_nodeA.getService().getNodeId(),
						  "request",
						  AP3MessageType.REQUEST,
						  DEFAULT_FETCH_PROB);
    _nodeB.getService().messageForAppl(msg);

    // Routing table should contain exactly one entry
    AP3RoutingTable table = _nodeB.getService().getAP3RoutingTable();
    assertTrue("RouteRequest", "Routing table should contain exactly one entry",
	       table.getNumEntries() == 1);

    // Routing table has the correct entry
    AP3RoutingTableEntry entry = table.getEntry(msg.getID());
    assertTrue("RouteRequest", "Routing table should have entry for message w/ id" + msg.getID(),
	       entry != null);
    assertEquals("RouteRequest", "Routing table entry should have source of msg equal to  nodeA",
		 _nodeA.getService().getNodeId(), entry.getSource());

    // Routing to the correct location
    assertEquals("RouteRequest", "Should be routing request to nodeC",
		 _nodeC.getService().getNodeId(),
		 _nodeB.getService()._routeMsgDest);

    // Routing message.source = nodeB's id
    assertEquals("RouteRequest", "Msg.source should equal nodeB's node id",
		 _nodeB.getService().getNodeId(),
		 _nodeB.getService()._routeMsgMsg.getSource());
  }

  /**
   * Tests that responses are routed back correctly.
   * Simulates nodeB routing a response to nodeA.
   */
  protected void testRouteResponse() throws TestFailedException {
    initNode(_nodeB, 0, null);

    // Set up routing table to know about a request from A
    AP3TestingMessage request = new AP3TestingMessage(_nodeA.getService().getNodeId(),
						      "request",
						      AP3MessageType.REQUEST,
						      DEFAULT_FETCH_PROB);
    AP3RoutingTable table = _nodeB.getService().getAP3RoutingTable();
    try {
      table.addEntry(request);
    }
    catch (MessageIDCollisionException e) {
      throw new TestFailedException("Routing table had unexpected collision: " + e);
    }

    // Create and return response
    AP3TestingMessage msg = new AP3TestingMessage(null,
						  "response",
						  AP3MessageType.RESPONSE,
						  DEFAULT_FETCH_PROB);
    msg.setID(request.getID());
    _nodeB.getService().messageForAppl(msg);

    // Routing table should have been cleared
    assertTrue("RouteResponse", "Routing table should contain no entries",
	       table.getNumEntries() == 0);

    // Response was cached
    assertTrue("RouteResponse", "Response should be cached",
	       !_nodeB.isCacheEmpty());

    // Routing back to nodeA
    assertEquals("RouteResponse", "Should be routing response back to nodeA",
		 _nodeA.getService().getNodeId(),
		 _nodeB.getService()._routeMsgDest);
  }

  /**
   * Tests that content is fetched correctly from client.
   * The content should be fetched by the client, should be cached, and the
   * routing table should not be modified.
   */
  protected void testFetch() throws TestFailedException {
    initNode(_nodeB, 1, null);
    _nodeB.setContent("my content");

    // Send request msg from A, it's fetch probability set to 1.
    AP3TestingMessage request = new AP3TestingMessage(_nodeA.getService().getNodeId(),
						      "request",
						      AP3MessageType.REQUEST,
						      1);
    _nodeB.getService().messageForAppl(request);

    // Item should be cached
    assertTrue("Fetch", "Fetched content should be cached",
	       !_nodeB.isCacheEmpty());

    // Content should have been fetched from client
    assertEquals("Fetch", "Fetched content should have come from client",
		 "my content", _nodeB.getService()._routeMsgMsg.getContent());

    // Routing table should be unmodified
    AP3RoutingTable table = _nodeB.getService().getAP3RoutingTable();
    assertTrue("Fetch", "Routing table should be unmodified",
	       table.getNumEntries() == 0);

    // Message is routed back to nodeA
    assertEquals("Fetch", "Message should be routed back to nodeA",
		 _nodeA.getService().getNodeId(),
		 _nodeB.getService()._routeMsgDest);
  }

  /**
   * Tests that content is returned correctly if found in client's cache.
   * The routing table should not be modified, and the message
   */
  protected void testFetchFromCache() throws TestFailedException {
    initNode(_nodeB, 0, null);

    // Seed the cache so that it returns a response
    _nodeB.setCachedResponse("cached content");

    // Send request msg from A, it's fetch probability set to 0.
    AP3TestingMessage request = new AP3TestingMessage(_nodeA.getService().getNodeId(),
						      "request",
						      AP3MessageType.REQUEST,
						      0);
    _nodeB.getService().messageForAppl(request);

    // nodeB should have found a response in its cache to route back

    // Content should have been fetched from cache
    assertEquals("CachedResponse", "Fetched content should have come from cache",
		 "cached content", _nodeB.getService()._routeMsgMsg.getContent());

    // Routing table should be unmodified
    AP3RoutingTable table = _nodeB.getService().getAP3RoutingTable();
    assertTrue("CachedResponse", "Routing table should be unmodified",
	       table.getNumEntries() == 0);

    // Message is routed back to nodeA
    assertEquals("CachedResponse", "Message should be routed back to nodeA",
		 _nodeA.getService().getNodeId(),
		 _nodeB.getService()._routeMsgDest);

    // Message is a response
    assertEquals("CachedResponse", "Routed message should be a response",
		 AP3MessageType.RESPONSE + "",
		 _nodeB.getService()._routeMsgMsg.getType() + "");
  }

  /**
   * Tests that calling getAnonymizedContent sends a request to another node,
   * and updates its own routing table after sending the message.
   * In this case, nodeA is asked to retrieve content and it first routes to nodeB.
   */
  protected void testGetAnonymizedContent() throws TestFailedException {
    initNode(_nodeA, 0, _nodeB.getService().getNodeId());

    // Request anonymized content
    String response = (String) _nodeA.getService().getAnonymizedContent("request", 0, DEFAULT_TIMEOUT);

    // The message that nodeA will wrap the request in and try to route
    AP3TestingMessage msg = (AP3TestingMessage) _nodeA.getService()._routeMsgMsg;

    // Routing table should contain exactly one entry
    AP3RoutingTable table = _nodeA.getService().getAP3RoutingTable();
    assertTrue("GetAnonymizedContent", "Routing table should contain exactly one entry",
	       table.getNumEntries() == 1);

    // Routing table has the correct entry. That is, an entry for a message with
    // the same id as the message it is routing.
    AP3RoutingTableEntry entry = table.getEntry(msg.getID());
    assertTrue("GetAnonymizedContent", "Routing table should have entry for message w/ id" + msg.getID(),
	       entry != null);
    assertEquals("GetAnonymizedContent", "Routing table entry should have source of msg equal to  nodeB",
		 _nodeB.getService().getNodeId(), entry.getSource());

    // Routing to the correct location
    assertEquals("GetAnonymizedContent", "Should be routing request to nodeB",
		 _nodeB.getService().getNodeId(),
		 _nodeA.getService()._routeMsgDest);

    // Routing message.source = nodeA's id
    assertEquals("GetAnonymizedContent", "Msg.source should equal nodeA's node id",
		 _nodeA.getService().getNodeId(),
		 _nodeA.getService()._routeMsgMsg.getSource());
  }

  /**
   * Tests that calling getAnonymizedContent and then timing out
   * causes the method to return null.
   */
  protected void testGetAnonymizedContentTimeout() throws TestFailedException {
    // Setup nodes so that A sends to be and B fetches.
    initNode(_nodeA, 0, _nodeB.getService().getNodeId());
    initNode(_nodeB, 1, null);
    _nodeA.getService().suspendRouteMsg = false;
    _nodeB.getService().suspendRouteMsg = false;
    
    // Request anonymized content but ensure that a timeout will occur
    String response = (String) _nodeA.getService().getAnonymizedContent("request", 0, 0);
    
    // Response content should be null due to timeout
    assertTrue("GetAnonymizedContentTimeout", 
	       "Method should have returned null due to timeout",
	       response == null);
  }

  /**
   * Tests that after getAnonymizedContent returns, the routing
   * table is unchanged.
   */
  protected void testGetAnonymizedContentRoutingTable() 
    throws TestFailedException
  {
    // Setup nodes so that A sends to be and B fetches.
    initNode(_nodeA, 0, _nodeB.getService().getNodeId());
    initNode(_nodeB, 1, null);
    _nodeA.getService().suspendRouteMsg = false;
    _nodeB.getService().suspendRouteMsg = false;
    
    // Request anonymized content.
    String response = (String) _nodeA.getService().getAnonymizedContent("request", 0, DEFAULT_TIMEOUT);
    
    // Response content should not be null
    assertTrue("GetAnonymizedContentRoutingTable", 
	       "Content should have been non-null",
	       response != null);

    // Routing table should be unchanged
    assertTrue("GetAnonymizedContentRoutingTable", 
	       "Routing table should be unchanged - number of entries should be zero",
	       _nodeA.getService().getAP3RoutingTable().getNumEntries() == 0);
  }


  /**
   * Initializes and runs all regression tests.
   */
  public void runTests() {
    initialize();

    try {
      // Run each test
      testRouteRequest();
      testRouteResponse();
      testFetch();
      testFetchFromCache();

      System.out.println("\n\nDEBUG-All tests passed!---------------------\n");
    }
    catch (TestFailedException e) {
      System.out.println("\n\nDEBUG-Test Failed!--------------------------\n");
      System.out.println(e.toString());
      System.out.println("\n\n--------------------------------------------\n");
    }
  }


  /**
   * Usage: RMIAP3Test [-port p] [-nodes n] [-bootstrap host[:port]] [-help]
   */
  public static void main(String args[]) {

    doRMIinitstuff(args);
    RMIAP3RegrTest ap3Test = new RMIAP3RegrTest();
    ap3Test.runTests();

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
