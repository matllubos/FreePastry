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
 * Need localnode within handle for three reasons: to determine isLocal
 * (thus alive and distance = 0), to set senderId in messages (used for
 * coalescing on the other end), and to bounce messages back to self on
 * failure.
 *
 * @version $Id$
 *
 * @author Sitaram Iyer
 */

public class RMINodeHandle extends LocalNode implements NodeHandle, Serializable
{
    private RMIRemoteNodeI remoteNode;
    private NodeId remotenid;

    public transient static int index=0;
    public transient int id;

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

    private transient boolean isLocal;

    private transient long lastpingtime;
    private static final long pingthrottle = 14 /* seconds */;

    private transient RMINodeHandle redirect;
    private transient boolean verified;

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
	if (Log.ifp(6)) System.out.println("creating RMI handle for node: " + nid);
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
	if (Log.ifp(6)) System.out.println("creating RMI handle for node: " + nid + ", local = " + pn);
	init(rn, nid);
	//System.out.println("setLocalNode " + this + ":" + getNodeId() + " to " + pn + ":" + pn.getNodeId());
	setLocalNode(pn);
    }

    private void init(RMIRemoteNodeI rn, NodeId nid) {
	redirect = null;
	verified = false;
	remoteNode = rn;
	remotenid = nid;
	alive = true;
	distance = Integer.MAX_VALUE;
	isInPool = false;
	isLocal = false;
	lastpingtime = 0;
	id = index++;
    }

    /**
     * NodeId accessor method. Same as redirect.getNodeId().

     * @return NodeId of remote Pastry node.
     */
    public NodeId getNodeId() { return remotenid; }

    /**
     * Remotenode accessor method. Same as redirect.getRemote().

     * @return RMI remote reference to Pastry node.
     */
    public RMIRemoteNodeI getRemote() { return remoteNode; }


    /**
     * Remotenode accessor method.
     *
     * @param rn RMI remote reference to some Pastry node.
     */
    public void setRemoteNode(RMIRemoteNodeI rn) {
	if (verified == false) verify();

	if (remoteNode != null) System.out.println("panic");
	remoteNode = rn;

        if (redirect != null) {			// do nothing
	    /* assert(redirect.getRemote().nodeid == rn.nodeid); */
	}
    }

    /**
     * Method called from LocalNode after localnode is set to non-null.
     */
    public void afterSetLocalNode() {
	if (getLocalNode().getNodeId().equals(remotenid))
	    isLocal = true;
    }

    /**
     * The three liveness functions.
     *
     * @return a cached boolean value.
     */
    public boolean isAlive() {
	if (verified == false) verify();
        if (redirect != null) { return redirect.isAlive(); }

	if (isLocal && !alive) System.out.println("panic; local node dead");
	return alive;
    }

    /**
     * Mark this handle as alive (if dead earlier), and reset distance to
     * infinity.
     */
    public void markAlive() {
	if (verified == false) verify();
        if (redirect != null) { redirect.markAlive(); return; }

	if (alive == false) {
	    if (Log.ifp(5)) System.out.println(getLocalNode() + "found " + remotenid + " to be alive after all");
	    alive = true;
	    distance = Integer.MAX_VALUE; // reset to infinity. alternatively, recompute.
	}
    }

    /**
     * Mark this handle as dead (if alive earlier), and reset distance to
     * infinity.
     */
    public void markDead() {
	if (verified == false) verify();
        if (redirect != null) { redirect.markDead(); return; }

	if (alive == true) {
	    if (Log.ifp(5)) System.out.println(getLocalNode() + "found " + remotenid + " to be dead");
	    alive = false;
	    distance = Integer.MAX_VALUE;
	}
    }

    /**
     * Proximity metric.
     *
     * @return the cached proximity value (Integer.MAX_VALUE initially), or
     * 0 if node is local.
     */
    public int proximity() {
	if (verified == false) verify();
        if (redirect != null) { return redirect.proximity(); }

	if (isLocal) return 0;
	// for (int i = 0; i < 10; i++) if (!ping()) break;
	return distance;
    }

    public boolean getIsInPool() {
	if (verified == false) verify();
        if (redirect != null) { return redirect.getIsInPool(); }
	return isInPool;
    }

    public void setIsInPool(boolean iip) { isInPool = iip; }

    /**
     * Called to send a message to the node corresponding to this handle.
     *
     * @param msg Message to be delivered, may or may not be routeMessage.
     */
    public void receiveMessage(Message msg) {

	if (verified == false) verify();
        if (redirect != null) { redirect.receiveMessage(msg); return; }

	assertLocalNode();

	if (isLocal) {
	    getLocalNode().receiveMessage(msg);
	    return;
	}

	if (alive == false)
	    if (Log.ifp(6))
		System.out.println("warning: trying to send msg to dead node "
				   + remotenid + ": " + msg);

	if (isInPool == false)
	    System.out.println("panic: sending message to unverified handle "
			       + this + " for " + remotenid + ": " + msg);

	msg.setSenderId(getLocalNode().getNodeId());

	if (Log.ifp(6))
	    System.out.println("sending " +
			       (msg instanceof RouteMessage ? "route" : "direct")
			       + " msg to " + remotenid + ": " + msg);

	try {

	    remoteNode.remoteReceiveMessage(msg);
	    //System.out.println("message sent successfully");

	    markAlive();
	} catch (RemoteException e) { // failed; mark it dead
	    if (Log.ifp(6)) System.out.println("message failed: " + msg + e);
	    if (isLocal) System.out.println("panic; local message failed: " + msg);

	    markDead();

	    // bounce back to local dispatcher
	    if (Log.ifp(6)) System.out.println("bouncing message back to self at " + getLocalNode());
	    if (msg instanceof RouteMessage) {
		RouteMessage rmsg = (RouteMessage) msg;
		rmsg.nextHop = null;
		if (Log.ifp(6)) System.out.println("this msg bounced is " + rmsg);
		getLocalNode().receiveMessage(rmsg);
	    } else {
		getLocalNode().receiveMessage(msg);
	    }
	}
    }

    /**
     * Ping the remote node now, and update the proximity metric.
     *
     * @return liveness of remote node.
     */
    public boolean ping() {

	if (verified == false) verify();
        if (redirect != null) { return redirect.ping(); }

	NodeId tryid;

	/*
	 * Note subtle point: When ping is called from RouteSet.readObject,
	 * the RMI security manager has not yet had a chance to call the
	 * above setLocalNode. So isLocal may be false even for local node.
	 *
	 * This is not disastrous; at worst, we'll ping the local node once.
	 */

	if (isLocal) return alive;

	/*
	 * throttle super-rapid pings
	 */
	long now = System.currentTimeMillis();
	if (now - lastpingtime < pingthrottle*1000)
	    return alive;
	lastpingtime = now;

	if (Log.ifp(7)) System.out.println(getLocalNode() + " pinging " + remotenid);
	try {

	    long starttime = System.currentTimeMillis();

	    tryid = remoteNode.getNodeId();

	    long stoptime = System.currentTimeMillis();
	    if (distance > (int)(stoptime - starttime))
		distance = (int)(stoptime - starttime);

	    if (Log.ifp(7)) System.out.println("proximity metric = " + distance);

	    if (tryid.equals(remotenid) == false)
		System.out.println("PANIC: remote node has changed its ID from "
				   + remotenid + " to " + tryid);
	    markAlive();
	} catch (RemoteException e) {
	    if (alive) if (Log.ifp(6)) System.out.println("ping failed on live node: " + e);
	    markDead();
	}
	return alive;
    }

    /**
     * Someday verify will actually implement some policy. not today.
     */
    private void verify()
    {
	RMIPastryNode localnode = (RMIPastryNode) getLocalNode();
	if (localnode == null) {
	    //System.out.println("warning: localnode null in " + this + ":" + getNodeId() + ", can't verify");
	    return;
	}

	RMINodeHandle nh = localnode.getHandlePool().coalesce(this);
	if (nh != this)
	    redirect = nh;
	else
	    redirect = null;

	verified = true;
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
	if (isLocal) if (Log.ifp(7)) {
	    assertLocalNode();
	    System.out.println("writeObject from " + getLocalNode().getNodeId() + " to local node " + remotenid);
	}
	out.writeObject(remoteNode);
	out.writeObject(remotenid);
    } 

    public String toString() {
	if (verified == false) verify();
	if (redirect != null) { return redirect.toString(); }

	return (isLocal ? "(local " : "") + "handle " + remotenid
	    + (alive ? "" : ":dead")
	    + ", localnode = " + getLocalNode()
	    + ")";
    }
}
