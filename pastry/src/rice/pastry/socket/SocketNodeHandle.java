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
import rice.pastry.dist.*;
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
public class SocketNodeHandle extends DistNodeHandle {

  // the ip address and port of the remote node
  private InetSocketAddress address;

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
    super(nid);

    debug("creating Socket handle for node: " + nid + " address: " + address);

    this.address = address;
    lastpingtime = 0;
  }

  /**
   * Alternate constructor with local Pastry node.
   *
   * @param address The address of the host on which this node resides
   * @param nid The NodeId of this host
   * @param pn The local Pastry node
   */
  public SocketNodeHandle(InetSocketAddress address, NodeId nid, PastryNode pn) {
    super(nid);

    debug("creating Socket handle for node: " + nid + ", local: " + pn + " address: " + address);

    this.address = address;
    lastpingtime = 0;

    setLocalNode(pn);
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
   * Called to send a message to the node corresponding to this handle.
   *
   * @param msg Message to be delivered, may or may not be routeMessage.
   */
  public void receiveMessageImpl(Message msg) {
    assertLocalNode();

    SocketPastryNode spn = (SocketPastryNode) getLocalNode();

    if (isLocal) {
      debug("Sending message " + msg + " locally");
      spn.receiveMessage(msg);
    } else {
      debug("Passing message " + msg + " to the socket manager for writing");
      spn.getSocketManager().write(address, msg);
    }
  }

  /**
   * Ping the remote node now, and update the proximity metric.
   *
   * @return liveness of remote node.
   */
  public boolean pingImpl() {
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
      NodeId localId = getLocalNode().getNodeId();
      receiveMessage(new PingMessage(getNodeId(), localId, getAddress(), localAddress));
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
      debug("ERROR (pingResponse): Ping should never be sent to local node...");
      debug("This Error is OK *ONLY* if this is happening during the initial join phase (before this node has recieved a JoinMessage response");
      return;
    }

    long stoptime = System.currentTimeMillis();
    if (distance > (int)(stoptime - starttime))
      distance = (int)(stoptime - starttime);

    debug("Received ping - proximity is " + distance);

    markAlive();
  }

  public String toStringImpl() {
    return (isLocal ? "(local " : "") + "handle " + nodeId
            + (alive ? "" : ":dead")
            + ", localnode = " + getLocalNode()
            + " " + address + ")";
  }
}
