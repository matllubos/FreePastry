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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;

import rice.pastry.Log;
import rice.pastry.messaging.Message;
import rice.pastry.routing.RouteMessage;
import rice.pastry.socket.messaging.AckMessage;
import rice.pastry.socket.messaging.AddressMessage;
import rice.pastry.socket.messaging.SocketTransportMessage;
import rice.pastry.testing.HelloMsg;
//import rice.pastry.testing.HelloMsg;

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
 * As opposed to previous versions of Socket and Wire, any message that makes it 
 * to this point is considered "in-flight" and is not recalled.  Messages that
 * are queued here are attempting to be "nageled" together into the same packet.
 *
 * @author Alan Mislove, Jeff Hoye
 */
public class SocketChannelWriter {

  public boolean wroteOnce = false;
	private static Object statLock = new Object();
  private static HashMap msgTypes = new HashMap();
  private static int numWrites = 0;
  private static int numWriteModulo = 1000;
  private static boolean logWriteTypes = false;

  // the pastry node
  private SocketPastryNode spn;

  // internal buffer for storing the serialized object
  private ByteBuffer buffer;

  // internal list of objects waiting to be written
  private LinkedList queue;

  private SocketManager manager;

  /**
   *  the maximum length of the queue
   */
  public static int MAXIMUM_QUEUE_LENGTH = 128;

  // the magic number array which is written first
  /**
   * DESCRIBE THE FIELD
   */
  protected static byte[] MAGIC_NUMBER = new byte[]{0x77, 0x21, 0x25, 0x67};

  InetSocketAddress address;

  /**
   * Constructor which creates this SocketChannelWriter with a pastry node and
   * an object to write out.
   *
   * @param spn The spn the SocketChannelWriter servers
   */
  public SocketChannelWriter(SocketPastryNode spn, SocketManager sm) {
    this.spn = spn;
    this.manager = sm;
    queue = new LinkedList();
  }

