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

public class RegrTestApp extends PastryAppl {

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
	routeMsg(nid, new RTMessage(addr, getNodeId(), nid),
		 cred, new SendOptions());
    }

    public void sendTrace(NodeId nid) {
	//System.out.println("sending a trace from " + getNodeId() + " to " + nid);
	routeMsg(nid, new RTMessage(addr, getNodeId(), nid),
		 cred, new SendOptions());
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


    public void leafSetChange(NodeHandle nh, boolean wasAdded) {
	NodeId nid = nh.getNodeId();

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
	      !prg.pastryNodesLastAdded.contains(getNodeId()) && !prg.inConcJoin) ||
	     (inBetween <= lsSize && !wasAdded && getLeafSet().get(nid) == null) && prg.pastryNodes.contains(nh) ) {
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
    }

}

/**
 * DO NOT declare this inside PingClient; see HelloWorldApp for details.
 */
class RTMessage extends Message {
    public NodeId source;
    public NodeId target;

    public RTMessage(Address addr, NodeId src, NodeId tgt) {
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
