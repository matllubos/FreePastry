
package rice.pastry.socket;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.*;
import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;

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
 * @version $Id: SocketChannelWriter.java,v 1.5 2004/03/08 19:53:57 amislove Exp
 *      $
 * @author Alan Mislove
 */
public class SocketChannelWriter {
  
  /**
   * Static fields for logging based on the messages we are writing.
   * Enable logWriteTypes to turn on this output.
   */  
  private static boolean logWriteTypes = true;
  private static Object statLock = new Object();
  private static HashMap msgTypes = new HashMap();
  private static HashMap msgSizes = new HashMap();
  private static long statsLastWritten = System.currentTimeMillis();
  private static long statsWriteInterval = 60 * 1000;
  private static long numWrites = 0;
  
  // the maximum length of the queue
  public static int MAXIMUM_QUEUE_LENGTH = 128;
  
  // the pastry node
  private PastryNode spn;

  // internal buffer for storing the serialized object
  private ByteBuffer buffer;

  // internal list of objects waiting to be written
  private LinkedList queue;
  
  // the address this writer is writing to
  protected SourceRoute path;

  /**
   * Constructor which creates this SocketChannelWriter with a pastry node and
   * an object to write out.
   *
   * @param spn The spn the SocketChannelWriter servers
   */
  public SocketChannelWriter(PastryNode spn, SourceRoute path) {
    this.spn = spn;
    this.path = path;
    queue = new LinkedList();
  }
  
