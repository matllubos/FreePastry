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
 * @author Andrew Ladd
 */

public class StandardLeafSetProtocol implements Observer, MessageReceiver {
    private NodeHandle localHandle;

    private PastrySecurityManager security;
    private LeafSet leafSet;
    private RoutingTable routeTable;

    private Address address;

    private boolean noBroadcast;
    private boolean dirty;
    
    public StandardLeafSetProtocol(NodeHandle local, PastrySecurityManager sm, LeafSet ls, RoutingTable rt) {
	localHandle = local;
	security = sm;
	leafSet = ls;
	routeTable = rt;

	address = new LeafSetProtocolAddress();

	ls.addObserver(this);

	noBroadcast = false;
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
	    
	    NodeHandle from = bls.from();
	    LeafSet remotels = bls.leafSet();
	    
	    int cwSize = remotels.cwSize();
	    int ccwSize = remotels.ccwSize();
	    
	    noBroadcast = true;
	    dirty = false;

	    for (int i=-ccwSize; i<=cwSize; i++) {
		NodeHandle nh;

		if (i == 0) nh = from;
		else nh = remotels.get(i);
		
		nh = security.verifyNodeHandle(nh);
		
		if (nh.isAlive() == false) continue;
		
		leafSet.put(nh);
		// update RT as well - PD
		routeTable.put(nh);
	    }
	    
	    if (dirty) broadcast();

	    noBroadcast = false;
	}
	else if (msg instanceof RequestLeafSet) {
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
     * Mass broadcast the leaf set.
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
     * Receives updates from the leaf set.
     *
     * @param arg an argument - assumed to NodeSetUpdate.
     */
    
    public void update(Observable obs, Object arg) {	
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
    }
}
