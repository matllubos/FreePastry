package rice.pastry.rmi;

import rice.pastry.*;
import rice.pastry.routing.*;
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
    private RMINodeHandlePool handlepool;

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
     * sets the local handle pool (local method)
     * @param hp the handle pool maintained by the local pastry node
     */
    public void setHandlePool(RMINodeHandlePool hp) { handlepool = hp; }

    /**
     * Proxies to the local node to get the local NodeId.
     */
    public NodeId getNodeId() { return node.getNodeId(); }

    /**
     * Proxies to the local node to accept a message.
     */
    public void receiveMessage(Message msg) {
	/*
	 * The sender of this message is alive. So if we have a handle in
	 * our pool with this Id, then it should be reactivated.
	 */
	NodeId sender = msg.getSenderId();
	if (sender != null) handlepool.activate(sender);

	System.out.println("[rmi] received " +
			   (msg instanceof RouteMessage ? "route" : "direct")
			   + " msg from " + sender + ": " + msg);

	node.receiveMessage(msg);
    }
}
