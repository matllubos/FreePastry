package rice.pastry.testing;

import rice.environment.Environment;
import rice.pastry.*;
import rice.pastry.dist.*;
import rice.pastry.standard.*;
import rice.pastry.join.*;
import rice.pastry.client.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.pastry.leafset.*;

import java.util.*;
import java.io.IOException;
import java.net.*;
import java.rmi.Naming;
import java.rmi.RMISecurityManager;
import java.rmi.RemoteException;

/**
 * a regression test suite for pastry with "distributed" nodes. All nodes are on
 * one physical machine, but they communicate through one of the network
 * transport protocols, i.e., RMI or WIRE.
 * 
 * See the usage for more information, the -protocol option can be used to
 * specify which protocol to run the test with.
 * 
 * @version $Id$
 * 
 * @author Alan Mislove
 */

public class DistPastryRegrTest extends PastryRegrTest {

  private static int port = 5009;

  private static String bshost;

  private static int bsport = 5009;

  private static int numnodes = 10;

  private static int protocol = DistPastryNodeFactory.PROTOCOL_DEFAULT;

  private InetSocketAddress bsaddress;

  static {
    try {
      bshost = InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      System.out.println("Error determining local host: " + e);
    }
  }

  // constructor

  public DistPastryRegrTest(Environment env) throws IOException {
    super(env);

    // we need to wrap the TreeMap to synchronize it
    // -- it is shared among multiple virtual nodes
    pastryNodesSorted = Collections.synchronizedSortedMap(pastryNodesSorted);

    factory = DistPastryNodeFactory.getFactory(new IPNodeIdFactory(InetAddress.getLocalHost(), port, env),
        protocol, port, env);

    try {
      bsaddress = new InetSocketAddress(bshost, bsport);
    } catch (Exception e) {
      System.out.println("ERROR (init): " + e);
    }
  }

  /**
   * Gets a handle to a bootstrap node.
   * 
   * @param firstNode true if bootstraping the first virtual node on this host
   * @return handle to bootstrap node, or null.
   */
  protected NodeHandle getBootstrap(boolean firstNode) {
    if (firstNode)
      return ((DistPastryNodeFactory) factory).getNodeHandle(bsaddress);
    else {
      InetSocketAddress addr = null;
      try {
        addr = new InetSocketAddress(InetAddress.getLocalHost().getHostName(),
            port);
      } catch (UnknownHostException e) {
        System.out.println(e);
      }
      return ((DistPastryNodeFactory) factory).getNodeHandle(addr);
    }
  }

  /**
   * process command line args, set the RMI security manager, and start the RMI
   * registry. Standard gunk that has to be done for all Dist apps.
   */
  private static void doInitstuff(String args[]) {
    // process command line arguments

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-help")) {
        System.out
            .println("Usage: DistPastryRegrTest [-port p] [-protocol (rmi|wire|socket)] [-nodes n] [-bootstrap host[:port]] [-help]");
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

    bsport = port; // make sure bsport = port, if no -bootstrap argument is
                   // provided
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

        //          if (s.equalsIgnoreCase("wire"))
        //            protocol = DistPastryNodeFactory.PROTOCOL_WIRE;
        //          else if (s.equalsIgnoreCase("rmi"))
        //            protocol = DistPastryNodeFactory.PROTOCOL_RMI;
        //          else
        if (s.equalsIgnoreCase("socket"))
          protocol = DistPastryNodeFactory.PROTOCOL_SOCKET;
        else
          System.out.println("ERROR: Unsupported protocol: " + s);

        break;
      }
    }
  }

  /**
   * wire protocol specific handling of the application object e.g., RMI may
   * launch a new thread
   * 
   * @param pn pastry node
   * @param app newly created application
   */
  protected void registerapp(PastryNode pn, RegrTestApp app) {
  }

  // do nothing in the DIST world
  public boolean simulate() {
    return false;
  }

  public synchronized void pause(int ms) {
    System.out.println("Waiting " + ms + "ms...");
    try {
      wait(ms);
    } catch (InterruptedException e) {
    }
  }

  public boolean isReallyAlive(NodeHandle nh) {
    // xxx
    return false;
  }

  protected void killNode(PastryNode pn) {
    ((DistPastryNode) pn).destroy();
  }

  /**
   * Usage: DistRegrPastryTest [-port p] [-protocol (wire|rmi)] [-nodes n]
   * [-bootstrap host[:port]] [-help]
   */

  public static void main(String args[]) throws IOException {
    doInitstuff(args);
    DistPastryRegrTest pt = new DistPastryRegrTest(new Environment());
    mainfunc(pt, args, numnodes /* n */, 1 /* d */, 1/* k */, 20/* m */, 4/* conc */);
  }
}
