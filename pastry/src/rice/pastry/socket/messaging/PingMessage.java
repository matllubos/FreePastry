/*************************************************************************

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

package rice.pastry.socket.messaging;

import rice.pastry.messaging.*;
import rice.pastry.socket.*;
import rice.pastry.*;

import java.net.*;

/**
 * Class which represents a "ping" message sent through the
 * socket pastry system.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class PingMessage extends Message {

  // the time the ping began
  private long time;

  // the remote address (address of the node to be pinged)
  private InetSocketAddress remoteAddress;

  // the local address (address of the pinging node)
  private InetSocketAddress localAddress;

  // the nodeId of the pinging node
  private NodeId localId;

  // the nodeId of the pinged node
  private NodeId remoteId;

  /**
   * Constructor
   *
   * @param remoteId The pinged node's nodeId.
   * @param localId The pinging node's nodeId.
   * @param remoteAddress The address of the pinged node.
   * @param localAddress The address of the pinging node.
   */
  public PingMessage(NodeId remoteId, NodeId localId, InetSocketAddress remoteAddress, InetSocketAddress localAddress) {
    super(SocketPingManagerAddress.instance());

    this.time = System.currentTimeMillis();
    this.remoteAddress = remoteAddress;
    this.localAddress = localAddress;
    this.remoteId = remoteId;
    this.localId = localId;
  }

  /**
   * Returns the appropriate PingResponseMessage for this PingMessage.
   *
   * @return A PingResponseMessage for this PingMessage.
   */
  public PingResponseMessage getResponse() {
    return new PingResponseMessage(time, remoteId, localId, remoteAddress, localAddress);
  }

  /**
   * Returns the address of the pinged node.
   *
   * @return The address of the node to be pinged.
   */
  public InetSocketAddress getRemoteAddress() {
    return remoteAddress;
  }

  /**
   * Returns the address of the pinging node.
   *
   * @return The address of the node pinging.
   */
  public InetSocketAddress getLocalAddress() {
    return localAddress;
  }

  /**
   * Returns the nodeId of the pinging node.
   *
   * @return The nodeId of the pinging node.
   */
  public NodeId getLocalNodeId() {
    return localId;
  }

  /**
   * Returns the nodeId of the pinging node.
   *
   * @return The nodeId of the pinging node.
   */
  public NodeId getRemoteNodeId() {
    return remoteId;
  }
}