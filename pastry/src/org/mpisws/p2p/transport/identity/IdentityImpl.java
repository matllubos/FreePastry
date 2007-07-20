package org.mpisws.p2p.transport.identity;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.mpisws.p2p.transport.ErrorHandler;
import org.mpisws.p2p.transport.MessageCallback;
import org.mpisws.p2p.transport.MessageRequestHandle;
import org.mpisws.p2p.transport.P2PSocket;
import org.mpisws.p2p.transport.P2PSocketReceiver;
import org.mpisws.p2p.transport.SocketCallback;
import org.mpisws.p2p.transport.SocketRequestHandle;
import org.mpisws.p2p.transport.TransportLayer;
import org.mpisws.p2p.transport.TransportLayerCallback;
import org.mpisws.p2p.transport.exception.NodeIsFaultyException;
import org.mpisws.p2p.transport.liveness.LivenessListener;
import org.mpisws.p2p.transport.liveness.LivenessProvider;
import org.mpisws.p2p.transport.liveness.PingListener;
import org.mpisws.p2p.transport.liveness.Pinger;
import org.mpisws.p2p.transport.proximity.ProximityListener;
import org.mpisws.p2p.transport.proximity.ProximityProvider;
import org.mpisws.p2p.transport.util.MessageRequestHandleImpl;
import org.mpisws.p2p.transport.util.OptionsFactory;
import org.mpisws.p2p.transport.util.SocketRequestHandleImpl;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.Cancellable;

public class IdentityImpl<UpperIdentifier, UpperMsgType, LowerIdentifier> {
  protected byte[] localIdentifier;
  
  protected LowerIdentityImpl lower;
  protected UpperIdentityImpl upper;
  
  protected Map<UpperIdentifier, Set<IdentityMessageHandle>> pendingMessages;
  protected Set<UpperIdentifier> deadForever;

  protected Environment environment;
  protected Logger logger;
  
  protected IdentitySerializer<UpperIdentifier> serializer;
  protected NodeChangeStrategy<UpperIdentifier, LowerIdentifier> nodeChangeStrategy;

  /**
   * Multiple UpperIdentifiers claim the same binding to the 
   * LowerIdentifier.
   * 
   * If these are unrelated nodes (different ID/credentials) we need to 
   * make sure to expire the node first.  If they are the same node, and 
   * just a new epoch, then we can exprie them right away.
   * 
   * Note, that it's possible to have the UpperIdentifier in multiple places if it
   * has multiple paths (such as source routing)
   */
  protected Map<LowerIdentifier, List<UpperIdentifier>> bindings;
  /**
   * Used to delete bindings in setDead()/setDeadForever()
   */
  protected Map<UpperIdentifier, List<LowerIdentifier>> reverseBinding; 
  
  /**
   * Held in the options map of the message/socket.  This is a pointer from the upper 
   * level to the lower level.
   */
  Map<Integer, UpperIdentifier> intendedDest;
  Map<UpperIdentifier, Integer> reverseIntendedDest;
  int intendedDestCtr = Integer.MIN_VALUE;
  
  public static final byte SUCCESS = 1;
  public static final byte FAILURE = 0;
  
  public static final byte NO_ID = 2;
  public static final byte NORMAL = 1;
  public static final byte INCORRECT_IDENTITY = 0;
  
  public static final String NODE_HANDLE_INDEX = "identity.node_handle_index";
  
  public IdentityImpl(
      byte[] localIdentifier, 
      IdentitySerializer<UpperIdentifier> serializer, 
      NodeChangeStrategy<UpperIdentifier, LowerIdentifier> nodeChangeStrategy,
      Environment environment) {
    this.logger = environment.getLogManager().getLogger(IdentityImpl.class, null);
    this.localIdentifier = localIdentifier;    
    this.serializer = serializer;
    this.nodeChangeStrategy = nodeChangeStrategy;
    this.environment = environment;    
    
    this.pendingMessages = new HashMap<UpperIdentifier, Set<IdentityMessageHandle>>();
    this.deadForever = Collections.synchronizedSet(new HashSet<UpperIdentifier>());
    
    this.intendedDest = new HashMap<Integer, UpperIdentifier>();
    this.reverseIntendedDest = new HashMap<UpperIdentifier, Integer>();
    
    this.bindings = new HashMap<LowerIdentifier, List<UpperIdentifier>>();
    this.reverseBinding = new HashMap<UpperIdentifier, List<LowerIdentifier>>();
  }
  
