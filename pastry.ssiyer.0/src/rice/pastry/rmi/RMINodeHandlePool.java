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

package rice.pastry.rmi;

import rice.pastry.*;

import java.util.*;
import java.lang.ref.WeakReference;
import java.rmi.RemoteException;

/**
 * Maintains a pool of RMINodeHandles and implements the coalescing logic.
 *
 * @version $Id$
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
