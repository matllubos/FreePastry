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
package rice.pastry.socket;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.pastry.socket.messaging.*;
import rice.selector.*;

/**
 * Class which maintains all outgoing open sockets. It is responsible for
 * keeping only MAX_OPEN_SOCKETS number of client sockets open at once. It also
 * binds a ServerSocketChannel to the specified port and listens for incoming
 * connections. Once a connections is established, it uses the interal
 * SocketConnector to read the greeting message (HelloMessage) off of the
 * stream, and hands the connection off to the appropriate node handle.
 *
 * @version $Id: SocketCollectionManager.java,v 1.3 2004/03/08 19:53:57 amislove
 *      Exp $
 * @author Alan Mislove
 */
public class SocketCollectionManager extends SelectionKeyHandler {
  
  // the number of sockets where we start closing other sockets
  public static int MAX_OPEN_SOCKETS = 40;
  
  // the number of source routes through this node (note, each has 2 sockets)
  public static int MAX_OPEN_SOURCE_ROUTES = 20;
  
  // the size of the buffers for the socket
  public static int SOCKET_BUFFER_SIZE = 32768;
  
  // how long to wait for a ping response to come back before declaring lost
  public static int PING_DELAY = 2500;
  
  // how much jitter to add to the ping waits - we may wait up to this time before giving up
  public static int PING_JITTER = 1000;
  
  // how many tries to ping before giving up
  public static int NUM_PING_TRIES = 3;
  
  // the fake port for booting
  public static int BOOTSTRAP_PORT = 1;
  
  // the header which signifies a normal socket
  protected static byte[] HEADER_DIRECT = new byte[] {0x06, 0x1B, 0x49, 0x74};
  
  // the header which signifies a normal socket
  protected static byte[] HEADER_SOURCE_ROUTE = new byte[] {0x19, 0x53, 0x13, 0x00};
  
  // the length of the socket header
  public static int HEADER_SIZE = HEADER_DIRECT.length;
  
  // the pastry node which this manager serves
  private PastryNode pastryNode;

  // the local address of the node
  private EpochInetSocketAddress localAddress;

  // the linked list of open sockets
  private LinkedList socketQueue;

  // maps a SelectionKey -> SocketConnector
  private Hashtable sockets;
  
  // the linked list of open source routes
  private LinkedList sourceRouteQueue;

  // ServerSocketChannel for accepting incoming connections
  private SelectionKey key;

  // the ping manager for doing udp stuff
  private PingManager pingManager;
  
  // the source route manager, which keeps track of routes
  private SocketSourceRouteManager manager;
  
  // whether or not we've resigned
  private boolean resigned;
  
  // the RNG
  private Random random;

  /**
   * Constructs a new SocketManager.
   *
   * @param node The pastry node this manager is serving
   * @param port The port number which this manager is listening on
   * @param pool DESCRIBE THE PARAMETER
   * @param address The address to claim the node is at (for proxying)
   */
  public SocketCollectionManager(PastryNode node, SocketNodeHandlePool pool, SocketSourceRouteManager manager, EpochInetSocketAddress bindAddress, EpochInetSocketAddress proxyAddress) {
    this.pastryNode = node;
    this.manager = manager;
    this.localAddress = proxyAddress;
    this.pingManager = new PingManager(pool, manager, node, bindAddress, proxyAddress);
    this.socketQueue = new LinkedList();
    this.sockets = new Hashtable();
    this.sourceRouteQueue = new LinkedList();
    this.resigned = false;
    this.random = new Random();
    
    if (SocketPastryNode.verbose) System.out.println("BINDING TO ADDRESS " + bindAddress + " AND CLAIMING " + localAddress);
    
    try {
      // bind to port
      final ServerSocketChannel channel = ServerSocketChannel.open();
      channel.configureBlocking(false);
      channel.socket().bind(bindAddress.getAddress());
      
      final SelectionKeyHandler handler = this;

      SelectorManager.getSelectorManager().invoke(
        new Runnable() {
          public void run() {
            try {
              key = SelectorManager.getSelectorManager().register(channel, handler, SelectionKey.OP_ACCEPT);
            } catch (IOException e) {
              System.out.println("ERROR creating server socket key " + e);
            }
          }
        });
    } catch (IOException e) {
      System.out.println("ERROR creating server socket channel " + e);
      e.printStackTrace();
    }
  }  
  
  /**
   * Method which suggests a ping to the remote node.
   *
   * @param route The route to use
   */
  public void ping(SourceRoute route) {
    pingManager.ping(route, null);
  }
  
  /**
   * Method which checks the liveness of the given path: if a socket is already
   * open, true is immediately returned.  Otherwise, checkDead() is called on
   * the path.
   *
   * @param route The route to use
   */
  public void checkLiveness(SourceRoute route) {
    if (! sockets.containsKey(route))
      checkDead(route);
  }
  
