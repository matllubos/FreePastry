package rice.pastry.rmi;

import rice.pastry.*;
import rice.pastry.messaging.*;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * A remote interface exported by Pastry nodes. This is a subset of
 * NodeHandle, since it doesn't implement proximity or getAlive.
 *
 * @author Sitaram Iyer
 */

public interface RMIPastryNode extends Remote
{
    public NodeId getNodeId() throws java.rmi.RemoteException;
    public void receiveMessage(Message msg) throws java.rmi.RemoteException;
}
