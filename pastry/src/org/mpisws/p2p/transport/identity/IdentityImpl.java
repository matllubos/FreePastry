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
import org.mpisws.p2p.transport.util.InsufficientBytesException;
import org.mpisws.p2p.transport.util.MessageRequestHandleImpl;
import org.mpisws.p2p.transport.util.OptionsFactory;
import org.mpisws.p2p.transport.util.SocketInputBuffer;
import org.mpisws.p2p.transport.util.SocketRequestHandleImpl;
import org.mpisws.p2p.transport.util.SocketWrapperSocket;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.Cancellable;
import rice.p2p.util.rawserialization.SimpleInputBuffer;
import rice.p2p.util.rawserialization.SimpleOutputBuffer;
import rice.pastry.socket.SocketNodeHandle;

public class IdentityImpl<UpperIdentifier, MiddleIdentifier, UpperMsgType, LowerIdentifier> {
  protected byte[] localIdentifier;
  
  protected LowerIdentityImpl lower;
  protected UpperIdentityImpl upper;
  
  protected Map<UpperIdentifier, Set<IdentityMessageHandle>> pendingMessages;
  protected Set<UpperIdentifier> deadForever;

  protected Environment environment;
  protected Logger logger;
  
  protected IdentitySerializer<UpperIdentifier, MiddleIdentifier, LowerIdentifier> serializer;
  protected NodeChangeStrategy<UpperIdentifier, LowerIdentifier> nodeChangeStrategy;
  protected SanityChecker<UpperIdentifier, MiddleIdentifier> sanityChecker;
  
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
  
  public static final String NODE_HANDLE_TO_INDEX = "identity.node_handle_to_index";
  public static final String NODE_HANDLE_FROM_INDEX = NODE_HANDLE_TO_INDEX;
//  public static final String NODE_HANDLE_FROM_INDEX = "identity.node_handle_from_index";
  
  public IdentityImpl(
      byte[] localIdentifier, 
      IdentitySerializer<UpperIdentifier, MiddleIdentifier, LowerIdentifier> serializer, 
      NodeChangeStrategy<UpperIdentifier, LowerIdentifier> nodeChangeStrategy,
      SanityChecker<UpperIdentifier, MiddleIdentifier> sanityChecker,
      Environment environment) {
    this.logger = environment.getLogManager().getLogger(IdentityImpl.class, null);
//    logger.log("IdentityImpl.ctor");
    this.sanityChecker = sanityChecker;
    if (sanityChecker == null) throw new IllegalArgumentException("SanityChecker is null");
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
    upper.notifyLivenessListeners(i, LivenessListener.LIVENESS_DEAD_FOREVER);
    deleteBindings(i);
    Set<IdentityMessageHandle> cancelMe = pendingMessages.remove(i);
    if (cancelMe != null) {
      for (IdentityMessageHandle msg : cancelMe) {
        msg.deadForever(); 
      }
    }
    upper.clearState(i);
  }
  