  public void addPendingMessage(UpperIdentifier i, IdentityMessageHandle ret) {
    if (logger.level <= Logger.FINER) logger.log("addPendingMessage("+i+","+ret+")");
    synchronized(pendingMessages) {
      Set<IdentityMessageHandle> set = pendingMessages.get(i);
      if (set == null) {
        set = new HashSet<IdentityMessageHandle>();
        pendingMessages.put(i, set);
      }
      set.add(ret);
    }
  }
  
  public void setDeadForever(UpperIdentifier i) {
    if (deadForever.contains(i)) return;
    if (logger.level <= Logger.INFO) logger.log("setDeadForever("+i+")");
    deadForever.add(i);
    deleteBindings(i);
    Set<IdentityMessageHandle> cancelMe = pendingMessages.remove(i);
    if (cancelMe != null) {
      for (IdentityMessageHandle msg : cancelMe) {
        msg.deadForever(); 
      }
    }
    upper.livenessProvider.clearState(i);
  }
  
  /**
   * Returns the identifier.
   * 
   * @param i
   * @return
   */
  protected int addIntendedDest(UpperIdentifier i) {
    synchronized(intendedDest) {
      if (intendedDest.containsKey(i)) return reverseIntendedDest.get(i);
      intendedDest.put(intendedDestCtr, i);
      reverseIntendedDest.put(i, intendedDestCtr);
      intendedDestCtr++;
      return intendedDestCtr-1;
    }
  }
  
  protected void addBinding(UpperIdentifier u, LowerIdentifier l) {
    synchronized(bindings) {
      List<UpperIdentifier> list = bindings.get(l);
      if (list == null) {
        list = new LinkedList<UpperIdentifier>();
        bindings.put(l, list);
      } else {
        if (list.contains(l)) return;          
      }
      // the list is non-null, and we're not in it
      list.add(u);
      List<LowerIdentifier> rlist = reverseBinding.get(u);
      if (rlist == null) {
        rlist = new ArrayList<LowerIdentifier>(); 
        reverseBinding.put(u, rlist);
      } else {
        if (list.contains(l)) return; // this is bad, something got confused 
      }
      rlist.add(l);
    }
  }
  
  protected void deleteBindings(UpperIdentifier i) {
    synchronized(bindings) {
      List<LowerIdentifier> rlist = reverseBinding.remove(i);
      if (rlist == null) return;
      for (LowerIdentifier l : rlist) {
        List list = bindings.get(l);
        list.remove(i); // should not throw a NPE, if it does, there is a synchronization problem
        if (list.isEmpty()) bindings.remove(l);
      }
    }
  }
  
  public void initLowerLayer(TransportLayer<LowerIdentifier, ByteBuffer> tl) {
    lower = new LowerIdentityImpl(tl);
  }
  
  public LowerIdentity<LowerIdentifier, ByteBuffer> getLowerIdentity() {
    return lower;
  }

  
  class LowerIdentityImpl implements LowerIdentity<LowerIdentifier, ByteBuffer>, TransportLayerCallback<LowerIdentifier, ByteBuffer> {
    TransportLayer<LowerIdentifier, ByteBuffer> tl;    
    TransportLayerCallback<LowerIdentifier, ByteBuffer> callback;
    ErrorHandler<LowerIdentifier> handler;
    Logger logger;
    
    public LowerIdentityImpl(
        TransportLayer<LowerIdentifier, ByteBuffer> tl) {
      this.tl = tl;
      logger = environment.getLogManager().getLogger(IdentityImpl.class, "lower");
      
      tl.setCallback(this);
    }

