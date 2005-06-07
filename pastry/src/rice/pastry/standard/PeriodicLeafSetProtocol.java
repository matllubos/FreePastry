
package rice.pastry.standard;

import java.util.Hashtable;

import rice.environment.logging.Logger;
import rice.pastry.NodeHandle;
import rice.pastry.NodeSet;
import rice.pastry.PastryNode;
import rice.pastry.leafset.BroadcastLeafSet;
import rice.pastry.leafset.InitiateLeafSetMaintenance;
import rice.pastry.leafset.LeafSet;
import rice.pastry.leafset.LeafSetProtocolAddress;
import rice.pastry.leafset.RequestLeafSet;
import rice.pastry.messaging.Address;
import rice.pastry.messaging.Message;
import rice.pastry.messaging.MessageReceiver;
import rice.pastry.routing.RoutingTable;
import rice.pastry.security.PastrySecurityManager;

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

	private Address address;

  /**
   * NodeHandle -> Long
   * remembers the TIME when we received a BLS from that NodeHandle
   */
  protected Hashtable lastTimeReceivedBLS;
  
  /**
   * Related to rapidly determining direct neighbor liveness.
   */
  public static final int PING_NEIGHBOR_PERIOD = 20*1000;
  public static final int CHECK_LIVENESS_PERIOD = PING_NEIGHBOR_PERIOD+(10*1000);//30*1000;
  
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
    this.lastTimeReceivedBLS = new Hashtable();

    // Removed after meeting on 5/5/2005  Don't know if this is always the appropriate policy.
    //leafSet.addObserver(this);
		address = new LeafSetProtocolAddress();
    localNode.scheduleMsgAtFixedRate(new InitiatePingNeighbor(),
        PING_NEIGHBOR_PERIOD, PING_NEIGHBOR_PERIOD);
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
      
      lastTimeReceivedBLS.put(bls.from(),new Long(localNode.getEnvironment().getTimeSource().currentTimeMillis()));

      // if we have now successfully joined the ring, set the local node ready
			if (bls.type() == BroadcastLeafSet.JoinInitial) {
        // merge the received leaf set into our own
        leafSet.merge(bls.leafSet(), bls.from(), routeTable, security, false, null);

//				localNode.setReady();
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
        NodeHandle handle = set.get(localNode.getEnvironment().getRandomSource().nextInt(set.size() - 1) + 1);
        handle.receiveMessage(new RequestLeafSet(localHandle));
        handle.receiveMessage(new BroadcastLeafSet(localHandle, leafSet, BroadcastLeafSet.Update));
        
        NodeHandle check = set.get(localNode.getEnvironment().getRandomSource().nextInt(set.size() - 1) + 1);
        check.checkLiveness();
      }
    } else if (msg instanceof InitiatePingNeighbor) {
      // IPN every 20 seconds
      NodeHandle left = leafSet.get(-1);
      NodeHandle right = leafSet.get(1);
      
      // send BLS to left neighbor
      if (left != null) {
        left.receiveMessage(new BroadcastLeafSet(localHandle, leafSet, BroadcastLeafSet.Update));         
        left.receiveMessage(new RequestLeafSet(localHandle));         
      }
      // see if received BLS within past 30 seconds from right neighbor
      if (right != null) {
        Long time = (Long)lastTimeReceivedBLS.get(right);
        if (time == null || 
            (time.longValue() < (localNode.getEnvironment().getTimeSource().currentTimeMillis()-CHECK_LIVENESS_PERIOD))) {
          // else checkLiveness() on right neighbor
          localNode.getEnvironment().getLogManager().getLogger(PeriodicLeafSetProtocol.class, null).log(Logger.FINE,
              "PeriodicLeafSetProtocol: Checking liveness on right neighbor:"+right);
          right.checkLiveness();
        }
      }
      if (left != null) {
        Long time = (Long)lastTimeReceivedBLS.get(left);
        if (time == null || 
            (time.longValue() < (localNode.getEnvironment().getTimeSource().currentTimeMillis()-CHECK_LIVENESS_PERIOD))) {
          // else checkLiveness() on left neighbor
          localNode.getEnvironment().getLogManager().getLogger(PeriodicLeafSetProtocol.class, null).log(Logger.FINE,
              "PeriodicLeafSetProtocol: Checking liveness on left neighbor:"+left);
          left.checkLiveness();
        }
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

  /**
   * Used to kill self if leafset shrunk by too much.
   * NOTE: PLSP is not registered as an observer.
   * 
   */
//  public void update(Observable arg0, Object arg1) {
//    NodeSetUpdate nsu = (NodeSetUpdate)arg1;
//    if (!nsu.wasAdded()) {
//      if (localNode.isReady() && !leafSet.isComplete() && leafSet.size() < (leafSet.maxSize()/2)) {
//        // kill self
//        System.outt.println("PeriodicLeafSetProtocol: "+localNode.getEnvironment().getTimeSource().currentTimeMillis()+" Killing self due to leafset collapse. "+leafSet);
//        localNode.resign();
//      }
//    }
//  }
}
