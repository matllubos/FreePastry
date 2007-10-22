package org.mpisws.p2p.transport.peerreview.replay.record;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

import org.mpisws.p2p.transport.ClosedChannelException;
import org.mpisws.p2p.transport.P2PSocket;
import org.mpisws.p2p.transport.P2PSocketReceiver;
import org.mpisws.p2p.transport.peerreview.PeerReviewEvents;
import org.mpisws.p2p.transport.peerreview.history.SecureHistory;
import org.mpisws.p2p.transport.util.SocketWrapperSocket;

import rice.environment.logging.Logger;
import rice.p2p.util.MathUtils;

public class RecordSocket<Identifier> extends SocketWrapperSocket<Identifier, Identifier> implements PeerReviewEvents {

  int socketId;
  ByteBuffer socketIdBuffer;
  RecordLayer<Identifier> recordLayer;
  boolean closed = false;
  boolean outputShutdown = false;
  
  public RecordSocket(Identifier identifier, P2PSocket<Identifier> socket, Logger logger, Map<String, Integer> options, int socketId, ByteBuffer sib, RecordLayer<Identifier> recordLayer) {
    super(identifier, socket, logger, options);
    this.socketId = socketId;
    this.socketIdBuffer = sib;
    this.recordLayer = recordLayer;
  }

  @Override
  public long read(ByteBuffer dsts) throws IOException {
    // remember the position
    int pos = dsts.position();
    
    // read the bytes
    int ret = (int)super.read(dsts);
    
    // do the proper logging
    if (ret < 0) {
      // the socket was closed
      try {
        socketIdBuffer.clear();
        recordLayer.logEvent(EVT_SOCKET_CLOSED, socketIdBuffer);
      } catch (IOException ioe) {
        if (logger.level <= Logger.WARNING) logger.logException(this+".read()",ioe); 
      }      
    } else {    
      // wrap the read bytes with a new BB
      ByteBuffer bits = ByteBuffer.wrap(dsts.array());
      bits.position(pos);
      bits.limit(pos+ret);
  
      try {
        socketIdBuffer.clear();
        recordLayer.logEvent(EVT_SOCKET_READ, socketIdBuffer, bits);
      } catch (IOException ioe) {
        if (logger.level <= Logger.WARNING) logger.logException(this+".read()",ioe); 
      }
    }
    
    return ret;
  }

  @Override
  public long write(ByteBuffer srcs) throws IOException {
    int pos = srcs.position();
    
    // write the bytes
    int ret = (int)super.write(srcs);
    
    // do the proper logging
    if (ret < 0) {
      // the socket was closed
      try {
        socketIdBuffer.clear();
        recordLayer.logEvent(EVT_SOCKET_CLOSED, socketIdBuffer);
      } catch (IOException ioe) {
        if (logger.level <= Logger.WARNING) logger.logException(this+".write()",ioe); 
      }      
    } else {    
      // wrap the read bytes with a new BB
      ByteBuffer bits = ByteBuffer.wrap(srcs.array());
      bits.position(pos);
      bits.limit(pos+ret);
  
      try {
        socketIdBuffer.clear();
        recordLayer.logEvent(EVT_SOCKET_WRITE, socketIdBuffer, bits);
      } catch (IOException ioe) {
        if (logger.level <= Logger.WARNING) logger.logException(this+".write()",ioe); 
      }
    }

    return ret;
  }
  
  @Override
  public void close() {
    try {
      closed = true;
//      logger.logException("close()",new Exception("close()"));
      socketIdBuffer.clear();      
      recordLayer.logEvent(EVT_SOCKET_CLOSE, socketIdBuffer);
    } catch (IOException ioe2) {
      if (logger.level <= Logger.WARNING) logger.logException(this+".receiveException()",ioe2); 
    }        
    super.close();
  }
  
  @Override
  public void shutdownOutput() {
    try {
      outputShutdown = true;
      
//    logger.logException("close()",new Exception("close()"));
      socketIdBuffer.clear();      
      recordLayer.logEvent(EVT_SOCKET_SHUTDOWN_OUTPUT, socketIdBuffer);
    } catch (IOException ioe2) {
      if (logger.level <= Logger.WARNING) logger.logException(this+".receiveException()",ioe2); 
    }        
    super.shutdownOutput();
  }

  @Override
  public void register(boolean wantToRead, boolean wantToWrite, final P2PSocketReceiver<Identifier> receiver) {
    if (closed) {
      receiver.receiveException(this, new ClosedChannelException("Socket "+this+" already closed."));
      return;
    }
    if (wantToWrite && outputShutdown) {
      receiver.receiveException(this, new ClosedChannelException("Socket "+this+" already shutdown output."));
      return;
    }
    
    super.register(wantToRead, wantToWrite, new P2PSocketReceiver<Identifier>(){

      public void receiveSelectResult(P2PSocket<Identifier> socket, boolean canRead, boolean canWrite) throws IOException {
        short evt;
        if (canRead && canWrite) {
          evt = EVT_SOCKET_CAN_RW;
        } else if (canRead) {
          evt = EVT_SOCKET_CAN_READ;
        } else if (canWrite) {
          evt = EVT_SOCKET_CAN_WRITE;            
        } else {
          throw new IOException("I can't read or write. canRead:"+canRead+" canWrite:"+canWrite);
        }
        try {
          socketIdBuffer.clear();
          recordLayer.logEvent(evt, socketIdBuffer);
        } catch (IOException ioe2) {
          if (logger.level <= Logger.WARNING) logger.logException(this+".receiveException()",ioe2); 
        }        
        receiver.receiveSelectResult(RecordSocket.this, canRead, canWrite);
      }
    
      public void receiveException(P2PSocket<Identifier> socket, IOException ioe) {
        try {
          socketIdBuffer.clear();
//          logger.logException(this+".register()", ioe);
          recordLayer.logSocketException(socketIdBuffer, ioe);
        } catch (IOException ioe2) {
          if (logger.level <= Logger.WARNING) logger.logException(this+"@"+socketId+".receiveException()",ioe2); 
        }
        receiver.receiveException(socket, ioe);
      }
    });
  }
}
