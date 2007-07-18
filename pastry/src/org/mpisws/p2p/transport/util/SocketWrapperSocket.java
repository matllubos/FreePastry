package org.mpisws.p2p.transport.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

import org.mpisws.p2p.transport.P2PSocket;
import org.mpisws.p2p.transport.P2PSocketReceiver;

import rice.environment.logging.Logger;

/**
 * Just maps a socket from one form into another.
 * 
 * @author Jeff Hoye
 *
 * @param <Identifier>
 * @param <SubIdentifier>
 */
public class SocketWrapperSocket<Identifier, SubIdentifier> implements P2PSocket<Identifier> {

  private Identifier identifier;
  private P2PSocket<SubIdentifier> socket;
  private Logger logger;
  private Map<String, Integer> options;
  
  public SocketWrapperSocket(Identifier identifier, P2PSocket<SubIdentifier> socket, Logger logger, Map<String, Integer> options) {
    this.identifier = identifier;
    this.socket = socket;
    this.logger = logger;
    this.options = options;
  }
  
  public Identifier getIdentifier() {
    return identifier;
  }
  public void close() {    
    socket.close();
  }

  public long read(ByteBuffer dsts) throws IOException {
    long ret = socket.read(dsts);
    if (logger.level <= Logger.FINEST) logger.log(this+"read():"+ret);
    return ret;
  }

  public long read(ByteBuffer[] dsts, int offset, int length)
      throws IOException {
    return socket.read(dsts, offset, length);
  }

  public void register(boolean wantToRead, boolean wantToWrite,
      final P2PSocketReceiver<Identifier> receiver) {
    if (logger.level <= Logger.FINEST) logger.log(this+"register("+wantToRead+","+wantToWrite+","+receiver+")");
    socket.register(wantToRead, wantToWrite, new P2PSocketReceiver<SubIdentifier>() {    
      public void receiveSelectResult(P2PSocket<SubIdentifier> socket, boolean canRead,
          boolean canWrite) throws IOException {
        if (logger.level <= Logger.FINEST) logger.log(SocketWrapperSocket.this+"rsr("+socket+","+canRead+","+canWrite+")");
        receiver.receiveSelectResult(SocketWrapperSocket.this, canRead, canWrite);
      }
    
      public void receiveException(P2PSocket<SubIdentifier> socket, IOException e) {
        receiver.receiveException(SocketWrapperSocket.this, e);
      }    
    });
  }

  public void shutdownOutput() {
    socket.shutdownOutput();
  }

  public long write(ByteBuffer srcs) throws IOException {
    long ret = socket.write(srcs);
    if (logger.level <= Logger.FINEST) logger.log(this+"write():"+ret);
    return ret;
  }

  public long write(ByteBuffer[] srcs, int offset, int length)
      throws IOException {
    return socket.write(srcs, offset, length);
  }
  
  @Override
  public String toString() {
    return "Socket<"+identifier+">";
  }

  public Map<String, Integer> getOptions() {
    return options;
  }
}
