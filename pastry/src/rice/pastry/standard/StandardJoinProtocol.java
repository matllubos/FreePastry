
package rice.pastry.standard;

import rice.environment.logging.Logger;
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

public class StandardJoinProtocol implements MessageReceiver {
	protected PastryNode localNode;
	protected NodeHandle localHandle;
	protected PastrySecurityManager security;
	protected RoutingTable routeTable;
	protected LeafSet leafSet;

	protected Address address;

	/**
	 * Constructor.
	 *
	 * @param lh the local node handle.
	 * @param sm the Pastry security manager.
	 */

	public StandardJoinProtocol(
		PastryNode ln,
		NodeHandle lh,
		PastrySecurityManager sm,
		RoutingTable rt,
		LeafSet ls) {
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

	public Address getAddress() {
		return address;
	}

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

	//		if (nh.isAlive() == true) // the handle is alive
				if (jr.accepted() == false) {
					// this is the terminal node on the request path
					//leafSet.put(nh);
          if (localNode.isReady()) {
  					jr.acceptJoin(localHandle, leafSet);
	  				nh.receiveMessage(jr);
          } else {
            localNode.getEnvironment().getLogManager().getLogger(StandardJoinProtocol.class, null).log(Logger.INFO,
                "NOTE: Dropping incoming JoinRequest " + jr + " because local node is not ready!");
          }
				} else { // this is the node that initiated the join request in the first place
					NodeHandle jh = jr.getJoinHandle(); // the node we joined to.

					jh = security.verifyNodeHandle(jh);

					if (jh.equals(localHandle) && !localNode.isReady()) {
            localNode.getEnvironment().getLogManager().getLogger(StandardJoinProtocol.class, null).log(Logger.WARNING,
							"NodeId collision, unable to join: " + localHandle + ":" + jh);
						//Thread.dumpStack();
					} else if (jh.isAlive() == true) { // the join handle is alive
						routeTable.put(jh);
						// add the num. closest node to the routing table

						// update local RT, then broadcast rows to our peers
						broadcastRows(jr);

						// now update the local leaf set
						BroadcastLeafSet bls =
							new BroadcastLeafSet(
								jh,
								jr.getLeafSet(),
								BroadcastLeafSet.JoinInitial);
						localHandle.receiveMessage(bls);
            
            // we have now successfully joined the ring, set the local node ready
						setReady();
					}
				}
		} else if (msg instanceof RouteMessage) {
			// a join request message at an intermediate node
			RouteMessage rm = (RouteMessage) msg;

			JoinRequest jr = (JoinRequest) rm.unwrap();

			NodeId localId = localHandle.getNodeId();
			NodeHandle jh = jr.getHandle();
			NodeId nid = jh.getNodeId();

			jh = security.verifyNodeHandle(jh);

      int base = localNode.getRoutingTable().baseBitLength();

			int msdd = localId.indexOfMSDD(nid, base);
			int last = jr.lastRow();

			for (int i = last - 1; msdd > 0 && i >= msdd; i--) {
				RouteSet row[] = routeTable.getRow(i);

				jr.pushRow(row);
			}

			rm.routeMessage(localHandle);
		} else if (msg instanceof InitiateJoin) { // request from the local node to join
			InitiateJoin ij = (InitiateJoin) msg;

			NodeHandle nh = ij.getHandle();

			nh = security.verifyNodeHandle(nh);

			if (nh.isAlive() == true) {
				JoinRequest jr = new JoinRequest(localHandle, localNode.getRoutingTable().baseBitLength());

				RouteMessage rm =
					new RouteMessage(
						localHandle.getNodeId(),
						jr,
						new PermissiveCredentials(),
						address);
				rm.getOptions().setRerouteIfSuspected(false);
				nh.bootstrap(rm);
			}
		}
	}
  
  protected void setReady() {
    localNode.setReady(); 
  }

	/**
	 * Broadcasts the route table rows.
	 *
	 * @param jr the join row.
	 */

	public void broadcastRows(JoinRequest jr) {
		//NodeId localId = localHandle.getNodeId();
		int n = jr.numRows();

		// send the rows to the RouteSetProtocol on the local node
		for (int i = jr.lastRow(); i < n; i++) {
			RouteSet row[] = jr.getRow(i);

			if (row != null) {
				BroadcastRouteRow brr = new BroadcastRouteRow(localHandle, row);

				localHandle.receiveMessage(brr);
			}
		}

		// now broadcast the rows to our peers in each row

		for (int i = jr.lastRow(); i < n; i++) {
			RouteSet row[] = jr.getRow(i);

			BroadcastRouteRow brr = new BroadcastRouteRow(localHandle, row);

			for (int j = 0; j < row.length; j++) {
				RouteSet rs = row[j];
				if (rs == null)
					continue;

				// send to closest nodes only

				NodeHandle nh = rs.closestNode();
				if (nh != null)
					nh = security.verifyNodeHandle(nh);
				if (nh != null)
					nh.receiveMessage(brr);

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
