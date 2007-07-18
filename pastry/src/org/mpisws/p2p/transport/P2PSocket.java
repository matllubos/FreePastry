package org.mpisws.p2p.transport;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

import rice.p2p.commonapi.Cancellable;
import rice.p2p.commonapi.appsocket.AppSocket;

/**
 * A socket in the layered transport layer.
 * 
 * @author Jeff Hoye
 *
 * @param <Identifier> the identification of the remote node
 */
public interface P2PSocket<Identifier> {
  /**
   * Details on the connectivity of the socket (encrypted, source-routed etc)
   * 
   * @return a read-only list of options on this socket
   */
  public Map<String, Integer> getOptions();
  
  /**
   * The identification of the node at the other end of the socket.
   * 
   * @return The identification of the node at the other end of the socket.
   */
  public Identifier getIdentifier();
  
  /**
   *
   * Reads a sequence of bytes from this channel into a subsequence of the given buffer.
   * 
   * @param dsts
   * @return
   * @throws IOException
   */
  long read(ByteBuffer dsts) throws IOException; 
  
  /**
   * Reads a sequence of bytes from this channel into a subsequence of the given buffers.
   * 
   * @param dsts
   * @param offset
   * @param length
   * @return
   * @throws IOException
   */
  long read(ByteBuffer[] dsts, int offset, int length) throws IOException; 
  
  /**
   * Writes a sequence of bytes to this channel from a subsequence of the given buffers.
   * @throws IOException 
   */  
  long write(ByteBuffer srcs) throws IOException; 
  long write(ByteBuffer[] srcs, int offset, int length) throws IOException; 
  
  /**
   * Must be called every time a Read/Write occurs to continue operation.
   *
   * Can cancel this task by calling with null.
   *
   * @param wantToRead if you want to read from this socket
   * @param wantToWrite if you want to write to this socket
   * @param receiver will have receiveSelectResult() called on it
   * note that you must call select() each time receiveSelectResult() is called.  This is so
   * your application can properly handle flow control
   */
  void register(boolean wantToRead, boolean wantToWrite, P2PSocketReceiver<Identifier> receiver);
  
  /**
   * Disables the output stream for this socket.  Used to properly close down a socket
   * used for bi-directional communication that can be initated by either side.   
   */
  void shutdownOutput();
  
  /**
   * Closes this socket.
   */
  void close(); 
}
