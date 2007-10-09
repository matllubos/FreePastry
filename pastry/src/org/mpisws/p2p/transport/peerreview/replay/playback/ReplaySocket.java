package org.mpisws.p2p.transport.peerreview.replay.playback;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

import org.mpisws.p2p.transport.P2PSocket;
import org.mpisws.p2p.transport.P2PSocketReceiver;
import org.mpisws.p2p.transport.peerreview.Verifier;

public class ReplaySocket<Identifier> implements P2PSocket<Identifier> {

  protected Identifier identifier;
  protected int socketId;
  protected Verifier<Identifier> verifier;

  /**
   * TODO: Make extensible by putting into a factory.
   * 
   * @param identifier
   * @param socketId
   * @param verifier
   */
  public ReplaySocket(Identifier identifier, int socketId, Verifier<Identifier> verifier) {
    this.identifier = identifier;
    this.socketId = socketId;
    this.verifier = verifier;
  }

  public void close() {
    // TODO Auto-generated method stub
    
  }

  public Identifier getIdentifier() {
    return identifier;
  }

  public Map<String, Integer> getOptions() {
    // TODO Auto-generated method stub
    return null;
  }

  public long read(ByteBuffer dsts) throws IOException {
    // TODO Auto-generated method stub
    return 0;
  }

  public void register(boolean wantToRead, boolean wantToWrite, P2PSocketReceiver<Identifier> receiver) {
    // TODO Auto-generated method stub
    
  }

  public void shutdownOutput() {
    // TODO Auto-generated method stub
    
  }

  public long write(ByteBuffer srcs) throws IOException {
    // TODO Auto-generated method stub
    return 0;
  }

}
