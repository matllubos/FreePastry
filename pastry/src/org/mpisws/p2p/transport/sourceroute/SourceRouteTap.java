package org.mpisws.p2p.transport.sourceroute;

import java.nio.ByteBuffer;

import org.mpisws.p2p.transport.P2PSocket;

/**
 * Notified of messages sent to intermediate nodes.
 * 
 * @author Jeff Hoye
 */
public interface SourceRouteTap {
  /**
   * We are the intermediate node for a message.
   * 
   * @param m
   * @param path
   */
  public void receivedMessage(ByteBuffer m, SourceRoute path);
  
  /**
   * 
   * @param path
   * @param a
   * @param b
   */
  public void socketOpened(SourceRoute path, P2PSocket a, P2PSocket b);
  
  /**
   * 
   * @param path
   * @param a
   * @param b
   */
  public void socketClosed(SourceRoute path, P2PSocket a, P2PSocket b);

  /**
   * We are the intermediate node for some bytes from Socket a to Socket b
   * @param m
   * @param path
   * @param a
   * @param b
   */
  public void receivedBytes(ByteBuffer m, SourceRoute path, P2PSocket a, P2PSocket b);
  
}
