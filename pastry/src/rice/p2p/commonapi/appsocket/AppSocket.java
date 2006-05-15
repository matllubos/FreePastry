/*
 * Created on Jan 30, 2006
 */
package rice.p2p.commonapi.appsocket;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Interface for sending bulk data from the application.  Mimics 
 * java's non-blocking SocketChannel interface this should make it
 * easier to implement in any Java-based p2p overlay.
 */ 
public interface AppSocket {
  /**
   * Reads a sequence of bytes from this channel into a subsequence of the given buffers.
   */
  long read(ByteBuffer[] dsts, int offset, int length) throws IOException; 
  /**
   * Writes a sequence of bytes to this channel from a subsequence of the given buffers.
   * @throws IOException 
   */  
  long write(ByteBuffer[] srcs, int offset, int length) throws IOException; 
  
  /**
   * Must be called every time a Read/Write occurs to continue operation.
   *
   * @receiver will have receiveSelectResult() called on it
   * note that you must call select() each time receiveSelectResult() is called.  This is so
   * your application can properly handle flow control
   */
  void register(boolean wantToRead, boolean wantToWrite, int timeout, AppSocketReceiver receiver);
  
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
