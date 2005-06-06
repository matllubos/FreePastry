package rice.pastry.socket;

import java.io.IOException;
import java.net.*;
import java.nio.channels.SocketChannel;
import java.util.Enumeration;

import rice.environment.Environment;
import rice.environment.params.simple.SimpleParameters;
import rice.environment.random.RandomSource;
import rice.pastry.*;
import rice.pastry.dist.DistPastryNodeFactory;
import rice.pastry.leafset.LeafSet;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.pastry.socket.messaging.*;
import rice.pastry.standard.*;

/**
 * Pastry node factory for Socket-linked nodes.
 *
 * @version $Id: SocketPastryNodeFactory.java,v 1.6 2004/03/08 19:53:57 amislove
 *      Exp $
 * @author Alan Mislove
 */
public class SocketPastryNodeFactory extends DistPastryNodeFactory {
  
  private Environment environment;

  private NodeIdFactory nidFactory;

  private int port;

  // max number of handles stored per routing table entry
  private int rtMax;

  // leafset size
  private int lSetSize;
  
  /**
   * Large period (in seconds) means infrequent, 0 means never.
   */
  private int leafSetMaintFreq;
  private int routeSetMaintFreq;
  
  private RandomSource random;

  /**
   * Convienience constructor.  Uses a default environment.
   *
   * @param nf The factory for building node ids
   * @param startPort The port to start creating nodes on
   */
  public SocketPastryNodeFactory(NodeIdFactory nf, int startPort) throws IOException {
    this(nf, startPort, new Environment());
  }
  
  /**
   * Constructor.
   *
   * @param nf The factory for building node ids
   * @param startPort The port to start creating nodes on
	 * @param env The environment.
   */
  public SocketPastryNodeFactory(NodeIdFactory nf, int startPort, Environment env) {
    environment = env;
    nidFactory = nf;
    port = startPort;
    SimpleParameters sp = (SimpleParameters)env.getParameters();
    rtMax = env.getParameters().getInt("pastry_rtMax");
    lSetSize = env.getParameters().getInt("pastry_lSetSize");
    leafSetMaintFreq = env.getParameters().getInt("pastry_leafSetMaintFreq");
    routeSetMaintFreq = env.getParameters().getInt("pastry_routeSetMaintFreq");
    this.random = env.getRandomSource();
  }
  
  /**
   * This method returns the routes a remote node is using
   *
   * @param handle The node to connect to
   * @return The leafset of the remote node
   */
  public SourceRoute[] getRoutes(NodeHandle handle) throws IOException {
    SocketNodeHandle wHandle = (SocketNodeHandle) handle;
    
    RoutesResponseMessage lm = (RoutesResponseMessage) getResponse(wHandle.getAddress(), new RoutesRequestMessage());
    
    return lm.getRoutes();
  }

