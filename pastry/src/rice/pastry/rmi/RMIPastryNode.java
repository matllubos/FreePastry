package rice.pastry.rmi;

import rice.pastry.*;
import rice.pastry.messaging.*;

import java.rmi.server.UnicastRemoteObject;
import java.rmi.RemoteException;

/**
 * RMIPastryNodeImpl
 *
 * an RMI-exported object associated with each Pastry node, serving as an
 * RMI proxy for the two exported methods. We're exporting this object and
 * not the PastryNode itself, to abstract out the RMI wire protocol stuff.
 *
 * @author Sitaram Iyer
 */

class RMIPastryNodeImpl extends UnicastRemoteObject implements RMIPastryNode
{
    private PastryNode node;
    public RMIPastryNodeImpl() throws RemoteException { node = null; }
    public void setLocalPastryNode(PastryNode n) { node = n; }

    public NodeId getNodeId() { return node.getNodeId(); }

    public void receiveMessage(Message msg) {
	System.out.println("[rmi] received message: " + msg);
	node.receiveMessage(msg);
    }
}
