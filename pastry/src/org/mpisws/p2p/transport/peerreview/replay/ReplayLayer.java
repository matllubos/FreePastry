package org.mpisws.p2p.transport.peerreview.replay;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

import org.mpisws.p2p.transport.ErrorHandler;
import org.mpisws.p2p.transport.MessageCallback;
import org.mpisws.p2p.transport.MessageRequestHandle;
import org.mpisws.p2p.transport.SocketCallback;
import org.mpisws.p2p.transport.SocketRequestHandle;
import org.mpisws.p2p.transport.TransportLayer;
import org.mpisws.p2p.transport.TransportLayerCallback;
import org.mpisws.p2p.transport.peerreview.PeerReviewEvents;
import org.mpisws.p2p.transport.peerreview.Verifier;
import org.mpisws.p2p.transport.peerreview.history.HashProvider;
import org.mpisws.p2p.transport.peerreview.history.IndexEntry;
import org.mpisws.p2p.transport.peerreview.history.SecureHistory;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.environment.time.simulated.DirectTimeSource;
import rice.selector.TimerTask;

public class ReplayLayer<Identifier> extends Verifier<Identifier> implements 
  TransportLayer<Identifier, ByteBuffer> {

  TransportLayerCallback<Identifier, ByteBuffer> callback;
  DirectTimeSource timeSource;
  
  long nextHistoryIndex = 0;
  IndexEntry next;
  
  public ReplayLayer(IdentifierSerializer<Identifier> serializer, HashProvider hashProv, SecureHistory history, Identifier localHandle, long initialTime, DirectTimeSource ts, Environment environment) throws IOException {
    super(serializer, hashProv, history, localHandle, (short)0, (short)0, 0, initialTime, environment.getLogManager().getLogger(ReplayLayer.class, localHandle.toString()));
    this.timeSource = ts;
  }
  

  
//  public IndexEntry getNextEvent() {
//    IndexEntry event = log.statEntry(nextHistoryIndex);
//    nextHistoryIndex++;
//    return event;
//  }
//  
//  public boolean playNextEvent() {
//    if (next != null) return false;
//    next = getNextEvent();
//    setTime(next.getSeq()/1000);
//    switch(next.getType()) {
//    case EVT_SEND:
//      // wait for the message to be sent
//      break;
//    case EVT_RECV:
//      playReceived(next);
//      next = null;
//    }
//  }
//  
//  public void playReceived(IndexEntry ie) {
//    
//  }
//  
//  public void setTime(long now) {
//    timeSource.setTime(now);
//  }

  public SocketRequestHandle<Identifier> openSocket(Identifier i, SocketCallback<Identifier> deliverSocketToMe, Map<String, Integer> options) {
    // TODO Auto-generated method stub
    return null;
  }
  
  public MessageRequestHandle<Identifier, ByteBuffer> sendMessage(Identifier i, ByteBuffer m, MessageCallback<Identifier, ByteBuffer> deliverAckToMe, Map<String, Integer> options) {
    return null;
  }

  public Identifier getLocalIdentifier() {
    return localHandle;
  }

  public void setCallback(TransportLayerCallback<Identifier, ByteBuffer> callback) {
    this.callback = callback;
  }

  public void setErrorHandler(ErrorHandler<Identifier> handler) {
    // TODO Auto-generated method stub    
  }

  public void destroy() {
  }

  public void acceptMessages(boolean b) {
  }

  public void acceptSockets(boolean b) {
  }

  Environment environment;

  @Override
  protected void receive(final Identifier from, final ByteBuffer msg, final long timeToDeliver) {
    environment.getSelectorManager().schedule(new TimerTask() {
    
      @Override
      public long scheduledExecutionTime() {
        return timeToDeliver;
      }
    
      @Override
      public void run() {
        try {
          callback.messageReceived(from, msg, null);
        } catch (IOException ioe) {
          if (logger.level <= Logger.WARNING) logger.logException("Error in receive",ioe);
        }
        // TODO: pump next event makeProgress()
      }    
    });
  }
  
}
