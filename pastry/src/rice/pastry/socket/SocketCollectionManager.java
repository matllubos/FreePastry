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
  
  // the size of the buffers for the socket
  public static int SOCKET_BUFFER_SIZE = 32768;
  
  // the ping throttle, or how often to actually ping a remote node
  public static int PING_THROTTLE = 300000;

  // how long to wait for a ping response to come back before declaring lost
  public static long PING_DELAY = 1500;
  
  // how many tries to ping before giving up
  public static int NUM_PING_TRIES = 3;
  
  // the timeout for writing time
  public static long TIMER_TIMEOUT = 180000;
  
  // the fake port for booting
  public static int BOOTSTRAP_PORT = 1;

  // the pastry node which this manager serves
  private SocketPastryNode pastryNode;

  // the node handle pool on this node
  private SocketNodeHandlePool pool;

  // the local address of the node
  private InetSocketAddress localAddress;

  // the linked list of open sockets
  private LinkedList queue;

  // maps a SelectionKey -> SocketConnector
  private Hashtable sockets;

  // ServerSocketChannel for accepting incoming connections
  private SelectionKey key;

  // the port number this manager is listening on
  private int port;

  // the list of cached dead addresses
  private HashSet dead;

  // the ping manager for doing udp stuff
  private PingManager pingManager;

  /**
   * Constructs a new SocketManager.
   *
   * @param node The pastry node this manager is serving
   * @param port The port number which this manager is listening on
   * @param pool DESCRIBE THE PARAMETER
   * @param manager DESCRIBE THE PARAMETER
   * @param pingManager DESCRIBE THE PARAMETER
   * @param address The address to claim the node is at (for proxying)
   */
  public SocketCollectionManager(SocketPastryNode node, SocketNodeHandlePool pool, int port, PingManager pingManager, InetSocketAddress bindAddress, InetSocketAddress proxyAddress) {
    this.pingManager = pingManager;
    this.pastryNode = node;
    this.port = port;
    this.pool = pool;
    this.localAddress = proxyAddress;
    queue = new LinkedList();
    sockets = new Hashtable();
    dead = new HashSet();

    try {

      // bind to port
      final ServerSocketChannel channel = ServerSocketChannel.open();
      channel.configureBlocking(false);

      System.out.println("BINDING TO ADDRESS " + bindAddress + " AND CLAIMING " + localAddress);

      channel.socket().bind(bindAddress);
      
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
   * Method which returns the last cached liveness value for the given address.
   * If there is no cached value, then true is returned.
   *
   * @param address The address to return the value for
   * @return The Alive value
   */
  public boolean isAlive(InetSocketAddress address) {
    return (! dead.contains(address));
  }

  /**
   * Prints out the list of all open sockets
   *
   * @return The list of all open sockets
   */
  public String toString() {
    Enumeration i = sockets.keys();
    String result = "";

    while (i.hasMoreElements()) {
      result += (i.nextElement()).toString() + "\n";
    }

    return result;
  }

  /**
   * Method which sends a message across the wire.
   *
   * @param message The message to send
   * @param address The address to send the message to
   */
  public void send(InetSocketAddress address, Message message) {
    synchronized (sockets) {
      if (!sockets.containsKey(address)) {
        debug("No connection open to " + address + " - opening one");
        openSocket(address, false);
      }

      if (sockets.containsKey(address)) {
        debug("Found connection open to " + address + " - sending now");

        ((SocketManager) sockets.get(address)).send(message);
        socketUpdated(address);
      } else {
        debug("ERROR: Could not connection to remote address " + address + " rerouting message " + message);
        reroute(address, message);
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
  public void bootstrap(InetSocketAddress address, Message message) {
    synchronized (sockets) {
      openSocket(address, true);    
      ((SocketManager) sockets.get(address)).send(message);
    }
  }

  /**
   * Method which returns the last cached proximity value for the given address.
   * If there is no cached value, then DEFAULT_PROXIMITY is returned.
   *
   * @param address The address to return the value for
   * @return The ping value to the remote address
   */
  public int proximity(InetSocketAddress address) {
    return pingManager.proximity(address);
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
      new SocketManager(key);
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
  protected void checkDead(InetSocketAddress address) {
    if (address == null) {
      return;
    }
    
    DeadChecker checker = new DeadChecker(address, NUM_PING_TRIES, pingManager);
    pastryNode.getTimer().scheduleAtFixedRate(checker, PING_DELAY, PING_DELAY);
    pingManager.forcePing(address, checker);
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
  protected void socketOpened(InetSocketAddress address, SocketManager manager) {
    synchronized (sockets) {
      if (!sockets.containsKey(address)) {
        sockets.put(address, manager);
        queue.addFirst(address);

        debug("Recorded opening of socket to " + address);

        if (sockets.size() > MAX_OPEN_SOCKETS) {
          InetSocketAddress toClose = (InetSocketAddress) queue.removeLast();
          debug("Too many sockets open - closing socket to " + toClose);
          closeSocket(toClose);
        }
      } else {
        debug("ERROR: Request to record socket opening for already-open socket to " + address);
        String local = "" + localAddress.getAddress().getHostAddress() + localAddress.getPort();
        String remote = "" + address.getAddress().getHostAddress() + address.getPort();

        debug("RESOLVE: Comparing " + local + " and " + remote);

        if (remote.compareTo(local) < 0) {
          debug("RESOLVE: Cancelling existing connection to " + address);
          SocketManager toClose = (SocketManager) sockets.get(address);

          socketClosed(address, toClose);
          socketOpened(address, manager);
          toClose.close();
        } else {
          debug("RESOLVE: Cancelling new connection to " + address);
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
  protected void socketClosed(InetSocketAddress address, SocketManager manager) {
    synchronized (sockets) {
      if (sockets.containsKey(address)) {
        if (sockets.get(address) == manager) {
          debug("Recorded closing of socket to " + address);

          queue.remove(address);
          sockets.remove(address);
        } else {
          debug("SocketClosed called with corrent address, but incorrect manager - not a big deal.");
        }
      } else {
        debug("SEROUS ERROR: Request to record socket closing for non-existant socket to " + address);
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
  protected void socketUpdated(InetSocketAddress address) {
    synchronized (sockets) {
      if (sockets.containsKey(address)) {
        queue.remove(address);
        queue.addFirst(address);
      } else {
        debug("SERIOUS ERROR: Request to record update for non-existant socket to " + address);
      }
    }
  }

  /**
   * Method which opens a socket to a given remote node handle, and updates the
   * bookkeeping to keep track of this socket
   *
   * @param address The address of the remote node
   */
  protected void openSocket(InetSocketAddress address, boolean bootstrap) {
    try {
      synchronized (sockets) {
        if (!sockets.containsKey(address)) {
          socketOpened(address, new SocketManager(address, bootstrap));
        } else {
          debug("SERIOUS ERROR: Request to open socket to already-open socket to " + address);
        }
      }
    } catch (IOException e) {
      System.out.println("GOT ERROR " + e + " OPENING SOCKET - MARKING " + address + " AS DEAD!");
      e.printStackTrace();
      closeSocket(address);
      markDead(address);
    }
  }

  /**
   * Method which cloeses a socket to a given remote node handle, and updates
   * the bookkeeping to keep track of this closing
   *
   * @param address The address of the remote node
   */
  protected void closeSocket(InetSocketAddress address) {
    synchronized (sockets) {
      if (sockets.containsKey(address)) {
        ((SocketManager) sockets.get(address)).close();
      } else {
        debug("SERIOUS ERROR: Request to close socket to non-open handle to " + address);
      }
    }
  }

  /**
   * Reroutes the given message. If this node is alive, send() is called. If
   * this node is not alive and the message is a route message, it is rerouted.
   * Otherwise, the message is dropped.
   *
   * @param m The message
   * @param address The address of the remote node
   */
  protected void reroute(InetSocketAddress address, Message m) {
    if (isAlive(address)) {
      debug("Attempting to resend message " + m + " to alive address " + address);
      send(address, m);
    } else {
      if (m instanceof RouteMessage) {
        debug("Attempting to reroute route message " + m);
        ((RouteMessage) m).nextHop = null;
        pastryNode.receiveMessage(m);
      } else {
        System.out.println("Dropping message " + m + " because next hop is dead!");
      }
    }
  }

  /**
   * Marks the associated address as being dead
   *
   * @param address The address to mark dead
   */
  protected void markDead(InetSocketAddress address) {
    if (!dead.contains(address)) {
      dead.add(address);
      pool.update(address, SocketNodeHandle.DECLARED_DEAD);
      //pingManager.resetLastTimePinged(address);
      
      System.out.println("COUNT: " + System.currentTimeMillis() + " Found address " + address + " to be dead.");
    }
  }

  /**
   * Marks the associated address as being alive
   *
   * @param address The address to mark alive
   */
  protected void markAlive(InetSocketAddress address) {
    if (dead.contains(address)) {
      dead.remove(address);
      pool.update(address, SocketNodeHandle.DECLARED_LIVE);
      
      System.out.println("COUNT: " + System.currentTimeMillis() + " Found address " + address + " to be alive again.");
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
   * DESCRIBE THE CLASS
   *
   * @version $Id$
   * @author jeffh
   */
  class DeadChecker extends rice.selector.TimerTask implements PingResponseListener {
    int tries = 1;
    // already called once
    int NUM_TRIES;
    InetSocketAddress address;
    PingManager manager;

    /**
     * Constructor for DeadChecker.
     *
     * @param address DESCRIBE THE PARAMETER
     * @param numTries DESCRIBE THE PARAMETER
     * @param mgr DESCRIBE THE PARAMETER
     */
    public DeadChecker(InetSocketAddress address, int numTries, PingManager mgr) {
      this.address = address;
      manager = mgr;
      this.NUM_TRIES = numTries;
    }

    /**
     * DESCRIBE THE METHOD
     *
     * @param address DESCRIBE THE PARAMETER
     * @param RTT DESCRIBE THE PARAMETER
     * @param timeHeardFrom DESCRIBE THE PARAMETER
     */
    public void pingResponse(InetSocketAddress address,long RTT, long timeHeardFrom) {
      System.out.println("Terminated DeadChecker(" + address + ") due to ping.");
      cancel();
    }

    /**
     * Main processing method for the DeadChecker object
     */
    public void run() {
      if (tries < NUM_TRIES) {
        tries++;
        manager.forcePing(address, this);
      } else {
        markDead(address);
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
    private InetSocketAddress address;
    
    // the address we accepted a connection from
    private InetSocketAddress connectAddress;
    
    // the timer task which is set once we set the 'writable' flag
    private rice.selector.TimerTask timer;
    
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
      this.reader = new SocketChannelReader(pastryNode);
      this.writer = new SocketChannelWriter(pastryNode, null);
      this.bootstrap = false;
      acceptConnection(key);
    }

    /**
     * Constructor which creates an outgoing connection to the given node
     * handle. This creates the connection by building the socket and sending
     * accross the greeting message. Once the response greeting message is
     * received, everything proceeds as normal.
     *
     * @param address DESCRIBE THE PARAMETER
     * @exception IOException DESCRIBE THE EXCEPTION
     */
    public SocketManager(InetSocketAddress address, boolean bootstrap) throws IOException {
      this.reader = new SocketChannelReader(pastryNode);
      this.writer = new SocketChannelWriter(pastryNode, address);
      this.bootstrap = bootstrap;
      createConnection(address);
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
        
        if (timer != null)
          timer.cancel();

        if (address != null) {
          socketClosed(address, this);

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

            if (o instanceof Message) {
              reroute(address, (Message) o);
            }
          }

          address = null;
        }
      } catch (IOException e) {
        System.out.println("ERROR: Recevied exception " + e + " while closing socket!");
      }
    }

    /**
     * The entry point for outgoing messages - messages from here are enqueued
     * for transport to the remote node
     *
     * @param message DESCRIBE THE PARAMETER
     */
    public void send(final Object message) {
      writer.enqueue(message);

      if (key != null) {
        SelectorManager.getSelectorManager().modifyKey(key);
      }
    }

    /**
     * Method which should change the interestOps of the handler's key. This
     * method should *ONLY* be called by the selection thread in the context of
     * a select().
     *
     * @param key The key in question
     */
    public synchronized void modifyKey(SelectionKey key) {
      if ((! writer.isEmpty()) && ((key.interestOps() & SelectionKey.OP_WRITE) == 0)) 
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
        if (((SocketChannel) key.channel()).finishConnect()) {
          // deregister interest in connecting to this socket
          key.interestOps(key.interestOps() & ~SelectionKey.OP_CONNECT);
        }

        markAlive(address);

        debug("Found connectable channel - completed connection");
      } catch (Exception e) {
        debug("Got exception " + e + " on connect - marking as dead");
        System.out.println("Unable to connect to " + address + " (" + e + ") marking as dead.");
        markDead(address);

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
          if (o instanceof InetSocketAddress) {
            if (address == null) {
              this.address = (InetSocketAddress) o;
              socketOpened(address, this);

              markAlive(address);
            } else {
              System.out.println("SERIOUS ERROR: Received duplicate address assignments: " + this.address + " and " + o);
            }
          } else {
            receive((Message) o);
          }
        }
      } catch (IOException e) {
        debug("ERROR " + e + " reading - cancelling.");
        
        if ((address != null) && (address.getPort() != BOOTSTRAP_PORT))
          checkDead(address);
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
          
          if (bootstrap) {
            System.out.println("BOOTSTRAP: DONE SENDING - CLOSING ");
            close();
          }
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
    protected void acceptConnection(SelectionKey serverKey) throws IOException {
      final SocketChannel channel = (SocketChannel) ((ServerSocketChannel) serverKey.channel()).accept();
      channel.socket().setSendBufferSize(SOCKET_BUFFER_SIZE);
      channel.socket().setReceiveBufferSize(SOCKET_BUFFER_SIZE);
      channel.configureBlocking(false);

      debug("Accepted connection from " + channel.socket().getRemoteSocketAddress());
      connectAddress = (InetSocketAddress) channel.socket().getRemoteSocketAddress();
      
      key = SelectorManager.getSelectorManager().register(channel, this, SelectionKey.OP_READ);
    }

    /**
     * Creates the outgoing socket to the remote handle
     *
     * @param address The accress to connect to
     * @exception IOException DESCRIBE THE EXCEPTION
     */
    protected void createConnection(final InetSocketAddress address) throws IOException {
      final SocketChannel channel = SocketChannel.open();
      channel.socket().setSendBufferSize(SOCKET_BUFFER_SIZE);
      channel.socket().setReceiveBufferSize(SOCKET_BUFFER_SIZE);
      channel.configureBlocking(false);

      final boolean done = channel.connect(address);
      this.address = address;

      debug("Initiating socket connection to " + address);

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

      if (bootstrap) {
        send(new InetSocketAddress(InetAddress.getLocalHost(), BOOTSTRAP_PORT));
      } else {
        send(localAddress);
      }
    }

    /**
     * Method which is called once a message is received off of the wire If it's
     * for us, it's handled here, otherwise, it's passed to the pastry node.
     *
     * @param message The receved message
     */
    protected void receive(Message message) {
      if (message instanceof NodeIdRequestMessage) {
        send(new NodeIdResponseMessage(pastryNode.getNodeId()));
      } else if (message instanceof LeafSetRequestMessage) {
        send(new LeafSetResponseMessage(pastryNode.getLeafSet()));
      } else if (message instanceof RouteRowRequestMessage) {
        RouteRowRequestMessage rrMessage = (RouteRowRequestMessage) message;
        send(new RouteRowResponseMessage(pastryNode.getRoutingTable().getRow(rrMessage.getRow())));
      } else {
        if (address != null) {
          long start = System.currentTimeMillis();
          pastryNode.receiveMessage(message);
          System.out.println("ST: " + (System.currentTimeMillis() - start) + " deliver of " + message);
        } else {
          System.out.println("SERIOUS ERROR: Received no address assignment, but got message " + message);
        }
      }
    }

    /**
     * DESCRIBE THE METHOD
     *
     * @param s DESCRIBE THE PARAMETER
     */
    private void debug(String s) {
      if (Log.ifp(8)) {
        System.out.println(pastryNode.getNodeId() + " (SM " + pastryNode.getNodeId() + " -> " + address + "): " + s);
      }
    }
  }
}
