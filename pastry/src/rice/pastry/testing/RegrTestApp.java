
package rice.pastry.testing;

import rice.pastry.*;
import rice.pastry.direct.*;
import rice.pastry.standard.*;
import rice.pastry.join.*;
import rice.pastry.client.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;

import java.util.*;

/**
 * RegrTestApp
 *
 * A regression test suite for pastry. This is the per-node app object.
 *
 * @version $Id$
 *
 * @author andrew ladd
 * @author peter druschel
 */

public class RegrTestApp extends CommonAPIAppl {

    private static class RTAddress implements Address {
	private int myCode = 0x9219d6ff;
	
	public int hashCode() { return myCode; }

	public boolean equals(Object obj) {
	    return (obj instanceof RTAddress);
	}

	public String toString() { return "[RTAddress]"; }
    }

	
    private static Credentials cred = new PermissiveCredentials();
    private static Address addr = new RTAddress();

    private PastryRegrTest prg;
    
    public RegrTestApp(PastryNode pn, PastryRegrTest prg) {
	super(pn);
	this.prg = prg;
    }

    public Address getAddress() { return addr; }

    public Credentials getCredentials() { return cred; }

    public void sendMsg(NodeId nid) {
	routeMsg(nid, new RTMessage(addr, getNodeHandle(), nid),
		 cred, new SendOptions());
    }

    public void sendTrace(NodeId nid) {
	//System.out.println("sending a trace from " + getNodeId() + " to " + nid);
	routeMsg(nid, new RTMessage(addr, getNodeHandle(), nid),
		 cred, new SendOptions());
    }

    //public void messageForAppl(Message msg) {
    public void deliver(Id key, Message msg) {

	/*
	System.out.print(msg);
	System.out.println(" received at " + getNodeId());
	*/

	// check if numerically closest
	RTMessage rmsg = (RTMessage)msg;
	//NodeId key = rmsg.target;
	NodeId localId = getNodeId();

	if (localId != key) {
	    int inBetween;
	    if (localId.compareTo(key) < 0) {
		int i1 = prg.pastryNodesSorted.subMap(localId,key).size();
		int i2 = prg.pastryNodesSorted.tailMap(key).size() + 
		    prg.pastryNodesSorted.headMap(localId).size();	    
	    
		inBetween = (i1 < i2) ? i1 : i2;
	    }
	    else {
		int i1 = prg.pastryNodesSorted.subMap(key,localId).size();
		int i2 = prg.pastryNodesSorted.tailMap(localId).size() + 
		    prg.pastryNodesSorted.headMap(key).size();

		inBetween = (i1 < i2) ? i1 : i2;
	    }

	    if (inBetween > 1) {
		System.out.println("messageForAppl failure, inBetween=" + inBetween);
		System.out.print(msg);
		System.out.println(" received at " + getNodeId());
		System.out.println(getLeafSet());
	    }
	}
    }
    
    //public boolean enrouteMessage(Message msg, Id key, NodeId nextHop, SendOptions opt) {
    public void forward(RouteMessage rm) {
	/*
	System.out.print(msg);
	System.out.println(" at " + getNodeId());
	*/
	Message msg = rm.unwrap();
	Id key = rm.getTarget();
	NodeId nextHop = rm.getNextHop().getNodeId();

	NodeId localId = getNodeId();
	NodeId.Distance dist = localId.distance(key);
	int base = getRoutingTable().baseBitLength();

	if (prg.lastMsg == msg) {
	    int localIndex = localId.indexOfMSDD(key,base);
	    int lastIndex = prg.lastNode.indexOfMSDD(key,base);

	    if ( (localIndex > lastIndex && nextHop != localId) ||
		 (localIndex == lastIndex && dist.compareTo(prg.lastDist) > 0) )
		System.out.println("at... " + getNodeId() + " enrouteMessage failure with " + msg +
				   " lastNode=" + prg.lastNode + " lastDist=" + prg.lastDist + 
				   " dist=" + dist + " nextHop=" + nextHop + 
				   " loci=" + localIndex + " lasti=" + lastIndex);

	    prg.lastDist = dist;
	}
	prg.lastMsg = msg;
	prg.lastDist = dist;
	prg.lastNode = localId;

	//return true;
    }


