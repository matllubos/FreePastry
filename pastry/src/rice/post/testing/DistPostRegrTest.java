package rice.post.testing;

import rice.*;

import rice.p2p.commonapi.IdFactory;

import rice.p2p.past.*;

import rice.pastry.*;
import rice.pastry.commonapi.*;
import rice.pastry.dist.*;
import rice.pastry.standard.*;
import rice.pastry.security.*;

import rice.post.*;
import rice.post.messaging.*;
import rice.post.storage.*;
import rice.post.security.*;

import rice.scribe.*;

import rice.persistence.*;

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
  private SecurityService security;

  private Object waitObject = "waitObject";
  private boolean notificationReceived = false;
  private boolean notificationFailed = false;
  private Post receivingPost = null;

  private Random rng;
  private RandomNodeIdFactory idFactory;

  private static int numNodes = 4;
  private static int k = 3;  // replication factor

  private static int port = 5009;
  private static String bshost;
  private static int bsport = 5009;

  private static int protocol = DistPastryNodeFactory.PROTOCOL_WIRE;

  private static IdFactory FACTORY = new PastryIdFactory();

  private static String INSTANCE_NAME = "PostTesting";

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
    rng = new Random();
    
    kpg = KeyPairGenerator.getInstance("RSA");
    caPair = kpg.generateKeyPair();
    security = new SecurityService(null, null);
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

    StorageManager sm = new StorageManager(FACTORY,
                                           new MemoryStorage(FACTORY),
                                           new LRUCache(new MemoryStorage(FACTORY), 1000000));
    PastImpl past = new PastImpl(pn, sm, k, INSTANCE_NAME);
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
      PastImpl past = (PastImpl) pastNodes.elementAt(i);
      Scribe scribe = (Scribe) scribeNodes.elementAt(i);

      KeyPair pair = kpg.generateKeyPair();

      PostUserAddress address = new PostUserAddress("TEST" + i);

      PostCertificate certificate = security.generateCertificate(address, pair.getPublic(), caPair.getPrivate());
      
      Post post = new Post(pn, past, scribe, address, pair, certificate, caPair.getPublic(), INSTANCE_NAME);
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

    Thread t = new Thread() {
      public void run() {
        try {
          while (true) {
            System.out.println("Publishing presence...");
            receivingPost.announcePresence();
            Thread.sleep(2000);
          }
        } catch (Exception e) {
          System.out.println("INTERRUPTED: " + e);
        }
      }
    };

    t.start();

    System.out.println("Waiting for notification message...");

    synchronized (waitObject) {
      try {
        waitObject.wait();
      } catch (Exception e) {
        System.out.println("INTERRUPTED (2): " + e);
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
   * Tests the storage service on POST
   */
  protected void testStorage() throws TestFailedException {
    int node = rng.nextInt(numNodes);

    Post post = (Post) postNodes.elementAt(node);
    StorageService storage = post.getStorageService();

    PostData data = new DummyPostData(rng.nextLong());

    DummyContentHashStorageTest test = new DummyContentHashStorageTest(storage, data);
    test.start();
  }
  
  /**
   * Initializes and runs all regression tests.
   */
  public void runTests() {
    initialize();

    try {
      // Run each test
      testNotification();
      testStorage();

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

      post.getPostLog(new Continuation() {
        public void receiveResult(Object o) {
        }

        public void receiveException(Exception e) {
        }
      });
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

  protected static class DummyPostData implements PostData {

    private long number;
    
    public DummyPostData(long number) {
      this.number = number;
    }

    public boolean equals(Object o) {
      if (o instanceof DummyPostData) {
        return (((DummyPostData) o).number == number);
      }

      return false;
    }

    public SignedReference buildSignedReference(Id location) {
      return new SignedReference(location);
    }

    public ContentHashReference buildContentHashReference(Id location, Key key) {
      return new ContentHashReference(location, key);
    }

    public SecureReference buildSecureReference(Id location, Key key) {
      return new SecureReference(location, key);
    }
  }

  protected class DummyContentHashStorageTest implements Continuation {

    public static final int STATE_1 = 1;
    public static final int STATE_2 = 2;
    
    private StorageService storage;
    private PostData data;
    private ContentHashReference reference;
    private int state;
    
    public DummyContentHashStorageTest(StorageService storage, PostData data) {
      this.storage = storage;
      this.data = data;
    }

    public void start() {
      System.out.println("ContentHashTest storing data.");
      
      state = STATE_1;
      storage.storeContentHash(data, this);
    }

    private void startState1(ContentHashReference ref) {
      this.reference = ref;
      System.out.println("ContentHashTest received reference - checking.");

      state = STATE_2;
      storage.retrieveContentHash(reference, this);
    }

    private void startState2(PostData data) {
      if (this.data.equals(data)) {
        System.out.println("ContentHashTest ran successfully.");

        DummySecureStorageTest test = new DummySecureStorageTest(storage, data);
        test.start();
      } else {
        System.out.println("ContentHashTest return incorrect data.");
      }
    }
    
    public void receiveResult(Object o) {
      switch (state) {
        case STATE_1:
          startState1((ContentHashReference) o);
          break;
        case STATE_2:
          startState2((PostData) o);
          break;
        default:
          System.out.println("Unknown state in DummyCHST:" + state);
          break;
      }
    }

    public void receiveException(Exception e) {
      System.out.println("Exception occured in DummyCHST: " + e);
    }
  }

  protected class DummySecureStorageTest implements Continuation {

    public static final int STATE_1 = 1;
    public static final int STATE_2 = 2;

    private StorageService storage;
    private PostData data;
    private SecureReference reference;
    private int state;

    public DummySecureStorageTest(StorageService storage, PostData data) {
      this.storage = storage;
      this.data = data;
    }

    public void start() {
      System.out.println("SecureTest storing data.");

      state = STATE_1;
      storage.storeSecure(data, this);
    }

    private void startState1(SecureReference ref) {
      this.reference = ref;
      System.out.println("SecureTest received reference - checking.");

      state = STATE_2;
      storage.retrieveSecure(reference, this);
    }

    private void startState2(PostData data) {
      if (this.data.equals(data)) {
        System.out.println("SecureTest ran successfully.");

        DummySignedStorageTest test = new DummySignedStorageTest(storage, data);
        test.start();        
      } else {
        System.out.println("SecureTest return incorrect data.");
      }
    }

    public void receiveResult(Object o) {
      switch (state) {
        case STATE_1:
          startState1((SecureReference) o);
          break;
        case STATE_2:
          startState2((PostData) o);
          break;
        default:
          System.out.println("Unknown state in DummySST:" + state);
          break;
      }
    }

    public void receiveException(Exception e) {
      System.out.println("Exception occured in DummySST: " + e);
    }
  }

  protected class DummySignedStorageTest implements Continuation {

    public static final int STATE_1 = 1;
    public static final int STATE_2 = 2;
    public static final int STATE_3 = 3;
    public static final int STATE_4 = 4;

    private StorageService storage;
    private PostData data;
    private PostData dataNew;
    private SignedReference reference;
    private int state;
    private NodeId nodeId;

    public DummySignedStorageTest(StorageService storage, PostData data) {
      this.storage = storage;
      this.data = data;
    }

    public void start() {
      System.out.println("SignedTest storing data.");

      nodeId = idFactory.generateNodeId();
      
      state = STATE_1;
      storage.storeSigned(data, nodeId, this);
    }

    private void startState1(SignedReference ref) {
      this.reference = ref;
      System.out.println("SignedTest received reference - checking.");

      state = STATE_2;
      storage.retrieveAndVerifySigned(reference, this);
    }

    private void startState2(PostData data) {
      if (this.data.equals(data)) {
        System.out.println("SignedTest retreived successfully.");

        dataNew = new DummyPostData(rng.nextLong());
        state = STATE_3;
        storage.storeSigned(dataNew, nodeId, this);
      } else {
        System.out.println("SignedTest return incorrect data.");
      }
    }


    private void startState3(SignedReference ref) {
      System.out.println("SignedTest received new reference - checking.");

      state = STATE_4;
      storage.retrieveAndVerifySigned(reference, this);
    }

    private void startState4(PostData data) {
      if (this.dataNew.equals(data)) {
        System.out.println("SignedTest retreived new data successfully.");
      } else {
        System.out.println("SignedTest return incorrect data.");
      }
    }
    
    public void receiveResult(Object o) {
      switch (state) {
        case STATE_1:
          startState1((SignedReference) o);
          break;
        case STATE_2:
          startState2((PostData) o);
          break;
        case STATE_3:
          startState3((SignedReference) o);
          break;
        case STATE_4:
          startState4((PostData) o);
          break;
        default:
          System.out.println("Unknown state in DummySST:" + state);
          break;
      }
    }

    public void receiveException(Exception e) {
      System.out.println("Exception occured in DummySST: " + e);
    }
  }  
}
