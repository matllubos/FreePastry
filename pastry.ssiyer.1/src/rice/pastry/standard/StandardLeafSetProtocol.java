//////////////////////////////////////////////////////////////////////////////
// Rice Open Source Pastry Implementation                  //               //
//                                                         //  R I C E      //
// Copyright (c)                                           //               //
// Romer Gil                   rgil@cs.rice.edu            //   UNIVERSITY  //
// Andrew Ladd                 aladd@cs.rice.edu           //               //
// Tsuen Wan Ngan              twngan@cs.rice.edu          ///////////////////
//                                                                          //
// This program is free software; you can redistribute it and/or            //
// modify it under the terms of the GNU General Public License              //
// as published by the Free Software Foundation; either version 2           //
// of the License, or (at your option) any later version.                   //
//                                                                          //
// This program is distributed in the hope that it will be useful,          //
// but WITHOUT ANY WARRANTY; without even the implied warranty of           //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the            //
// GNU General Public License for more details.                             //
//                                                                          //
// You should have received a copy of the GNU General Public License        //
// along with this program; if not, write to the Free Software              //
// Foundation, Inc., 59 Temple Place - Suite 330,                           //
// Boston, MA  02111-1307, USA.                                             //
//                                                                          //
// This license has been added in concordance with the developer rights     //
// for non-commercial and research distribution granted by Rice University  //
// software and patent policy 333-99.  This notice may not be removed.      //
//////////////////////////////////////////////////////////////////////////////

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
 * @author Peter Druschel
 * @author Andrew Ladd
 */

public class StandardLeafSetProtocol implements Observer, MessageReceiver {
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

	//ls.addObserver(this);
    }

    /**
     * Gets the address.
     *
     * @return the address.
     */

    public Address getAddress() { return address; }
    
    /**
     * Receives leaf set broadcasts from remote nodes.
     *
     * @param msg the message.
     */

    public void receiveMessage(Message msg) {
	if (msg instanceof BroadcastLeafSet) {
	    BroadcastLeafSet bls = (BroadcastLeafSet) msg;
	    boolean emptyLS = false;

	    NodeHandle from = bls.from();
	    LeafSet remotels = bls.leafSet();
	    
	    int cwSize = remotels.cwSize();
	    int ccwSize = remotels.ccwSize();

	    if (leafSet.size() == 0) emptyLS = true;  // this node is just joining

	    //System.out.println("received leafBC from " + from.getNodeId() + " at " + 
	    //localHandle.getNodeId() + ":" + remotels);

	    // first, merge the received leaf set into our own
	    for (int i=-ccwSize; i<=cwSize; i++) {
		NodeHandle nh;

		if (i == 0) nh = from;
		else nh = remotels.get(i);
		
		nh = security.verifyNodeHandle(nh);
		
		if (nh.isAlive() == false) continue;
		
		// merge into our leaf set
		leafSet.put(nh);

		// update RT as well
		routeTable.put(nh);
	    }
	    
	    // if this node just joined, notify the members of the original leaf set
	    if (emptyLS) broadcast(remotels,from);   

	    // if "from" dropped from our leafset, then we leave the following check for another node
	    if (!leafSet.member(from.getNodeId())) return;

	    // now, check if any of our local leaf set members are missing in the received leaf set
	    cwSize = leafSet.cwSize();
	    ccwSize = leafSet.ccwSize();
	    BroadcastLeafSet bl = new BroadcastLeafSet(localHandle, leafSet);
	    boolean missing = false;
		
	    for (int i=-ccwSize; i<=cwSize; i++) {
		NodeHandle nh;

		if (i == 0) continue;
		nh = leafSet.get(i);

		if (nh.isAlive() == false) continue;
		
		if (remotels.test(nh)) {
		    // member nh is missing from remote leafset, send local leafset
		    //System.out.println("StandardLeafsetProtocol: node " + nh.getNodeId() + " missing from " +
		    //remotels);
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

	else if (msg instanceof RequestLeafSet) {    // reuqest for the leaf set from a remote node
	    RequestLeafSet rls = (RequestLeafSet) msg;

	    NodeHandle returnHandle = rls.returnHandle();

	    returnHandle = security.verifyNodeHandle(returnHandle);
	    
	    if (returnHandle.isAlive()) {
		BroadcastLeafSet bls = new BroadcastLeafSet(localHandle, leafSet);

		returnHandle.receiveMessage(bls);
	    }
	}
	else throw new Error("message received is of unknown type");
    }

    /**
     * Broadcast the leaf set to all members of the local leaf set.
     */
    
    protected void broadcast() {
	BroadcastLeafSet bls = new BroadcastLeafSet(localHandle, leafSet);

	int cwSize = leafSet.cwSize();
	int ccwSize = leafSet.ccwSize();
	
	for (int i=-ccwSize; i<=cwSize; i++) {
	    if (i == 0) continue;
	    
	    NodeHandle nh = leafSet.get(i);
	    
	    nh.receiveMessage(bls);
	}
    }


    /**
     * Broadcast the leaf set to all members of the given leaf set, plus the node from which the leaf set was received.
     *
     * @param ls the leafset whose members we send to local leaf set
     * @param from the node from which ls was received
     */
    
    protected void broadcast(LeafSet ls, NodeHandle from) {
	BroadcastLeafSet bls = new BroadcastLeafSet(localHandle, leafSet);

	int cwSize = ls.cwSize();
	int ccwSize = ls.ccwSize();
	
	for (int i=-ccwSize; i<=cwSize; i++) {
	    NodeHandle nh;

	    if (i == 0) 
		nh = from;
	    else 
		nh = leafSet.get(i);

	    nh = security.verifyNodeHandle(nh);

	    nh.receiveMessage(bls);
	}
    }

    /**
     * Receives updates from the leaf set.
     *
     * @param arg an argument - assumed to NodeSetUpdate.
     */
    
    public void update(Observable obs, Object arg) {	
	/*
	NodeSetUpdate nsu = (NodeSetUpdate) arg;

	if (nsu.wasAdded()) {	    
	    NodeHandle handle = nsu.handle();
	    
	    if (leafSet.get(handle.getNodeId()) == null) return;  // verify the node is in the leafset

	    if (noBroadcast == false) broadcast();
	    else dirty = true;

	    RequestLeafSet rls = new RequestLeafSet(localHandle);

	    handle.receiveMessage(rls);
	}
	else if (noBroadcast == false) {
	    int cwSize = leafSet.cwSize();
	    int ccwSize = leafSet.ccwSize();

	    RequestLeafSet rls = new RequestLeafSet(localHandle);
	    
	    if (cwSize > 0) {
		NodeHandle handle = leafSet.get(cwSize);
		
		handle.receiveMessage(rls);
	    }
	    if (ccwSize > 0) {
		NodeHandle handle = leafSet.get(-ccwSize);

		handle.receiveMessage(rls);
	    }
	}
	*/
    }
}
