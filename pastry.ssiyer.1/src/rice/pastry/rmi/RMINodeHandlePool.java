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
	    return nh;
	}

	// coalesce nh and ph

	if (ph.isAlive() == false && nh.isAlive() == true) {
	    try {
		RMIPastryNode rn = nh.getRemote();
		NodeId rnid = rn.getNodeId(); // bypass the nid cached in handle
		if (rnid.equals(nid))
		    ph.makeAlive(rn);
	    } catch (RemoteException e) {
		// ignore; red herring probably due to routing anomaly
	    }
	}

	return ph;
    }
}
