package rice.pastry.rmi;

import rice.pastry.*;
import rice.pastry.routing.*;
import rice.pastry.messaging.*;

import java.io.*;
import java.rmi.RemoteException;

/**
 * A locally stored node handle that points to a remote RMIPastryNode.
 *
 * @author Sitaram Iyer
 */

public class RMINodeHandle implements NodeHandle, Serializable
{
    private RMIPastryNode remoteNode;
    private NodeId remotenid;

    private transient boolean alive;	// don't serialize
    private transient int distance;	// don't serialize

    // this is a sanity check thing: messages should never be sent to
    // unverified node handles, so this handle should be in the Pool.
    private transient boolean isInPool;	// don't serialize

    private transient NodeHandle localhandle;

    /**
     * Constructor.
     *
     * rn could be the local node, in which case this elegantly folds in the
     * terrible ProxyNodeHandle stuff (since the RMI node acts as a proxy).
     *
     * @param rn pastry node for whom we're constructing a handle.
     * @param nid its node id.
     */
    public RMINodeHandle(RMIPastryNode rn, NodeId nid) {
	init(rn, nid);
    }

    /**
     * Alternate constructor with local Pastry node.
     *
     * @param rn pastry node for whom we're constructing a handle.
     * @param nid its node id.
     * @param pn local Pastry node.
     */
    public RMINodeHandle(RMIPastryNode rn, NodeId nid, PastryNode pn) {
	init(rn, nid);
	setLocalHandle(pn.getLocalHandle());
    }

    private void init(RMIPastryNode rn, NodeId nid) {
	System.out.println("[rmi] creating RMI handle for node: " + nid);
	remoteNode = rn;
	remotenid = nid;
	alive = true;
	distance = 42;
	isInPool = false;
    }

    public RMIPastryNode getRemote() { return remoteNode; }

    public NodeId getNodeId() { return remotenid; }

    /**
     * The two localhandle accessor methods.
     */
    public NodeHandle getLocalHandle() { return localhandle; }

    public void setLocalHandle(NodeHandle lh) {
	localhandle = lh;
	if (localhandle.getNodeId().equals(remotenid)) {
	    distance = 0;
	}
    }

    /**
     * The three liveness functions.
     * @return a cached boolean value.
     */
    public boolean isAlive() {
	return alive;
    }

    public void markAlive() {
	if (alive == false) {
	    System.out.println("[rmi] remote node became alive: " + remotenid);
	    alive = true;
	    // xxx reset distance to zero, or infinity, or recompute it now
	}
    }

    public void markDead() {
	if (alive == true) {
	    System.out.println("[rmi] remote node declared dead: " + remotenid);
	}
	alive = false;
    }

    public int proximity() {
	/* does a few pings */ return distance;
    }

    public boolean getIsInPool() { return isInPool; }
    public void setIsInPool(boolean iip) { isInPool = iip; }

    public void receiveMessage(Message msg) {
	try {

	    if (isInPool == false) {
		System.out.println("panic: sending message to unverified handle "
				   + this + " for " + remotenid + ": " + msg);
	    }

	    if (msg instanceof RouteMessage) {
		RouteMessage rmsg = (RouteMessage) msg;
		rmsg.setSenderId(localhandle.getNodeId());
		System.out.println("[rmi] sending route msg to " +
				   remotenid + ": " + msg);
	    } else {
		System.out.println("[rmi] sending direct msg: " + msg);
	    }

	    remoteNode.receiveMessage(msg);
	    //System.out.println("[rmi] message sent successfully");

	    markAlive();
	} catch (RemoteException e) { // failed; mark it dead
	    if ((msg instanceof RouteMessage) == false) {
		System.out.println("[rmi] panic: local message failed: " + msg);
	    }

	    System.out.println("[rmi] message failed: " + e);
	    markDead();

	    // bounce back to local dispatcher

	    System.out.println("[rmi] bouncing message back to self at " + localhandle);
	    RouteMessage rmsg = (RouteMessage) msg;
	    rmsg.setNextHop(null);
	    localhandle.receiveMessage(rmsg);
	}
    }

    public boolean ping() {
	NodeId tryid;
	try {
	    tryid = remoteNode.getNodeId();
	    if (tryid.equals(remotenid) == false) {
		System.out.println("[rmi] PANIC: remote node has changed its ID from "
				   + remotenid + " to " + tryid);
	    }
	    markAlive();
	} catch (RemoteException e) {
	    if (alive) System.out.println("[rmi] ping failed on live node: " + e);
	    markDead();
	}
	return alive;
    }

    /*
     * XXX remove these two methods, and things should still work
     */
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException 
    {
	remoteNode = (RMIPastryNode) in.readObject();
	remotenid = (NodeId) in.readObject();

	alive = true;
	isInPool = false;
	distance = 42;
	localhandle = null;
    }

    private void writeObject(ObjectOutputStream out)
	throws IOException, ClassNotFoundException 
    {
	out.writeObject(remoteNode);
	out.writeObject(remotenid);
    } 
}