  /**
   * Method which returns the last cached proximity value for the given address.
   * If there is no cached value, then DEFAULT_PROXIMITY is returned.
   *
   * @param address The address to return the value for
   * @return The ping value to the remote address
   */
  public int proximity(SourceRoute path) {
    return pingManager.proximity(path);
  }

  /**
   * Method which sends a message across the wire.
   *
   * @param message The message to send
   * @param address The address to send the message to
   */
  public void send(SourceRoute path, Message message) {
    synchronized (sockets) {
      if (!sockets.containsKey(path)) {
        debug("No connection open to path " + path + " - opening one");
        openSocket(path, false);
      }
      
      if (sockets.containsKey(path)) {
        debug("Found connection open to path " + path + " - sending now");

        ((SocketManager) sockets.get(path)).send(message);
        socketUpdated(path);
      } else {
        debug("ERROR: Could not connection to remote address " + path + " rerouting message " + message);
        manager.reroute(path.getLastHop(), message);
      }
    }
  }
  
  /**
   * Method which sends bootstraps a node by sending message across the wire,
   * using a fake IP address in the header so that the local node is not marked
   * alive, and then closes the connection.
   *
   * @param message The message to send
   * @param address The address to send the message to
   */
  public void bootstrap(SourceRoute path, Message message) {
    synchronized (sockets) {
      openSocket(path, true);    
      ((SocketManager) sockets.get(path)).send(message);
    }
  }
  
  /**
   * Method which is called by the ping manager to indicate that the address has expired
   * and should be marked dead without question - this indicates a new epoch.
   *
   * @param address The address of the remote node
   */
  protected void markDead(EpochInetSocketAddress address) {
    manager.markDead(address); 
  }

  /**
   * Specified by the SelectionKeyHandler interface. Is called whenever a key
   * has become acceptable, representing an incoming connection. This method
   * will accept the connection, and attach a SocketConnector in order to read
   * the greeting off of the channel. Once the greeting has been read, the
   * connector will hand the channel off to the appropriate node handle.
   *
   * @param key The key which is acceptable.
   */
  public void accept(SelectionKey key) {
    try {
      new SocketAccepter(key);
    } catch (IOException e) {
      System.out.println("ERROR (accepting connection): " + e);
    }
  }

  /**
   * Initiates a liveness test on the given address, if the remote node does not
   * respond, it is declared dead.
   *
   * @param address DESCRIBE THE PARAMETER
   */
  protected void checkDead(SourceRoute path) {    
    if (! resigned) {
      if (SocketPastryNode.verbose) System.out.println("CHECK DEAD: " + localAddress + " CHECKING DEATH OF PATH " + path);
      DeadChecker checker = new DeadChecker(path, NUM_PING_TRIES);
      ((SocketPastryNode) pastryNode).getTimer().scheduleAtFixedRate(checker, PING_DELAY + random.nextInt(PING_JITTER), PING_DELAY + random.nextInt(PING_JITTER));
      pingManager.forcePing(path, checker);
    }
  }

  /**
   * Method which opens a socket to a given remote node handle, and updates the
   * bookkeeping to keep track of this socket
   *
   * @param address The address of the remote node
   */
  protected void openSocket(SourceRoute path, boolean bootstrap) {
    try {
      synchronized (sockets) {
        if (!sockets.containsKey(path)) {
          socketOpened(path, new SocketManager(path, bootstrap));
        } else {
          debug("SERIOUS ERROR: Request to open socket to already-open socket to path " + path);
        }
      }
    } catch (IOException e) {
      System.out.println("GOT ERROR " + e + " OPENING PATH - MARKING PATH " + path + " AS DEAD!");
      e.printStackTrace();
      closeSocket(path);
      manager.markDead(path);
    }
  }
  
  /**
   * Method which cloeses a socket to a given remote node handle, and updates
   * the bookkeeping to keep track of this closing.  Note that this method does
   * not completely close the socket, rather,  it simply calls shutdown(), which
   * starts the shutdown process.
   *
   * @param address The address of the remote node
   */
  protected void closeSocket(SourceRoute path) {
    synchronized (sockets) {
      if (sockets.containsKey(path)) {
        ((SocketManager) sockets.get(path)).shutdown();
      } else {
        debug("SERIOUS ERROR: Request to close socket to non-open handle to path " + path);
      }
    }
  }

