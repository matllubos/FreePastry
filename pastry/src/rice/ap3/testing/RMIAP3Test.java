package rice.ap3.testing;

import rice.ap3.*;

import rice.pastry.*;
import rice.pastry.rmi.*;
import rice.pastry.standard.*;

import java.util.*;
import java.net.*;
import java.rmi.Naming;
import java.rmi.RMISecurityManager;

/**
 * @(#) RMIAP3Test.java
 *
 * Tests AP3ServiceImpl using RMI.
 *
 * @version $Id$
 * @author Charlie Reis
 * @author Gaurav Oberoi
 */

public class RMIAP3Test {
    private PastryNodeFactory factory;
    private Vector pastrynodes;
    private Vector ap3Nodes;

    private Random rng;

    private static int port = 5009;
    private static String bshost = "localhost";
    private static int bsport = 5009;
    private static int numnodes = 50;

    public RMIAP3Test() {
	factory = new RMIPastryNodeFactory(port);
	pastrynodes = new Vector();
	ap3Nodes = new Vector();
	rng = new Random();
    }

    /**
     * Gets a handle to a bootstrap node. First tries localhost, to see
     * whether a previous virtual node has already bound itself. Then it
     * tries nattempts times on bshost:bsport.
     *
     * @return handle to bootstrap node, or null.
     */
    protected NodeHandle getBootstrap() {
	RMIRemoteNodeI bsnode = null;
	try {
	    bsnode = (RMIRemoteNodeI)Naming.lookup("//:" + port + "/Pastry");
	} catch (Exception e) {
	    System.out.println("Unable to find bootstrap node on localhost");
	}

	int nattempts = 3;

	// if bshost:bsport == localhost:port then nattempts = 0.
	// waiting for ourselves is not harmful, but pointless, and denies
	// others the usefulness of symmetrically waiting for us.

	if (bsport == port) {
	    InetAddress localaddr = null, connectaddr = null;
	    String host = null;

	    try {
		host = "localhost"; localaddr = InetAddress.getLocalHost();
		connectaddr = InetAddress.getByName(host = bshost);
	    } catch (UnknownHostException e) {
		System.out.println("[rmi] Error: Host unknown: " + host);
		nattempts = 0;
	    }

	    if (nattempts != 0 && localaddr.equals(connectaddr))
		nattempts = 0;
	}

	for (int i = 1; bsnode == null && i <= nattempts; i++) {
	    try {
		bsnode = (RMIRemoteNodeI)Naming.lookup("//" + bshost
							 + ":" + bsport
							 + "/Pastry");
	    } catch (Exception e) {
		System.out.println("Unable to find bootstrap node on "
				   + bshost + ":" + bsport
				   + " (attempt " + i + "/" + nattempts + ")");
	    }

	    if (i != nattempts)
		pause(1000);
	}

	NodeId bsid = null;
	if (bsnode != null) {
	    try {
		bsid = bsnode.getNodeId();
	    } catch (Exception e) {
		System.out.println("[rmi] Unable to get remote node id: " + e.toString());
		bsnode = null;
	    }
	}

	RMINodeHandle bshandle = null;
	if (bsid != null)
	    bshandle = new RMINodeHandle(bsnode, bsid);

	return bshandle;
    }

    /**
     * process command line args, set the RMI security manager, and start
     * the RMI registry. Standard gunk that has to be done for all RMI apps.
     */
    private static void doRMIinitstuff(String args[]) {
	// process command line arguments

	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-help")) {
		System.out.println("Usage: RMIAP3Test [-port p] [-nodes n] [-bootstrap host[:port]] [-help]");
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

	// set RMI security manager

	if (System.getSecurityManager() == null)
	    System.setSecurityManager(new RMISecurityManager());

	// start RMI registry

	try {
	    java.rmi.registry.LocateRegistry.createRegistry(port);
	} catch (Exception e) {
	    System.out.println("Error starting RMI registry: " + e);
	}
    }

    public void makePastryNode() {
	// or, for a sweet one-liner,
	// pastrynodes.add(new RMIPastryNode(factory, getBootstrap()));

	PastryNode pn = factory.newNode(getBootstrap());
	pastrynodes.add(pn);

	AP3Client ap3Client = new AP3TestingClient(pn);
	ap3Nodes.addElement(ap3Client);
	System.out.println("created " + pn);
    }

    /**
    public void printLeafSets() {
	pause(1000);
	for (int i = 0; i < pastrynodes.size(); i++)
	    System.out.println(((PastryNode)pastrynodes.get(i)).getLeafSet());
    }
    */

    public synchronized void pause(int ms) {
	System.out.println("waiting for " + (ms/1000) + " sec");
	try { wait(ms); } catch (InterruptedException e) {}
    }

  /*
  public void makeRequests(int k) {
    int n = ap3Nodes.size();
    
    for (int i=0; i<k; i++) {
      int nodeIndex = rng.nextInt(n);
      
      AP3TestingClient ap3Node = (AP3TestingClient) ap3Nodes.get(nodeIndex);
      
      ap3Node.getService().getAnonymizedContent("requestMsg", 0.100);
      
      //while(simulate());
      
      System.out.println("\n\n------------------- Finished making requests\n\n");
    }
  }
  */

  public void makeRequests(int k) {
    // Send one message only, from the first node to the second one in the list
    AP3TestingClient sourceNode = (AP3TestingClient) ap3Nodes.get(0);
    AP3TestingClient destNode = (AP3TestingClient) ap3Nodes.get(1);
    
    PastryNode sourcePastryNode = (PastryNode) pastrynodes.get(0);
    PastryNode destPastryNode = (PastryNode) pastrynodes.get(1);

    /**
    Enumeration nodes = pastrynodes.elements();
    while (nodes.hasMoreElements()) {
      PastryNode node = (PastryNode) nodes.nextElement();
      while (!node.isReady()) {
        pause(1000);
        System.out.println("DEBUG ---------- Waiting for node to be ready");
      }
    }
    */

    //while(!sourcePastryNode.isReady()) {
    //  System.out.println("DEBUG ----------------- Source node not ready");
    //}
    //while(!destPastryNode.isReady()) {
    //  System.out.println("DEBUG ----------------- Dest node not ready");
    //}
      
    sourceNode.getService().setRandomNode(destNode.getService().getNodeId());
    String content = (String) sourceNode.getService().getAnonymizedContent("requestMsg", 1);

    System.out.println("\n\nDEBUG ----------- CONTENT RECEIVED: " + content);

    System.out.println("\n\n------------------- Finished making requests\n\n");
  }
  

  /**
   * Usage: RMIAP3Test [-port p] [-nodes n] [-bootstrap host[:port]] [-help]
   */
  public static void main(String args[]) {
    int k = 5; // number of messages to send
    
    doRMIinitstuff(args);
    RMIAP3Test ap3Test = new RMIAP3Test();
    
    for (int i = 0; i < numnodes; i++)
      ap3Test.makePastryNode();
    System.out.println(numnodes + " nodes constructed");

    ap3Test.pause(20000);
    System.out.println("DEBUG ---------- Done waiting for 20 seconds\n\n");

    ap3Test.makeRequests(k);
    
    //while (true)
    //for (int i = 0; i < 20; i++)
    //    pt.printLeafSets();
    System.out.println("\n\n------------------- Finished initializing\n\n");
  }
}
