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
	//ssiyer//System.out.println("[router] receiveroutemsg " + msg);
	NodeId target = msg.getTarget();
	
	int cwSize = leafSet.cwSize();
	int ccwSize = leafSet.ccwSize();

	int lsPos = leafSet.mostSimilar(target);

	if (lsPos == 0) // message is for the local node so deliver it
	{
	    msg.nextHop = localHandle;
	    //ssiyer//System.out.println("[router] nexthop = localhandle = " + localHandle.getNodeId());
	}

	else if ((lsPos > 0 && lsPos < cwSize) ||
		 //		 (lsPos < 0 && lsPos < ccwSize)) // message is for a node in the leaf set
		 (lsPos < 0 && -lsPos < ccwSize)) // message is for a node in the leaf set
	    {
		NodeHandle handle = leafSet.get(lsPos);

		if (handle.isAlive() == false) {   // node is dead - get rid of it and try again

		    //ssiyer//System.out.println("before: " + leafSet);
		    //ssiyer//System.out.println("[router] leafset dead handle = " + handle.getNodeId());

		    leafSet.remove(handle.getNodeId());
		    //ssiyer//System.out.println("after : " + leafSet);

		    receiveRouteMessage(msg);
		    return;
		}
		else msg.nextHop = handle;
		//ssiyer//System.out.println("[router] leafset live handle = " + handle.getNodeId());
	    }
	else {
	    RouteSet rs = routeTable.getBestEntry(target);
	    NodeHandle handle = null;

	    // get the closest alive node
	    handle = rs.closestNode();

	    if (handle == null) {
		// no live routing table entry matching the next digit
		// get best alternate RT entry
		handle = routeTable.bestAlternateRoute(target);

		if (handle == null) {
		    // no alternate in RT, take leaf set
		    handle = leafSet.get(lsPos);
		}
		else {
		    NodeId.Distance altDist = handle.getNodeId().distance(target);
		    NodeId.Distance lsDist = leafSet.get(lsPos).getNodeId().distance(target);

		    if (lsDist.compareTo(altDist) < 0) {
			// closest leaf set member is closer
			//System.out.println("forw to edge leaf set member, alt=" + handle.getNodeId() + 
			//" lsm=" + leafSet.get(lsPos).getNodeId());
			handle = leafSet.get(lsPos);
		    }
		}
	    }

	    msg.nextHop = handle;
	}

	localHandle.receiveMessage(msg);
    }
}
