package org.mpisws.p2p.transport.sourceroute.manager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

import org.mpisws.p2p.transport.P2PSocket;
import org.mpisws.p2p.transport.P2PSocketReceiver;
import org.mpisws.p2p.transport.sourceroute.SourceRoute;

import rice.environment.Environment;
import rice.environment.logging.Logger;

public class SourceRouteManagerP2PSocket<Identifier> implements
    P2PSocket<Identifier> {

  P2PSocket<SourceRoute<Identifier>> socket;
  Logger logger;
  
  public SourceRouteManagerP2PSocket(P2PSocket<SourceRoute<Identifier>> socket, Environment env) {
    this.socket = socket; 
    this.logger = env.getLogManager().getLogger(SourceRouteManagerP2PSocket.class,null);
  }
  
  public void close() {
    socket.close();
  }

  public Identifier getIdentifier() {
    return socket.getIdentifier().getLastHop();
  }

  public long read(ByteBuffer dsts) throws IOException {
    return socket.read(dsts);
  }

  public long read(ByteBuffer[] dsts, int offset, int length)
      throws IOException {
    return socket.read(dsts, offset, length);
  }

  public void register(boolean wantToRead, boolean wantToWrite,
      final P2PSocketReceiver<Identifier> receiver) {
    if (logger.level <= Logger.FINEST) logger.log("register("+wantToRead+","+wantToWrite+","+receiver+")");
    socket.register(wantToRead, wantToWrite, new P2PSocketReceiver<SourceRoute<Identifier>>(){    
      public void receiveSelectResult(P2PSocket<SourceRoute<Identifier>> socket, boolean canRead, boolean canWrite) throws IOException {
        if (socket != SourceRouteManagerP2PSocket.this.socket) throw new IllegalStateException("socket != this.socket"+socket+","+SourceRouteManagerP2PSocket.this.socket); // it is a bug if this gets tripped
        receiver.receiveSelectResult(SourceRouteManagerP2PSocket.this, canRead, canWrite);
      }
      public void receiveException(P2PSocket<SourceRoute<Identifier>> socket, IOException e) {
        if (socket != SourceRouteManagerP2PSocket.this.socket) throw new IllegalStateException("socket != this.socket"+socket+","+SourceRouteManagerP2PSocket.this.socket); // it is a bug if this gets tripped
        receiver.receiveException(SourceRouteManagerP2PSocket.this, e);
      }    
    });
  }

  public void shutdownOutput() {
    socket.shutdownOutput();
  }

  public long write(ByteBuffer srcs) throws IOException {
    return socket.write(srcs);
  }

  public long write(ByteBuffer[] srcs, int offset, int length)
      throws IOException {
    return socket.write(srcs, offset, length);
  }

  public Map<String, Integer> getOptions() {
    return socket.getOptions();
  }
}
