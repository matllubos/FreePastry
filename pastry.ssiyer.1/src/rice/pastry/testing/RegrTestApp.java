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
 * a regression test suite for pastry.
 *
 * @author andrew ladd
 * @author peter druschel
 */

public class RegrTestApp extends PastryAppl {

    private static class RTAddress implements Address {
	private int myCode = 0x9219d6ff;
	
	public int hashCode() { return myCode; }

	public boolean equals(Object obj) {
	    return (obj instanceof RTAddress);
	}
    }

	
    private static Credentials cred = new PermissiveCredentials();
    private static Address addr = new RTAddress();

    private class RTMessage extends Message {
	public NodeId source;
	public NodeId target;

	public RTMessage(NodeId src, NodeId tgt) {
	    super(addr);

	    source = src;
	    target = tgt;
	}
	
	public String toString() {
	    String s="";
	    
	    s += "RTMsg from " + source + " to " + target;
	    return s;
	}
    }

    private PastryRegrTest prg;
    
    public RegrTestApp(PastryNode pn, PastryRegrTest prg) {
	super(pn);
	this.prg = prg;
    }

    public Address getAddress() { return addr; }

    public Credentials getCredentials() { return cred; }

    public void sendMsg(NodeId nid) {
	routeMsg(nid, new RTMessage(getNodeId(), nid), cred, new SendOptions());
    }

    public void sendTrace(NodeId nid) {
	//System.out.println("sending a trace from " + getNodeId() + " to " + nid);
	routeMsg(nid, new RTMessage(getNodeId(), nid), cred, new SendOptions());
    }

    public void messageForAppl(Message msg) {
	/*
	System.out.print(msg);
	System.out.println(" received at " + getNodeId());
	*/

	// check if numerically closest
	RTMessage rmsg = (RTMessage)msg;
	NodeId key = rmsg.target;
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
    
    public boolean enrouteMessage(Message msg, NodeId key, NodeId nextHop, SendOptions opt) {
	/*
	System.out.print(msg);
	System.out.println(" at " + getNodeId());
	*/

	NodeId localId = getNodeId();
	NodeId.Distance dist = localId.distance(key);

	if (prg.lastMsg == msg) {
	    if ( (localId.indexOfMSDD(key,4) > prg.lastNode.indexOfMSDD(key,4) &&
		  nextHop != localId) ||
		 (localId.indexOfMSDD(key,4) == prg.lastNode.indexOfMSDD(key,4) && 
		  dist.compareTo(prg.lastDist) > 0) )
		System.out.println("at... " + getNodeId() + " enrouteMessage failure with " + msg +
				   " lastNode=" + prg.lastNode + " lastDist=" + prg.lastDist + 
				   " dist=" + dist + " nextHop=" + nextHop);

	    prg.lastDist = dist;
	}
	prg.lastMsg = msg;
	prg.lastDist = dist;
	prg.lastNode = localId;

	return true;
    }


    public void leafSetChange(NodeId nid, boolean wasAdded) {
	/*
	System.out.println("at... " + getNodeId() + "'s leaf set");
	System.out.print("node " + nid + " was ");
	if (wasAdded) System.out.println("added");
	else System.out.println("removed");
	*/
  
	if (!prg.pastryNodesSorted.containsKey(nid)) 
	    System.out.println("at... " + getNodeId() + "leafSetChange failure 1 with " + nid);
	
	NodeId localId = thePastryNode.getNodeId();

	if (localId == nid) 
	    System.out.println("at... " + getNodeId() + "leafSetChange failure 2 with " + nid);

	int inBetween;

	/*
	if (localId.clockwise(nid)) {
	    // nid is clockwise from this node
	    if (localId.compareTo(nid) < 0)  { // localId < nid?
		inBetween = prg.pastryNodesSorted.subMap(localId,nid).size();
		//System.out.println("c1");
	    }
	    else {
		inBetween = prg.pastryNodesSorted.tailMap(localId).size() + 
		    prg.pastryNodesSorted.headMap(nid).size();
		//System.out.println("c2");
	    }
	}
	else {
	    // nid is counter-clockwise from this node
	    if (localId.compareTo(nid) > 0) { // localId > nid?
		inBetween = prg.pastryNodesSorted.subMap(nid,localId).size();
		//System.out.println("c3");
	    }
	    else {
		inBetween = prg.pastryNodesSorted.tailMap(nid).size() + 
		    prg.pastryNodesSorted.headMap(localId).size();	    
		//System.out.println("c4");
	    }
	}    
	*/

	
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

	if ( (inBetween > lsSize && wasAdded && !prg.pastryNodeLastAdded.equals(getNodeId())) ||
	     (inBetween <= lsSize && !wasAdded && getLeafSet().get(nid) == null) ) {
	    System.out.println("at... " + getNodeId() + "leafSetChange failure 3 with " + nid + 
			       " wasAdded=" + wasAdded + " inBetween=" + inBetween);
	    System.out.println(getLeafSet());
	    Iterator it = prg.pastryNodesSorted.keySet().iterator();
	    while (it.hasNext())
		System.out.println(it.next());
	}

    }


    public void routeSetChange(NodeId nid, boolean wasAdded) {
	/*
	System.out.println("at... " + getNodeId() + "'s route set");
	System.out.print("node " + nid + " was ");
	if (wasAdded) System.out.println("added");
	else System.out.println("removed");

	System.out.println(getRoutingTable());
	*/
    }

}
