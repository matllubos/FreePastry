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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;

import rice.pastry.Log;
import rice.pastry.messaging.Message;
import rice.pastry.wire.exception.NodeIsDeadException;
import rice.pastry.wire.exception.SerializationException;
import rice.pastry.wire.messaging.socket.HelloMessage;
import rice.pastry.wire.messaging.socket.SocketCommandMessage;
import rice.pastry.wire.messaging.socket.SocketTransportMessage;

/**
 * Class which serves as an "writer" for all of the messages sent across the
 * wire in Pastry. This class serializes and properly formats all messages, and
 * then waits to be called with an available SocketChannel in order to write the
 * message out. If the messagae could not be written in one go, subsequent calls
 * to the write() method will finish writing out the message. This class also
 * maintains an internal queue of messages waiting to be sent across the wire.
 * Calling isEmpty() will tell clients if it is safe to mark the SelectionKey as
 * not being interested in writing.
 *
 * @version $Id: SocketChannelWriter.java,v 1.13 2003/10/01 06:35:10 amislove
 *      Exp $
 * @author Alan Mislove
 */
public class SocketChannelWriter {

  // the pastry node
  private WirePastryNode pastryNode;

  // internal buffer for storing the serialized object
  private ByteBuffer buffer;

  // internal list of objects waiting to be written
  private LinkedList queue;

  private SelectionKey key;

  private boolean waitingForGreeting = false;

  // performance helper to remember the state of the selectionKey
  private boolean interestedInWriting = false;

  // the maximum length of the queue
  /**
   * DESCRIBE THE FIELD
   */
  public static int MAXIMUM_QUEUE_LENGTH = 256;

  // the magic number array which is written first
  /**
   * DESCRIBE THE FIELD
   */
  protected static byte[] MAGIC_NUMBER = new byte[]{0x45, 0x79, 0x12, 0x0D};


  /**
   * Constructor which creates this SocketChannelWriter with a pastry node and
   * an object to write out.
   *
   * @param spn The PastryNode the SocketChannelWriter servers
   * @param msg DESCRIBE THE PARAMETER
   * @param key DESCRIBE THE PARAMETER
   */
  public SocketChannelWriter(WirePastryNode spn, SocketCommandMessage msg, SelectionKey key) {
    pastryNode = spn;
    this.key = key;

    try {
      buffer = serialize(msg);
    } catch (IOException e) {
      System.out.println("PANIC: Error serializing message " + msg);
    }

    queue = new LinkedList();

    if (msg != null) {
      if (msg instanceof HelloMessage) {
        waitingForGreeting = true;
      } else {
        synchronized (queue) {
          queue.addLast(msg);
          updateSelectionKeyBasedOnQueue();
        }
      }
    }
  }

  /**
   * Gets the InterestedInWriting attribute of the SocketChannelWriter object
   *
   * @return The InterestedInWriting value
   */
  public boolean isInterestedInWriting() {
    return interestedInWriting;
  }

  /**
   * Returns whether or not there are objects in the queue on in writing. If the
   * result is true, it the safe to mark the SelectionKey as not being
   * interested in writing.
   *
   * @return Whether or not there are objects still to be written.
   */
  public boolean isEmpty() {
    synchronized (queue) {
      return ((buffer == null) && ((queue.size() == 0) || waitingForGreeting));
    }
  }

  /**
   * Returns the queue of writes for the remote address We are only returning
   * the iterator of the queue because we don't actually want to return the
   * queue, as we need to control the SelectionKey state based on the queue. If
   * we give up the queue, we don't know if someone is adding/removing items to
   * it without properly adjusting the selection key state.
   *
   * @return the queue of writes for the remote address
   */
  public Iterator getQueue() {
    return queue.iterator();
  }

