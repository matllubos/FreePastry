
package rice.pastry.testing;

import rice.pastry.*;
import rice.pastry.direct.*;
import rice.pastry.standard.*;
import rice.pastry.join.*;
import rice.pastry.client.*;
import rice.pastry.security.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;

import java.util.*;
import java.io.*;

/**
 * A hello world example for pastry. This is the per-node app object.
 *
 * @version $Id$
 *
 * @author Sitaram Iyer
 */

public class HelloWorldApp extends PastryAppl {

    private int msgid = 0;

    private static Address addr = new HelloAddress();
    private static Credentials cred = new PermissiveCredentials();

    private static class HelloAddress implements Address {
	private int myCode = 0x1984abcd;
	
	public int hashCode() { return myCode; }

	public boolean equals(Object obj) {
	    return (obj instanceof HelloAddress);
	}

	public String toString() { return "[HelloAddress]"; }
    }

    public HelloWorldApp(PastryNode pn) {
	super(pn);
    }

    /**
     * Sends a message to a randomly chosen node. Yeah, for fun.
     *
     * @param rng Random number generator.
     */
    public void sendRndMsg(Random rng) {
	Id rndid = Id.makeRandomId(rng);
	if (Log.ifp(5))
	    System.out.println("Sending message from " + getNodeId() + " to random dest " + rndid);
	Message msg = new HelloMsg(addr, thePastryNode.getLocalHandle(), rndid, ++msgid);
	routeMsg(rndid, msg, cred, new SendOptions());
    }

    // The remaining methods override abstract methods in the PastryAppl API.

    /**
     * Get address.
     *
     * @return the address of this application.
     */
    public Address getAddress() { return addr; }

    /**
     * Get credentials.
     *
     * @return credentials.
     */
    public Credentials getCredentials() { return cred; }

    /**
     * Invoked on destination node when a message arrives.
     *
     * @param msg Message being routed around
     */
    public void messageForAppl(Message msg) {
	if (Log.ifp(5))
	    System.out.println("Received " + msg + " at " + getNodeId());
    }
    
    /**
     * Invoked on intermediate nodes in routing path.
     *
     * @param msg Message that's passing through this node.
     * @param key destination
     * @param nextHop next hop
     * @param opt send options
     * @return true if message needs to be forwarded according to plan.
     */
    public boolean enrouteMessage(Message msg, Id key, NodeId nextHop, SendOptions opt) {
	if (Log.ifp(5))
	    System.out.println("Enroute " + msg + " at " + getNodeId());
	return true;
    }

    /**
     * Invoked upon change to leafset.
     *
     * @param nh node handle that got added/removed
     * @param wasAdded added (true) or removed (false)
     */
    public void leafSetChange(NodeHandle nh, boolean wasAdded) {
	if (Log.ifp(5)) {
	    System.out.print("In " + getNodeId() + "'s leaf set, " +
			     "node " + nh.getNodeId() + " was ");
	    if (wasAdded) System.out.println("added");
	    else System.out.println("removed");
	}
    }

    /**
     * Invoked upon change to routing table.
     *
     * @param nh node handle that got added/removed
     * @param wasAdded added (true) or removed (false)
     */
    public void routeSetChange(NodeHandle nh, boolean wasAdded) {
	if (Log.ifp(5)) {
	    System.out.print("In " + getNodeId() + "'s route set, " +
			     "node " + nh.getNodeId() + " was ");
	    if (wasAdded) System.out.println("added");
	    else System.out.println("removed");
	}
    }

    /**
     * Invoked by {RMI,Direct}PastryNode when the node has something in its
     * leaf set, and has become ready to receive application messages.
     */
    public void notifyReady() {
	if (true /*Log.ifp(6)*/)
	    System.out.println("Node " + getNodeId() + " ready, waking up any clients");
	//sendRndMsg(new Random());

    }
}

