/**************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/

package rice.pastry.socket;

import rice.pastry.*;
import rice.pastry.routing.*;
import rice.pastry.messaging.*;
import rice.pastry.socket.messaging.*;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;
import java.net.*;

/**
 * Class which represents a node handle in the socket-based pastry protocol.
 * It uses the SocketManager to send messages to the remote node.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class SocketNodeHandle extends LocalNode implements NodeHandle, Serializable {

  // the ip address and port of the remote node
  private InetSocketAddress address;

  // the node id of the remote node
  private NodeId nodeId;

  // cached liveness bit, updated by the socket client
  private transient boolean alive;

  // cached proximity metric
  private transient int distance;

  // whether or not we are on the local node
  private transient boolean isLocal;

  // the time the last ping was performed
  private transient long lastpingtime;

  private int pingthrottle = 5;

  /**
   * Constructor.
   *
   * @param address The address of the host on which this node resides
   * @param port The port number of this node on the host
   * @param nid The NodeId of this host
   */
  public SocketNodeHandle(InetSocketAddress address, NodeId nid) {
    debug("creating Socket handle for node: " + nid + " address: " + address);

    init(address, nid);
  }

  /**
   * Alternate constructor with local Pastry node.
   *
   * @param address The address of the host on which this node resides
   * @param nid The NodeId of this host
   * @param pn The local Pastry node
   */
  public SocketNodeHandle(InetSocketAddress address, NodeId nid, PastryNode pn) {
    debug("creating Socket handle for node: " + nid + ", local: " + pn + " address: " + address);

    init(address, nid);
    setLocalNode(pn);
  }

  /**
   * Utility method for the constructors - finished initializing
   * the nodes.
   *
   * @param address The address of the remote node
   * @param nid The Node ID of the remote node
   */
  private void init(InetSocketAddress socketaddr, NodeId nid) {
    nodeId = nid;
    address = socketaddr;

    alive = true;
    distance = Integer.MAX_VALUE;
    isLocal = false;
    lastpingtime = 0;
  }

  /**
   * Returns the Node ID of the remote node
   *
   * @return The Node ID of the remote node.
   */
  public NodeId getNodeId() {
    return nodeId;
  }

  /**
   * Returns the IP address and port of the remote node.
   *
   * @return The InetSocketAddress of the remote node.
   */
  public InetSocketAddress getAddress() {
    return address;
  }

  /**
   * Method called from LocalNode after localnode is set to non-null.
   * Updates the isLocal and alive variables.
   */
  public void afterSetLocalNode() {
    alive = true;

    if (getLocalNode().getNodeId().equals(nodeId)) {
      isLocal = true;
    } else {
      isLocal = false;
    }
  }

  /**
   * Returns whether or not the remote node is alive
   *
   * @return a cached boolean value.
   */
  public boolean isAlive() {
    if (isLocal && !alive)
      System.out.println("panic; local node dead");

    return alive;
  }

  /**
   * Mark this handle as alive (if dead earlier), and reset distance to
   * infinity.
   */
  public void markAlive() {
    if (alive == false) {
      if (Log.ifp(5))
        System.out.println(getLocalNode() + "found " + nodeId + " to be alive after all");

      alive = true;
      distance = Integer.MAX_VALUE; // reset to infinity. alternatively, recompute.
    }
  }

  /**
   * Mark this handle as dead (if alive earlier), and reset distance to
   * infinity.
   */
  public void markDead() {
    if (alive == true) {
      if (Log.ifp(5))
        System.out.println(getLocalNode() + "found " + nodeId + " to be dead");

      alive = false;
      distance = Integer.MAX_VALUE;
    }
  }

  /**
   * Proximity metric.
   *
   * @return the cached proximity value (Integer.MAX_VALUE initially), or
   * 0 if node is local.
   */
  public int proximity() {
    if (isLocal)
      return 0;

    return distance;
  }

  /**
   * Called to send a message to the node corresponding to this handle.
   *
   * @param msg Message to be delivered, may or may not be routeMessage.
   */
  public void receiveMessage(Message msg) {
    assertLocalNode();

    SocketPastryNode spn = (SocketPastryNode) getLocalNode();

    if (isLocal) {
      debug("Sending message " + msg + " locally");
      spn.receiveMessage(msg);
    } else {
      debug("Passing message " + msg + " to the socket client for writing");
      spn.getSocketManager().write(address, msg);
    }
  }

  /**
   * Ping the remote node now, and update the proximity metric.
   *
   * @return liveness of remote node.
   */
  public boolean ping() {
    if (isLocal) {
      distance = 0;
      return alive;
    }

    long now = System.currentTimeMillis();
    if (now - lastpingtime < pingthrottle*1000)
        return alive;
    lastpingtime = now;

    if (getLocalNode() != null) {
      debug("Pinging " + nodeId);

      InetSocketAddress localAddress = ((SocketNodeHandle) getLocalNode().getLocalHandle()).getAddress();
      receiveMessage(new PingMessage(getNodeId(), getAddress(), localAddress));
    }

    return alive;
  }

  /**
   * Method which is called by the SocketPingManager when a
   * ping response comes back for this node.
   *
   * @param starttime The time at which this ping was initiated.
   */
  public void pingResponse(long starttime) {
    if (isLocal) {
      System.out.println("ERROR (pingResponse): Ping should never be sent to local node...");
      return;
    }

    long stoptime = System.currentTimeMillis();
    if (distance > (int)(stoptime - starttime))
      distance = (int)(stoptime - starttime);

    debug("Received ping - proximity is " + distance);

    markAlive();
  }

  public String toString() {
    return (isLocal ? "(local " : "") + "handle " + nodeId
            + (alive ? "" : ":dead")
            + ", localnode = " + getLocalNode()
            + ")";
  }

  private void debug(String s) {
    if (Log.ifp(6))
      System.out.println(getLocalNode().getNodeId() + " (" + nodeId + "): " + s);
  }
}