  /**
   * Sets this writer's path
   *
   * @param path The path this writer is using
   */
  protected void setPath(SourceRoute path) {
    this.path = path;
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
    
  /**
   * Adds an object to this SocketChannelWriter's queue of pending objects to
   * write. This methos is synchronized and therefore safe for use by multiple
   * threads.
   *
   * @param o The object to be written.
   * @return DESCRIBE THE RETURN VALUE
   */
  public boolean enqueue(Object o) {
    synchronized (queue) {
      addToQueue(o);

      if (queue.size() > MAXIMUM_QUEUE_LENGTH) {
        Object remove = queue.removeLast();
        System.err.println(spn.getNodeId() + " (W): Maximum TCP queue length reached to " + path + " - message " + remove + " will be dropped.");
        return false;
      } else if (queue.size() > 20) {
        System.err.println(spn.getNodeId() + "  ERROR: Queue to " + path + " has more than 20 elements - probably a bad sign - enqueue of " + o);
        rice.pastry.dist.DistPastryNode.addError("WARNING: Outgoing queue has " + queue.size() + " elements, enqueuing " + o);
      }        
    }

    return true;
  }

  /**
   * Returns the queue of writes for the remote address
   *
   */
  public void reset() {
    queue = new LinkedList();
    buffer = null;
  }
  
  protected void record(String action, Object obj, int size, SourceRoute path) {
		boolean recorded = false;
		
		try {
			if (obj instanceof rice.pastry.routing.RouteMessage) {
				record(action, ((rice.pastry.routing.RouteMessage) obj).unwrap(), size, path);
				recorded = true;
			} else if (obj instanceof rice.pastry.commonapi.PastryEndpointMessage) {
				record(action, ((rice.pastry.commonapi.PastryEndpointMessage) obj).getMessage(), size, path);
				recorded = true;
			} else if (obj instanceof rice.post.messaging.PostPastryMessage) {
				record(action, ((rice.post.messaging.PostPastryMessage) obj).getMessage().getMessage(), size, path);
				recorded = true;
			} 
		} catch (java.lang.NoClassDefFoundError exc) { }

    if (!recorded) {
      if (SocketPastryNode.verbose) System.out.println("COUNT: " + System.currentTimeMillis() + " " + action + " message " + obj.getClass() + " of size " + size + " to " + path);
    }
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
    while (true) {
      synchronized (queue) {
        if (buffer == null) {
          if (! queue.isEmpty()) {
            debug("About to serialize object " + queue.getFirst());
            buffer = serialize(queue.getFirst());
            
            if (buffer != null) {
              if ((spn != null) && (spn instanceof SocketPastryNode))
                ((SocketPastryNode) spn).broadcastSentListeners(queue.getFirst(), (path == null ? new InetSocketAddress[] {(InetSocketAddress) sc.socket().getRemoteSocketAddress()} : path.toArray()), buffer.limit());
            
              record("Sent", queue.getFirst(), buffer.limit(), path);
            } else {
              synchronized (queue) {
                queue.removeFirst();
              }
              
              return write(sc);
            }
          } else {
            return true;
          }
        }
        
        int j = buffer.limit();
        int i = sc.write(buffer);
                
        record("Wrote " + i + " of " + j + " bytes of", queue.getFirst(), buffer.limit(), path);
        
        debug("Wrote " + i + " of " + j + " bytes to " + sc.socket().getRemoteSocketAddress());
        
        if (buffer.remaining() != 0) 
          return false;
        
        if (spn != null) 
          debug("Finished writing message " + queue.getFirst() + " - queue now contains " + (queue.size() - 1) + " items");
        
        synchronized (queue) {
          queue.removeFirst();
          buffer = null;
        }
      }
    }
  }

  /**
   * Adds an entry into the queue, taking message prioritization into account
   *
   * @param o The feature to be added to the ToQueue attribute
   */
  private void addToQueue(Object o) {
    record("Enqueued", o, -1, path);

    if ((queue.size() > 0) && (o instanceof Message)) {
      int i=1;
      
      while (i < queue.size()) {
        Object obj = queue.get(i);

        if ((obj instanceof Message) && (((Message) obj).getPriority() > ((Message) o).getPriority()))
          break;
        
        i++;
      }
      
      if (SocketPastryNode.verbose) System.out.println("COUNT: " + System.currentTimeMillis() + " Enqueueing message " + o.getClass().getName() + " at location " + i + " in the pending queue (priority " + ((Message) o).getPriority() + ")");
    
      queue.add(i, o);
    } else {
      queue.addLast(o);
    }
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
    if (o == null) 
      return null;
    else if (o instanceof byte[]) 
      return ByteBuffer.wrap((byte[]) o);
    
    if (logWriteTypes) {
      synchronized(statLock) {
        long now = System.currentTimeMillis();
        if ((statsLastWritten/statsWriteInterval) != (now/statsWriteInterval)) {
          System.out.println("@L.TR interval="+statsLastWritten+"-"+now+" numWrites="+numWrites);
          statsLastWritten = now;
          
          Iterator ii = msgTypes.keySet().iterator();
          while (ii.hasNext()) {
            String s = (String)ii.next();
            System.out.println("@L.TR   "+s+":"+msgTypes.get(s)+" "+msgSizes.get(s));
          }
          
          msgTypes.clear();
          msgSizes.clear();
        }
      }
    }

    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);

      // write out object and find its length
      oos.writeObject(o);
      oos.close();
      int len = baos.toByteArray().length;
      
      if (logWriteTypes) {
        Object newO = o;
        if (newO instanceof RouteMessage) {
          newO = ((RouteMessage)newO).unwrap();
        }
        
        if (newO instanceof rice.pastry.commonapi.PastryEndpointMessage) {
          newO = ((rice.pastry.commonapi.PastryEndpointMessage)newO).getMessage();
        }
        
        String oType = newO.getClass().getName();
        logMessageSent(oType, len);
      }

      ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
      DataOutputStream dos = new DataOutputStream(baos2);

      // write out length of the object, followed by the object itself
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
    } catch (NullPointerException e) {
      System.out.println("PANIC: Object to be serialized caused null pointer exception! [" + o + "]");
      return null;
    } catch (Exception e) {
      System.out.println("PANIC: Object to be serialized caused excception! [" + e + "]");
      throw new IOException("Exception during attempt to serialize.");
    }
  }
  
  /**
   * Records that a message of a certain type and size has been sent
   * by this node.
   *
   * @param className The name of the message class being sent.
   * @param sizeInBytes The number of bytes after serialization
   */
  static void logMessageSent(String className, int sizeInBytes) {
    synchronized(statLock) {
      Integer it = (Integer)msgTypes.get(className);
      if (it == null) {
        msgTypes.put(className, new Integer(1));
      } else {
        msgTypes.put(className, new Integer(it.intValue()+1));        
      }
      
      Long is = (Long)msgSizes.get(className);
      if (is == null) {
        msgSizes.put(className, new Long(sizeInBytes));
      } else {
        msgSizes.put(className, new Long(is.longValue() + sizeInBytes));
      }
      
      numWrites ++;
    }
  }
}
