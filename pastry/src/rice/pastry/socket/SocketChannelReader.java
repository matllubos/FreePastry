
package rice.pastry.socket;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;

import rice.*;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.pastry.*;
import rice.pastry.messaging.Message;

/**
 * Class which serves as an "reader" for messages sent across the wire via the
 * Pastry socket protocol. This class builds up an object as it is being sent
 * across the wire, and when it has recieved all of an object, it informs the
 * WirePastryNode by using the recieveMessage(msg) method. The
 * SocketChannelReader is designed to be reused, to read objects continiously
 * off of one stream.
 *
 * @version $Id: SocketChannelReader.java,v 1.5 2004/03/08 19:53:57 amislove Exp
 *      $
 * @author Alan Mislove
 */
public class SocketChannelReader {
  
  // the maximal message size to be deserialized on the selector thread
  public int SELECTOR_DESERIALIZATION_MAX_SIZE;

  // the pastry node
  private PastryNode spn;

  // the cached size of the message
  private int objectSize = -1;

  // for reading from the socket
  private ByteBuffer buffer;

  // for reading the size of the object (header)
  private ByteBuffer sizeBuffer;
  
  // the address this reader is reading from
  protected SourceRoute path;
  
  // the environment to use
  protected Environment environment;

  protected Logger logger;
  
  /**
   * Constructor which creates this SocketChannelReader and the WirePastryNode.
   * Once the reader has completely read a message, it deserializes the message
   * and hands it off to the pastry node.
   *
   * @param spn The PastryNode the SocketChannelReader serves.
   */
  public SocketChannelReader(PastryNode spn, SourceRoute path) {
    this(spn.getEnvironment(), path);
    this.spn = spn;
  }
  
  public SocketChannelReader(Environment env, SourceRoute path) {
    this.environment = env;
    this.path = path;
    this.logger = env.getLogManager().getLogger(SocketChannelReader.class, null);
    sizeBuffer = ByteBuffer.allocateDirect(4);
    SELECTOR_DESERIALIZATION_MAX_SIZE = environment.getParameters().getInt(
        "pastry_socket_reader_selector_deserialization_max_size");
  }
  
  /**
   * Sets this reader's path
   *
   * @param path The path this reader is using
   */
  protected void setPath(SourceRoute path) {
    this.path = path;
  }
  
  /**
   * Method which is to be called when there is data available on the specified
   * SocketChannel. The data is read in, and if the object is done being read,
   * it is parsed.
   *
   * @param sc The channel to read from.
   * @return The object read off the stream, or null if no object has been
   *      completely read yet
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  public Object read(final SocketChannel sc) throws IOException {
    if (objectSize == -1) {
      int read = sc.read(sizeBuffer);

      // implies that the channel is closed
      if (read == -1) 
        throw new IOException("Error on read - the channel has been closed.");

      if (sizeBuffer.remaining() == 0)
        initializeObjectBuffer(sc);
      else
        return null;
    }

    if (objectSize != -1) {
      int read = sc.read(buffer);

      if (logger.level <= Logger.FINEST) logger.log(
          "(R) Read " + read + " bytes of object..." + buffer.remaining());

      // implies that the channel is closed
      if (read == -1) 
        throw new ClosedChannelException();

      if (buffer.remaining() == 0) {
        buffer.flip();

        final byte[] objectArray = new byte[objectSize];
        buffer.get(objectArray);
        final int size = objectSize + 4;
        reset();

        if (size < SELECTOR_DESERIALIZATION_MAX_SIZE) {
          Object obj = deserialize(objectArray);
          
          if (obj != null) {
            if (logger.level <= Logger.FINER) logger.log(
                "(R) Deserialized bytes into object " + obj);
            
            if ((spn != null) && (spn instanceof SocketPastryNode))
              ((SocketPastryNode) spn).broadcastReceivedListeners(obj, (path == null ? (InetSocketAddress) sc.socket().getRemoteSocketAddress() : path.getLastHop().address), size, NetworkListener.TYPE_TCP);

            record(obj, size, path);
            
            return obj;
          }
        } else {
          if (logger.level <= Logger.INFO) logger.log(
              "COUNT: Read message, but too big to deserialize on Selector thread");
          ((SocketPastryNode) spn).process(new Executable() {
            public String toString() { return "Deserialization of message of size " + size + " from " + path; }
            public Object execute() {
              if (logger.level <= Logger.INFO) logger.log(
                  "COUNT: Starting deserialization on message on processing thread");
              try {
                return deserialize(objectArray);
              } catch (Exception e) {
                return e;
              }
            }
          }, new Continuation() {
            public void receiveResult(Object o) {
              if ((spn != null) && (spn instanceof SocketPastryNode))
                ((SocketPastryNode) spn).broadcastReceivedListeners(o, (path == null ? (InetSocketAddress) sc.socket().getRemoteSocketAddress() : path.getLastHop().address), size, NetworkListener.TYPE_TCP);
              
              record(o, size, path);
              
              if (o instanceof Message) 
                spn.receiveMessage((Message) o);
              else
                receiveException((Exception) o);
            }
            
            public void receiveException(Exception e) {
              if (logger.level <= Logger.WARNING) logger.logException(
                  "Processing deserialization of message caused exception ", e);
            }
          });
        }
      }
    }

    return null;
  }
  
  protected void record(Object obj, int size, SourceRoute path) {
		boolean recorded = false;
		try {
			if (obj instanceof rice.pastry.routing.RouteMessage) {
				record(((rice.pastry.routing.RouteMessage) obj).unwrap(), size, path);
				recorded = true;
			} else if (obj instanceof rice.pastry.commonapi.PastryEndpointMessage) {
				record(((rice.pastry.commonapi.PastryEndpointMessage) obj).getMessage(), size, path);
				recorded = true;
//			} else if (obj instanceof rice.post.messaging.PostPastryMessage) {
//				record(((rice.post.messaging.PostPastryMessage) obj).getMessage().getMessage(), size, path);
//				recorded = true;
			}
		} catch (java.lang.NoClassDefFoundError exc) { }

    if (!recorded) {
      if (logger.level <= Logger.FINER) logger.log(
          "COUNT: Read message " + obj.getClass() + " of size " + size + " from " + path);
    }
  }

  /**
   * Resets this input stream so that it is ready to read another object off of
   * the queue.
   */
  public void reset() {
    objectSize = -1;

    buffer = null;
    sizeBuffer.clear();
  }