  /**
   * Returns whether or not there are objects in the queue on in writing. If the
   * result is true, it the safe to mark the SelectionKey as not being
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

  long timeEnque = 0;
  Hashtable timeEnq = new Hashtable();

  private void checkPoint(Object m, int state) {
    
    if (m instanceof SocketTransportMessage) {
      m = ((SocketTransportMessage)m).msg;
    }

    if (m instanceof RouteMessage) {
      m = ((RouteMessage)m).unwrap();
    }    

    if (m instanceof HelloMsg) {
      HelloMsg hm = (HelloMsg)m;
      hm.state = state;
      if (state == 1) {
        hm.addReceiver(address);
        //Thread.dumpStack();
      }
    }
/*
    if (m instanceof JoinRequest) {
      System.out.println(m+" at "+state+" "+this);
    }
  */  
  }


  /**
   * Adds an object to this SocketChannelWriter's queue of pending objects to
   * write. This methos is synchronized and therefore safe for use by multiple
   * threads.
   *
   * @param o The object to be written.
   * @return DESCRIBE THE RETURN VALUE
   */
  
  Object firstObject;
  public boolean enqueue(Object o) {
    if (ConnectionManager.LOG_LOW_LEVEL)
      System.out.println("ENQ3:@"+System.currentTimeMillis()+":"+this+":"+o);
    checkPoint(o,103);

    if (firstObject == null) {
      firstObject = o;
//      System.out.println(this+" firstObject = "+firstObject+ " : "+firstObject.getClass().getName());
      if (firstObject instanceof SocketTransportMessage) {
//        Thread.dumpStack();
      }
    }
//    System.out.println("ENQ4:"+manager+".SCW.enqueue()" + o);
    timeEnque = System.currentTimeMillis();
    timeEnq.put(o,new Long(timeEnque));
    synchronized (queue) {
      if (queue.size() < MAXIMUM_QUEUE_LENGTH) {
        addToQueue(o);
        return true;
      } else {
        System.err.println(System.currentTimeMillis()+":"+spn.getNodeId()+"->"+ address + " (W): Maximum TCP queue length reached - message " + o + " will be dropped.");
        return false;
      }
    }
  }

  /**
   * Returns the queue of writes for the remote address
   *
   */
  public void reset() {
    queue = new LinkedList();
    buffer = null;
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
    if (address == null) {
      address = (InetSocketAddress) sc.socket().getRemoteSocketAddress();
    }
    synchronized (queue) {
      if (buffer == null) {
        if (queue.size() > 0) {
          Long lo = (Long)timeEnq.remove(queue.getFirst());
          
          long timeToWrite;
          if (lo != null) {
            timeToWrite = System.currentTimeMillis() - lo.longValue();
          } else {
            timeToWrite = 0;
          }
//          System.out.println("SEN:"+manager+".SCW.write()" + queue.getFirst() + ":"+timeToWrite);
          debug("About to serialize object " + queue.getFirst());
          if (ConnectionManager.LOG_LOW_LEVEL)
            System.out.println("SND:@"+System.currentTimeMillis()+":"+this+":"+queue.getFirst());

          Object first = queue.getFirst();
          checkPoint(first,104);
          buffer = serialize(first);
          if ((first != null) && (buffer == null)) {
            // we got a message that was too big
            queue.removeFirst();
          }
          if (!(first instanceof AddressMessage)) {
            wroteOnce = true;
          }

          //    if (spn != null)
          //      spn.broadcastSentListeners(queue.getFirst(), (InetSocketAddress) sc.socket().getRemoteSocketAddress(), buffer.limit());
        } else {
          return true;
        }
      }

      if (buffer != null) {
        int j = buffer.limit();
        int i = sc.write(buffer);
  
  //      System.out.println("SCW.write():Wrote " + i + " of " + j + " bytes to " + sc.socket().getRemoteSocketAddress());
        debug("Wrote " + i + " of " + j + " bytes to " + sc.socket().getRemoteSocketAddress());
  
        if (buffer.remaining() != 0) {
          return false;
        }
  
        if (spn != null) {
          debug("Finished writing message " + queue.getFirst() + " - queue now contains " + (queue.size() - 1) + " items");
        }
  
        queue.removeFirst();
  
        buffer = null;
      }
/*
      // if there are more objects in the queue, try writing those
      // otherwise, return saying all objects have been written
      if ((manager != null) && (manager.getType() == SocketCollectionManager.TYPE_CONTROL)) {
        if (manager.canWrite()) {
          return write(sc);
        } else {
          return false;
        }
      } else { // mgr == null || TYPE_DATA
        */
        return write(sc);        
      //}
      
    }
  }

  /**
   * Adds an entry into the queue, taking message prioritization into account
   *
   * @param o The feature to be added to the ToQueue attribute
   */
  private void addToQueue(Object o) {
    //System.out.println("ENQ4:@"+System.currentTimeMillis()+":"+this+":"+o);
  	
    if (queue.size() > 2 && !(o instanceof AckMessage)) {
      //System.out.println("SCW.addToQueue("+o+"):"+o.getClass().getName());
      //Thread.dumpStack();
    }
    if (o instanceof Message) {
      boolean priority = ((Message) o).hasPriority();

      if ((priority) && (queue.size() > 0)) {
        for (int i = 1; i < queue.size(); i++) {
          Object thisObj = queue.get(i);

          if ((thisObj instanceof Message) && (!((Message) thisObj).hasPriority())) {
            debug("Prioritizing socket message " + o + " over message " + thisObj);
				    //System.out.println("ENQ5a:@"+System.currentTimeMillis()+":"+this+":"+o);
            queue.add(i, o);
            if (manager!=null) {
              manager.messageEnqueued();            
            }
            return;
          }
        }
      }
    }

    //System.out.println("ENQ5b:@"+System.currentTimeMillis()+":"+this+":"+o);
    queue.addLast(o);
    if (manager!=null) {
      manager.messageEnqueued();            
    }
  }

  int getSize() {
    return queue.size();
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param s DESCRIBE THE PARAMETER
   */
  private void debug(String s) {
    if (Log.ifp(8)) {
      if (spn == null) {
        System.out.println("(W): " + s);
      } else {
        System.out.println(spn.getNodeId() + " (W): " + s);
      }
    }
  }
  
  public String toString() {
    return "SCW for "+manager;
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
  public ByteBuffer serialize(Object o) throws IOException {
    if (o == null) {
      return null;
    }
    if (logWriteTypes) {
      synchronized(statLock) {
        Object newO = o;
        if (newO instanceof SocketTransportMessage) {
          newO = ((SocketTransportMessage)newO).msg;
        }
  
        if (newO instanceof RouteMessage) {
          newO = ((RouteMessage)newO).unwrap();
        }
  
        String oType = newO.getClass().getName();
        
        Integer it = (Integer)msgTypes.get(oType);
        if (it == null) {
          msgTypes.put(oType, new Integer(1));
        } else {
          msgTypes.put(oType, new Integer(it.intValue()+1));        
        }
        numWrites++;
        if (numWrites % numWriteModulo == 0) {
          System.out.println("numWrites = "+numWrites);
          Iterator ii = msgTypes.keySet().iterator();
          while (ii.hasNext()) {
            String s = (String)ii.next();
            System.out.println("  "+s+":"+msgTypes.get(s));
          }
        }
        
      }
    }
    
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      //ObjectOutputStream oos = new XMLObjectOutputStream(new BufferedOutputStream(new GZIPOutputStream(baos)));
      ObjectOutputStream oos = new ObjectOutputStream(baos);

      // write out object and find its length
      oos.writeObject(o);
      oos.close();
      int len = baos.toByteArray().length;

      
      if ((manager != null) &&
          (manager.getType() == ConnectionManager.TYPE_CONTROL) &&
          (len > ConnectionManager.MAX_ROUTE_MESSAGE_SIZE)) {
            manager.messageNotSent(o, len);
            return null;
      }

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
}
