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

package rice.pastry.standard;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.leafset.*;
import rice.pastry.routing.*;

import java.util.*;

/**
 * An implementation of a simple leaf set protocol.
 *
 * @version $Id$
 *
 * @author Peter Druschel
 * @author Andrew Ladd
 */

public class StandardLeafSetProtocol implements MessageReceiver {
    private NodeHandle localHandle;

    private PastrySecurityManager security;
    private LeafSet leafSet;
    private RoutingTable routeTable;

    private Address address;
    
    public StandardLeafSetProtocol(NodeHandle local, PastrySecurityManager sm, LeafSet ls, RoutingTable rt) {
	localHandle = local;
	security = sm;
	leafSet = ls;
	routeTable = rt;

	address = new LeafSetProtocolAddress();
    }

    /**
     * Gets the address.
     *
     * @return the address.
     */

    public Address getAddress() { return address; }
    
    /**
     * Receives messages.
     *
     * @param msg the message.
     */

    public void receiveMessage(Message msg) {
	if (msg instanceof BroadcastLeafSet) {    // receive a leafset from another node
	    BroadcastLeafSet bls = (BroadcastLeafSet) msg;
	    int type = bls.type();

	    NodeHandle from = bls.from();
	    LeafSet remotels = bls.leafSet();

	    //System.out.println("received leafBC from " + from.getNodeId() + " at " + 
	    //	       localHandle.getNodeId() + "type=" + type + " :" + remotels);

	    // first, merge the received leaf set into our own
	    mergeLeafSet(remotels, from);

	    // if this node just joined, notify the members of our new leaf set
	    if (type == BroadcastLeafSet.JoinInitial) broadcast();   

	    // if this is not a join advertisement, then we are done
	    // or, if "from" dropped from our leafset, then we leave the following check for another node
	    if (type != BroadcastLeafSet.JoinAdvertise || !leafSet.member(from.getNodeId())) return;

	    // else, check if any of our local leaf set members are missing in the received leaf set
	    // if so, we send our leafset to each missing entry and to the source of the leafset
	    // this guarantees correctness in the event of concurrent node joins in the same leaf set
	    checkLeafSet(remotels, from);

	}

	else if (msg instanceof RequestLeafSet) {    // request for leaf set from a remote node
	    RequestLeafSet rls = (RequestLeafSet) msg;

	    NodeHandle returnHandle = rls.returnHandle();
	    returnHandle = security.verifyNodeHandle(returnHandle);

	    if (returnHandle.isAlive()) {
		BroadcastLeafSet bls = new BroadcastLeafSet(localHandle, leafSet, BroadcastLeafSet.Update);

		returnHandle.receiveMessage(bls);
	    }
	}

	else if (msg instanceof InitiateLeafSetMaintenance) {  // request for leafset maintenance
	    
	    // perform leafset maintenance
	    maintainLeafSet();
 
	}
	else throw new Error("message received is of unknown type");
    }

    /**
     * Checks a received leaf set advertisement for concurrently joined nodes
     
     * @param remotels the remote leafset
     * @param from the node from which we received the leafset
     */

    protected void checkLeafSet(LeafSet remotels, NodeHandle from) {

	// check if any of our local leaf set members are missing in the received leaf set
	// if so, we send our leafset to each missing entry and to the source of the leafset
	// this ensures correctness in the event of concurrent node joins in the same leaf set
	int cwSize = leafSet.cwSize();
	int ccwSize = leafSet.ccwSize();
	BroadcastLeafSet bl = new BroadcastLeafSet(localHandle, leafSet, BroadcastLeafSet.Update);
	boolean missing = false;
		
	for (int i=-ccwSize; i<=cwSize; i++) {
	    NodeHandle nh;

	    if (i == 0) continue;
	    nh = leafSet.get(i);

	    if (nh.isAlive() == false) continue;
		
	    if (remotels.test(nh)) {
		// member nh is missing from remote leafset, send local leafset
		//System.out.println("StandardLeafsetProtocol: at " + localHandle.getNodeId() + 
		//	   ", node " + nh.getNodeId() + " missing from " + remotels);
		missing = true;
		nh.receiveMessage(bl);		    
		//System.out.println("sending ls to " + nh.getNodeId());
	    }
	}

	if (missing) {
	    // nodes where missing, send update to "from"
	    from = security.verifyNodeHandle(from);
	    from.receiveMessage(bl);
	    //System.out.println("sending ls to src " + from.getNodeId());
	}

    }


