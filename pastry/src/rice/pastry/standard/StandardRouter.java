/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate 

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
import rice.pastry.routing.*;
import rice.pastry.leafset.*;
import rice.pastry.security.*;

/**
 * An implementation of the standard Pastry routing algorithm.
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 * @author Rongmei Zhang/Y.Charlie Hu
 */

public class StandardRouter implements MessageReceiver {
    private NodeId localId;
    private NodeHandle localHandle;

    private RoutingTable routeTable;
    private LeafSet leafSet;
    private PastrySecurityManager security;       
    private Address routeAddress;

    /**
     * Constructor.
     *
     * @param rt the routing table.
     * @param ls the leaf set.
     */

    public StandardRouter(NodeHandle handle, RoutingTable rt, LeafSet ls, PastrySecurityManager sm) {
	localHandle = handle;
	localId = handle.getNodeId();

	routeTable = rt;
	leafSet = ls;
	security = sm;

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

  /**
   * Receive and process a route message.
   *
   * @param msg the message.
   */

	private void receiveRouteMessage(RouteMessage msg) {
		Id target = msg.getTarget();

		if (target == null)
			target = localId;

		int cwSize = leafSet.cwSize();
		int ccwSize = leafSet.ccwSize();

		int lsPos = leafSet.mostSimilar(target);

		if (lsPos == 0) // message is for the local node so deliver it
			msg.nextHop = localHandle;

		else if (
			(lsPos > 0
				&& (lsPos < cwSize || !leafSet.get(lsPos).getNodeId().clockwise(target)))
				|| (lsPos < 0
					&& (-lsPos < ccwSize
						|| leafSet.get(lsPos).getNodeId().clockwise(target))))
			// the target is within range of the leafset, deliver it directly 
			{
			NodeHandle handle = leafSet.get(lsPos);

			if (handle.isAlive() == false) {
				// node is dead - get rid of it and try again
				leafSet.remove(handle.getNodeId());
				receiveRouteMessage(msg);
				return;
			} else {
				msg.nextHop = handle;
				msg.getOptions().setRerouteIfSuspected(false);
			}
		} else {
			// use the routing table
			RouteSet rs = routeTable.getBestEntry(target);
			NodeHandle handle = null;

			if (rs == null
				|| // get the closest alive node
			 (handle = rs.closestNode(NodeHandle.LIVENESS_ALIVE))
					== null) {

				// no live routing table entry matching the next digit
				// get best alternate RT entry

				handle = routeTable.bestAlternateRoute(NodeHandle.LIVENESS_ALIVE, target);

				if (handle == null) {
					// no alternate in RT, take leaf set
					handle = leafSet.get(lsPos);

					if (handle.isAlive() == false) {
						leafSet.remove(handle.getNodeId());
						receiveRouteMessage(msg);
						return;
					} else {
						msg.getOptions().setRerouteIfSuspected(false);
					}
				} else {
					NodeId.Distance altDist = handle.getNodeId().distance(target);
					NodeId.Distance lsDist =
						leafSet.get(lsPos).getNodeId().distance(target);

					if (lsDist.compareTo(altDist) < 0) {
						// closest leaf set member is closer
						//System.out.println("forw to edge leaf set member, alt=" + handle.getNodeId() + 
						//" lsm=" + leafSet.get(lsPos).getNodeId());
						handle = leafSet.get(lsPos);

						if (handle.isAlive() == false) {
							leafSet.remove(handle.getNodeId());
							receiveRouteMessage(msg);
							return;
						} else {
							msg.getOptions().setRerouteIfSuspected(false);
						}
					}
				}
			} else {
				// we found an appropriate RT entry, check for RT holes at previous node
				checkForRouteTableHole(msg, handle);
			}

			msg.nextHop = handle;
		}

		msg.setPrevNode(localHandle);
		localHandle.receiveMessage(msg);
	}

    
    /**
     * checks to see if the previous node along the path was missing a RT entry
     * if so, we send the previous node the corresponding RT row to patch the hole
     *
     * @param msg the RouteMessage being routed
     * @param handle the next hop handle
     */

    private void checkForRouteTableHole(RouteMessage msg, NodeHandle handle) {

	if (msg.getPrevNode() == null) return;

	NodeId prevId = msg.getPrevNode().getNodeId();
	Id key = msg.getTarget();

	//System.out.println("checkForRouteTableHole, prevNode=" + prevId +
	//	   " localId=" + localId + " key=" + msg.getTarget() +
	//	   " nextHop=" + handle.getNodeId());

	int diffDigit;

	if ( (diffDigit = prevId.indexOfMSDD(key, RoutingTable.baseBitLength())) == 
	     localId.indexOfMSDD(key, RoutingTable.baseBitLength())) {
	    
	    // the previous node is missing a RT entry, send the row
	    // for now, we send the entire row for simplicity

	    //System.out.println("checkForRouteTableHole, sending row=" + diffDigit + " to=" + prevId);

	    RouteSet[] row = routeTable.getRow(diffDigit);
	    BroadcastRouteRow brr = new BroadcastRouteRow(localHandle, row);

	    NodeHandle prevNode = security.verifyNodeHandle(msg.getPrevNode());
	    if (prevNode.isAlive())
		prevNode.receiveMessage(brr);
	}
	    

    }
}


