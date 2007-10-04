package org.mpisws.p2p.transport.peerreview.replay.record;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

import org.mpisws.p2p.transport.P2PSocket;
import org.mpisws.p2p.transport.peerreview.PeerReviewEvents;
import org.mpisws.p2p.transport.peerreview.history.SecureHistory;
import org.mpisws.p2p.transport.util.SocketWrapperSocket;

import rice.environment.logging.Logger;
import rice.p2p.util.MathUtils;

public class RecordSocket<Identifier> extends SocketWrapperSocket<Identifier, Identifier> implements PeerReviewEvents {
  int socketId;
  ByteBuffer socketIdBuffer;
  SecureHistory history;
  
  public RecordSocket(Identifier identifier, P2PSocket<Identifier> socket, Logger logger, Map<String, Integer> options, int socketId, ByteBuffer sib, SecureHistory history) {
    super(identifier, socket, logger, options);
    this.socketId = socketId;
    this.socketIdBuffer = sib;
    this.history = history;
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
        history.appendEntry(EVT_SOCKET_CLOSED, true, socketIdBuffer);
      } catch (IOException ioe) {
        if (logger.level <= Logger.WARNING) logger.logException(this+".read()",ioe); 
      }      
    } else {    
      // wrap the read bytes with a new BB
      ByteBuffer bits = ByteBuffer.wrap(dsts.array());
      bits.position(pos);
      bits.limit(pos+ret);
  
      try {
        history.appendEntry(EVT_SOCKET_READ, true, socketIdBuffer, bits);
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
        history.appendEntry(EVT_SOCKET_CLOSED, true, socketIdBuffer);
      } catch (IOException ioe) {
        if (logger.level <= Logger.WARNING) logger.logException(this+".read()",ioe); 
      }      
    } else {    
      // wrap the read bytes with a new BB
      ByteBuffer bits = ByteBuffer.wrap(srcs.array());
      bits.position(pos);
      bits.limit(pos+ret);
  
      try {
        history.appendEntry(EVT_SOCKET_WRITE, true, socketIdBuffer, bits);
      } catch (IOException ioe) {
        if (logger.level <= Logger.WARNING) logger.logException(this+".read()",ioe); 
      }
    }

    return ret;
  }
}