    public SocketRequestHandle<LowerIdentifier> openSocket(
        final LowerIdentifier i, 
        final SocketCallback<LowerIdentifier> deliverSocketToMe, 
        Map<String, Integer> options) {
      
      // what happens if they cancel after the socket has been received by a lower layer, but is still reading the header?  
      // May need to re-think this SRHI at all the layers
      final SocketRequestHandleImpl<LowerIdentifier> ret = new SocketRequestHandleImpl<LowerIdentifier>(i, options);
      int index = options.get(NODE_HANDLE_INDEX);
      final UpperIdentifier dest = intendedDest.get(index);
      
      final ByteBuffer buf;
      try {
        buf = ByteBuffer.wrap(serializer.serialize(dest));
      } catch (IOException ioe) {
        deliverSocketToMe.receiveException(ret, ioe);
        return ret;
      }
      
      ret.setSubCancellable(tl.openSocket(i, 
          new SocketCallback<LowerIdentifier>(){

            public void receiveException(SocketRequestHandle<LowerIdentifier> s, IOException ex) {
              deliverSocketToMe.receiveException(ret, ex);
            }

            public void receiveResult(SocketRequestHandle<LowerIdentifier> cancellable, P2PSocket<LowerIdentifier> sock) {
              sock.register(false, true, new P2PSocketReceiver<LowerIdentifier>() {

                public void receiveSelectResult(P2PSocket<LowerIdentifier> socket, boolean canRead, boolean canWrite) throws IOException {
                  if (canRead) throw new IOException("Never asked to read!");
                  if (!canWrite) throw new IOException("Can't write!");
                  socket.write(buf);
                  if (buf.hasRemaining()) {
                    socket.register(false, true, this);
                  } else {
                    // wait for the response
                    socket.register(true, false, new P2PSocketReceiver<LowerIdentifier>() {
                      ByteBuffer responseBuffer = ByteBuffer.allocate(1);

                      public void receiveException(P2PSocket<LowerIdentifier> socket, IOException ioe) {
                        deliverSocketToMe.receiveException(ret, ioe);                        
                      }

                      public void receiveSelectResult(P2PSocket<LowerIdentifier> socket, boolean canRead, boolean canWrite) throws IOException {
                        if (socket.read(responseBuffer) == -1) {
                          // socket unexpectedly closed
                          deliverSocketToMe.receiveException(ret, new ClosedChannelException());                                                  
                        }
                        
                        if (responseBuffer.remaining() > 0) {
                          socket.register(true, false, this);
                        } else {
                          byte answer = responseBuffer.array()[0];
                          if (answer == FAILURE) {
                            // wrong address, read more 
                            // TODO read the new address
                            if (logger.level <= Logger.INFO) logger.log("openSocket("+i+","+deliverSocketToMe+") answer = FAILURE");
                            deliverSocketToMe.receiveException(ret, new NodeIsFaultyException(i));
                            setDeadForever(dest);
                          } else {
                            deliverSocketToMe.receiveResult(ret, socket);
                          }
                        }                      
                      }                    
                    });
                  }
                }        

                public void receiveException(P2PSocket<LowerIdentifier> socket, IOException ioe) {
                  deliverSocketToMe.receiveException(ret, ioe);                  
                }              
              });
            }
          }, 
          options));
      return ret;
    }