  /**
   * Returns the identifier.
   * 
   * @param i
   * @return
   */
  protected int addIntendedDest(UpperIdentifier i) {
    synchronized(intendedDest) {
      if (reverseIntendedDest.containsKey(i)) return reverseIntendedDest.get(i);
      intendedDest.put(intendedDestCtr, i);
      reverseIntendedDest.put(i, intendedDestCtr);
      intendedDestCtr++;
      if (logger.level <= Logger.FINER) {
        logger.log("addIntendedDest("+i+" hash:"+i.hashCode()+"):"+(intendedDestCtr-1));
        if (i instanceof SocketNodeHandle) {
          SocketNodeHandle snh = (SocketNodeHandle)i;
          if (snh.getId().toString().startsWith("<0x000")) {
            logger.logException("StackTrace snh:"+i+" epoch:"+snh.getEpoch(), new Exception("foo"));
          }
        }
      }
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
      int index = options.get(NODE_HANDLE_TO_INDEX);
      final UpperIdentifier dest = intendedDest.get(index);
      if (logger.level <= Logger.FINE) logger.log("openSocket("+i+") dest:"+dest);
      
      final ByteBuffer buf;
      try {
        SimpleOutputBuffer sob = new SimpleOutputBuffer((int)(localIdentifier.length*2.5)); // good estimate
        serializer.serialize(sob, dest);
        sob.write(localIdentifier);
//        logger.log("writing:"+Arrays.toString(sob.getBytes()));
        buf = ByteBuffer.wrap(sob.getBytes());
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
      if (logger.level <= Logger.FINE) logger.log("incomingSocket("+s+")");
      s.register(true, false, new P2PSocketReceiver<LowerIdentifier>() {
        ByteBuffer buf = ByteBuffer.allocate(localIdentifier.length);

        public void receiveException(P2PSocket<LowerIdentifier> socket, IOException ioe) {
          handler.receivedException(socket.getIdentifier(), ioe);
        }

        public void receiveSelectResult(P2PSocket<LowerIdentifier> socket, boolean canRead, boolean canWrite) throws IOException {
          if (canWrite) throw new IOException("Never asked to write!");
          if (!canRead) throw new IOException("Can't read!");
          
          // read the TO field
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
            // the TO was me, now read the FROM, and add the proper index into the options
            final SocketInputBuffer sib = new SocketInputBuffer(socket, 1024);
            new P2PSocketReceiver<LowerIdentifier>() {
              public void receiveException(P2PSocket<LowerIdentifier> socket, IOException ioe) {
                handler.receivedException(socket.getIdentifier(), ioe);
              }

              public void receiveSelectResult(P2PSocket<LowerIdentifier> socket, boolean canRead, boolean canWrite) throws IOException {
                if (canWrite) throw new IOException("Never asked to write!");
                if (!canRead) throw new IOException("Can't read!");
                
                final Map<String, Integer> newOptions = OptionsFactory.copyOptions(socket.getOptions());
                try {
                  // add to intendedDest, add option index                  
                  UpperIdentifier from = serializer.deserialize(sib, socket.getIdentifier());                  
                  newOptions.put(NODE_HANDLE_FROM_INDEX, addIntendedDest(from));
                } catch (InsufficientBytesException ibe) {
                  socket.register(true, false, this); 
                }
                
                // once we are here, we have succeeded in deserializing the NodeHanlde, and added it to the new options
                
                // now write Success
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
                    
                    if (writeMe.hasRemaining()) {
                      // need to read more
                      socket.register(false, true, this);
                      return;
                    }

                    // done writing pass up socket with the newOptions
                    final P2PSocket<LowerIdentifier> returnMe = new SocketWrapperSocket<LowerIdentifier, LowerIdentifier>(
                        socket.getIdentifier(), socket, logger, newOptions);
                    callback.incomingSocket(returnMe);
                  }
                });
              }              
            }.receiveSelectResult(socket, canRead, canWrite);
            
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
        if (logger.level <= Logger.FINE) {
          logger.log("sendMessage("+i+","+m+")");        
        }
      }

      // what happens if they cancel after the socket has been received by a lower layer, but is still reading the header?  
      // May need to re-think this SRHI at all the layers
      final MessageRequestHandleImpl<LowerIdentifier, ByteBuffer> ret = 
        new MessageRequestHandleImpl<LowerIdentifier, ByteBuffer>(i, m, options);
   
      Integer index = null;
      if (options != null) {
        index = options.get(NODE_HANDLE_TO_INDEX);
      }
      
      byte[] msgWithHeader;
      if (index == null) {
        // don't include an id
        msgWithHeader = new byte[1+localIdentifier.length+m.remaining()];    
        msgWithHeader[0] = NO_ID;        
        
        System.arraycopy(localIdentifier, 0, msgWithHeader, 1, localIdentifier.length); // write the FROM
        m.get(msgWithHeader, 1+localIdentifier.length, m.remaining()); // write the message

//        m.get(msgWithHeader, 1, m.remaining());
      } else {
        // don't include an id
        UpperIdentifier dest = intendedDest.get(index.intValue());
      
        addBinding(dest, i);
        
        byte[] destBytes;
        try {          
          SimpleOutputBuffer sob = new SimpleOutputBuffer((int)(localIdentifier.length*2.5)); // good estimate
          serializer.serialize(sob, dest);
          destBytes = sob.getBytes();
        } catch (IOException ioe) {
          deliverAckToMe.sendFailed(ret, ioe);
          return ret;
        }
        
        // build a new ByteBuffer with the header      
        msgWithHeader = new byte[1+destBytes.length+localIdentifier.length+m.remaining()];
        msgWithHeader[0] = NORMAL; // write the TYPE
        System.arraycopy(destBytes, 0, msgWithHeader, 1, destBytes.length); // write the TO
        System.arraycopy(localIdentifier, 0, msgWithHeader, 1+destBytes.length, localIdentifier.length); // write the FROM
        m.get(msgWithHeader, 1+destBytes.length+localIdentifier.length, m.remaining()); // write the message
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
      Map<String, Integer> newOptions = new HashMap<String, Integer>(options);
      
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
            return;
          }          
          
        case NO_ID:
          SimpleInputBuffer sib = new SimpleInputBuffer(m.array(),m.position());
          UpperIdentifier from = serializer.deserialize(sib, i);
          m.position(m.array().length - sib.bytesRemaining());

          newOptions.put(NODE_HANDLE_FROM_INDEX, addIntendedDest(from));          
          // continue to read the rest of the message
          
          // it's for me, no problem
          if (logger.level <= Logger.FINEST) {
            byte[] b = new byte[m.remaining()];
            System.arraycopy(m.array(), m.position(), b, 0, b.length);
            logger.log(
              "received message for me from:"+from+"("+from+"("+i+")) "+Arrays.toString(b));
          } else {
            if (logger.level <= Logger.FINER) 
              logger.log("received message for me from:"+from+"("+i+") "+m);            
          }
          callback.messageReceived(i, m, newOptions);
          break;
        case INCORRECT_IDENTITY:
  
          // it's an error, read it in
          UpperIdentifier oldDest = null;
          List<UpperIdentifier> list = bindings.get(i);
          if (list != null) {
            oldDest = list.get(0);
          }
          
          if (oldDest != null) {
            UpperIdentifier newDest = serializer.deserialize(new SimpleInputBuffer(m.array(),m.position()), i);
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
                  setDeadForever(oldDest);
                  upper.notifyLivenessListeners(newDest, LivenessListener.LIVENESS_ALIVE);
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
  
  public void initUpperLayer(UpperIdentifier localIdentifier, TransportLayer<MiddleIdentifier, UpperMsgType> tl,
      LivenessProvider<MiddleIdentifier> live,
      ProximityProvider<MiddleIdentifier> prox) {
    if (upper != null) throw new IllegalStateException("upper already initialized:"+upper);
    upper = new UpperIdentityImpl(localIdentifier, tl, live, prox);
  }

  
  class UpperIdentityImpl implements 
      UpperIdentity<UpperIdentifier, UpperMsgType>, 
      TransportLayerCallback<MiddleIdentifier, UpperMsgType>, 
      LivenessListener<MiddleIdentifier>,
      ProximityListener<MiddleIdentifier> {
    TransportLayer<MiddleIdentifier, UpperMsgType> tl;    
    ProximityProvider<MiddleIdentifier> prox;

    private ErrorHandler<UpperIdentifier> errorHandler;
    private TransportLayerCallback<UpperIdentifier, UpperMsgType> callback;
    Logger logger;
    private LivenessProvider<MiddleIdentifier> livenessProvider;
    private UpperIdentifier localIdentifier;
    
    public UpperIdentityImpl(
        UpperIdentifier local,
        TransportLayer<MiddleIdentifier, UpperMsgType> tl,
        LivenessProvider<MiddleIdentifier> live,
        ProximityProvider<MiddleIdentifier> prox) {
      this.localIdentifier = local;
      this.tl = tl;
      this.livenessProvider = live;
      this.prox = prox;
      logger = environment.getLogManager().getLogger(IdentityImpl.class, "upper");
      
      tl.setCallback(this);
      livenessProvider.addLivenessListener(this);
      prox.addProximityListener(this);
//      pinger.addPingListener(this);
    }
    
    public void clearState(UpperIdentifier i) {
      livenessProvider.clearState(serializer.translateDown(i));
    }

    public SocketRequestHandle<UpperIdentifier> openSocket(
        final UpperIdentifier i, 
        final SocketCallback<UpperIdentifier> deliverSocketToMe, 
        final Map<String, Integer> options) {
      if (logger.level <= Logger.FINE) logger.log("openSocket("+i+","+deliverSocketToMe+","+options+")");
      final SocketRequestHandleImpl<UpperIdentifier> handle = 
        new SocketRequestHandleImpl<UpperIdentifier>(i, options);
      
      Map<String, Integer> newOptions = OptionsFactory.copyOptions(options);
      newOptions.put(NODE_HANDLE_TO_INDEX, addIntendedDest(i));      


      handle.setSubCancellable(tl.openSocket(serializer.translateDown(i), new SocketCallback<MiddleIdentifier>(){
        public void receiveException(SocketRequestHandle<MiddleIdentifier> s, IOException ex) {
          deliverSocketToMe.receiveException(handle, ex);
        }
        public void receiveResult(SocketRequestHandle<MiddleIdentifier> cancellable, P2PSocket<MiddleIdentifier> sock) {
          deliverSocketToMe.receiveResult(handle, new SocketWrapperSocket<UpperIdentifier, MiddleIdentifier>(i,sock,logger,options));
        }      
      }, newOptions));
      return handle;
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
          MessageRequestHandle<UpperIdentifier, UpperMsgType> mrh =             
            new MessageRequestHandleImpl<UpperIdentifier, UpperMsgType>(i, m, options);
          deliverAckToMe.sendFailed(mrh, new NodeIsFaultyException(i, m));
          return mrh;
        }
      
        options = OptionsFactory.copyOptions(options);
        options.put(NODE_HANDLE_TO_INDEX, addIntendedDest(i));      
        IdentityMessageHandle ret = new IdentityMessageHandle(i, m, options, deliverAckToMe);
        addPendingMessage(i, ret);
        ret.setSubCancellable(tl.sendMessage(serializer.translateDown(i), m, ret, options));        
        return ret;        
      }      
    }
    
    public void incomingSocket(P2PSocket<MiddleIdentifier> s) throws IOException {
      if (logger.level <= Logger.FINE) logger.log("incomingSocket("+s+")");
      int index = s.getOptions().get(NODE_HANDLE_FROM_INDEX);
      final UpperIdentifier from = intendedDest.get(index);

      if (sanityChecker.isSane(from, s.getIdentifier())) {
        callback.incomingSocket(new SocketWrapperSocket<UpperIdentifier, MiddleIdentifier>(from, s, logger, s.getOptions()));
      } else {
        if (logger.level <= Logger.WARNING) logger.logException(
            "incomingSocket() Sanity checker did not match "+from+" to "+s.getIdentifier()+" options:"+s.getOptions(), 
            new Exception("Stack Trace"));
        s.close();
      }
    }

    public void messageReceived(MiddleIdentifier i, UpperMsgType m, Map<String, Integer> options) throws IOException {
      if (logger.level <= Logger.FINE) logger.log("messageReceived("+i+","+m+","+options+")");
      int index = options.get(NODE_HANDLE_FROM_INDEX);
      final UpperIdentifier from = intendedDest.get(index);

      if (sanityChecker.isSane(from, i)) {
        callback.messageReceived(from, m, options);
      } else {
        if (logger.level <= Logger.WARNING) logger.logException(
            "messageReceived() Sanity checker did not match "+from+" to "+i+" options:"+options, 
            new Exception("Stack Trace"));
      }
    }
    

    public boolean checkLiveness(UpperIdentifier i, Map<String, Integer> options) {
      if (logger.level <= Logger.FINE) logger.log("checkLiveness("+i+","+options+")");
      if (deadForever.contains(i)) return false;
      options = OptionsFactory.copyOptions(options);
      options.put(NODE_HANDLE_TO_INDEX, addIntendedDest(i));      
      return livenessProvider.checkLiveness(serializer.translateDown(i), options);
    }

    List<LivenessListener<UpperIdentifier>> livenessListeners = new ArrayList<LivenessListener<UpperIdentifier>>();
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
    
    public int getLiveness(UpperIdentifier i, Map<String, Integer> options) {
      if (logger.level <= Logger.FINER) logger.log("getLiveness("+i+","+options+")");
      if (deadForever.contains(i)) return LIVENESS_DEAD_FOREVER;
      options = OptionsFactory.copyOptions(options);
      options.put(NODE_HANDLE_TO_INDEX, addIntendedDest(i));      
      
      return livenessProvider.getLiveness(serializer.translateDown(i), options);
    }

    public void livenessChanged(MiddleIdentifier i, int val) {
      if (deadForever.contains(i)) {
        if (val < LIVENESS_DEAD) {
          if (logger.level <= Logger.SEVERE) logger.log("Node "+i+" came back from the dead!  It's a miracle! "+val+" Ignoring."); 
        }
        return;
      }
      UpperIdentifier upper = serializer.translateUp(i);
      notifyLivenessListeners(upper, val);          
    }


    
    private void notifyLivenessListeners(UpperIdentifier i, int liveness) {
      if (logger.level <= Logger.FINER) logger.log("notifyLivenessListeners("+i+","+liveness+")");
      List<LivenessListener<UpperIdentifier>> temp;
      synchronized(livenessListeners) {
        temp = new ArrayList<LivenessListener<UpperIdentifier>>(livenessListeners);
      }
      for (LivenessListener<UpperIdentifier> listener : temp) {
        listener.livenessChanged(i, liveness);
      }
    }
    
    Collection<ProximityListener<UpperIdentifier>> proxListeners = 
      new ArrayList<ProximityListener<UpperIdentifier>>();
    public void addProximityListener(ProximityListener<UpperIdentifier> name) {
      synchronized(proxListeners) {
        proxListeners.add(name);
      }
    }

    public boolean removeProximityListener(ProximityListener<UpperIdentifier> name) {
      synchronized(proxListeners) {
        return proxListeners.remove(name);
      }
    }
    
    public int proximity(UpperIdentifier i) {
      if (logger.level <= Logger.FINE) logger.log("proximity("+i+")");
      if (deadForever.contains(i)) return Integer.MAX_VALUE;
      return prox.proximity(serializer.translateDown(i));
    }

    public void proximityChanged(MiddleIdentifier i, int newProx, Map<String, Integer> options) {
      notifyProximityListeners(serializer.translateUp(i), newProx, options);    
    }
    
    private void notifyProximityListeners(UpperIdentifier i, int newProx, Map<String, Integer> options) {
      if (logger.level <= Logger.FINER) logger.log("notifyProximityListeners("+i+","+newProx+")");
      List<ProximityListener<UpperIdentifier>> temp;
      synchronized(proxListeners) {
        temp = new ArrayList<ProximityListener<UpperIdentifier>>(proxListeners);
      }
      for (ProximityListener<UpperIdentifier> listener : temp) {
        listener.proximityChanged(i, newProx, options);
      }
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
      return localIdentifier;
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
  
  class IdentityMessageHandle implements MessageRequestHandle<UpperIdentifier, UpperMsgType>, MessageCallback<MiddleIdentifier, UpperMsgType> {

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

    public void ack(MessageRequestHandle<MiddleIdentifier, UpperMsgType> msg) {
      pendingMessages.get(identifier).remove(this);
      if (deliverAckToMe != null) deliverAckToMe.ack(this);
    }

    public void sendFailed(MessageRequestHandle<MiddleIdentifier, UpperMsgType> msg, IOException reason) {
      pendingMessages.get(identifier).remove(this);
      if (deliverAckToMe != null) deliverAckToMe.sendFailed(this, reason);
    }
  }
}
