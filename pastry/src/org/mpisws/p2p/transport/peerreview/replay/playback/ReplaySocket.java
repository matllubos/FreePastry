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
  boolean closed = false;
  
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

  public Identifier getIdentifier() {
    return identifier;
  }

  public Map<String, Integer> getOptions() {
    return null;
  }

  public long read(ByteBuffer dst) throws IOException {
    return verifier.readSocket(socketId, dst);
  }

  public long write(ByteBuffer src) throws IOException {
    return verifier.writeSocket(socketId, src);
  }

  P2PSocketReceiver<Identifier> reader;
  P2PSocketReceiver<Identifier> writer;
  public void register(boolean wantToRead, boolean wantToWrite, P2PSocketReceiver<Identifier> receiver) {
    if (closed) throw new IllegalStateException("Socket "+identifier+" "+this+" is already closed.");

    if (wantToWrite) {
      if (writer != null) {
        if (writer != receiver) throw new IllegalStateException("Already registered "+writer+" for writing, you can't register "+receiver+" for writing as well!"); 
      }
    }
    
    if (wantToRead) {
      if (reader != null) {
        if (reader != receiver) throw new IllegalStateException("Already registered "+reader+" for reading, you can't register "+receiver+" for reading as well!"); 
      }
      reader = receiver; 
    }
    
    if (wantToWrite) {
      writer = receiver; 
    }
  }

  public void close() {
    closed = true;
    verifier.close(socketId);
  }

  public void shutdownOutput() {
    throw new RuntimeException("Not implemented.");
  }

}
