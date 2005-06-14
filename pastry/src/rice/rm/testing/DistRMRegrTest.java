package rice.rm.testing;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.environment.params.Parameters;
import rice.environment.params.simple.SimpleParameters;
import rice.pastry.*;
import rice.pastry.standard.*;
import rice.pastry.join.*;
import rice.pastry.client.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.pastry.leafset.*;
import rice.pastry.dist.*;

import rice.rm.*;
import rice.rm.testing.*;

import java.util.*;
import java.io.IOException;
import java.net.*;
import java.rmi.Naming;
import java.rmi.RMISecurityManager;
import java.rmi.RemoteException;
import java.security.*;

/**
 * @(#) DistRMRegrTest.java
 * 
 * A test suite for Replica Manager with RMI/WIRE.
 * 
 * @version $Id$
 * 
 * @author Animesh Nandi
 */

public class DistRMRegrTest {
  private PastryNodeFactory factory;

  private Vector pastryNodes;

  public Vector distClients;

  public Vector localNodes;

  private static int port = 5009;

  private static String bshost = null;

  private static int bsport = 5009;

  private static int numNodes = 5;

  public static int protocol = DistPastryNodeFactory.PROTOCOL_DEFAULT;

  public DistRMRegrTest(Environment env) throws IOException {
    int i;
    NodeId topicId;

    factory = DistPastryNodeFactory.getFactory(new RandomNodeIdFactory(env),
        protocol, port, env);
    pastryNodes = new Vector();
    distClients = new Vector();
    localNodes = new Vector();
  }

  private NodeHandle getBootstrap() {
    InetSocketAddress addr = null;
    if (bshost != null)
      addr = new InetSocketAddress(bshost, bsport);
    else {
      try {
        addr = new InetSocketAddress(InetAddress.getLocalHost().getHostName(),
            port);
      } catch (UnknownHostException e) {
        System.out.println(e);
      }
    }

    NodeHandle bshandle = ((DistPastryNodeFactory) factory).getNodeHandle(addr);
    return bshandle;
  }

  /**
   * process command line args, set the security manager
   */
  private static void doInitstuff(String args[]) {
    // process command line arguments

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-help")) {
        System.out
            .println("Usage: DistRMRegrTest [-port p] [-protocol (rmi|wire)] [-bootstrap host[:port]] [-nodes n] [-help]");
        System.exit(1);
      }
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-port") && i + 1 < args.length) {
        int p = Integer.parseInt(args[i + 1]);
        if (p > 0)
          port = p;
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
          numNodes = n;
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
   * application for this node, and spawn off a separate thread for it.
   * 
   * @return the PastryNode on which the RM application exists
   */
  public PastryNode makeRMNode() {
    boolean firstNodeInSystem = false;
    NodeHandle bootstrap = getBootstrap();
    if (bootstrap == null)
      firstNodeInSystem = true;
    PastryNode pn = factory.newNode(bootstrap); // internally initiateJoins
    pastryNodes.addElement(pn);
    localNodes.addElement(pn.getNodeId());

    Credentials cred = new PermissiveCredentials();

    DistRMRegrTestApp app = new DistRMRegrTestApp(pn, cred, firstNodeInSystem,
        "Instance1");
    RMImpl rm = new RMImpl(pn, app, RMRegrTestApp.rFactor, "Instance1");
    distClients.addElement(app);
    return pn;

  }

  /**
   * Usage: DistRMRegrTest [-nodes n] [-port p] [-bootstrap bshost[:bsport]]
   * [-protocol [wire,rmi]] [-help].
   * 
   * Ports p and bsport refer to WIRE/RMI port numbers (default = 5009). Without
   * -bootstrap bshost[:bsport], only localhost:p is used for bootstrap.
   */
  public static void main(String args[]) throws IOException {
    PastryNode pn;
    
    Parameters params = new SimpleParameters(Environment.defaultParamFileArray,null);
    
    long seed = params.getLong("random_seed");
    if (seed == 0) {
      //seed = -532555437;
      seed = (int)System.currentTimeMillis();
      params.setLong("random_seed", seed);
    }

    System.out.println("seed used=" + seed);
    
    // by properly setting the params first, the enviornment will use
    // the specified seed when creating a default RandomSource
    Environment env = new Environment(null,null,null,null,params);
    
    doInitstuff(args);
    
    DistRMRegrTest driver = new DistRMRegrTest(env);

    // create first node
    pn = driver.makeRMNode();
    bshost = null;

    // We set bshost to null and wait till the first PastryNode on this host is
    // ready so that the
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

    for (int i = 1; i < numNodes; i++) {
      driver.makeRMNode();
    }
    env.getLogManager().getLogger(DistRMRegrTest.class, null).log(Logger.INFO,
        numNodes + " nodes constructed");
  }
}

