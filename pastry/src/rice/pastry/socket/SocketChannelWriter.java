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

import rice.pastry.socket.exception.*;
import rice.pastry.routing.*;
import rice.pastry.messaging.*;
import rice.pastry.*;

/**
 * Class which serves as an "writer" for all of the
 * messages sent across the wire in Pastry. This class serializes
 * and properly formats all messages, and then waits to be called
 * with an available SocketChannel in order to write the message
 * out.  If the messagae could not be written in one go, subsequent
 * calls to the write() method will finish writing out the message.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class SocketChannelWriter {

  // the pastry node
  private SocketPastryNode _spn;

  // internal buffer for storing the serialized object
  private ByteBuffer _buffer;

  /**
   * Constructor which creates this SocketChannelWriter with
   * a pastry node and an object to write out.
   *
   * @param spn The PastryNode the SocketChannelWriter servers
   * @param o The object to be written
   */
  public SocketChannelWriter(SocketPastryNode spn, Object o) throws IOException {
    _spn = spn;
    _buffer = serialize(o);
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
    int j = _buffer.limit();
    int i = sc.write(_buffer);

    debug("Wrote " + i + " of " + j + " bytes to " + sc.socket().getRemoteSocketAddress());

    if (i != j) {
      byte[] tmp = new byte[_buffer.remaining()];
      _buffer.get(tmp);
      _buffer = ByteBuffer.wrap(tmp);

      return false;
    } else {
      return true;
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
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);

      // write out object and find its length
      oos.writeObject(o);
      int len = baos.toByteArray().length;

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
      throw new SerializationException("Invalid class during attempt to serialize.");
    } catch (NotSerializableException e) {
      System.out.println("PANIC: Object to be serialized was not serializable!");
      throw new SerializationException("Unserializable class during attempt to serialize.");
    }
  }

  private void debug(String s) {
    if (Log.ifp(6))
      System.out.println(_spn.getNodeId() + " (W): " + s);
  }
}