  /**
   * This method returns the remote leafset of the provided handle to the
   * caller, in a protocol-dependent fashion. Note that this method may block
   * while sending the message across the wire.
   *
   * @param handle The node to connect to
   * @return The leafset of the remote node
   */
  public LeafSet getLeafSet(NodeHandle handle) throws IOException {
    SocketNodeHandle wHandle = (SocketNodeHandle) handle;

    LeafSetResponseMessage lm = (LeafSetResponseMessage) getResponse(wHandle.getAddress(), new LeafSetRequestMessage());

    return lm.getLeafSet();
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
  public RouteSet[] getRouteRow(NodeHandle handle, int row) throws IOException {
    SocketNodeHandle wHandle = (SocketNodeHandle) handle;
    
    RouteRowResponseMessage rm = (RouteRowResponseMessage) getResponse(wHandle.getAddress(), new RouteRowRequestMessage(row));
    
    return rm.getRouteRow();
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
    EpochInetSocketAddress lAddress = ((SocketNodeHandle) local).getEpochAddress();
    EpochInetSocketAddress rAddress = ((SocketNodeHandle) handle).getEpochAddress();

    lAddress = new EpochInetSocketAddress(new InetSocketAddress(lAddress.getAddress().getAddress(), lAddress.getAddress().getPort()+1));
    
    // if this is a request for an old version of us, then we return
    // infinity as an answer
    if (lAddress.getAddress().equals(rAddress.getAddress())) {
      return Integer.MAX_VALUE;
    }
    
    DatagramSocket socket = null;
    SourceRoute route = SourceRoute.build(new EpochInetSocketAddress[] {rAddress});

    try {
      socket = new DatagramSocket(lAddress.getAddress().getPort());
      socket.setSoTimeout(5000);

      byte[] data = PingManager.addHeader(route, new PingMessage(route, route.reverse(lAddress), environment), lAddress);
      
      socket.send(new DatagramPacket(data, data.length, rAddress.getAddress()));
      
      long start = environment.getTimeSource().currentTimeMillis();
      socket.receive(new DatagramPacket(new byte[10000], 10000));
      return (int) (environment.getTimeSource().currentTimeMillis() - start);
    } catch (IOException e) {
      return Integer.MAX_VALUE-1;
    } finally {
      if (socket != null)
        socket.close();
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
    if (SocketPastryNode.verbose) System.out.println("Socket: Contacting bootstrap node " + address);

    try {
      NodeIdResponseMessage rm = (NodeIdResponseMessage) getResponse(address, new NodeIdRequestMessage());
      
      return new SocketNodeHandle(new EpochInetSocketAddress(address, rm.getEpoch()), rm.getNodeId());
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
//    if (bootstrap == null) {
//      return newNode(bootstrap, NodeId.buildNodeId());
//    } 
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
   * @param address The address to claim that this node is at - used for proxies behind NATs
   * @return A node with a random ID and next port number.
   */
  public PastryNode newNode(final NodeHandle bootstrap, NodeId nodeId, InetSocketAddress pAddress) {
    final SocketPastryNode pn = new SocketPastryNode(nodeId, environment);

    SocketSourceRouteManager srManager = null;
    SocketNodeHandlePool pool = new SocketNodeHandlePool(pn);
    EpochInetSocketAddress localAddress = null;
    EpochInetSocketAddress proxyAddress = null;
    long epoch = random.nextLong();

    synchronized (this) {
      localAddress = getEpochAddress(port, epoch);
      
      if (pAddress == null)
        proxyAddress = localAddress;
      else
        proxyAddress = new EpochInetSocketAddress(pAddress, epoch);
      
      srManager = new SocketSourceRouteManager(pn, pool, localAddress, proxyAddress);
      port++;
    }

    final SocketNodeHandle localhandle = new SocketNodeHandle(proxyAddress, nodeId);
    SocketPastrySecurityManager secureMan = new SocketPastrySecurityManager(localhandle, pool);
    MessageDispatch msgDisp = new MessageDispatch(pn);
    RoutingTable routeTable = new RoutingTable(localhandle, rtMax);
    LeafSet leafSet = new LeafSet(localhandle, lSetSize);

    StandardRouter router = new StandardRouter(localhandle, routeTable, leafSet, secureMan);
    PeriodicLeafSetProtocol lsProtocol = new PeriodicLeafSetProtocol(pn, localhandle, secureMan, leafSet, routeTable);
    StandardRouteSetProtocol rsProtocol = new StandardRouteSetProtocol(localhandle, secureMan, routeTable, environment);
//    StandardJoinProtocol jProtocol = new StandardJoinProtocol(pn, localhandle, secureMan, routeTable, leafSet);
    ConsistentJoinProtocol jProtocol = new ConsistentJoinProtocol(pn, localhandle, secureMan, routeTable, leafSet);

    msgDisp.registerReceiver(router.getAddress(), router);
    msgDisp.registerReceiver(lsProtocol.getAddress(), lsProtocol);
    msgDisp.registerReceiver(rsProtocol.getAddress(), rsProtocol);
    msgDisp.registerReceiver(jProtocol.getAddress(), jProtocol);

    pn.setElements(localhandle, secureMan, msgDisp, leafSet, routeTable);
    pn.setSocketElements(proxyAddress, srManager, pool, leafSetMaintFreq, routeSetMaintFreq);
    secureMan.setLocalPastryNode(pn);

    pool.coalesce(localhandle);
    localhandle.setLocalNode(pn);

    if (bootstrap != null) 
      bootstrap.setLocalNode(pn);
    
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {}
    
    pn.doneNode(getNearest(localhandle, bootstrap));
 //   pn.doneNode(bootstrap);

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
    SocketChannelWriter writer = new SocketChannelWriter(environment, SourceRoute.build(new EpochInetSocketAddress(address, 0)));
    SocketChannelReader reader = new SocketChannelReader(null, SourceRoute.build(new EpochInetSocketAddress(address, 0)));

    // bind to the appropriate port
    SocketChannel channel = SocketChannel.open();
    channel.configureBlocking(true);
    channel.socket().connect(address, 20000);
    channel.socket().setSoTimeout(20000);

    writer.enqueue(SocketCollectionManager.HEADER_DIRECT);
    writer.enqueue(message);
    writer.write(channel);
    Object o = null;

    while (o == null) {
      o = reader.read(channel);
    }

    channel.socket().close();
    channel.close();

    return (Message) o;
  }

  /**
   * Method which constructs an InetSocketAddres for the local host with the
   * specifed port number.
   *
   * @param portNumber The port number to create the address at.
   * @return An InetSocketAddress at the localhost with port portNumber.
   */
  private EpochInetSocketAddress getEpochAddress(int portNumber, long epoch) {
    EpochInetSocketAddress result = null;

    try {
      result = new EpochInetSocketAddress(new InetSocketAddress(InetAddress.getLocalHost(), portNumber), epoch);
      ServerSocket test = new ServerSocket();
      
      try {
        test.bind(result.getAddress());
      } catch (SocketException e) {
        Socket temp = new Socket("yahoo.com", 80);
        result = new EpochInetSocketAddress(new InetSocketAddress(temp.getLocalAddress(), portNumber), epoch);
        temp.close();
        
        System.out.println("Error binding to original IP, using " + result);
      }
      
      test.close();
      return result;
    } catch (UnknownHostException e) {
      System.out.println("PANIC: Unknown host in getAddress. " + e);
    } catch (IOException e) {
      System.out.println("PANIC: IOException in getAddress. " + e);
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
  public static InetSocketAddress verifyConnection(int timeout, InetSocketAddress local, InetSocketAddress[] existing, Environment env) throws IOException {
    System.err.println("Verifying connection of local node " + local + " using " + existing[0] + " and " + existing.length + " more");
    DatagramSocket socket = null;
    
    try {
      socket = new DatagramSocket(local);
      socket.setSoTimeout(timeout);
      
      for (int i=0; i<existing.length; i++) {
        byte[] buf = PingManager.addHeader(SourceRoute.build(new EpochInetSocketAddress(existing[i])), new IPAddressRequestMessage(env), new EpochInetSocketAddress(local));    
        DatagramPacket send = new DatagramPacket(buf, buf.length, existing[i]);
        socket.send(send);
      }
      
      DatagramPacket receive = new DatagramPacket(new byte[10000], 10000);
      socket.receive(receive);
      
      byte[] data = new byte[receive.getLength() - 38];
      System.arraycopy(receive.getData(), 38, data, 0, data.length);
      
      return ((IPAddressResponseMessage) PingManager.deserialize(data)).getAddress();
    } finally {
      if (socket != null)
        socket.close();
    }
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
