/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redist ributions of source code must retain the above copyright
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

import rice.pastry.messaging.*;
import rice.pastry.socket.exception.*;
import rice.pastry.*;

/**
 * Class which serves as an "reader" for messages sent across the wire
 * via the Pastry socket protocol.  This class builds up an
 * object as it is being sent across the wire, and when it
 * has recieved all of an object, it informs the SocketPastryNode by using
 * the recieveMessage(msg) method.  The SocketChannelReader is designed to
 * be reused, to read objects continiously off of one stream.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class SocketChannelReader {

  // the pastry node
  private SocketPastryNode _spn;

  // whether or not the reader has read the message header
  private boolean _initialized;

  // the cached size of the message
  private int _objectSize = -1;

  // an array for building up the object
  private byte[] _objectArray;

  // for reading from the socket
  private ByteBuffer _buffer;

  // a counter of how many bytes we have read
  private int _totalRead = 0;

  /**
   * Constructor which creates this SocketChannelReader and the
   * SocketPastryNode.  Once the reader has completely read a message,
   * it deserializes the message and hands it off to the pastry node.
   *
   * @param spn The PastryNode the SocketServer serves.
   */
  public SocketChannelReader(SocketPastryNode spn) {
    _spn = spn;
    _initialized = false;
    _buffer = ByteBuffer.allocateDirect(1500);
  }

  /**
   * Method which is to be called when there is data available on
   * the specified SocketChannel.  The data is read in, and if
   * the object is done being read, it is parsed.  If there more data
   * on the stream after one object has been read, the SocketChannelReader
   * will reset and begin reading the second object after finishing with
   * the first.
   *
   * @param channel The channel to read from.
   */
  public void dataAvailable(SocketChannel channel) throws IOException {
    // read from the channel
    _buffer.clear();
    int read = channel.read(_buffer);

    debug("Read " + read + " bytes from " + channel.socket().getRemoteSocketAddress());

    if (read == -1) {
      // implies that the channel is closed
      throw new IOException("Error on read - the channel has been closed.");
    } else {
      _buffer.flip();
      readData();
    }
  }

  /**
   * Method which reads data from the interal buffer once it has
   * been copied from the channel.
   */
  private void readData() throws IOException {

    // if not initialized, we must read the size of the object
    if (! _initialized) {

      // ensure that there is at least the object header ready to
      // be read
      if (_buffer.remaining() >= 4) {
        _initialized = true;

        // allocate space for the header
        byte[] sizeArray = new byte[4];
        _buffer.get(sizeArray, 0, 4);

        // read the object size
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(sizeArray));
        _objectSize = dis.readInt();

        if (_objectSize <= 0) {
          throw new ImproperlyFormattedMessageException("Found message of improper number of bytes - " + _objectSize + " bytes");
        }

        // allocate the appropriate space
        _objectArray = new byte[_objectSize];
      } else {
        // if the header is only partially there, wait for more data to be available
        return;
      }
    }

    int len = _buffer.remaining();
    int data_remaining = _objectSize - _totalRead;

    // copy the data into the buffer
    if (data_remaining < len) {
      _buffer.get(_objectArray, _totalRead, data_remaining);
      _totalRead += data_remaining;

      _buffer.mark();
    } else {
      _buffer.get(_objectArray, _totalRead, len);
      _totalRead += len;
    }

    // object is ready to be parsed
    if (_totalRead == _objectSize) {
      performFinalOperations();

      // see if there is another object waiting on the queue
      if (data_remaining < len)
        readData();
    }
  }

  /**
   * Method which parses an object once it is ready, and
   * notifies the pastry node of the message.
   */
  private void performFinalOperations() throws IOException {
    ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(_objectArray));

    try {
      Object o = ois.readObject();
      Message msg = (Message) o;

      debug("Read message " + o + " - passing to pastry node.");

      _spn.receiveMessage(msg);
    } catch (ClassCastException e) {
      System.out.println("PANIC: Serialized message was not a pastry message!");
      throw new ImproperlyFormattedMessageException("Message recieved was not a pastry message - closing channel.");
    } catch (ClassNotFoundException e) {
      System.out.println("PANIC: Unknown class type in serialized message!");
      throw new ImproperlyFormattedMessageException("Unknown class type in message - closing channel.");
    } catch (InvalidClassException e) {
      System.out.println("PANIC: Serialized message was an invalid class!");
      throw new DeserializationException("Invalid class in message - closing channel.");
    }

    reset();
  }

  /**
   * Resets this input stream so that it is ready to read another
   * object off of the queue.
   */
  public void reset() {
    _initialized = false;
    _totalRead = 0;
    _objectSize = -1;
  }

  private void debug(String s) {
    if (Log.ifp(6))
      System.out.println(_spn.getNodeId() + " (R): " + s);
  }

}