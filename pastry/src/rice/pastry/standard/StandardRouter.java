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
import rice.pastry.routing.*;
import rice.pastry.leafset.*;

/**
 * An implementation of the standard Pastry routing algorithm.
 *
 * @author Andrew Ladd
 */

public class StandardRouter implements MessageReceiver {
    private NodeId localId;
    private NodeHandle localHandle;

    private RoutingTable routeTable;
    private LeafSet leafSet;
       
    private Address routeAddress;

    /**
     * Constructor.
     *
     * @param rt the routing table.
     * @param ls the leaf set.
     */

    public StandardRouter(NodeHandle handle, RoutingTable rt, LeafSet ls) {
	localHandle = handle;
	localId = handle.getNodeId();

	routeTable = rt;
	leafSet = ls;

	routeAddress = new RouterAddress();
    }

    /**
     * Gets the address of this component.
     *
     * @return the address.
     */
    
    public Address getAddress() { return routeAddress; }

    /**
     * Receive a message from a remote node.
     *
     * @param msg the message.
     */

    public void receiveMessage(Message msg) {
	if (msg instanceof RouteMessage) {
	    RouteMessage rm = (RouteMessage) msg;

	    if (rm.routeMessage(localHandle.getNodeId()) == false) receiveRouteMessage(rm);
	}
	else {
	    throw new Error("message " + msg + " bounced at StandardRouter");
	}
    }
    
    /**
     * Receive and process a route message.
     *
     * @param msg the message.
     */

    public void receiveRouteMessage(RouteMessage msg) 
    {
	NodeId target = msg.getTarget();
	
	int cwSize = leafSet.cwSize();
	int ccwSize = leafSet.ccwSize();

	int lsPos = leafSet.mostSimilar(target);
	
	if (lsPos == 0) // message is for the local node so deliver it
	    msg.nextHop = localHandle;

	else if ((lsPos > 0 && lsPos < cwSize) ||
		 (lsPos < 0 && lsPos < ccwSize)) // message is for a node in the leaf set
	    {
		NodeHandle handle = leafSet.get(lsPos);

		if (handle.isAlive() == false) {   // node is dead - get rid of it and try again
		    leafSet.remove(handle.getNodeId());
		    receiveRouteMessage(msg);
		    return;
		}
		else msg.nextHop = handle;
	    }
	else {
	    RouteSet rs = routeTable.bestRoute(target);
	    NodeHandle handle;

	    if (rs == null) {
		System.out.println("Empty RT entry for " + target);
		System.out.println(localHandle.getNodeId());
	    }
	    
	    if (rs.size() == 0) 
		handle = leafSet.get(lsPos);  // can't route, route to leaf set
	    // XXX - this is wrong

	    else handle = rs.closestNode(); // route using the table

	    if (handle.isAlive() == false) { // node is dead - get rid of it and try again
		if (rs.size() == 0) leafSet.remove(handle.getNodeId());
		else routeTable.remove(handle.getNodeId());

		receiveRouteMessage(msg);
		return;
	    }
	    else msg.nextHop = handle;
	}

	localHandle.receiveMessage(msg);
    }
}