    public void incomingSocket(P2PSocket<LowerIdentifier> s) throws IOException {
      s.register(true, false, new P2PSocketReceiver<LowerIdentifier>() {
        ByteBuffer buf = ByteBuffer.allocate(localIdentifier.length);

        public void receiveException(P2PSocket<LowerIdentifier> socket, IOException ioe) {
          handler.receivedException(socket.getIdentifier(), ioe);
        }

        public void receiveSelectResult(P2PSocket<LowerIdentifier> socket, boolean canRead, boolean canWrite) throws IOException {
          if (canWrite) throw new IOException("Never asked to write!");
          if (!canRead) throw new IOException("Can't read!");
          
          if (socket.read(buf) == -1) {
            // socket closed
            handler.receivedException(socket.getIdentifier(), new ClosedChannelException());
            return;
          }
          
          if (buf.hasRemaining()) {
            // need to read more
            socket.register(true, false, this);
            return;
          }
          
          if (Arrays.equals(buf.array(), localIdentifier)) {
            byte[] result = {SUCCESS};
            final ByteBuffer writeMe = ByteBuffer.wrap(result);
            socket.register(false, true, new P2PSocketReceiver<LowerIdentifier>() {
              public void receiveException(P2PSocket<LowerIdentifier> socket, IOException ioe) {
                handler.receivedException(socket.getIdentifier(), ioe);                
              }

              public void receiveSelectResult(P2PSocket<LowerIdentifier> socket, boolean canRead, boolean canWrite) throws IOException {
                if (canRead) throw new IOException("Not expecting to read.");
                if (!canWrite) throw new IOException("Expecting to write.");
                
                if (socket.write(writeMe) == -1) {
                  // socket closed
                  handler.receivedException(socket.getIdentifier(), new ClosedChannelException());
                  return;                  
                }
                
                if (buf.hasRemaining()) {
                  // need to read more
                  socket.register(false, true, this);
                  return;
                }

                // done writing pass up socket
                callback.incomingSocket(socket);
              }
            });
          } else {
            if (logger.level <= Logger.INFO) 
              logger.log("incomingSocket() FAILURE expected "+
                  Arrays.toString(buf.array())+" me:"+
                  Arrays.toString(localIdentifier));
            
            // not expecting me, send failure
            byte[] result = {FAILURE};
            final ByteBuffer writeMe = ByteBuffer.wrap(result);
            socket.register(false, true, new P2PSocketReceiver<LowerIdentifier>() {
              public void receiveException(P2PSocket<LowerIdentifier> socket, IOException ioe) {
                handler.receivedException(socket.getIdentifier(), ioe);                
              }

              public void receiveSelectResult(P2PSocket<LowerIdentifier> socket, boolean canRead, boolean canWrite) throws IOException {
                if (canRead) throw new IOException("Not expecting to read.");
                if (!canWrite) throw new IOException("Expecting to write.");
                
                if (socket.write(writeMe) == -1) {
                  // socket closed
                  handler.receivedException(socket.getIdentifier(), new ClosedChannelException());
                  return;                  
                }
                
                if (buf.hasRemaining()) {
                  // need to read more
                  socket.register(false, true, this);
                  return;
                }
              }
            });
          }
        }        
      });
    }

    /**
     * Head the message with the expected identifier
     */
    public MessageRequestHandle<LowerIdentifier, ByteBuffer> sendMessage(
        final LowerIdentifier i, 
        ByteBuffer m, 
        final MessageCallback<LowerIdentifier, ByteBuffer> deliverAckToMe, 
        Map<String, Integer> options) {

      if (logger.level <= Logger.FINEST) {
        byte[] b = new byte[m.remaining()];
        System.arraycopy(m.array(), m.position(), b, 0, b.length);
        logger.log("sendMessage("+i+","+m+")"+Arrays.toString(b));
      } else {
        if (logger.level <= Logger.FINE) logger.log("sendMessage("+i+","+m+")");        
      }

      // what happens if they cancel after the socket has been received by a lower layer, but is still reading the header?  
      // May need to re-think this SRHI at all the layers
      final MessageRequestHandleImpl<LowerIdentifier, ByteBuffer> ret = 
        new MessageRequestHandleImpl<LowerIdentifier, ByteBuffer>(i, m, options);
   
      Integer index = null;
      if (options != null) {
        index = options.get(NODE_HANDLE_INDEX);
      }
      
      byte[] msgWithHeader;
      if (index == null) {
        // don't include an id
        msgWithHeader = new byte[1+m.remaining()];    
        msgWithHeader[0] = NO_ID;        
        m.get(msgWithHeader, 1, m.remaining());
      } else {
        // don't include an id
        UpperIdentifier dest = intendedDest.get(index.intValue());
      
        addBinding(dest, i);
        
        byte[] destBytes;        
        try {          
          destBytes = serializer.serialize(dest);
        } catch (IOException ioe) {
          deliverAckToMe.sendFailed(ret, ioe);
          return ret;
        }
        
        // build a new ByteBuffer with the header      
        msgWithHeader = new byte[1+destBytes.length+m.remaining()];
        msgWithHeader[0] = NORMAL;
        System.arraycopy(destBytes, 0, msgWithHeader, 1, destBytes.length);
        m.get(msgWithHeader, destBytes.length+1, m.remaining());
      }
      
      
      final ByteBuffer buf = ByteBuffer.wrap(msgWithHeader);
      
      ret.setSubCancellable(tl.sendMessage(i, buf, 
          new MessageCallback<LowerIdentifier, ByteBuffer>(){

            public void ack(MessageRequestHandle<LowerIdentifier, ByteBuffer> msg) {              
              if (ret.getSubCancellable() != null && msg != ret.getSubCancellable()) 
                throw new RuntimeException("msg != cancellable.getSubCancellable() (indicates a bug in the code) msg:"+
                    msg+" sub:"+ret.getSubCancellable());
              if (deliverAckToMe != null) deliverAckToMe.ack(ret);
            }

            public void sendFailed(MessageRequestHandle<LowerIdentifier, ByteBuffer> msg, IOException ex) {
              if (ret.getSubCancellable() != null && msg != ret.getSubCancellable()) 
                throw new RuntimeException("msg != cancellable.getSubCancellable() (indicates a bug in the code) msg:"+
                    msg+" sub:"+ret.getSubCancellable());
              if (deliverAckToMe == null) {
                handler.receivedException(i, ex);
              } else {
                deliverAckToMe.sendFailed(ret, ex);
              }
            }
      
          }, options));
      return ret;
    }
    
