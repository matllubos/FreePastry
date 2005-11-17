
package rice.pastry.direct;

import rice.Continuation;
import rice.environment.Environment;
import rice.environment.logging.*;
import rice.environment.logging.CloneableLogManager;
import rice.p2p.commonapi.CancellableTask;
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

  /**
   * Main constructor.
   * 
   * @param nf the NodeIdFactory
   * @param sim the NetworkSimulator
   * @param e the Enviornment
   */
  public DirectPastryNodeFactory(NodeIdFactory nf, NetworkSimulator sim, Environment env) {    
    super(env);
    nidFactory = nf;
    simulator = sim;
  }

  /**
   * Getter for the NetworkSimulator.
   * 
   * @return the NetworkSimulator we are using.
   */
  public NetworkSimulator getNetworkSimulator() { return simulator; }
  
  /**
   * Manufacture a new Pastry node.
   *
   * @return a new PastryNode
   */
  public PastryNode newNode(NodeHandle bootstrap) {
    return newNode(bootstrap, nidFactory.generateNodeId());
  }

  Hashtable recordTable = new Hashtable();
  
  /**
   * Manufacture a new Pastry node.
   *
   * @return a new PastryNode
   */
  public PastryNode newNode(NodeHandle bootstrap, NodeId nodeId) {
    if (bootstrap == null)
      environment.getLogManager().getLogger(DirectPastryNodeFactory.class, null).log(Logger.WARNING, 
        "No bootstrap node provided, starting a new ring...");
    
    // this code builds a different environment for each PastryNode
    Environment environment = this.environment;
    if (this.environment.getParameters().getBoolean("pastry_factory_multipleNodes")) {
      if (this.environment.getLogManager() instanceof CloneableLogManager) {
        environment = new Environment(
            this.environment.getSelectorManager(),
            this.environment.getProcessor(),
          this.environment.getRandomSource(),
          this.environment.getTimeSource(),
          ((CloneableLogManager)this.environment.getLogManager()).clone("0x"+nodeId.toStringBare()),
          this.environment.getParameters());
      }
    }    

    NodeRecord nr = (NodeRecord)recordTable.get(nodeId);
    if (nr == null) {
      nr = simulator.generateNodeRecord();
      recordTable.put(nodeId,nr);
    }
    DirectPastryNode pn = new DirectPastryNode(nodeId, simulator, environment, nr);

    DirectNodeHandle localhandle = new DirectNodeHandle(pn, pn, simulator);
    simulator.registerNode(pn);

    DirectSecurityManager secureMan = new DirectSecurityManager(simulator);
    MessageDispatch msgDisp = new MessageDispatch(pn);
 
    RoutingTable routeTable = new RoutingTable(localhandle, rtMax, rtBase);
    LeafSet leafSet = new LeafSet(localhandle, lSetSize);

    StandardRouter router =
      new StandardRouter(pn, secureMan);
    StandardLeafSetProtocol lsProtocol =
      new StandardLeafSetProtocol(pn, localhandle, secureMan, leafSet, routeTable);
    StandardRouteSetProtocol rsProtocol =
      new StandardRouteSetProtocol(localhandle, secureMan, routeTable, environment);

    msgDisp.registerReceiver(router.getAddress(), router);
    msgDisp.registerReceiver(lsProtocol.getAddress(), lsProtocol);
    msgDisp.registerReceiver(rsProtocol.getAddress(), rsProtocol);

    pn.setElements(localhandle, secureMan, msgDisp, leafSet, routeTable);
    pn.setDirectElements(/* simulator */);
    secureMan.setLocalPastryNode(pn);

    StandardJoinProtocol jProtocol =
      new StandardJoinProtocol(pn, localhandle, secureMan, routeTable, leafSet);    
    
    DirectNodeHandle simClosest = simulator.getClosest(localhandle);
    DirectNodeHandle closest = (DirectNodeHandle)getNearest(localhandle, bootstrap);
    if (simClosest != null) {
      int sProx = simulator.proximity(localhandle, simClosest);
      int prox = simulator.proximity(localhandle, closest);
      
      if (sProx != prox) {
        System.out.println("localhandle:"+localhandle+" closest:"+closest+":"+prox+" simClosest:"+simClosest+":"+sProx);
      }
    }    
    // pn.doneNode(bootstrap);
    //pn.doneNode( simulator.getClosest(localhandle) );    
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
   * The non-blocking versions here all execute immeadiately.  
   * This CancellableTask is just a placeholder.
   * @author Jeff Hoye
   */
  class NullCancellableTask implements CancellableTask {
    public void run() {
    }

    public boolean cancel() {
      return false;
    }

    public long scheduledExecutionTime() {
      return 0;
    }
  }

  
  public CancellableTask getLeafSet(NodeHandle handle, Continuation c) {
    DirectNodeHandle dHandle = (DirectNodeHandle) handle;
    c.receiveResult(dHandle.getRemote().getLeafSet());
    return new NullCancellableTask();  
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

  public CancellableTask getRouteRow(NodeHandle handle, int row, Continuation c) {
    DirectNodeHandle dHandle = (DirectNodeHandle) handle;
    c.receiveResult(dHandle.getRemote().getRoutingTable().getRow(row));
    return new NullCancellableTask();  
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
    return simulator.proximity((DirectNodeHandle)local, (DirectNodeHandle)remote);
  }

}
