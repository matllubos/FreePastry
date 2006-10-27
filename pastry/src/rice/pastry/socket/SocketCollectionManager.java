package rice.pastry.socket;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

import rice.environment.logging.Logger;
import rice.environment.params.Parameters;
import rice.environment.random.RandomSource;
import rice.environment.time.TimeSource;
import rice.p2p.commonapi.appsocket.AppSocketReceiver;
import rice.p2p.commonapi.rawserialization.MessageDeserializer;
import rice.p2p.util.MathUtils;
import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.pastry.socket.SocketSourceRouteManager.AddressManager;
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
  
  // factor of jitter to adjust to the ping waits - we may wait up to this time before giving up
  public final float PING_JITTER;
  
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
  
  protected static byte[] PASTRY_MAGIC_NUMBER = new byte[] {0x27, 0x40, 0x75, 0x3A};
  
  // this is for historical purposes, and can definately be renamed
  // this got added in FP 2.0 when we added a magic number and version number
  public static int TOTAL_HEADER_SIZE = PASTRY_MAGIC_NUMBER.length+4+HEADER_SIZE;
  
  // the pastry node which this manager serves
  SocketPastryNode pastryNode;

  // the local address of the node
  EpochInetSocketAddress localAddress;

  // the linked list of open sockets
  private LinkedList socketQueue;

  // maps a SelectionKey -> SocketConnector
  public Hashtable sockets;
  
  // maps a SelectionKey -> SocketConnector
  public LinkedList appSockets;
  
  /**
   * 
   * used to fix a memory leak caused by a hanging SM who never was put into the 
   * sockets collection
   * put() called when SM is constructed
   * remove() called when added to socekts on socketOpened 
   * emptied() in SCM.destroy()
   */
  HashSet unIdentifiedSM = new HashSet();

  // the linked list of open source routes
  private LinkedList sourceRouteQueue;

  // ServerSocketChannel for accepting incoming connections
  private SelectionKey key;

  // the ping manager for doing udp stuff
  private PingManager pingManager;
  
  // the source route manager, which keeps track of routes
  SocketSourceRouteManager manager;
  
  // whether or not we've resigned
  private boolean resigned;
  
  protected Logger logger;
  
  protected RandomSource random;

  TimeSource timeSource;
  
  MessageDeserializer defaultDeserializer;
  /**
   * Constructs a new SocketManager.
   *
   * @param node The pastry node this manager is serving
   * @param port The port number which this manager is listening on
   * @param pool DESCRIBE THE PARAMETER
   * @param address The address to claim the node is at (for proxying)
   */
  public SocketCollectionManager(SocketPastryNode node, SocketSourceRouteManager manager, EpochInetSocketAddress bindAddress, EpochInetSocketAddress proxyAddress, RandomSource random) throws IOException {
    this.pastryNode = node;
    defaultDeserializer = new JavaSerializedDeserializer(node);
    this.manager = manager;
    this.localAddress = proxyAddress;
    this.pingManager = new PingManager(node, manager, bindAddress, proxyAddress);
    this.socketQueue = new LinkedList();
    this.appSockets = new LinkedList();
    this.sockets = new Hashtable();
    this.sourceRouteQueue = new LinkedList();
    this.resigned = false;
    this.logger = node.getEnvironment().getLogManager().getLogger(SocketCollectionManager.class, null);
    this.random = random;
    if (random == null) {
      this.random = node.getEnvironment().getRandomSource(); 
    }
    this.timeSource = node.getEnvironment().getTimeSource();
    
    Parameters p = pastryNode.getEnvironment().getParameters();
    
    MAX_OPEN_SOCKETS = p.getInt("pastry_socket_scm_max_open_sockets");
    MAX_OPEN_SOURCE_ROUTES = p.getInt("pastry_socket_scm_max_open_source_routes");
    SOCKET_BUFFER_SIZE = p.getInt("pastry_socket_scm_socket_buffer_size");
    PING_DELAY = p.getInt("pastry_socket_scm_ping_delay");
    PING_JITTER = p.getFloat("pastry_socket_scm_ping_jitter");
    NUM_PING_TRIES = p.getInt("pastry_socket_scm_num_ping_tries");
    WRITE_WAIT_TIME = p.getInt("pastry_socket_scm_write_wait_time");
    BACKOFF_INITIAL = p.getInt("pastry_socket_scm_backoff_initial");
    BACKOFF_LIMIT = p.getInt("pastry_socket_scm_backoff_limit");

    
    if (logger.level <= Logger.FINE) logger.log("BINDING TO ADDRESS " + bindAddress + " AND CLAIMING " + localAddress);
    
    ServerSocketChannel temp = null; // just to clean up after the exception
    try {
      // bind to port
      final ServerSocketChannel channel = ServerSocketChannel.open();
      temp = channel;
      channel.configureBlocking(false);
      channel.socket().setReuseAddress(true);
      channel.socket().bind(bindAddress.getInnermostAddress());
      
      this.key = pastryNode.getEnvironment().getSelectorManager().register(channel, this, 0);
      this.key.interestOps(SelectionKey.OP_ACCEPT);
    } catch (IOException e) {
//      if (logger.level <= Logger.WARNING) logger.logException("ERROR creating server socket channel ",e);
      try {
        if (temp != null)
          temp.close(); 
      } catch (IOException e2) {
      }
      
      pingManager.resign();
      throw e;
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
  public void bootstrap(SourceRoute path, Message message) throws IOException {
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
  public void send(SourceRoute path, SocketBuffer message, AddressManager am) {
    if (! sendInternal(path, message))
      new MessageRetry(path, message, am); 
  }
  
  /**
   * Method which sends a message across the wire.
   *
   * @param message The message to send
   * @param address The address to send the message to
   */
  public void connect(SourceRoute path, int appId, AppSocketReceiver receiver, int timeout) {
    openAppSocket(path, appId, receiver, timeout);
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
    if (path.getLastHop().equals(localAddress)) return;
    if (! resigned) {
      int rto = manager.rto(path);
      
      DeadChecker checker = new DeadChecker(path, NUM_PING_TRIES, rto);      
      ((SocketPastryNode) pastryNode).getTimer().schedule(checker, rto);
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
    * @param path The path to send the message along
    */
  protected boolean sendInternal(SourceRoute path, SocketBuffer message) {
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
          socketOpened(path, new SocketManager(this, path, bootstrap));
      }
    } catch (IOException e) {
      if (logger.level <= Logger.WARNING) logger.logException("GOT ERROR " + e + " OPENING PATH - MARKING PATH " + path + " AS DEAD!",e);
      closeSocket(path);
      manager.markDead(path);
    }
  }
  
  /**
   * Method which opens a socket to a given remote node handle, and updates the
   * bookkeeping to keep track of this socket
   *
   * @param address The address of the remote node
   */
  protected void openAppSocket(SourceRoute path, int appId, AppSocketReceiver connector, int timeout) {
    try {
      synchronized (sockets) { // all of these changes are synchronized on the same data structure        
        appSocketOpened(new SocketAppSocket(this, path, appId, connector, timeout));
      }
    } catch (IOException e) {
      if (logger.level <= Logger.WARNING) logger.logException("GOT ERROR " + e + " OPENING PATH - MARKING PATH " + path + " AS DEAD!",e);
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

        if ((sockets.size() + appSockets.size()) > MAX_OPEN_SOCKETS) {
          //SourceRoute toClose = (SourceRoute) socketQueue.removeLast();
          closeOneSocket();
        }
      } else {
        if (logger.level <= Logger.FINE) logger.logException( "(SCM) Request to record path opening for already-open path " + path, new Exception("stack trace"));
        String local = "" + localAddress.getAddress(localAddress).getAddress().getHostAddress() +":"+ localAddress.getAddress(localAddress).getPort();
        String remote = "" + path.getLastHop().getAddress(localAddress).getAddress().getHostAddress() +":"+ path.getLastHop().getAddress(localAddress).getPort();

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

  protected void appSocketOpened(SocketAppSocket sas) {
    synchronized (sockets) {
      if (logger.level <= Logger.FINE) logger.log("(SCM) Recorded opening of app socket " + sas);
      appSockets.addFirst(manager);
      
      if ((sockets.size() + appSockets.size()) > MAX_OPEN_SOCKETS) {
        //SourceRoute toClose = (SourceRoute) socketQueue.removeLast();
        closeOneSocket();
      }
    }
  }

  /**
   * TODO: Add also checking the top of the AppSocketQueue
   *
   */
  protected void closeOneSocket() {
    SourceRoute toClose = getSocketToClose();
    socketQueue.remove(toClose);
    
    if (logger.level <= Logger.FINE) logger.log("(SCM) Too many sockets open - closing currently unused socket to path " + toClose);
    closeSocket(toClose); 
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
        if (logger.level <= Logger.FINE) logger.log("(SCM) SocketClosed called with socket not in the list: path:"+path+" manager:"+manager);        
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
  protected void appSocketClosed(SocketAppSocket sas) {
    synchronized (sockets) {
      if (appSockets.contains(sas)) {
        if (logger.level <= Logger.FINE) logger.log("(SCM) Recorded closing of app socket to " + sas);
        appSockets.remove(sas);
      } else {
        if (logger.level <= Logger.FINE) logger.log("(SCM) appSocketClosed called with socket not in the list: path:"+sas);        
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
    key.attach(null);
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
    protected SocketBuffer message;
    
    // This is to keep a hard link to the AM, so it isn't collected
    protected AddressManager am;
    
    /**
     * Constructor, taking a message and the route
     *
     * @param message The message
     * @param route The route
     */
    public MessageRetry(SourceRoute route, SocketBuffer message, AddressManager am) {
      this.am = am;
      this.message = message;
      this.route = route;
      this.timeout = (long) (timeout * (0.8 + (0.4 * random.nextDouble())));

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
          timeout = (long) ((2 * timeout) * (0.8 + (0.4 * random.nextDouble())));
          
          pastryNode.getTimer().schedule(this, timeout);
        } else {
          if (logger.level <= Logger.WARNING) logger.log( "WARNING: Could not send message " + message + " after " + tries + " retries.  Dropping on the floor.");
        } 
      } else {
        if (logger.level <= Logger.FINE) logger.log("BACKOFF: Was able to send message " + message + " after " + tries + " timeout " + timeout + " retries.");
      }
    }
  }
  
  int totalPingsToResponders = 0;
  int totalSuccessfulChecks = 0;
  
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

    
    // for debugging
    long startTime; // the start time
    int initialDelay; // the initial expected delay
    
    /**
     * Constructor for DeadChecker.
     *
     * @param address DESCRIBE THE PARAMETER
     * @param numTries DESCRIBE THE PARAMETER
     * @param mgr DESCRIBE THE PARAMETER
     */
    public DeadChecker(SourceRoute path, int numTries, int initialDelay) {
      if (logger.level <= Logger.INFO) logger.log("CHECK DEAD: " + localAddress + " CHECKING DEATH OF PATH " + path+" rto:"+initialDelay);

      this.path = path;
      this.numTries = numTries;
      
      this.initialDelay = initialDelay;
      this.startTime = timeSource.currentTimeMillis();
    }

    /**
     * DESCRIBE THE METHOD
     *
     * @param address DESCRIBE THE PARAMETER
     * @param RTT DESCRIBE THE PARAMETER
     * @param timeHeardFrom DESCRIBE THE PARAMETER
     */
    public void pingResponse(SourceRoute path, long RTT, long timeHeardFrom) {
      if (!cancelled) {
        totalPingsToResponders+=tries;
        totalSuccessfulChecks++;
        if (tries > 1) {
          long delay = timeSource.currentTimeMillis()-startTime;
          if (logger.level <= Logger.INFO) logger.log("DeadChecker.pingResponse("+path+") tries="+tries+" estimated="+initialDelay+" totalDelay="+delay+" pings="+totalPingsToResponders+" success="+totalSuccessfulChecks);        
        }
      }      
      if (logger.level <= Logger.FINE) logger.log("Terminated DeadChecker(" + path + ") due to ping.");
      manager.markAlive(path);
      cancel();
    }

    /**
     * Main processing method for the DeadChecker object
     * 
     * value of tries before run() is called:the time since ping was called:the time since deadchecker was started 
     * 
     * 1:500:500
     * 2:1000:1500
     * 3:2000:3500
     * 4:4000:7500
     * 5:8000:15500 // ~15 seconds to find 1 path faulty, using source routes gives us 30 seconds to find a node faulty
     * 
     */
    public void run() {
      if (tries < numTries) {
        tries++;
        if (manager.getLiveness(path.getLastHop()) == SocketNodeHandle.LIVENESS_ALIVE)
          manager.markSuspected(path);
        
        
        pingManager.ping(path, this);
        int absPD = (int)(PING_DELAY*Math.pow(2,tries-1));
        int jitterAmt = (int)(((float)absPD)*PING_JITTER);
        int scheduledTime = absPD-jitterAmt+random.nextInt(jitterAmt*2);
        ((SocketPastryNode) pastryNode).getTimer().schedule(this,scheduledTime);
      } else {
        if (logger.level <= Logger.FINE) logger.log("DeadChecker(" + path + ") expired - marking as dead.");
        manager.markDead(path);
        cancel();
      }
    }

    public boolean cancel() {
      pingManager.removePingResponseListener(path,this);
      return super.cancel();
    }
    
    public String toString() {
      return "DeadChecker("+path+" #"+System.identityHashCode(this)+"):"+tries; 
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
    SocketChannel otherChannel(SelectableChannel channel) {
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
      String k = "unlogged";
      if (logger.level <= Logger.FINER) {
        k = (channel == channel1 ? "1" : "2");
        logger.log( "(SRM) " + this + "   adding interest op " + op + " to key " + k);
      }
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
      String k = "unlogged";
      if (logger.level <= Logger.FINER) {
        k = (channel == channel1 ? "1" : "2");
        logger.log( "(SRM) " + this + "   removing interest op " + op + " from key " + k);
      }
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
      String k = "unlogged";
      if (logger.level <= Logger.FINE) {
        k = (key.channel() == channel1 ? "1" : "2");
        logger.log("(SRM) " + this + " reading from key " + k + " " + key.interestOps());
      }
      
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
      if (logger.level <= Logger.FINE) logger.log("(SRM) " + this + " creating connection for key 2 as " + address.getAddress(localAddress));

      channel2 = SocketChannel.open();
      channel2.socket().setSendBufferSize(SOCKET_BUFFER_SIZE);
      channel2.socket().setReceiveBufferSize(SOCKET_BUFFER_SIZE);
      channel2.configureBlocking(false);
      
      if (logger.level <= Logger.FINE) logger.log("(SRM) " + "Initiating source route connection to " + address);
      
      pastryNode.broadcastChannelOpened(address.getAddress(localAddress), NetworkListener.REASON_SR);
      
      boolean done = channel2.connect(address.getAddress(localAddress));

      if (done)
        pastryNode.getEnvironment().getSelectorManager().register(channel2, this, SelectionKey.OP_READ);
      else 
        pastryNode.getEnvironment().getSelectorManager().register(channel2, this, SelectionKey.OP_READ | SelectionKey.OP_CONNECT);
      
      if (logger.level <= Logger.FINE) logger.log("(SRM) " + this + "   setting initial ops to " + SelectionKey.OP_READ + " for key 2");
    }

    public String toString() {
      String s1 = null;
      if (channel1 != null) {
        if (channel1.socket() != null) {
          if (channel1.socket().getRemoteSocketAddress() != null) {
            s1 = channel1.socket().getRemoteSocketAddress().toString();
          } else {
            s1 = channel1.socket().toString();
          }
        } else {
          s1 = channel1.toString(); 
        }
      }
      String s2 = null;
      if (channel2 != null) {
        if (channel2.socket() != null) {
          if (channel2.socket().getRemoteSocketAddress() != null) {
            s2 = channel2.socket().getRemoteSocketAddress().toString();
          } else {
            s2 = channel2.socket().toString();
          }
        } else {
          s2 = channel2.toString(); 
        }
      }
      return "SourceRouteManager "+s1+" to " +s2;
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
      this.buffer = ByteBuffer.allocateDirect(TOTAL_HEADER_SIZE);
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
      pastryNode.broadcastChannelOpened((InetSocketAddress)channel.socket().getRemoteSocketAddress(), NetworkListener.REASON_ACC_NORMAL);

      key = pastryNode.getEnvironment().getSelectorManager().register(channel, this, SelectionKey.OP_READ);
    }
    
    /**
     * Reads from the socket attached to this connector.
     *
     * @param key The selection key for this manager
     */
    public void read(SelectionKey key) {
      try {
        int read = ((SocketChannel) key.channel()).read(buffer);
        
        if (logger.level <= Logger.FINE) logger.log("(SA)1 Read " + read + " bytes from newly accepted connection.");
        
        // implies that the channel is closed
        if (read == -1) 
          throw new IOException("Error on read - the channel has been closed.");
        
        // this could be a problem if a socket is opened and nothing, or not enough is being written
        if (buffer.remaining() == 0) 
          processBuffer();
      } catch (IOException e) {
        if (logger.level <= Logger.FINE) logger.log("(SA) ERROR " + e + " reading source route - cancelling.");
        close();
      }
    }
       
    /**
     * Private method which is designed to examine the newly read buffer and
     * handoff the connection to the approriate handler
     *
     * @exception IOException DESCRIBE THE EXCEPTION
     */
    ByteBuffer appTypeBuffer = null;
    byte[] array = null;
    private void processBuffer() throws IOException {
      // NOTE: this is kind of a funky hack, the reason is that it is possible to 
      // read the header without reading the appId bytes.  So, this code makes 
      // read/processBuffer just keep being called until both arrive
      // we don't want to touch buffer once we construct appTypeBuffer, and we
      // return until it finishes reading
      if (appTypeBuffer == null) {
        // flip the buffer
        buffer.flip();
        array = new byte[HEADER_SIZE];
        buffer.get(array, 0, HEADER_SIZE);
        if (!Arrays.equals(array, PASTRY_MAGIC_NUMBER)) throw new IOException("Not a pastry socket:"+array[0]+","+array[1]+","+array[2]+","+array[3]);
        
        buffer.get(array, 0, HEADER_SIZE);
        int version = MathUtils.byteArrayToInt(array);
        if (!(version == 0)) throw new IOException("Unknown Version:"+version);
        
        // allocate space for the header
        buffer.get(array, 0, HEADER_SIZE);
        appTypeBuffer = ByteBuffer.allocateDirect(4);
      }        
      // verify the buffer
      if (Arrays.equals(array, HEADER_DIRECT)) {
        int read = ((SocketChannel) key.channel()).read(appTypeBuffer);        
        if (logger.level <= Logger.FINE) logger.log("(SA)2 Read " + read + " bytes from newly accepted connection.");            
        if (appTypeBuffer.hasRemaining()) return;

        appTypeBuffer.flip();
        byte[] appIDbytes = new byte[4];         
        appTypeBuffer.get(appIDbytes, 0, 4);
        int appId = MathUtils.byteArrayToInt(appIDbytes);
//        if (logger.level <= Logger.FINE) logger.log("Found connection with AppId "+appId);
        // TODO: make this level FINE when done
        if (appId == 0) {
          unIdentifiedSM.add(new SocketManager(SocketCollectionManager.this, key));
        } else {
          if (logger.level <= Logger.FINE) logger.log("Found connection with AppId "+appId);
          appSockets.add(new SocketAppSocket(SocketCollectionManager.this, key, appId));
        }
      } else if (Arrays.equals(array, HEADER_SOURCE_ROUTE)) {
        new SourceRouteManager(key);
      } else {
        if (logger.level <= Logger.WARNING) logger.log( "ERROR: Improperly formatted header received accepted connection - ignoring.");
        if (logger.level <= Logger.WARNING) logger.log( "READ " + array[0] + " " + array[1] + " " + array[2] + " " + array[3]+" expected "+HEADER_SOURCE_ROUTE[0] + " " + HEADER_SOURCE_ROUTE[1] + " " + HEADER_SOURCE_ROUTE[2] + " " + HEADER_SOURCE_ROUTE[3]);
        throw new IOException("Improperly formatted header received - unknown header.");
      }
    }    
  } 
}
