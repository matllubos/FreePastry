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
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.util.Collection;
import java.util.Iterator;
import java.util.TimerTask;
import java.util.WeakHashMap;

import rice.pastry.Log;
import rice.pastry.NodeHandle;
import rice.pastry.messaging.Message;
import rice.pastry.routing.RouteMessage;
import rice.pastry.socket.exception.TooManyMessagesException;
import rice.selector.SelectionKeyHandler;
import rice.selector.SelectorManager;

/**
 * Class which maintains all outgoing open sockets. It holds a ConnectionManager 
 * for each active connection, and it uses the SocketPoolManager to keep 
 * only MAX_OPEN_SOCKETS number of client sockets open at once. It also
 * binds a ServerSocketChannel to the specified port and listens for incoming
 * connections, builds a SocketManager, then hands the SocketManager to an appropriate
 * ConnectionManager. 
 *
 * You can think of the SocketCollectionManager as the only thing that ScketNodeHandle
 * communicates with, and is almost the most the external api of the Socket 
 * package to the rest of pastry.
 *  
 * @author Alan Mislove, Jeff Hoye
 */
public class SocketCollectionManager extends SelectionKeyHandler {

	// the selector manager which the collection manager uses
  //SelectorManager manager;

  // the pastry node which this manager serves
  SocketPastryNode pastryNode;

  // the node handle pool on this node
  private SocketNodeHandlePool pool;

  protected SocketPoolManager socketPoolManager;
  
  // maps an SocketNodeHandle -> ConnectionManager
  private WeakHashMap connections;

  // ServerSocketChannel for accepting incoming connections
  private SelectionKey key;

  // the port number this manager is listening on
  private int port;

  private PingManager pingManager;

  private InetSocketAddress bindAddress;

  /**
   * the size of the buffers for the socket
   */
  public static int SOCKET_BUFFER_SIZE = 32768;

  /**
   * Constructs a new SocketManager.
   *
   * @param node The pastry node this manager is serving
   * @param port The port number which this manager is listening on
   * @param pool DESCRIBE THE PARAMETER
   * @param manager DESCRIBE THE PARAMETER
   * @param pingManager DESCRIBE THE PARAMETER
   */
  public SocketCollectionManager(SocketPastryNode node, SocketNodeHandlePool pool, int port, PingManager pingManager, InetSocketAddress bindAddress) throws BindException {
    this.pingManager = pingManager;
    this.pastryNode = node;
    this.port = port;
    this.pool = pool;
    this.bindAddress = bindAddress;
    pool.scm = this;
    connections = new WeakHashMap();
    
    try {

      // bind to port
      final ServerSocketChannel channel = ServerSocketChannel.open();
      channel.configureBlocking(false);
      boolean failedOnce = false;
      channel.socket().bind(bindAddress); // can throw bind exception
      
      this.socketPoolManager = new SocketPoolManager(this);

      final boolean fFailedOnce = failedOnce;
      SelectorManager.getSelectorManager().invoke(
        new Runnable() {
          public void run() {
            if (fFailedOnce) System.out.println("SCM.ctor:4");
            try {
              key = SelectorManager.getSelectorManager().register(channel, SocketCollectionManager.this, SelectionKey.OP_ACCEPT);
              key.attach(SocketCollectionManager.this);
            } catch (IOException e) {
              System.out.println("ERROR creating server socket key " + e);
            }
            if (fFailedOnce) System.out.println("SCM.ctor:6");
          }
        });      
    } catch (BindException be) {
      throw be;
    } catch (IOException e) {
      System.out.println("ERROR creating server socket channel " + e);      
    }
  }

  public void kill() {
    SelectorManager.getSelectorManager().invoke(new Runnable() {
      public void run() {
        try {
          key.channel().close();
        } catch (IOException ioe) {
          ioe.printStackTrace();
        }
        
        Iterator i = getConnectionManagers().iterator();
        while (i.hasNext()) {
          ConnectionManager cm = (ConnectionManager)i.next();
          cm.close();
        }
        
      }
    });
  }

