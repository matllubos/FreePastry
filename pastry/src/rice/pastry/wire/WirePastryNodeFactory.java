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

package rice.pastry.wire;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.SocketChannel;

import rice.pastry.Log;
import rice.pastry.NodeHandle;
import rice.pastry.NodeId;
import rice.pastry.NodeIdFactory;
import rice.pastry.PastryNode;
import rice.pastry.dist.DistCoalesedNodeHandle;
import rice.pastry.dist.DistPastryNodeFactory;
import rice.pastry.leafset.LeafSet;
import rice.pastry.messaging.MessageDispatch;
import rice.pastry.routing.RouteSet;
import rice.pastry.routing.RoutingTable;
import rice.pastry.standard.StandardJoinProtocol;
import rice.pastry.standard.StandardLeafSetProtocol;
import rice.pastry.standard.StandardRouteSetProtocol;
import rice.pastry.standard.StandardRouter;
import rice.pastry.wire.messaging.socket.LeafSetRequestMessage;
import rice.pastry.wire.messaging.socket.LeafSetResponseMessage;
import rice.pastry.wire.messaging.socket.NodeIdRequestMessage;
import rice.pastry.wire.messaging.socket.NodeIdResponseMessage;
import rice.pastry.wire.messaging.socket.RouteRowRequestMessage;
import rice.pastry.wire.messaging.socket.RouteRowResponseMessage;
import rice.pastry.wire.messaging.socket.SocketCommandMessage;

/**
 * Pastry node factory for Wire-linked nodes.
 *
 * @author Alan Mislove, Jeff Hoye
 */
public class WirePastryNodeFactory extends DistPastryNodeFactory {

  private NodeIdFactory nidFactory;

  private int port;

  private final static int rtMax = 1;
  private final static int lSetSize = 24;
  private final static int maxOpenSockets = 5;

  /**
   * Large period (in seconds) means infrequent, 0 means never.
   */
  private final static int leafSetMaintFreq = 1 * 60;
  private final static int routeSetMaintFreq = 15 * 60;

  /**
   * Constructor.
   *
   * @param nf DESCRIBE THE PARAMETER
   * @param startPort DESCRIBE THE PARAMETER
   */
  public WirePastryNodeFactory(NodeIdFactory nf, int startPort) {
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
    WireNodeHandle wHandle = (WireNodeHandle) handle;

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
    WireNodeHandle wHandle = (WireNodeHandle) handle;

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
    WireNodeHandle lHandle = (WireNodeHandle) local;
    WireNodeHandle wHandle = (WireNodeHandle) handle;

    // if this is a request for an old version of us, then we return
    // infinity as an answer
    if (lHandle.getAddress().equals(wHandle.getAddress())) {
      return Integer.MAX_VALUE;
    }

    if (wHandle.proximity() == DistCoalesedNodeHandle.DEFAULT_DISTANCE) {
      try {
        long startTime = System.currentTimeMillis();
        getResponse(wHandle.getAddress(), new NodeIdRequestMessage());
        long ping = System.currentTimeMillis() - startTime;

        return (int) ping;
      } catch (IOException e) {
        System.out.println("Error pinging address " + wHandle.getAddress() + ": " + e);
        return wHandle.DEFAULT_DISTANCE;
      }
    } else {
      return wHandle.proximity();
    }
  }

