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
    private AP3TestingClient _nodeA, _nodeB;
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


    protected AP3TestingClient makeAP3Node() {
	// or, for a sweet one-liner,
	// pastrynodes.add(new RMIPastryNode(factory, getBootstrap()));

	PastryNode pn = factory.newNode(getBootstrap());
	pastrynodes.add(pn);

	AP3TestingClient ap3Client = new AP3TestingClient(pn);
	ap3Nodes.addElement(ap3Client);
	System.out.println("created " + pn);

	return ap3Client;
    }

    /**
     * Creates a chain of three AP3 nodes: originator, forwarder, and fetcher
     */
    protected void createNodes() {
      /**
      _originator = makeAP3Node();
      _forwarder = makeAP3Node();
      _fetcher = makeAP3Node();
      */
      _nodeA = makeAP3Node();
      _nodeB = makeAP3Node();
    }

    /**
     * Initializes the AP3 nodes in the chain
     */
    protected void initNodes() {
      /**
      // Originator
      initNode(_originator, 0, _forwarder.getService().getNodeId());

      // Forwarder
      initNode(_forwarder, 0, _fetcher.getService().getNodeId());

      // Fetcher
      initNode(_fetcher, 1, null);
      */

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

  protected void assertTrue(String name, String intention, boolean test)
    throws TestFailedException
  {
    if (!test) {
      throw new TestFailedException("\nAssertion failed in '" + name + "'\nExpected: " + intention);
    }
  }

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
   * Simulates nodeA sending a request through nodeB.
   */
  protected void testRouteRequest() throws TestFailedException {
    initNode(_nodeB, 0, null);

    AP3TestingMessage msg = new AP3TestingMessage(_nodeA.getService().getNodeId(),
                                                   "request",
                                                   AP3MessageType.REQUEST,
                                                   DEFAULT_FETCH_PROB);
    _nodeB.getService().messageForAppl(msg);

    // Check routing table entry after receiving message
    AP3RoutingTable table = _nodeB.getService().getAP3RoutingTable();
    assertTrue("RouteRequest", "Routing table should contain exactly one entry",
                table.getNumEntries() == 1);

    AP3RoutingTableEntry entry = table.getEntry(msg.getID());
    assertTrue("RouteRequest", "Routing table should have entry for message " + msg.getID(),
                entry != null);
    assertEquals("RouteRequest", "Source of message should be nodeA",
                  _nodeA.getService().getNodeId(), entry.getSource());

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
  }

  /**
   * Tests that content is fetched correctly from client.
   * The content should be fetched by the client, should be cached, and the
   * routing table should stay unmodified.
   */
  protected void testFetch() throws TestFailedException {
    initNode(_nodeB, 1, null);
    _nodeB.setContent("my content");

    // Set up routing table to know about a request from A
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
    assertTrue("Fetch", "Routing table should be unmodified",
                table.getNumEntries() == 0);
  }

  /**
   * Tests that content is returned correctly if found in client's cache.
   */
  protected void testFetchFromCache() throws TestFailedException {
    /**
    initNode(_nodeB, 1, null);
    _nodeB.setContent("my content");

    // Set up routing table to know about a request from A
    AP3TestingMessage request = new AP3TestingMessage(_nodeA.getService().getNodeId(),
                                                       "request",
                                                       AP3MessageType.REQUEST,
                                                       1);
    _nodeB.getService().messageForAppl(request);

    // Item should still be cached
    assertTrue("Fetch", "Cached content should still be cached",
                !_nodeB.isCacheEmpty());
    // Content should have been fetched from client
    assertEquals("Fetch", "Fetched content should have come from client",
                  "my content", _nodeB.getService()._routeMsgMsg.getContent());
    // Routing table should be unmodified
    assertTrue("Fetch", "Routing table should be unmodified",
                table.getNumEntries() == 0);
    */
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