  /**
   * Method which returns the last cached liveness value for the given address.
   * If there is no cached value, then true is returned.
   *
   * @param address The address to return the value for
   * @return The Alive value
   */
  public boolean isAlive(SocketNodeHandle snh) {
    return ((ConnectionManager)connections.get(snh)).getLiveness() < NodeHandle.LIVENESS_FAULTY;    
  }

  /**
   * Prints out the list of all open sockets
   *
   * @return The list of all open sockets
   */
  public String toString() {
    String result = "";

    Iterator i = connections.keySet().iterator();
    while (i.hasNext()) {
      result += i.next().toString() + "\n";
    }

    return result;
  }

//  public void addConnectionManager(SocketNodeHandle snh) {
////    System.out.println("SNH:"+snh);
////    System.out.println("SNH.getId():"+snh.getId());
////    System.out.println("connections:"+connections);
//    
//    ConnectionManager cm = (ConnectionManager)connections.get(snh.getId());
//    if (cm == null) {
//      cm = new ConnectionManager(this, snh);
//      connections.put(snh.getId(), cm);
//    }        
//  }

  /**
   * Returns the ConnectionManager for that address.  If one doesn't exist,
   * it creates a new one.
   * 
   * @param address The address that corresponds to the ConnectionManager
   * @return the ConnectionManager correcsponding to the address.
   */
//  public ConnectionManager getConnectionManager(InetSocketAddress address, int foo) {
  public ConnectionManager getConnectionManager(SocketNodeHandle snh) {
    if (!pastryNode.isAlive()) return null; // keep from creating new connection managers while we are shutting the rest down
    ConnectionManager cm = (ConnectionManager)connections.get(snh);
    if (cm == null) {
      cm = new ConnectionManager(this, snh);
      connections.put(snh, cm);
    }
    return cm;
  }
  
  public Collection getConnectionManagers() {
    return connections.values();
  }
  
  /**
   * Method which sends a message across the wire.
   *
   * @param message The message to send
   * @param address the address to send the message
   */
  public void send(SocketNodeHandle snh, Message message) throws TooManyMessagesException {
//    public void send(InetSocketAddress address, Message message) throws TooManyMessagesException {
    if (!pastryNode.isAlive()) return;
    getConnectionManager(snh).send(message);
  }

  /**
   * Method which returns the last cached proximity value for the given address.
   * If there is no cached value, then DEFAULT_PROXIMITY is returned.
   *
   * @param address The address to return the value for
   * @return RTT time in millis 
   */
  public int proximity(SocketNodeHandle snh) {
    return pingManager.proximity(snh);
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
    if (acceptorKey != null) {
//      numTimesNotRemoveKey++;                
      disableAccept(); // gets enabled when we acceptSocket()
    }
    acceptorKey = key; // gets set back to null in acceptSocket()
    socketPoolManager.requestAccept(); // calls acceptSocket() now or later
  }

  public void doAccept(SelectionKey key) {
    try {
      socketPoolManager.socketOpened(new SocketManager(key, this));
    } catch (IOException e) {
      if (ConnectionManager.LOG_LOW_LEVEL)
        System.out.println("ERROR (accepting connection): " + e + " at "+addressString());
    }    
  }

  public boolean waitingToAccept() {
    return acceptorKey != null;
  }
  
  /**
   * Schedules a task to be called every "period" after "delay"
   * @param task The task to run.
   * @param delay How long to wait to run it the first time.
   * @param period How long to wait to run it after the last time it was run.
   */
  protected void scheduleTask(TimerTask task, long delay, long period) {
    try {
      pastryNode.scheduleTask(task, delay, period);  
    } catch (IllegalStateException ise) {
      if (pastryNode.isAlive()) {
        throw ise;
      }
    }
  }
  
  /**
   * Schedules a task to be called once after "delay"
   * @param task The task to run.
   * @param delay How long to wait to run it.
   */
  protected void scheduleTask(TimerTask task, long delay) {
    try {
      pastryNode.scheduleTask(task, delay);
    } catch (IllegalStateException ise) {
      if (pastryNode.isAlive()) {
        throw ise;
      }
    }
  }
  
