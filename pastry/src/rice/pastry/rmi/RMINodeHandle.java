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
import rice.pastry.routing.*;
import rice.pastry.messaging.*;

import java.io.*;
import java.rmi.RemoteException;

/**
 * A locally stored node handle that points to a remote RMIRemoteNodeI.
 *
 * @version $Id$
 *
 * @author Sitaram Iyer
 */

public class RMINodeHandle implements NodeHandle, Serializable
{
    private RMIRemoteNodeI remoteNode;
    private NodeId remotenid;

    /**
     * Cached liveness bit, updated by any message, including ping.
     */
    private transient boolean alive;

    /**
     * Cached proximity metric, updated by ping().
     */
    private transient int distance;

    /**
     * this is a sanity check thing: messages should never be sent to
     * unverified node handles, so this handle should be in the Pool.
     */
    private transient boolean isInPool;

    private transient PastryNode localnode;
    private transient boolean isLocal;

    /**
     * Constructor.
     *
     * rn could be the local node, in which case this elegantly folds in the
     * terrible ProxyNodeHandle stuff (since the RMI node acts as a proxy).
     *
     * @param rn pastry node for whom we're constructing a handle.
     * @param nid its node id.
     */
     
    public RMINodeHandle(RMIRemoteNodeI rn, NodeId nid) {
	if (Log.ifp(5)) System.out.println("creating RMI handle for node: " + nid);
	init(rn, nid);
    }

    /**
     * Alternate constructor with local Pastry node.
     *
     * @param rn pastry node for whom we're constructing a handle.
     * @param nid its node id.
     * @param pn local Pastry node.
     */
    public RMINodeHandle(RMIRemoteNodeI rn, NodeId nid, PastryNode pn) {
	if (Log.ifp(5)) System.out.println("creating RMI handle for node: " + nid + ", local = " + pn);
	init(rn, nid);
	setLocalNode(pn);
    }

    private void init(RMIRemoteNodeI rn, NodeId nid) {
	remoteNode = rn;
	remotenid = nid;
	alive = true;
	distance = Integer.MAX_VALUE;
	isInPool = false;
	localnode = null;
	isLocal = false;
    }

    public NodeId getNodeId() { return remotenid; }

    /**
     * The two remotenode accessor methods.
     */
    public RMIRemoteNodeI getRemote() { return remoteNode; }
    public void setRemoteNode(RMIRemoteNodeI rn) {
	if (remoteNode != null) System.out.println("panic");
	remoteNode = rn;
    }

    /**
     * The two localnode accessor methods.
     */
    public PastryNode getLocalNode() { return localnode; }
    public void setLocalNode(PastryNode ln) {
	//System.out.println("setlocalnode " + this + "(" + remotenid + ") " + lh);
	localnode = ln;
	if (localnode.getNodeId().equals(remotenid))
	    isLocal = true;
    }

    /**
     * The three liveness functions.
     * @return a cached boolean value.
     */
    public boolean isAlive() {
	if (isLocal && !alive) System.out.println("panic; local node dead");
	return alive;
    }

    public void markAlive() {
	if (alive == false) {
	    if (Log.ifp(5)) System.out.println("remote node became alive: " + remotenid);
	    alive = true;
	    distance = Integer.MAX_VALUE; // reset to infinity. alternatively, recompute.
	}
    }

    public void markDead() {
	if (alive == true) {
	    if (Log.ifp(5)) System.out.println("remote node declared dead: " + remotenid);
	    alive = false;
	    distance = Integer.MAX_VALUE;
	}
    }

    /**
     * @return the cached proximity value, Integer.MAX_VALUE initially, 0 if its local.
     */
    public int proximity() {
	if (isLocal) return 0;
	// for (int i = 0; i < 10; i++) if (!ping()) break;
	return distance;
    }

    public boolean getIsInPool() { return isInPool; }
    public void setIsInPool(boolean iip) { isInPool = iip; }

    public void receiveMessage(Message msg) {

	if (isLocal) {
	    localnode.receiveMessage(msg);
	    return;
	}

	if (alive == false)
	    if (Log.ifp(5))
		System.out.println("warning: trying to send msg to dead node "
				   + remotenid + ": " + msg);

	if (isInPool == false)
	    System.out.println("panic: sending message to unverified handle "
			       + this + " for " + remotenid + ": " + msg);

	msg.setSenderId(localnode.getNodeId());

	if (Log.ifp(5))
	    System.out.println("sending " +
			       (msg instanceof RouteMessage ? "route" : "direct")
			       + " msg to " + remotenid + ": " + msg);

	try {

	    remoteNode.remoteReceiveMessage(msg);
	    //System.out.println("message sent successfully");

	    markAlive();
	} catch (RemoteException e) { // failed; mark it dead
	    if (Log.ifp(5)) System.out.println("message failed: " + msg + e);
	    if (isLocal) System.out.println("panic; local message failed: " + msg);

	    markDead();

	    // bounce back to local dispatcher
	    if (Log.ifp(5)) System.out.println("bouncing message back to self at " + localnode);
	    if (msg instanceof RouteMessage) {
		RouteMessage rmsg = (RouteMessage) msg;
		rmsg.nextHop = null;
		if (Log.ifp(5)) System.out.println("this msg bounced is " + rmsg);
		localnode.receiveMessage(rmsg);
	    } else {
		localnode.receiveMessage(msg);
	    }
	}
    }

    /**
     * Ping the remote node.
     *
     * @return liveness of remote node.
     */
    public boolean ping() {
	NodeId tryid;

	/*
	 * Note subtle point: When ping is called from RouteSet.readObject,
	 * the RMI security manager has not yet had a chance to call the
	 * above setLocalNode. So isLocal may be false even for local node.
	 *
	 * This is not disastrous; at worst, we'll ping the local node once.
	 */
	if (isLocal) return alive;

	if (Log.ifp(7)) System.out.println("pinging " + remotenid);
	try {

	    long starttime = System.currentTimeMillis();

	    tryid = remoteNode.getNodeId();

	    long stoptime = System.currentTimeMillis();
	    if (distance > (int)(stoptime - starttime))
		distance = (int)(stoptime - starttime);

	    if (tryid.equals(remotenid) == false)
		System.out.println("PANIC: remote node has changed its ID from "
				   + remotenid + " to " + tryid);
	    markAlive();
	} catch (RemoteException e) {
	    if (alive) if (Log.ifp(5)) System.out.println("ping failed on live node: " + e);
	    markDead();
	}
	return alive;
    }

    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException 
    {
	RMIRemoteNodeI rn = (RMIRemoteNodeI) in.readObject();
	NodeId rnid = (NodeId) in.readObject();
	init(rn, rnid); // initialize all the other elements
    }

    private void writeObject(ObjectOutputStream out)
	throws IOException, ClassNotFoundException 
    {
	if (isLocal) if (Log.ifp(6)) System.out.println("writeObject from " + localnode.getNodeId() + " to local node " + remotenid);
	out.writeObject(remoteNode);
	out.writeObject(remotenid);
    } 
}
