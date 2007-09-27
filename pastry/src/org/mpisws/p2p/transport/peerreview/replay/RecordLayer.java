package org.mpisws.p2p.transport.peerreview.replay;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

import org.mpisws.p2p.transport.ErrorHandler;
import org.mpisws.p2p.transport.MessageCallback;
import org.mpisws.p2p.transport.MessageRequestHandle;
import org.mpisws.p2p.transport.P2PSocket;
import org.mpisws.p2p.transport.SocketCallback;
import org.mpisws.p2p.transport.SocketRequestHandle;
import org.mpisws.p2p.transport.TransportLayer;
import org.mpisws.p2p.transport.TransportLayerCallback;
import org.mpisws.p2p.transport.peerreview.PeerReviewEvents;
import org.mpisws.p2p.transport.peerreview.history.Hash;
import org.mpisws.p2p.transport.peerreview.history.HashProvider;
import org.mpisws.p2p.transport.peerreview.history.SecureHistory;
import org.mpisws.p2p.transport.peerreview.history.SecureHistoryFactoryImpl;
import org.mpisws.p2p.transport.peerreview.history.stub.NullHashProvider;
import org.mpisws.p2p.transport.util.SocketRequestHandleImpl;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.util.MathUtils;
import rice.pastry.PastryNode;

