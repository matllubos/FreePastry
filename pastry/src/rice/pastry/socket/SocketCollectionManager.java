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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.TimerTask;

import rice.pastry.Log;
import rice.pastry.messaging.Message;
import rice.pastry.routing.RouteMessage;
import rice.pastry.socket.messaging.AckMessage;
import rice.pastry.socket.messaging.LeafSetRequestMessage;
import rice.pastry.socket.messaging.LeafSetResponseMessage;
import rice.pastry.socket.messaging.NodeIdRequestMessage;
import rice.pastry.socket.messaging.NodeIdResponseMessage;
import rice.pastry.socket.messaging.RouteRowRequestMessage;
import rice.pastry.socket.messaging.RouteRowResponseMessage;
import rice.pastry.socket.messaging.SocketMessage;

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
public class SocketCollectionManager implements SelectionKeyHandler {

  long PING_DELAY = 3000;
  int NUM_PING_TRIES = 3;

  long ACK_DELAY = 1500;


  // the selector manager which the collection manager uses
  SelectorManager manager;

  // the pastry node which this manager serves
  SocketPastryNode pastryNode;

  // the node handle pool on this node
  private SocketNodeHandlePool pool;

  // the local address of the node
  InetSocketAddress localAddress;

  // the linked list of open sockets
  private LinkedList queue;

  // maps a SelectionKey -> SocketConnector
  private Hashtable sockets;

  // ServerSocketChannel for accepting incoming connections
  private SelectionKey key;

  // the port number this manager is listening on
  private int port;

  /*
   *  / the list of the last time a ping was sent
   *  private Hashtable pingtimes;
   *  / the list of cached ping values
   *  private Hashtable pings;
   */
  // the list of cached dead addresses
  private HashSet dead;

  private PingManager pingManager;

  // the number of sockets where we start closing other sockets
  /**
   * DESCRIBE THE FIELD
   */
  public static int MAX_OPEN_SOCKETS = 40;

  // the size of the buffers for the socket
  /**
   * DESCRIBE THE FIELD
   */
  public static int SOCKET_BUFFER_SIZE = 32768;

  // the ping throttle, or how often to actually ping a remote node
  /**
   * DESCRIBE THE FIELD
   */
  public static int PING_THROTTLE = 300000;

