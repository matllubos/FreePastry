package rice.pastry.rmi;

import rice.pastry.*;

import java.util.*;
import java.rmi.RemoteException;

/**
 * Maintains a pool of RMINodeHandles and implements the coalescing logic.
 *
 * @author Sitaram Iyer
 */

class RMINodeHandlePool
{
    private HashMap handles; // pool of RMINodeHandles

    /**
     * Constructor
     */
    public RMINodeHandlePool()
    {
	handles = new HashMap();
    }

    /**
     * Adds a RMINodeHandle to the pool if another with the same NodeId
     * isn't found.
     *
     * @param nh the node handle to coalesce into pool.
     * @return either nh, or a handle from the pool with same NodeId.
     */
    public RMINodeHandle coalesce(RMINodeHandle nh)
    {
	NodeId nid = nh.getNodeId();
	RMINodeHandle ph = (RMINodeHandle) handles.get(nid);
	if (ph == null) {
	    handles.put(nid, nh);
	    nh.setIsInPool(true);
	    //System.out.println("[rmi] ADDING " + nh + " with id " + nid + " to pool");
	    return nh;
	} else {
	    //System.out.println("[rmi] " + ph + " found, so NOT ADDING " + nh + " with id " + nid + " to pool");
	    if (ph != nh)
		nh.setIsInPool(false);
	}
	return ph;
    }

    /**
     * If given NodeId has a NodeHandle in the pool, then the latter is
     * marked alive.
     *
     * @param nid the node nid of the handle to lookup and mark alive.
     */
    public void activate(NodeId nid)
    {
	RMINodeHandle ph = (RMINodeHandle) handles.get(nid);
	if (ph != null) {
	    ph.markAlive();
	} else {
	    // do nothing; ignore senders who we don't know anything about
	}
    }
}
