package rice.pastry.rmi;

import rice.pastry.*;

import java.util.*;
import java.lang.ref.WeakReference;
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
     * @param handle the node handle to coalesce into pool.
     * @return either handle, or a handle from the pool with same NodeId.
     */
    public RMINodeHandle coalesce(RMINodeHandle handle)
    {
	NodeId nid = handle.getNodeId();
	WeakReference storedref = (WeakReference) handles.get(nid);
	RMINodeHandle storedhandle = null;

	if (storedref != null) {
	    storedhandle = (RMINodeHandle) storedref.get();
	    if (storedhandle == null)
		storedref.clear(); // xxx need to do?
	}

	if (storedhandle == null) {

	    WeakReference newref = new WeakReference(handle);
	    handles.put(nid, newref);
	    handle.setIsInPool(true);
	    //System.out.println("[rmi] ADDING " + handle + " with id " + nid + " to pool");
	    return handle;

	} else {
	    //System.out.println("[rmi] " + storedhandle + " found, so NOT ADDING " + handle + " with id " + nid + " to pool");
	    if (storedhandle != handle)
		handle.setIsInPool(false);
	}
	return storedhandle;
    }

    /**
     * If given NodeId has a NodeHandle in the pool, then the latter is
     * marked alive.
     *
     * @param nid the node nid of the handle to lookup and mark alive.
     */
    public void activate(NodeId nid)
    {
	WeakReference storedref = (WeakReference) handles.get(nid);
	RMINodeHandle storedhandle = null;
	if (storedref != null) {
	    storedhandle = (RMINodeHandle) storedref.get();
	    if (storedhandle == null)
		storedref.clear(); // xxx need to do?
	}

	if (storedhandle != null) {
	    storedhandle.markAlive();
	} else {
	    // do nothing; ignore senders who we don't know anything about
	}
    }
}
