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

    public RMIPastryTest() {
	factory = new RMIPastryNodeFactory();
    }

    public void makePastryNode() {

	pause(2000);

	RMIPastryNode other = null;
	try {
	    other = (RMIPastryNode)Naming.lookup("//thor05:" + 5009 + "/Pastry");
	} catch (Exception e) {
	    System.out.println("Unable to find another node: " + e.toString());
	}

	// this also creates an associated RMIPastryNode and binds it:
	PastryNode pn = new PastryNode(factory);
	System.out.println("created " + pn);

	// PingClient pc = new PingClient(pn);

	if (other != null) {
	    RMINodeHandle other_handle = new RMINodeHandle(other);
	    pn.receiveMessage(new InitiateJoin(other_handle));
	}
    }

    public static void main(String args[]) {
	RMIPastryTest pt = new RMIPastryTest();
	
	if (System.getSecurityManager() == null)
	    System.setSecurityManager(new RMISecurityManager());

	try {
	    Runtime.getRuntime().exec("rmiregistry " + 5009);
	} catch (Exception e) {
	    System.out.println("Unable to start rmiregistry: " + e.toString());
	}

	pt.makePastryNode();
	//pt.sendPings(k);
    }

    private synchronized void pause(int ms) {
	try {
	    System.err.print("wait for rmiregistry to start..");
	    wait(1000);
	    System.err.println("okay.");
	} catch (InterruptedException e) {
	}
    }
}