  /**
   * Method which is designed to be called by node handles when they wish to
   * open a socket to their remote node. This method will determine if another
   * node handle needs to disconnect, and will disconnect the ejected node
   * handle if necessary.
   *
   * @param address The address of the remote node
   * @param manager The manager for the remote address
   */
  protected void socketOpened(SourceRoute path, SocketManager manager) {
    synchronized (sockets) {
      if (! sockets.containsKey(path)) {
        sockets.put(path, manager);
        socketQueue.addFirst(path);

        debug("Recorded opening of socket to path " + path);

        if (sockets.size() > MAX_OPEN_SOCKETS) {
          SourceRoute toClose = (SourceRoute) socketQueue.removeLast();
          debug("Too many sockets open - closing socket to path " + toClose);
          closeSocket(toClose);
        }
      } else {
        debug("ERROR: Request to record path opening for already-open path " + path);
        String local = "" + localAddress.getAddress().getAddress().getHostAddress() + localAddress.getAddress().getPort();
        String remote = "" + path.getLastHop().getAddress().getAddress().getHostAddress() + path.getLastHop().getAddress().getPort();

        debug("RESOLVE: Comparing paths " + local + " and " + remote);

        if (remote.compareTo(local) < 0) {
          debug("RESOLVE: Cancelling existing connection to " + path);
          SocketManager toClose = (SocketManager) sockets.get(path);

          socketClosed(path, toClose);
          socketOpened(path, manager);
          toClose.close();
        } else {
          debug("RESOLVE: Implicitly cancelling new connection to path " + path);
        } 
      }
    }
  }

  /**
   * Method which is designed to be called *ONCE THE SOCKET HAS BEEN CLOSED*.
   * This method simply updates the bookeeping, but does not actually close the
   * socket.
   *
   * @param address The address of the remote node
   * @param manager The manager for the remote address
   */
  protected void socketClosed(SourceRoute path, SocketManager manager) {
    synchronized (sockets) {
      if (sockets.containsKey(path)) {
        if (sockets.get(path) == manager) {
          debug("Recorded closing of socket to " + path);

          socketQueue.remove(path);
          sockets.remove(path);
        } else {
          debug("SocketClosed called with corrent address, but incorrect manager - not a big deal.");
        }
      } else {
        debug("SEROUS ERROR: Request to record socket closing for non-existant socket to path " + path);
      }
    }
  }
  
  /**
   * Method which is designed to be called whenever a node has network activity.
   * This is used to determine which nodes should be disconnected, should it be
   * necessary (implementation of a LRU stack).
   *
   * @param address The address of the remote node
   */
  protected void socketUpdated(SourceRoute path) {
    synchronized (sockets) {
      if (sockets.containsKey(path)) {
        socketQueue.remove(path);
        socketQueue.addFirst(path);
      } else {
        debug("SERIOUS ERROR: Request to record update for non-existant socket to " + path);
      }
    }
  }
  
  /**
   * Method which is designed to be called when a new source route manager is
   * created.  This method will close another source route, if there are too
   * many source routes already open through this node.
   *
   * @param address The address of the remote node
   * @param manager The manager for the remote address
   */
  protected void sourceRouteOpened(SourceRouteManager manager) {
    if (! sourceRouteQueue.contains(manager)) {
      sourceRouteQueue.addFirst(manager);
      
      debug("Recorded opening of source route manager " + manager);
      
      if (sourceRouteQueue.size() > MAX_OPEN_SOURCE_ROUTES) {
        SourceRouteManager toClose = (SourceRouteManager) sourceRouteQueue.removeLast();
        debug("Too many source routes open - closing source route manager " + toClose);

        toClose.close();
        sourceRouteClosed(toClose);
      }
    } else {
      debug("ERROR: Request to record source route opening for already-open manager " + manager);
      sourceRouteUpdated(manager);
    }
  }
  
  /**
   * Method which is designed to be called *ONCE THE SOURCE ROUTE MANAGER HAS BEEN CLOSED*.
   * This method simply updates the bookeeping, but does not actually close the
   * source route.
   *
   * @param address The address of the remote node
   * @param manager The manager for the remote address
   */
  protected void sourceRouteClosed(SourceRouteManager manager) {
    if (sourceRouteQueue.contains(manager)) {
      sourceRouteQueue.remove(manager);
      
      debug("Recorded closing of source route manager " + manager);      
    } else {
      debug("ERROR: Request to record source route closing for unknown manager " + manager);
    }
  }

