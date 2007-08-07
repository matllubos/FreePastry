package org.mpisws.p2p.transport.liveness;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.mpisws.p2p.transport.MessageRequestHandle;
import org.mpisws.p2p.transport.ErrorHandler;
import org.mpisws.p2p.transport.MessageCallback;
import org.mpisws.p2p.transport.P2PSocket;
import org.mpisws.p2p.transport.P2PSocketReceiver;
import org.mpisws.p2p.transport.SocketCallback;
import org.mpisws.p2p.transport.SocketRequestHandle;
import org.mpisws.p2p.transport.TransportLayer;
import org.mpisws.p2p.transport.TransportLayerCallback;
import org.mpisws.p2p.transport.exception.NodeIsFaultyException;
import org.mpisws.p2p.transport.util.DefaultErrorHandler;
import org.mpisws.p2p.transport.util.MessageRequestHandleImpl;
import org.mpisws.p2p.transport.util.SocketWrapperSocket;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.environment.params.Parameters;
import rice.environment.time.TimeSource;
import rice.p2p.util.rawserialization.SimpleOutputBuffer;
import rice.selector.Timer;
import rice.selector.TimerTask;

public class LivenessTransportLayerImpl<Identifier> implements 
    LivenessTransportLayer<Identifier, ByteBuffer>, 
    TransportLayerCallback<Identifier, ByteBuffer> {
  // how long to wait for a ping response to come back before declaring lost
  public final int PING_DELAY;
  
  // factor of jitter to adjust to the ping waits - we may wait up to this time before giving up
  public final float PING_JITTER;
  
  // how many tries to ping before giving up
  public final int NUM_PING_TRIES;
  
  // the maximal amount of time to wait for write to be called before checking liveness
//  public final int WRITE_WAIT_TIME;
  
  // the initial timeout for exponential backoff
  public final long BACKOFF_INITIAL;
  
  // the limit on the number of times for exponential backoff
  public final int BACKOFF_LIMIT;
  
  // the minimum amount of time between check dead checks on dead routes
  public long CHECK_DEAD_THROTTLE;
  
  // the minimum amount of time between liveness checks
//  public long LIVENESS_CHECK_THROTTLE;
  
//  public int NUM_SOURCE_ROUTE_ATTEMPTS;
  
  // the default distance, which is used before a ping
//  public static final int DEFAULT_PROXIMITY = 60*60*1000;
  
  /**
   * millis for the timeout
   * 
   * The idea is that we don't want this parameter to change too fast, 
   * so this is the timeout for it to increase, you could set this to infinity, 
   * but that may be bad because it doesn't account for intermediate link failures
   */
//  public int PROX_TIMEOUT;// = 60*60*1000;

  public int DEFAULT_RTO;// = 3000;
  
  /**
   * RTO helper see RFC 1122 for a detailed description of RTO calculation
   */
  int RTO_UBOUND;// = 10000; // 10 seconds
  /**
   * RTO helper see RFC 1122 for a detailed description of RTO calculation
   */
  int RTO_LBOUND;// = 50;

  /**
   * RTO helper see RFC 1122 for a detailed description of RTO calculation
   */
  double gainH;// = 0.25;

  /**
   * RTO helper see RFC 1122 for a detailed description of RTO calculation
   */
  double gainG;// = 0.125;


  protected TransportLayer<Identifier, ByteBuffer> tl;
  protected Logger logger;
  protected Environment environment;
  protected TimeSource time;
  protected Timer timer;
  protected Random random;

  /**
   * Pass the msg to the callback if it is NORMAL
   */
  public static final byte HDR_NORMAL = 0;
  public static final byte HDR_PING = 1;
  public static final byte HDR_PONG = 2;
  
  /**
   * Holds only pending DeadCheckers
   */
  Map<Identifier, EntityManager> managers;

  private TransportLayerCallback<Identifier, ByteBuffer> callback;
  
  private ErrorHandler<Identifier> errorHandler;
  
  public LivenessTransportLayerImpl(TransportLayer<Identifier, ByteBuffer> tl, Environment env, ErrorHandler<Identifier> errorHandler, int checkDeadThrottle) {
    this.tl = tl;
    this.environment = env;
    this.logger = env.getLogManager().getLogger(LivenessTransportLayerImpl.class, null);
    this.time = env.getTimeSource();
    this.timer = env.getSelectorManager().getTimer();
    random = new Random();
    this.livenessListeners = new ArrayList<LivenessListener<Identifier>>();
    this.pingListeners = new ArrayList<PingListener<Identifier>>();
    this.managers = new HashMap<Identifier, EntityManager>();
    Parameters p = env.getParameters();
    PING_DELAY = p.getInt("pastry_socket_scm_ping_delay");
    PING_JITTER = p.getFloat("pastry_socket_scm_ping_jitter");
    NUM_PING_TRIES = p.getInt("pastry_socket_scm_num_ping_tries");
    BACKOFF_INITIAL = p.getInt("pastry_socket_scm_backoff_initial");
    BACKOFF_LIMIT = p.getInt("pastry_socket_scm_backoff_limit");
    CHECK_DEAD_THROTTLE = checkDeadThrottle; //p.getLong("pastry_socket_srm_check_dead_throttle"); // 300000
    DEFAULT_RTO = p.getInt("pastry_socket_srm_default_rto"); // 3000 // 3 seconds
    RTO_UBOUND = p.getInt("pastry_socket_srm_rto_ubound");//240000; // 240 seconds
    RTO_LBOUND = p.getInt("pastry_socket_srm_rto_lbound");//1000;
    gainH = p.getDouble("pastry_socket_srm_gain_h");//0.25;
    gainG = p.getDouble("pastry_socket_srm_gain_g");//0.125;

    tl.setCallback(this);
    this.errorHandler = errorHandler;
    if (this.errorHandler == null) {
      this.errorHandler = new DefaultErrorHandler<Identifier>(logger);
    }
  }
  
  public void clearState(Identifier i) {
    if (logger.level <= Logger.FINE) logger.log("clearState("+i+")");
    deleteManager(i);
  }

  public boolean checkLiveness(Identifier i, Map<String, Integer> options) {
    return getManager(i).checkLiveness(options);
  }
  
  public EntityManager getManager(Identifier i) {
    synchronized(managers) {
      EntityManager manager = managers.get(i);
      if (manager == null) {
        manager = new EntityManager(i);
        managers.put(i,manager);
      }
      return manager;
    }
  }
  
  public EntityManager deleteManager(Identifier i) {
    synchronized(managers) {
      EntityManager manager = managers.remove(i);
      if (manager.pending != null) manager.pending.cancel();
      return manager;
    }
  }
  
  public int getLiveness(Identifier i, Map<String, Integer> options) {
    if (logger.level <= Logger.FINE) logger.log("getLiveness("+i+","+options+")");
    synchronized(managers) {
      if (managers.containsKey(i))
        return managers.get(i).liveness;
    }
    return LivenessListener.LIVENESS_SUSPECTED;
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

  /**
   * TODO: make this a number of failures?  Say 3?  Use 1 if using SourceRoutes
   */
  boolean connectionExceptionMeansFaulty = true;
  /**
   * Set this to true if you want a ConnectionException to mark the 
   * connection as faulty.  Default = true;
   *
   */
  public void connectionExceptionMeansFaulty(boolean b) {
    connectionExceptionMeansFaulty = b;
  }
  
  public SocketRequestHandle<Identifier> openSocket(final Identifier i, final SocketCallback<Identifier> deliverSocketToMe, final Map<String, Integer> options) {
    // this code marks the Identifier faulty if there is an error connecting the socket.  It's possible that this
    // should be moved to the source route manager, but there needs to be a way to cancel the liveness
    // checks, or maybe the higher layer can ignore them.
    return tl.openSocket(i, new SocketCallback<Identifier>(){
      public void receiveException(SocketRequestHandle<Identifier> s, IOException ex) {
        // the upper layer is probably going to retry, so mark this dead first
        if (connectionExceptionMeansFaulty) {
          if (logger.level <= Logger.FINE) logger.logException("Marking "+s+" dead due to exception opening socket.", ex);
          getManager(i).markDead(options);
        }
        deliverSocketToMe.receiveException(s, ex);
      }
      public void receiveResult(SocketRequestHandle<Identifier> cancellable, P2PSocket<Identifier> sock) {
        deliverSocketToMe.receiveResult(cancellable, new LSocket(getManager(i), sock));
      }    
    }, options);
  }

  public MessageRequestHandle<Identifier, ByteBuffer> sendMessage(
      final Identifier i, 
      final ByteBuffer m, 
      final MessageCallback<Identifier, ByteBuffer> deliverAckToMe, 
      Map<String, Integer> options) {
//    logger.log("sendMessage("+i+","+m+")");      
    final MessageRequestHandleImpl<Identifier, ByteBuffer> handle = 
      new MessageRequestHandleImpl<Identifier, ByteBuffer>(i, m, options);
        
    EntityManager mgr = getManager(i);
    if ((mgr != null) && (mgr.liveness >= LivenessListener.LIVENESS_DEAD)) {
      if (deliverAckToMe != null) deliverAckToMe.sendFailed(handle, new NodeIsFaultyException(i, m));
      return handle;
    }
//    logger.log("sendMessage2("+i+","+m+")");      
    
    byte[] msgBytes = new byte[m.remaining()+1];
    msgBytes[0] = HDR_NORMAL;
    m.get(msgBytes, 1, m.remaining());
    final ByteBuffer myMsg = ByteBuffer.wrap(msgBytes);

    handle.setSubCancellable(tl.sendMessage(i, myMsg, new MessageCallback<Identifier, ByteBuffer>() {    
      public void ack(MessageRequestHandle<Identifier, ByteBuffer> msg) {
        if (handle.getSubCancellable() != null && msg != handle.getSubCancellable()) throw new RuntimeException("msg != handle.getSubCancelable() (indicates a bug in the code) msg:"+msg+" sub:"+handle.getSubCancellable());
        if (deliverAckToMe != null) deliverAckToMe.ack(handle);
      }    
      public void sendFailed(MessageRequestHandle<Identifier, ByteBuffer> msg, IOException ex) {
        if (handle.getSubCancellable() != null && msg != handle.getSubCancellable()) throw new RuntimeException("msg != handle.getSubCancelable() (indicates a bug in the code) msg:"+msg+" sub:"+handle.getSubCancellable());
        if (deliverAckToMe == null) {
          errorHandler.receivedException(i, ex);
        } else {
          deliverAckToMe.sendFailed(handle, ex);
        }
      }
    }, options));
    return handle;
  }

  public void messageReceived(Identifier i, ByteBuffer m, Map<String, Integer> options) throws IOException {
//    logger.log("messageReceived1("+i+","+m+"):"+m.remaining());      
    byte hdr = m.get();
    switch(hdr) {
    case HDR_NORMAL:
      if (logger.level <= Logger.FINEST) logger.log("messageReceived("+i+","+m+")");
//      logger.log("messageReceived2("+i+","+m+"):"+m.remaining());      
      callback.messageReceived(i, m, options);                
      return;
    case HDR_PING:
      if (logger.level <= Logger.FINEST) logger.log("messageReceived("+i+", PING)");
//      logger.log("Got ping from "+i);
      pong(i, m.getLong());      
      notifyPingListenersPing(i);
      return;
    case HDR_PONG:
      if (logger.level <= Logger.FINEST) logger.log("messageReceived("+i+", PONG)");
      EntityManager manager = getManager(i);
      long sendTime = m.getLong();
      int rtt = (int)(time.currentTimeMillis()-sendTime);
      manager.updateRTO(rtt);
      if (manager != null) {
        synchronized(manager) {
          if (manager.pending != null) {
            manager.pending.pingResponse(sendTime, options);
          }
        }
      } else {
        errorHandler.receivedUnexpectedData(i, m.array(), 0, null);      
      }
      notifyPingListenersPong(i,rtt);
      return;            
    default:
      errorHandler.receivedUnexpectedData(i, m.array(), 0, null);      
    }
  }

  /**
   * Send the ping.
   * 
   * @param i
   */
  public boolean ping(Identifier i, Map<String, Integer> options) {
    if (logger.level <= Logger.FINER) logger.log("ping("+i+")");
    if (i.equals(tl.getLocalIdentifier())) return false;
    try {
      SimpleOutputBuffer sob = new SimpleOutputBuffer(1024);
      sob.writeByte(HDR_PING);
      new PingMessage(time.currentTimeMillis()).serialize(sob);
      tl.sendMessage(i, ByteBuffer.wrap(sob.getBytes()), null, options);
      return true;
    } catch (IOException ioe) {
      //Should not happen.  There must be a bug in our serialization code.
      errorHandler.receivedException(i, ioe);
    }
    return false;
  }

  /**
   * Send the pong();
   * 
   * @param i
   * @param senderTime
   */
  public void pong(Identifier i, long senderTime) {
    if (logger.level <= Logger.FINEST) logger.log("pong("+i+","+senderTime+")");
    try {
      SimpleOutputBuffer sob = new SimpleOutputBuffer(1024);
      sob.writeByte(HDR_PONG);
      new PongMessage(senderTime).serialize(sob);
      tl.sendMessage(i, ByteBuffer.wrap(sob.getBytes()), null, null);
    } catch (IOException ioe) {
      //Should not happen.  There must be a bug in our serialization code.
      errorHandler.receivedException(i, ioe);
    }
  }

  public void setCallback(TransportLayerCallback<Identifier, ByteBuffer> callback) {
    this.callback = callback;
  }

  public void setErrorHandler(ErrorHandler<Identifier> handler) {
    errorHandler = handler;
    this.errorHandler = errorHandler;
    if (this.errorHandler == null) {
      this.errorHandler = new DefaultErrorHandler<Identifier>(logger);
    }
  }

  public void destroy() {
    if (logger.level <= Logger.INFO) logger.log("destroy()");
    tl.destroy();    
    livenessListeners.clear();
    livenessListeners = null;
    pingListeners.clear();
    pingListeners = null;
    for (EntityManager em : managers.values()) {
      em.destroy();
    }
    managers.clear();    
    managers = null;
  }

  public void incomingSocket(P2PSocket<Identifier> s) throws IOException {
    callback.incomingSocket(new LSocket(getManager(s.getIdentifier()), s));    
  }

  List<LivenessListener<Identifier>> livenessListeners;
  public void addLivenessListener(LivenessListener<Identifier> name) {
    synchronized(livenessListeners) {
      livenessListeners.add(name);
    }
  }

  public boolean removeLivenessListener(LivenessListener<Identifier> name) {
    synchronized(livenessListeners) {
      return livenessListeners.remove(name);
    }
  }
  
  private void notifyLivenessListeners(Identifier i, int liveness, Map<String, Integer> options) {
    if (logger.level <= Logger.FINER) logger.log("notifyLivenessListeners("+i+","+liveness+")");
    List<LivenessListener<Identifier>> temp;
    synchronized(livenessListeners) {
      temp = new ArrayList<LivenessListener<Identifier>>(livenessListeners);
    }
    for (LivenessListener<Identifier> listener : temp) {
      listener.livenessChanged(i, liveness, options);
    }
  }
  
  List<PingListener<Identifier>> pingListeners;
  public void addPingListener(PingListener<Identifier> name) {
    synchronized(pingListeners) {
      pingListeners.add(name);
    }
  }

  public boolean removePingListener(PingListener<Identifier> name) {
    synchronized(pingListeners) {
      return pingListeners.remove(name);
    }
  }
  
  private void notifyPingListenersPing(Identifier i) {
    List<PingListener<Identifier>> temp;
    synchronized(pingListeners) {
      temp = new ArrayList<PingListener<Identifier>>(pingListeners);
    }
    for (PingListener<Identifier> listener : temp) {
      listener.pingReceived(i, null);
    }
  }
  
  private void notifyPingListenersPong(Identifier i, int rtt) {
    List<PingListener<Identifier>> temp;
    synchronized(pingListeners) {
      temp = new ArrayList<PingListener<Identifier>>(pingListeners);
    }
    for (PingListener<Identifier> listener : temp) {
      listener.pingResponse(i, rtt, null);
    }
  }
  
  /**
   * DESCRIBE THE CLASS
   *
   * @version $Id: SocketCollectionManager.java 3613 2007-02-15 14:45:14Z jstewart $
   * @author jeffh
   */
  protected class DeadChecker extends TimerTask {

    // The number of tries that have occurred so far
    protected int tries = 1;
    
    // the total number of tries before declaring death
    protected int numTries;
    
    // the path to check
    protected EntityManager manager;
    
    // for debugging
    long startTime; // the start time
    int initialDelay; // the initial expected delay
    
    Map<String, Integer> options;
    
    /**
     * Constructor for DeadChecker.
     *
     * @param address DESCRIBE THE PARAMETER
     * @param numTries DESCRIBE THE PARAMETER
     * @param mgr DESCRIBE THE PARAMETER
     */
    public DeadChecker(EntityManager manager, int numTries, int initialDelay, Map<String, Integer> options) {
      if (logger.level <= Logger.FINE) {
//        String s = 
//        if (options.containsKey("identity.node_handle_to_index")) {
//          
//        }
        logger.log("CHECKING DEATH OF PATH " + manager.identifier+" rto:"+initialDelay+" options:"+options);
      }
      
      this.manager = manager;
      this.numTries = numTries;
      this.options = options;
      
      this.initialDelay = initialDelay;
      this.startTime = time.currentTimeMillis();
    }

    /**
     * DESCRIBE THE METHOD
     *
     * @param address DESCRIBE THE PARAMETER
     * @param RTT DESCRIBE THE PARAMETER
     * @param timeHeardFrom DESCRIBE THE PARAMETER
     */
    public void pingResponse(long RTT, Map<String, Integer> options) {
      if (!cancelled) {
        if (tries > 1) {
          long delay = time.currentTimeMillis()-startTime;
          if (logger.level <= Logger.INFO) logger.log(
              "DeadChecker.pingResponse("+manager.identifier+") tries="+tries+
              " estimated="+initialDelay+" totalDelay="+delay);        
        }
      }      
      if (logger.level <= Logger.FINE) logger.log("Terminated DeadChecker(" + manager.identifier + ") due to ping.");
      manager.markAlive(options);
      cancel();
    }

    /**
     * Main processing method for the DeadChecker object
     * 
     * value of tries before run() is called:the time since ping was called:the time since deadchecker was started 
     * 
     * 1:500:500
     * 2:1000:1500
     * 3:2000:3500
     * 4:4000:7500
     * 5:8000:15500 // ~15 seconds to find 1 path faulty, using source routes gives us 30 seconds to find a node faulty
     * 
     */
    public void run() {
//      logger.log(this+".run()");
      if (tries < numTries) {
        tries++;
//        if (manager.getLiveness(path.getLastHop()) == SocketNodeHandle.LIVENESS_ALIVE)
        manager.markSuspected(options);        
        
        ping(manager.identifier, options);
        int absPD = (int)(PING_DELAY*Math.pow(2,tries-1));
        int jitterAmt = (int)(((float)absPD)*PING_JITTER);
        int scheduledTime = absPD-jitterAmt+random.nextInt(jitterAmt*2);
//        logger.log(this+".run():scheduling for "+scheduledTime);
        timer.schedule(this,scheduledTime);
      } else {
        if (logger.level <= Logger.FINE) logger.log("DeadChecker(" + manager.identifier + ") expired - marking as dead.");
//        cancel(); // done in markDead()
        manager.markDead(options);
      }
    }

    public boolean cancel() {
      synchronized(manager) {
        manager.pending = null;
      }
      return super.cancel();
    }
    
    public String toString() {
      return "DeadChecker("+manager.identifier+" #"+System.identityHashCode(this)+"):"+tries+"/"+numTries; 
    }    
  }
  
  /**
   * Internal class which is charges with managing the remote connection via
   * a specific route
   * 
   */
  public class EntityManager {
    
    /**
     * Retransmission Time Out
     */
    int RTO = DEFAULT_RTO; 

    /**
     * Average RTT
     */
    double RTT = 0;

    /**
     * Standard deviation RTT
     */
    double standardD = RTO/4.0;  // RFC1122 recommends choose value to target RTO = 3 seconds
    
    
    // the remote route of this manager
    protected Identifier identifier;
    
    // the current liveness of this route
    protected int liveness;
    
    // the current best-known proximity of this route
//    protected int proximity;
//    protected long proximityTimeout; // when the proximity is no longer valid
    
    // the last time the liveness information was updated
    protected long updated;
    
    // whether or not a check dead is currently being carried out on this route
    protected DeadChecker pending;
    
    /**
     * Constructor - builds a route manager given the route
     *
     * @param route The route
     */
    public EntityManager(Identifier identifier) {
      if (identifier == null) throw new IllegalArgumentException("identifier is null");
      this.identifier = identifier;
      this.liveness = LivenessListener.LIVENESS_SUSPECTED;

//      proximity = DEFAULT_PROXIMITY;
//      proximityTimeout = time.currentTimeMillis()+PROX_TIMEOUT;
      
      this.pending = null;
      this.updated = 0L;
    }
    
    public int rto() {
      return (int)RTO; 
    }
    
    /**
     * Method which returns the last cached proximity value for the given address.
     * If there is no cached value, then DEFAULT_PROXIMITY is returned.
     *
     * @param address The address to return the value for
     * @return The ping value to the remote address
     */
//    public int proximity() {
//      long now = time.currentTimeMillis();
//      // prevent from changing too much
//      if (proximityTimeout > now) return proximity;
//
//      proximity = (int)RTT;
//      proximityTimeout = now+PROX_TIMEOUT;
//      
//      // TODO, schedule notification
//      
//      return proximity;
//    }
     
    /**
     * This method should be called when this route is declared
     * alive.
     */
    protected void markAlive(Map<String, Integer> options) {
      boolean notify = false;
      if (liveness != LivenessListener.LIVENESS_ALIVE) notify = true;
      this.liveness = LivenessListener.LIVENESS_ALIVE;
      if (notify) {
        notifyLivenessListeners(identifier, liveness, options);
      }
    }
    
    /**
     * This method should be called when this route is declared
     * suspected.
     */
    protected void markSuspected(Map<String, Integer> options) {      
      boolean notify = false;
      if (liveness != LivenessListener.LIVENESS_SUSPECTED) notify = true;
      this.liveness = LivenessListener.LIVENESS_SUSPECTED;
      if (notify) {
        if (logger.level <= Logger.FINE) logger.log(this+".markSuspected() notify = true");
        notifyLivenessListeners(identifier, liveness, options);
      }
    }    
    
    /**
     * This method should be called when this route is declared
     * dead.
     */
    protected void markDead(Map<String, Integer> options) {
      boolean notify = false;
      if (liveness != LivenessListener.LIVENESS_DEAD) notify = true;
      if (logger.level <= Logger.FINER) logger.log(this+".markDead() notify:"+notify);
      this.liveness = LivenessListener.LIVENESS_DEAD;
      if (pending != null) {
        pending.cancel(); // sets to null too
      }
      if (notify) {
        notifyLivenessListeners(identifier, liveness, options);
      }
    }
    
    /**
     * This method should be called when this route has its proximity updated
     *
     * @param proximity The proximity
     */
//    protected void markProximity(int proximity) {
//      if (proximity < 0) throw new IllegalArgumentException("proximity must be >= 0, was:"+proximity);
//      updateRTO(proximity);
//      if (this.proximity > proximity) {
//        proximityTimeout = time.currentTimeMillis();
//        this.proximity = proximity;
//      }
//      // TODO: Schedule notification
//    }

    /**
     * Adds a new round trip time datapoint to our RTT estimate, and 
     * updates RTO and standardD accordingly.
     * 
     * @param m new RTT
     */
    private void updateRTO(long m) {      
      if (m < 0) throw new IllegalArgumentException("rtt must be >= 0, was:"+m);
      
      // rfc 1122
      double err = m-RTT;
      double absErr = err;
      if (absErr < 0) {
        absErr *= -1;
      }
      RTT = RTT+gainG*err;
      standardD = standardD + gainH*(absErr-standardD);
      RTO = (int)(RTT+(4.0*standardD));
      if (RTO > RTO_UBOUND) {
        RTO = RTO_UBOUND;
      }
      if (RTO < RTO_LBOUND) {
        RTO = RTO_LBOUND;
      }
//        System.out.println("CM.updateRTO() RTO = "+RTO+" standardD = "+standardD+" suspected in "+getTimeToSuspected(RTO)+" faulty in "+getTimeToFaulty(RTO));
    }      
    
    /**
     * Method which checks to see this route is dead.  If this address has
     * been checked within the past CHECK_DEAD_THROTTLE millis, then
     * this method does not actually do a check.
     *
     * @return true if there will be an update (either a ping, or a change in liveness)
     */
    long start = 0; // delme
    int ctr = 0; // delme
    protected boolean checkLiveness(Map<String, Integer> options) {
//      logger.log(this+".checkLiveness()");
      if (logger.level <= Logger.FINER) logger.log(this+".checkLiveness()");

      // *************** delme ******************
      // we're gonna exit if checkLilveness was called 100 times in 1 second
      ctr++;
      if (ctr%100 == 0) {
        ctr = 0;
        long time_now = time.currentTimeMillis();        
        if ((time_now - start) < 1000) {
          logger.logException("great scotts! "+start, new Exception("Stack Trace"));
          System.exit(1);
        }
        start = time_now;
      }      
      // *************** end delme **********
      
      boolean ret = false;
      int rto = DEFAULT_RTO;
      synchronized (this) {
        if (this.pending != null)
          return true;
        
        long now = time.currentTimeMillis();
        if ((this.liveness < LivenessListener.LIVENESS_DEAD) || 
            (this.updated < now - CHECK_DEAD_THROTTLE)) {
          this.updated = now;
          rto = rto();
          this.pending = new DeadChecker(this, NUM_PING_TRIES, rto, options);
          ret = true;
        } else {
          if (logger.level <= Logger.FINE) {
            logger.log(this+".checkLiveness() not checking "+identifier+" checked to recently, can't check for "+((updated+CHECK_DEAD_THROTTLE)-now)+" millis.");
          }
        }
      }
      if (ret) {
        timer.schedule(pending, rto);
        ping(identifier, options);
      }
      
      return ret;
    }
    
    public String toString() {
      return identifier.toString();
//      return "SRM{"+System.identityHashCode(this)+"}"+identifier;
    }
    
    public void destroy() {      
      if (pending != null) pending.cancel();
    }
  }

  /**
   * The purpose of this class is to checkliveness on a stalled socket that we are waiting to write on.
   * 
   * the livenessCheckerTimer is set every time we want to write, and killed every time we do write
   * 
   * TODO: think about exactly what we want to use for the delay on the timer, currently using rto*4
   * 
   * @author Jeff Hoye
   *
   */
  class LSocket extends SocketWrapperSocket<Identifier, Identifier> {
    EntityManager manager;
    /**
     * set every time we want to write, and killed every time we do write
     */
    TimerTask livenessCheckerTimer;
    
    public LSocket(EntityManager manager, P2PSocket<Identifier> socket) {
      super(socket.getIdentifier(), socket, LivenessTransportLayerImpl.this.logger, socket.getOptions());
      this.manager = manager;
    }      

    @Override
    public void register(boolean wantToRead, boolean wantToWrite, final P2PSocketReceiver<Identifier> receiver) {     
      if (wantToWrite) startLivenessCheckerTimer();
      super.register(wantToRead, wantToWrite, new P2PSocketReceiver<Identifier>() {

        public void receiveException(P2PSocket<Identifier> socket, IOException ioe) {
          receiver.receiveException(socket, ioe);
        }

        public void receiveSelectResult(P2PSocket<Identifier> socket, boolean canRead, boolean canWrite) throws IOException {
          if (canWrite) stopLivenessCheckerTimer();
          receiver.receiveSelectResult(socket, canRead, canWrite);
        }});
    }

    public void startLivenessCheckerTimer() {      
      stopLivenessCheckerTimer();
      livenessCheckerTimer = new TimerTask(){      
        @Override
        public void run() {
          manager.checkLiveness(options);
        }      
      };
      timer.schedule(livenessCheckerTimer, manager.rto()*4);
    }

    public void stopLivenessCheckerTimer() {
      if (livenessCheckerTimer != null) livenessCheckerTimer.cancel();
      livenessCheckerTimer = null;
    }
  }
}
