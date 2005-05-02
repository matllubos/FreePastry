
package rice.pastry.direct;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.standard.*;
import rice.pastry.routing.*;
import rice.pastry.leafset.*;

import java.io.*;
import java.util.*;

/**
 * Pastry node factory for direct connections between nodes (local instances).
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 * @author Sitaram Iyer
 * @author Rongmei Zhang/Y. Charlie Hu
 */
public class DirectPastryNodeFactory extends PastryNodeFactory {

  private NodeIdFactory nidFactory;
  private NetworkSimulator simulator;

  // max number of handles stored per routing table entry
  private static final int rtMax = 1;

  // leafset size
  private static final int lSetSize = 24;

  public DirectPastryNodeFactory(NodeIdFactory nf, NetworkSimulator sim) {
    nidFactory = nf;
    simulator = sim;
  }

  public NetworkSimulator getNetworkSimulator() { return simulator; }
  
  /**
   * Manufacture a new Pastry node.
   *
   * @return a new PastryNode
   */
  public PastryNode newNode(NodeHandle bootstrap) {
    return newNode(bootstrap, nidFactory.generateNodeId());
  }

  /**
   * Manufacture a new Pastry node.
   *
   * @return a new PastryNode
   */
  public PastryNode newNode(NodeHandle bootstrap, NodeId nodeId) {
    DirectPastryNode pn = new DirectPastryNode(nodeId, simulator);

    DirectNodeHandle localhandle = new DirectNodeHandle(pn, pn, simulator);
    simulator.registerNodeId( localhandle );

    DirectSecurityManager secureMan = new DirectSecurityManager(simulator);
    MessageDispatch msgDisp = new MessageDispatch(pn);

    RoutingTable routeTable = new RoutingTable(localhandle, rtMax);
    LeafSet leafSet = new LeafSet(localhandle, lSetSize);

    StandardRouter router =
      new StandardRouter(localhandle, routeTable, leafSet, secureMan);
    StandardLeafSetProtocol lsProtocol =
      new StandardLeafSetProtocol(pn, localhandle, secureMan, leafSet, routeTable);
    StandardRouteSetProtocol rsProtocol =
      new StandardRouteSetProtocol(localhandle, secureMan, routeTable);
    StandardJoinProtocol jProtocol =
      new StandardJoinProtocol(pn, localhandle, secureMan, routeTable, leafSet);

    msgDisp.registerReceiver(router.getAddress(), router);
    msgDisp.registerReceiver(lsProtocol.getAddress(), lsProtocol);
    msgDisp.registerReceiver(rsProtocol.getAddress(), rsProtocol);
    msgDisp.registerReceiver(jProtocol.getAddress(), jProtocol);

    pn.setElements(localhandle, secureMan, msgDisp, leafSet, routeTable);
    pn.setDirectElements(/* simulator */);
    secureMan.setLocalPastryNode(pn);

    // pn.doneNode(bootstrap);
    //pn.doneNode( simulator.getClosest(nodeId) );
    pn.doneNode(getNearest(localhandle, bootstrap));
      
    return pn;
  }

  /**
   * This method returns the remote leafset of the provided handle
   * to the caller, in a protocol-dependent fashion.  Note that this method
   * may block while sending the message across the wire.
   *
   * @param handle The node to connect to
   * @return The leafset of the remote node
   */
  public LeafSet getLeafSet(NodeHandle handle) throws IOException {
    DirectNodeHandle dHandle = (DirectNodeHandle) handle;

    return dHandle.getRemote().getLeafSet();
  }

  /**
   * This method returns the remote route row of the provided handle
   * to the caller, in a protocol-dependent fashion.  Note that this method
   * may block while sending the message across the wire.
   *
   * @param handle The node to connect to
   * @param row The row number to retrieve
   * @return The route row of the remote node
   */
  public RouteSet[] getRouteRow(NodeHandle handle, int row) throws IOException {
    DirectNodeHandle dHandle = (DirectNodeHandle) handle;

    return dHandle.getRemote().getRoutingTable().getRow(row);
  }

  /**
   * This method determines and returns the proximity of the current local
   * node the provided NodeHandle.  This will need to be done in a protocol-
   * dependent fashion and may need to be done in a special way.
   *
   * @param handle The handle to determine the proximity of
   * @return The proximity of the provided handle
   */
  public int getProximity(NodeHandle local, NodeHandle remote) {
    return simulator.proximity(local.getNodeId(), remote.getNodeId());
  }
}
