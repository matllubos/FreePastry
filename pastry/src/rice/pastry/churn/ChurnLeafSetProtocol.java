/*
 * Created on May 18, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.pastry.churn;

import java.util.Collection;

import rice.pastry.NodeHandle;
import rice.pastry.PastryNode;
import rice.pastry.leafset.BroadcastLeafSet;
import rice.pastry.leafset.InitiateLeafSetMaintenance;
import rice.pastry.leafset.LeafSet;
import rice.pastry.leafset.RequestLeafSet;
import rice.pastry.messaging.Message;
import rice.pastry.routing.RoutingTable;
import rice.pastry.security.PastrySecurityManager;
import rice.pastry.socket.SocketNodeHandle;
import rice.pastry.standard.StandardLeafSetProtocol;

/**
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class ChurnLeafSetProtocol extends StandardLeafSetProtocol implements FailedSetManager {

	public ChurnLeafSetProtocol(
		PastryNode ln,
		NodeHandle local,
		PastrySecurityManager sm,
		LeafSet ls,
		RoutingTable rt) {
		super(ln, local, sm, ls, rt);
	}

	/**
	 * Receives messages.
	 *
	 * @param msg the message.
	 */
	public void receiveMessage(Message msg) {
		if (msg instanceof BroadcastLeafSet) {
			// receive a leafset from another node
			BroadcastLeafSet bls = (BroadcastLeafSet) msg;
			int type = bls.type();

			NodeHandle from = bls.from();
			LeafSet remotels = bls.leafSet();

			//System.out.println("received leafBC from " + from.getNodeId() + " at " + 
			//         localHandle.getNodeId() + "type=" + type + " :" + remotels);

			// first, merge the received leaf set into our own
			boolean changed = mergeLeafSet(remotels, from);
			//        if (changed)
			//          System.out.println("received leafBC from " + from.getNodeId() + " at " + 
			//                 localHandle.getNodeId() + "type=" + type + " :" + remotels);

			if (type == BroadcastLeafSet.JoinInitial) {
				// we have now successfully joined the ring, set the local node ready
				localNode.setReady();
			}

			if (!failstop) {
				// with arbitrary node failures, we need to broadcast whenever we learn something new
				if (changed)
					broadcast();

				// then, send ls to sending node if that node's ls is missing nodes
				checkLeafSet(remotels, from, false);
				return;
			}

			// if this node has just joined, notify the members of our new leaf set
			if (type == BroadcastLeafSet.JoinInitial)
				broadcast();

			// if we receive a correction to a leafset we have sent out, notify the members of our new leaf set
			if (type == BroadcastLeafSet.Correction && changed)
				broadcast();

			// Check if any of our local leaf set members are missing in the received leaf set
			// if so, we send our leafset to each missing entry and to the source of the leafset
			// this guarantees correctness in the event of concurrent node joins in the same leaf set
			checkLeafSet(remotels, from, true);
		} else if (
			msg instanceof RequestLeafSet) {
			// request for leaf set from a remote node
			RequestLeafSet rls = (RequestLeafSet) msg;

			NodeHandle returnHandle = rls.returnHandle();
			returnHandle = security.verifyNodeHandle(returnHandle);

			if (returnHandle.isAlive()) {
				BroadcastLeafSet bls =
					new BroadcastLeafSet(localHandle, leafSet, BroadcastLeafSet.Update);

				returnHandle.receiveMessage(bls);
			}
		} else if (
			msg instanceof InitiateLeafSetMaintenance) {
			// request for leafset maintenance

			// perform leafset maintenance
			maintainLeafSet();

		} else
			throw new Error("message received is of unknown type");

	}

	/**
	 * probe the entire leafset
	 */
	public void probeLeafSet() {
    if (true) return;
    for (int i=-leafSet.ccwSize(); i<=leafSet.cwSize(); i++) {
      SocketNodeHandle snh = (SocketNodeHandle)leafSet.get(i); 
      snh.probe();
    }    		
	}

	public Collection getFailedSet() {
		return null;
	}

}
