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
import rice.pastry.leafset.*;
import rice.pastry.routing.*;
import rice.pastry.security.*;
import rice.pastry.join.*;

import java.util.*;

/**
 * An implementation of a simple join protocol.
 *
 * @author Andrew Ladd
 */

public class StandardJoinProtocol implements MessageReceiver 
{
    private NodeHandle localHandle;
    private PastrySecurityManager security;
    private RoutingTable routeTable;
    private LeafSet leafSet;
    
    private Address address;

    /**
     * Constructor.
     *
     * @param lh the local node handle.
     * @param sm the Pastry security manager.
     */

    public StandardJoinProtocol(NodeHandle lh, PastrySecurityManager sm, RoutingTable rt, LeafSet ls) {
	localHandle = lh;
	security = sm;
	address = new JoinAddress();

	routeTable = rt;
	leafSet = ls;
    }

    /**
     * Get address.
     *
     * @return gets the address.
     */

    public Address getAddress() { return address; }
    
    /**
     * Receives a message from the outside world.
     *
     * @param msg the message that was received.
     */
    
    public void receiveMessage(Message msg) {
	if (msg instanceof JoinRequest) {
	    JoinRequest jr = (JoinRequest) msg;

	    NodeHandle nh = jr.getHandle();

	    nh = security.verifyNodeHandle(nh);
	    
	    if (nh.isAlive() == true)  // the handle is alive
		if (jr.accepted() == false) {   // this the terminal node on the request path
		    jr.acceptJoin(localHandle,leafSet);

		    nh.receiveMessage(jr);
		}
		
		else { // this is the node that initiated the join request in the first place
		    NodeHandle jh = jr.getJoinHandle();  // the node we joined to.
		    
		    jh = security.verifyNodeHandle(jh);

		    if (jh.isAlive() == true) {  // the join handle is alive
			routeTable.put(jh); // add the num. closest node to the routing table

			// update local RT, then broadcast rows to our peers
			broadcastRows(jr);  
	    			
			// now update the local leaf set
			//System.out.println("Join ls:" + jr.getLeafSet());
			BroadcastLeafSet bls = new BroadcastLeafSet(jh, jr.getLeafSet(), BroadcastLeafSet.JoinInitial);
			localHandle.receiveMessage(bls);
		    }
		}	    
	}
	else if (msg instanceof RouteMessage) { // a join request message at an intermediate node
	    RouteMessage rm = (RouteMessage) msg;

	    JoinRequest jr = (JoinRequest) rm.unwrap();

	    NodeId localId = localHandle.getNodeId();
	    NodeHandle jh = jr.getHandle();
	    NodeId nid = jh.getNodeId();

	    jh = security.verifyNodeHandle(jh);

	    if (jh.isAlive() == true) routeTable.put(jh);

	    int base = RoutingTable.idBaseBitLength;
		
	    int msdd = localId.indexOfMSDD(nid, base);
	    int last = jr.lastRow();
		
	    for (int i=last - 1; i>=msdd; i--) {
		//System.out.println(routeTable);
		//System.out.print(i + " ");

		RouteSet row[] = routeTable.getRow(i);

		jr.pushRow(row);
	    }

	    //System.out.println("done");

	    rm.routeMessage(localId);
	}
	else if (msg instanceof InitiateJoin) {  // request from the local node to join
	    InitiateJoin ij = (InitiateJoin) msg;
	    
	    NodeHandle nh = ij.getHandle();

	    nh = security.verifyNodeHandle(nh);

	    if (nh.isAlive() == true) {
		JoinRequest jr = new JoinRequest(localHandle);

		RouteMessage rm = new RouteMessage(localHandle.getNodeId(), jr, new PermissiveCredentials(), address);

		nh.receiveMessage(rm);
	    }
	}
    }
    
    /**
     * Broadcasts the route table rows.
     *
     * @param jr the join row.
     */

    public void broadcastRows(JoinRequest jr) {
	NodeId localId = localHandle.getNodeId();
	int n = jr.numRows();

	for (int i=jr.lastRow(); i<n; i++) {
	    RouteSet row[] = jr.getRow(i);
	    
	    if (row != null) {
		BroadcastRouteRow brr = new BroadcastRouteRow(localId, row);

		localHandle.receiveMessage(brr);
	    }
	}


	for (int i=0; i<n; i++) {
	    RouteSet row[] = routeTable.getRow(i);

	    for (int j=0; j<row.length; j++) {
		RouteSet rs = row[j];

		BroadcastRouteRow brr = new BroadcastRouteRow(localId, row);

		// broadcast to closest node only
		NodeHandle nh = rs.closestNode();
		if (nh != null) nh.receiveMessage(brr);

		/*
		int m = rs.size();
		for (int k=0; k<m; k++) {
		    NodeHandle nh = rs.get(k);
		    
		    nh.receiveMessage(brr);
		}
		*/
	    }
	}


    }
}
