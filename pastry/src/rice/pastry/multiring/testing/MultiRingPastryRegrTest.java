/*************************************************************************

"Free Pastry" Peer-to-Peer Application Development Substrate

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

package rice.pastry.multiring.testing;

import java.io.*;
import java.net.*;

import rice.pastry.*;
import rice.pastry.client.*;
import rice.pastry.dist.*;
import rice.pastry.messaging.*;
import rice.pastry.multiring.*;
import rice.pastry.routing.*;
import rice.pastry.security.*;
import rice.pastry.standard.*;
import rice.pastry.wire.*;

public class MultiRingPastryRegrTest {

  private static int port = 5009;
  private static String bshost = "localhost";
  private static int bsport = 5009;
  private static int numnodes = 1;
  private static int protocol = DistPastryNodeFactory.PROTOCOL_WIRE;

  private static int NUM_SUBRINGS = 2;
  private static int NUM_GLOBAL_NODES_PER_SUBRING = 2;
  private static int NUM_LOCAL_NODES_PER_SUBRING = 1;

  private DistPastryNodeFactory distFactory;
  private MultiRingPastryNodeFactory factory;
  private MultiRingPastryNode[][] nodes;
  private MultiRingPastryNode[][] globalNodes;
  private MultiRingTestApp[][] apps;
  private MultiRingTestApp[][] globalApps;
  private RingId[] ringIds;

  public MultiRingPastryRegrTest() {
    distFactory = DistPastryNodeFactory.getFactory(new RandomNodeIdFactory(), protocol, port);
    factory = new MultiRingPastryNodeFactory(distFactory);

    createPastryNodes();
    createApps();

    startTest();
  }

  public void startTest() {
    RingNodeId dest = new RingNodeId(nodes[NUM_SUBRINGS-1][NUM_GLOBAL_NODES_PER_SUBRING].getNodeId(),
                                     ringIds[NUM_SUBRINGS-1]);

    System.out.println("Sending message from " + globalNodes[0][0].getNodeId() + " to " + dest);

    apps[0][NUM_GLOBAL_NODES_PER_SUBRING].sendTo(dest);
  }

  public void createPastryNodes() {
    globalNodes = new MultiRingPastryNode[NUM_SUBRINGS][NUM_GLOBAL_NODES_PER_SUBRING];
    nodes = new MultiRingPastryNode[NUM_SUBRINGS][NUM_GLOBAL_NODES_PER_SUBRING+NUM_LOCAL_NODES_PER_SUBRING];
    ringIds = new RingId[NUM_SUBRINGS];

    for (int i=0; i<NUM_SUBRINGS; i++) {
      for (int j=0; j<NUM_GLOBAL_NODES_PER_SUBRING; j++) {
        globalNodes[i][j] = (MultiRingPastryNode) factory.newNode(getGlobalBootstrap((i==0) && (j==0)));
        while (! globalNodes[i][j].isReady()) {
          pause(1000);
        }

        System.out.println("Created node " + globalNodes[i][j].getNodeId() + " in global ring.");

        nodes[i][j] =  (MultiRingPastryNode) factory.joinRing(globalNodes[i][j], getLocalBootstrap(i, j==0));
        while (! nodes[i][j].isReady()) {
          pause(1000);
        }

        if (j == 0)
          ringIds[i] = nodes[i][j].getMultiRingAppl().getRingId();
        
        System.out.println("Created node " + nodes[i][j].getNodeId() + " in ring " + ringIds[i] + ".");
      }
    }

    for (int i=0; i<NUM_SUBRINGS; i++) {
      for (int j=NUM_GLOBAL_NODES_PER_SUBRING; j<NUM_GLOBAL_NODES_PER_SUBRING+NUM_LOCAL_NODES_PER_SUBRING; j++) {
        nodes[i][j] = (MultiRingPastryNode) factory.newNode(getLocalBootstrap(i, false));
        while (! nodes[i][j].isReady()) {
          pause(1000);
        }

        System.out.println("Created node " + nodes[i][j].getNodeId() + " in local ring only.");
      }
    }
  }

  public void createApps() {
    globalApps = new MultiRingTestApp[NUM_SUBRINGS][NUM_GLOBAL_NODES_PER_SUBRING];
    apps = new MultiRingTestApp[NUM_SUBRINGS][NUM_GLOBAL_NODES_PER_SUBRING+NUM_LOCAL_NODES_PER_SUBRING];

    for (int i=0; i<NUM_SUBRINGS; i++) {
      for (int j=0; j<NUM_GLOBAL_NODES_PER_SUBRING; j++) {
        globalApps[i][j] = new MultiRingTestApp(globalNodes[i][j]);
      }
    }

    for (int i=0; i<NUM_SUBRINGS; i++) {
      for (int j=0; j<NUM_GLOBAL_NODES_PER_SUBRING+NUM_LOCAL_NODES_PER_SUBRING; j++) {
        apps[i][j] = new MultiRingTestApp(nodes[i][j]);
      }
    }
  }
  
  protected NodeHandle getLocalBootstrap(int ring, boolean firstNode) {
    if (firstNode) {
      return null;
    } else {
      InetSocketAddress addr = null;

      try {
        addr = new InetSocketAddress(InetAddress.getLocalHost().getHostName(), port + (2 * ring * NUM_GLOBAL_NODES_PER_SUBRING) + 1);
      } catch (UnknownHostException e) {
        System.out.println(e);
      }

      return distFactory.getNodeHandle(addr);
    }
  }
  
  protected NodeHandle getGlobalBootstrap(boolean firstNode) {
    if (firstNode) {
      return null;
    } else {
      InetSocketAddress addr = null;
      
      try {
        addr = new InetSocketAddress(InetAddress.getLocalHost().getHostName(), port);
      } catch (UnknownHostException e) {
        System.out.println(e);
      }
      
      return distFactory.getNodeHandle(addr);
    }
  }

  private static class MultiRingTestApp extends PastryAppl {

    private Credentials credentials = new PermissiveCredentials();
    
    public MultiRingTestApp(PastryNode pn) {
      super(pn);
    }

    public Credentials getCredentials() {
      return credentials;
    }

    public Address getAddress() {
      return MultiRingTestAppAddress.instance();
    }

    public void messageForAppl(Message m) {
      System.out.println("Received message " + m + " at " + thePastryNode);
    }

    public void sendTo(RingNodeId ringId) {
      routeMsg(ringId, new MultiRingTestAppMessage(ringId), credentials, null);
    }
  }

  public static class MultiRingTestAppMessage extends Message implements Serializable {

    private RingNodeId ringId;
    
    public MultiRingTestAppMessage(RingNodeId ringId) {
      super(MultiRingTestAppAddress.instance());

      this.ringId = ringId;
    }

    public RingNodeId getRingNodeId() {
      return ringId;
    }
  }

    
  public static class MultiRingTestAppAddress implements Address {

    private static MultiRingTestAppAddress _instance;

    private int _code = 0x0495acd9;

    public static MultiRingTestAppAddress instance() {
      if(null == _instance) {
        _instance = new MultiRingTestAppAddress();
      }
      return _instance;
    }

    private MultiRingTestAppAddress() {}

    public int hashCode() { return _code; }

    public boolean equals(Object obj) {
      return (obj instanceof MultiRingTestAppAddress);
    }
  } 

  public synchronized void pause(int ms) {
    System.out.println("Waiting " + ms + "ms...");
    try { wait(ms); } catch (InterruptedException e) {}
  }

  private static void processArgs(String args[]) {
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-help")) {
        System.out.println("Usage: MultiRingPastryRegrTest [-port p] [-protocol (rmi|wire)] [-nodes n] [-bootstrap host[:port]] [-help]");
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
      if (args[i].equals("-nodes") && i+1 < args.length) {
        int n = Integer.parseInt(args[i+1]);
        if (n > 0) numnodes = n;
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

  public static void main(String[] args) {
    processArgs(args);

    MultiRingPastryRegrTest test = new MultiRingPastryRegrTest();
  //  test.start();
  }
}