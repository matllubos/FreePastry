/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/

package rice.testharness;

import rice.pastry.client.*;
import rice.pastry.security.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.pastry.*;
import rice.pastry.direct.*;
import rice.pastry.dist.*;
import rice.pastry.wire.*;
import rice.pastry.standard.*;

import rice.scribe.*;
import rice.scribe.messaging.*;

import rice.testharness.messaging.*;

import java.util.*;
import java.io.*;
import java.net.*;


/**
 * A TestHarness is a PastryAppl with allows the user to run tests,
 * collect data, and publish new versions of the TestHarness
 * in a Pastry network.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */

public class DistTestHarnessRunner {

  /**
   * The port to start the first node on.
   */
  private static int defaultStartPort = 10293;

  /**
   * The port to start the first node on.
   */
  private int startPort;

  /**
   * The port on the bootstrapHost to look for the bootstrap node on
   */
  public static int bootstrapPort = 10293;

  /**
   * The name of the machine on which to look for the bootstrap node
   */
  public static String bootstrapHost = "ron0.emulab.net";

  private static byte[] bootstrapNodeIdArray = new byte[32];

  private Vector _pastryNodes;
  private Vector _testNodes;

  private DistPastryNodeFactory _factory;

  /**
   * Constructor which creates a TestHarness given a
   * PastryNode.
   *
   * @param pn The PastryNode this TestHarness is running on.
   */
  public DistTestHarnessRunner(int num, int port) {
    startPort = port;

    _pastryNodes = new Vector();
    _testNodes = new Vector();
    _factory = DistPastryNodeFactory.getFactory(new RandomNodeIdFactory(), DistPastryNodeFactory.PROTOCOL_WIRE, startPort);

    // create all of the nodes
    for (int i=0; i<num; i++) {
      createNode(i);
    }

    boolean quit = false;
    BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
    String command = null;

    // loop over commands read from System.in
    while( !quit ) {
      try {
        System.out.print("\ncommand: ");
        command = input.readLine();
        quit = parseInput( command );
      }
      catch( Exception e ) {
        System.out.println("ERROR (DistHarnessTestRunner): " + e );
        quit = true;
      }
    }
  }

  /**
   * Creates a Pastry Node, Scribe Node, and TestHarness Node for
   * testing.
   *
   * @param num The index of this node.
   */
  private void createNode(final int num) {
    int port = startPort + num;

    DistPastryNode pn = null;

    try {
      pn = createPastryNode(port);
    } catch (Exception e) {
      System.out.println(e);
    }

    System.err.println("Created node " + num + " " + pn.getNodeId());

    TestHarness test = new TestHarness(pn);

    while (! pn.isReady()) {
      pause(500);
    }

    test.initialize();

    _pastryNodes.addElement(pn);
    _testNodes.addElement(test);
  }


  /**
   * Creates a new PastryNode to be used by services.
   *
   * The current implementation attempts to join an existing Pastry network
   * simply by looking for a PastryNode on dosa.cs.rice.edu.
   *
   * @param ifBootstrap The NodeId to use if this is the bootstrap node.
   * @param useDefault Whether or not we should check to see if we are the bootstrap node
   * @return a new local PastryNode
   */
  private DistPastryNode createPastryNode(int port) throws Exception {
    InetSocketAddress address = null;
    InetSocketAddress bootAddress = null;

    try {
      address = new InetSocketAddress(InetAddress.getLocalHost(), port);
      bootAddress = new InetSocketAddress(bootstrapHost, bootstrapPort);
    } catch (Exception e) {
      System.out.println("ERROR (doStuff): " + e);
    }

    DistPastryNode pn = null;

    pn = (DistPastryNode) _factory.newNode((DistNodeHandle) getPastryBootstrap());

    return pn;
  }

  /**
     * Gets a handle to a bootstrap PastryNode. First tries localhost, to see
     * whether a previous virtual node has already bound itself. Then it
     * tries nattempts times on bshost:bsport.
     *
     * Currently hardcoded to look at dosa.cs.rice.edu:5009.
     *
     * @return handle to bootstrap node, or null.
     */
  private NodeHandle getPastryBootstrap() throws Exception {
    if (_pastryNodes.size() == 0) {
      InetSocketAddress bootAddress = null;

      try {
        bootAddress = new InetSocketAddress(bootstrapHost, bootstrapPort);
      } catch (Exception e) {
        System.out.println("ERROR (getBootstrap): " + e);
      }

      return _factory.getNodeHandle(bootAddress);
    } else {
      return _factory.getNodeHandle(((WireNodeHandle) ((PastryNode) _pastryNodes.elementAt(_pastryNodes.size() - 1)).getLocalHandle()).getAddress());
    }
  }