  /**
   * Constructs a new SocketManager.
   *
   * @param node The pastry node this manager is serving
   * @param port The port number which this manager is listening on
   * @param pool DESCRIBE THE PARAMETER
   * @param manager DESCRIBE THE PARAMETER
   * @param pingManager DESCRIBE THE PARAMETER
   */
  public SocketCollectionManager(SocketPastryNode node, SocketNodeHandlePool pool, int port, final SelectorManager manager, PingManager pingManager) {
    this.pingManager = pingManager;
    this.pastryNode = node;
    this.port = port;
    this.pool = pool;
    this.manager = manager;
    queue = new LinkedList();
    sockets = new Hashtable();
    //pings = new Hashtable();
    //pingtimes = new Hashtable();
    dead = new HashSet();

    try {
      this.localAddress = new InetSocketAddress(InetAddress.getLocalHost(), port);

      // bind to port
      final ServerSocketChannel channel = ServerSocketChannel.open();
      channel.configureBlocking(false);
      channel.socket().bind(localAddress);

      final SelectionKeyHandler handler = this;

      manager.invoke(
        new Runnable() {
          public void run() {
            try {
              key = channel.register(manager.getSelector(), SelectionKey.OP_ACCEPT);
              key.attach(handler);
            } catch (IOException e) {
              System.out.println("ERROR creating server socket key " + e);
            }
          }
        });
    } catch (IOException e) {
      System.out.println("ERROR creating server socket channel " + e);
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
    return (!dead.contains(address));
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
   * Method which initiates a ping to the remote node. Once the ping is
   * complete, the result will be available via the proximity() call.
   *
   * @param address The address to ping
   * @param message DESCRIBE THE PARAMETER
   */
  /*
   *  public void ping(InetSocketAddress address) {
   *  if ((pingtimes.get(address) == null) ||
   *  (System.currentTimeMillis() - ((Long) pingtimes.get(address)).longValue() > PING_THROTTLE)) {
   *  pingtimes.put(address, new Long(System.currentTimeMillis()));
   *  send(address, new PingMessage());
   *  }
   *  }
   */
  /**
   * Method which sends a message across the wire.
   *
   * @param message The message to send
   * @param address DESCRIBE THE PARAMETER
   */
  public void send(InetSocketAddress address, Message message) {
    synchronized (sockets) {
      if (!sockets.containsKey(address)) {
        debug("No connection open to " + address + " - opening one");
        openSocket(address);
      }

      SocketManager sm = (SocketManager) sockets.get(address);        
      if (sm != null) {
        //debug("Found connection open to " + address + " - sending now");
        
//        if (sm.isProbing()) {
//          reroute(address, message);
//        } else {
          sm.send(message);        
          socketUpdated(address);
//        }
      } else {
        debug("ERROR: Could not connection to remote address " + address + " rerouting message " + message);
        reroute(address, message);
      }
    }
  }

  /**
   * Method which returns the last cached proximity value for the given address.
   * If there is no cached value, then DEFAULT_PROXIMITY is returned.
   *
   * @param address The address to return the value for
   * @return DESCRIBE THE RETURN VALUE
   */
  public int proximity(InetSocketAddress address) {
    return pingManager.proximity(address);
    /*
     *  Integer i = (Integer) pings.get(address);
     *  if (i == null)
     *  return SocketNodeHandle.DEFAULT_PROXIMITY;
     *  return i.intValue();
     */
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
      new SocketManager(key, this);
    } catch (IOException e) {
      System.out.println("ERROR (accepting connection): " + e);
    }
  }

  /**
   * Method which should change the interestOps of the handler's key. This
   * method should *ONLY* be called by the selection thread in the context of a
   * select().
   *
   * @param key The key in question
   */
  public void modifyKey(SelectionKey key) {
    System.out.println("PANIC: modifyKey() called on SocketCollectionManager!");
  }

  /**
   * Specified by the SelectionKeyHandler interface - is called whenever a key
   * has data available. The appropriate SocketConnecter is informed, and is
   * told to read the data.
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

  /**
   * DESCRIBE THE METHOD
   *
   * @param address DESCRIBE THE PARAMETER
   */
  protected void checkDead(InetSocketAddress address) {
    if (address == null) {
      System.out.println("checkDead called with null address");
      return;
    }
    
    DeadChecker checker = new DeadChecker(address, NUM_PING_TRIES, pingManager);
    pastryNode.scheduleTask(checker, PING_DELAY, PING_DELAY);
    pingManager.forcePing(address, checker);
  }

  /**
   * Method which is designed to be called by node handles when they wish to
   * open a socket to their remote node. This method will determine if another
   * node handle needs to disconnect, and will disconnect the ejected node
   * handle if necessary.
   *
   * @param address DESCRIBE THE PARAMETER
   * @param manager DESCRIBE THE PARAMETER
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
   * @param address DESCRIBE THE PARAMETER
   * @param manager DESCRIBE THE PARAMETER
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
   * @param address DESCRIBE THE PARAMETER
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
   * @param address DESCRIBE THE PARAMETER
   */
  protected void openSocket(InetSocketAddress address) {
    try {
      synchronized (sockets) {
        if (!sockets.containsKey(address)) {
          socketOpened(address, new SocketManager(address, this));
        } else {
          debug("SERIOUS ERROR: Request to open socket to already-open socket to " + address);
        }
      }
    } catch (IOException e) {
      System.out.println("GOT ERROR " + e + " OPENING SOCKET!");
      e.printStackTrace();
      closeSocket(address);
    }
  }

  /**
   * Method which cloeses a socket to a given remote node handle, and updates
   * the bookkeeping to keep track of this closing
   *
   * @param address DESCRIBE THE PARAMETER
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
   * @param address DESCRIBE THE PARAMETER
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
    if (!dead.contains(address)) {
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
  class DeadChecker extends TimerTask implements PingResponseListener {
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
      //System.out.println("Registered DeadChecker("+address+")");
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
    public void pingResponse(
                             InetSocketAddress address,
                             long RTT,
                             long timeHeardFrom) {
      //System.out.println("Terminated DeadChecker(" + address + ") due to ping.");
      cancel();
    }

    /**
     * Main processing method for the DeadChecker object
     */
    public void run() {
      if (tries < NUM_TRIES) {
        //System.out.println("DeadChecker("+address+") pinging again."+tries);
        tries++;
        manager.forcePing(address, this);
      } else {
        //System.out.println("DeadChecker("+address+") marking node dead.");
        markDead(address);
        cancel();
      }
    }
  }


}
