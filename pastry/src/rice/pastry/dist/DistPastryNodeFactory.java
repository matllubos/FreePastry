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

package rice.pastry.dist;

import rice.*;
import rice.Continuation.*;
import rice.pastry.*;
import rice.pastry.rmi.*;
import rice.pastry.socket.*;
import rice.pastry.wire.*;
import rice.pastry.messaging.*;

import java.net.*;
import java.util.*;

/**
 * An abstraction of the nodeId factory for distributed nodes. In order to
 * obtain a nodeId factory, a client should use the getFactory method, passing
 * in either PROTOCOL_RMI or PROTOCOL_WIRE as the protocol, and the port number
 * the factory should use.  In the wire protocol, the port number is the starting
 * port number that the nodes are constructed on, and in the rmi protocol, the
 * port number is the location of the local RMI registry.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public abstract class DistPastryNodeFactory extends PastryNodeFactory {

  // choices of protocols
  public static int PROTOCOL_RMI = 0;
  public static int PROTOCOL_WIRE = 1;
  public static int PROTOCOL_SOCKET = 2;

  /**
   * Constructor. Protected - one should use the getFactory method.
   */
  protected DistPastryNodeFactory() {
  }

  /**
   * Method which all subclasses should implement allowing the client to
   * generate a node handle given the address of a node.  This is designed to
   * allow the client to get their hands on a bootstrap node during the
   * initialization phase of the client application.
   *
   * @param The location of the remote node.
   */
  public abstract NodeHandle generateNodeHandle(InetSocketAddress address);

  /**
   * Method which a client should use in order to get a bootstrap node from the
   * factory.
   *
   * In the wire protocol, this method will generate a node handle corresponding to
   * the pastry node at location address.  In the rmi protocol, this method will
   * generate a node handle for the pastry node bound to address.
   *
   * @param address The address of the remote node.
   */
  public final NodeHandle getNodeHandle(InetSocketAddress address) {
    return generateNodeHandle(address);
  }

  /**
   * Generates a new pastry node with a random NodeId using the bootstrap
   * bootstrap.
   *
   * @param bootstrap Node handle to bootstrap from.
   */
  public abstract PastryNode newNode(NodeHandle bootstrap);

  /**
   * Generates a new pastry node with the specified NodeId using the bootstrap
   * bootstrap.
   *
   * @param bootstrap Node handle to bootstrap from.
   */
  public abstract PastryNode newNode(NodeHandle bootstrap, NodeId nodeId);

  /**
   * Static method which is designed to be used by clients needing a
   * distrubuted pastry node factory.  The protocol should be one of
   * PROTOCOL_RMI or PROTOCOL_WIRE.  The port is protocol-dependent, and
   * is the port number of the RMI registry if using RMI, or is the
   * starting port number the nodes should be created on if using wire.
   *
   * @param protocol The protocol to use (PROTOCOL_RMI or PROTOCOL_WIRE)
   * @param port The RMI registry port if RMI, or the starting port if wire.
   * @return A DistPastryNodeFactory using the given protocol and port.
   * @throws IllegalArgumentException If protocol is an unsupported port.
   */
  public static DistPastryNodeFactory getFactory(NodeIdFactory nf, int protocol, int port) {
    if (protocol == PROTOCOL_RMI)
      return new RMIPastryNodeFactory(nf, port);
    else if (protocol == PROTOCOL_WIRE)
      return new WirePastryNodeFactory(nf, port);
    else if (protocol == PROTOCOL_SOCKET)
      return new SocketPastryNodeFactory(nf, port);

    throw new IllegalArgumentException("Unsupported Protocol " + protocol);
  }
}

