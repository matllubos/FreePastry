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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SocketChannel;
import java.util.Arrays;

import rice.pastry.Log;
import rice.pastry.PastryObjectInputStream;
import rice.pastry.socket.exception.SocketClosedByRemoteHostException;
import rice.pastry.socket.messaging.AddressMessage;
//import rice.pastry.testing.HelloMsg;

/**
 * Class which serves as an "reader" for messages sent across the wire via the
 * Pastry socket protocol. This class builds up an object as it is being sent
 * across the wire, and when it has recieved all of an object it returns the 
 * object to the caller (usually a SocketManager).
 *
 * @author Alan Mislove, Jeff Hoye
 */
public class SocketChannelReader {

  public boolean readOnce = false;

	// the pastry node
  private SocketPastryNode spn;

  // whether or not the reader has read the message header
  private boolean initialized;

  // the cached size of the message
  private int objectSize = -1;

  // for reading from the socket
  private ByteBuffer buffer;

  // for reading the size of the object (header)
  private ByteBuffer sizeBuffer;

  // for reading the size of the object (header)
  private ByteBuffer magicBuffer;

  // the magic number array which is written first
  /**
   * DESCRIBE THE FIELD
   */
  protected static byte[] MAGIC_NUMBER = SocketChannelWriter.MAGIC_NUMBER;

  private SocketManager manager;

  /**
   * Constructor which creates this SocketChannelReader and the WirePastryNode.
   * Once the reader has completely read a message, it deserializes the message
   * and hands it off to the pastry node.
   *
   * @param spn The PastryNode the SocketChannelReader serves.
   */
  public SocketChannelReader(SocketPastryNode spn, SocketManager sm) {
    this.spn = spn;
    this.manager = sm;
    initialized = false;

    sizeBuffer = ByteBuffer.allocateDirect(4);
    magicBuffer = ByteBuffer.allocateDirect(MAGIC_NUMBER.length);
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
  public Object read(SocketChannel sc) throws IOException {
    if (!initialized) {
      int read = -1;
      try {
        read = sc.read(magicBuffer);
      } catch (NotYetConnectedException nyce) {
        // Note: This happens when we kill the remote node using kill, so throwing a SocketClosed... will be great.
        //System.out.println("SCR:NotYetConnectedException"+manager+","+manager.getStatus());
        //throw nyce;
      }

      if (read == -1) {
        // implies that the channel is closed
        
        throw new SocketClosedByRemoteHostException("Error on read - the channel has been closed.");
//        throw new IOException("Error on read - the channel has been closed.");
      }

      if (magicBuffer.remaining() == 0) {
        magicBuffer.flip();
        verifyMagicBuffer();
      } else {
        return null;
      }
    }

    if (objectSize == -1) {
      int read = sc.read(sizeBuffer);

      if (read == -1) {
        // implies that the channel is closed
        throw new IOException("Error on read - the channel has been closed.");
      }

      if (sizeBuffer.remaining() == 0) {
        sizeBuffer.flip();
        initializeObjectBuffer();
      } else {
        return null;
      }
    }

    if (objectSize != -1) {
      int read = sc.read(buffer);

      debug("Read " + read + " bytes of object..." + buffer.remaining());

      if (read == -1) {
        // implies that the channel is closed
        throw new IOException("Error on read - the channel has been closed.");
      }

      if (buffer.remaining() == 0) {
        buffer.flip();

        byte[] objectArray = new byte[objectSize];
        buffer.get(objectArray);
        buffer = null;
        //   int size = objectSize + MAGIC_NUMBER.length + 4;
        Object obj = deserialize(objectArray);
        debug("Deserialized bytes into object " + obj);
        checkPoint(obj,777);
        if (!(obj instanceof AddressMessage)) {
          readOnce = true;
        }
//        System.out.println("SCR.read():Deserialized bytes into object " + obj);

        //   if (spn != null)
        //     spn.broadcastReceivedListeners(obj, (InetSocketAddress) sc.socket().getRemoteSocketAddress(), size);
        return obj;
      }
    }

    return null;
  }

  public String toString() {
    return "SCR for "+manager;
  }

  private void checkPoint(Object m, int state) {
    /*
    if (m instanceof SocketTransportMessage) {
      m = ((SocketTransportMessage)m).msg;
    }

    if (m instanceof RouteMessage) {
      m = ((RouteMessage)m).unwrap();
    }    

    if (m instanceof HelloMsg) {
      HelloMsg hm = (HelloMsg)m;
      hm.state = state;
    }

    if (m instanceof JoinRequest) {
      System.out.println(m+" at "+state+" "+this);
    }
    */
  }


  /**
   * Resets this input stream so that it is ready to read another object off of
   * the queue.
   */
  public void reset() {
    initialized = false;
    objectSize = -1;

    buffer = null;
    sizeBuffer.clear();
    magicBuffer.clear();
  }

  /**
   * Private method which is designed to verify the magic number buffer coming
   * across the wire.
   *
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  private void verifyMagicBuffer() throws IOException {
    // ensure that there is at least the object header ready to
    // be read
    if (magicBuffer.remaining() == 4) {
      initialized = true;

      // allocate space for the header
      byte[] magicArray = new byte[4];
      magicBuffer.get(magicArray, 0, 4);

      // verify the buffer
      if (!Arrays.equals(magicArray, MAGIC_NUMBER)) {
        System.out.println("Improperly formatted message received - ignoring.");
        throw new IOException("Improperly formatted message - incorrect magic number.");
      }
    }
  }

  /**
   * Private method which is designed to read the header of the incoming
   * message, and prepare the buffer for the object appropriately.
   *
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  private void initializeObjectBuffer() throws IOException {
    // ensure that there is at least the object header ready to
    // be read
    if (sizeBuffer.remaining() == 4) {

      // allocate space for the header
      byte[] sizeArray = new byte[4];
      sizeBuffer.get(sizeArray, 0, 4);

      // read the object size
      DataInputStream dis = new DataInputStream(new ByteArrayInputStream(sizeArray));
      objectSize = dis.readInt();

      if (objectSize <= 0) {
        throw new IOException("Found message of improper number of bytes - " + objectSize + " bytes");
      }

      debug("Found object of " + objectSize + " bytes");

      // allocate the appropriate space
      buffer = ByteBuffer.allocateDirect(objectSize);
    } else {
      // if the header is only partially there, wait for more data to be available
      debug("PANIC: Only " + buffer.remaining() + " bytes in buffer - must wait for 4.");
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
    //ObjectInputStream ois = new XMLObjectInputStream(new BufferedInputStream(new GZIPInputStream(new ByteArrayInputStream(array))));
    ObjectInputStream ois = new PastryObjectInputStream(new ByteArrayInputStream(array), spn);
    Object o = null;

    try {
      reset();

      return ois.readObject();
    } catch (ClassCastException e) {
      System.out.println("PANIC: Serialized message was not a pastry message!");
      throw new IOException("Message recieved " + o + " was not a pastry message - closing channel.");
    } catch (ClassNotFoundException e) {
      System.out.println("PANIC: Unknown class type in serialized message!");
      throw new IOException("Unknown class type in message - closing channel.");
    } catch (InvalidClassException e) {
      System.out.println("PANIC: Serialized message was an invalid class!");
      throw new IOException("Invalid class in message - closing channel.");
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
        System.out.println("(R): " + s);
      } else {
        System.out.println(spn.getNodeId() + " (R): " + s);
      }
    }
  }

	/**
	 * @return true if we are actively downloading an object
	 */
	public boolean isDownloading() {
		return (buffer != null);
	}

}
