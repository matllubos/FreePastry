
package rice.pastry.standard;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.leafset.*;
import rice.pastry.routing.*;

import java.util.*;

/**
 * An implementation of a periodic-style leafset protocol
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class PeriodicLeafSetProtocol implements MessageReceiver {

	protected NodeHandle localHandle;
	protected PastryNode localNode;
	protected PastrySecurityManager security;
	protected LeafSet leafSet;
	protected RoutingTable routeTable;
  protected Random random;

	private Address address;

  /**
   * Builds a periodic leafset protocol
   *
   */
	public PeriodicLeafSetProtocol(PastryNode ln, NodeHandle local, PastrySecurityManager sm, LeafSet ls, RoutingTable rt) {
		this.localNode = ln;
		this.localHandle = local;
		this.security = sm;
		this.leafSet = ls;
		this.routeTable = rt;
    this.random = new Random();

		address = new LeafSetProtocolAddress();
	}

	/**
	 * Gets the address.
	 *
	 * @return the address.
	 */
	public Address getAddress() {
		return address;
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

      // if we have now successfully joined the ring, set the local node ready
			if (bls.type() == BroadcastLeafSet.JoinInitial) {
        // merge the received leaf set into our own
        leafSet.merge(bls.leafSet(), bls.from(), routeTable, security, false, null);

				localNode.setReady();
				broadcastAll();
      } else {
        // first check for missing entries in their leafset 
        NodeSet set = leafSet.neighborSet(Integer.MAX_VALUE);

        // if we find any missing entries, check their liveness
        for (int i=0; i<set.size(); i++) 
          if (bls.leafSet().test(set.get(i)))
            set.get(i).checkLiveness();
        
        // now check for assumed-dead entries in our leafset 
        set = bls.leafSet().neighborSet(Integer.MAX_VALUE);
        
        // if we find any missing entries, check their liveness
        for (int i=0; i<set.size(); i++) 
          if (! set.get(i).isAlive())
            set.get(i).checkLiveness();

        // merge the received leaf set into our own
        leafSet.merge(bls.leafSet(), bls.from(), routeTable, security, false, null);
      }
		} else if (msg instanceof RequestLeafSet) {
			// request for leaf set from a remote node
			RequestLeafSet rls = (RequestLeafSet) msg;

			rls.returnHandle().receiveMessage(new BroadcastLeafSet(localHandle, leafSet, BroadcastLeafSet.Update));
		} else if (msg instanceof InitiateLeafSetMaintenance) {
			// perform leafset maintenance
      NodeSet set = leafSet.neighborSet(Integer.MAX_VALUE);
      
      if (set.size() > 1) {
        NodeHandle handle = set.get(random.nextInt(set.size() - 1) + 1);
        handle.receiveMessage(new RequestLeafSet(localHandle));
        handle.receiveMessage(new BroadcastLeafSet(localHandle, leafSet, BroadcastLeafSet.Update));
        
        NodeHandle check = set.get(random.nextInt(set.size() - 1) + 1);
        check.checkLiveness();
      }
		} 
	}

	/**
	 * Broadcast the leaf set to all members of the local leaf set.
	 *
	 * @param type the type of broadcast message used
	 */
	protected void broadcastAll() {
		BroadcastLeafSet bls = new BroadcastLeafSet(localHandle, leafSet, BroadcastLeafSet.JoinAdvertise);
    NodeSet set = leafSet.neighborSet(Integer.MAX_VALUE);

    for (int i=1; i<set.size(); i++) 
			set.get(i).receiveMessage(bls);
	}
}