    //public void leafSetChange(NodeHandle nh, boolean wasAdded) {
    public void update(NodeHandle nh, boolean wasAdded) {
	final NodeId nid = nh.getNodeId();

	/*
	System.out.println("at... " + getNodeId() + "'s leaf set");
	System.out.print("node " + nid + " was ");
	if (wasAdded) System.out.println("added");
	else System.out.println("removed");
	*/
  
	if (!prg.pastryNodesSorted.containsKey(nid) && nh.isAlive()) 
	    System.out.println("at... " + getNodeId() + "leafSetChange failure 1 with " + nid);
	
	NodeId localId = thePastryNode.getNodeId();

	if (localId == nid) 
	    System.out.println("at... " + getNodeId() + "leafSetChange failure 2 with " + nid);

	int inBetween;

	if (localId.compareTo(nid) < 0)  { // localId < nid?
	    int i1 = prg.pastryNodesSorted.subMap(localId,nid).size();
	    int i2 = prg.pastryNodesSorted.tailMap(nid).size() + 
		prg.pastryNodesSorted.headMap(localId).size();	    
	    
	    inBetween = (i1 < i2) ? i1 : i2;
	}
	else {
	    int i1 = prg.pastryNodesSorted.subMap(nid,localId).size();
	    int i2 = prg.pastryNodesSorted.tailMap(localId).size() + 
		prg.pastryNodesSorted.headMap(nid).size();

	    inBetween = (i1 < i2) ? i1 : i2;
	}

	int lsSize = getLeafSet().maxSize() / 2;

	if ( (inBetween > lsSize && wasAdded && 
	      !prg.pastryNodesLastAdded.contains(thePastryNode) && !prg.inConcJoin) ||
	     (inBetween <= lsSize && !wasAdded && !getLeafSet().member(nh)) && 
	      prg.pastryNodesSorted.containsKey(nh.getNodeId()) ) {

	    System.out.println("at... " + getNodeId() + "leafSetChange failure 3 with " + nid + 
			       " wasAdded=" + wasAdded + " inBetween=" + inBetween);
	    System.out.println(getLeafSet());
	    /*
	    Iterator it = prg.pastryNodesSorted.keySet().iterator();
	    while (it.hasNext())
		System.out.println(it.next());
	    */
	}

    }


    public void routeSetChange(NodeHandle nh, boolean wasAdded) {
	NodeId nid = nh.getNodeId();

	/*
	System.out.println("at... " + getNodeId() + "'s route set");
	System.out.print("node " + nid + " was ");
	if (wasAdded) System.out.println("added");
	else System.out.println("removed");
	System.out.println(getRoutingTable());
	*/

	if (!prg.pastryNodesSorted.containsKey(nid)) {
	    if (nh.isAlive() || wasAdded) 
		System.out.println("at... " + getNodeId() + "routeSetChange failure 1 with " + nid +
				   " wasAdded=" + wasAdded);
	}

    }


    /**
     * Invoked when the Pastry node has joined the overlay network and
     * is ready to send and receive messages
     */
    
    public void notifyReady() {
	//if (getLeafSet().size() == 0) System.out.println("notifyReady at " + getNodeId() + " : leafset is empty!!");
    }


}

/**
 * DO NOT declare this inside PingClient; see HelloWorldApp for details.
 */
class RTMessage extends Message {
    public NodeHandle sourceNode;
    //public NodeId source;
    public NodeId target;

    public RTMessage(Address addr, NodeHandle src, NodeId tgt) {
	super(addr);
	sourceNode = src;
	//source = src.getNodeId();
	target = tgt;
    }

    public String toString() {
	String s="";
	s += "RTMsg from " + sourceNode + " to " + target;
	return s;
    }
}