  /**
   * Adds an object to this SocketChannelWriter's queue of pending objects to
   * write. This methos is synchronized and therefore safe for use by multiple
   * threads.
   *
   * @param o The object to be written.
   */
  public void enqueue(Object o) {
    synchronized (queue) {
      if (queue.size() < MAXIMUM_QUEUE_LENGTH) {
        addToQueue(o);
        updateSelectionKeyBasedOnQueue();
      } else {
        System.err.println("Maximum TCP queue length reached - message " + o + " will be dropped.");
      }
    }
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @return DESCRIBE THE RETURN VALUE
   */
  public int queueSize() {
    return queue.size();
  }

  /**
   * Resets the SocketChannelWriter, by clearing both the buffer and the queue.
   * Should not be used except when a socket has just been opened.
   *
   * @param msg The greeting message that should be enqueued first
   */
  public void reset(SocketCommandMessage msg) {
    try {
      buffer = serialize(msg);
      greetingReceived();
    } catch (IOException e) {
      System.out.println("PANIC: Error serializing message " + msg);
    }
  }

  /**
   * DESCRIBE THE METHOD
   */
  public void greetingReceived() {
    debug("Greeting has been received - acting normally.");

    waitingForGreeting = false;
  }

  /**
   * Method which is designed to be called when this writer should write out its
   * data. Returns whether or not the message was completely written. If false
   * is returns, write() will need to be called again when the SocketChannel is
   * ready for data to be written.
   *
   * @param sc The SocketChannel to write to
   * @return true if this output stream is done, false otherwise
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  public boolean write(SocketChannel sc) throws IOException {
    IOException ioe = null;
    boolean didWrite = false;
    
    Object ooo = null;
    if (queue.size() > 0) {
      ooo = queue.getFirst();
    } 
    
    try {
      synchronized (queue) {
        if (buffer == null) {
          if ((!waitingForGreeting) && (queue.size() > 0)) {
            debug("About to serialize object " + queue.getFirst());
            buffer = serialize(queue.getFirst());
          } else {
            updateSelectionKeyBasedOnQueue();
            return true;
          }
        }
  
        int j = buffer.limit();
        int i = sc.write(buffer);
  
        debug("Wrote " + i + " of " + j + " bytes to " + sc.socket().getRemoteSocketAddress());
  
        if (buffer.remaining() != 0) {
          return false;
        }
  
        if (!waitingForGreeting) {
          queue.removeFirst();
        }
  
        buffer = null;
  
        // if there are more objects in the queue, try writing those
        // otherwise, return saying all objects have been written
        didWrite = write(sc);
      }
    } catch (IOException e) {
      ioe = e;
    } finally {

      Wire.registerSocketChannel(sc,"write:"+ooo+" ex:"+ioe);

      if (ioe != null) throw ioe;
      return didWrite;
    }
  }

  /**
   * Adds an entry into the queue, taking message prioritization into account
   *
   * @param o The feature to be added to the ToQueue attribute
   */
  private void addToQueue(Object o) {
    if (o instanceof SocketTransportMessage) {
      boolean priority = ((Message) ((SocketTransportMessage) o).getObject()).hasPriority();

      if ((priority) && (queue.size() > 0)) {
        for (int i = 1; i < queue.size(); i++) {
          Object thisObj = queue.get(i);

          if ((thisObj instanceof SocketTransportMessage) &&
            (!((Message) ((SocketTransportMessage) thisObj).getObject()).hasPriority())) {
            debug("Prioritizing socket message " + o + " over message " + thisObj);

            queue.add(i, o);
            return;
          }
        }
      }
    }

    queue.addLast(o);
  }

  /**
   * only stops writing if queue is empty for performance reasons, it is
   * required that the caller is synchronized on the queue
   */
  private void updateSelectionKeyBasedOnQueue() {
    if (queue.size() == 0) {
      if (interestedInWriting) {
        enableWrite(false);
      }
    } else {
      // there are items in the queue
      if (!interestedInWriting) {
        enableWrite(true);
      }
    }
  }

  /**
   * helper method for updateSelectionKeyBasedOnQueue
   *
   * @param write
   */
  private void enableWrite(boolean write) {
    if (key == null) {
      return;
    }
    // WirePastryNodeFactory.generateNodeHandle()
    // can create a blocking version of this (SocketChannelWriter), which doesn't have a key
    // TODO: Get rid of the ability for WirePastryNodeFactory.generateNodeHandle()'s ability to do blocking IO

    if (write) {
      try {
        Selector selector = key.selector();
        synchronized (selector) {
          key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
        }
      } catch (CancelledKeyException cke) {
        SelectorManager selMgr = pastryNode.getSelectorManager();
        if (!selMgr.isAlive()) {
          throw new NodeIsDeadException(cke);
        } else {
          throw cke;
        }
      }
    } else {
      // turn writing off
      Selector selector = key.selector();
      synchronized (selector) {
        key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
      }
    }
    interestedInWriting = write;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param s DESCRIBE THE PARAMETER
   */
  private void debug(String s) {
    if (Log.ifp(8)) {
      if (pastryNode == null) {
        System.out.println("(W): " + s);
      } else {
        System.out.println(pastryNode.getNodeId() + " (W): " + s);
      }
    }
  }

  /**
   * Method which serializes a given object into a ByteBuffer, in order to
   * prepare it for writing. This is necessary because the size of the object
   * must be prepended to the to the front of the buffer in order to tell the
   * reciever how long the object is.
   *
   * @param o The object to serialize
   * @return A ByteBuffer containing the object prepended with its size.
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  public static ByteBuffer serialize(Object o) throws IOException {
    if (o == null) {
      return null;
    }

    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);

      // write out object and find its length
      oos.writeObject(o);
      int len = baos.toByteArray().length;

      //System.out.println("serializingS " + o + " len=" + len);

      ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
      DataOutputStream dos = new DataOutputStream(baos2);

      // write out length of the object, followed by the object itself
      dos.write(MAGIC_NUMBER);
      dos.writeInt(len);
      dos.flush();
      dos.write(baos.toByteArray());
      dos.flush();

      return ByteBuffer.wrap(baos2.toByteArray());
    } catch (InvalidClassException e) {
      System.out.println("PANIC: Object to be serialized was an invalid class!");
      throw new SerializationException("Invalid class during attempt to serialize.");
    } catch (NotSerializableException e) {
      System.out.println("PANIC: Object to be serialized was not serializable! [" + o + "]");
      throw new SerializationException("Unserializable class during attempt to serialize.");
    }
  }
}