    public void messageReceived(LowerIdentifier i, ByteBuffer m, Map<String, Integer> options) throws IOException {
      byte msgType = m.get();
      if (logger.level <= Logger.FINE) logger.log("messageReceived("+i+","+m+"):"+msgType);
      switch(msgType) {
        case NORMAL:
          // this is a normal message, make sure it's for me        
          byte[] dest = new byte[localIdentifier.length];
          m.get(dest);
          if (!Arrays.equals(dest, localIdentifier)) {
            // send back an error 
            if (logger.level <= Logger.INFO) logger.log(
                "received message for wrong node from:"+i+
                " intended:"+Arrays.toString(dest)+
                " me:"+Arrays.toString(localIdentifier));
            
            byte[] errorMessage = new byte[1+localIdentifier.length];
            errorMessage[0] = INCORRECT_IDENTITY;
            System.arraycopy(localIdentifier, 0, errorMessage, 1, localIdentifier.length);
            ByteBuffer buf = ByteBuffer.wrap(errorMessage);
            tl.sendMessage(i, buf, null, options);          
            
          }
          // continue to read the rest of the message
          
        case NO_ID:
          // it's for me, no problem
          // send back an error 
          if (logger.level <= Logger.FINEST) {
            byte[] b = new byte[m.remaining()];
            System.arraycopy(m.array(), m.position(), b, 0, b.length);
            logger.log(
              "received message for me from:"+i+" "+Arrays.toString(b));
          } else {
            if (logger.level <= Logger.FINER) 
              logger.log("received message for me from:"+i+" "+m);            
          }
          callback.messageReceived(i, m, options);
          break;
        case INCORRECT_IDENTITY:
  
          // it's an error, read it in
          UpperIdentifier oldDest = null;
          List<UpperIdentifier> list = bindings.get(i);
          if (list != null) {
            oldDest = list.get(0);
          }
          
          if (oldDest != null) {
            UpperIdentifier newDest = serializer.deserialize(m);
            if (logger.level <= Logger.INFO) logger.log(
                "received INCORRECT_IDENTITY:"+i+
                " old:"+oldDest+
                " new:"+newDest);
            if (oldDest.equals(newDest)) {
  //            if (logger.level <= Logger.FINE) logger.log("1");
              // don't do anything 
            } else {
  //            if (logger.level <= Logger.FINE) logger.log("2");
              if (deadForever.contains(oldDest)) {
  //              if (logger.level <= Logger.FINE) logger.log("3");              
              } else {
  //              if (logger.level <= Logger.FINE) logger.log("4");
                if (nodeChangeStrategy.canChange(oldDest, newDest, i)) {
  //                if (logger.level <= Logger.FINE) logger.log("5");
                  upper.livenessChanged(newDest, LivenessListener.LIVENESS_ALIVE);
                  setDeadForever(oldDest);
                }
              }
            }
          }
      }
    }    

