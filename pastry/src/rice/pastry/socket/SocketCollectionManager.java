package rice.pastry.socket;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

import rice.environment.logging.Logger;
import rice.environment.params.Parameters;
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
  public final int MAX_OPEN_SOCKETS;
  
  // the number of source routes through this node (note, each has 2 sockets)
  public final int MAX_OPEN_SOURCE_ROUTES;
  
  // the size of the buffers for the socket
  public final int SOCKET_BUFFER_SIZE;
  
  // how long to wait for a ping response to come back before declaring lost
  public final int PING_DELAY;
  
  // how much jitter to add to the ping waits - we may wait up to this time before giving up
  public final int PING_JITTER;
  
  // how many tries to ping before giving up
  public final int NUM_PING_TRIES;
  
  // the maximal amount of time to wait for write to be called before checking liveness
  public final int WRITE_WAIT_TIME;
  
  // the initial timeout for exponential backoff
  public final long BACKOFF_INITIAL;
  
  // the limit on the number of times for exponential backoff
  public final int BACKOFF_LIMIT;
  
  // the header which signifies a normal socket
  protected static byte[] HEADER_DIRECT = new byte[] {0x06, 0x1B, 0x49, 0x74};
  
  // the header which signifies a normal socket
  protected static byte[] HEADER_SOURCE_ROUTE = new byte[] {0x19, 0x53, 0x13, 0x00};
  
  // the length of the socket header
  public static int HEADER_SIZE = HEADER_DIRECT.length;
  
  // the pastry node which this manager serves
  private SocketPastryNode pastryNode;

  // the local address of the node
  private EpochInetSocketAddress localAddress;

  // the linked list of open sockets
  private LinkedList socketQueue;

  // maps a SelectionKey -> SocketConnector
  public Hashtable sockets;
  
  /**
   * 
   * used to fix a memory leak caused by a hanging SM who never was put into the 
   * sockets collection
   * put() called when SM is constructed
   * remove() called when added to socekts on socketOpened 
   * emptied() in SCM.destroy()
   */
  private HashSet unIdentifiedSM = new HashSet();

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
  
  protected Logger logger;

  
  /**
   * Constructs a new SocketManager.
   *
   * @param node The pastry node this manager is serving
   * @param port The port number which this manager is listening on
   * @param pool DESCRIBE THE PARAMETER
   * @param address The address to claim the node is at (for proxying)
   */
  public SocketCollectionManager(SocketPastryNode node, SocketNodeHandlePool pool, SocketSourceRouteManager manager, EpochInetSocketAddress bindAddress, EpochInetSocketAddress proxyAddress) {
    this.pastryNode = node;
    this.manager = manager;
    this.localAddress = proxyAddress;
    this.pingManager = new PingManager(node, manager, bindAddress, proxyAddress);
    this.socketQueue = new LinkedList();
    this.sockets = new Hashtable();
    this.sourceRouteQueue = new LinkedList();
    this.resigned = false;
    this.logger = node.getEnvironment().getLogManager().getLogger(SocketChannelWriter.class, null);
    
    Parameters p = pastryNode.getEnvironment().getParameters();
    MAX_OPEN_SOCKETS = p.getInt("pastry_socket_scm_max_open_sockets");
    MAX_OPEN_SOURCE_ROUTES = p.getInt("pastry_socket_scm_max_open_source_routes");
    SOCKET_BUFFER_SIZE = p.getInt("pastry_socket_scm_socket_buffer_size");
    PING_DELAY = p.getInt("pastry_socket_scm_ping_delay");
    PING_JITTER = p.getInt("pastry_socket_scm_ping_jitter");
    NUM_PING_TRIES = p.getInt("pastry_socket_scm_num_ping_tries");
    WRITE_WAIT_TIME = p.getInt("pastry_socket_scm_write_wait_time");
    BACKOFF_INITIAL = p.getInt("pastry_socket_scm_backoff_initial");
    BACKOFF_LIMIT = p.getInt("pastry_socket_scm_backoff_limit");

    
    if (logger.level <= Logger.FINE) logger.log("BINDING TO ADDRESS " + bindAddress + " AND CLAIMING " + localAddress);
    
    try {
      // bind to port
      final ServerSocketChannel channel = ServerSocketChannel.open();
      channel.configureBlocking(false);
      channel.socket().bind(bindAddress.getAddress());
      
      this.key = pastryNode.getEnvironment().getSelectorManager().register(channel, this, 0);
      this.key.interestOps(SelectionKey.OP_ACCEPT);
    } catch (IOException e) {
      if (logger.level <= Logger.WARNING) logger.logException("ERROR creating server socket channel ",e);
    }
  }  
  
  
  /**
   *      -----  EXTERNAL METHODS ----- 
   */
  
  /**
   * Method which sends bootstraps a node by sending message across the wire,
   * using a fake IP address in the header so that the local node is not marked
   * alive, and then closes the connection.
   *
   * @param message The message to send
   * @param address The address to send the message to
   */
  public void bootstrap(SourceRoute path, Message message) {
    if (! resigned) {
      synchronized (sockets) {
        openSocket(path, true);    
        ((SocketManager) sockets.get(path)).send(message);
      }
    }
  }

  /**
   * Method which sends a message across the wire.
   *
   * @param message The message to send
   * @param address The address to send the message to
   */
  public void send(SourceRoute path, Message message) {
    if (! sendInternal(path, message))
      new MessageRetry(path, message); 
  }
  
  /**
   * Method which suggests a ping to the remote node.
   *
   * @param route The route to use
   */
  public void ping(SourceRoute route) {
    if (! resigned) 
      pingManager.ping(route, null);
  }
  
  /**
   * Initiates a liveness test on the given address, if the remote node does not
   * respond, it is declared dead.
   *
   * @param address DESCRIBE THE PARAMETER
   */
  protected void checkLiveness(SourceRoute path) {    
    if (! resigned) {
      if (logger.level <= Logger.FINE) logger.log("CHECK DEAD: " + localAddress + " CHECKING DEATH OF PATH " + path);
      DeadChecker checker = new DeadChecker(path, NUM_PING_TRIES);
      ((SocketPastryNode) pastryNode).getTimer().scheduleAtFixedRate(checker, PING_DELAY + pastryNode.getEnvironment().getRandomSource().nextInt(PING_JITTER), PING_DELAY + pastryNode.getEnvironment().getRandomSource().nextInt(PING_JITTER));
      pingManager.ping(path, checker);
    }
  }
  
  /**
   * Returns whether or not a socket is currently open to the given 
   * route
   *
   * @param route The route
   * @return Whether or not a socket is currently open to that route
   */
  public boolean isOpen(SourceRoute route) {
    return sockets.containsKey(route); 
  }
  
  /**
   * Method which should be called when a remote node is declared dead.  
   * This method will close any outstanding sockets, and will reroute
   * any pending messages
   *
   * @param address The address which was declared dead
   */
  public void declaredDead(EpochInetSocketAddress address) {
    SourceRoute[] routes = (SourceRoute[]) sockets.keySet().toArray(new SourceRoute[0]);
    
    for (int i=0; i<routes.length; i++)
      if (routes[i].getLastHop().equals(address)) {
        if (logger.level <= Logger.FINE) logger.log("WRITE_TIMER::Closing active socket to " + routes[i]);
        ((SocketManager) sockets.get(routes[i])).close();
      }
  }
  
  /**
   *      -----  INTERNAL METHODS ----- 
   */  
  
  /**
    * Method which sends a message across the wire.
    *
    * @param message The message to send
    * @param address The address to send the message to
    */
  protected boolean sendInternal(SourceRoute path, Message message) {
    if (! resigned) {
      synchronized (sockets) {
        if (! sockets.containsKey(path)) {
          if (logger.level <= Logger.FINE) logger.log("(SCM) No connection open to path " + path + " - opening one");
          openSocket(path, false);
        }
        
        if (sockets.containsKey(path)) {
          if (logger.level <= Logger.FINE) logger.log("(SCM) Found connection open to path " + path + " - sending now");
          
          ((SocketManager) sockets.get(path)).send(message);
          socketUpdated(path);
          return true;
        } else {
          if (logger.level <= Logger.WARNING) logger.log( "(SCM) ERROR: Could not connect to remote address " + path + " delaying " + message);
          return false;
        }
      }
    } else {
      return true;
    }
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
      if (logger.level <= Logger.WARNING) logger.log( "ERROR (accepting connection): " + e);
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
        if ((! sockets.containsKey(path)) && 
            ((sockets.size() < MAX_OPEN_SOCKETS) || (getSocketToClose() != null)))
          socketOpened(path, new SocketManager(path, bootstrap));
      }
    } catch (IOException e) {
      if (logger.level <= Logger.WARNING) logger.logException("GOT ERROR " + e + " OPENING PATH - MARKING PATH " + path + " AS DEAD!",e);
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
        if (logger.level <= Logger.SEVERE) logger.log( "(SCM) SERIOUS ERROR: Request to close socket to non-open handle to path " + path);
      }
    }
  }
  
  /**
   * Internal method which returns the next socket to be closed
   * 
   * @return The next socket to be closed
   */
  protected SourceRoute getSocketToClose() {
    for (int i=socketQueue.size()-1; i>=0; i--)
      if (((SocketManager) sockets.get(socketQueue.get(i))).writer.isEmpty()) 
        return (SourceRoute) socketQueue.get(i);
    
    return null;
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
        unIdentifiedSM.remove(manager);
        sockets.put(path, manager);
        socketQueue.addFirst(path);

        if (logger.level <= Logger.FINE) logger.log("(SCM) Recorded opening of socket to path " + path);

        if (sockets.size() > MAX_OPEN_SOCKETS) {
          //SourceRoute toClose = (SourceRoute) socketQueue.removeLast();
          SourceRoute toClose = getSocketToClose();
          socketQueue.remove(toClose);
          
          if (logger.level <= Logger.FINE) logger.log("(SCM) Too many sockets open - closing currently unused socket to path " + toClose);
          closeSocket(toClose);
        }
      } else {
        if (logger.level <= Logger.WARNING) logger.log( "(SCM) ERROR: Request to record path opening for already-open path " + path);
        String local = "" + localAddress.getAddress().getAddress().getHostAddress() + localAddress.getAddress().getPort();
        String remote = "" + path.getLastHop().getAddress().getAddress().getHostAddress() + path.getLastHop().getAddress().getPort();

        if (logger.level <= Logger.FINE) logger.log("(SCM) RESOLVE: Comparing paths " + local + " and " + remote);

        if (remote.compareTo(local) < 0) {
          if (logger.level <= Logger.FINE) logger.log("(SCM) RESOLVE: Cancelling existing connection to " + path);
          SocketManager toClose = (SocketManager) sockets.get(path);

          socketClosed(path, toClose);
          socketOpened(path, manager);
          toClose.close();
        } else {
          if (logger.level <= Logger.FINE) logger.log("(SCM) RESOLVE: Implicitly cancelling new connection to path " + path);
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
          if (logger.level <= Logger.FINE) logger.log("(SCM) Recorded closing of socket to " + path);

          socketQueue.remove(path);
          sockets.remove(path);
        } else {
          if (logger.level <= Logger.FINE) logger.log("(SCM) SocketClosed called with corrent address, but incorrect manager - not a big deal.");
        }
      } else {
        if (logger.level <= Logger.SEVERE) logger.log( "(SCM) SEROUS ERROR: Request to record socket closing for non-existant socket to path " + path);
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
        if (logger.level <= Logger.SEVERE) logger.log( "(SCM) SERIOUS ERROR: Request to record update for non-existant socket to " + path);
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
      
      if (logger.level <= Logger.FINE) logger.log("(SCM) Recorded opening of source route manager " + manager);
      
      if (sourceRouteQueue.size() > MAX_OPEN_SOURCE_ROUTES) {
        SourceRouteManager toClose = (SourceRouteManager) sourceRouteQueue.removeLast();
        if (logger.level <= Logger.FINE) logger.log("(SCM) Too many source routes open - closing source route manager " + toClose);

        toClose.close();
        sourceRouteClosed(toClose);
      }
    } else {
      if (logger.level <= Logger.FINE) logger.log("(SCM) ERROR: Request to record source route opening for already-open manager " + manager);
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
      
      if (logger.level <= Logger.FINE) logger.log("(SCM) Recorded closing of source route manager " + manager);      
    } else {
      if (logger.level <= Logger.WARNING) logger.log( "(SCM) ERROR: Request to record source route closing for unknown manager " + manager);
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
      if (logger.level <= Logger.SEVERE) logger.log( "(SCM) SERIOUS ERROR: Request to record update for unknown source route " + manager);
    }
  }

  /**
   * Makes this node resign from the network.  Is designed to be used for
   * debugging and testing.
   */
  public void destroy() throws IOException {
    resigned = true;
    
    pingManager.resign();
    
    while (socketQueue.size() > 0) 
      ((SocketManager) sockets.get(socketQueue.getFirst())).close();
    
    while (sourceRouteQueue.size() > 0) 
      ((SourceRouteManager) sourceRouteQueue.getFirst()).close();
        
    // anything somehow left in sockets?
    while (sockets.size() > 0) {
      ((SocketManager) sockets.values().iterator().next()).close();   
    }
    
    // any left in un
    while (unIdentifiedSM.size() > 0) {
      ((SocketManager) unIdentifiedSM.iterator().next()).close();
    }
    
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
   * Internal testing method which simulates a stall. DO NOT USE!!!!!
   */
  public void stall() {
    key.interestOps(key.interestOps() & ~SelectionKey.OP_ACCEPT);

    Iterator i = sockets.keySet().iterator();
    
    while (i.hasNext()) {
      SelectionKey key = ((SocketManager) sockets.get(i.next())).key; 
      key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
    }
    
    pingManager.stall();
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
   * Internal class which represents a message which is currently delayed, waiting
   * for an open socket.  The message will be tried using exponential backoff up
   * to BACKOFF_LIMIT times before being dropped.
   */
  protected class MessageRetry extends rice.selector.TimerTask {
    
    // The number of tries that have occurred so far
    protected int tries = 0;
    
    // the current timeout
    protected long timeout = BACKOFF_INITIAL;
   
    // The destination route
    protected SourceRoute route;
    
    // The message
    protected Message message;
    
    /**
     * Constructor, taking a message and the route
     *
     * @param message The message
     * @param route The route
     */
    public MessageRetry(SourceRoute route, Message message) {
      this.message = message;
      this.route = route;
      this.timeout = (long) (timeout * (0.8 + (0.4 * pastryNode.getEnvironment().getRandomSource().nextDouble())));

      pastryNode.getTimer().schedule(this, timeout);
    }
    
    /**
     * Main processing method for the DeadChecker object
     */
    public void run() {
      if (! sendInternal(route, message)) {
        if (logger.level <= Logger.FINE) logger.log("BACKOFF: Could not send message " + message + " after " + tries + " timeout " + timeout + " retries - retrying.");

        if (tries < BACKOFF_LIMIT) {
          tries++;
          timeout = (long) ((2 * timeout) * (0.8 + (0.4 * pastryNode.getEnvironment().getRandomSource().nextDouble())));
          
          pastryNode.getTimer().schedule(this, timeout);
        } else {
          if (logger.level <= Logger.WARNING) logger.log( "WARNING: Could not send message " + message + " after " + tries + " retries.  Dropping on the floor.");
        } 
      } else {
        if (logger.level <= Logger.FINE) logger.log("BACKOFF: Was able to send message " + message + " after " + tries + " timeout " + timeout + " retries.");
      }
    }
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
      if (logger.level <= Logger.FINE) logger.log("DeadChecker(" + path + ") started.");

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
      if (logger.level <= Logger.FINE) logger.log("Terminated DeadChecker(" + path + ") due to ping.");
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
        
        pingManager.ping(path, this);
      } else {
        if (logger.level <= Logger.FINE) logger.log("DeadChecker(" + path + ") expired - marking as dead.");
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
    protected SelectionKey key;
    
    // the channel we are associated with
    protected SocketChannel channel;

    // the reader reading data off of the stream
    protected SocketChannelReader reader;

    // the writer (in case it is necessary)
    protected SocketChannelWriter writer;
    
    // the timer we use to check for stalled nodes
    protected rice.selector.TimerTask timer;

    // the node handle we're talking to
    protected SourceRoute path;
    
    // whether or not this is a bootstrap socket - if so, we fake the address 
    // and die once the the message has been sent
    protected boolean bootstrap;

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
      
      if (logger.level <= Logger.FINE) logger.log("Opening connection with path " + path);
      
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
        if (logger.level <= Logger.FINE) logger.log("Shutting down output on connection with path " + path);
        
        if (channel != null)
          channel.socket().shutdownOutput();
        else
          if (logger.level <= Logger.SEVERE) logger.log( "ERROR: Unable to shutdown output on channel; channel is null!");

        socketClosed(path, this);
        pastryNode.getEnvironment().getSelectorManager().modifyKey(key);
      } catch (IOException e) {
        if (logger.level <= Logger.SEVERE) logger.log( "ERROR: Received exception " + e + " while shutting down output.");
        close();
      }
    }

    /**
     * Method which closes down this socket manager, by closing the socket,
     * cancelling the key and setting the key to be interested in nothing
     */
    public void close() {
      try {
        if (logger.level <= Logger.FINE) logger.log("Closing connection with path " + path);
        
        clearTimer();
        
        if (key != null) {
          key.cancel();
          key.attach(null);
          key = null;
        }
        
        unIdentifiedSM.remove(this);
        
        if (channel != null) 
          channel.close();

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
        if (logger.level <= Logger.SEVERE) logger.log( "ERROR: Recevied exception " + e + " while closing socket!");
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
        pastryNode.getEnvironment().getSelectorManager().modifyKey(key);
    }

    /**
     * Method which should change the interestOps of the handler's key. This
     * method should *ONLY* be called by the selection thread in the context of
     * a select().
     *
     * @param key The key in question
     */
    public synchronized void modifyKey(SelectionKey key) {
      if (channel.socket().isOutputShutdown()) {
        key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
        clearTimer();
      } else if ((! writer.isEmpty()) && ((key.interestOps() & SelectionKey.OP_WRITE) == 0)) {
        key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
        setTimer();
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
        if (channel.finishConnect()) 
          key.interestOps(key.interestOps() & ~SelectionKey.OP_CONNECT);

        manager.markAlive(path);

        if (logger.level <= Logger.FINE) logger.log("(SM) Found connectable channel - completed connection");
      } catch (Exception e) {
        if (logger.level <= Logger.FINE) logger.logException(
            "(SM) Unable to connect to path " + path + " (" + e + ") marking as dead.",e);
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
        Object o = reader.read(channel);

        if (o != null) {
          if (logger.level <= Logger.FINE) logger.log("(SM) Read message " + o + " from socket.");
          if (o instanceof SourceRoute) {
            if (this.path == null) {
              this.path = (SourceRoute) o;
              socketOpened(this.path, this);
              manager.markAlive(this.path);
              this.writer.setPath(this.path);
              this.reader.setPath(this.path.reverse());

              if (logger.level <= Logger.FINE) logger.log("Read open connection with path " + this.path);              
            } else {
              if (logger.level <= Logger.SEVERE) logger.log( "SERIOUS ERROR: Received duplicate path assignments: " + this.path + " and " + o);
            }
          } else {
            receive((Message) o);
          }
        }
      } catch (IOException e) {
        if (logger.level <= Logger.FINE) logger.log("(SM) WARNING " + e + " reading - cancelling.");        
        
        // if it's not a bootstrap path, and we didn't close this socket's output,
        // then check to see if the remote address is dead or just closing a socket
        if ((path != null) && 
            (! ((SocketChannel) key.channel()).socket().isOutputShutdown()))
          checkLiveness(path);
        
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
        clearTimer();
        
        if (writer.write(channel)) {
          key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
          
          if (bootstrap) 
            close();
        } else {
          setTimer();
        }
      } catch (IOException e) {
        if (logger.level <= Logger.WARNING) logger.log( "(SM) ERROR " + e + " writing - cancelling.");
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
      this.channel = (SocketChannel) key.channel();
      this.key = pastryNode.getEnvironment().getSelectorManager().register(key.channel(), this, 0);
      this.key.interestOps(SelectionKey.OP_READ);
      
      if (logger.level <= Logger.FINE) logger.log("(SM) Accepted connection from " + channel.socket().getRemoteSocketAddress());
    }

    /**
     * Creates the outgoing socket to the remote handle
     *
     * @param address The accress to connect to
     * @exception IOException DESCRIBE THE EXCEPTION
     */
    protected void createConnection(final SourceRoute path) throws IOException {
      this.path = path;
      this.channel = SocketChannel.open();
      this.channel.socket().setSendBufferSize(SOCKET_BUFFER_SIZE);
      this.channel.socket().setReceiveBufferSize(SOCKET_BUFFER_SIZE);
      this.channel.configureBlocking(false);
      this.key = pastryNode.getEnvironment().getSelectorManager().register(channel, this, 0);
      
      if (logger.level <= Logger.FINE) logger.log("(SM) Initiating socket connection to path " + path);

      if (this.channel.connect(path.getFirstHop().getAddress())) 
        this.key.interestOps(SelectionKey.OP_READ);
      else 
        this.key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_CONNECT);
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
        long start = pastryNode.getEnvironment().getTimeSource().currentTimeMillis();
        pastryNode.receiveMessage(message);
        if (logger.level <= Logger.FINER) logger.log( "ST: " + (pastryNode.getEnvironment().getTimeSource().currentTimeMillis() - start) + " deliver of " + message);
      }
    }
    
    /**
     * Internal method which sets the internal timer
     */
    protected void setTimer() {
      if (this.timer == null) {
        this.timer = new rice.selector.TimerTask() {
          public void run() {
            if (logger.level <= Logger.FINE) logger.log("WRITE_TIMER::Timer expired, checking liveness...");
            manager.checkLiveness(path.getLastHop());
          }
        };
        
        pastryNode.getEnvironment().getSelectorManager().schedule(this.timer, WRITE_WAIT_TIME);
      }
    }
    
    /**
     * Internal method which clears the internal timer
     */
    protected void clearTimer() {
      if (this.timer != null)
        this.timer.cancel();
      
      this.timer = null;
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

    // the first channel
    private SocketChannel channel1;
    
    // the second channel
    private SocketChannel channel2;
    
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
    private SocketChannel otherChannel(SelectableChannel channel) {
      return (channel == channel1 ? channel2 : channel1);
    }
    
    /**
     * Internal method which adds an interest op to the given channel's interest set.
     * One should note that if the passed in key is null, it will determine which
     * channel this is the key for, and then rebuild a key for that channel.  
     *
     * @param channel The channel
     * @param op The operation to add to the key's interest set
     */
    protected void addInterestOp(SelectableChannel channel, int op) throws IOException {
      String k = (channel == channel1 ? "1" : "2");
      if (logger.level <= Logger.FINER) logger.log( "(SRM) " + this + "   adding interest op " + op + " to key " + k);

      if (pastryNode.getEnvironment().getSelectorManager().getKey(channel) == null) {
        if (logger.level <= Logger.FINER) logger.log( "(SRM) " + this + "   key " + k + " is null - reregistering with ops " + op);
        pastryNode.getEnvironment().getSelectorManager().register(channel, this, op);
      } else {
        pastryNode.getEnvironment().getSelectorManager().register(channel, this, pastryNode.getEnvironment().getSelectorManager().getKey(channel).interestOps() | op);
        if (logger.level <= Logger.FINER) logger.log( "(SRM) " + this + "   interest ops for key " + k + " are now " + pastryNode.getEnvironment().getSelectorManager().getKey(channel).interestOps());
      }
    }
    
    /**
     * Internal method which removes an interest op to the given key's interest set.
     * One should note that if the passed in key no longer has any interest ops,
     * it is cancelled, removed from the selector's key set, and the corresponding
     * key is set to null in this class.
     *
     * @param channel The channel
     * @param op The operation to remove from the key's interest set
     */
    protected void removeInterestOp(SelectableChannel channel, int op) throws IOException {
      String k = (channel == channel1 ? "1" : "2");
      if (logger.level <= Logger.FINER) logger.log( "(SRM) " + this + "   removing interest op " + op + " from key " + k);

      SelectionKey key = pastryNode.getEnvironment().getSelectorManager().getKey(channel);
      
      if (key != null) {
        key.interestOps(key.interestOps() & ~op);
      
        if (key.interestOps() == 0) {
          if (logger.level <= Logger.FINER) logger.log( "(SRM) " + this + "   key " + k + " has no interest ops - cancelling");
          pastryNode.getEnvironment().getSelectorManager().cancel(key);
        }
      }
    }
    
    /**
     * Method which initiates a shutdown of this socket by calling 
     * shutdownOutput().  This has the effect of removing the manager from
     * the open list.
     */
    public void shutdown(SocketChannel channel) {
      try {
        if (logger.level <= Logger.FINE) logger.log("(SRM) " + this + " shutting down output to key " + (channel == channel1 ? "1" : "2"));
        channel.socket().shutdownOutput();
        sourceRouteClosed(this);
      } catch (IOException e) {
        if (logger.level <= Logger.SEVERE) logger.log( "ERROR: Received exception " + e + " while shutting down SR output.");
        close();
      }
    }
    
    /**
      * Method which closes down this socket manager, by closing the socket,
     * cancelling the key and setting the key to be interested in nothing
     */
    public void close() {
      if (logger.level <= Logger.FINE) logger.log("(SRM) " + this + " closing source route");
      
      try {
        if (channel1 != null) {
          SelectionKey key = pastryNode.getEnvironment().getSelectorManager().getKey(channel1);
          
          if (key != null)
            key.cancel();
          channel1.close();
          channel1 = null;
        }
        
        if (channel2 != null) {
          SelectionKey key = pastryNode.getEnvironment().getSelectorManager().getKey(channel2);
          
          if (key != null)
            key.cancel();
          channel2.close();
          channel2 = null;
        }
        
        sourceRouteClosed(this);
      } catch (IOException e) {
        if (logger.level <= Logger.WARNING) logger.log( "ERROR: Recevied exception " + e + " while closing intermediateSourceRoute!");
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
      if (logger.level <= Logger.FINE) logger.log("(SRM) " + this + " connecting to key " + (key.channel() == channel1 ? "1" : "2"));
      
      try {
        // deregister interest in connecting to this socket
        if (((SocketChannel) key.channel()).finishConnect()) 
          removeInterestOp(key.channel(), SelectionKey.OP_CONNECT);
        
        if (logger.level <= Logger.FINE) logger.log("(SRM) Found connectable source route channel - completed connection");
      } catch (IOException e) {
        if (logger.level <= Logger.WARNING) logger.log( "(SRM) Got exception " + e + " on connect - killing off source route");
        
        close();
      }
    }
    
    /**
      * Reads from the socket attached to this connector.
     *
     * @param key The selection key for this manager
     */
    public void read(SelectionKey key) {
      String k = (key.channel() == channel1 ? "1" : "2");
      if (logger.level <= Logger.FINE) logger.log("(SRM) " + this + " reading from key " + k + " " + key.interestOps());
      
      try {        
        try {
          if (repeater.read((SocketChannel) key.channel())) {
            addInterestOp(otherChannel(key.channel()), SelectionKey.OP_WRITE);
            removeInterestOp(key.channel(), SelectionKey.OP_READ);
          }

          if (logger.level <= Logger.FINE) logger.log("(SRM) " + this + " done reading from key " + k);
        } catch (ClosedChannelException e) {
          if (logger.level <= Logger.FINE) logger.log("(SRM) " + this + " reading from key " + k + " returned -1 - processing shutdown");
          
          // then determine if the sockets are now completely shut down,
          // or if only half is now closed
          if (otherChannel(key.channel()).socket().isInputShutdown()) {
            if (logger.level <= Logger.FINE) logger.log("(SRM) " + this + " other key is shut down - closing");
            close();
          } else {
            // first, deregister in reading and writing to the appropriate sockets
            ((SocketChannel) key.channel()).socket().shutdownInput();
            removeInterestOp(key.channel(), SelectionKey.OP_READ);
            removeInterestOp(otherChannel(key.channel()), SelectionKey.OP_WRITE);

            if (logger.level <= Logger.FINE) logger.log("(SRM) " + this + " other key not yet closed - shutting it down");
            shutdown(otherChannel(key.channel()));
          }
        }
      } catch (IOException e) {
        if (logger.level <= Logger.FINE) logger.logException(
            "(SRM) ERROR " + e + " reading source route - cancelling.",e);
        close();
      }
    }
    
    /**
     * Writes to the socket attached to this socket manager.
     *
     * @param key The selection key for this manager
     */
    public synchronized void write(SelectionKey key) { 
      String k = (key.channel() == channel1 ? "1" : "2");
      if (logger.level <= Logger.FINER) logger.log( "(SRM) " + this + " writing to key " + k + " " + key.interestOps());

      try {        
        if (repeater.write((SocketChannel) key.channel())) {
          addInterestOp(otherChannel(key.channel()), SelectionKey.OP_READ);
          removeInterestOp(key.channel(), SelectionKey.OP_WRITE);
        }
        
        if (logger.level <= Logger.FINER) logger.log( "(SRM) " + this + " done writing to key " + k);
      } catch (IOException e) {
        if (logger.level <= Logger.WARNING) logger.log( "ERROR " + e + " writing source route - cancelling.");
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
      if (logger.level <= Logger.FINE) logger.log("(SRM) " + this + " accepted connection for key 1 as " + ((SocketChannel) key.channel()).socket().getRemoteSocketAddress());

      if (logger.level <= Logger.FINE) logger.log("(SRM) Accepted source route connection from " + ((SocketChannel) key.channel()).socket().getRemoteSocketAddress());
      
      pastryNode.getEnvironment().getSelectorManager().register(key.channel(), this, SelectionKey.OP_READ);
      this.channel1 = (SocketChannel) key.channel();
    }
    
    /**
      * Creates the outgoing socket to the remote handle
     *
     * @param address The accress to connect to
     * @exception IOException DESCRIBE THE EXCEPTION
     */
    protected void createConnection(final EpochInetSocketAddress address) throws IOException {  
      if (logger.level <= Logger.FINE) logger.log("(SRM) " + this + " creating connection for key 2 as " + address.getAddress());

      channel2 = SocketChannel.open();
      channel2.socket().setSendBufferSize(SOCKET_BUFFER_SIZE);
      channel2.socket().setReceiveBufferSize(SOCKET_BUFFER_SIZE);
      channel2.configureBlocking(false);
      
      if (logger.level <= Logger.FINE) logger.log("(SRM) " + "Initiating source route connection to " + address);
      
      boolean done = channel2.connect(address.getAddress());

      if (done)
        pastryNode.getEnvironment().getSelectorManager().register(channel2, this, SelectionKey.OP_READ);
      else 
        pastryNode.getEnvironment().getSelectorManager().register(channel2, this, SelectionKey.OP_READ | SelectionKey.OP_CONNECT);
      
      if (logger.level <= Logger.FINE) logger.log("(SRM) " + this + "   setting initial ops to " + SelectionKey.OP_READ + " for key 2");
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
        if (logger.level <= Logger.WARNING) logger.log( "(SA) " + "ERROR: Recevied exception " + e + " while closing just accepted socket!");
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
        
        if (logger.level <= Logger.FINE) logger.log("(SA) Read " + read + " bytes from newly accepted connection.");
        
        // implies that the channel is closed
        if (read == -1) 
          throw new IOException("Error on read - the channel has been closed.");
        
        if (buffer.remaining() == 0) 
          processBuffer();
      } catch (IOException e) {
        if (logger.level <= Logger.FINE) logger.log("(SA) ERROR " + e + " reading source route - cancelling.");
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
      
      if (logger.level <= Logger.FINE) logger.log("(SA) " + "Accepted incoming connection from " + channel.socket().getRemoteSocketAddress());
      
      key = pastryNode.getEnvironment().getSelectorManager().register(channel, this, SelectionKey.OP_READ);
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
        unIdentifiedSM.add(new SocketManager(key));
      } else if (Arrays.equals(array, HEADER_SOURCE_ROUTE)) {
        new SourceRouteManager(key);
      } else {
        if (logger.level <= Logger.WARNING) logger.log( "ERROR: Improperly formatted header received accepted connection - ignoring.");
        if (logger.level <= Logger.WARNING) logger.log( "READ " + array[0] + " " + array[1] + " " + array[2] + " " + array[3]);
        throw new IOException("Improperly formatted header received - unknown header.");
      }
    }    
  } 
}
