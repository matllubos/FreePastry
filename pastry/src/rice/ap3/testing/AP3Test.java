package rice.ap3.testing;

import rice.ap3.*;

import rice.pastry.*;
import rice.pastry.direct.*;
import rice.pastry.standard.*;
import rice.pastry.join.*;

import java.util.*;

/**
 * @(#) AP3Test.java
 *
 * Tests AP3ServiceImpl.
 *
 * @version $Id$
 # @author Gaurav Oberoi
 */

public class AP3Test {
    private DirectPastryNodeFactory factory;
    private NetworkSimulator simulator;

    private Vector pastryNodes;
    private Vector ap3Nodes;

    private Random rng;

    public AP3Test() {
	factory = new DirectPastryNodeFactory();
	simulator = factory.getNetworkSimulator();

	pastryNodes = new Vector();
	ap3Nodes = new Vector();
	rng = new Random();
    }

    private NodeHandle getBootstrap() {
	NodeHandle bootstrap = null;
	try {
	    PastryNode lastnode = (PastryNode) pastryNodes.lastElement();
	    bootstrap = lastnode.getLocalHandle();
	} catch (NoSuchElementException e) {
	}
	return bootstrap;
    }

    public void makePastryNode() {
	PastryNode pn = factory.newNode(getBootstrap());
	pastryNodes.addElement(pn);
	
	AP3Client ap3Client = new AP3TestingClient(pn);
	ap3Nodes.addElement(ap3Client);
    }

  /*
    public void sendPings(int k) {
	int n = pingClients.size();
		
	for (int i=0; i<k; i++) {
	    int from = rng.nextInt(n);
	    int to = rng.nextInt(n);
	    
	    PingClient pc = (PingClient) pingClients.get(from);
	    PastryNode pn = (PastryNode) pastryNodes.get(to);

	    pc.sendTrace(pn.getNodeId());

	    while(simulate());
	    
	    System.out.println("-------------------");
	}
    }
  */

    public void makeRequests(int k) {
	int n = ap3Nodes.size();
		
	for (int i=0; i<k; i++) {
	    int nodeIndex = rng.nextInt(n);

	    AP3TestingClient ap3Node = (AP3TestingClient) ap3Nodes.get(nodeIndex);

	    ap3Node.getService().getAnonymizedContent("requestMsg", 0.50, 10000);

	    while(simulate());
	    
	    System.out.println("-------------------");
	}
    }

    public boolean simulate() { 
	return simulator.simulate(); 
    }

    public static void main(String args[]) {
	AP3Test ap3Test = new AP3Test();
	
	int n = 40;
	int m = 5;
	int k = 5;

	int msgCount = 0;

	Date old = new Date();

	for (int i=0; i<n; i++) {
	    ap3Test.makePastryNode();
	    while (ap3Test.simulate()) msgCount++;

	    if ((i + 1) % m == 0) {
		Date now = new Date();
		System.out.println((i + 1) + " " + (now.getTime() - old.getTime()) + " " + msgCount);
		msgCount = 0;
		old = now;
	    }
	}
	
	System.out.println(n + " nodes constructed");
	
	ap3Test.makeRequests(k);
    }
}
