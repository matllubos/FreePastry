package rice.past.search.testing;

import rice.past.*;
import rice.past.search.*;
import rice.past.messaging.*;

import rice.pastry.*;
import rice.pastry.standard.*;
import rice.pastry.security.*;
import rice.pastry.dist.*;

import rice.storage.*;
import rice.storage.testing.*;


import ObjectWeb.Persistence.*;

import java.util.*;
import java.net.*;
import java.io.Serializable;

/**
 * Provides regression testing for the SearchService using distributed nodes.
 *
 * @version $Id: DistPASTSearchRegrTest.java,v 1.1 2002/09/26 17:27:46 amislove Exp $
 * @author Alan Mislove
 */

public class BloomSearchRegrTest {
  private DistPastryNodeFactory factory;
  private Vector pastrynodes;
  private Vector pastNodes;
  private Vector searchServices;

  private Random rng;

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

  public BloomSearchRegrTest() {
    factory = DistPastryNodeFactory.getFactory(new RandomNodeIdFactory(),
                                               protocol,
                                               port);
    pastrynodes = new Vector();
    pastNodes = new Vector();
    searchServices = new Vector();
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
  protected SearchService makeSearchService() {
    PastryNode pn = factory.newNode(getBootstrap());
    pastrynodes.add(pn);

    PersistenceManager pm = new DummyPersistenceManager();
    StorageManager sm = new StorageManagerImpl(pm);

    PASTServiceImpl past = new PASTServiceImpl(pn, sm);
    past.DEBUG = true;
    pastNodes.add(past);
    System.out.println("created " + pn);

    //SearchServiceImpl search = new SearchServiceImpl(past);
    SearchService search = new BloomSearchServiceImpl(pn, past);
    searchServices.add(search);

    return search;
  }

  /**
   * Creates the nodes used for testing.
   */
  protected void createNodes() {
    for (int i=0; i < numNodes; i++) {
      makeSearchService();
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
   * Tests inserting a Searchable object into the SearchService.
   */
  protected void testInsert() throws TestFailedException {
    String[] keys = {"monkey", "cat", "dog"};
    double[] ranks = {0.5, 0.5, 0.5};
    URL url = null;

    try {
      url = new URL("http://www.msnbc.com");
    } catch (MalformedURLException e) {
      throw new TestFailedException("URL creation failed...");
    }

    Searchable s = new DummySearchable(keys, url, ranks);
    SearchService local = (SearchService) searchServices.elementAt(rng.nextInt(numNodes));

    System.out.println("TEST: Inserting dummy document into system...");

    // inserting document into index
    local.index(s);

    // waiting
    pause(1000);

    System.out.println("TEST: Retrieving list for 'monkey'...");

    // retrieving list for monkey
    IndexKeyString[] indexKeys = {new IndexKeyString("monkey")};

    URL[] entries = local.query(indexKeys);

    assertTrue("testInsert", "Retrieved list should have one element",
               entries.length == 1);

    assertEquals("testInsert", "Retrieved URL should be the same as inserted.",
                 entries[0], url);

    // TEST AN ADDITIONAL ENTRY
    String[] keys2 = {"monkey", "computer"};
    double[] ranks2 = {0.2, 0.3};
    URL url2 = null;

    try {
      url2 = new URL("http://www.cnn.com");
    } catch (MalformedURLException e) {
      throw new TestFailedException("URL creation failed...");
    }

    Searchable s2 = new DummySearchable(keys2, url2, ranks2);
    SearchService local2 = (SearchService) searchServices.elementAt(rng.nextInt(numNodes));

    System.out.println("TEST: Inserting second dummy document into system...");

    // inserting document into index
    local2.index(s2);

    // waiting
    pause(1000);

    System.out.println("TEST: Retrieving list for 'monkey'...");

    // retrieving list for monkey
    URL[] entries2 = local2.query(indexKeys);
    
    assertTrue("testInsert", "Retrieved list should have two elements, had " + entries2.length,
               entries2.length == 2);

    assertEquals("testInsert", "First retrieved URL should be the same as inserted.",
                 entries2[0], url);

    assertEquals("testInsert", "Second retrieved URL should be the same as inserted.",
                 entries2[1], url2);

    // TEST A THIRD ENTRY
    String[] keys3 = {"monkey", "mouse"};
    double[] ranks3 = {0.9, 0.3};
    URL url3 = null;

    try {
      url3 = new URL("http://www.slashdot.com");
    } catch (MalformedURLException e) {
      throw new TestFailedException("URL creation failed...");
    }

    Searchable s3 = new DummySearchable(keys3, url3, ranks3);
    SearchService local3 = (SearchService) searchServices.elementAt(rng.nextInt(numNodes));

    System.out.println("TEST: Inserting third dummy document into system...");

    // inserting document into index
    local3.index(s3);

    // waiting
    pause(1000);

    System.out.println("TEST: Retrieving list for 'monkey'...");

    // retrieving list for monkey
    URL[] entries3 = local3.query(indexKeys);
    Vector theEntries = new Vector();
    
    for(int i = 0; i < entries3.length; i++) {
        theEntries.add(entries3[i]);
    }
    
    assertTrue("testInsert", "Retrieved list should have two elements",
               entries3.length == 3);

    assertTrue("testInsert", "First retrieved URL should be the same as inserted.",
                 theEntries.contains(url3));

    assertTrue("testInsert", "Second retrieved URL should be the same as inserted.",
                 theEntries.contains(url));

    assertTrue("testInsert", "Third retrieved URL should be the same as inserted.",
                 theEntries.contains(url2));

    /*
    assertEquals("testInsert", "First retrieved URL should be the same as inserted.",
                 entries3[0], url3);

    assertEquals("testInsert", "Second retrieved URL should be the same as inserted.",
                 entries3[1], url);

    assertEquals("testInsert", "Third retrieved URL should be the same as inserted.",
                 entries3[2], url2);
    */

  }

  /*
   * Tests the Combinator class
   */
  protected void testCombinator() throws TestFailedException {
    IndexKeyString[] keys = {new IndexKeyString("dog"),
                       new IndexKeyString("cat"),
                       new IndexKeyString("monkey"),
                       new IndexKeyString("tree"),
                       new IndexKeyString("dolphin")};

    Combinator c = new Combinator(keys);

    assertTrue("testCombinator", "Number of one-element tuples should be 5",
                 c.getCombination(1).size() == 5);

    assertTrue("testCombinator", "Number of two-element tuples should be 10",
                 c.getCombination(2).size() == 10);

    assertTrue("testCombinator", "Number of three-element tuples should be 10",
                 c.getCombination(3).size() == 10);

    assertTrue("testCombinator", "Number of four-element tuples should be 5",
                 c.getCombination(4).size() == 5);

    assertTrue("testCombinator", "Number of five-element tuples should be 1",
                 c.getCombination(5).size() == 1);

    IndexKeyString[] keysExclude = {new IndexKeyString("dog"),
                       new IndexKeyString("tree"),
                       new IndexKeyString("computer")};

    Combinator d = new Combinator(keys, keysExclude);

    assertTrue("testCombinator", "Number of elements in an exclude combinator should be 3, was " + d.size(),
                d.size() == 3);
  }

  /*
   * Tests the findHighest method in SearchServiceImpl
   */
  protected void testFindHighest() throws TestFailedException {
    String[] keys = {"dell", "gateway", "micron"};
    double[] ranks = {0.3, 0.3, 0.3};
    URL url = null;

    try {
      url = new URL("http://www.computers.com");
    } catch (MalformedURLException e) {
      throw new TestFailedException("URL creation failed...");
    }

    Searchable s = new DummySearchable(keys, url, ranks);
    SearchService local = (SearchService) searchServices.elementAt(rng.nextInt(numNodes));

    System.out.println("TEST: Inserting dummy document into system...");

    // inserting document into index
    local.index(s);

    String[] keys2 = {"dell", "gateway", "apple"};
    double[] ranks2 = {0.5, 0.5, 0.3};
    URL url2 = null;

    try {
      url2 = new URL("http://www.computers.com");
    } catch (MalformedURLException e) {
      throw new TestFailedException("URL creation failed...");
    }

    Searchable s2 = new DummySearchable(keys2, url2, ranks2);
    SearchService local2 = (SearchService) searchServices.elementAt(rng.nextInt(numNodes));

    System.out.println("TEST: Inserting second dummy document into system...");

    // inserting document into index
    local2.index(s2);

    IndexKeyString[] indexKeys = {new IndexKeyString("dell"),
                            new IndexKeyString("gateway")};

    Combinator c = new Combinator(indexKeys);

    BloomSearchServiceImpl local3 = (BloomSearchServiceImpl) searchServices.elementAt(rng.nextInt(numNodes));
    //SearchServiceImpl local3 = (SearchServiceImpl) searchServices.elementAt(rng.nextInt(numNodes));

    IndexKeyString[] results = local3.findHighest(c, 2);

    assertTrue("testFindHighest", "Highest tuple should be a one-tuple",
                 results.length == 1);

    assertTrue("testFindHighest", "Tuple should be one of the search terms",
                 results[0].equals(indexKeys[0]) || results[0].equals(indexKeys[1]));

    IndexKeyString dualKey = new IndexKeyString(indexKeys);

    PASTService past = (PASTService) pastNodes.elementAt(rng.nextInt(numNodes));

    past.insert(dualKey.getNodeId(), new IndexPlaceholder(dualKey), null);

    BloomSearchServiceImpl local4 = (BloomSearchServiceImpl) searchServices.elementAt(rng.nextInt(numNodes));
    //SearchServiceImpl local4 = (SearchServiceImpl) searchServices.elementAt(rng.nextInt(numNodes));

    IndexKeyString[] results2 = local4.findHighest(c, 2);

    assertTrue("testFindHighest", "Highest tuple should be a two-tuple",
                 results2.length == 2);

    assertTrue("testFindHighest", "Tuple should be both of the search terms",
                 (results2[0].equals(indexKeys[0]) && results2[1].equals(indexKeys[1])) ||
                 (results2[0].equals(indexKeys[1]) && results2[1].equals(indexKeys[0])) );

  }


  /*
   * Tests the intersection method in SearchServiceImpl
   */
  protected void testIntersection() throws TestFailedException {
    String[] keys = {"monet", "renoir", "manet"};
    double[] ranks = {0.3, 0.3, 0.3};
    URL url = null;

    try {
      url = new URL("http://www.art1.com");
    } catch (MalformedURLException e) {
      throw new TestFailedException("URL creation failed...");
    }

    Searchable s = new DummySearchable(keys, url, ranks);
    SearchService local = (SearchService) searchServices.elementAt(rng.nextInt(numNodes));

    System.out.println("TEST: Inserting dummy document into system...");

    // inserting document into index
    local.index(s);

    String[] keys2 = {"monet", "manet", "picasso"};
    double[] ranks2 = {0.5, 0.5, 0.3};
    URL url2 = null;

    try {
      url2 = new URL("http://www.art2.com");
    } catch (MalformedURLException e) {
      throw new TestFailedException("URL creation failed...");
    }

    Searchable s2 = new DummySearchable(keys2, url2, ranks2);
    SearchService local2 = (SearchService) searchServices.elementAt(rng.nextInt(numNodes));

    System.out.println("TEST: Inserting second dummy document into system...");

    // inserting document into index
    local2.index(s2);

    String[] keys3 = {"monet", "manet", "van gogh", "adams"};
    double[] ranks3 = {0.4, 0.4, 0.3, 0.7};
    URL url3 = null;

    try {
      url3 = new URL("http://www.art3.com");
    } catch (MalformedURLException e) {
      throw new TestFailedException("URL creation failed...");
    }

    Searchable s3 = new DummySearchable(keys3, url3, ranks3);
    SearchService local3 = (SearchService) searchServices.elementAt(rng.nextInt(numNodes));

    System.out.println("TEST: Inserting third dummy document into system...");

    // inserting document into index
    local3.index(s3);

    String[] keys4 = {"monet", "tree", "gorilla"};
    double[] ranks4 = {0.4, 0.4, 0.3};
    URL url4 = null;

    try {
      url4 = new URL("http://www.monkeys.com");
    } catch (MalformedURLException e) {
      throw new TestFailedException("URL creation failed...");
    }

    Searchable s4 = new DummySearchable(keys4, url4, ranks4);
    SearchService local4 = (SearchService) searchServices.elementAt(rng.nextInt(numNodes));

    System.out.println("TEST: Inserting fourth dummy document into system...");

    // inserting document into index
    local4.index(s4);

    IndexKeyString indexKey1 = new IndexKeyString("monet");
    IndexKeyString indexKey2 = new IndexKeyString("manet");

    PASTService past = (PASTService) pastNodes.elementAt(rng.nextInt(numNodes));

    Vector v1 = past.lookup(indexKey1.getNodeId()).getUpdates();
    Vector v2 = past.lookup(indexKey2.getNodeId()).getUpdates();

    Vector both = new Vector();
    both.addElement(v1);
    both.addElement(v2);

    BloomSearchServiceImpl local5 = (BloomSearchServiceImpl) searchServices.elementAt(rng.nextInt(numNodes));
    //SearchServiceImpl local5 = (SearchServiceImpl) searchServices.elementAt(rng.nextInt(numNodes));

    IndexKeyString[] overallKeys = {indexKey1, indexKey2};

    IndexEntry[] entries = local5.intersect(new IndexKeyString(overallKeys), both);
    //IndexEntry[] entries = local5.intersection(new IndexKeyString(overallKeys), both);

    assertTrue("testIntersection", "Intersection should produce 3 documents",
                 entries.length == 3);
  }

  /*
   * Tests the query method in SearchServiceImpl
   */
  protected void testQuery() throws TestFailedException {
    String[] keys = {"america", "germany", "france"};
    double[] ranks = {0.6, 0.6, 0.6};
    URL url = null;

    try {
      url = new URL("http://www.countries1.com");
    } catch (MalformedURLException e) {
      throw new TestFailedException("URL creation failed...");
    }

    Searchable s = new DummySearchable(keys, url, ranks);
    SearchService local = (SearchService) searchServices.elementAt(rng.nextInt(numNodes));

    System.out.println("TEST: Inserting dummy document into system...");

    // inserting document into index
    local.index(s);

    String[] keys2 = {"america", "britian", "italy"};
    double[] ranks2 = {0.9, 0.3, 0.3};
    URL url2 = null;

    try {
      url2 = new URL("http://www.countries2.com");
    } catch (MalformedURLException e) {
      throw new TestFailedException("URL creation failed...");
    }

    Searchable s2 = new DummySearchable(keys2, url2, ranks2);
    SearchService local2 = (SearchService) searchServices.elementAt(rng.nextInt(numNodes));

    System.out.println("TEST: Inserting second dummy document into system...");

    // inserting document into index
    Vector resultVec = new Vector();
    local2.index(s2);

    IndexKeyString[] queryKeys = {new IndexKeyString("america")};
    URL[] results = local2.query(queryKeys);
    
    for(int i = 0; i < results.length; i++) {
        resultVec.add(results[i]);    
    }
        
    assertTrue("testQuery", "Result set should be 2 URLs",
               results.length == 2);

    assertTrue("testQuery", "First result should be highest-ranking",
                 resultVec.contains(url));

    assertTrue("testQuery", "Second result should be lowest-ranking",
                 resultVec.contains(url2));

    String[] keys3 = {"america", "germany", "mexico"};
    double[] ranks3 = {0.5, 0.9, 0.1};
    URL url3 = null;

    try {
      url3 = new URL("http://www.countries3.com");
    } catch (MalformedURLException e) {
      throw new TestFailedException("URL creation failed...");
    }

    Searchable s3 = new DummySearchable(keys3, url3, ranks3);
    SearchService local3 = (SearchService) searchServices.elementAt(rng.nextInt(numNodes));

    System.out.println("TEST: Inserting third dummy document into system...");

    // inserting document into index
    local3.index(s3);

    String[] keys4 = {"germany", "spain", "portugal"};
    double[] ranks4 = {0.3, 0.2, 0.8};
    URL url4 = null;

    try {
      url4 = new URL("http://www.countries4.com");
    } catch (MalformedURLException e) {
      throw new TestFailedException("URL creation failed...");
    }

    Searchable s4 = new DummySearchable(keys4, url4, ranks4);
    SearchService local4 = (SearchService) searchServices.elementAt(rng.nextInt(numNodes));

    System.out.println("TEST: Inserting fourth dummy document into system...");

    // inserting document into index
    local4.index(s4);

    IndexKeyString[] queryKeys2 = {new IndexKeyString("america"), new IndexKeyString("germany")};
    URL[] results2 = local4.query(queryKeys2);
    
    resultVec.clear();
    
    for(int i = 0; i < results2.length; i++) {
        resultVec.add(results2[i]);    
    }
    
    assertTrue("testQuery", "Result set (multi) should be 2 URLs",
               results2.length == 2);

    assertTrue("testQuery", "First multi result should be highest-ranking",
                 resultVec.contains(url3));

    assertTrue("testQuery", "Second multi result should be lowest-ranking",
                 resultVec.contains(url));
  }


  /*
   * Tests the caching in SearchServiceImpl
   */
  protected void testCache() throws TestFailedException {
    String[] keys = {"oak", "pine", "cedar"};
    double[] ranks = {0.6, 0.6, 0.6};
    URL url = null;

    try {
      url = new URL("http://www.trees1.com");
    } catch (MalformedURLException e) {
      throw new TestFailedException("URL creation failed...");
    }

    Searchable s = new DummySearchable(keys, url, ranks);
    SearchService local = (SearchService) searchServices.elementAt(rng.nextInt(numNodes));

    System.out.println("TEST: Inserting dummy document into system...");

    // inserting document into index
    local.index(s);

    String[] keys2 = {"oak", "cedar", "cypress"};
    double[] ranks2 = {0.9, 0.3, 0.3};
    URL url2 = null;

    try {
      url2 = new URL("http://www.trees2.com");
    } catch (MalformedURLException e) {
      throw new TestFailedException("URL creation failed...");
    }

    Searchable s2 = new DummySearchable(keys2, url2, ranks2);
    SearchService local2 = (SearchService) searchServices.elementAt(rng.nextInt(numNodes));

    System.out.println("TEST: Inserting second dummy document into system...");

    // inserting document into index
    local2.index(s2);

    String[] keys3 = {"oak", "cedar", "maple", "pine"};
    double[] ranks3 = {0.5, 0.9, 0.1, 0.4};
    URL url3 = null;

    try {
      url3 = new URL("http://www.countries3.com");
    } catch (MalformedURLException e) {
      throw new TestFailedException("URL creation failed...");
    }

    Searchable s3 = new DummySearchable(keys3, url3, ranks3);
    SearchService local3 = (SearchService) searchServices.elementAt(rng.nextInt(numNodes));

    System.out.println("TEST: Inserting third dummy document into system...");

    // inserting document into index
    local3.index(s3);

    String[] keys4 = {"oak", "maple", "magnolia"};
    double[] ranks4 = {0.3, 0.2, 0.8};
    URL url4 = null;

    try {
      url4 = new URL("http://www.countries4.com");
    } catch (MalformedURLException e) {
      throw new TestFailedException("URL creation failed...");
    }

    Searchable s4 = new DummySearchable(keys4, url4, ranks4);
    SearchService local4 = (SearchService) searchServices.elementAt(rng.nextInt(numNodes));

    System.out.println("TEST: Inserting fourth dummy document into system...");

    // inserting document into index
    local4.index(s4);

    // force the cache of the result
    IndexKeyString[] queryKeys = {new IndexKeyString("oak"), new IndexKeyString("cedar")};
    URL[] results = local4.query(queryKeys);

    assertTrue("testQuery", "Result set (non-cached) should be 3 URLs",
               results.length == 3);

    PASTService past = (PASTService) pastNodes.elementAt(rng.nextInt(numNodes));

    // delete each of the singular entries
    past.delete((new IndexKeyString("oak")).getNodeId(), null);
    past.delete((new IndexKeyString("cedar")).getNodeId(), null);

    // requery for the results
    /*
     * This test doesn't apply to the bloom implementation
    IndexKeyString[] queryKeys2 = {new IndexKeyString("oak"), new IndexKeyString("cedar")};
    URL[] results2 = local4.query(queryKeys2);

    assertTrue("testQuery", "Result set (cached) should be 3 URLs",
               results2.length == 3);

    assertTrue("testQuery", "Cached result should be the same as the original",
                 results[0].equals(results2[0]) && results[1].equals(results2[1]) &&
                 results[2].equals(results2[2]));
                 */
  }


  /*
   * Tests the caching (again) in SearchServiceImpl
   */
  protected void testCache2() throws TestFailedException {
    String[] keys = {"coke", "pepsi", "sprite"};
    double[] ranks = {0.6, 0.6, 0.6};
    URL url = null;

    try {
      url = new URL("http://www.drinks1.com");
    } catch (MalformedURLException e) {
      throw new TestFailedException("URL creation failed...");
    }

    Searchable s = new DummySearchable(keys, url, ranks);
    SearchService local = (SearchService) searchServices.elementAt(rng.nextInt(numNodes));

    System.out.println("TEST: Inserting dummy document into system...");

    // inserting document into index
    local.index(s);

    String[] keys2 = {"coke", "7up", "pepsi"};
    double[] ranks2 = {0.9, 0.3, 0.3};
    URL url2 = null;

    try {
      url2 = new URL("http://www.drinks2.com");
    } catch (MalformedURLException e) {
      throw new TestFailedException("URL creation failed...");
    }

    Searchable s2 = new DummySearchable(keys2, url2, ranks2);
    SearchService local2 = (SearchService) searchServices.elementAt(rng.nextInt(numNodes));

    System.out.println("TEST: Inserting second dummy document into system...");

    // inserting document into index
    local2.index(s2);

    String[] keys3 = {"coke", "sprite", "barqs", "pepsi"};
    double[] ranks3 = {0.5, 0.9, 0.1, 0.4};
    URL url3 = null;

    try {
      url3 = new URL("http://www.drinks3.com");
    } catch (MalformedURLException e) {
      throw new TestFailedException("URL creation failed...");
    }

    Searchable s3 = new DummySearchable(keys3, url3, ranks3);
    SearchService local3 = (SearchService) searchServices.elementAt(rng.nextInt(numNodes));

    System.out.println("TEST: Inserting third dummy document into system...");

    // inserting document into index
    local3.index(s3);

    String[] keys4 = {"coke", "pepsi", "fanta"};
    double[] ranks4 = {0.3, 0.2, 0.8};
    URL url4 = null;

    try {
      url4 = new URL("http://www.drinks4.com");
    } catch (MalformedURLException e) {
      throw new TestFailedException("URL creation failed...");
    }

    Searchable s4 = new DummySearchable(keys4, url4, ranks4);
    SearchService local4 = (SearchService) searchServices.elementAt(rng.nextInt(numNodes));

    System.out.println("TEST: Inserting fourth dummy document into system...");

    // inserting document into index
    local4.index(s4);

    IndexKeyString[] queryKeys = {new IndexKeyString("coke"), new IndexKeyString("pepsi")};
    URL[] results = local4.query(queryKeys);

    assertTrue("testQuery", "Result set (non-cached) should be 4 URLs",
               results.length == 4);

    /*
     * Cached stuff doesn't work
    IndexKeyString[] queryKeys2 = {new IndexKeyString("coke"), new IndexKeyString("pepsi"), new IndexKeyString("sprite")};
    URL[] results2 = local4.query(queryKeys2);

    assertTrue("testQuery", "Result set (cached) should be 2 URLs",
               results2.length == 2);


    // requery for the results
    IndexKeyString[] queryKeys3 = {new IndexKeyString("coke"), new IndexKeyString("pepsi"), new IndexKeyString("fanta")};
    URL[] results3 = local4.query(queryKeys3);

    assertTrue("testQuery", "Result set (cached) should be 1 URL",
               results3.length == 1);

    // requery for the results
    IndexKeyString[] queryKeys4 = {new IndexKeyString("coke"), new IndexKeyString("pepsi"), new IndexKeyString("blah")};
    URL[] results4 = local4.query(queryKeys4);

    assertTrue("testQuery", "Result set (cached) should be 0 URLs",
               results4.length == 0);

    IndexKeyString[] queryKeys5 = {new IndexKeyString("coke"), new IndexKeyString("pepsi"), new IndexKeyString("sprite"), new IndexKeyString("barqs")};
    URL[] results5 = local4.query(queryKeys5);

    assertTrue("testQuery", "Result set (cached) should be 1 URL",
               results5.length == 1);
    */
  }

  /**
   * Initializes and runs all regression tests.
   */
  public void runTests() {
    initialize();

    try {
      // Run each test
      testInsert();
      testCombinator();
      testFindHighest();
      testIntersection();
      testQuery();
      testCache();
      testCache2();

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
   * Usage: DistPASTSearchRegrTest [-port p] [-bootstrap host[:port]] [-protocol (rmi|wire)] [-help]
   */
  public static void main(String args[]) {

    doInitstuff(args);
    DistPASTSearchRegrTest pastTest = new DistPASTSearchRegrTest();
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

  /**
   * Dummy class provided for testing
   */
  protected class DummySearchable implements Searchable {

    private String[] _keys;
    private URL _url;
    private double[] _ranks;

    public DummySearchable(String[] keys, URL url, double[] ranks) {
      _keys = keys;
      _url = url;
      _ranks = ranks;
    }

    public IndexEntry[] catalog() {
      IndexEntry[] entries = new IndexEntry[_keys.length];
      Date expiration = new Date();
      expiration.setYear(2010);

      for (int i=0; i<entries.length; i++) {
        IndexEntry thisEntry = new IndexEntry(new IndexKeyString(_keys[i]),
                                              _url,
                                              _ranks[i],
                                              expiration,
                                              null);

        entries[i] = thisEntry;
      }

      return entries;
    }

    public URL getURL() {
      return _url;
    }
  }


}
