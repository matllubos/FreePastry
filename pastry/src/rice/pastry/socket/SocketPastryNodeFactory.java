/**
 * "FreePastry" Peer-to-Peer Application Development Substrate Copyright 2002,
 * Rice University. All rights reserved. Redistribution and use in source and
 * binary forms, with or without modification, are permitted provided that the
 * following conditions are met: - Redistributions of source code must retain
 * the above copyright notice, this list of conditions and the following
 * disclaimer. - Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution. -
 * Neither the name of Rice University (RICE) nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission. This software is provided by RICE and the
 * contributors on an "as is" basis, without any representations or warranties
 * of any kind, express or implied including, but not limited to,
 * representations or warranties of non-infringement, merchantability or fitness
 * for a particular purpose. In no event shall RICE or contributors be liable
 * for any direct, indirect, incidental, special, exemplary, or consequential
 * damages (including, but not limited to, procurement of substitute goods or
 * services; loss of use, data, or profits; or business interruption) however
 * caused and on any theory of liability, whether in contract, strict liability,
 * or tort (including negligence or otherwise) arising in any way out of the use
 * of this software, even if advised of the possibility of such damage.
 */

package rice.pastry.socket;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.SocketChannel;

import rice.pastry.Log;
import rice.pastry.NodeHandle;
import rice.pastry.NodeId;
import rice.pastry.NodeIdFactory;
import rice.pastry.PastryNode;
import rice.pastry.churn.ChurnJoinProtocol;
import rice.pastry.churn.ChurnLeafSetProtocol;
import rice.pastry.dist.DistPastryNodeFactory;
import rice.pastry.leafset.LeafSet;
import rice.pastry.messaging.Message;
import rice.pastry.messaging.MessageDispatch;
import rice.pastry.routing.RouteSet;
import rice.pastry.routing.RoutingTable;
import rice.pastry.socket.messaging.LeafSetRequestMessage;
import rice.pastry.socket.messaging.LeafSetResponseMessage;
import rice.pastry.socket.messaging.NodeIdRequestMessage;
import rice.pastry.socket.messaging.NodeIdResponseMessage;
import rice.pastry.socket.messaging.RouteRowRequestMessage;
import rice.pastry.socket.messaging.RouteRowResponseMessage;
import rice.pastry.standard.StandardJoinProtocol;
import rice.pastry.standard.StandardLeafSetProtocol;
import rice.pastry.standard.StandardRouteSetProtocol;
import rice.pastry.standard.StandardRouter;
import rice.selector.SelectorManager;

/**
 * Pastry node factory for Socket-linked nodes.
 *
 * @version $Id: SocketPastryNodeFactory.java,v 1.6 2004/03/08 19:53:57 amislove
 *      Exp $
 * @author Alan Mislove, Jeff Hoye
 */
public class SocketPastryNodeFactory extends DistPastryNodeFactory {

  private NodeIdFactory nidFactory;

  private int port;

  private final static int rtMax = 1;
  private final static int lSetSize = 24;

  /**
   * Large period (in seconds) means infrequent, 0 means never.
   */
  private final static int leafSetMaintFreq = 1 * 60;
  private final static int routeSetMaintFreq = 15 * 60;

  public static boolean churn = false;
  public static boolean useNearest = true;
  

  /**
   * Constructor.
   *
   * @param nf The factory for building node ids
   * @param startPort The port to start creating nodes on
   */
  public SocketPastryNodeFactory(NodeIdFactory nf, int startPort) {
    nidFactory = nf;
    port = startPort;
  }

  /**
   * This method returns the remote leafset of the provided handle to the
   * caller, in a protocol-dependent fashion. Note that this method may block
   * while sending the message across the wire.
   *
   * @param handle The node to connect to
   * @return The leafset of the remote node
   */
  public LeafSet getLeafSet(NodeHandle handle) {
    SocketNodeHandle wHandle = (SocketNodeHandle) handle;

    try {
      LeafSetResponseMessage lm = (LeafSetResponseMessage) getResponse(wHandle.getAddress(), new LeafSetRequestMessage());

      return lm.getLeafSet();
    } catch (IOException e) {
      System.out.println("Error connecting to (leafset) address " + wHandle.getAddress() + ": " + e);
      return null;
    }
  }

  /**
   * This method returns the remote route row of the provided handle to the
   * caller, in a protocol-dependent fashion. Note that this method may block
   * while sending the message across the wire.
   *
   * @param handle The node to connect to
   * @param row The row number to retrieve
   * @return The route row of the remote node
   */
  public RouteSet[] getRouteRow(NodeHandle handle, int row) {
    SocketNodeHandle wHandle = (SocketNodeHandle) handle;

    try {
      RouteRowResponseMessage rm = (RouteRowResponseMessage) getResponse(wHandle.getAddress(), new RouteRowRequestMessage(row));

      return rm.getRouteRow();
    } catch (IOException e) {
      System.out.println("Error connecting to (routerow) address " + wHandle.getAddress() + ": " + e);
      return new RouteSet[0];
    }
  }

