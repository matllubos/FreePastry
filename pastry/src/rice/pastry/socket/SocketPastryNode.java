package rice.pastry.socket;

import java.io.*;
import java.lang.ref.WeakReference;
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
import rice.pastry.socket.SocketSourceRouteManager.AddressManager;
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
  SocketSourceRouteManager srManager;

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
      int lsmf, int rsmf) {
    this.address = address;
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
   * Makes this node resign from the network. Is designed to be used for
   * debugging and testing.
   * 
   * If run on the SelectorThread, then destroys now. Other threads cause a task
   * to be placed on the selector, and destroyed asap. Make sure to call
   * super.destroy() !!!
   */
  public void destroy() {
    super.destroy();
    if (getEnvironment().getSelectorManager().isSelectorThread()) {
      // destroy now
      try {
        super.destroy();
        srManager.destroy();
      } catch (IOException e) {
        getEnvironment().getLogManager().getLogger(SocketPastryNode.class,
            "ERROR: Got exception " + e + " while resigning node!");
      }
    } else {
      // schedule to be destroyed on the selector
      getEnvironment().getSelectorManager().invoke(new Runnable() {
        public void run() {
          destroy();
        }
      });
    }
  }

  public NodeHandle coalesce(NodeHandle newHandle) {
    return srManager.coalesce(newHandle);
  }

  public void setSocketSourceRouteManager(SocketSourceRouteManager srManager) {
    this.srManager = srManager;
  }

}