  /**
   * Private method which is designed to read the header of the incoming
   * message, and prepare the buffer for the object appropriately.
   *
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  private void initializeObjectBuffer(SocketChannel sc) throws IOException {
    // flip the buffer
    sizeBuffer.flip();

    // allocate space for the header
    byte[] sizeArray = new byte[4];
    sizeBuffer.get(sizeArray, 0, 4);
    
    // read the object size
    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(sizeArray));
    objectSize = dis.readInt();
    
    if (objectSize <= 0) 
      throw new IOException("Found message of improper number of bytes - " + objectSize + " bytes");
    
    if (logger.level <= Logger.FINER) logger.log(
        "(R) Found object of " + objectSize + " bytes from "+sc.socket().getRemoteSocketAddress());
    
    // allocate the appropriate space
    try {
      buffer = ByteBuffer.allocateDirect(objectSize);
    } catch (OutOfMemoryError oome) {
      if (logger.level <= Logger.SEVERE) logger.logException(
          "SCR ran out of memory allocating an object of size "+objectSize+" from "+path, oome);
      throw oome; 
    }
  }

  /**
   * Method which parses an object once it is ready, and notifies the pastry
   * node of the message.
   *
   * @param array DESCRIBE THE PARAMETER
   * @return the deserialized object
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  private Object deserialize(byte[] array) throws IOException {
    ObjectInputStream ois = new PastryObjectInputStream(new ByteArrayInputStream(array), spn);
    Object o = null;

    try {
      Object ret = ois.readObject();

      return ret;
    } catch (ClassCastException e) {
      if (logger.level <= Logger.SEVERE) logger.log(
          "PANIC: Serialized message was not a pastry message!");
      throw new IOException("Message recieved " + o + " was not a pastry message - closing channel.");
    } catch (ClassNotFoundException e) {
      if (logger.level <= Logger.SEVERE) logger.log(
          "PANIC: Unknown class type in serialized message!");
      throw new IOException("Unknown class type in message - closing channel.");
    } catch (InvalidClassException e) {
      if (logger.level <= Logger.SEVERE) logger.log(
          "PANIC: Serialized message was an invalid class! " + e.getMessage());
      throw new IOException("Invalid class in message - closing channel.");
    } catch (IllegalStateException e) {
      if (logger.level <= Logger.SEVERE) logger.log(
          "PANIC: Serialized message caused an illegal state exception! " + e.getMessage());
      throw new IOException("Illegal state from deserializing message - closing channel.");
    } catch (NullPointerException e) {
      if (logger.level <= Logger.SEVERE) logger.logException(
          "PANIC: Serialized message caused a null pointer exception! " , e);
      
      return null;
    } catch (Exception e) {
      if (logger.level <= Logger.SEVERE) logger.log(
          "PANIC: Serialized message caused exception! " + e.getMessage());
      throw new IOException("Exception from deserializing message - closing channel.");
    }
  }
}
