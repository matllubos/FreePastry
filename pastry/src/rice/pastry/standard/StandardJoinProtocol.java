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
import rice.pastry.leafset.*;
import rice.pastry.routing.*;
import rice.pastry.security.*;
import rice.pastry.join.*;

import java.util.*;

/**
 * An implementation of a simple join protocol.
 *
 * @version $Id$
 *
 * @author Peter Druschel
 * @author Andrew Ladd
 * @author Rongmei Zhang
 * @author Y. Charlie Hu
 */

public class StandardJoinProtocol implements MessageReceiver 
{
    private PastryNode localNode;
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

    public StandardJoinProtocol(PastryNode ln, NodeHandle lh, PastrySecurityManager sm, 
				RoutingTable rt, LeafSet ls) {
	localNode = ln;
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
		    //leafSet.put(nh);
		    jr.acceptJoin(localHandle,leafSet);
		    nh.receiveMessage(jr);
		}
		
		else { // this is the node that initiated the join request in the first place
		    NodeHandle jh = jr.getJoinHandle();  // the node we joined to.
		    
		    jh = security.verifyNodeHandle(jh);

		    if (jh.getNodeId().equals(localHandle.getNodeId())) {
			System.out.println("NodeId collision, join failed!");
		    }
		    else if (jh.isAlive() == true) { // the join handle is alive
			routeTable.put(jh); // add the num. closest node to the routing table

			// update local RT, then broadcast rows to our peers
			broadcastRows(jr);  
	    			
			// now update the local leaf set
			//System.out.println("Join ls:" + jr.getLeafSet());
			BroadcastLeafSet bls = new BroadcastLeafSet(jh, jr.getLeafSet(), BroadcastLeafSet.JoinInitial);
			localHandle.receiveMessage(bls);
			
			// we have now successfully joined the ring, set the local node ready
			localNode.setReady();
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

	    int base = RoutingTable.baseBitLength();
		
	    int msdd = localId.indexOfMSDD(nid, base);
	    int last = jr.lastRow();

	    //System.out.println("join from " + nid + " at " + localId + " msdd=" + msdd + " last=" + last);
			       
	    for (int i=last - 1; msdd>0 && i>=msdd; i--) {
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

	// send the rows to the RouteSetProtocol on the local node
	for (int i=jr.lastRow(); i<n; i++) {
	    RouteSet row[] = jr.getRow(i);
	    
	    if (row != null) {
		BroadcastRouteRow brr = new BroadcastRouteRow(localId, row);

		localHandle.receiveMessage(brr);
	    }
	}

	// now broadcast the rows to our peers in each row
	//for (int i=0; i<n; i++) {
	//    RouteSet row[] = routeTable.getRow(i);

	for (int i=jr.lastRow(); i<n; i++) {
	    RouteSet row[] = jr.getRow(i);

	    int myCol = localHandle.getNodeId().getDigit(i,rice.pastry.routing.RoutingTable.baseBitLength());
	    NodeHandle nhMyCol = row[myCol].closestNode();
	    row[myCol].put(localHandle);

	    BroadcastRouteRow brr = new BroadcastRouteRow(localId, row);

	    for (int j=0; j<row.length; j++) {
		RouteSet rs = row[j];

		// broadcast to closest node only
		
		NodeHandle nh;
		if (j != myCol)
		    nh = rs.closestNode();
		else
		    nh = nhMyCol;
		if (nh != null) nh = security.verifyNodeHandle(nh);
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
