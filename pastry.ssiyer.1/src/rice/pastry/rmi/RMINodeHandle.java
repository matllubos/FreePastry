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
    private boolean alive;

    // handle with care: localHandle is *not* serialized.
    private NodeHandle localHandle;
    /**
     * setting the local handle. Called with an RMINodeHandle by
     * RMIPastrySecurityManager after the rest of NodeHandle has serialized
     * and travelled across. Or at initialization, in two places: PastryTest
     * calls it with the PastryNode, and NodeFactory calls it with itself.
     *
     * @param lnh the local node handle (for bouncing messages back to self).
     */
    public void setLocalHandle(NodeHandle lnh) { localHandle = lnh; }


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
	System.out.println("[rmi] creating RMI handle for node: " + nid);
	remoteNode = rn;
	remotenid = nid;
	alive = true;
	localHandle = null;
    }

    /**
     * Alternate constructor with localNodeHandle.
     *
     * @param rn pastry node for whom we're constructing a handle.
     * @param nid its node id.
     * @param lnh the local node handle (for bouncing messages back to self).
     */
    public RMINodeHandle(RMIPastryNode rn, NodeId nid, NodeHandle lnh) {
	System.out.println("[rmi] creating RMI handle for node: " + nid);
	remoteNode = rn;
	remotenid = nid;
	alive = true;
	localHandle = lnh;
    }

    public RMIPastryNode getRemote() { return remoteNode; }
    public NodeId getNodeId() { return remotenid; }
    public boolean isAlive() { return alive; }

    /**
     * make this nodehandle alive again, if it seemed down due to a routing
     * anomaly or something.
     *
     * @param rn a remote node for this nodeId known to be alive
     * @param nid its nodeid (procured by remote call from nodehandlepool)
     */
    public void makeAlive(RMIPastryNode rn) {
	if (alive == false) {
	    // xxx time threshold and expire (return) if greater
	    // else nodeId clashes will occur eventually
	}
	remoteNode = rn;
	alive = true;
    }

    public int proximity() { return 1; /* xxx */ }

    public void receiveMessage(Message msg) {
	// sanity check:
	if (localHandle == null) {
	    System.out.println("warning: localHandle is null");
	}

	if (alive == false) {
	    System.out.println("warning: trying to speak to dead node: " + msg);
	}

	try {
	    System.out.println("[rmi] sent message: " + msg);
	    remoteNode.receiveMessage(msg);
	    System.out.println("[rmi] message sent successfully");
	} catch (RemoteException e) { // failed; mark it dead
	    System.out.println("[rmi] message failed; remote node declared dead: "
			       + remotenid);
	    System.out.println(e.toString());

	    alive = false;

	    // bounce back to local dispatcher
	    System.out.println("[rmi] bouncing message back to self at " + localHandle);
	    if (msg instanceof RouteMessage) {
		RouteMessage rmsg = (RouteMessage) msg;
		rmsg.setNextHop(null);
		localHandle.receiveMessage(rmsg);
	    } else {
		localHandle.receiveMessage(msg);
	    }
	}
    }


    private void readObject(ObjectInputStream in)
        throws IOException, ClassNotFoundException
    {
        remoteNode = (RMIPastryNode) in.readObject();
		// xxx The above must be passed by reference. Is it?
        remotenid = (NodeId) in.readObject();
        alive = in.readBoolean();
	localHandle = null; // will be filled in by RMIPastrySecurityManager
    }

    private void writeObject(ObjectOutputStream out)
        throws IOException, ClassNotFoundException
    {
        out.writeObject(remoteNode);
        out.writeObject(remotenid);
        out.writeBoolean(alive);
    }
}
