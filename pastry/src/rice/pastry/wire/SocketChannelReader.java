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

package rice.pastry.wire;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

import rice.pastry.messaging.*;
import rice.pastry.wire.exception.*;
import rice.pastry.wire.messaging.socket.*;
import rice.pastry.*;

/**
 * Class which serves as an "reader" for messages sent across the wire
 * via the Pastry socket protocol.  This class builds up an
 * object as it is being sent across the wire, and when it
 * has recieved all of an object, it informs the WirePastryNode by using
 * the recieveMessage(msg) method.  The SocketChannelReader is designed to
 * be reused, to read objects continiously off of one stream.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class SocketChannelReader {

  // the magic number array which is written first
  protected static byte[] MAGIC_NUMBER = SocketChannelWriter.MAGIC_NUMBER;

  // the pastry node
  private WirePastryNode spn;

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

  /**
   * Constructor which creates this SocketChannelReader and the
   * WirePastryNode.  Once the reader has completely read a message,
   * it deserializes the message and hands it off to the pastry node.
   *
   * @param spn The PastryNode the SocketChannelReader serves.
   */
  public SocketChannelReader(WirePastryNode spn) {
    this.spn = spn;
    initialized = false;

    sizeBuffer = ByteBuffer.allocateDirect(4);
    magicBuffer = ByteBuffer.allocateDirect(MAGIC_NUMBER.length);
  }

  /**
   * Method which is to be called when there is data available on
   * the specified SocketChannel.  The data is read in, and if
   * the object is done being read, it is parsed.
   *
   * @param sc The channel to read from.
   * @return The object read off the stream, or null if no object has
   *         been completely read yet
   */
  public Object read(SocketChannel sc) throws IOException {
    if (! initialized) {
      int read = sc.read(magicBuffer);

      if (read == -1) {
        // implies that the channel is closed
        throw new IOException("Error on read - the channel has been closed.");
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

      if (read == -1)  {
        // implies that the channel is closed
        throw new IOException("Error on read - the channel has been closed.");
      }

      if (buffer.remaining() == 0) {
        buffer.flip();

        byte[] objectArray = new byte[objectSize];
        buffer.get(objectArray);
        return deserialize(objectArray);
      }
    }

    return null;
  }

  /**
   * Private method which is designed to verify the magic number buffer
   * coming across the wire.
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
      if (! Arrays.equals(magicArray, MAGIC_NUMBER)) {
        System.out.println("Improperly formatted message received - ignoring.");
        throw new IOException("Improperly formatted message - incorrect magic number.");
      }
    }
  }
  
  /**
   * Private method which is designed to read the header of the incoming
   * message, and prepare the buffer for the object appropriately.
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
        throw new ImproperlyFormattedMessageException("Found message of improper number of bytes - " + objectSize + " bytes");
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
   * Method which parses an object once it is ready, and
   * notifies the pastry node of the message.
   *
   * @return the deserialized object
   */
  private Object deserialize(byte[] array) throws IOException {
    ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(array));
    Object o = null;

    try {
      reset();
      return ois.readObject();
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
  }

  /**
   * Resets this input stream so that it is ready to read another
   * object off of the queue.
   */
  public void reset() {
    initialized = false;
    objectSize = -1;

    buffer = null;
    sizeBuffer.clear();
    magicBuffer.clear();
  }

  private void debug(String s) {
    if (Log.ifp(8)) {
      if (spn == null) {
        System.out.println("(R): " + s);
      } else {
        System.out.println(spn.getNodeId() + " (R): " + s);
      }
    }
  }

}