  /**
   * Method which is designed to be called whenever a source route has network activity.
   * This is used to determine which source routes should be disconnected, should it be
   * necessary (implementation of a LRU stack).
   *
   * @param manager The manager with activity
   */
  protected void sourceRouteUpdated(SourceRouteManager manager) {
    if (sourceRouteQueue.contains(manager)) {
      sourceRouteQueue.remove(manager);
      sourceRouteQueue.addFirst(manager);
    } else {
      debug("SERIOUS ERROR: Request to record update for unknown source route " + manager);
    }
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param s DESCRIBE THE PARAMETER
   */
  private void debug(String s) {
    if (Log.ifp(8)) {
      System.out.println(pastryNode.getNodeId() + " (SCM): " + s);
    }
  }
  
  /**
   * Makes this node resign from the network.  Is designed to be used for
   * debugging and testing.
   */
  public void resign() throws IOException {
    resigned = true;
    
    pingManager.resign();
    
    while (socketQueue.size() > 0) 
      ((SocketManager) sockets.get(socketQueue.getFirst())).close();
    
    while (sourceRouteQueue.size() > 0) 
      ((SourceRouteManager) sourceRouteQueue.getFirst()).close();
    
    key.channel().close();
    key.cancel();    
  }
  
  public int getNumSourceRoutes() {
    return sourceRouteQueue.size();
  }
  
  public int getNumSockets() {
    return socketQueue.size();
  }
  
  /**
   * Method which returns the internal PingManager
   *
   * @param route The route to use
   * @param prl The listener
   */
  public PingManager getPingManager() {
    return pingManager;
  }

  /**
   * DESCRIBE THE CLASS
   *
   * @version $Id$
   * @author jeffh
   */
  protected class DeadChecker extends rice.selector.TimerTask implements PingResponseListener {

    // The number of tries that have occurred so far
    protected int tries = 1;
    
    // the total number of tries before declaring death
    protected int numTries;
    
    // the path to check
    protected SourceRoute path;

    /**
     * Constructor for DeadChecker.
     *
     * @param address DESCRIBE THE PARAMETER
     * @param numTries DESCRIBE THE PARAMETER
     * @param mgr DESCRIBE THE PARAMETER
     */
    public DeadChecker(SourceRoute path, int numTries) {
      if (SocketPastryNode.verbose) System.out.println("DeadChecker(" + path + ") started.");

      this.path = path;
      this.numTries = numTries;
    }

    /**
     * DESCRIBE THE METHOD
     *
     * @param address DESCRIBE THE PARAMETER
     * @param RTT DESCRIBE THE PARAMETER
     * @param timeHeardFrom DESCRIBE THE PARAMETER
     */
    public void pingResponse(SourceRoute path, long RTT, long timeHeardFrom) {
      if (SocketPastryNode.verbose) System.out.println("Terminated DeadChecker(" + path + ") due to ping.");
      manager.markAlive(path);
      cancel();
    }

    /**
     * Main processing method for the DeadChecker object
     */
    public void run() {
      if (tries < numTries) {
        tries++;
        if (manager.getLiveness(path.getLastHop()) == SocketNodeHandle.LIVENESS_ALIVE)
          manager.markSuspected(path);
        
        pingManager.forcePing(path, this);
      } else {
        System.out.println("DeadChecker(" + path + ") expired - marking as dead.");
        manager.markDead(path);
        cancel();
      }
    }
  }
    
  /**
   * Private class which is tasked with reading the greeting message off of a
   * newly connected socket. This greeting message says who the socket is coming
   * from, and allows the connected to hand the socket off the appropriate node
   * handle.
   *
   * @version $Id$
   * @author jeffh
   */
  private class SocketManager extends SelectionKeyHandler {

    // the key to read from
    private SelectionKey key;

    // the reader reading data off of the stream
    private SocketChannelReader reader;

    // the writer (in case it is necessary)
    private SocketChannelWriter writer;

    // the node handle we're talking to
    private SourceRoute path;
    
    // whether or not this is a bootstrap socket - if so, we fake the address 
    // and die once the the message has been sent
    private boolean bootstrap;

    /**
     * Constructor which accepts an incoming connection, represented by the
     * selection key. This constructor builds a new SocketManager, and waits
     * until the greeting message is read from the other end. Once the greeting
     * is received, the manager makes sure that a socket for this handle is not
     * already open, and then proceeds as normal.
     *
     * @param key The server accepting key for the channel
     * @exception IOException DESCRIBE THE EXCEPTION
     */
    public SocketManager(SelectionKey key) throws IOException {
      this.reader = new SocketChannelReader(pastryNode, null);
      this.writer = new SocketChannelWriter(pastryNode, null);
      this.bootstrap = false;
      acceptConnection(key);
    }
    
    /**
     * Constructor which creates an outgoing connection to the given node
     * handle using the provided address as a source route intermediate node. 
     * This creates the connection by building the socket and sending
     * accross the greeting message. Once the response greeting message is
     * received, everything proceeds as normal.
     *
     * @param address The ultimate destination of this socket
     * @param proxy The intermediate destination of this socket (if a source route)
     * @exception IOException An error
     */
    public SocketManager(SourceRoute path, boolean bootstrap) throws IOException {
      this.reader = new SocketChannelReader(pastryNode, path.reverse());
      this.writer = new SocketChannelWriter(pastryNode, path);
      this.bootstrap = bootstrap;
      
      if (SocketPastryNode.verbose) System.out.println("Opening connection with path " + path);
      
      // build the entire connection
      createConnection(path);
      
      for (int i=1; i<path.getNumHops(); i++) {
        send(HEADER_SOURCE_ROUTE);
        send(SocketChannelRepeater.encodeHeader(path.getHop(i)));
      }
      
      send(HEADER_DIRECT);
      
      if (! bootstrap)
        send(path.reverse(localAddress));
    }
    
    /**
     * Method which initiates a shutdown of this socket by calling 
     * shutdownOutput().  This has the effect of removing the manager from
     * the open list.
     */
    public void shutdown() {
      try {
        System.out.println("SHUTDOWN OUT: " + localAddress + " Shutting down output to path " + path);
        ((SocketChannel) key.channel()).socket().shutdownOutput();
        socketClosed(path, this);
        SelectorManager.getSelectorManager().modifyKey(key);
      } catch (IOException e) {
        System.err.println("ERROR: Received exception " + e + " while shutting down output.");
        close();
      }
    }

    /**
     * Method which closes down this socket manager, by closing the socket,
     * cancelling the key and setting the key to be interested in nothing
     */
    public void close() {
      try {
        if (key != null) {
          key.channel().close();
          key.cancel();
          key.attach(null);
          key = null;
        }

        if (path != null) {
          socketClosed(path, this);

          Iterator i = writer.getQueue().iterator();
          writer.reset();

          /**
           * Here, if we have not been declared dead, then we attempt to resend
           * the messages. However, if we have been declared dead, we reroute
           * the route messages via the pastry node, but delete any messages
           * routed directly.
           */
          while (i.hasNext()) {
            Object o = i.next();

            if ((o instanceof Message) && (manager != null)) 
              manager.reroute(path.getLastHop(), (Message) o);
          } 

          path = null;
        }
      } catch (IOException e) {
        System.out.println("ERROR: Recevied exception " + e + " while closing socket!");
      }
    }

    /**
     * The entry point for outgoing messages - messages from here are ensocketQueued
     * for transport to the remote node
     *
     * @param message DESCRIBE THE PARAMETER
     */
    public void send(final Object message) {
      writer.enqueue(message);

      if (key != null) 
        SelectorManager.getSelectorManager().modifyKey(key);
    }

    /**
     * Method which should change the interestOps of the handler's key. This
     * method should *ONLY* be called by the selection thread in the context of
     * a select().
     *
     * @param key The key in question
     */
    public synchronized void modifyKey(SelectionKey key) {
      if (((SocketChannel) key.channel()).socket().isOutputShutdown()) 
        key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
      else if ((! writer.isEmpty()) && ((key.interestOps() & SelectionKey.OP_WRITE) == 0))
        key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
    }

    /**
     * Specified by the SelectionKeyHandler interface - calling this tells this
     * socket manager that the connection has completed and we can now
     * read/write.
     *
     * @param key The key which is connectable.
     */
    public void connect(SelectionKey key) {
      try {
        // deregister interest in connecting to this socket
        if (((SocketChannel) key.channel()).finishConnect()) 
          key.interestOps(key.interestOps() & ~SelectionKey.OP_CONNECT);

        manager.markAlive(path);

        debug("Found connectable channel - completed connection");
      } catch (Exception e) {
        debug("Got exception " + e + " on connect - marking as dead");
        System.out.println("Unable to connect to path " + path + " (" + e + ") marking as dead.");
        e.printStackTrace();
        manager.markDead(path);

        close();
      }
    }

    /**
     * Reads from the socket attached to this connector.
     *
     * @param key The selection key for this manager
     */
    public void read(SelectionKey key) {
      try {
        Object o = reader.read((SocketChannel) key.channel());

        if (o != null) {
          debug("Read message " + o + " from socket.");
          if (o instanceof SourceRoute) {
            if (this.path == null) {
              this.path = (SourceRoute) o;
              socketOpened(this.path, this);
              manager.markAlive(this.path);
              this.writer.setPath(this.path);
              this.reader.setPath(this.path.reverse());

              if (SocketPastryNode.verbose) System.out.println("Read open connection with path " + this.path);              
            } else {
              System.out.println("SERIOUS ERROR: Received duplicate path assignments: " + this.path + " and " + o);
            }
          } else {
            receive((Message) o);
          }
        }
      } catch (IOException e) {
        debug("ERROR " + e + " reading - cancelling.");
        System.out.println("SHUTDOWN OUT: " + localAddress + " Read closing of path " + path + " " + ((SocketChannel) key.channel()).socket().isOutputShutdown());
        
        // if it's not a bootstrap path, and we didn't close this socket's output,
        // then check to see if the remote address is dead or just closing a socket
        if ((path != null) && (path.getFirstHop().getAddress().getPort() != BOOTSTRAP_PORT) && 
            (! ((SocketChannel) key.channel()).socket().isOutputShutdown()))
          checkDead(path);
        
        close();
      }
    }


    /**
     * Writes to the socket attached to this socket manager.
     *
     * @param key The selection key for this manager
     */
    public synchronized void write(SelectionKey key) {
      try {        
        if (writer.write((SocketChannel) key.channel())) {
          key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
          
          if (bootstrap) 
            close();
        }
      } catch (IOException e) {
        debug("ERROR " + e + " writing - cancelling.");
        close();
      }
    }

    /**
     * Accepts a new connection on the given key
     *
     * @param serverKey The server socket key
     * @exception IOException DESCRIBE THE EXCEPTION
     */
    protected void acceptConnection(SelectionKey key) throws IOException {
      debug("Accepted connection from " + ((SocketChannel) key.channel()).socket().getRemoteSocketAddress());
      
      this.key = SelectorManager.getSelectorManager().register(key.channel(), this, SelectionKey.OP_READ);
    }

    /**
     * Creates the outgoing socket to the remote handle
     *
     * @param address The accress to connect to
     * @exception IOException DESCRIBE THE EXCEPTION
     */
    protected void createConnection(final SourceRoute path) throws IOException {
      final SocketChannel channel = SocketChannel.open();
      channel.socket().setSendBufferSize(SOCKET_BUFFER_SIZE);
      channel.socket().setReceiveBufferSize(SOCKET_BUFFER_SIZE);
      channel.configureBlocking(false);

      final boolean done = channel.connect(path.getFirstHop().getAddress());
      this.path = path;

      debug("Initiating socket connection to path " + path);

      final SelectionKeyHandler handler = this;

      SelectorManager.getSelectorManager().invoke(
        new Runnable() {
          public void run() {
            try {
              if (done) {
                key = SelectorManager.getSelectorManager().register(channel, handler, SelectionKey.OP_READ);
              } else {
                key = SelectorManager.getSelectorManager().register(channel, handler, SelectionKey.OP_READ | SelectionKey.OP_CONNECT);
              }

              SelectorManager.getSelectorManager().modifyKey(key);
            } catch (IOException e) {
              System.out.println("ERROR creating server socket channel " + e);
            }
          }
        });
    }

    /**
     * Method which is called once a message is received off of the wire If it's
     * for us, it's handled here, otherwise, it's passed to the pastry node.
     *
     * @param message The receved message
     */
    protected void receive(Message message) {
      if (message instanceof NodeIdRequestMessage) {
        send(new NodeIdResponseMessage(pastryNode.getNodeId(), localAddress.getEpoch()));
      } else if (message instanceof LeafSetRequestMessage) {
        send(new LeafSetResponseMessage(pastryNode.getLeafSet()));
      } else if (message instanceof RoutesRequestMessage) {
        send(new RoutesResponseMessage((SourceRoute[]) manager.getBest().values().toArray(new SourceRoute[0])));
      } else if (message instanceof RouteRowRequestMessage) {
        RouteRowRequestMessage rrMessage = (RouteRowRequestMessage) message;
        send(new RouteRowResponseMessage(pastryNode.getRoutingTable().getRow(rrMessage.getRow())));
      } else {
        long start = System.currentTimeMillis();
        pastryNode.receiveMessage(message);
        if (SocketPastryNode.verbose) System.out.println("ST: " + (System.currentTimeMillis() - start) + " deliver of " + message);
      }
    }

    /**
     * DESCRIBE THE METHOD
     *
     * @param s DESCRIBE THE PARAMETER
     */
    private void debug(String s) {
      if (Log.ifp(8)) {
        System.out.println(pastryNode.getNodeId() + " (SM " + pastryNode.getNodeId() + " -> " + path + "): " + s);
      }
    }
  }
  
  /**
   * Private class which is tasked with maintaining a source route which goes through this node.
   * This class maintains to sockets, and transfers the data between them.  It also is responsible
   * for performing the initial handshake and sending the data across the wire.
   *
   * @version $Id$
   * @author jeffh
   */
  protected class SourceRouteManager extends SelectionKeyHandler {
    
    // the first key to read from
    private SelectionKey key1;
    
    // the second key to read from
    private SelectionKey key2;
    
    // the repeater, which does the actual byte moving from socket to socket
    private SocketChannelRepeater repeater;
    
    /**
      * Constructor which accepts an incoming connection, represented by the
     * selection key. This constructor builds a new IntermediateSourceRouteManager, 
     * and waits until the greeting message is read from the other end. Once the greeting
     * is received, the manager makes sure that a socket for this handle is not
     * already open, and then proceeds as normal.
     *
     * @param key The server accepting key for the channel
     * @exception IOException DESCRIBE THE EXCEPTION
     */
    public SourceRouteManager(SelectionKey key) throws IOException {
      this.repeater = new SocketChannelRepeater(pastryNode, this);
      sourceRouteOpened(this);
      acceptConnection(key);
    }
    
    /**
     * Internal method which returns the other key
     *
     * @param key The wrong key
     * @return The right key
     */
    private SelectionKey otherKey(SelectionKey key) {
      return ((key == key1) ? key2 : key1);
    }
    
    /**
     * Method which initiates a shutdown of this socket by calling 
     * shutdownOutput().  This has the effect of removing the manager from
     * the open list.
     */
    public void shutdown(SelectionKey key) {
      try {
        System.out.println("SHUTDOWN OUT: " + localAddress + " Shutting down output to SR path...");
        ((SocketChannel) key.channel()).socket().shutdownOutput();
        sourceRouteClosed(this);
      } catch (IOException e) {
        System.err.println("ERROR: Received exception " + e + " while shutting down SR output.");
        close();
      }
    }
    
    /**
      * Method which closes down this socket manager, by closing the socket,
     * cancelling the key and setting the key to be interested in nothing
     */
    public void close() {
      try {
        if (key1 != null) {
          key1.channel().close();
          key1.cancel();
          key1.attach(null);
          key1 = null;
        }
        
        if (key2 != null) {
          key2.channel().close();
          key2.cancel();
          key2.attach(null);
          key2 = null;
        }
        
        System.out.println("SHUTDOWN OUT: " + localAddress + " Closing SR path...");
        
        sourceRouteClosed(this);
      } catch (IOException e) {
        System.out.println("ERROR: Recevied exception " + e + " while closing intermediateSourceRoute!");
      }
    }
    
    /**
      * Specified by the SelectionKeyHandler interface - calling this tells this
     * socket manager that the connection has completed and we can now
     * read/write.
     *
     * @param key The key which is connectable.
     */
    public void connect(SelectionKey key) {
      try {
        // deregister interest in connecting to this socket
        if (((SocketChannel) key.channel()).finishConnect()) 
          key.interestOps(key.interestOps() & ~SelectionKey.OP_CONNECT);
        
        debug("Found connectable source route channel - completed connection");
      } catch (IOException e) {
        debug("Got exception " + e + " on connect - killing off source route");
        
        close();
      }
    }
    
    /**
      * Reads from the socket attached to this connector.
     *
     * @param key The selection key for this manager
     */
    public void read(SelectionKey key) {
      try {        
        try {
          if (repeater.read((SocketChannel) key.channel())) {
            key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
            otherKey(key).interestOps(otherKey(key).interestOps() | SelectionKey.OP_WRITE);
          }
        } catch (ClosedChannelException e) {
          debug("INFO " + e + " reading source route - closing other half...");
          // first, deregister in reading and writing to the appropriate sockets
          ((SocketChannel) key.channel()).socket().shutdownInput();
          key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
          otherKey(key).interestOps(otherKey(key).interestOps() & ~SelectionKey.OP_WRITE);
          
          // then determine if the sockets are now completely shut down,
          // or if only half is now closed
          if (((SocketChannel) otherKey(key).channel()).socket().isInputShutdown()) 
            close();
          else
            shutdown(otherKey(key));
        }
      } catch (IOException e) {
        debug("ERROR " + e + " reading source route - cancelling.");
        close();
      }
    }
    
    /**
     * Writes to the socket attached to this socket manager.
     *
     * @param key The selection key for this manager
     */
    public synchronized void write(SelectionKey key) {
      try {        
        if (repeater.write((SocketChannel) key.channel())) {
          key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
          otherKey(key).interestOps(otherKey(key).interestOps() | SelectionKey.OP_READ);
        }
      } catch (IOException e) {
        debug("ERROR " + e + " writing source route - cancelling.");
        close();
      }
    }
    
    /**
     * Accepts a new connection on the given key
     *
     * @param serverKey The server socket key
     * @exception IOException DESCRIBE THE EXCEPTION
     */
    protected void acceptConnection(SelectionKey key) throws IOException {
      debug("Accepted source route connection from " + ((SocketChannel) key.channel()).socket().getRemoteSocketAddress());
      
      this.key1 = SelectorManager.getSelectorManager().register(key.channel(), this, SelectionKey.OP_READ);
    }
    
    /**
      * Creates the outgoing socket to the remote handle
     *
     * @param address The accress to connect to
     * @exception IOException DESCRIBE THE EXCEPTION
     */
    protected void createConnection(final EpochInetSocketAddress address) throws IOException {
      final SocketChannel channel = SocketChannel.open();
      channel.socket().setSendBufferSize(SOCKET_BUFFER_SIZE);
      channel.socket().setReceiveBufferSize(SOCKET_BUFFER_SIZE);
      channel.configureBlocking(false);
      
      debug("Initiating source route connection to " + address);
      
      boolean done = channel.connect(address.getAddress());
      
      if (done)
        key2 = SelectorManager.getSelectorManager().register(channel, this, SelectionKey.OP_READ);
      else 
        key2 = SelectorManager.getSelectorManager().register(channel, this, SelectionKey.OP_READ | SelectionKey.OP_CONNECT);
    }
    
    /**
      * DESCRIBE THE METHOD
     *
     * @param s DESCRIBE THE PARAMETER
     */
    private void debug(String s) {
      if (Log.ifp(8)) {
        System.out.println(pastryNode.getNodeId() + " (SRM): " + s);
      }
    }
  }
  
  /**
   * Internal class which reads the greeting message off of a newly-accepted 
   * socket.  This class determines whether this is a normal connection or
   * a source-route request, and then hands the connection off to 
   * the appropriate handler (SocketManager or SourceRouteManager).
   */
  protected class SocketAccepter extends SelectionKeyHandler {
    
    // the key to read from
    private SelectionKey key;
    
    // the buffer used to read the header
    private ByteBuffer buffer;
    
    /**
      * Constructor which accepts an incoming connection, represented by the
     * selection key. This constructor builds a new IntermediateSourceRouteManager, 
     * and waits until the greeting message is read from the other end. Once the greeting
     * is received, the manager makes sure that a socket for this handle is not
     * already open, and then proceeds as normal.
     *
     * @param key The server accepting key for the channel
     * @exception IOException DESCRIBE THE EXCEPTION
     */
    public SocketAccepter(SelectionKey key) throws IOException {
      this.buffer = ByteBuffer.allocateDirect(HEADER_SIZE);
      acceptConnection(key);
    }
    
    /**
     * Method which closes down this socket manager, by closing the socket,
     * cancelling the key and setting the key to be interested in nothing
     */
    public void close() {
      try {
        if (key != null) {
          key.channel().close();
          key.cancel();
          key.attach(null);
          key = null;
        }
      } catch (IOException e) {
        System.out.println("ERROR: Recevied exception " + e + " while closing just accepted socket!");
      }
    }
    
    /**
     * Reads from the socket attached to this connector.
     *
     * @param key The selection key for this manager
     */
    public void read(SelectionKey key) {
      try {
        int read = ((SocketChannel) key.channel()).read(buffer);
        
        debug("Read " + read + " bytes from newly accepted connection.");
        
        // implies that the channel is closed
        if (read == -1) 
          throw new IOException("Error on read - the channel has been closed.");
        
        if (buffer.remaining() == 0) 
          processBuffer();
      } catch (IOException e) {
        debug("ERROR " + e + " reading source route - cancelling.");
        close();
      }
    }
       
    /**
     * Accepts a new connection on the given key
     *
     * @param serverKey The server socket key
     * @exception IOException DESCRIBE THE EXCEPTION
     */
    protected void acceptConnection(SelectionKey serverKey) throws IOException {
      final SocketChannel channel = (SocketChannel) ((ServerSocketChannel) serverKey.channel()).accept();
      channel.socket().setSendBufferSize(SOCKET_BUFFER_SIZE);
      channel.socket().setReceiveBufferSize(SOCKET_BUFFER_SIZE);
      channel.configureBlocking(false);
      
      debug("Accepted incoming connection from " + channel.socket().getRemoteSocketAddress());
      
      key = SelectorManager.getSelectorManager().register(channel, this, SelectionKey.OP_READ);
    }
    
    /**
     * Private method which is designed to examine the newly read buffer and
     * handoff the connection to the approriate handler
     *
     * @exception IOException DESCRIBE THE EXCEPTION
     */
    private void processBuffer() throws IOException {
      // flip the buffer
      buffer.flip();
      
      // allocate space for the header
      byte[] array = new byte[HEADER_SIZE];
      buffer.get(array, 0, HEADER_SIZE);
      
      // verify the buffer
      if (Arrays.equals(array, HEADER_DIRECT)) {
        new SocketManager(key);
      } else if (Arrays.equals(array, HEADER_SOURCE_ROUTE)) {
        new SourceRouteManager(key);
      } else {
        System.out.println("ERROR: Improperly formatted header received accepted connection - ignoring.");
        System.out.println("READ " + array[0] + " " + array[1] + " " + array[2] + " " + array[3]);
        throw new IOException("Improperly formatted header received - unknown header.");
      }
    }
    
    /**
     * Debugging method
     *
     * @param s DESCRIBE THE PARAMETER
     */
    private void debug(String s) {
      if (Log.ifp(8)) {
        System.out.println(pastryNode.getNodeId() + " (SA): " + s);
      }
    }
  } 
}