    public void acceptMessages(boolean b) {
      tl.acceptMessages(b);
    }

    public void acceptSockets(boolean b) {
      tl.acceptMessages(b);
    }

    public LowerIdentifier getLocalIdentifier() {
      return tl.getLocalIdentifier();
    }

    public void setCallback(TransportLayerCallback<LowerIdentifier, ByteBuffer> callback) {
      this.callback = callback;
    }

    public void setErrorHandler(ErrorHandler<LowerIdentifier> handler) {
      this.handler = handler;
    }

    public void destroy() {
      if (logger.level <= Logger.INFO) logger.log("destroy()");
      tl.destroy();
    }
  }
  
  public UpperIdentity<UpperIdentifier, UpperMsgType> getUpperIdentity() {
    return upper; 
  }
  
  public void initUpperLayer(TransportLayer<UpperIdentifier, UpperMsgType> tl,
      LivenessProvider<UpperIdentifier> live,
      ProximityProvider<UpperIdentifier> prox) {
    if (upper != null) throw new IllegalStateException("upper already initialized:"+upper);
    upper = new UpperIdentityImpl(tl, live, prox);
  }

  
  class UpperIdentityImpl implements 
      UpperIdentity<UpperIdentifier, UpperMsgType>, 
      TransportLayerCallback<UpperIdentifier, UpperMsgType>, 
      LivenessListener<UpperIdentifier> {
    TransportLayer<UpperIdentifier, UpperMsgType> tl;    
    ProximityProvider<UpperIdentifier> prox;

    Collection<LivenessListener<UpperIdentifier>> livenessListeners; 
    Collection<PingListener<UpperIdentifier>> pingListeners;
    private ErrorHandler<UpperIdentifier> errorHandler;
    private TransportLayerCallback<UpperIdentifier, UpperMsgType> callback;
    Logger logger;
    private LivenessProvider<UpperIdentifier> livenessProvider;
    
    public UpperIdentityImpl(
        TransportLayer<UpperIdentifier, UpperMsgType> tl,
        LivenessProvider<UpperIdentifier> live,
        ProximityProvider<UpperIdentifier> prox) {
      this.tl = tl;
      this.livenessProvider = live;
      this.prox = prox;
      livenessListeners = new ArrayList<LivenessListener<UpperIdentifier>>();
      pingListeners = new ArrayList<PingListener<UpperIdentifier>>();
      logger = environment.getLogManager().getLogger(IdentityImpl.class, "upper");
      
      tl.setCallback(this);
      livenessProvider.addLivenessListener(this);
//      pinger.addPingListener(this);
    }
    
    public void clearState(UpperIdentifier i) {
      livenessProvider.clearState(i);
    }

    public SocketRequestHandle<UpperIdentifier> openSocket(
        UpperIdentifier i, 
        SocketCallback<UpperIdentifier> deliverSocketToMe, 
        Map<String, Integer> options) {
      if (logger.level <= Logger.FINE) logger.log("openSocket("+i+","+deliverSocketToMe+","+options+")");
      options = OptionsFactory.copyOptions(options);
      options.put(NODE_HANDLE_INDEX, addIntendedDest(i));      
      // the lower layer will handle this
      return tl.openSocket(i, deliverSocketToMe, options);
    }

    public MessageRequestHandle<UpperIdentifier, UpperMsgType> sendMessage(
        UpperIdentifier i, 
        UpperMsgType m, 
        MessageCallback<UpperIdentifier, UpperMsgType> deliverAckToMe, 
        Map<String, Integer> options) {
      if (logger.level <= Logger.FINE) logger.log("sendMessage("+i+","+m+","+options+")");

      // how to synchronized this properly?  It's too bad that we have to hold the lock for the calls into the lower levels.
      // an alternative would be to re-synchronized and check again, and immeadiately cancel the message

      synchronized(deadForever) {
        if (deadForever.contains(i)) {
          MessageRequestHandle<UpperIdentifier, UpperMsgType> mrh = new MessageRequestHandleImpl<UpperIdentifier, UpperMsgType>(i, m, options);
          deliverAckToMe.sendFailed(mrh, new NodeIsFaultyException(i, m));
          return mrh;
        }
      
        options = OptionsFactory.copyOptions(options);
        options.put(NODE_HANDLE_INDEX, addIntendedDest(i));      
        IdentityMessageHandle ret = new IdentityMessageHandle(i, m, options, deliverAckToMe);
        addPendingMessage(i, ret);
        ret.setSubCancellable(tl.sendMessage(i, m, ret, options));        
        return ret;        
      }      
    }
    
    public void incomingSocket(P2PSocket<UpperIdentifier> s) throws IOException {
      if (logger.level <= Logger.FINE) logger.log("incomingSocket("+s+")");
      callback.incomingSocket(s);
    }

    public void messageReceived(UpperIdentifier i, UpperMsgType m, Map<String, Integer> options) throws IOException {
      if (logger.level <= Logger.FINE) logger.log("messageReceived("+i+","+m+","+options+")");
      callback.messageReceived(i, m, options);
    }
    

    public boolean checkLiveness(UpperIdentifier i, Map<String, Integer> options) {
      if (logger.level <= Logger.FINE) logger.log("checkLiveness("+i+","+options+")");
      if (deadForever.contains(i)) return false;
      options = OptionsFactory.copyOptions(options);
      options.put(NODE_HANDLE_INDEX, addIntendedDest(i));      
      return livenessProvider.checkLiveness(i, options);
    }

    public int getLiveness(UpperIdentifier i, Map<String, Integer> options) {
      if (logger.level <= Logger.FINER) logger.log("getLiveness("+i+","+options+")");
      if (deadForever.contains(i)) return LIVENESS_DEAD_FOREVER;
      options = OptionsFactory.copyOptions(options);
      options.put(NODE_HANDLE_INDEX, addIntendedDest(i));      
      return livenessProvider.getLiveness(i, options);
    }

//    public boolean ping(UpperIdentifier i, Map<String, Integer> options) {      
//      if (logger.level <= Logger.FINE) logger.log("ping("+i+","+options+")");
//      if (deadForever.contains(i)) return false;
//      
//      return tl.ping(i, options);
//    }

    public void acceptMessages(boolean b) {
      tl.acceptMessages(b);
    }

    public void acceptSockets(boolean b) {
      tl.acceptSockets(b);
    }

    public UpperIdentifier getLocalIdentifier() {
      return tl.getLocalIdentifier();
    }

    public void setCallback(TransportLayerCallback<UpperIdentifier, UpperMsgType> callback) {
      this.callback = callback;
    }

    public void setErrorHandler(ErrorHandler<UpperIdentifier> handler) {
      this.errorHandler = handler;
    }

    public void destroy() {
      if (logger.level <= Logger.INFO) logger.log("destroy()");
      tl.destroy();
    }

    public int proximity(UpperIdentifier i) {
      if (logger.level <= Logger.FINE) logger.log("proximity("+i+")");
      if (deadForever.contains(i)) return Integer.MAX_VALUE;
      return prox.proximity(i);
    }

    public void addProximityListener(ProximityListener<UpperIdentifier> name) {
      prox.addProximityListener(name);
    }

    public boolean removeProximityListener(ProximityListener<UpperIdentifier> name) {
      return prox.removeProximityListener(name);
    }
    
    public void addLivenessListener(LivenessListener<UpperIdentifier> name) {
      synchronized(livenessListeners) {
        livenessListeners.add(name);
      }
    }

    public boolean removeLivenessListener(LivenessListener<UpperIdentifier> name) {
      synchronized(livenessListeners) {
        return livenessListeners.remove(name);
      }
    }

    public void addPingListener(PingListener<UpperIdentifier> name) {
      synchronized(pingListeners) {
        pingListeners.add(name);
      }
    }

    public boolean removePingListener(PingListener<UpperIdentifier> name) {
      synchronized(livenessListeners) {
        return pingListeners.remove(name);
      }
    }

    public void livenessChanged(UpperIdentifier i, int val) {
      if (deadForever.contains(i)) {
        if (val < LIVENESS_DEAD) if (logger.level <= Logger.SEVERE) logger.log("Node "+i+" came back from the dead!  It's a miracle! "+val+" Ignoring."); 
        return;
      }
      
      ArrayList<LivenessListener<UpperIdentifier>> temp;
      synchronized(livenessListeners) {
        temp = new ArrayList<LivenessListener<UpperIdentifier>>(livenessListeners);
      }
      for (LivenessListener<UpperIdentifier> l : temp) {
        l.livenessChanged(i, val); 
      }
    }

//    public void pingReceived(UpperIdentifier i, Map<String, Integer> options) {
//      if (deadForever.contains(i)) {
//        if (logger.level <= Logger.SEVERE) logger.log("Dead forever Node "+i+" pinged us! Ignoring."+options); 
//        return;
//      }
//      
//      ArrayList<PingListener<UpperIdentifier>> temp;
//      synchronized(pingListeners) {
//        temp = new ArrayList<PingListener<UpperIdentifier>>(pingListeners);
//      }
//      for (PingListener<UpperIdentifier> l : temp) {
//        l.pingReceived(i, options); 
//      }
//    }
//
//    public void pingResponse(UpperIdentifier i, int rtt, Map<String, Integer> options) {
//      if (deadForever.contains(i)) {
//        if (logger.level <= Logger.SEVERE) logger.log("Dead forever Node "+i+" responded to a ping! Ignoring. rtt: "+rtt+" options:"+options); 
//        return;
//      }
//      
//      ArrayList<PingListener<UpperIdentifier>> temp;
//      synchronized(pingListeners) {
//        temp = new ArrayList<PingListener<UpperIdentifier>>(pingListeners);
//      }
//      for (PingListener<UpperIdentifier> l : temp) {
//        l.pingResponse(i, rtt, options); 
//      }
//    }
  }
  