  /**
   * Reroutes the given message. If this node is alive, send() is called. If
   * this node is not alive and the message is a route message, it is rerouted.
   * Otherwise, the message is dropped.
   *
   * @param m The message
   * @param address the address the message came from
   */
  protected void reroute(RouteMessage m) {
    if (m instanceof RouteMessage) {
      debug("Attempting to reroute route message " + m);
      ((RouteMessage) m).nextHop = null;
      pastryNode.receiveMessage(m);
    } else {
      debug("Dropping message " + m + " because next hop is dead!");
    }    
  }

  /**
   * Marks the associated address as being dead
   *
   * @param address The address to mark dead
   */
  protected void markDead(SocketNodeHandle snh) {
    pool.update(snh, SocketNodeHandle.DECLARED_DEAD);
  }

  /**
   * Marks the associated address as being alive
   *
   * @param address The address to mark alive
   */
  protected void markAlive(SocketNodeHandle snh) {
    pool.update(snh, SocketNodeHandle.DECLARED_LIVE);
  }

  /**
   * Log debugging trace
   *
   * @param s the trace to log
   */
  void debug(String s) {
    if (Log.ifp(8)) {
      System.out.println(pastryNode.getNodeId() + " (SCM): " + s);
    }
  }


	/**
	 * @param address the address to find liveness info for
	 * @return NodeHandle.LIVENESS_ALIVE, LIVENESS_SUSPECTED(_FAULTY), LIVENESS_FAULTY, LIVENESS_UNKNOWN
	 */
	public int getLiveness(SocketNodeHandle snh) {
    ConnectionManager cm = (ConnectionManager)connections.get(snh);
    if (cm != null) {
      return cm.getLiveness();
    }
		return 0;
	}

	/**
	 * The SocketPoolManager for this node.
	 */
	public SocketPoolManager getSocketPoolManager() {
		return socketPoolManager;
	}

	/**
   * Called by a SocketManager when it receives the address message from a new
   * socket.  Calls accept on the appropriate ConnectionManager.
	 * @param manager
	 */
  public boolean newSocketManager(SocketNodeHandle snh, SocketManager manager) {
//    public void newSocketManager(InetSocketAddress address, SocketManager manager) {
    if (!pastryNode.isAlive()) return false;
    getConnectionManager(snh).acceptSocket(manager);
    return true;
	}

	/**
	 * @return The PingManager for this node.
	 */
	public PingManager getPingManager() {
		return pingManager;
	}

	/**
	 * 
	 */
	public void disableAccept() {
    key.interestOps(key.interestOps() & ~SelectionKey.OP_ACCEPT);
	}

	/**
	 * 
	 */
	public void enableAccept() {
    key.interestOps(key.interestOps() | SelectionKey.OP_ACCEPT);
	}

	/**
	 * @return
	 */
	public String addressString() {
    InetSocketAddress a = ((SocketNodeHandle)pastryNode.getLocalHandle()).getAddress();
    if (a.equals(bindAddress)) {
      return bindAddress.toString();
    }
    return a+"@"+bindAddress;      
	}
  
  public Iterator getConnections() {
    return connections.values().iterator();
  }

  public SocketNodeHandle getLocalNodeHandle() {
    return (SocketNodeHandle)pastryNode.getLocalHandle();
  }

	/**
	 * 
	 */
	public InetSocketAddress getAddress() {
		return getLocalNodeHandle().getAddress();		
	}

  SelectionKey acceptorKey = null;
  
  public void acceptSocket() {
    if (!pastryNode.isAlive()) return;
    enableAccept();
    SelectionKey tempKey = acceptorKey;
    acceptorKey = null;

    boolean removeKey = false;
    if (tempKey != null) {
      SelectionKeyHandler skh = (SelectionKeyHandler)tempKey.attachment();
      if (skh != null && tempKey.isValid() && tempKey.isAcceptable()) {
        doAccept(tempKey);
      }
    
    /*
        if (skh.accept(tempKey)) {
          removeKey = true;
        } 
      } else {
        removeKey = true;      
      }
  
      if (removeKey) {
        selector.selectedKeys().remove(tempKey);                                      
      }*/
    }
  }


}
