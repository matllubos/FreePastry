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

package rice.pastry.wire;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.standard.*;
import rice.pastry.routing.*;
import rice.pastry.leafset.*;
import rice.pastry.dist.*;

import java.util.*;
import java.net.*;
import java.io.*;
import java.nio.channels.*;
import java.nio.*;
import rice.pastry.wire.messaging.socket.*;

/**
 * Pastry node factory for Wire-linked nodes.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class WirePastryNodeFactory extends DistPastryNodeFactory {

  private NodeIdFactory nidFactory;

  private int port;

  private static final int rtMax = 8;
  private static final int lSetSize = 24;
  private static final int maxOpenSockets = 5;

  /**
   * Large period (in seconds) means infrequent, 0 means never.
   */
  private static final int leafSetMaintFreq = 5*60;
  private static final int routeSetMaintFreq = 15*60;

  /**
   * Constructor.
   *
   * @param p RMI registry port.
   */
  public WirePastryNodeFactory(NodeIdFactory nf, int startPort) {
    nidFactory = nf;
    port = startPort;
  }

  /**
   * Method which contructs a node handle (using the wire protocol) for the
   * node at address NodeHandle.
   *
   * @param address The address of the remote node.
   * @return A NodeHandle cooresponding to that address
   */
  public NodeHandle generateNodeHandle(InetSocketAddress address) {
    // send nodeId request to remote node, wait for response
    // allocate enought bytes to read a node handle
    WireNodeHandle handle = null;

    try {
      // create reader and writer
      SocketChannelWriter writer = new SocketChannelWriter(null);
      SocketChannelReader reader = new SocketChannelReader(null);

      // bind to the appropriate port
      SocketChannel channel = SocketChannel.open();
      channel.configureBlocking(true);
      channel.socket().connect(address);

      writer.enqueue(new NodeIdRequestMessage());
      System.out.println("Contacting boot node " + address);

      writer.write(channel);
      Object o = null;

      while (o == null) {
        o = reader.read(channel);
      }

      debug("Read " + o + " from remote node...");

      NodeIdResponseMessage rm = (NodeIdResponseMessage) o;
      handle = new WireNodeHandle(address, rm.getNodeId());

      channel.socket().close();
      channel.close();

      return handle;
    } catch (IOException e) {
      System.out.println("Error connecting to address " + address + ": " + e);
      return null;
    }
  }

  /**
   * Method which creates a Pastry node from the next port
   * with a randomly generated NodeId.
   *
   * @param bootstrap Node handle to bootstrap from.
   * @return A node with a random ID and next port number.
   */
  public PastryNode newNode(final NodeHandle bootstrap) {
    NodeId nodeId = nidFactory.generateNodeId();

    // HACKED TO MAKE TEST HARNESS WORK
    //
    // DO NOT CHECK IN OR USE!
    //if (bootstrap == null) {
    //  nodeId = new NodeId(new byte[NodeId.nodeIdBitLength]);
    //}

    final WirePastryNode pn = new WirePastryNode(nodeId);

    SelectorManager sManager = new SelectorManager(pn);

    DatagramManager dManager = new DatagramManager(pn, sManager, port);

    SocketManager socketManager = new SocketManager(pn, port , sManager.getSelector());

    InetSocketAddress address = getAddress(port);

    WireNodeHandle localhandle = new WireNodeHandle(address, nodeId);

    WireNodeHandlePool pool = new WireNodeHandlePool(pn);

    WirePastrySecurityManager secureMan = new WirePastrySecurityManager(localhandle, pool);

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

    pn.setElements(localhandle, secureMan, msgDisp, leafSet, routeTable);
    pn.setSocketElements(address, sManager, dManager, socketManager, pool, leafSetMaintFreq, routeSetMaintFreq);
    secureMan.setLocalPastryNode(pn);

    pool.coalesce(localhandle);
    localhandle.setLocalNode(pn);

    if (bootstrap != null)
      bootstrap.setLocalNode(pn);

    // launch thread to handle the sockets
    Thread t = new Thread("Thread for node " + nodeId) {
      public void run() {
        pn.doneNode(bootstrap);
      }
    };

    pn.setThread(t);

    t.start();

    port++;

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

  private void debug(String s) {
    if (Log.ifp(6))
      System.out.println(" (F): " + s);
  }
}