  /**
   * This method determines and returns the proximity of the current local node
   * the provided NodeHandle. This will need to be done in a protocol- dependent
   * fashion and may need to be done in a special way.
   *
   * @param handle The handle to determine the proximity of
   * @param local DESCRIBE THE PARAMETER
   * @return The proximity of the provided handle
   */
  public int getProximity(NodeHandle local, NodeHandle handle) {
    SocketNodeHandle lHandle = (SocketNodeHandle) local;
    SocketNodeHandle wHandle = (SocketNodeHandle) handle;

    // if this is a request for an old version of us, then we return
    // infinity as an answer
    if (lHandle.getAddress().equals(wHandle.getAddress())) {
      return Integer.MAX_VALUE;
    }

    if (wHandle.proximity() == SocketNodeHandle.DEFAULT_PROXIMITY) {
      try {
        long startTime = System.currentTimeMillis();
        getResponse(wHandle.getAddress(), new NodeIdRequestMessage());
        long ping = System.currentTimeMillis() - startTime;

        return (int) ping;
      } catch (IOException e) {
        System.out.println("SPNF: Error pinging address " + wHandle.getAddress() + ": " + e);
        //e.printStackTrace();
        return SocketNodeHandle.DEFAULT_PROXIMITY;
      }
    } else {
      return wHandle.proximity();
    }
  }

  /**
   * Method which contructs a node handle (using the socket protocol) for the
   * node at address NodeHandle.
   *
   * @param address The address of the remote node.
   * @return A NodeHandle cooresponding to that address
   */
  public NodeHandle generateNodeHandle(InetSocketAddress address) {
    // send nodeId request to remote node, wait for response
    // allocate enought bytes to read a node handle
    System.out.println("Socket: Contacting bootstrap node " + address);

    try {
      NodeIdResponseMessage rm = (NodeIdResponseMessage) getResponse(address, new NodeIdRequestMessage());
      
      return rm.getHandle();
    } catch (IOException e) {
      System.out.println("Error connecting to address " + address + ": " + e);
      System.out.println("Couldn't find a bootstrap node, starting a new ring...");
      return null;
    }
  }

  /**
   * Method which creates a Pastry node from the next port with a randomly
   * generated NodeId.
   *
   * @param bootstrap Node handle to bootstrap from.
   * @return A node with a random ID and next port number.
   */
  public PastryNode newNode(NodeHandle bootstrap) {
    return newNode(bootstrap, nidFactory.generateNodeId());
  }

  /**
   * Method which creates a Pastry node from the next port with a randomly
   * generated NodeId.
   *
   * @param bootstrap Node handle to bootstrap from.
   * @param nodeId DESCRIBE THE PARAMETER
   * @return A node with a random ID and next port number.
   */
  public PastryNode newNode(final NodeHandle bootstrap, NodeId nodeId) {
    return newNode(bootstrap, nodeId, null);
  }
  
  /**
   * Method which creates a Pastry node from the next port with a randomly
   * generated NodeId.
   *
   * @param bootstrap Node handle to bootstrap from.
   * @return A node with a random ID and next port number.
   */
  public PastryNode newNode(NodeHandle bootstrap, InetSocketAddress proxy) {
    return newNode(bootstrap, nidFactory.generateNodeId(), proxy);
  }
  
  /**
   * Ability to specify a proxy for this new node.
   */
  public PastryNode newNode(NodeHandle bootstrap, NodeId nodeId, InetSocketAddress proxy) {
    try {
      return newNode(bootstrap, nidFactory.generateNodeId(), proxy, InetAddress.getLocalHost());
    } catch (UnknownHostException uhe) {
      throw new RuntimeException(uhe);
    }
  }

