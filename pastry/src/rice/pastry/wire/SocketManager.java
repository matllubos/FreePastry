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

package rice.pastry.wire;

import rice.pastry.*;
import rice.pastry.routing.*;
import rice.pastry.messaging.*;
import rice.pastry.wire.messaging.socket.*;
import rice.pastry.wire.exception.*;

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
public class SocketManager implements SelectionKeyHandler {

  // the pastry node which this manager serves
  private WirePastryNode pastryNode;

  // the number of sockets where we start closing other sockets
  public static int MAX_OPEN_SOCKETS = 256;

  // the linked list of open sockets
  private LinkedList openSockets;

  // maps a SelectionKey -> SocketConnector
  private HashMap connectors;

  // ServerSocketChannel for accepting incoming connections
  private SelectionKey key;

  // the port number this manager is listening on
  private int port;

  /**
   * Constructs a new SocketManager.
   *
   * @param node The pastry node this manager is serving
   * @param port The port number which this manager is listening on
   * @param selector The Selector this manager should register with
   */
  public SocketManager(WirePastryNode node, int port, Selector selector) {
    pastryNode = node;
    openSockets = new LinkedList();
    connectors = new HashMap();
    this.port = port;

    try {
      // bind to port
      InetSocketAddress server = new InetSocketAddress(InetAddress.getLocalHost(), port);

      ServerSocketChannel channel = ServerSocketChannel.open();
      channel.configureBlocking(false);
      channel.socket().bind(server);

      key = channel.register(selector, SelectionKey.OP_ACCEPT);
      key.attach(this);
    } catch (IOException e) {
      System.out.println("ERROR creating server socket channel " + e);
    }
  }

  /**
   * Method which is designed to be called by node handles when they wish
   * to open a socket to their remote node.  This method will determine if another
   * node handle needs to disconnect, and will disconnect the ejected node handle
   * if necessary.
   *
   * @param handle The handle which wishes to open a connection
   */
  public void openSocket(WireNodeHandle handle) {
    synchronized (openSockets) {
      if (! openSockets.contains(handle)) {
        openSockets.addFirst(handle);

        debug("Got request to open socket to " + handle);

        if (openSockets.size() > MAX_OPEN_SOCKETS) {
          WireNodeHandle snh = (WireNodeHandle) openSockets.removeLast();
          snh.disconnect();
        }
      } else {
        System.out.println(pastryNode.getNodeId() + " (SC): ERROR: Request to open already-open socket to " + handle.getAddress());
        (new Exception()).printStackTrace();
      }
    }
  }

  /**
   * Method which is designed to be called by node handles which have been disconnected
   * by the remote node (i.e. they received a DisconnectMessage).
   *
   * @param handle The handle which has been disconnected.
   */
  public void closeSocket(WireNodeHandle handle) {
    synchronized (openSockets) {
      if (openSockets.contains(handle)) {
        openSockets.remove(handle);
      } else {
        System.out.println(pastryNode.getNodeId() + " (SC): ERROR: Request to close non-open socket to " + handle.getAddress());
        (new Exception()).printStackTrace();
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
        System.out.println(pastryNode.getNodeId() + " (SC): ERROR: Request to update non-open socket to " + handle.getAddress());
        (new Exception()).printStackTrace();
      }
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
      SocketChannel channel = (SocketChannel) ((ServerSocketChannel) key.channel()).accept();
      channel.configureBlocking(false);
      SelectionKey clientKey = channel.register(pastryNode.getSelectorManager().getSelector(), SelectionKey.OP_READ);

      debug("Accepted connection from " + channel.socket().getRemoteSocketAddress());

      clientKey.attach(this);
      connectors.put(clientKey, new SocketConnector(clientKey));
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
    SocketConnector connector = (SocketConnector) connectors.get(key);

    debug("Found data to be read from " + ((SocketChannel) key.channel()).socket().getRemoteSocketAddress());

    try {
      connector.read();
    } catch (IOException e) {
      System.out.println("ERROR " + e + " reading connnector - cancelling.");
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
    System.out.println("PANIC: write() called on SocketManager!");
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
  public void wakeup() {}

  private void debug(String s) {
    if (Log.ifp(6))
      System.out.println(pastryNode.getNodeId() + " (SM): " + s);
  }

  /**
   * Private class which is tasked with reading the greeting message off of
   * a newly connected socket.  This greeting message says who the socket is
   * coming from, and allows the connected to hand the socket off the appropriate
   * node handle.
   */
  private class SocketConnector {

    // the key to read from
    private SelectionKey key;

    // a buffer containing the size (number) of the greeting message
    private ByteBuffer sizeBuffer;

    // a buffer containing the greeting message itself
    private ByteBuffer objectBuffer;

    // the size of the greeting message in bytes
    private int objectSize;

    /**
     * Constructor
     *
     * @param key The key to read from
     */
    public SocketConnector(SelectionKey key) {
      this.key = key;
      sizeBuffer = ByteBuffer.allocateDirect(4);
    }

    /**
     * Reads from the socket attached to this connector.  It first reads the first
     * 4 bytes, containing the size of the greeting message.  It then allocates
     * enough space to read the greeting message, and does so.
     */
    public void read() throws IOException {
      if (objectBuffer == null) {
        ((SocketChannel) key.channel()).read(sizeBuffer);
        sizeBuffer.flip();

        if (sizeBuffer.remaining() == 4) {
          // allocate space for the header
          byte[] sizeArray = new byte[4];
          sizeBuffer.get(sizeArray, 0, 4);

          // read the object size
          DataInputStream dis = new DataInputStream(new ByteArrayInputStream(sizeArray));
          objectSize = dis.readInt();

          if (objectSize <= 0) {
            throw new ImproperlyFormattedMessageException("Found message of improper number of bytes - " + objectSize + " bytes");
          }

          debug("Found header of size " + objectSize);

          // allocate the appropriate space
          objectBuffer = ByteBuffer.allocateDirect(objectSize);
        }
      } else {
        ((SocketChannel) key.channel()).read(objectBuffer);
        objectBuffer.flip();

        if (objectBuffer.remaining() == objectSize) {
          byte[] objectArray = new byte[objectSize];
          objectBuffer.get(objectArray);
          ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(objectArray));
          Object o = null;

          try {
            o = ois.readObject();
            HelloMessage hm = (HelloMessage) o;

            debug("Read header message " + hm);

            WireNodeHandle wnh = new WireNodeHandle(hm.getAddress(), hm.getNodeId(), pastryNode);
            wnh = (WireNodeHandle) pastryNode.getNodeHandlePool().coalesce(wnh);

            wnh.setKey(key);
          } catch (ClassCastException e) {
            System.out.println("PANIC: Serialized message was not a pastry message!");
            throw new ImproperlyFormattedMessageException("Message recieved " + o + " was not a pastry message - closing channel.");
          } catch (ClassNotFoundException e) {
            System.out.println("PANIC: Unknown class type in serialized message!");
            throw new ImproperlyFormattedMessageException("Unknown class type in message - closing channel.");
          } catch (InvalidClassException e) {
            System.out.println("PANIC: Serialized message was an invalid class!");
            throw new DeserializationException("Invalid class in message - closing channel.");
          }

          // since we're done, remove this entry
          connectors.remove(key);
        }
      }
    }

    private void debug(String s) {
      if (Log.ifp(6))
        System.out.println(pastryNode.getNodeId() + " (SC): " + s);
    }
  }
}