public class RecordLayer<Identifier> implements PeerReviewEvents,
  TransportLayer<Identifier, ByteBuffer>,
  TransportLayerCallback<Identifier, ByteBuffer> {

  /**
   * The relevant length of the message.  Default:all (-1)
   */
  public static final String PR_RELEVANT_LEN = "pr_relevant_len";
  /**
   * If the message is relevant.  Default:true (1)
   */
  public static final String PR_RELEVANT_MSG = "pr_relevant_msg";
  
    
  Environment environment;
  TransportLayer<Identifier, ByteBuffer> tl;
  TransportLayerCallback<Identifier, ByteBuffer> callback;
  IdentifierSerializer<Identifier> identifierSerializer;
  
  SecureHistory history;
  Logger logger;

  long lastLogEntry;
  boolean initialized = false;
  
  int socketCtr = Integer.MIN_VALUE;
  
  public RecordLayer(TransportLayer<Identifier, ByteBuffer> tl, String name, Environment env) throws IOException {
    SecureHistoryFactoryImpl shf = new SecureHistoryFactoryImpl();
    NullHashProvider nhp = new NullHashProvider();
    this.history = shf.create(name, 0, nhp.EMPTY_HASH, nhp);
    
    this.environment = env;
    this.lastLogEntry = -1;
    this.logger = env.getLogManager().getLogger(RecordLayer.class, null);
    
    initialized = true;
  }

  public SocketRequestHandle<Identifier> openSocket(final Identifier i, final SocketCallback<Identifier> deliverSocketToMe, final Map<String, Integer> options) {
    final int socketId = socketCtr++;
    final ByteBuffer socketIdBuffer = ByteBuffer.wrap(MathUtils.intToByteArray(socketId));
    try {
      history.appendEntry(EVT_SOCKET_OPEN_OUTGOING, true, identifierSerializer.serialize(i), socketIdBuffer);
    } catch (IOException ioe) {
      if (logger.level <= Logger.WARNING) logger.logException("openSocket("+i+")",ioe); 
    }

    final SocketRequestHandleImpl<Identifier> ret = new SocketRequestHandleImpl<Identifier>(i, options);
    
    ret.setSubCancellable(tl.openSocket(i, new SocketCallback<Identifier>(){
      public void receiveResult(SocketRequestHandle<Identifier> cancellable, P2PSocket<Identifier> sock) {
        socketIdBuffer.clear();
        try {
          history.appendEntry(EVT_SOCKET_OPENED_OUTGOING, true, identifierSerializer.serialize(i), socketIdBuffer);
        } catch (IOException ioe) {
          if (logger.level <= Logger.WARNING) logger.logException("openSocket("+i+")",ioe); 
        }
        socketIdBuffer.clear();
        deliverSocketToMe.receiveResult(ret, new RecordSocket(i, sock, logger, options, socketId, socketIdBuffer, history));
      }
      public void receiveException(SocketRequestHandle<Identifier> s, IOException ex) {
        socketIdBuffer.clear();
        try {
          history.appendEntry(EVT_SOCKET_EXCEPTION, true, identifierSerializer.serialize(i), socketIdBuffer);
        } catch (IOException ioe) {
          if (logger.level <= Logger.WARNING) logger.logException("openSocket("+i+")",ioe); 
        }
        deliverSocketToMe.receiveException(ret, ex);
      }
    }, options));

    return ret;
  }

  public void incomingSocket(P2PSocket<Identifier> s) throws IOException {
    callback.incomingSocket(s);
  }

  public MessageRequestHandle<Identifier, ByteBuffer> sendMessage(Identifier i, ByteBuffer m, MessageCallback<Identifier, ByteBuffer> deliverAckToMe, Map<String, Integer> options) {
    // If the 'RELEVANT_MSG' flag is set to false, the message is passed through to the transport
    // layer. This is used e.g. for liveness/proximity pings in Pastry. 
    if (options == null || !options.containsKey(PR_RELEVANT_MSG) || options.get(PR_RELEVANT_MSG) != 0) {
      int position = m.position(); // mark the current position
      
      int relevantLen = m.remaining();
    
      // If someone sets relevantLen=-1, it means the whole message is relevant.
      if (options != null && options.containsKey(PR_RELEVANT_LEN) && options.get(PR_RELEVANT_LEN) >= 0) {
        relevantLen = options.get(PR_RELEVANT_LEN);
      }
      
      try {
        history.appendEntry(EVT_SEND, true, identifierSerializer.serialize(i), m);
      } catch (IOException ioe) {
        if (logger.level <= Logger.WARNING) logger.logException("sendMessage("+i+","+m+")",ioe); 
      }
      
      m.position(position); // set the incoming position
    }
    
    return tl.sendMessage(i, m, deliverAckToMe, options);
    
//    assert(initialized && (0<=relevantLen) && (relevantLen<=m.remaining()));

//    updateLogTime();
    
    // Pass the message to the Commitment protocol    
    // commitmentProtocol.handleOutgoingMessage(i, m, relevantLen);
    
    
  }
  
  /**
   * PeerReview only updates its internal clock when it returns to the main loop, but not
   * in between (e.g. while it is handling messages). When the clock needs to be
   * updated, this function is called. 
   */
   void updateLogTime() {
     long now = environment.getTimeSource().currentTimeMillis(); // transport->getTime();
    
     if (now > lastLogEntry) {
       if (!history.setNextSeq(now * 1000))
         throw new RuntimeException("PeerReview: Cannot roll back history sequence number from "+history.getLastSeq()+" to "+now*1000+" did you change the local time?");
         
       lastLogEntry = now;
     }
   }

  public void messageReceived(Identifier i, ByteBuffer m, Map<String, Integer> options) throws IOException {
    try {
      history.appendEntry(EVT_RECV, true, identifierSerializer.serialize(i), m);
    } catch (IOException ioe) {
      if (logger.level <= Logger.WARNING) logger.logException("messageReceived("+i+","+m+")",ioe); 
    }

    callback.messageReceived(i, m, options);
  }
  
  public void acceptMessages(boolean b) {
    tl.acceptMessages(b);
  }

  public void acceptSockets(boolean b) {
    tl.acceptSockets(b);
  }

  public Identifier getLocalIdentifier() {
    return tl.getLocalIdentifier();
  }

  public void setCallback(TransportLayerCallback<Identifier, ByteBuffer> callback) {
    this.callback = callback;
  }

  public void setErrorHandler(ErrorHandler<Identifier> handler) {
    // TODO Auto-generated method stub
    
  }

  public void destroy() {
    tl.destroy();
  }
  
}
