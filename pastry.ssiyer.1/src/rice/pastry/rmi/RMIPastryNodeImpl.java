package rice.pastry.rmi;

import rice.pastry.*;
import rice.pastry.messaging.*;

import java.rmi.server.UnicastRemoteObject;
import java.rmi.RemoteException;

/**
 * An RMI-exported proxy object associated with each Pastry node. Its remote
 * interface is exported over RMI (and not the PastryNode itself, which is
 * RMI-unaware), and acts as a proxy, explicitly calling PastryNode methods.
 *
 * @author Sitaram Iyer
 */

class RMIPastryNodeImpl extends UnicastRemoteObject implements RMIPastryNode
{
    private PastryNode node;

    /**
     * Constructor
     */
    public RMIPastryNodeImpl() throws RemoteException { node = null; }

    /**
     * sets the local Pastry node (local method)
     * @param n the local pastry node that this helper is associated with.
     */
    public void setLocalPastryNode(PastryNode n) { node = n; }

    /**
     * Proxies to the local node to get the local NodeId (remotely invoked).
     */
    public NodeId getNodeId() { return node.getNodeId(); }

    /**
     * Proxies to the local node to accept a message (remotely invoked).
     */
    public void receiveMessage(Message msg) {
	System.out.println("[rmi] received message: " + msg);
	node.receiveMessage(msg);
    }
}
