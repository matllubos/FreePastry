package org.mpisws.p2p.transport.peerreview.replay.record;

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
import org.mpisws.p2p.transport.peerreview.history.SecureHistory;
import org.mpisws.p2p.transport.peerreview.history.SecureHistoryFactoryImpl;
import org.mpisws.p2p.transport.peerreview.history.stub.NullHashProvider;
import org.mpisws.p2p.transport.peerreview.replay.IdentifierSerializer;
import org.mpisws.p2p.transport.util.SocketRequestHandleImpl;

import rice.environment.Environment;
import rice.environment.logging.LogManager;
import rice.environment.logging.Logger;
import rice.environment.params.Parameters;
import rice.environment.params.simple.SimpleParameters;
import rice.environment.processing.Processor;
import rice.environment.processing.sim.SimProcessor;
import rice.environment.random.RandomSource;
import rice.environment.random.simple.SimpleRandomSource;
import rice.environment.time.simple.SimpleTimeSource;
import rice.environment.time.simulated.DirectTimeSource;
import rice.p2p.util.MathUtils;
import rice.selector.SelectorManager;

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
  
  public static ByteBuffer ONE, ZERO;
  
  public RecordLayer(TransportLayer<Identifier, ByteBuffer> tl, String name, IdentifierSerializer<Identifier> serializer, Environment env) throws IOException {
    NullHashProvider nhp = new NullHashProvider();
    SecureHistoryFactoryImpl shf = new SecureHistoryFactoryImpl(nhp, env);
    
    byte[] one = new byte[1];
    one[0] = 1;
    ONE = ByteBuffer.wrap(one);
    
    byte[] zero = new byte[1];
    zero[0] = 0;
    ZERO = ByteBuffer.wrap(zero);
    
    this.tl = tl;
    this.tl.setCallback(this);
    this.history = shf.create(name, 0, nhp.EMPTY_HASH);
    this.identifierSerializer = serializer;
    
    this.environment = env;
    this.lastLogEntry = -1;
    this.logger = env.getLogManager().getLogger(RecordLayer.class, null);
    
    initialized = true;
  }

  /**
   * PeerReview only updates its internal clock when it returns to the main loop, but not
   * in between (e.g. while it is handling messages). When the clock needs to be
   * updated, this function is called. 
   */  
  public void updateLogTime() {
   long now = environment.getTimeSource().currentTimeMillis();
  
   if (now > lastLogEntry) {
     if (!history.setNextSeq(now * 1000000))
       throw new RuntimeException("PeerReview: Cannot roll back history sequence number from "+history.getLastSeq()+" to "+now*1000000+"; did you change the local time?");
       
     lastLogEntry = now;
   }
  }
  
  /* Called by applications to log some application-specific event, such as PAST_GET. */
  
  public void logEvent(short type, ByteBuffer ... entry) throws IOException {
//   assert(initialized && (type > EVT_MAX_RESERVED));
   updateLogTime();
   history.appendEntry(type, true, entry);   
  }

  
  public SocketRequestHandle<Identifier> openSocket(final Identifier i, final SocketCallback<Identifier> deliverSocketToMe, final Map<String, Integer> options) {
    final int socketId = socketCtr++;
    final ByteBuffer socketIdBuffer = ByteBuffer.wrap(MathUtils.intToByteArray(socketId));
    try {
      logEvent(EVT_SOCKET_OPEN_OUTGOING, identifierSerializer.serialize(i), socketIdBuffer);
    } catch (IOException ioe) {
      if (logger.level <= Logger.WARNING) logger.logException("openSocket("+i+")",ioe); 
    }

    final SocketRequestHandleImpl<Identifier> ret = new SocketRequestHandleImpl<Identifier>(i, options);
    
    ret.setSubCancellable(tl.openSocket(i, new SocketCallback<Identifier>(){
      public void receiveResult(SocketRequestHandle<Identifier> cancellable, P2PSocket<Identifier> sock) {
        socketIdBuffer.clear();
        try {
          logEvent(EVT_SOCKET_OPENED_OUTGOING, identifierSerializer.serialize(i), socketIdBuffer);
        } catch (IOException ioe) {
          if (logger.level <= Logger.WARNING) logger.logException("openSocket("+i+")",ioe); 
        }
        socketIdBuffer.clear();
        deliverSocketToMe.receiveResult(ret, new RecordSocket(i, sock, logger, options, socketId, socketIdBuffer, history));
      }
      public void receiveException(SocketRequestHandle<Identifier> s, IOException ex) {
        socketIdBuffer.clear();
        try {
          logEvent(EVT_SOCKET_EXCEPTION, identifierSerializer.serialize(i), socketIdBuffer);
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
//    logger.logException("sendMessage("+i+","+m+"):"+MathUtils.toHex(m.array()), new Exception("Stack Trace"));
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
        logEvent(EVT_SEND, identifierSerializer.serialize(i), m);
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
  
  public void messageReceived(Identifier i, ByteBuffer m, Map<String, Integer> options) throws IOException {
    try {
      logEvent(EVT_RECV, identifierSerializer.serialize(i), m);
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
  
  public static Environment generateEnvironment() {
    return generateEnvironment(null);
  }
  
  public static Environment generateEnvironment(int randomSeed) {
    SimpleRandomSource srs = new SimpleRandomSource(randomSeed, null);
    Environment env = generateEnvironment(srs);
    srs.setLogManager(env.getLogManager());
    return env;
  }
  
  public static Environment generateEnvironment(RandomSource rs) {
    Parameters params = new SimpleParameters(Environment.defaultParamFileArray,null);
    DirectTimeSource dts = new DirectTimeSource(System.currentTimeMillis());
    LogManager lm = Environment.generateDefaultLogManager(dts,params);
    dts.setLogManager(lm);
    SelectorManager selector = new RecordSM("Default", new SimpleTimeSource(), dts,lm);
    dts.setSelectorManager(selector);
    Processor proc = new SimProcessor(selector);
    Environment ret = new Environment(selector,proc,rs,dts,lm,
        params, Environment.generateDefaultExceptionStrategy(lm));
    return ret;
  }
}
