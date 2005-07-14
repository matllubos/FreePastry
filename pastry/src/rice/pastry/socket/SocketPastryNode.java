package rice.pastry.socket;

import java.io.*;
import java.net.*;
import java.util.*;

import rice.environment.Environment;
import rice.pastry.*;
import rice.pastry.client.*;
import rice.pastry.dist.*;
import rice.pastry.join.*;
import rice.pastry.leafset.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.selector.*;

/**
 * An Socket-based Pastry node, which has two threads - one thread for
 * performing route set and leaf set maintainance, and another thread for
 * listening on the sockets and performing all non-blocking I/O.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class SocketPastryNode extends DistPastryNode {

  // The address (ip + port) of this pastry node
  private EpochInetSocketAddress address;
  
  // The SocketManager, controlling the sockets
  private SocketSourceRouteManager srManager;

  // The pool of all node handles
  private SocketNodeHandlePool pool;

  /**
   * Constructor
   *
   * @param id The NodeId of this Pastry node.
   */
  public SocketPastryNode(NodeId id, Environment e) {
    super(id, e);
  }
  
  /**
   * Returns the SocketSourceRouteManager for this pastry node.
   *
   * @return The SocketSourceRouteManager for this pastry node.
   */
  public SocketSourceRouteManager getSocketSourceRouteManager() {
    return srManager;
  }

  /**
   * Returns the WireNodeHandlePool for this pastry node.
   *
   * @return The WireNodeHandlePool for this pastry node.
   */
  public DistNodeHandlePool getNodeHandlePool() {
    return pool;
  }

  /**
   * Helper method which allows the WirePastryNodeFactory to initialize a number
   * of the pastry node's elements.
   *
   * @param address The address of this pastry node.
   * @param manager The socket manager for this pastry node.
   * @param lsmf Leaf set maintenance frequency. 0 means never.
   * @param rsmf Route set maintenance frequency. 0 means never.
   * @param sManager The new SocketElements value
   * @param pingManager The new SocketElements value
   * @param pool The new SocketElements value
   */
  public void setSocketElements(EpochInetSocketAddress address,
                                SocketSourceRouteManager srManager,
                                SocketNodeHandlePool pool,
                                int lsmf,
                                int rsmf) {
    this.address = address;
    this.srManager = srManager;
    this.pool = pool;
    this.leafSetMaintFreq = lsmf;
    this.routeSetMaintFreq = rsmf;
  }

  /**
   * Called after the node is initialized.
   *
   * @param bootstrap The node which this node should boot off of.
   */
  public void doneNode(NodeHandle bootstrap) {
    super.doneNode(bootstrap);
    initiateJoin(bootstrap);
  }

  /**
   * Prints out a String representation of this node
   *
   * @return a String
   */
  public String toString() {
    return "SocketNodeHandle (" + getNodeId() + "/" + address + ")";
  }
  
  /**
   * Makes this node resign from the network.  Is designed to be used for
   * debugging and testing.
   */
  public void resign() {
    try {
      super.resign();
      srManager.resign();
    } catch (IOException e) {
      getEnvironment().getLogManager().getLogger(SocketPastryNode.class, 
          "ERROR: Got exception " + e + " while resigning node!");
    }
  }
}
