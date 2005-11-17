
package rice.p2p.commonapi.testing;

import rice.*;

import rice.environment.Environment;
import rice.p2p.commonapi.*;

import rice.pastry.*;
import rice.pastry.NodeIdFactory;
import rice.pastry.PastryNode;
import rice.pastry.commonapi.*;
import rice.pastry.direct.*;
import rice.pastry.dist.*;
import rice.pastry.standard.*;

import java.util.*;
import java.net.*;
import java.io.*;
import java.io.Serializable;

/**
 * Provides regression testing setup for applications written on top of the
 * commonapi.  Currently is written to use Pastry nodes, but this will be abstracted
 * away. 
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public abstract class CommonAPITest {

  // ----- VARAIBLES -----
  
  // the collection of nodes which have been created
  protected Node[] nodes;


  // ----- PASTRY SPECIFIC VARIABLES -----

  // the factory for creating pastry nodes
  protected PastryNodeFactory factory;

  // the factory for creating random node ids
  protected NodeIdFactory idFactory;

  // the simulator, in case of direct
  protected NetworkSimulator simulator;
  
  // the environment
  protected Environment environment;
  
  // ----- STATIC FIELDS -----

  // the number of nodes to create
  public static int NUM_NODES = 10;

  // the factory which creates pastry ids
  public final IdFactory FACTORY; //= new PastryIdFactory();


  // ----- TESTING SPECIFIC FIELDS -----

  // the text to print to the screen
  public static final String SUCCESS = "SUCCESS";
  public static final String FAILURE = "FAILURE";

  // the width to pad the output
  protected static final int PAD_SIZE = 60;

  // the direct protocol
  public static final int PROTOCOL_DIRECT = -138;

  // the possible network simulation models
  public static final int SIMULATOR_SPHERE = -1;
  public static final int SIMULATOR_EUCLIDEAN = -2;


  // ----- PASTRY SPECIFIC FIELDS -----

  // the port to begin creating nodes on
  public static int PORT = 5009;

  // the host to boot the first node off of
  public static String BOOTSTRAP_HOST = "localhost";

  // the port on the bootstrap to contact
  public static int BOOTSTRAP_PORT = 5009;

  // the procotol to use when creating nodes
  public static int PROTOCOL = DistPastryNodeFactory.PROTOCOL_DEFAULT;

  // the simulator to use in the case of direct
  public static int SIMULATOR = SIMULATOR_SPHERE;

  // the instance name to use
  public static String INSTANCE_NAME = "DistCommonAPITest";

  // ----- ATTEMPT TO LOAD LOCAL HOSTNAME -----
  
  static {
    try {
      BOOTSTRAP_HOST = InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      System.err.println("Error determining local host: " + e);
    }
  }
  
  
  // ----- EXTERNALLY AVAILABLE METHODS -----
  
  /**
   * Constructor, which takes no arguments and sets up the
   * factories in preparation for node creation.
   */
  public CommonAPITest(Environment env) throws IOException {
    this.environment = env;
      FACTORY = new PastryIdFactory(env);
      //idFactory = new IPNodeIdFactory(PORT); 
      idFactory = new RandomNodeIdFactory(environment);

    if (SIMULATOR == SIMULATOR_SPHERE) {
      simulator = new SphereNetwork(env);
    } else {
      simulator = new EuclideanNetwork(env);
    }
    
    if (PROTOCOL == PROTOCOL_DIRECT) {
      factory = new DirectPastryNodeFactory(idFactory, simulator, env);
    } else {
      factory = DistPastryNodeFactory.getFactory(idFactory,
                                                 PROTOCOL,
                                                 PORT,
                                                 env);
    }

    nodes = new Node[NUM_NODES];
  }

  /**
   * Method which creates the nodes
   */
  public void createNodes() {
    for (int i=0; i<NUM_NODES; i++) {
      nodes[i] = createNode(i);
    
      simulate();
    
      processNode(i, nodes[i]);
      simulate();
    
      System.out.println("Created node " + i + " with id " + ((PastryNode) nodes[i]).getNodeId());
    }
  }
  
  /**
   * Method which starts the creation of nodes
   */
  public void start() {
    createNodes();

    System.out.println("\nTest Beginning\n");
    
    runTest();
  }


  // ----- INTERNAL METHODS -----

  /**
   * In case we're using the direct simulator, this method
   * simulates the message passing.
   */
  protected void simulate() {
    if (PROTOCOL == PROTOCOL_DIRECT) {
      while (simulator.simulate()) {}
    } else {
      pause(500);
    }
  }

  /**
   * Method which creates a single node, given it's node
   * number
   *
   * @param num The number of creation order
   * @return The created node
   */
  protected Node createNode(int num) {
    if (num == 0) {
      return factory.newNode((rice.pastry.NodeHandle) null);
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
    if (PROTOCOL == PROTOCOL_DIRECT) {
      return ((DirectPastryNode) nodes[0]).getLocalHandle();
    } else {
      InetSocketAddress address = new InetSocketAddress(BOOTSTRAP_HOST, BOOTSTRAP_PORT);
      return ((DistPastryNodeFactory) factory).getNodeHandle(address);
    }
  }

  /**
   * Method which pauses for the provided number of milliseconds
   *
   * @param ms The number of milliseconds to pause
   */
  protected synchronized void pause(int ms) {
    if (PROTOCOL != PROTOCOL_DIRECT)
      try { wait(ms); } catch (InterruptedException e) {}
  }

  /**
   * Method which kills the specified node
   *
   * @param n The node to kill
   */
  protected void kill(int n) {
    if (PROTOCOL == PROTOCOL_DIRECT)
      ((PastryNode)nodes[n]).destroy();
//      simulator.setAlive((rice.pastry.NodeId) nodes[n].getId(), false);
  }


  // ----- METHODS TO BE PROVIDED BY IMPLEMENTATIONS -----

  /**
   * Method which should process the given newly-created node
   *
   * @param num The number o the node
   * @param node The newly created node
   */
  protected abstract void processNode(int num, Node node);

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
        System.out.println("Usage: DistCommonAPITest [-port p] [-protocol (direct|socket)] [-bootstrap host[:port]] [-help]");
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

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-port") && i+1 < args.length) {
        int p = Integer.parseInt(args[i+1]);
        if (p > 0) PORT = p;
        break;
      }
    }

    BOOTSTRAP_PORT = PORT;  
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-bootstrap") && i+1 < args.length) {
        String str = args[i+1];
        int index = str.indexOf(':');
        if (index == -1) {
          BOOTSTRAP_HOST = str;
          BOOTSTRAP_PORT = PORT;
        } else {
          BOOTSTRAP_HOST = str.substring(0, index);
          BOOTSTRAP_PORT = Integer.parseInt(str.substring(index + 1));
          if (BOOTSTRAP_PORT <= 0) BOOTSTRAP_PORT = PORT;
        }
        break;
      }
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-protocol") && i+1 < args.length) {
        String s = args[i+1];
        if (s.equalsIgnoreCase("socket"))
          PROTOCOL = DistPastryNodeFactory.PROTOCOL_SOCKET;
        else if (s.equalsIgnoreCase("direct"))
          PROTOCOL = PROTOCOL_DIRECT;
        else
          System.out.println("ERROR: Unsupported protocol: " + s);
        break;
      }
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-simulator") && i+1 < args.length) {
        String s = args[i+1];

        if (s.equalsIgnoreCase("sphere"))
          SIMULATOR = SIMULATOR_SPHERE;
        else if (s.equalsIgnoreCase("euclidean"))
          SIMULATOR = SIMULATOR_EUCLIDEAN;
        else
          System.out.println("ERROR: Unsupported simulator: " + s);

        break;
      }
    }
  }
}
