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

- Neither  the name of Rice  University (RICE) nor  the names  of its
contributors may be used to endorse or promote  products derived from
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
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.standard.*;
import rice.pastry.routing.*;
import rice.pastry.leafset.*;

import java.util.*;
import java.net.*;

/**
 * Pastry node factory for Socket-linked nodes.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class SocketPastryNodeFactory implements PastryNodeFactory {

  private RandomNodeIdFactory nidFactory;

  private int port;

  private static final int rtMax = 8;
  private static final int lSetSize = 24;
  private static final int maxOpenSockets = 16;

  /**
   * Large period (in seconds) means infrequent, 0 means never.
   */
  private static final int leafSetMaintFreq = 60;
  private static final int routeSetMaintFreq = 15*60;

  /**
   * Constructor.
   *
   * @param p RMI registry port.
   */
  public SocketPastryNodeFactory(int startPort) {
    nidFactory = new RandomNodeIdFactory();
    port = startPort;
  }

  /**
   * Method which creates a Pastry node from the next port
   * with a randomly generated NodeId.
   *
   * @param bootstrap Node handle to bootstrap from.
   * @return A node with a random ID and next port number.
   */
  public PastryNode newNode(NodeHandle bootstrap) {
    return newNode(bootstrap, port++, nidFactory.generateNodeId());
  }

  /**
   * Method which creates a Pastry node at the specified port
   * with a randomly generated NodeId.
   *
   * @param bootstrap Node handle to bootstrap from.
   * @param portNumber The port number to create the pastry node at.
   * @return A node with a random ID at port portNumber.
   */
  public PastryNode newNode(NodeHandle bootstrap, int portNumber) {
    return newNode(bootstrap, portNumber, nidFactory.generateNodeId());
  }

  /**
   * Method which creates a Pastry node at the next port
   * with a specified NodeId.
   *
   * @param bootstrap Node handle to bootstrap from.
   * @param nodeId The nodeId to create the new node with.
   * @return A node with the specified ID at the next port.
   */
  public PastryNode newNode(NodeHandle bootstrap, NodeId nodeId) {
    return newNode(bootstrap, port++,  nodeId);
  }

  /**
   * Method which creates a Pastry node at the specified port with
   * the specified node ID.
   *
   * @param bootstrap Node handle to bootstrap from.
   * @param nodeId The nodeId to create the new node with.
   * @param portNumber The port number to create the pastry node at.
   * @return A node with the specified ID and  port number.
   */
  public PastryNode newNode(final NodeHandle bootstrap, int portNumber, NodeId nodeId) {
    final SocketPastryNode pn = new SocketPastryNode(nodeId);

    SocketManager socketManager = new SocketManager(pn, portNumber);

    if (bootstrap != null)
      bootstrap.setLocalNode(pn);

    InetSocketAddress address = getAddress(portNumber);

    SocketNodeHandle localhandle = new SocketNodeHandle(address, nodeId, pn);

    SocketNodeHandlePool pool = new SocketNodeHandlePool(pn);

    SocketPastrySecurityManager secureMan = new SocketPastrySecurityManager(localhandle, pool);

    SocketPingManager manager = new SocketPingManager(pn, secureMan);

    MessageDispatch msgDisp = new MessageDispatch();

    RoutingTable routeTable = new RoutingTable(localhandle, rtMax);
    LeafSet leafSet = new LeafSet(localhandle, lSetSize);

    StandardRouter router =
       new StandardRouter(localhandle, routeTable, leafSet, secureMan);
    StandardLeafSetProtocol lsProtocol =
       new StandardLeafSetProtocol(localhandle, secureMan, leafSet, routeTable);
    StandardRouteSetProtocol rsProtocol =
       new StandardRouteSetProtocol(localhandle, secureMan, routeTable);
    StandardJoinProtocol jProtocol =
       new StandardJoinProtocol(pn, localhandle, secureMan, routeTable, leafSet);

    msgDisp.registerReceiver(router.getAddress(), router);
    msgDisp.registerReceiver(lsProtocol.getAddress(), lsProtocol);
    msgDisp.registerReceiver(rsProtocol.getAddress(), rsProtocol);
    msgDisp.registerReceiver(jProtocol.getAddress(), jProtocol);
    msgDisp.registerReceiver(manager.getAddress(), manager);

    pn.setElements(localhandle, secureMan, msgDisp, leafSet, routeTable);
    pn.setSocketElements(address, socketManager, pool, leafSetMaintFreq, routeSetMaintFreq);
    secureMan.setLocalPastryNode(pn);

    pool.coalesce(localhandle);

    // launch thread to handle the sockets
    Thread t = new Thread("Thread for node " + nodeId) {
      public void run() {
        pn.doneNode(bootstrap);
      }
    };

    t.start();

    return pn;
  }

  /**
   * Method which constructs an InetSocketAddres for the local host
   * with the specifed port number.
   *
   * @param portNumber The port number to create the address at.
   * @return An InetSocketAddress at the localhost with port portNumber.
   */
  private InetSocketAddress getAddress(int portNumber) {
    InetSocketAddress result = null;

    try {
      result = new InetSocketAddress(InetAddress.getLocalHost(), portNumber);
    } catch (UnknownHostException e) {
      System.out.println("PANIC: Unknown host in getAddress. " + e);
    }

    return result;
  }
}

