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
import rice.pastry.routing.*;
import rice.pastry.messaging.*;
import rice.pastry.socket.exception.*;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.net.*;

/**
 * Class which maintains all outgoing open sockets.  It
 * is responsible for keeping only maxOpenSockets number
 * of client sockets open at once.  When a nodehandle wishes
 * to write to a specific node, it simply needs to call
 * the write() method with its InetSocketAddress and Message.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class SocketManager {

  // the port this socketmanager is listening on
  private int port;

  // the selector used
  private Selector selector;

  // the map of keys (address -> key)
  private HashMap keyList;

  // the pastry node
  private SocketPastryNode pastryNode;

  // the queue of writes
  private WriterQueue writerQueue;

  // the list of readers (address -> reader)
  private HashMap readerList;

  // used for testing (simulating killing a node)
  private boolean alive = true;

  // the server channel listening for incoming connections
  private ServerSocketChannel serverChannel;

  // the thread which the SocketManager runs in
  private Thread executionThread;

  /**
   * Constructor.
   *
   * @param node The pastry node this SocketManager is serving
   * @param maxOpenSockets The maximum number of open sockets
   */
  public SocketManager(SocketPastryNode node, int port) {
    this.port = port;
    keyList = new HashMap();
    writerQueue = new WriterQueue();
    readerList = new HashMap();
    pastryNode = node;

    // attempt to create selector
    try {
      selector = Selector.open();
    } catch (IOException e) {
      System.out.println("ERROR (SocketClient): Error creating selector " + e);
    }

    try {
      // bind to the appropriate port
      serverChannel = ServerSocketChannel.open();
      serverChannel.configureBlocking(false);
      InetSocketAddress isa = new InetSocketAddress(port);
      serverChannel.socket().bind(isa);
    } catch (IOException e) {
      System.out.println("PANIC: Error binding socket server to port " + port + ": " + e);
    }
  }

  /**
   * Method by which a new node handle registers with the SocketClient.
   * The registration is in place so that the SocketClient can inform the
   * node handle if it should be marked as alive or dead.  The
   * registration also has the effect of initiating a connection to the
   * remote node.
   *
   * @param nh The node handle being registered.
   * @return Whether or not the registration was successful
   */
  public boolean register(SocketNodeHandle nh) {
    debug("Got node registration from " + nh.getNodeId() + " address " + nh.getAddress());

    // connect to the node, if remote
    if (! nh.getNodeId().equals(pastryNode.getNodeId())) {
      try {
        openSocket(nh);
        return true;
      } catch (IOException e) {
        System.out.println("PANIC: IOException connecting to node handle. " + e);
      }
    }

    return false;
  }

  /**
   * Opens a socket to a specified address, if necessary. The socket will be in
   * non-blocking mode, and a finishConnect() may need to be called on
   * the socket to finish the connection.
   *
   * @param nh The Node Handle representing the address which we need to
   *           connect to.
   */
  private void openSocket(SocketNodeHandle nh) throws IOException {
    SelectionKey key = null;

    // open socket, if necessary
    if (! keyList.containsKey(nh.getAddress())) {
      SocketChannel channel = SocketChannel.open();
      channel.configureBlocking(false);
      boolean done = channel.connect(nh.getAddress());

      debug("Connecting to address " + nh.getAddress() + " with thread " + Thread.currentThread());

      if (done) {
        key = channel.register(selector, SelectionKey.OP_READ, nh);
      } else {
        key = channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_CONNECT, nh);
      }

      keyList.put(nh.getAddress(), key);
    }
  }

  /**
   * Closes the socket to the specified address.  The socket will be closed
   * and will be removed from the open sockets list.
   *
   * @param nh The Node Handle representing the address we need to close.
   */
  private void closeSocket(SocketNodeHandle nh) throws IOException {
    if (keyList.containsKey(nh.getAddress())) {
      debug("Socket to " + nh.getAddress() + " is about to be closed...");

      SelectionKey key = (SelectionKey) keyList.get(nh.getAddress());
      SocketChannel channel = (SocketChannel) key.channel();

      channel.socket().close();
      key.cancel();
      keyList.remove(nh.getAddress());
    } else {
      debug("Socket not open to " + nh.getAddress() + " disconnect request ignored...");
    }
  }

  /**
   * Method by which clients can request that data be written. Calling this
   * method will queue up a request to write the specified data to the
   * specified address.
   *
   * @param address The destination address
   * @param o The object to be written
   * @return Whether or not the write request was accepted
   */
  public boolean write(InetSocketAddress address, Object o) {
    try {
      SocketChannelWriter scos = new SocketChannelWriter(pastryNode, o);

      debug("Enqueueing write request of " + o + " to " + address + " with thread " + Thread.currentThread());

      if (keyList.containsKey(address)) {
        // if we are in the executing thread, we can modify the interestOps of
        // the SelectionKey directly. Otherwise, we must enqueue the write and wait
        // to modify the interestOps
        if (Thread.currentThread().equals(executionThread)) {
          SelectionKey key = (SelectionKey) keyList.get(address);
          key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
        } else {
          selector.wakeup();
        }

        // add write to the queue
        synchronized(writerQueue) {
          writerQueue.add(address, scos);
        }

        return true;
      } else {
        debug("ERROR: Request to write " + o + " to unknown address " + address);
      }
    } catch (SerializationException e) {
      System.out.println("Error during message serialization - cancelling message.");
    } catch (IOException e) {
      System.out.println("IO Error during message serialization - cancelling message.");
    }

    return false;
  }

  /**
   * Utility method for handling and IOException that occurs while talking
   * to a node handle.  This method marks the node as dead, closes the socket,
   * and then attempts to reestablish the connection.  If another error occurs
   * while reestablishing the connection, the node is assumed to be dead, and
   * no further action is taken.
   *
   * @param snh The node handle which caused the exception.
   */
  private void handleIOException(SocketNodeHandle snh) {
    try {
      debug("IOException occurred talking to " + snh + " - closing socket and marking node as dead.");

      snh.markDead();
      closeSocket(snh);
    } catch (IOException e) {
      System.out.println("PANIC: IOException on reconnect to " + snh + " - leaving node as dead. " + e);
    }
  }

  /**
   * Utility method for handling and IOException that occurs while talking
   * to an incoming address.  This method marks cancels the key, and closes the
   * socket.
   *
   * @param key The SelectionKey representing the socket which caused the problem.
   */
  private void handleIOException(SelectionKey key) {
    try {
      SocketChannel channel = (SocketChannel) key.channel();
      SocketAddress address = channel.socket().getRemoteSocketAddress();

      debug("IOException occurred talking to " + address + " - closing socket.");

      channel.close();
      channel.socket().close();
      key.cancel();
      keyList.remove(address);
    } catch (IOException e) {
      System.out.println("ERROR: IOException on socket close - ignoring.");
    }
  }

  /**
   * This method starts the socket manager listening for incoming connections.
   * It is designed to be started when this thread's start() method is invoked.
   *
   * In this method, the SocketManager blocks while waiting for activity.  It
   * listens on its specified port for incoming connections, listens on client
   * ports for incoming data, and processes any write() requests from pastry
   * node handles.
   */
  public void run() {
    try {
      // Record which thread the SocketManager is running in
      executionThread = Thread.currentThread();

      // Register interest in when data is available (on server port)
      SelectionKey serverKey = serverChannel.register(selector, SelectionKey.OP_ACCEPT);

      debug("Manager starting...");

      // loop while waiting for activity
      while (alive && (selector.select(100) >= 0)) {
        Iterator it = selector.selectedKeys().iterator();

        // ensure that we are interested in writing for each pending write
        // this must be done here b/c interestOps() doesn't work if it is
        // done in the other thread
        synchronized(writerQueue) {
          Iterator i = writerQueue.iterator();

          while (i.hasNext()) {
            InetSocketAddress thisAddress = (InetSocketAddress) i.next();
            SelectionKey key = (SelectionKey) keyList.get(thisAddress);

            if (key != null) {
              if ((key.interestOps() & SelectionKey.OP_WRITE) == 0)
                key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
            }
          }
        }

        // Walk through set
        while (it.hasNext()) {
          SelectionKey key = (SelectionKey) it.next();

          // Remove current entry
          it.remove();

          // if a client wishes to connect, accept the connection
          if (key.isValid() && key.isAcceptable()) {
            try {
              debug("Found incoming connection - accepting connection...");

              // Register to listen for events
              SocketChannel channel = serverChannel.accept();
              channel.configureBlocking(false);
              SelectionKey clientKey = channel.register(selector, SelectionKey.OP_READ);

              // Grab the address
              InetSocketAddress address = (InetSocketAddress) channel.socket().getRemoteSocketAddress();

              // Create new InputStream to read data
              SocketChannelReader scr = new SocketChannelReader(pastryNode);

              keyList.put(address, clientKey);

              // Attach the input stream
              readerList.put(address, scr);

              debug("Done accepting connection from " + address);
            } catch (IOException e) {
              System.out.println("ERROR accepting connection - cancelling. " + e);
              e.printStackTrace();
            }
          } else {
            // Get channel
            SocketChannel channel = (SocketChannel) key.channel();

            // Get node handle (may be null if client-initiated
            SocketNodeHandle snh = (SocketNodeHandle) key.attachment();

            // Grab the address
            InetSocketAddress address = (InetSocketAddress) channel.socket().getRemoteSocketAddress();

            // if a connection needs to be completed, do so
            if (key.isValid() && key.isConnectable()) {
              try {
                // Finish connection
                boolean done = channel.finishConnect();

                if (done) {
                  snh.markAlive();

                  // deregister interest in connecting to this socket
                  key.interestOps(key.interestOps() & ~SelectionKey.OP_CONNECT);
                }

                debug("Found connectable channel - completing connection to " + address);
              } catch (ConnectException e) {
                handleIOException(snh);
              } catch (SocketException e) {
                handleIOException(snh);
              }
            }

            // if data can now be written, do so
            if (key.isValid() && key.isWritable()) {
              debug("Found channel ready for data - writing to " + address);

              snh.markAlive();

              // see if there are any pending writes to this address
              synchronized(writerQueue) {
                if (writerQueue.contains(address)) {
                  Iterator i = writerQueue.get(address);

                  try {
                    while (i.hasNext()) {
                      SocketChannelWriter scw = (SocketChannelWriter) i.next();

                      // if writer is done, remove from the list
                      if (scw.write(channel)) {
                        i.remove();
                      }
                    }

                    if (! writerQueue.get(address).hasNext()) {
                      // if there are no more pending writes, deregister interest
                      // in writing to this address
                      writerQueue.remove(address);
                      key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                    }
                  } catch (IOException e) {
                    handleIOException(snh);
                  }
                } else {
                  // if there are no pending writes, deregister interest in
                  // writing to this address
                  key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                }
              }
            }

            // if data can now be read, do so
            if (key.isValid() && key.isReadable()) {
              SocketChannelReader scr = (SocketChannelReader) readerList.get(address);
              debug("Found data to be read from " + address);

              if (scr != null) {
                try {
                  // inform reader that data is available
                  scr.dataAvailable(channel);
                } catch (ImproperlyFormattedMessageException e) {
                  System.out.println("Improperly formatted message found during parsing - ignoring message...");
                  scr.reset();
                } catch (DeserializationException e) {
                  System.out.println("An error occured during message deserialization - ignoring message...");
                  scr.reset();
                } catch (IOException e) {
                  debug("Error occurred during reading from " + address + " closing socket.");
                  handleIOException(key);
                }
              } else {
                // if we are not aware of this address, we close the socket
                // (when a client-initiated socket becomes readable, it means
                // that the socket is closed.
                debug("Found unrecognized address on read...");
                if (snh != null) {
                  handleIOException(snh);
                }
              }
            }

            // if key is not valid, close socket
            if (! key.isValid()) {
              debug("Found invalid key - closing socket.");

              if (snh == null) {
                handleIOException(key);
              } else {
                handleIOException(snh);
              }
            }
          }
        }
      }
    } catch (IOException e) {
      System.out.println("ERROR (SocketClient:run): " + e);
      e.printStackTrace();
    }

    // close all sockets and keys
    Iterator it = keyList.values().iterator();
    SelectionKey[] keys = new SelectionKey[keyList.values().size()];

    // copy keys into our container
    int i=0;
    while (it.hasNext()) {
      keys[i] = (SelectionKey) it.next();
      i++;
    }

    // kill each socket
    for (int j=0; j<keys.length; j++) {
      handleIOException(keys[j]);
    }

    // kill the selector
    try {
      selector.wakeup();
      selector.close();
    } catch (IOException e) {
      System.out.println("ERROR (SocketClient:run:exit): " + e);
    }
  }

  /**
   * Inner class which maintains a queue of writes waiting on a connection
   * to finish.  It basically maps an InetSocketAddress to a LinkedList of
   * SocketChannelWriters.
   */
  private class WriterQueue {

    // maps address -> linked list
    private HashMap map;

    /**
     * Constructor.
     */
    public WriterQueue() {
      map = new HashMap();
    }

    /**
     * Adds a pending write.
     *
     * @param address The destination address
     * @param scos The output stream for this write.
     * @return Whether or not a connection will need to be established
     */
    public boolean add(InetSocketAddress address, SocketChannelWriter scw) {
      LinkedList l = (LinkedList) map.get(address);
      boolean notPending = (l == null);

      // we have not seen this pending write before
      if (notPending) {
        l = new LinkedList();
        map.put(address, l);
      }

      l.addLast(scw);

      return notPending;
    }

    /**
     * Retrieves and removes all pending writes for a specifed
     * address.
     *
     * @param address The destination address
     * @return An iterator over all of the pending writes.
     */
    public Iterator get(InetSocketAddress address) {
      LinkedList l = (LinkedList) map.get(address);

      if (l == null)
        return null;
      else
        return l.iterator();
    }

    /**
     * Returns whether or not this WriterQueue contains an entry
     * for the specified address.
     *
     * @param address The address to query for.
     * @return Whether or not there is an entry for the address.
     */
    public boolean contains(InetSocketAddress address) {
      return map.containsKey(address);
    }

    /**
     * Returns an interator over all of the address in this
     * WriterQueue.
     *
     * @return An iterator over all the InetSocketAddresses.
     */
    public Iterator iterator() {
      return map.keySet().iterator();
    }

    /**
     * Removes the entry associated with a specified address from
     * the writer queue.
     *
     * @param address The address to remove.
     */
    public void remove(InetSocketAddress address) {
      map.remove(address);
    }
  }

  /**
   * To be used for testing purposes only - kills the socket client
   * by shutting down all outgoing sockets and stopping the client
   * thread.
   */
  public void kill() {
    // mark socketmanager as dead
    alive = false;
    selector.wakeup();
  }

  private void debug(String s) {
    if (Log.ifp(6))
      System.out.println(pastryNode.getNodeId() + " (M): " + s);
  }
}