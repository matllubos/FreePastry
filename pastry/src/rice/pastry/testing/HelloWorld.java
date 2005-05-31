package rice.pastry.testing;

import rice.environment.Environment;
import rice.pastry.*;
import rice.pastry.direct.*;
import rice.pastry.standard.*;
import rice.pastry.join.*;

import java.util.*;

/**
 * A hello world example for pastry. This is the "direct" driver.
 * 
 * @version $Id$
 * 
 * @author Sitaram Iyer
 */

public class HelloWorld {
  private PastryNodeFactory factory;

  private NetworkSimulator simulator;

  private Vector pastryNodes;

  private Vector helloClients;

  private Random rng;

  private static int numnodes = 3;

  private static int nummsgs = 3; // total messages

  private static boolean simultaneous_joins = false;

  private static boolean simultaneous_msgs = false;

  /**
   * Constructor
   */
  public HelloWorld(Environment env) {
    simulator = new EuclideanNetwork(env);
    factory = new DirectPastryNodeFactory(new RandomNodeIdFactory(), simulator, env);

    pastryNodes = new Vector();
    helloClients = new Vector();
    rng = new Random(PastrySeed.getSeed());
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
  public void makePastryNode() {
    PastryNode pn = factory.newNode(getBootstrap());
    pastryNodes.addElement(pn);

    HelloWorldApp app = new HelloWorldApp(pn);
    helloClients.addElement(app);
    if (Log.ifp(5))
      System.out.println("created " + pn);
  }

  /**
   * Print leafsets of all nodes in pastryNodes.
   */
  private void printLeafSets() {
    for (int i = 0; i < pastryNodes.size(); i++) {
      PastryNode pn = (PastryNode) pastryNodes.get(i);
      if (Log.ifp(5))
        System.out.println(pn.getLeafSet());
    }
  }

  /**
   * Invoke a HelloWorldApp method called sendRndMsg. First choose a random
   * application from helloClients.
   */
  private void sendRandomMessage() {
    int n = helloClients.size();
    int client = rng.nextInt(n);
    HelloWorldApp app = (HelloWorldApp) helloClients.get(client);
    app.sendRndMsg(rng);
  }

  /**
   * Process one message.
   */
  private boolean simulate() {
    return simulator.simulate();
  }

  /**
   * Usage: HelloWorld [-msgs m] [-nodes n] [-verbose|-silent|-verbosity v]
   * [-simultaneous_joins] [-simultaneous_msgs] [-help]
   */
  public static void main(String args[]) {

    Log.init(args);

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-nodes") && i + 1 < args.length)
        numnodes = Integer.parseInt(args[i + 1]);

      if (args[i].equals("-msgs") && i + 1 < args.length)
        nummsgs = Integer.parseInt(args[i + 1]);

      if (args[i].equals("-simultaneous_joins"))
        simultaneous_joins = true;

      if (args[i].equals("-simultaneous_msgs"))
        simultaneous_msgs = true;

      if (args[i].equals("-help")) {
        System.out
            .println("Usage: HelloWorld [-msgs m] [-nodes n] [-verbose|-silent|-verbosity v]");
        System.out
            .println("                  [-simultaneous_joins] [-simultaneous_msgs] [-help]");
        System.exit(1);
      }
    }

    HelloWorld driver = new HelloWorld(new Environment());

    for (int i = 0; i < numnodes; i++) {
      driver.makePastryNode();
      if (simultaneous_joins == false)
        while (driver.simulate())
          ;
    }
    if (simultaneous_joins) {
      if (Log.ifp(5))
        System.out.println("let the joins begin!");
      while (driver.simulate())
        ;
    }

    if (Log.ifp(5))
      System.out.println(numnodes + " nodes constructed");

    driver.printLeafSets();

    for (int i = 0; i < nummsgs; i++) {
      driver.sendRandomMessage();
      if (simultaneous_msgs == false)
        while (driver.simulate())
          ;
    }

    if (simultaneous_msgs) {
      if (Log.ifp(5))
        System.out.println("let the msgs begin!");
      while (driver.simulate())
        ;
    }
  }
}