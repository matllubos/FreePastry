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
import rice.pastry.dist.*;
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

public class RMINodeHandle extends DistNodeHandle
{
    private RMIRemoteNodeI remoteNode;

    public transient static int index=0;
    public transient int id;

    private transient long lastpingtime;
    private static final long pingthrottle = 14 /* seconds */;

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
	super(nid);

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
	super(nid);

	if (Log.ifp(6)) System.out.println("creating RMI handle for node: " + nid + ", local = " + pn);
	init(rn, nid);
	//System.out.println("setLocalNode " + this + ":" + getNodeId() + " to " + pn + ":" + pn.getNodeId());
	setLocalNode(pn);
    }

    private void init(RMIRemoteNodeI rn, NodeId remoteNodeId) {
	remoteNode = rn;
	nodeId = remoteNodeId;

	lastpingtime = 0;
	id = index++;
    }

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
	if (remoteNode != null) System.out.println("panic");
	remoteNode = rn;
    }

    /**
    * Called to send a message to the node corresponding to this handle.
    *
    * @param msg Message to be delivered, may or may not be routeMessage.
     */
    public void receiveMessageImpl(Message msg) {
	assertLocalNode();

	if (isLocal) {
	    getLocalNode().receiveMessage(msg);
	    return;
	}

	if (alive == false)
	    if (Log.ifp(6))
		System.out.println("warning: trying to send msg to dead node "
				   + nodeId + ": " + msg);

	if (isInPool == false)
	    System.out.println("panic: sending message to unverified handle "
			       + this + " for " + nodeId + ": " + msg);

	msg.setSenderId(getLocalNode().getNodeId());

	if (Log.ifp(6))
	    System.out.println("sending " +
			       (msg instanceof RouteMessage ? "route" : "direct")
			       + " msg to " + nodeId + ": " + msg);

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
		if (((RouteMessage)msg).getOptions().multipleHopsAllowed() == true) {
		    RouteMessage rmsg = (RouteMessage) msg;
		    rmsg.nextHop = null;
		    if (Log.ifp(6)) System.out.println("this msg bounced is " + rmsg);
		    getLocalNode().receiveMessage(rmsg);
		}
		else {
		    // Drop the RouteMsgDirect message on the floor
		}
		
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
    public boolean pingImpl() {
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

	if (Log.ifp(7)) System.out.println(getLocalNode() + " pinging " + nodeId);
	try {

	    long starttime = System.currentTimeMillis();

	    tryid = remoteNode.getNodeId();

	    long stoptime = System.currentTimeMillis();
	    if (distance > (int)(stoptime - starttime))
		distance = (int)(stoptime - starttime);

	    if (Log.ifp(7)) System.out.println("proximity metric = " + distance);

	    if (tryid.equals(nodeId) == false)
		System.out.println("PANIC: remote node has changed its ID from "
				   + nodeId + " to " + tryid);
	    markAlive();
	} catch (RemoteException e) {
	    if (alive) if (Log.ifp(6)) System.out.println("ping failed on live node: " + e);
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
	    if (isLocal) if (Log.ifp(7)) {
		assertLocalNode();
		System.out.println("writeObject from " + getLocalNode().getNodeId() + " to local node " + nodeId);
	    }
	    out.writeObject(remoteNode);
	    out.writeObject(nodeId);
	}

    public String toStringImpl() {
	return (isLocal ? "(local " : "") + "handle " + nodeId
	    + (alive ? "" : ":dead")
	    + ", localnode = " + getLocalNode()
	    + ")";
    }
}
