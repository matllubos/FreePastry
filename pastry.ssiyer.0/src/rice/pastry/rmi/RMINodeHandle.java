package rice.pastry.rmi;

import rice.pastry.*;
import rice.pastry.messaging.*;

import java.io.*;
import java.rmi.RemoteException;

/**
 * RMINodeHandle
 *
 * a locally stored node handle that points to a remote RMIPastryNode.
 *
 * @author Sitaram Iyer
 */

public class RMINodeHandle implements NodeHandle, Serializable
{
    private RMIPastryNode remoteNode;
    private NodeId cachedNodeId;
    private boolean alive;

    public RMINodeHandle(RMIPastryNode rn) {
	System.out.println("[rmi] creating RMI handle for remote node: " + rn);
	try {
	    System.out.println("[rmi] .. with id " + rn.getNodeId());
	} catch (Exception e) {
	    System.out.println("[rmi] .. getNodeId FAILED: " + e.toString());
	}
	remoteNode = rn;
	cachedNodeId = null;
	alive = true;
    }

    // elegantly folds in the terrible ProxyNodeHandle stuff
    public RMINodeHandle(RMIPastryNode rn, NodeId nid) {
	System.out.println("[rmi] creating RMI handle for local node: " + nid);
	remoteNode = rn;
	cachedNodeId = nid;
	alive = true;
    }

    public RMIPastryNode getRemote() { return remoteNode; }

    public NodeId getNodeId() {
	if (cachedNodeId != null) return cachedNodeId;

	try {
	    cachedNodeId = remoteNode.getNodeId();
	    return cachedNodeId;
	} catch (RemoteException e) { // failed; mark it dead
	    System.out.println("[rmi] node with unknown ID found dead in getNodeId: " + e.toString());
	    alive = false;
	    //xxx
	    return null;
	}
    }

    public boolean isAlive() { return alive; }

    public int proximity() { return 1; /* xxx */ }

    public void receiveMessage(Message msg) {
	try {
	    System.out.println("[rmi] sent message: " + msg);
	    remoteNode.receiveMessage(msg);
	    System.out.println("[rmi] message sent successfully");
	} catch (RemoteException e) { // failed; mark it dead
	    if (cachedNodeId == null) {
		System.out.println("[rmi] message failed; remote node declared dead: " + e.toString());
	    } else {
		System.out.println("[rmi] message failed; remote node " + cachedNodeId + " declared dead: " + e.toString());
	    }

	    alive = false;
	    // bounce back to local dispatcher
	}
    }
}