  /**
   * Method which contructs a node handle (using the wire protocol) for the node
   * at address NodeHandle.
   *
   * @param address The address of the remote node.
   * @return A NodeHandle cooresponding to that address
   */
  public NodeHandle generateNodeHandle(InetSocketAddress address) {
    // send nodeId request to remote node, wait for response
    // allocate enought bytes to read a node handle
    System.out.println("Wire: Contacting bootstrap node " + address);

    try {
      NodeIdResponseMessage rm = (NodeIdResponseMessage) getResponse(address, new NodeIdRequestMessage());

//      for (int i = 0; i< 10; i++) {
//        getResponse(address, new NodeIdRequestMessage());
//      }

      return new WireNodeHandle(address, rm.getNodeId());
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
   * Method which creates a Pastry node from the next port with a randomly
   * generated NodeId.
   *
   * @param bootstrap Node handle to bootstrap from.
   * @param nodeId DESCRIBE THE PARAMETER
   * @return A node with a random ID and next port number.
   */
  public PastryNode newNode(final NodeHandle bootstrap, NodeId nodeId, InetSocketAddress proxy) {
    final WirePastryNode pn = new WirePastryNode(nodeId);
    
    SelectorManager sManager = new SelectorManager(pn);

    DatagramManager dManager = null;
    SocketManager socketManager = null;
    InetSocketAddress address = null;

    synchronized (this) {
      dManager = new DatagramManager(pn, sManager, port); 

      // wakeup was moved to the NeedsWakeUp interface from SelectionKeyHandler
      // it must now be explicitly registered
      sManager.registerForWakeup(dManager);
      
//      socketManager = new GlobalSocketManager(pn, port, sManager.getSelector());
      socketManager = new SocketManager(pn, port, sManager.getSelector());

      address = getAddress(port);

      port++;
    }

    final WireNodeHandle localhandle = new WireNodeHandle(address, nodeId);

    WireNodeHandlePool pool = new WireNodeHandlePool(pn);

    WirePastrySecurityManager secureMan = new WirePastrySecurityManager(localhandle, pool);

    MessageDispatch msgDisp = new MessageDispatch();

    RoutingTable routeTable = new RoutingTable(localhandle, rtMax);
    LeafSet leafSet = new LeafSet(localhandle, lSetSize);

    StandardRouter router =
      new StandardRouter(localhandle, routeTable, leafSet, secureMan);
    StandardLeafSetProtocol lsProtocol =
      new StandardLeafSetProtocol(pn, localhandle, secureMan, leafSet, routeTable);
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

    if (bootstrap != null) {
      bootstrap.setLocalNode(pn);
    }

    // launch thread to handle the sockets
    Thread t =
      new Thread("Thread for node " + nodeId) {
        public void run() {
          try {
            sleep(250);
          } catch (InterruptedException e) {
            System.err.println("Interrupted in newNode!");
          }

          pn.doneNode(getNearest(localhandle, bootstrap));
          //pn.doneNode(bootstrap);
        }
      };

    pn.setThread(t);

    t.start();

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
  protected SocketCommandMessage getResponse(
    InetSocketAddress address,
    SocketCommandMessage message)
    throws IOException {

    SocketChannel channel = null;
    Object o = null;
    IOException ex = null;


    try {

      // create reader and writer
      SocketChannelWriter writer =
        new SocketChannelWriter(null, null, null);
      SocketChannelReader reader = new SocketChannelReader(null,null);

      Wire.acquireFileDescriptor();

      // bind to the appropriate port
      channel = SocketChannel.open();
      channel.configureBlocking(true);
      channel.socket().connect(address);

      writer.enqueue(message);
      writer.write(channel);

      while (o == null) {
        o = reader.read(channel);
      }

    } catch (IOException e) {
      //e.printStackTrace();
      ex = e;

    } finally {

      Wire.registerSocketChannel(channel, "getResponse("+message+")");


      if (ex == null) {
      } else {
        debug("not registering bogus channel:"+ex);
      }

      channel.close();
//      channel.socket().close();

      Wire.releaseFileDescriptor();

      if (ex != null)
        throw ex;

      return (SocketCommandMessage) o;
    }
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
    * Method which can be used to test the connectivity contstrains of the local node.
   * This (optional) method is designed to be called by applications to ensure
   * that the local node is able to connect through the network - checks can
   * be done to check TCP/UDP connectivity, firewall setup, etc...
   *
   * If the method works, then nothing should be done and the method should return.  If
   * an error condition is detected, an exception should be thrown.
   */
  public void verifyConnection(InetSocketAddress local, InetSocketAddress proxy, InetSocketAddress existing) throws IOException {
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
