package rice.pastry.testing;

import rice.pastry.*;
import rice.pastry.rmi.*;
import rice.pastry.standard.*;
import rice.pastry.join.*;

import java.util.*;
import java.rmi.Naming;
import java.rmi.RMISecurityManager;

/**
 * Pastry test.
 *
 * A test case for pastry RMI. Each node currently starts one instance of
 * Pastry, but pt.makePastryNode can be called multiple times from main
 * (say for the benefit of different and incompatible p2p applications).
 *
 * @author andrew ladd
 * @author sitaram iyer
 */

public class RMIPastryTest {
    private RMIPastryNodeFactory factory;

    private static int port;
    private static String connecthost;
    private static int connectport;

    public RMIPastryTest() {
	factory = new RMIPastryNodeFactory();
    }

    public void makePastryNode() {

	pause();

	// PingClient pc = new PingClient(pn);

	RMIPastryNode other = null;
	try {
	    other = (RMIPastryNode)Naming.lookup("//" + connecthost +
						  ":" + connectport + "/Pastry");
	} catch (Exception e) {
	    System.out.println("Unable to find another node: " + e.toString());
	}

	/*
	 * This creates a PastryNode and an associated RMIPastryNode, and
	 * binds the latter to the registry. We do the above lookup prior to
	 * creating the node, so we don't find ourselves.
	 */
	PastryNode pn = new PastryNode(factory);
	System.out.println("created " + pn);

	/*
	 * don't know anyone else, so we return now and just hang around
	 */
	if (other == null) return;

	NodeId otherid;
	try {
	    otherid = other.getNodeId();
	} catch (Exception e) {
	    System.out.println("Unable to get remote node id: " + e.toString());
	    return;
	}

	RMINodeHandle other_handle = new RMINodeHandle(other, otherid, pn);
	pn.receiveMessage(new InitiateJoin(other_handle));
    }

    /**
     * Usage: RMIPastryTest [-port n] [-connect host[:port]]
     */
    public static void main(String args[])
    {
	// defaults
	connecthost = "thor05";
	port = connectport = 5009;

	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-port") && i+1 < args.length) {
		int p = Integer.parseInt(args[i+1]);
		if (p > 0) port = p;
		break;
	    }
	}
	
	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-connect") && i+1 < args.length) {
		String str = args[i+1];
		int index = str.indexOf(':');
		if (index == -1) {
		    connecthost = str;
		    connectport = port;
		} else {
		    connecthost = str.substring(0, index);
		    connectport = Integer.parseInt(str.substring(index + 1));
		    if (connectport <= 0) connectport = port;
		}
		break;
	    }
	}
	
	RMIPastryTest pt = new RMIPastryTest();

	if (System.getSecurityManager() == null)
	    System.setSecurityManager(new RMISecurityManager());

	try {
	    Runtime.getRuntime().exec("rmiregistry " + port);
	} catch (Exception e) {
	    System.out.println("Unable to start rmiregistry: " + e.toString());
	}

	pt.makePastryNode();
	//pt.sendPings(k);
    }

    private synchronized void pause() {
	try {
	    System.err.print("wait for rmiregistry to start..");
	    wait(1000);
	    System.err.println("okay.");
	} catch (InterruptedException e) {
	}
    }
}
