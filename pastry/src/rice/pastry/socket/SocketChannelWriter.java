/*************************************************************************

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

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.*;

import rice.pastry.wire.exception.*;
import rice.pastry.routing.*;
import rice.pastry.messaging.*;
import rice.pastry.*;
import rice.pastry.wire.messaging.socket.*;
import rice.serialization.*;

/**
 * Class which serves as an "writer" for all of the
 * messages sent across the wire in Pastry. This class serializes
 * and properly formats all messages, and then waits to be called
 * with an available SocketChannel in order to write the message
 * out.  If the messagae could not be written in one go, subsequent
 * calls to the write() method will finish writing out the message.
 *
 * This class also maintains an internal queue of messages waiting
 * to be sent across the wire. Calling isEmpty() will tell clients if
 * it is safe to mark the SelectionKey as not being interested in writing.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class SocketChannelWriter {

  // the maximum length of the queue
  public static int MAXIMUM_QUEUE_LENGTH = 128;

  // the magic number array which is written first
  protected static byte[] MAGIC_NUMBER = new byte[] {0x77, 0x21, 0x25, 0x67};
  
  // the pastry node
  private SocketPastryNode spn;

  // internal buffer for storing the serialized object
  private ByteBuffer buffer;

  // internal list of objects waiting to be written
  private LinkedList queue;

  /**
   * Constructor which creates this SocketChannelWriter with
   * a pastry node and an object to write out.
   *
   * @param spn The spn the SocketChannelWriter servers
   * @param o The object to be written
   */
  public SocketChannelWriter(SocketPastryNode spn) {
    this.spn = spn;
    queue = new LinkedList();
  }

  /**
   * Adds an entry into the queue, taking message prioritization into account
   *
   * @param write The pending write to add
   */
  private void addToQueue(Object o) {
    if (o instanceof Message) {
      boolean priority = ((Message) o).hasPriority();
      
      if ((priority) && (queue.size() > 0)) {
        for (int i=1; i<queue.size(); i++) {
          Object thisObj = queue.get(i);
          
          if ((thisObj instanceof Message) && (! ((Message) thisObj).hasPriority())) {
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
   * Adds an object to this SocketChannelWriter's queue of pending
   * objects to write.  This methos is synchronized and therefore
   * safe for use by multiple threads.
   *
   * @param o The object to be written.
   */
  public boolean enqueue(Object o) {
    synchronized (queue) {
      if (queue.size() < MAXIMUM_QUEUE_LENGTH) {
        addToQueue(o);
        return true;
      } else {
        System.err.println(spn.getNodeId() + " (W): Maximum TCP queue length reached - message " + o + " will be dropped.");
        return false;
      }
    }
  }

  /**
   * Returns whether or not there are objects in the queue on in writing.
   * If the result is true, it the safe to mark the SelectionKey as not being
   * interested in writing.
   *
   * @return Whether or not there are objects still to be written.
   */
  public boolean isEmpty() {
    return ((buffer == null) && (queue.size() == 0));
  }
  
  /**
    * Returns the queue of writes for the remote address
   *
   * @return the queue of writes for the remote address
   */
  public LinkedList getQueue() {
    return queue;
  }
  
  /**
   * Returns the queue of writes for the remote address
   *
   * @return the queue of writes for the remote address
   */
  public void reset() {
    queue = new LinkedList();
    buffer = null;
  }

  /**
   * Method which is designed to be called when this
   * writer should write out its data. Returns whether or not the
   * message was completely written. If false is returns, write() will
   * need to be called again when the SocketChannel is ready for data
   * to be written.
   *
   * @param sc The SocketChannel to write to
   * @return true if this output stream is done, false otherwise
   */
  public boolean write(SocketChannel sc) throws IOException {
    synchronized (queue) {
      if (buffer == null) {
        if (queue.size() > 0) {
          debug("About to serialize object " + queue.getFirst());
          buffer = serialize(queue.getFirst());
          
      //    if (spn != null)
      //      spn.broadcastSentListeners(queue.getFirst(), (InetSocketAddress) sc.socket().getRemoteSocketAddress(), buffer.limit());
        } else {
          return true;
        }
      }

      int j = buffer.limit();
      int i = sc.write(buffer);

      debug("Wrote " + i + " of " + j + " bytes to " + sc.socket().getRemoteSocketAddress());

      if (buffer.remaining() != 0) {
        return false;
      }
      
      if (spn != null) 
        debug("Finished writing message " + queue.getFirst() + " - queue now contains " + (queue.size() - 1) + " items");
        
      queue.removeFirst();

      buffer = null;

      // if there are more objects in the queue, try writing those
      // otherwise, return saying all objects have been written
      return write(sc);
    }
  }

  /**
   * Method which serializes a given object into a ByteBuffer,
   * in order to prepare it for writing.  This is necessary
   * because the size of the object must be prepended to the
   * to the front of the buffer in order to tell the reciever
   * how long the object is.
   *
   * @param o The object to serialize
   * @return A ByteBuffer containing the object prepended with
   *         its size.
   */
  public static ByteBuffer serialize(Object o) throws IOException {
    if (o == null) return null;

    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      //ObjectOutputStream oos = new XMLObjectOutputStream(new BufferedOutputStream(new GZIPOutputStream(baos)));
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      
      // write out object and find its length
      oos.writeObject(o);
      oos.close();
      int len = baos.toByteArray().length;

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
      throw new IOException("Invalid class during attempt to serialize.");
    } catch (NotSerializableException e) {
      System.out.println("PANIC: Object to be serialized was not serializable! [" + o + "]");
      throw new IOException("Unserializable class during attempt to serialize.");
    }
  }

  private void debug(String s) {
    if (Log.ifp(8)) {
      if (spn == null) {
        System.out.println("(W): " + s);
      } else {
        System.out.println(spn.getNodeId() + " (W): " + s);
      }
    }
  }
}