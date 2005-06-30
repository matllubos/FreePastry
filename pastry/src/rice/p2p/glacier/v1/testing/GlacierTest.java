package rice.p2p.glacier.v1.testing;

import rice.*;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.environment.params.Parameters;
import rice.environment.params.simple.SimpleParameters;
import rice.environment.random.RandomSource;
import rice.environment.random.simple.SimpleRandomSource;
import rice.pastry.*;
import rice.pastry.testing.*;
import rice.pastry.commonapi.*;
import rice.pastry.direct.*;
import rice.pastry.standard.*;
import rice.pastry.join.*;
import rice.p2p.glacier.*;
import rice.p2p.glacier.v1.*;
import rice.p2p.glacier.v1.testing.*;
import rice.p2p.past.*;
import rice.p2p.commonapi.IdFactory;
import rice.p2p.multiring.MultiringIdFactory;
import rice.persistence.*;

import java.util.*;
import java.net.*;
import java.io.*;

public class GlacierTest {
  public static String INSTANCE = "GlacierTest";

  public static int REPLICATION_FACTOR = 3;

  public final IdFactory FACTORY; //= new PastryIdFactory();

  private PastryNodeFactory factory;

  private NetworkSimulator simulator;

  private Vector pastryNodes;

  private Vector glaciers;

  private static int numnodes = 20;

  private static int latecomers = 10;

  private static int nummsgs = 30; // total messages

  private static int numFragments = 30;

  private static int numSurvivors = 4;

  private static boolean simultaneous_joins = false;

  private Environment environment;
  
  /**
   * Constructor
   */
  public GlacierTest(Environment env) {
    environment = env;
    FACTORY = new PastryIdFactory(env);
    simulator = new EuclideanNetwork(env);
    factory = new DirectPastryNodeFactory(new RandomNodeIdFactory(environment), simulator,
        env);

    pastryNodes = new Vector();
    glaciers = new Vector();
  }

  public Environment getEnvironment() {
    return environment;
  }
  
  /**
   * Get a handle to a bootstrap node. This is only a simulation, so we pick the
   * most recently created node.
   * 
   * @return handle to bootstrap node, or null.
   */
  private NodeHandle getBootstrap() {
    NodeHandle bootstrap = null;
    try {
      PastryNode lastnode = (PastryNode) pastryNodes.lastElement();
      bootstrap = lastnode.getLocalHandle();
    } catch (NoSuchElementException e) {
    }
    return bootstrap;
  }

  /**
   * Create a Pastry node and add it to pastryNodes. Also create a client
   * application for this node.
   */
  public void makePastryNode(int i) {

    StorageManager pastStor = null, glacierStor = null;
    PastryNode pn = null;

    try {
      pn = factory.newNode(getBootstrap());
      pastryNodes.addElement(pn);

      pastStor = new StorageManagerImpl(FACTORY, new PersistentStorage(FACTORY,
          "past-root-" + i, ".", 1000000, pn.getEnvironment()), new LRUCache(new MemoryStorage(
          FACTORY), 1000000, pn.getEnvironment()));

      glacierStor = new StorageManagerImpl(FACTORY, new PersistentStorage(
          FACTORY, "glacier-root-" + i, ".", 1000000, pn.getEnvironment()), new LRUCache(
          new MemoryStorage(FACTORY), 1000000, pn.getEnvironment()));
    } catch (IOException ioe) {
      System.out.println("makePastryNode(" + i + ") failed, " + ioe);
      ioe.printStackTrace();
      System.exit(1);
    }

    GlacierImpl glac = new GlacierImpl(pn, "glacier-" + i, pastStor,
        glacierStor, new GlacierDefaultPolicy(), REPLICATION_FACTOR,
        numFragments, numSurvivors, null, INSTANCE);
    glaciers.addElement(glac);
    pn.getEnvironment().getLogManager().getLogger(GlacierTest.class, null).log(Logger.INFO,
      "created " + pn);
  }

  /**
   * Print leafsets of all nodes in pastryNodes.
   */
  private void printLeafSets() {
    for (int i = 0; i < pastryNodes.size(); i++) {
      PastryNode pn = (PastryNode) pastryNodes.get(i);
      pn.getEnvironment().getLogManager().getLogger(GlacierTest.class, null).log(Logger.INFO,
        pn.getLeafSet().toString());
    }
  }

  private Id generateId() {
    byte[] data = new byte[20];
    environment.getRandomSource().nextBytes(data);
    return Id.build(data);
  }

  private void randomInsert() {
    final GlacierImpl glac = (GlacierImpl) glaciers.get(environment.getRandomSource().nextInt(glaciers
        .size()));
    final Id fileId = generateId();
    TestContent file = new TestContent(fileId);

    System.err.println("INSERTING id " + fileId + " obj " + file);

    glac.insert(file, new Continuation() {
      public void receive(Object result) throws Exception {
        System.out.println("GREAT");
      }

      public void receiveException(Exception e) {
        System.out.println("BAD2");
      }

      public void receiveResult(Object o) {
        /* we get an array of booleans from the replicas */
        Boolean[] b = (Boolean[]) o;
        for (int i = 0; i < b.length; i++)
          System.out.println("B" + i + " " + b[i]);
      }
    });
  }

  /**
   * Process one message.
   */
  private boolean simulate() {
    return simulator.simulate();
  }

  /**
   * Usage: HelloWorld [-msgs m] [-nodes n]
   * [-simultaneous_joins] [-simultaneous_msgs] [-help]
   */
  public static void main(String args[]) throws IOException {

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-nodes") && i + 1 < args.length)
        numnodes = Integer.parseInt(args[i + 1]);

      if (args[i].equals("-msgs") && i + 1 < args.length)
        nummsgs = Integer.parseInt(args[i + 1]);

      if (args[i].equals("-simultaneous_joins"))
        simultaneous_joins = true;

      if (args[i].equals("-help")) {
        System.out
            .println("Usage: HelloWorld [-msgs m] [-nodes n] [-verbose|-silent|-verbosity v]");
        System.out.println("                  [-simultaneous_joins] [-help]");
        System.exit(1);
      }
    }

    GlacierTest driver = new GlacierTest(new Environment());

    for (int i = 0; i < numnodes; i++) {
      driver.makePastryNode(i);
      if (simultaneous_joins == false)
        while (driver.simulate())
          ;
    }
    if (simultaneous_joins) {
      driver.getEnvironment().getLogManager().getLogger(GlacierTest.class, null).log(Logger.INFO,
        "let the joins begin!");
      while (driver.simulate())
        ;
    }

    driver.getEnvironment().getLogManager().getLogger(GlacierTest.class, null).log(Logger.INFO,
      numnodes + " nodes constructed");

    driver.printLeafSets();

    for (int i = 0; i < nummsgs; i++) {
      driver.randomInsert();
      while (driver.simulate())
        ;
    }

    int turns = 0;
    while (true) {
      while (driver.simulate())
        ;
      System.err.print(".");
      try {
        Thread.currentThread().sleep(1000);
      } catch (InterruptedException ie) {
      }

      turns++;
      if (turns == 10) {
        for (int i = numnodes; i < (numnodes + latecomers); i++) {
          driver.makePastryNode(i);
          while (driver.simulate())
            ;
        }
      }
    }
  }
}
