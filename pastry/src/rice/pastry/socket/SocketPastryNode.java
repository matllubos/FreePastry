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
      SocketSourceRouteManager srManager, int lsmf, int rsmf) {
    this.address = address;
    this.srManager = srManager;
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

  /**
   * EpochInetSocketAddress -> WeakReference(NodeHandle)
   * 
   * Note that it is critical to keep the key == NodeHandle.eaddress.
   * 
   * And I mean the same object!!! not .equals(). The whole memory management
   * will get confused if this is not the case.
   */
  WeakHashMap nodeHandles = new WeakHashMap();

  public NodeHandle coalesce(NodeHandle newHandle) {
    SocketNodeHandle snh = (SocketNodeHandle) newHandle;
    synchronized (nodeHandles) {
      WeakReference wr = (WeakReference) nodeHandles.get(snh.eaddress);
      if (wr == null) {
        addNodeHandle(snh);
        return snh;
      } else {
        SocketNodeHandle ret = (SocketNodeHandle) wr.get();
        if (ret == null) {
          // if this happens, then the handle got collected, but not the
          // eaddress yet. Grumble...
          addNodeHandle(snh);
          return snh;
        } else {
          // inflates a stub NodeHandle
          if (ret.getNodeId() == null) {
            ret.setNodeId(newHandle.getNodeId());
          }
          return ret;
        }
      }
    }
  }

  private void addNodeHandle(SocketNodeHandle snh) {
    WeakReference wr = new WeakReference(snh);
    nodeHandles.put(snh.eaddress, wr);
    snh.setLocalNode(this);
  }

  public SocketNodeHandle getNodeHandle(EpochInetSocketAddress address) {
    synchronized (nodeHandles) {
      WeakReference wr = (WeakReference) nodeHandles.get(address);
      if (wr == null)
        return null;

      SocketNodeHandle ret = (SocketNodeHandle) wr.get();
      if (ret == null)
        return null;
      if (ret.getNodeId() == null)
        return null;
      return ret;
    }
  }

  public AddressManager getAddressManager(EpochInetSocketAddress address) {
    WeakReference wr = (WeakReference) nodeHandles.get(address);
    if (wr == null)
      return null;

    SocketNodeHandle snh = (SocketNodeHandle) wr.get();
    if (snh == null)
      return null;
    return snh.addressManager;
  }

  /**
   * Should be called while synchronized on nodeHandles
   * 
   * @param address
   * @param manager
   */
  public void putAddressManager(EpochInetSocketAddress address,
      AddressManager manager) {
    WeakReference wr = (WeakReference) nodeHandles.get(address);
    SocketNodeHandle snh;
    if (wr == null) {
      snh = new SocketNodeHandle(address, null);
      snh.setLocalNode(this);
      wr = new WeakReference(snh);
      nodeHandles.put(address, wr);
    } else {
      snh = (SocketNodeHandle) wr.get();
      if (snh == null) {
        // WARNING: this code must be repeated because of a very slight timing
        // issue with the garbage collector
        snh = new SocketNodeHandle(address, null);
        snh.setLocalNode(this);
        wr = new WeakReference(snh);
        nodeHandles.put(address, wr);
      }
    }

    if (snh.addressManager != null)
      throw new IllegalStateException("Address manager for address " + address
          + " already exists.");

    snh.addressManager = manager;
  }
}