  /**
   * Method which creates a Pastry node from the next port with a randomly
   * generated NodeId.
   *
   * @param bootstrap Node handle to bootstrap from.
   * @param nodeId DESCRIBE THE PARAMETER
   * @return A node with a random ID and next port number.
   */
  public PastryNode newNode(NodeHandle bootstrap, NodeId nodeId, InetSocketAddress proxyAddress, InetAddress bindAddress) {
    final SocketPastryNode pn = new SocketPastryNode(nodeId);    
    pn.getLocalNodeI(bootstrap); // register this nodehandle as early as we can
    //SelectorManager sManager = new SelectorManager(pn);

    SocketCollectionManager socketManager = null;
    InetSocketAddress address = proxyAddress;
    SocketNodeHandlePool pool = new SocketNodeHandlePool(pn);
    PingManager pingManager = null;
    SocketNodeHandle localhandle = null;

    synchronized (this) {
      
      boolean connected = false;
      while (!connected) {
        if (proxyAddress == null) {
          address = getAddress(port);
        }    
        localhandle = new SocketNodeHandle(address, nodeId);
        boolean pingSuccess = false;
        try {
          pingManager = new PingManager(port, pool, localhandle, pn);
          pingSuccess = true;
        } catch (BindException be) {
                    
        }
        if (pingSuccess) {
          try {
            socketManager = new SocketCollectionManager(pn, pool, port, pingManager, address);
            connected = true;
          } catch (BindException be) {
            pingManager.kill();
            port++;
          }
        }
      }
      port++;
    }

    SocketPastrySecurityManager secureMan = new SocketPastrySecurityManager(localhandle, pool);
    MessageDispatch msgDisp = new MessageDispatch();
    RoutingTable routeTable = new RoutingTable(localhandle, rtMax);
    LeafSet leafSet = new LeafSet(localhandle, lSetSize);

    StandardRouter router = new StandardRouter(localhandle, routeTable, leafSet, secureMan);
    StandardLeafSetProtocol lsProtocol;
    StandardJoinProtocol jProtocol;
    if (churn) {
      lsProtocol = new ChurnLeafSetProtocol(pn, localhandle, secureMan, leafSet, routeTable, leafSetMaintFreq);
      socketManager.addLivenessListener(lsProtocol);
      jProtocol = new ChurnJoinProtocol(pn, localhandle, secureMan, routeTable, leafSet, (ChurnLeafSetProtocol)lsProtocol);
      pingManager.addProbeListener((ChurnLeafSetProtocol)lsProtocol);
    } else {
      lsProtocol = new StandardLeafSetProtocol(pn, localhandle, secureMan, leafSet, routeTable);
      jProtocol = new StandardJoinProtocol(pn, localhandle, secureMan, routeTable, leafSet);      
    }

    StandardRouteSetProtocol rsProtocol = new StandardRouteSetProtocol(localhandle, secureMan, routeTable);

    msgDisp.registerReceiver(router.getAddress(), router);
    msgDisp.registerReceiver(lsProtocol.getAddress(), lsProtocol);
    msgDisp.registerReceiver(rsProtocol.getAddress(), rsProtocol);
    msgDisp.registerReceiver(jProtocol.getAddress(), jProtocol);

    pn.setElements(localhandle, secureMan, msgDisp, leafSet, routeTable);
    pn.setSocketElements(address, socketManager, pingManager, leafSetMaintFreq, routeSetMaintFreq);
    secureMan.setLocalPastryNode(pn);

    pool.coalesce(localhandle);
    localhandle.setLocalNode(pn);

    if (bootstrap != null) {
      bootstrap.setLocalNode(pn);
    }
    
    System.out.println("SPNF.getNearest():begin");
    if (useNearest)
      bootstrap = getNearest(localhandle, bootstrap);
    System.out.println("SPNF.getNearest():end");

    final NodeHandle theBootstrap = bootstrap;

    // launch thread to handle the sockets
    SelectorManager.getSelectorManager().invoke(
      new Runnable() {
        public void run() {
          System.out.println(pn+" SPNF.run():begin");
          pn.doneNode(theBootstrap);
          System.out.println(pn+" SPNF.run():end");
        }
      });

    return pn;
  }

  /**
   * This method anonymously sends the given message to the remote address,
   * blocks until a response is received, and then closes the socket and returns
   * the response.
   *
   * @param address The address to send to
   * @param message The message to send
   * @return The response
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  protected Message getResponse(InetSocketAddress address, Message message) throws IOException {
    // create reader and writer
    SocketChannelWriter writer = new SocketChannelWriter(null, null);
    SocketChannelReader reader = new SocketChannelReader(null, null);

    // bind to the appropriate port
    SocketChannel channel = SocketChannel.open();
    channel.configureBlocking(true);
    channel.socket().connect(address);

    writer.enqueue(message);
    writer.write(channel);
    Object o = null;

    while (o == null) {
      o = reader.read(channel);
    }

    channel.socket().close();
    channel.close();

//    System.out.println(o.getClass().getName());

    return (Message) o;
  }

  /**
   * Method which constructs an InetSocketAddres for the local host with the
   * specifed port number.
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

  /**
   * DESCRIBE THE METHOD
   *
   * @param s DESCRIBE THE PARAMETER
   */
  private void debug(String s) {
    if (Log.ifp(8)) {
      System.out.println(" (F): " + s);
    }
  }

}