  /**
   * Initializes the TestHarnesss are waits for input.
   */
  public static void main(String[] args) {
    int numNodes = 10;

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-nodes") && i+1 < args.length) {
        int n = Integer.parseInt(args[i+1]);
        if (n > 0) numNodes = n;
        break;
      }
    }

    int port = defaultStartPort;

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-bootstrap") && i+1 < args.length) {
        String str = args[i+1];
        int index = str.indexOf(':');
        if (index == -1) {
          bootstrapHost = str;
          bootstrapPort = port;
        } else {
          bootstrapHost = str.substring(0, index);
          int tmpport = Integer.parseInt(str.substring(index + 1));
          if (tmpport > 0) {
            bootstrapPort = tmpport;
            port = tmpport;
          }
        }

        break;
      }
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-port") && i+1 < args.length) {
        int n = Integer.parseInt(args[i+1]);
        if (n > 0) port = n;
        break;
      }
    }

    new DistTestHarnessRunner(numNodes, port);
  }

  /* Basically a big switch/case for reading the input and acting accordingly
   */
  private boolean parseInput( String in ) {
    try {
      StringTokenizer tokened = new StringTokenizer( in, " \t\n" );
      if( !tokened.hasMoreTokens() ) {
        return false;
      }

      String token = tokened.nextToken();

      if( token.startsWith( "quit" ) ) {
        System.exit(0);
        return true;

      } else if ( token.startsWith( "listnodes" ) ) {

        if (((TestHarness) _testNodes.elementAt(0))._subscribedNodes.size() == 0) {
          System.out.println("ERROR: 'listallnodes' can only be run on the control node.");
          return false;
        }

        System.out.println("Current nodes in the system:");

        for (int i=0; i<((TestHarness) _testNodes.elementAt(0))._subscribedNodes.size(); i++) {
          NodeId nh = (NodeId) ((TestHarness) _testNodes.elementAt(0))._subscribedNodes.elementAt(i);
          System.out.println("  " + i + ": " + nh);
        }

      } else if ( token.startsWith( "listlocalnodes" ) ) {

        System.out.println("Current nodes on this host:");

        for (int i=0; i<_pastryNodes.size(); i++) {
          NodeId nh = ((PastryNode) _pastryNodes.elementAt(i)).getNodeId();
          System.out.println("  " + i + ": " + nh);
        }

      } else if ( token.startsWith( "addnode" ) ) {

        int num_nodes = 1;

        try {
          num_nodes = Integer.parseInt(tokened.nextToken());
        } catch (NoSuchElementException e) {
        } catch (NumberFormatException e) {
          throw new NoSuchElementException("addnode syntax invalid");
        }

        for (int i=0; i<num_nodes; i++) {
          int num = _pastryNodes.size();
          createNode(num);
        }

      } else if ( token.startsWith( "delnode" ) ) {

        int num = Integer.parseInt(tokened.nextToken());

        if ((num < 0) || (num >= _pastryNodes.size())) {
          System.out.println("ERROR: node_number out of range");
          return false;
        }

        PastryNode pn = (PastryNode) _pastryNodes.elementAt(num);
        TestHarness thi = (TestHarness) _testNodes.elementAt(num);

        _pastryNodes.removeElementAt(num);
        _testNodes.removeElementAt(num);

        thi.kill();
        pauseQuiet(2000);
        ((DistPastryNode) pn).kill();

      } else if ( token.startsWith( "inittest" ) ) {
        String run_name = tokened.nextToken();
        String class_name = tokened.nextToken();

        Message m = new InitTestMessage(run_name, class_name);

        ((TestHarness) _testNodes.elementAt(0)).sendToAll(m);

      } else if ( token.startsWith( "collect" ) ) {
        String name = tokened.nextToken();

        Message m = new CollectResultsMessage(name, ((PastryNode) _pastryNodes.elementAt(0)).getLocalHandle());

        ((TestHarness) _testNodes.elementAt(0)).sendToAll(m);

      } else if ( token.startsWith( "starttest" ) ) {
        String node = tokened.nextToken();
        String name = tokened.nextToken();

        Vector nodeIds = ((TestHarness) _testNodes.elementAt(0))._subscribedNodes;

        NodeId[] nodes = new NodeId[nodeIds.size()];

        for (int i=0; i<nodes.length; i++) {
          nodes[i] = (NodeId) nodeIds.elementAt(i);
        }

        Message m = new StartTestMessage(name, nodes);

        if (node.equals("all")) {
          ((TestHarness) _testNodes.elementAt(0)).sendToAll(m);
        } else {
          int node_num = 0;

          try {
            node_num = Integer.parseInt(node);
          } catch (NumberFormatException e) {
            throw new NoSuchElementException("starttest syntax invalid");
          }

          if ((node_num < 0) || (node_num >= nodes.length)) {
            throw new NoSuchElementException("starttest syntax invalid");
          }

          ((TestHarness) _testNodes.elementAt(0)).send(m, nodes[node_num]);
        }
      } else if ( token.startsWith( "help" ) ) {
        System.out.println("COMMANDS AVAILABLE:");
        System.out.println("General Commands:");
        System.out.println("\thelp");
        System.out.println("\tquit");
        System.out.println();
        System.out.println("Node Control Commands:");
        System.out.println("\tlistlocalnodes");
        System.out.println("\tlistallnodes");
        System.out.println("\taddnode [num_nodes]");
        System.out.println("\tdelnode node_number");
        System.out.println();
        System.out.println("Test Control Commands:");
        System.out.println("\tinittest run_name test_class");
        System.out.println("\tstarttest (0..n-1|all) run_name");
        System.out.println("\tcollect run_name");
      } else {
        System.out.println("ERROR: Unrecognized command: '" + token + "'.  Type 'help' for help.");
      }
    } catch (NoSuchElementException e) {
      System.out.println("ERROR: Command syntax invalid.  Type 'help' for help.");
    }

    return false;
  }

  public static synchronized void pause(int ms) {
    System.err.println("waiting for " + (ms/1000) + " sec");
    System.out.println("waiting for " + (ms/1000) + " sec");
    try { Thread.currentThread().sleep(ms); } catch (InterruptedException e) {}
  }

  public static synchronized void pauseQuiet(int ms) {
    try { Thread.currentThread().sleep(ms); } catch (InterruptedException e) {}
  }  
}
