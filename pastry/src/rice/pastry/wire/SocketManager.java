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

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.pastry.wire.exception.*;
import rice.pastry.wire.messaging.socket.*;

/**
 * Class which maintains all outgoing open sockets. It is responsible for
 * keeping only MAX_OPEN_SOCKETS number of client sockets open at once. It also
 * binds a ServerSocketChannel to the specified port and listens for incoming
 * connections. Once a connections is established, it uses the interal
 * SocketConnector to read the greeting message (HelloMessage) off of the
 * stream, and hands the connection off to the appropriate node handle.
 *
 * @author Alan Mislove, Jeff Hoye
 */
public class SocketManager implements SelectionKeyHandler {

  /**
   * the pastry node which this manager serves
   */
  private WirePastryNode pastryNode;

  /**
   * the linked list of open sockets
   */
  protected LinkedList openSockets;

  /**
   * maps a SelectionKey -> SocketConnector
   */
  private HashMap connectors;

  /**
   * ServerSocketChannel for accepting incoming connections
   */
  private ServerSocketChannel serverChannel;

  /**
   * ServerSocketChannels key for accepting incoming connections
   */
  private SelectionKey serverKey;

  /**
   * the port number this manager is listening on
   */
  private int port;

  /**
   * the number of sockets where we start closing other sockets
   */
  public static int MAX_OPEN_SOCKETS = 256;

 
  /**
   * Constructs a new SocketManager.
   *
   * @param node The pastry node this manager is serving
   * @param port The port number which this manager is listening on
   * @param selector The Selector this manager should register with
   */
  public SocketManager(WirePastryNode node, int port, Selector selector) {
    pastryNode = node;
    wireDebug("DBG:SM ctor 1");
    openSockets = generateOpenSockets();
    connectors = new HashMap();
    this.port = port;

    try {
      // bind to port
      InetSocketAddress server = new InetSocketAddress(InetAddress.getLocalHost(), port);

      serverChannel = ServerSocketChannel.open();
      serverChannel.configureBlocking(false);
      serverChannel.socket().bind(server);

      serverKey = serverChannel.register(selector, SelectionKey.OP_ACCEPT);
      serverKey.attach(this);
    } catch (IOException e) {
      System.out.println("ERROR creating server socket channel " + e);
      wireDebug("DBG:SM ctor failure");
    }
    wireDebug("DBG:SM ctor 2");
  }

  /**
   * Can be overloaded.  This returns the list of open sockets
   * that this socketmanager is to act upon.
   * 
   * @return a LinkedList to remember what sockets are open
   */  
  protected LinkedList generateOpenSockets() {
    return new LinkedList();
  }

  /**
   * Method which is designed to be called by node handles when they wish to
   * open a socket to their remote node. This method will determine if another
   * node handle needs to disconnect, and will disconnect the ejected node
   * handle if necessary.
   *
   * @param handle The handle which wishes to open a connection
   */
  public void openSocket(WireNodeHandle handle) {
    synchronized (openSockets) {
      if (!openSockets.contains(handle)) {

        removeOpenSocketsIfNeeded();   

        openSockets.addFirst(handle);
        
        debug("Got request to open socket to " + handle);
        
      } else {
        debug("ERROR: Request to open already-open socket to " + handle.getAddress());
      }
    }
  }
  
  /**
   * called routinely to recycle sockets that haven't recently
   * been used
   *
   */
  protected void removeOpenSocketsIfNeeded() {
    while (needToDisconnectSockets() && openSockets.size() > 0) {             
      WireNodeHandle snh = (WireNodeHandle) openSockets.removeLast();
      System.out.println("Removing Open Soket because needed. :"+snh.getAddress());   
      snh.disconnect();
    }    
  }
  
  /**
   * called to determine if a socket should be closed
   * 
   * @return wether to close a socket
   */
  protected boolean needToDisconnectSockets() {
    return openSockets.size() > MAX_OPEN_SOCKETS;
  }

