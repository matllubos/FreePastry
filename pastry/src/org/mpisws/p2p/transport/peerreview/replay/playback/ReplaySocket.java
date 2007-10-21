package org.mpisws.p2p.transport.peerreview.replay.playback;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

import org.mpisws.p2p.transport.ClosedChannelException;
import org.mpisws.p2p.transport.P2PSocket;
import org.mpisws.p2p.transport.P2PSocketReceiver;
import org.mpisws.p2p.transport.SocketCallback;
import org.mpisws.p2p.transport.SocketRequestHandle;
import org.mpisws.p2p.transport.peerreview.Verifier;

public class ReplaySocket<Identifier> implements P2PSocket<Identifier>, SocketRequestHandle<Identifier> {

  protected Identifier identifier;
  protected int socketId;
  protected Verifier<Identifier> verifier;
  boolean closed = false;
  boolean outputClosed = false;
  Map<String, Integer> options;
  
  /**
   * TODO: Make extensible by putting into a factory.
   * 
   * @param identifier
   * @param socketId
   * @param verifier
   */
  public ReplaySocket(Identifier identifier, int socketId, Verifier<Identifier> verifier, Map<String, Integer> options) {
    this.identifier = identifier;
    this.socketId = socketId;
    this.verifier = verifier;
    this.options = options;
  }

  public Identifier getIdentifier() {
    return identifier;
  }

  public Map<String, Integer> getOptions() {
    return options;
  }

  public long read(ByteBuffer dst) throws IOException {
//    if (closed) throw new ClosedChannelException("Socket already closed.");
    return verifier.readSocket(socketId, dst);
  }

  public long write(ByteBuffer src) throws IOException {
//    if (closed || outputClosed) throw new ClosedChannelException("Socket already closed.");
    return verifier.writeSocket(socketId, src);
  }

  P2PSocketReceiver<Identifier> reader;
  P2PSocketReceiver<Identifier> writer;
  public void register(boolean wantToRead, boolean wantToWrite, P2PSocketReceiver<Identifier> receiver) {
    if (closed) throw new IllegalStateException("Socket "+identifier+" "+this+" is already closed.");

    if (wantToWrite) {
      if (outputClosed) {
        // need to record/remove EVT_SOCKET_EXCEPTION
        verifier.generatedSocketException(socketId, null);
        receiver.receiveException(this, 
            new ClosedChannelException("Socket "+identifier+" "+this+" already shut down output."));        
        return;
      }
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
  
  public void notifyIO(boolean canRead, boolean canWrite) throws IOException {
    if (!canRead && !canWrite) {
      throw new IOException("I can't read or write. canRead:"+canRead+" canWrite:"+canWrite);
    }
    if (canRead && canWrite) {
      if (writer != reader) throw new IllegalStateException("weader != writer canRead:"+canRead+" canWrite:"+canWrite);
      P2PSocketReceiver<Identifier> temp = writer;
      writer = null;
      reader = null;
      temp.receiveSelectResult(this, canRead, canWrite);
      return;
    } 
    
    if (canRead) {
      if (reader == null) throw new IllegalStateException("reader:"+reader+" canRead:"+canRead);
      P2PSocketReceiver<Identifier> temp = reader;
      reader = null;
      temp.receiveSelectResult(this, canRead, canWrite);
      return;
    } 
    
    if (canWrite) {
      if (writer == null) throw new IllegalStateException("writer:"+writer+" canWrite:"+canWrite);
      P2PSocketReceiver<Identifier> temp = writer;
      writer = null;
      temp.receiveSelectResult(this, canRead, canWrite);
      return;
    }     
  }

  public void close() {
    closed = true;
    verifier.close(socketId);
  }

  SocketCallback<Identifier> deliverSocketToMe;
  public void setDeliverSocketToMe(SocketCallback<Identifier> deliverSocketToMe) {
    this.deliverSocketToMe = deliverSocketToMe;
  }
  
  public void socketOpened() {
    deliverSocketToMe.receiveResult(this, this);
    deliverSocketToMe = null;
  }
  
  public void shutdownOutput() {
    outputClosed = true;
    verifier.shutdownOutput(socketId);
//    throw new RuntimeException("Not implemented.");
  }

  public void receiveException(IOException ioe) {
    if (deliverSocketToMe != null) {
      deliverSocketToMe.receiveException(this, ioe);
      return;
    }
    if (writer != null) {
      if (writer == reader) {
        writer.receiveException(this, ioe);
        writer = null;
        reader = null;
      } else {
        writer.receiveException(this, ioe);
        writer = null;
      }
    }
    
    if (reader != null) {
      reader.receiveException(this, ioe);
      reader = null;
    }
  }
  
  public boolean cancel() {
    throw new RuntimeException("Not implemented.");
  }

}
