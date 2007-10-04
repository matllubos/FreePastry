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
import org.mpisws.p2p.transport.util.MessageRequestHandleImpl;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.environment.time.simulated.DirectTimeSource;
import rice.selector.TimerTask;

public class ReplayLayer<Identifier> extends Verifier<Identifier> implements 
  TransportLayer<Identifier, ByteBuffer> {

  TransportLayerCallback<Identifier, ByteBuffer> callback;
//  DirectTimeSource timeSource;
  
  long nextHistoryIndex = 0;
  IndexEntry next;
  
  public ReplayLayer(IdentifierSerializer<Identifier> serializer, HashProvider hashProv, SecureHistory history, Identifier localHandle, long initialTime, Environment environment) throws IOException {
    super(serializer, hashProv, history, localHandle, (short)0, (short)0, 0, initialTime, environment.getLogManager().getLogger(ReplayLayer.class, localHandle.toString()));
    this.environment = environment;
//    this.timeSource = ts;
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
    logger.logException("sendMessage("+i+","+m+")", new Exception("Stack Trace"));
    if (logger.level <= Logger.FINE) logger.log("sendMessage("+i+","+m+","+options+")");
    MessageRequestHandleImpl<Identifier, ByteBuffer> ret = new MessageRequestHandleImpl<Identifier, ByteBuffer>(i, m, options);
    try {
      send(i, m, -1);
      if (deliverAckToMe != null) deliverAckToMe.ack(ret);
    } catch (IOException ioe) {
      if (logger.level <= Logger.WARNING) logger.logException("", ioe);
    }
    return ret;
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
//    logger.log("receive("+from+","+msg+","+timeToDeliver+"):"+(timeToDeliver-environment.getTimeSource().currentTimeMillis()));
    if (logger.level <= Logger.FINER) logger.log("receive("+from+","+msg+","+timeToDeliver+"):"+(timeToDeliver-environment.getTimeSource().currentTimeMillis()));
    environment.getSelectorManager().schedule(new TimerTask() {
    
      @Override
      public long scheduledExecutionTime() {
        return timeToDeliver;
      }
    
      @Override
      public void run() {
        try {
          if (logger.level <= Logger.FINE) logger.log("receive("+from+","+msg+","+timeToDeliver+")");
          callback.messageReceived(from, msg, null);
        } catch (IOException ioe) {
          if (logger.level <= Logger.WARNING) logger.logException("Error in receive",ioe);
        }
        // TODO: pump next event makeProgress()
      }    
      
      public String toString() {
        return "Delivery for receive("+from+","+msg+","+timeToDeliver+")";
      }
    });
  }
  
}
