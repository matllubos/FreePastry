package org.mpisws.p2p.transport;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

import rice.p2p.commonapi.rawserialization.InputBuffer;

/**
 * Used to receive incoming messages/sockets.
 * 
 * @author Jeff Hoye
 *
 * @param <Identifier>
 * @param <MessageType>
 */
public interface TransportLayerCallback<Identifier, MessageType> {
  /**
   * Called when a new message is received.
   * 
   * @param i The node it is coming from
   * @param m the message
   * @param options describe how the message arrived (udp/tcp, encrypted etc)
   * @throws IOException if there is a problem decoding the message
   */
  public void messageReceived(Identifier i, MessageType m, Map<String, Integer> options) throws IOException;
  /**
   * Notification of a new socket.
   * 
   * @param s the incoming socket
   * @throws IOException
   */
  public void incomingSocket(P2PSocket<Identifier> s) throws IOException;
}