  /**
   * Method which is designed to be called by node handles which have been
   * disconnected by the remote node (i.e. they received a DisconnectMessage).
   *
   * @param handle The handle which has been disconnected.
   */
  public void closeSocket(WireNodeHandle handle) {
    if (true) return;
    
    System.out.println("SocketManager.closeSocket():"+handle.getAddress()+","+handle.getId());
    synchronized (openSockets) {
      if (openSockets.contains(handle)) {
        openSockets.remove(handle);
      } else {
        debug("ERROR: Request to close non-open socket to " + handle.getAddress());
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
  public void update(WireNodeHandle handle) {
    synchronized (openSockets) {
      if (openSockets.contains(handle)) {
        openSockets.remove(handle);
        openSockets.addFirst(handle);
      } else {
        System.out.println("ERROR: Request to update non-open socket to " + handle.getAddress());
      }
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
      SocketChannel channel = (SocketChannel) ((ServerSocketChannel) key.channel()).accept();
      channel.socket().setSendBufferSize(WireNodeHandle.SOCKET_BUFFER_SIZE);
      channel.socket().setReceiveBufferSize(WireNodeHandle.SOCKET_BUFFER_SIZE);
      channel.configureBlocking(false);

      wireDebug("DBG:"+System.currentTimeMillis()+":accept("+channel.socket().getRemoteSocketAddress()+")");

      Selector selector = pastryNode.getSelectorManager().getSelector();

      synchronized (selector) {
        SelectionKey clientKey = channel.register(selector, SelectionKey.OP_READ);

        debug("Accepted connection from " + channel.socket().getRemoteSocketAddress());

        clientKey.attach(this);
        connectors.put(clientKey, new SocketConnector(clientKey));
      }
    } catch (IOException e) {
      System.out.println("ERROR (accepting connection): " + e);
      wireDebug("DBG:error during accept()");
    }
  }

  /**
   * Specified by the SelectionKeyHandler interface - is called whenever a key
   * has data available. The appropriate SocketConnecter is informed, and is
   * told to read the data.
   *
   * @param key The key which is readable.
   */
  public void read(SelectionKey key) {
    wireDebug("DBG:"+System.currentTimeMillis()+":read()");
    SocketConnector connector = (SocketConnector) connectors.get(key);

    debug("Found data to be read from " + ((SocketChannel) key.channel()).socket().getRemoteSocketAddress());

    try {
      connector.read();
    } catch (IOException e) {
      wireDebug("DBG:exception:"+serverKey+","+key+","+e);
      debug("ERROR " + e + " reading connnector - cancelling.");
      connectors.remove(key);

      try {
        key.channel().close();
      } catch (IOException f) {
        System.out.println("ERROR " + f + " occured while closing socket.");
      }

      key.cancel();
    }
  }

  /**
   * Specified by the SelectionKeyHandler interface - should NEVER be called!
   *
   * @param key The key which is writable.
   */
  public void write(SelectionKey key) {
    SocketConnector connector = (SocketConnector) connectors.get(key);

    debug("Found channel ready for data to " + ((SocketChannel) key.channel()).socket().getRemoteSocketAddress());

    try {
      connector.write();
    } catch (IOException e) {
      debug("ERROR " + e + " writing connnector - cancelling.");
      connectors.remove(key);

      try {
        key.channel().close();
      } catch (IOException f) {
        System.out.println("ERROR " + f + " occured while closing socket.");
      }

      key.cancel();
    }

  }

  /**
   * Specified by the SelectionKeyHandler interface - should NEVER be called!
   *
   * @param key The key which is connectable.
   */
  public void connect(SelectionKey key) {
    System.out.println("PANIC: connect() called on SocketManager!");
  }

  /**
   * Specified by the SelectionKeyHandler interface - does nothing.
   */
  public void wakeup() {
  }

  /**
   * general logging method
   *
   * @param s string to log
   */
  private void debug(String s) {
    if (Log.ifp(8)) {
      System.out.println(pastryNode.getNodeId() + " (SM): " + s);
    }
  }
  
  /**
   * special outputstream for wireDebug()
   */
  transient PrintStream outputStream = null;


  /**
   * This method provides extensive logging service for wire.  
   * It is used to verify that all queued messages are sent and received.
   * This system creates several log files that can be parced by 
   * rice.pastry.wire.testing.WireFileProcessor
   * 
   * @param s String to log.
   */
  void wireDebug(String s) {
    if (!Wire.outputDebug) return;
    synchronized(Wire.outputStreamLock) {
      try {
        if (outputStream == null) {
          String r = null;
          if (pastryNode != null) {
            r = pastryNode.getId().toString();
          }
          String t = "SM "+r+".txt";
          outputStream = new PrintStream(new FileOutputStream(t)); 
        }
        outputStream.println(s);
      } catch (IOException ioe) {
        ioe.printStackTrace();
      }
    }
  }
  
  /**
   * Private class which is tasked with reading the greeting message off of a
   * newly connected socket. This greeting message says who the socket is coming
   * from, and allows the connected to hand the socket off the appropriate node
   * handle.
   *
   * @author Alan Mislove, Jeff Hoye
   */
  private class SocketConnector {

    /**
     * the key to read from
     */
    private SelectionKey key;

    /**
     * the reader reading data off of the stream
     */
    private SocketChannelReader reader;

    /**
     * the writer (in case it is necessary)
     */
    private SocketChannelWriter writer;

    /**
     * Constructor
     *
     * @param key The key to read from
     */
    public SocketConnector(SelectionKey key) {
      this.key = key;
      reader = new SocketChannelReader(pastryNode,null);
    }

    /**
     * Reads from the socket attached to this connector. It first reads the
     * first 4 bytes, containing the size of the greeting message. It then
     * allocates enough space to read the greeting message, and does so.
     *
     * @exception IOException if there is an error reading/deserializing
     */
    public void read() throws IOException {
      Object o = reader.read((SocketChannel) key.channel());
      wireDebug("DBG:sc:read("+o+")");
      if (o != null) {
        if (o instanceof HelloMessage) {
          HelloMessage hm = (HelloMessage) o;
          wireDebug("DBG:read("+hm+")");

          debug("Read header message " + hm);

          if (hm.getDestination().equals(pastryNode.getNodeId())) {
            WireNodeHandle handle = ((WireNodeHandlePool) pastryNode.getNodeHandlePool()).get(hm.getNodeId());

            if (handle == null) {
              handle = new WireNodeHandle(hm.getAddress(), hm.getNodeId(), pastryNode);
              handle = (WireNodeHandle) pastryNode.getNodeHandlePool().coalesce(handle);
            }

            handle.setKey(key, hm.getResponse(pastryNode));
          } else {
            debug("Found socket for wrong nodeId " + hm.getDestination() + " at " + pastryNode.getNodeId() + " - killing.");
            key.interestOps(0);

            key.channel().close();
            key.cancel();
          }

          // since we're done, remove this entry
          connectors.remove(key);
        } else if (o instanceof NodeIdRequestMessage) {
          debug("Read nodeId request message " + o);

          writer = new SocketChannelWriter(pastryNode, null, key);

          writer.enqueue(new NodeIdResponseMessage(pastryNode.getNodeId()));
//          key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
        } else if (o instanceof LeafSetRequestMessage) {
          debug("Read leafset request message " + o);

          writer = new SocketChannelWriter(pastryNode, null, key);

          writer.enqueue(new LeafSetResponseMessage(pastryNode.getLeafSet()));
//          key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
        } else if (o instanceof RouteRowRequestMessage) {
          debug("Read route row request message " + o);

          RouteRowRequestMessage message = (RouteRowRequestMessage) o;
          writer = new SocketChannelWriter(pastryNode, null, key);

          writer.enqueue(new RouteRowResponseMessage(pastryNode.getRoutingTable().getRow(message.getRow())));
//          key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
        
        
        } else {
          System.out.println("Read unknown message " + o + " - dropping on floor.");
        }
      }
    }


    /**
     * @exception IOException if there is an error writing/serializing
     */
    public void write() throws IOException {
      boolean done = writer.write((SocketChannel) key.channel());

      if (done) {
//        key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
        enableWrite(false);
        // since we're done, remove this entry
        connectors.remove(key);
        key.attach(new StaleSKH());
        //key.cancel();
      }
    }

    /**
     * This method controls the key's write interestOp, 
     * and has a parallel boolean to improve performance. 
     * The actual interestOp is only called if the state 
     * changes.
     *
     * @param write new boolean for key's write interestOp
     */
    private void enableWrite(boolean write) {
      if (write) {
        SelectorManager selMgr = pastryNode.getSelectorManager();
        Selector selector = selMgr.getSelector();
        synchronized (selector) {
          key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
        }
      } else {
        SelectorManager selMgr = pastryNode.getSelectorManager();
        Selector selector = selMgr.getSelector();

        synchronized (selector) {
          key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
        }
      }
    }

    /**
     * general logging method
     *
     * @param s string to log
     */
    private void debug(String s) {
      if (Log.ifp(8)) {
        System.out.println(pastryNode.getNodeId() + " (SC): " + s);
      }
    }
  }
}