  class IdentityMessageHandle implements MessageRequestHandle<UpperIdentifier, UpperMsgType>, MessageCallback<UpperIdentifier, UpperMsgType> {

    private Cancellable subCancellable;
    private UpperIdentifier identifier;
    private UpperMsgType message;
    private Map<String, Integer> options;
    private MessageCallback<UpperIdentifier, UpperMsgType> deliverAckToMe;
    
    public IdentityMessageHandle(
        UpperIdentifier identifier, 
        UpperMsgType message, Map<String, Integer> options, 
        MessageCallback<UpperIdentifier, UpperMsgType> deliverAckToMe) {
      this.identifier = identifier;
      this.message = message;
      this.options = options;
      this.deliverAckToMe = deliverAckToMe; 
    }    

    public UpperIdentifier getIdentifier() {
      return identifier;
    }

    public UpperMsgType getMessage() {
      return message;
    }

    public Map<String, Integer> getOptions() {
      return options;
    }

    void deadForever() {
      cancel();
      if (deliverAckToMe != null) deliverAckToMe.sendFailed(this, new NodeIsFaultyException(identifier,message));
    }
    
    public boolean cancel() {
      pendingMessages.get(identifier).remove(this);
      return subCancellable.cancel();
    }

    public void setSubCancellable(Cancellable cancellable) {
      this.subCancellable = cancellable;
    }
    
    public Cancellable getSubCancellable() {
      return subCancellable;
    }

    public void ack(MessageRequestHandle<UpperIdentifier, UpperMsgType> msg) {
      pendingMessages.get(identifier).remove(this);
      if (deliverAckToMe != null) deliverAckToMe.ack(this);
    }

    public void sendFailed(MessageRequestHandle<UpperIdentifier, UpperMsgType> msg, IOException reason) {
      pendingMessages.get(identifier).remove(this);
      if (deliverAckToMe != null) deliverAckToMe.sendFailed(this, reason);
    }
  }
}