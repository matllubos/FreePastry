package rice.pastry.testing;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.pastry.*;
import rice.pastry.standard.*;
import rice.pastry.dist.*;

import java.util.*;
import java.io.IOException;
import java.net.*;

/**
 * A hello world example for pastry. This is the distributed driver.
 * 
 * For example, run with default arguments on two machines, both with args
 * -bootstrap firstmachine. Read the messages and follow the protocols.
 * 
 * Either separately or with the above, try -nodes 3 and -nodes 20. Try -msgs
 * 100. Try the two machine configuration, kill it, restart, and watch it join
 * as a different node. Do that a few times, watch LeafSet entries accumulate,
 * then in 30 seconds, watch the maintenance take over.
 * 
 * @version $Id$
 * 
 * @author Sitaram Iyer
 * @author Peter Druschel
 */

public class DistHelloWorld {
  private PastryNodeFactory factory;

  private Vector pastryNodes;

  private Vector helloClients;

  private static int port = 5009;

  private static String bshost = null;

  private static int bsport = 5009;

  private static int numnodes = 5;

  private static int nummsgs = 2; // per virtual node

  public static int protocol = DistPastryNodeFactory.PROTOCOL_DEFAULT;

  public Environment environment;
  /**
   * Constructor
   */
  public DistHelloWorld(Environment env) throws IOException {
    environment = env;
    factory = DistPastryNodeFactory.getFactory(new IPNodeIdFactory(InetAddress.getLocalHost(), port, env),
        protocol, port, env);
    pastryNodes = new Vector();
    helloClients = new Vector();
  }

  /**
   * Gets a handle to a bootstrap node. First we try localhost, to see whether a
   * previous virtual node has already bound itself there. Then we try nattempts
   * times on bshost:bsport. Then we fail.
   * 
   * @param firstNode true of the first virtual node is being bootstrapped on
   *          this host
   * @return handle to bootstrap node, or null.
   */
  protected NodeHandle getBootstrap(boolean firstNode) {
    InetSocketAddress addr = null;
    if (firstNode && bshost != null)
      addr = new InetSocketAddress(bshost, bsport);
    else {
      try {
        addr = new InetSocketAddress(InetAddress.getLocalHost().getHostName(),
            bsport);
      } catch (UnknownHostException e) {
        System.out.println(e);
      }
    }

    NodeHandle bshandle = ((DistPastryNodeFactory) factory).getNodeHandle(addr);
    return bshandle;
  }

  /**
   * process command line args,
   */
  private static void doIinitstuff(String args[]) {

    // process command line arguments

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-help")) {
        System.out
            .println("Usage: DistHelloWorld [-msgs m] [-nodes n] [-port p] [-bootstrap bshost[:bsport]]");
        System.out
            .println("                     [-protocol (rmi|wire)] [-verbose|-silent|-verbosity v] [-help]");
        System.out.println("");
        System.out
            .println("  Ports p and bsport refer to RMI registry  or Socket port numbers (default = 5009).");
        System.out
            .println("  Without -bootstrap bshost[:bsport], only localhost:p is used for bootstrap.");
        System.out
            .println("  Default verbosity is 5, -verbose is 10, and -silent is -1 (error msgs only).");
        System.exit(1);
      }
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-msgs") && i + 1 < args.length)
        nummsgs = Integer.parseInt(args[i + 1]);
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-port") && i + 1 < args.length) {
        int p = Integer.parseInt(args[i + 1]);
        if (p > 0) {
          port = p;
          bsport = p;
        }
        break;
      }
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-bootstrap") && i + 1 < args.length) {
        String str = args[i + 1];
        int index = str.indexOf(':');
        if (index == -1) {
          bshost = str;
          bsport = port;
        } else {
          bshost = str.substring(0, index);
          bsport = Integer.parseInt(str.substring(index + 1));
          if (bsport <= 0)
            bsport = port;
        }
        break;
      }
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-nodes") && i + 1 < args.length) {
        int n = Integer.parseInt(args[i + 1]);
        if (n > 0)
          numnodes = n;
        break;
      }
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-protocol") && i + 1 < args.length) {
        String s = args[i + 1];

        //		if (s.equalsIgnoreCase("wire"))
        //		    protocol = DistPastryNodeFactory.PROTOCOL_WIRE;
        //    else if (s.equalsIgnoreCase("rmi"))
        //      protocol = DistPastryNodeFactory.PROTOCOL_RMI;
        //    else
        if (s.equalsIgnoreCase("socket"))
          protocol = DistPastryNodeFactory.PROTOCOL_SOCKET;
        else
          System.out.println("ERROR: Unsupported protocol: " + s);

        break;
      }
    }

  }

  /**
   * Create a Pastry node and add it to pastryNodes. Also create a client
   * application for this node, so that when this node comes up ( when
   * pn.isReady() is true) , this application's notifyReady() method is called,
   * and it can do any interesting stuff it wants.
   */
  public PastryNode makePastryNode(boolean firstNode) {
    NodeHandle bootstrap = getBootstrap(firstNode);
    PastryNode pn = factory.newNode(bootstrap); // internally initiateJoins
    pastryNodes.addElement(pn);

    HelloWorldApp app = new HelloWorldApp(pn);
    helloClients.addElement(app);
    pn.getEnvironment().getLogManager().getLogger(DistHelloWorld.class, null).log(Logger.INFO, 
        "created " + pn);
    return pn;
  }

  /**
   * Usage: DistHelloWorld [-msgs m] [-nodes n] [-port p] [-bootstrap
   * bshost[:bsport]] [-verbose|-silent|-verbosity v] [-help].
   * 
   * Ports p and bsport refer to RMI registry/ Socket port numbers (default =
   * 5009). Without -bootstrap bshost[:bsport], only localhost:p is used for
   * bootstrap. Default verbosity is 5, -verbose is 10, and -silent is -1 (error
   * msgs only).
   */
  public static void main(String args[]) throws IOException {
    Environment env = new Environment();
    doIinitstuff(args);
    DistHelloWorld driver = new DistHelloWorld(env);

    // create first node
    PastryNode pn = driver.makePastryNode(true);

    // We wait till the first PastryNode on this host is ready so that the
    // rest of the nodes find a bootstrap node on the local host
    synchronized (pn) {
      while (!pn.isReady()) {
        try {
          pn.wait();
        } catch (InterruptedException e) {
          System.out.println(e);
        }
      }
    }

    for (int i = 1; i < numnodes; i++) {
      pn = driver.makePastryNode(false);
      synchronized (pn) {
        while (!pn.isReady()) {
          try {
            pn.wait();
          } catch (InterruptedException e) {
            System.out.println(e);
          }
        }
      }
    }

    env.getLogManager().getLogger(DistHelloWorld.class, null).log(Logger.INFO, 
        numnodes + " nodes constructed");

    for (int i = 0; i < nummsgs; i++) {
      for (int client = 0; client < driver.helloClients.size(); client++) {
        HelloWorldApp app = (HelloWorldApp) driver.helloClients.get(client);
        app.sendRndMsg(driver.environment.getRandomSource());
      }
    }
  }
}

