
package rice.pastry.rmi;

import rice.pastry.*;
import rice.pastry.leafset.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * A remote interface exported by Pastry nodes. This is a subset of
 * NodeHandle, since it doesn't implement proximity, ping or getAlive.
 *
 * @version $Id$
 *
 * @author Sitaram Iyer
 */
public interface RMIRemoteNodeI extends Remote
{
    public LeafSet getLeafSet() throws java.rmi.RemoteException;
    public RouteSet[] getRouteRow(int row) throws java.rmi.RemoteException;
    public NodeId getNodeId() throws java.rmi.RemoteException;
    public void remoteReceiveMessage(Message msg, NodeId hopDest) throws java.rmi.RemoteException;
}