    /**
     * Merge a remote leafset into our own
     
     * @param remotels the remote leafset
     * @param from the node from which we received the leafset
     */

    protected void mergeLeafSet(LeafSet remotels, NodeHandle from) {
	leafSet.merge(remotels, from, routeTable, security);
    }
    
    /**
     * Broadcast the leaf set to all members of the local leaf set.
     */
    
    protected void broadcast() {
	BroadcastLeafSet bls = new BroadcastLeafSet(localHandle, leafSet, BroadcastLeafSet.JoinAdvertise);

	int cwSize = leafSet.cwSize();
	int ccwSize = leafSet.ccwSize();
	
	for (int i=-ccwSize; i<=cwSize; i++) {
	    if (i == 0) continue;
	    
	    NodeHandle nh = leafSet.get(i);
	    if (nh.isAlive() == false) continue;
	    
	    nh.receiveMessage(bls);
	}
    }


    /**
     * Broadcast the local leaf set to all members of the given leaf set, 
     * plus the node from which the leaf set was received.
     *
     * @param ls the leafset whose members we send to local leaf set
     * @param from the node from which ls was received
     */
    
    protected void broadcast(LeafSet ls, NodeHandle from) {
	BroadcastLeafSet bls = new BroadcastLeafSet(localHandle, leafSet, BroadcastLeafSet.JoinAdvertise);

	int cwSize = ls.cwSize();
	int ccwSize = ls.ccwSize();

	//System.out.println("Broadcast: " + leafSet + " from=" + from.getNodeId());
	
	for (int i=-ccwSize; i<=cwSize; i++) {
	    NodeHandle nh;

	    if (i == 0) 
		nh = from;
	    else
		nh = ls.get(i);

	    if (nh.isAlive() == false) continue;

	    nh = security.verifyNodeHandle(nh);
	    
	    //System.out.println("Broadcast: from " + localHandle.getNodeId() + " to " + nh.getNodeId());

	    nh.receiveMessage(bls);

	}
    }

    /**
     * Maintain the leaf set. This method checks for dead leafset entries
     * and replaces them as needed. It is assumed that this method be
     * invoked periodically.
     */
    
    public void maintainLeafSet() {	

	if (Log.ifp(7))
	    System.out.println("maintainLeafSet " + localHandle.getNodeId());

	boolean lostMembers = false;

	// check leaf set for dead entries
	// ccw half
	for (int i=-leafSet.ccwSize(); i<0; i++) {
	    NodeHandle nh = leafSet.get(i);
	    if (!nh.ping()) {
		// remove the dead entry
		leafSet.remove(nh.getNodeId());
		lostMembers = true;
	    }
	}

	// cw half
	for (int i=leafSet.cwSize(); i>0; i--) {
	    NodeHandle nh = leafSet.get(i);
	    if (!nh.ping()) {
		// remove the dead entry
		leafSet.remove(nh.getNodeId());
		lostMembers = true;
	    }
	}

	// if we lost entries, or the size is below max and we don't span the entire ring then
	// request the leafset from other leafset members
	if (lostMembers || 
	    //(leafSet.size() < leafSet.maxSize() && !leafSet.overlaps())) {
	    (leafSet.size() < leafSet.maxSize()) ) {

	    // request leaf sets
	    requestLeafSet();
	}

    }

    /**
     * request the leaf sets from the two most distant members of our leaf set
     */

    private void requestLeafSet() {

	//System.out.println("requestLeafSet");

	RequestLeafSet rls = new RequestLeafSet(localHandle);
	int cwSize = leafSet.cwSize();
	int ccwSize = leafSet.ccwSize();
	boolean allDead = true;

	// request from most distant live ccw entry
	for (int i=-ccwSize; i < 0; i++) {
	    NodeHandle handle = leafSet.get(i);
	    if (handle.isAlive()) {
		handle.receiveMessage(rls);
		allDead = false;
		break;
	    }
	}

	if (allDead && leafSet.size() > 0) 
	    System.out.println("Ring failure at" + localHandle.getNodeId() + "all ccw leafset entries failed");

	allDead = true;
	// request from most distant live cw entry
	for (int i=cwSize; i > 0; i--) {
	    NodeHandle handle = leafSet.get(i);
	    if (handle.isAlive()) {
		handle.receiveMessage(rls);
		allDead = false;
		break;
	    }
	}

	if (allDead && leafSet.size() > 0) 
	    System.out.println("Ring failure at" + localHandle.getNodeId() + "all cw leafset entries failed");

    }

}
