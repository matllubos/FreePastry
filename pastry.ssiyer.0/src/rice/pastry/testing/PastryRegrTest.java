package rice.pastry.testing;

import rice.pastry.*;
import rice.pastry.direct.*;
import rice.pastry.standard.*;
import rice.pastry.join.*;
import rice.pastry.client.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.pastry.leafset.*;

import java.util.*;

/**
 * PastryRegrTest
 *
 * a regression test suite for pastry.
 *
 * @author andrew ladd
 * @author peter druschel
 */

public class PastryRegrTest {
    private DirectPastryNodeFactory factory;
    private NetworkSimulator simulator;

    private Vector pastryNodes;
    public TreeMap pastryNodesSorted;
    public NodeId pastryNodeLastAdded;
    private Vector rtApps;

    private Random rng;

    public Message lastMsg;
    public NodeId.Distance lastDist;
    public NodeId lastNode;

    // constructor

    public PastryRegrTest() {
	factory = new DirectPastryNodeFactory();
	simulator = factory.getNetworkSimulator();

	pastryNodes = new Vector();
	pastryNodesSorted = new TreeMap();
	rtApps = new Vector();
	rng = new Random();
    }

    public void makePastryNode() {
	PastryNode pn = new PastryNode(factory);
	System.out.println("created " + pn);
	pastryNodes.addElement(pn);
	pastryNodesSorted.put(pn.getNodeId(),pn);
	pastryNodeLastAdded = pn.getNodeId();

	RegrTestApp rta = new RegrTestApp(pn,this);
	rtApps.addElement(rta);

	int n = pastryNodes.size();

	if (n > 1) {
	    PastryNode other = (PastryNode) pastryNodes.get(n - 2);
	    
	    pn.receiveMessage(new InitiateJoin(other));
	}

	checkLeafSet(rta);
	//System.out.println("");
    }

    public void sendPings(int k) {
	int n = rtApps.size();
		
	for (int i=0; i<k; i++) {
	    int from = rng.nextInt(n);
	    int to = rng.nextInt(n);
	    byte[] keyBytes = new byte[16];
	    rng.nextBytes(keyBytes);
	    NodeId key = new NodeId(keyBytes);

	    RegrTestApp rta = (RegrTestApp) rtApps.get(from);
	    PastryNode pn = (PastryNode) pastryNodes.get(to);

	    // send to a  random node
	    rta.sendTrace(pn.getNodeId());
	    while(simulate());

	    // send to a random key
	    rta.sendTrace(key);
	    while(simulate());
	    
	    System.out.println("-------------------");
	}
    }

    public boolean simulate() { 
	return simulator.simulate(); 
    }

    public void checkLeafSet(RegrTestApp rta) {
	//LeafSet = rta.getLeafSet();

	// XXX


    }



    public static void main(String args[]) {
	PastryRegrTest pt = new PastryRegrTest();
	
	int n = 4000;
	int m = 100;
	int k = 3;

	int msgCount = 0;

	Date old = new Date();

	for (int i=0; i<n; i++) {
	    pt.makePastryNode();
	    while (pt.simulate()) msgCount++;

	    if ((i + 1) % m == 0) {
		Date now = new Date();
		System.out.println((i + 1) + " " + (now.getTime() - old.getTime()) + " " + msgCount);
		msgCount = 0;
		old = now;
	    }

	    pt.sendPings(k);
	}
	
	System.out.println(n + " nodes constructed");

	Iterator it = pt.pastryNodesSorted.keySet().iterator();
	while (it.hasNext())
	    System.out.println(it.next());

	//pt.sendPings(k);
    }
}

