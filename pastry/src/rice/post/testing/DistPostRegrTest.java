package rice.post.testing;

import rice.past.*;
import rice.past.messaging.*;

import rice.pastry.*;
import rice.pastry.dist.*;
import rice.pastry.standard.*;
import rice.pastry.security.*;

import rice.storage.*;
import rice.storage.testing.*;

import rice.post.*;
import rice.post.messaging.*;

import rice.scribe.*;

import java.util.*;
import java.net.*;
import java.io.Serializable;
import java.security.*;

/**
 * Provides regression testing for the POST service using distributeed nodes.
 *
 * @version $Id$
 * @author Alan Mislove
 */

public class DistPostRegrTest {
  private DistPastryNodeFactory factory;
  private Vector pastrynodes;
  private Vector pastNodes;
  private Vector scribeNodes;
  private Vector postNodes;
  private Vector postClients;
  private Credentials credentials = new PermissiveCredentials();
  private KeyPair caPair;
  private KeyPairGenerator kpg;

  private Object waitObject = "waitObject";
  private boolean notificationReceived = false;
  private boolean notificationFailed = false;
  private Post receivingPost = null;

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

  public DistPostRegrTest() throws NoSuchAlgorithmException {
    idFactory = new RandomNodeIdFactory();
    factory = DistPastryNodeFactory.getFactory(idFactory,
                                               protocol,
                                               port);
    pastrynodes = new Vector();
    pastNodes = new Vector();
    postNodes = new Vector();
    postClients = new Vector();
    scribeNodes = new Vector();    
    rng = new Random(5);
    
    kpg = KeyPairGenerator.getInstance("RSA");
    caPair = kpg.generateKeyPair();    
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
   * Creates a pastryNode with a past, scribe, and post running on it.
   */
  protected void makeNode() {
    PastryNode pn = factory.newNode(getBootstrap());
    pastrynodes.add(pn);

    StorageManager sm = new MemoryStorageManager();
    PASTServiceImpl past = new PASTServiceImpl(pn, sm);
    pastNodes.add(past);

    Scribe scribe = new Scribe(pn, credentials);
    scribeNodes.add(scribe);   
    System.out.println("created " + pn);
  }
  
  /**
    * Creates a POST.
   */
  protected void makePOSTNode(int i) {
    try {
      PastryNode pn = (PastryNode) pastrynodes.elementAt(i);
      PASTServiceImpl past = (PASTServiceImpl) pastNodes.elementAt(i);
      Scribe scribe = (Scribe) scribeNodes.elementAt(i);

      KeyPair pair = kpg.generateKeyPair();

      PostUserAddress address = new PostUserAddress("TEST" + i);

      Post post = new Post(pn, past, scribe, address, pair, null, caPair.getPublic());
      postNodes.add(post);

      DummyPostClient dpc = new DummyPostClient(post);
      postClients.add(dpc);
      
      post.addClient(dpc);
      System.out.println("built POST at " + pn);
    } catch (Throwable e) {
      System.out.println("ERROR BUILDING POST: " + e);
      e.printStackTrace();
    }
  }
  
  /**
   * Creates the nodes used for testing.
   */
  protected void createNodes() {
    for (int i=0; i < numNodes; i++) {
      makeNode();
    }

    for (int i=0; i < numNodes; i++) {
      makePOSTNode(i);
    }    
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
  protected void testNotification() throws TestFailedException {
    int sendingNode = rng.nextInt(numNodes);
    int receivingNode = rng.nextInt(numNodes);

    Post sendingPost = (Post) postNodes.elementAt(sendingNode);
    receivingPost = (Post) postNodes.elementAt(receivingNode);
    
    String sender = "TEST" + sendingNode;
    PostUserAddress senderAddr = new PostUserAddress(sender);
    
    String receiver = "TEST" + receivingNode;
    PostUserAddress receiverAddr = new PostUserAddress(receiver);

    DummyPostClient sendingPostClient = (DummyPostClient) postClients.elementAt(sendingNode);
    PostClientAddress addr = PostClientAddress.getAddress(sendingPostClient);

    DummyNotificationMessage dnm = new DummyNotificationMessage(addr, senderAddr, receiverAddr);

    sendingPost.sendNotification(dnm);

    synchronized (waitObject) {
      try {
        while (! notificationReceived) {
          receivingPost.announcePresence();
          waitObject.wait(2000);
          System.out.println("Waiting for notification message...");
        }
      } catch (InterruptedException e) {
        System.out.println("ERROR WAITING:"  + e);
      }
    }

    if (notificationFailed) {
      throw new TestFailedException("Notificaiton received at wrong Post!");
    }
  }

  public void notificationReceived(NotificationMessage nm, Post post) {
    if (! (receivingPost == post)) {
      notificationFailed = true;
    }
    
    notificationReceived = true;

    synchronized(waitObject) {
      waitObject.notify();
    }
  }

  /**
   * Initializes and runs all regression tests.
   */
  public void runTests() {
    initialize();

    try {
      // Run each test
      testNotification();

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
      if (args[i].equals("-nodes") && i+1 < args.length) {
        int p = Integer.parseInt(args[i+1]);
        if (p > 0) numNodes = p;
        break;
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
   * Usage: DistPostRegrTest [-port p] [-bootstrap host[:port]] [-nodes n] [-protocol (rmi|wire)] [-help]
   */
  public static void main(String args[]) throws NoSuchAlgorithmException {

    doInitstuff(args);
    DistPostRegrTest postT = new DistPostRegrTest();
    postT.runTests();

  }


  /**
   * Exception indicating that a regression test failed.
   */
  protected class TestFailedException extends Exception {
    protected TestFailedException(String message) {
      super(message);
    }
  }

  protected class DummyPostClient extends PostClient {

    private Post post;
    
    public DummyPostClient(Post p) {
      this.post = p;
    }

    public void notificationReceived(NotificationMessage nm) {
      DistPostRegrTest.this.notificationReceived(nm, post);
    }
  }

  protected static class DummyNotificationMessage extends NotificationMessage {

    public DummyNotificationMessage(PostClientAddress address, PostEntityAddress sender, PostEntityAddress receiver) {
      super(address, sender, receiver);
    }
  }

}
