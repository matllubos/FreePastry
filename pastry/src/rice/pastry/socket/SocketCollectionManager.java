/**************************************************************************

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

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.pastry.socket.messaging.*;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.net.*;

/**
 * Class which maintains all outgoing open sockets.  It
 * is responsible for keeping only MAX_OPEN_SOCKETS number
 * of client sockets open at once.  It also binds a ServerSocketChannel
 * to the specified port and listens for incoming connections.
 * Once a connections is established, it uses the interal SocketConnector
 * to read the greeting message (HelloMessage) off of the stream, and hands
 * the connection off to the appropriate node handle.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class SocketCollectionManager implements SelectionKeyHandler {
  
  // the number of sockets where we start closing other sockets
  public static int MAX_OPEN_SOCKETS = 40;
  
  // the size of the buffers for the socket
  public static int SOCKET_BUFFER_SIZE = 32768;
  
  // the ping throttle, or how often to actually ping a remote node
  public static int PING_THROTTLE = 10000;
  
  // the selector manager which the collection manager uses
  private SelectorManager manager;

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
  
  // the list of the last time a ping was sent
  private Hashtable pingtimes;
  
  // the list of cached ping values
  private Hashtable pings;
  
  // the list of cached dead addresses
  private HashSet dead;

  /**
   * Constructs a new SocketManager.
   *
   * @param node The pastry node this manager is serving
   * @param port The port number which this manager is listening on
   * @param selector The Selector this manager should register with
   */
  public SocketCollectionManager(SocketPastryNode node, SocketNodeHandlePool pool, int port, final SelectorManager manager) {
    this.pastryNode = node;
    this.port = port;
    this.pool = pool;
    this.manager = manager;
    queue = new LinkedList();
    sockets = new Hashtable();
    pings = new Hashtable();
    pingtimes = new Hashtable();
    dead = new HashSet();

    try {
      this.localAddress = new InetSocketAddress(InetAddress.getLocalHost(), port);
      
      // bind to port
      final ServerSocketChannel channel = ServerSocketChannel.open();
      channel.configureBlocking(false);
      channel.socket().bind(localAddress);

      final SocketCollectionManager socketCollectionManager = this;
      manager.invoke(new Runnable() {
        public void run() {
          try {
            key = channel.register(manager.getSelector(), SelectionKey.OP_ACCEPT);
            key.attach(socketCollectionManager);
          } catch (IOException e) {
            System.out.println("ERROR registering server socket " + e);
          }
        }
      });
    } catch (IOException e) {
      System.out.println("ERROR creating server socket channel " + e);
    }
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
   * Method which initiates a ping to the remote node.  Once the ping is complete,
   * the result will be available via the proximity() call.
   *
   * @param address The address to ping
   */
  public void ping(InetSocketAddress address) {
    if ((pingtimes.get(address) == null) || 
        (System.currentTimeMillis() - ((Long) pingtimes.get(address)).longValue() > PING_THROTTLE)) {
      pingtimes.put(address, new Long(System.currentTimeMillis()));
      send(address, new PingMessage()); 
    }
  }
  
  /**
   * Method which sends a message across the wire.
   *
   * @param handle The handle to send to
   * @param message The message to send
   */
  public void send(InetSocketAddress address, Message message) {
    synchronized (sockets) {
      if (! sockets.containsKey(address)) {
        debug("No connection open to " + address + " - opening one");
        openSocket(address);
      } 
      
      if (sockets.containsKey(address)) {
        //debug("Found connection open to " + address + " - sending now");
        
        ((SocketManager) sockets.get(address)).send(message);
        socketUpdated(address);
      } else {
        debug("ERROR: Could not connection to remote address " + address + " rerouting message " + message);
        reroute(address, message);
      }
    }
  }
  
  /**
   * Method which returns the last cached proximity value for the given address.  If there is no
   * cached value, then DEFAULT_PROXIMITY is returned.
   *
   * @param address The address to return the value for
   */
  public int proximity(InetSocketAddress address) {
    Integer i = (Integer) pings.get(address);
    
    if (i == null)
      return SocketNodeHandle.DEFAULT_PROXIMITY;
    
    return i.intValue();
  }
  
  /**
   * Method which returns the last cached liveness value for the given address.  If there is no
   * cached value, then true is returned.
   *
   * @param address The address to return the value for
   */
  public boolean isAlive(InetSocketAddress address) {
    return (! dead.contains(address));
  }

  /**
   * Method which is designed to be called by node handles when they wish
   * to open a socket to their remote node.  This method will determine if another
   * node handle needs to disconnect, and will disconnect the ejected node handle
   * if necessary.
   *
   * @param handle The handle which wishes to open a connection
   */
  protected void socketOpened(InetSocketAddress address, SocketManager manager) {
    synchronized (sockets) {
      if (! sockets.containsKey(address)) {
        sockets.put(address, manager);
        queue.addFirst(address);
        
     //   System.out.println("RESOLVE\tOPEN\t" + localAddress.getPort() + "\t" + address.getPort());
        
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
   //       System.out.println("RESOLVE\tCLOSE INIT\t" + localAddress.getPort() + "\t" + address.getPort());
          SocketManager toClose = (SocketManager) sockets.get(address);

          socketClosed(address, toClose);
          socketOpened(address, manager);
          toClose.close();
        } else {
          debug("RESOLVE: Cancelling new connection to " + address);
     //     System.out.println("RESOLVE\tCLOSE RECV\t" + localAddress.getPort() + "\t" + address.getPort());
        }
      }
    }
  }

  /**
   * Method which is designed to be called *ONCE THE SOCKET HAS BEEN CLOSED*.  
   * This method simply updates the bookeeping, but does not actually close the
   * socket.
   *
   * @param handle The handle which has been disconnected.
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
   * @param handle The node handle which has activity.
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
   * @param handle The handle to open a socket to
   */
  protected void openSocket(InetSocketAddress address) {
    try {
      synchronized (sockets) {
        if (! sockets.containsKey(address)) {
          socketOpened(address, new SocketManager(address));
        } else {
          debug("SERIOUS ERROR: Request to open socket to already-open socket to " + address);
        }
      }
    } catch (IOException e) {
      System.out.println("GOT ERROR " + e + " OPENING SOCKET!");
      closeSocket(address);
    }
  }
  
  /**
   * Method which cloeses a socket to a given remote node handle, and updates the
   * bookkeeping to keep track of this closing
   *
   * @param handle The handle to close the socket to
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
   * Reroutes the given message.  If this node is alive, send() is called.  If this node is not alive and
   * the message is a route message, it is rerouted.  Otherwise, the message is dropped.
   *
   * @param m The message
   */
  protected void reroute(InetSocketAddress address, Message m) {    
    if (isAlive(address)) {
      debug("Attempting to resend message " + m + " to alive address " + address);
      send(address, m);
    } else {
      if (m instanceof RouteMessage) {
        debug("Attempting to reroute route message " +m);
        ((RouteMessage) m).nextHop = null;
        pastryNode.receiveMessage(m);
      } else {
        debug("Dropping message " + m + " because next hop is dead!");
      }
    }
  }
  
  /**
   * Marks the associated address as being dead
   *
   * @param address The address to mark dead
   */
  protected void markDead(InetSocketAddress address) {
    if (! dead.contains(address)) { 
      dead.add(address);
      pool.update(address, SocketNodeHandle.DECLARED_DEAD);
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
    }
  }
  
  /**
   * Specified by the SelectionKeyHandler interface.  Is called whenever a key
   * has become acceptable, representing an incoming connection.
   *
   * This method will accept the connection, and attach a SocketConnector in order
   * to read the greeting off of the channel. Once the greeting has been read, the
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
   * Specified by the SelectionKeyHandler interface - is called whenever a key
   * has data available.  The appropriate SocketConnecter is informed, and is told
   * to read the data.
   *
   * @param key The key which is readable.
   */
  public void read(SelectionKey key) {
    System.out.println("PANIC: read() called on SocketCollectionManager!");
  }

  /**
   * Specified by the SelectionKeyHandler interface - should NEVER be called!
   *
   * @param key The key which is writable.
   */
  public void write(SelectionKey key) {
    System.out.println("PANIC: write() called on SocketCollectionManager!");
  }

  /**
   * Specified by the SelectionKeyHandler interface - should NEVER be called!
   *
   * @param key The key which is connectable.
   */
  public void connect(SelectionKey key) {
    System.out.println("PANIC: connect() called on SocketCollectionManager!");
  }

  private void debug(String s) {
    if (Log.ifp(8))
      System.out.println(pastryNode.getNodeId() + " (SCM): " + s);
  }

  /**
   * Private class which is tasked with reading the greeting message off of
   * a newly connected socket.  This greeting message says who the socket is
   * coming from, and allows the connected to hand the socket off the appropriate
   * node handle.
   */
  private class SocketManager implements SelectionKeyHandler {

    // the key to read from
    private SelectionKey key;

    // the reader reading data off of the stream
    private SocketChannelReader reader;

    // the writer (in case it is necessary)
    private SocketChannelWriter writer;
    
    // the node handle we're talking to
    private InetSocketAddress address;
    
    /**
     * Private constructor which builds the socket channel reader and
     * writer, as well other bookkeeping objects for this socket manager.
     */
    private SocketManager() {
      reader = new SocketChannelReader(pastryNode);
      writer = new SocketChannelWriter(pastryNode);
    }

    /**
     * Constructor which accepts an incoming connection, represented by 
     * the selection key.  This constructor builds a new SocketManager, and
     * waits until the greeting message is read from the other end.  Once the 
     * greeting is received, the manager makes sure that a socket for this 
     * handle is not already open, and then proceeds as normal.
     *
     * @param key The server accepting key for the channel
     */
    public SocketManager(SelectionKey key) throws IOException {
      this();
      acceptConnection(key);
    }
    
    /**
     * Constructor which creates an outgoing connection to the given node handle.
     * This creates the connection by building the socket and sending accross the 
     * greeting message.  Once the response greeting message is received, everything
     * proceeds as normal.
     *
     * @param handle The handle to establish a connection with
     */
    public SocketManager(InetSocketAddress address) throws IOException {
      this();
      createConnection(address);
    }
    
    /**
     * Accepts a new connection on the given key
     *
     * @param serverKey The server socket key
     */
    protected void acceptConnection(SelectionKey serverKey) throws IOException {
      final SocketChannel channel = (SocketChannel) ((ServerSocketChannel) serverKey.channel()).accept();
      channel.socket().setSendBufferSize(SOCKET_BUFFER_SIZE);
      channel.socket().setReceiveBufferSize(SOCKET_BUFFER_SIZE);
      channel.configureBlocking(false);
        
      debug("Accepted connection from " + address);
      
      final SocketManager socketManager = this;
      manager.invoke(new Runnable() {
        public void run() {
          try {
            key = channel.register(manager.getSelector(), SelectionKey.OP_READ);
            key.attach(socketManager);
          } catch (IOException e) {
            System.out.println("ERROR registering accepted socket " + e);
          }
        }
      });
    }
    
    /**
     * Creates the outgoing socket to the remote handle
     *
     * @param address The accress to connect to
     */
    protected void createConnection(final InetSocketAddress address) throws IOException {
      final SocketChannel channel = SocketChannel.open();
      channel.socket().setSendBufferSize(SOCKET_BUFFER_SIZE);
      channel.socket().setReceiveBufferSize(SOCKET_BUFFER_SIZE);
      channel.configureBlocking(false);
      
      final boolean done = channel.connect(address);
      this.address = address;
      
      debug("Initiating socket connection to " + address);
      
      final SocketManager socketManager = this;
      manager.invoke(new Runnable() {
        public void run() {
          try {
            if (done) {
              key = channel.register(manager.getSelector(), SelectionKey.OP_READ);
            
              if (! key.isValid()) {
                markDead(address);
                throw new IOException("Invalid key after connect - remote node dead!");
              }
            } else {
              key = channel.register(manager.getSelector(), SelectionKey.OP_READ | SelectionKey.OP_CONNECT);
            }
          
            key.attach(socketManager);
          } catch (IOException e) {
            System.out.println("ERROR registering server socket " + e);
          }
        }
      });
      
      send(localAddress);
    }
    
    /**
     * Method which closes down this socket manager, by closing the socket, cancelling the
     * key and setting the key to be interested in nothing
     */
    public void close() {
      try {
        if (key != null) {
          key.channel().close();
          key.cancel();
          key.attach(null);
        }
        
        if (address != null) {
          socketClosed(address, this);
          
          Iterator i = writer.getQueue().iterator();
          writer.reset();
          
          /**
           * Here, if we have not been declared dead, then we attempt to resend
           * the messages.  However, if we have been declared dead, we reroute the
           * route messages via the pastry node, but delete any messages routed
           * directly.
           */
          while (i.hasNext()) {
            Object o = i.next();
            
            if (o instanceof Message) {
              reroute(address, (Message) o);
            }
          }
        }
      } catch (IOException e) {
        System.out.println("ERROR: Recevied exception " + e + " while closing socket!");
      }
    }
    
    /**
     * The entry point for outgoing messages - messages from here are enqueued for
     * transport to the remote node
     *
     * @param Message message
     */
    public void send(final Object message) {
      writer.enqueue(message); 
            
      manager.invoke(new Runnable() {
        public void run() {
          if (key.isValid()) {
            key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
          } else {
            debug("ERROR: Unvalid key in write - while sending message " + message);
            
            if (message instanceof Message)
              reroute(address, (Message) message);
          }
        }
      });
    }
    
    /**
     * Method which is called once a message is received off of the wire
     * If it's for us, it's handled here, otherwise, it's passed to the
     * pastry node.
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
      } else if (message instanceof PingMessage) {
        send(new PingResponseMessage(((PingMessage) message).getStartTime()));
      } else if (message instanceof PingResponseMessage) {
        int time = (int) (System.currentTimeMillis() - ((PingResponseMessage) message).getStartTime());
        
        if ((pings.get(address) == null) || (((Integer) pings.get(address)).intValue() > time)) {
          pings.put(address, new Integer(time));
          pool.update(address, SocketNodeHandle.PROXIMITY_CHANGED);
        }
      } else {
        if (address != null) {
          pastryNode.receiveMessage(message);
        } else 
          System.out.println("SERIOUS ERROR: Received no address assignment, but got message " + message);
      }
    }
    
    /**
     * Specified by the SelectionKeyHandler interface.  Is called whenever a key
     * has become acceptable, representing an incoming connection.
     *
     * @param key The key which is acceptable.
     */
    public void accept(SelectionKey key) {
      System.out.println("PANIC: read() called on SocketCollectionManager!");
    }
    
    /**
     * Specified by the SelectionKeyHandler interface - calling this tells this socket manager
     * that the connection has completed and we can now read/write.
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
        close();
      }
    }

    /**
     * Writes to the socket attached to this socket manager.
     *
     * @param key The selection key for this manager
     */
    public void write(SelectionKey key) {
      try {
        if (writer.write((SocketChannel) key.channel())) {
          key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
        }
      } catch (IOException e) {
        debug("ERROR " + e + " writing - cancelling.");
        close();
      }
    }

    private void debug(String s) {
      if (Log.ifp(8))
        System.out.println(pastryNode.getNodeId() + " (SM " + pastryNode.getNodeId() + " -> " + address + "): " + s);
    }
  }
}
