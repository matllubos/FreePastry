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
import rice.pastry.wire.messaging.datagram.*;

/**
 * This class is an implementation of a UDP-based Pastry protocol. All messages
 * are sent across the network as datagrams. It uses a TransmissionManager in
 * order to ensure (ordered) delivery.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class DatagramManager implements SelectionKeyHandler {

  // the port number
  private int port;

  // the pastry node
  private WirePastryNode pastryNode;

  // the selector manager
  private SelectorManager manager;

  // a buffer used for read/writing datagrams
  private ByteBuffer buffer;

  // the channel used from talking to the network
  private DatagramChannel channel;

  // the key used to determine what has taken place
  private SelectionKey key;

  // the manager which controls the transmission of data
  private DatagramTransmissionManager transmissionManager;

  // maps address -> ack number (last one seen)
  private HashMap lastAckNum;

  // the queue of pending acks waiting to be sent
  private LinkedList ackQueue;

  // the size of the buffer used to read incoming datagrams
  // must be big enough to encompass multiple datagram packets
  /**
   * DESCRIBE THE FIELD
   */
  public static int DATAGRAM_RECEIVE_BUFFER_SIZE = 131072;

  // the size of the buffer used to send outgoing datagrams
  // this is also the largest message size than can be sent via UDP
  /**
   * DESCRIBE THE FIELD
   */
  public static int DATAGRAM_SEND_BUFFER_SIZE = 65536;

  /**
   * Constructor.
   *
   * @param port The port number this Datagram Manager should run on
   * @param pastryNode DESCRIBE THE PARAMETER
   * @param manager DESCRIBE THE PARAMETER
   */
  public DatagramManager(WirePastryNode pastryNode, SelectorManager manager, int port) {
    this.port = port;
    this.manager = manager;
    this.pastryNode = pastryNode;
    ackQueue = new LinkedList();
    lastAckNum = new HashMap();

    // allocate enought bytes to read a node handle
    buffer = ByteBuffer.allocateDirect(DATAGRAM_SEND_BUFFER_SIZE);

    try {
      // bind to the appropriate port
      channel = DatagramChannel.open();
      channel.configureBlocking(false);
      InetSocketAddress isa = new InetSocketAddress(port);
      channel.socket().bind(isa);
      channel.socket().setSendBufferSize(DATAGRAM_SEND_BUFFER_SIZE);
      channel.socket().setReceiveBufferSize(DATAGRAM_RECEIVE_BUFFER_SIZE);

      Selector selector = manager.getSelector();

      synchronized (selector) {
        key = channel.register(selector, SelectionKey.OP_READ);
      }

      key.attach(this);
    } catch (IOException e) {
      System.out.println("PANIC: Error binding datagram server to port " + port + ": " + e);
      System.exit(-1);
    }

    // instanciate the transmission manager
    transmissionManager = new DatagramTransmissionManager(pastryNode, key);
  }

  /**
   * Designed to be called by a node handle when a socket is open in order to
   * reset the seqence number of UDP.
   *
   * @param node The NodeId to reset.
   */
  public void resetAckNumber(NodeId node) {
    transmissionManager.resetAckNumber(node);
    lastAckNum.remove(node);
  }

  /**
   * Method designed for node handles to use when they wish to write to their
   * remote node. This method enqueues their message, and will eventually send
   * the message to the remote node.
   *
   * @param address The remote address to send the message to.
   * @param o The object that should be sent.
   * @param destination DESCRIBE THE PARAMETER
   */
  public void write(NodeId destination, InetSocketAddress address, Object o) {
    debug("Enqueueing write to " + destination + " of " + o);
    transmissionManager.add(new PendingWrite(destination, address, o));
    manager.getSelector().wakeup();
  }

  /**
   * Specified by the SelectionKeyHandler interface - is called when there is a
   * datagram ready to be read.
   *
   * @param key The SelectionKey which is readable.
   */
  public void read(SelectionKey key) {
    WireNodeHandle handle = null;

    try {
      InetSocketAddress address = null;
      while ((address = (InetSocketAddress) channel.receive(buffer)) != null) {
        debug("Received data from address " + address);
        buffer.flip();

        if (buffer.remaining() > 0) {
          Object o = deserialize(buffer);

          if (o instanceof DatagramMessage) {
            DatagramMessage message = (DatagramMessage) o;

            // make sure message is for us
            if (message.getDestination().equals(pastryNode.getNodeId())) {
              // make sure this handle is in the pool
              handle = ((WireNodeHandlePool) pastryNode.getNodeHandlePool()).get(message.getSource());

              if (handle == null) {
                handle = new WireNodeHandle(address, message.getSource(), pastryNode);
                handle = (WireNodeHandle) pastryNode.getNodeHandlePool().coalesce(handle);
              }

              // if ack, simply record it
              if (o instanceof AcknowledgementMessage) {
                transmissionManager.receivedAck((AcknowledgementMessage) message);
              } else {
                // hand message off to the pastry node
                if (sendAck(address, message)) {
                  if (o instanceof DatagramTransportMessage) {
                    // hand off to pastry node if a transport message is received
                    DatagramTransportMessage dtm = (DatagramTransportMessage) o;

                    pastryNode.receiveMessage((Message) dtm.getObject());
                  } else if (o instanceof PingMessage) {
                    // do nothing (ack has been sent)
                  } else {
                    System.out.println("ERROR: Recieved unreccognized datagrammessage: " + o);
                  }
                }
              }
            } else {
              debug("ERROR: Recieved message " + message + " at " + pastryNode.getNodeId() +
                " for dest " + message.getDestination() + " - dropping on floor.");
            }
          } else {
            System.out.println("ERROR: Received unrecognized message " + o + " at " + pastryNode.getNodeId() +
              " - dropping on floor.");
          }
        } else {
          debug("Read from datagram channel, but no bytes were there - no bad, but wierd.");
        }
      }
    } catch (IOException e) {
      debug("ERROR (datagrammanager:read): " + e);
      e.printStackTrace();
    }
  }

  /**
   * Specified by the SelectionKeyHandler interface - is called when there is
   * space in the DatagramChannel's buffer to write some data.
   *
   * @param key The key which is writable
   */
  public void write(SelectionKey key) {
    try {
      // first, write all pending acks
      Iterator i = ackQueue.iterator();

      while (i.hasNext()) {
        AcknowledgementMessage ack = (AcknowledgementMessage) i.next();

        if (channel.send(serialize(ack), ack.getAddress()) > 0) {
          i.remove();
        } else {
          System.out.println("ERROR: 0 bytes written of ack (not fatal, but bad)");
        }
      }

      // last, write all pending datagrams
      i = transmissionManager.getReady();

      while (i.hasNext()) {
        PendingWrite write = (PendingWrite) i.next();

        int num = channel.send(serialize(write.getObject()), write.getAddress());

        if (num == 0) {
          System.out.println("ERROR: 0 bytes were written (not fatal, but bad) - full buffer.");
        }

        debug("Wrote message " + write.getObject() + " to " + write.getDestination());
      }
    } catch (IOException e) {
      System.out.println("ERROR (datagrammanager:write): " + e);
    }
  }

  /**
   * Called by the SelectorManager whenever it is awoken. This allows the
   * TransmissionManager to check and make sure that if we are waiting to write,
   * we are registered as being interested in writing.
   */
  public void wakeup() {
    // wakeup the transmission manager, which checks to see if any packets have
    // been lost
    transmissionManager.wakeup();

    // if there are pending acks/pings, make sure that we are interested in writing
    if (ackQueue.size() > 0) {
      key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
    }
  }

  /**
   * Specified by the SelectionKeyHandler - should NEVER be called (since
   * datagrams are never accepted).
   *
   * @param key The key that is acceptable.
   */
  public void accept(SelectionKey key) {
    System.out.println("PANIC: accept was called on the DatagramManager!");
  }

  /**
   * Specified by the SelectionKeyHandler - should NEVER be called (since
   * datagrams are never connected).
   *
   * @param key The key that is connectable.
   */
  public void connect(SelectionKey key) {
    System.out.println("PANIC: connect was called on the DatagramManager!");
  }

  /**
   * Method which prepares and enqueues an ack for an incoming message. If we
   * have already seen the incoming data (i.e. it was a retransmission) this
   * method returns false, signifying that the packet should be dropped.
   *
   * @param address The desintation address of the ack.
   * @param message DESCRIBE THE PARAMETER
   * @return DESCRIBE THE RETURN VALUE
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  private boolean sendAck(InetSocketAddress address, DatagramMessage message)
     throws IOException {
    if (channel.send(serialize(message.getAck(address)), address) == 0) {
      ackQueue.add(message.getAck(address));
    }

    Integer num = (Integer) lastAckNum.get(message.getSource());

    // if we have not seen this node before, accept the packet
    if ((num == null) || (num.intValue() < message.getNum())) {
      lastAckNum.put(message.getSource(), new Integer(message.getNum()));
      return true;
    }

    if (num.intValue() > message.getNum()) {
      debug(pastryNode.getNodeId() + " (M): ERROR: Got transmission with ack less than the last ack - ignoring message." +
        " This is probably becuase a socket is being opened, but we haven't yet noticed it.");
    }

    return false;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param s DESCRIBE THE PARAMETER
   */
  private void debug(String s) {
    if (Log.ifp(8)) {
      System.out.println(pastryNode.getNodeId() + " (DM): " + s);
    }
  }

  /**
   * Method which serializes a given object into a ByteBuffer, in order to
   * prepare it for writing.
   *
   * @param o The object to serialize
   * @return A ByteBuffer containing the object
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  public static ByteBuffer serialize(Object o) throws IOException {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);

      // write out object and find its length
      oos.writeObject(o);

      int len = baos.toByteArray().length;
      //System.out.println("serializingD " + o + " len=" + len);

      return ByteBuffer.wrap(baos.toByteArray());
    } catch (InvalidClassException e) {
      System.out.println("PANIC: Object to be serialized was an invalid class!");
      throw new SerializationException("Invalid class during attempt to serialize.");
    } catch (NotSerializableException e) {
      System.out.println("PANIC: Object to be serialized was not serializable! [" + o + "]");
      throw new SerializationException("Unserializable class " + o + " during attempt to serialize.");
    }
  }

  /**
   * Method which takes in a ByteBuffer read from a datagram, and deserializes
   * the contained object.
   *
   * @param buffer The buffer read from the datagram.
   * @return The deserialized object.
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  public static Object deserialize(ByteBuffer buffer) throws IOException {
    int len = buffer.remaining();
    byte[] array = new byte[len];

    // copy the data into the buffer
    buffer.get(array);
    buffer.clear();

    ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(array));

    try {
      Object o = ois.readObject();

      return o;
    } catch (ClassNotFoundException e) {
      System.out.println("PANIC: Unknown class type in serialized message!");
      throw new ImproperlyFormattedMessageException("Unknown class type in message - closing channel.");
    } catch (InvalidClassException e) {
      System.out.println("PANIC: Serialized message was an invalid class!");
      throw new DeserializationException("Invalid class in message - closing channel.");
    }
  }
}
