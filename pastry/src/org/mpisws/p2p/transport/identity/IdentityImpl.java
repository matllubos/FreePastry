/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
package org.mpisws.p2p.transport.identity;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
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

import org.mpisws.p2p.transport.ClosedChannelException;
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
import org.mpisws.p2p.transport.liveness.LivenessTypes;
import org.mpisws.p2p.transport.liveness.OverrideLiveness;
import org.mpisws.p2p.transport.liveness.PingListener;
import org.mpisws.p2p.transport.liveness.Pinger;
import org.mpisws.p2p.transport.proximity.ProximityListener;
import org.mpisws.p2p.transport.proximity.ProximityProvider;
import org.mpisws.p2p.transport.util.DefaultErrorHandler;
import org.mpisws.p2p.transport.util.InsufficientBytesException;
import org.mpisws.p2p.transport.util.MessageRequestHandleImpl;
import org.mpisws.p2p.transport.util.OptionsFactory;
import org.mpisws.p2p.transport.util.SocketInputBuffer;
import org.mpisws.p2p.transport.util.SocketRequestHandleImpl;
import org.mpisws.p2p.transport.util.SocketWrapperSocket;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.Cancellable;
import rice.p2p.util.TimerWeakHashMap;
import rice.p2p.util.rawserialization.SimpleInputBuffer;
import rice.p2p.util.rawserialization.SimpleOutputBuffer;
import rice.pastry.socket.SocketNodeHandle;

public class IdentityImpl<UpperIdentifier, MiddleIdentifier, UpperMsgType, LowerIdentifier> implements LivenessTypes {
  protected byte[] localIdentifier;
  
  protected LowerIdentityImpl lower;
  protected UpperIdentityImpl upper;
  
  protected Map<UpperIdentifier, Set<IdentityMessageHandle>> pendingMessages;
  protected Set<UpperIdentifier> deadForever;  // TODO: make TimerWeakHashSet

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
  protected Map<LowerIdentifier, UpperIdentifier> bindings;  // this one may be difficult as the Upper has a ref to the lower
  
  /**
   * Held in the options map of the message/socket.  This is a pointer from the upper 
   * level to the lower level.
   */
  Map<Integer, WeakReference<UpperIdentifier>> intendedDest; // currently a very small memory leak
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
    
    this.intendedDest = new HashMap<Integer, WeakReference<UpperIdentifier>>(); // this is a memory leak, but very slow, maybe we should make a periodic iterator to clean this out...
    this.reverseIntendedDest = new TimerWeakHashMap<UpperIdentifier, Integer>(environment.getSelectorManager(), 300000);
    
    this.bindings = new HashMap<LowerIdentifier, UpperIdentifier>();
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
  
  public void removePendingMessage(UpperIdentifier i, IdentityMessageHandle ret) {
    synchronized(pendingMessages) {
      Set<IdentityMessageHandle> set = pendingMessages.get(i);
      if (set == null) {
        return;
      }
      set.remove(ret);
      if (set.isEmpty()) {
        pendingMessages.remove(i); 
      }
    }     
  }
  
  public void printMemStats(int logLevel) {
    if (logLevel <= Logger.FINE) {
      synchronized(pendingMessages) {
        int queueSum = 0;
        for(UpperIdentifier i : pendingMessages.keySet()) {
          Set<IdentityMessageHandle> theSet = pendingMessages.get(i);
          int queueSize = 0;
          if (theSet != null) {
            queueSize = theSet.size();
          }
          queueSum+=queueSize;
          if (logLevel <= Logger.FINER) {            
            logger.log("PM{"+i+","+upper.getLiveness(i, null)+"} queue:"+queueSize);
          }
        }        
        logger.log("NumUpperIds:"+pendingMessages.size()+" numPendingMsgs:"+queueSum);
      } // synchronized
    }
  }
  
  OverrideLiveness<LowerIdentifier> overrideLiveness;
  public void setOverrideLiveness(OverrideLiveness<LowerIdentifier> ol) {
    this.overrideLiveness = ol;
  }
  
  public void setDeadForever(LowerIdentifier l, UpperIdentifier i, Map<String, Integer> options) {
    if (deadForever.contains(i)) return;
    if (logger.level <= Logger.INFO) logger.logException("setDeadForever("+l+","+i+","+options+")",new Exception("Stack Trace"));
    deadForever.add(i);
    try {
      logger.log("setDeadForever("+l+","+i+","+(options == null)+"):overL:"+overrideLiveness+" "+(reverseIntendedDest == null));
      Map<String, Integer> o2 = OptionsFactory.addOption(options, NODE_HANDLE_FROM_INDEX, reverseIntendedDest.get(i));
      overrideLiveness.setLiveness(l, LIVENESS_DEAD_FOREVER, o2);
    } catch (NullPointerException npe) {
      logger.log("setDeadForever("+l+","+i+","+options+"):overL:"+overrideLiveness+" "+reverseIntendedDest);
      throw npe;
    }
//    upper.notifyLivenessListeners(i, LIVENESS_DEAD_FOREVER, options);  // now called as a result of overrideLiveness
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
      if (reverseIntendedDest.containsKey(i)) {
        int ret = reverseIntendedDest.get(i);
        // make sure that the value in memory is there (not garbage collected)
        if (intendedDest.get(ret).get() == null) {
          intendedDest.put(ret, new WeakReference<UpperIdentifier>(i));
        }
        return ret;
      }
      intendedDest.put(intendedDestCtr, new WeakReference<UpperIdentifier>(i));
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
  
  protected UpperIdentifier getIntendedDest(Map<String,Integer> options) {
    if (options == null) throw new IllegalArgumentException("options is null");
    if (!options.containsKey(NODE_HANDLE_FROM_INDEX)) throw new IllegalArgumentException("options doesn't have NODE_HANDLE_FROM_INDEX "+options);
    int index = options.get(NODE_HANDLE_FROM_INDEX);
    WeakReference<UpperIdentifier> ret1 = intendedDest.get(index);
    if (ret1 == null) throw new IllegalArgumentException("No record of NODE_HANDLE_FROM_INDEX "+index);
    UpperIdentifier ret = ret1.get();
    if (ret == null) {
      if (logger.level <= Logger.WARNING) logger.log("Memory already collected for NODE_HANDLE_FROM_INDEX "+index);
    }
    return ret;
  }

  /**
   * 
   * @param u
   * @param l
   * @param options
   * @return false if the new binding is actually old (IE, don't upgrade it)
   */
  protected boolean addBinding(UpperIdentifier u, LowerIdentifier l, Map<String, Integer> options) {
    synchronized(bindings) {
      if (deadForever.contains(u)) return false;
      
      UpperIdentifier old = bindings.get(l);
      if (old == null) {
        bindings.put(l, u);
        overrideLiveness.setLiveness(l, LIVENESS_ALIVE, OptionsFactory.addOption(options, NODE_HANDLE_FROM_INDEX, addIntendedDest(u)));
        return true;
      } else {
        if (old.equals(u)) {
          overrideLiveness.setLiveness(l, LIVENESS_ALIVE, OptionsFactory.addOption(options, NODE_HANDLE_FROM_INDEX, addIntendedDest(u)));
          return true;
        }
        
        // they are different        
        if (destinationChanged(old, u, l, options)) {
          bindings.put(l, u);      
          overrideLiveness.setLiveness(l, LIVENESS_ALIVE, OptionsFactory.addOption(options, NODE_HANDLE_FROM_INDEX, addIntendedDest(u)));
          return true;
        } else {          
          // mark the new one as faulty
          if (logger.level <= Logger.WARNING) logger.log("The nodeChangeStrategy found identifier "+u+
              " to be stale.  Should be using "+old);
//          setDeadForever(l, u, options);  
          return false;
        }
      }
    }
  }
  
  /**
   * Return true if the new one is a valid replacement of the old
   * 
   * True if they are the same
   * 
   * @param oldDest
   * @param newDest
   * @param i
   * @return
   */
  public boolean destinationChanged(UpperIdentifier oldDest, UpperIdentifier newDest, LowerIdentifier i, Map<String, Integer> options) {
    if (oldDest.equals(newDest)) {
//            if (logger.level <= Logger.FINE) logger.log("1");
      // don't do anything 
      return true;
    } else {
//            if (logger.level <= Logger.FINE) logger.log("2");
      if (deadForever.contains(oldDest)) {
        return true;
//              if (logger.level <= Logger.FINE) logger.log("3");              
      } else {
//              if (logger.level <= Logger.FINE) logger.log("4");
        if (nodeChangeStrategy.canChange(oldDest, newDest, i)) {
//                if (logger.level <= Logger.FINE) logger.log("5");
          if (logger.level <= Logger.INFO) logger.log("destinationChanged("+oldDest+"->"+newDest+","+i+","+options+")");
          setDeadForever(i, oldDest, options);   
          return true;
        } else {
          return false;
        }
      }
    }      
  }
    

  public void initLowerLayer(TransportLayer<LowerIdentifier, ByteBuffer> tl, ErrorHandler<LowerIdentifier> handler) {
    lower = new LowerIdentityImpl(tl, handler);
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
        TransportLayer<LowerIdentifier, ByteBuffer> tl,
        ErrorHandler<LowerIdentifier> handler) {
      this.tl = tl;
      logger = environment.getLogManager().getLogger(IdentityImpl.class, "lower");
      if (handler != null) {
        this.handler = handler;        
      } else {
        this.handler = new DefaultErrorHandler<LowerIdentifier>(logger);        
      }
            
      tl.setCallback(this);
    }

    public SocketRequestHandle<LowerIdentifier> openSocket(
        final LowerIdentifier i, 
        final SocketCallback<LowerIdentifier> deliverSocketToMe, 
        final Map<String, Integer> options) {      
      // what happens if they cancel after the socket has been received by a lower layer, but is still reading the header?  
      // May need to re-think this SRHI at all the layers
      final SocketRequestHandleImpl<LowerIdentifier> ret = new SocketRequestHandleImpl<LowerIdentifier>(i, options, logger);
      final UpperIdentifier dest = getIntendedDest(options);
      
      if (dest == null) {
        deliverSocketToMe.receiveException(ret, new MemoryExpiredException("No record of the upper identifier for "+i+" index="+options.get(NODE_HANDLE_FROM_INDEX))); 
        return ret;
      }

      if (logger.level <= Logger.FINE) logger.log("openSocket1("+i+") dest:"+dest);

      if (addBinding(dest, i, options)) {
        // no problem, sending message
      } else {
        deliverSocketToMe.receiveException(ret, new NodeIsFaultyException(i));
        return ret;
      }

      if (logger.level <= Logger.FINE) logger.log("openSocket2("+i+") dest:"+dest);
      
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

            public void receiveResult(SocketRequestHandle<LowerIdentifier> cancellable, final P2PSocket<LowerIdentifier> sock) {
              ret.setSubCancellable(new Cancellable() {              
                public boolean cancel() {
                  sock.close();
                  return true;
                }              
              });
              sock.register(false, true, new P2PSocketReceiver<LowerIdentifier>() {

                public void receiveSelectResult(P2PSocket<LowerIdentifier> socket, boolean canRead, boolean canWrite) throws IOException {
                  if (canRead) throw new IOException("Never asked to read!");
                  if (!canWrite) throw new IOException("Can't write!");
                  if (socket.write(buf) < 0)  {
                    deliverSocketToMe.receiveException(ret, new ClosedChannelException("Remote node closed socket while opening.  Try again."));
                    return;
                  }
                  if (buf.hasRemaining()) {
                    socket.register(false, true, this);
                  } else {
                    // wait for the response
                    socket.register(true, false, new P2PSocketReceiver<LowerIdentifier>() {
                      ByteBuffer responseBuffer = ByteBuffer.allocate(1);

                      public void receiveException(P2PSocket<LowerIdentifier> socket, IOException ioe) {
                        deliverSocketToMe.receiveException(ret, ioe);                        
                      }

                      public void receiveSelectResult(final P2PSocket<LowerIdentifier> socket, boolean canRead, boolean canWrite) throws IOException {
                        if (!canRead) throw new IOException("Can't read!");
                        if (canWrite) throw new IOException("Never asked to write!");
                        if (socket.read(responseBuffer) == -1) {
                          // socket unexpectedly closed
                          socket.close();
                          deliverSocketToMe.receiveException(ret, new ClosedChannelException("Remote node closed socket while opening.  Try again."));                           
                          return;
                        }
                        
                        if (responseBuffer.remaining() > 0) {
                          socket.register(true, false, this);
                        } else {
                          byte answer = responseBuffer.array()[0];
                          if (answer == FAILURE) {
                            // wrong address, read more 
                            if (logger.level <= Logger.INFO) logger.log("openSocket("+i+","+deliverSocketToMe+") answer = FAILURE");
//                            setDeadForever(dest);
                            UpperIdentifier newDest = serializer.deserialize(new SocketInputBuffer(socket, localIdentifier.length), i);
                            
                            addBinding(newDest, i, options);
                            
                            // need to do this so the boostrapper knows the proper identity
                            upper.notifyLivenessListeners(newDest, LIVENESS_ALIVE, options);
                            deliverSocketToMe.receiveException(ret, new NodeIsFaultyException(i));
                          } else {
                            ret.setSubCancellable(new Cancellable() {
                              public boolean cancel() {
                                throw new IllegalStateException("Can't cancel, already delivered. ret:"+ret+" sock:"+socket);
//                                return false;
                              }
                            }); // can't cancel any more
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

            public void receiveException(SocketRequestHandle<LowerIdentifier> s, IOException ex) {
              deliverSocketToMe.receiveException(ret, ex);
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

        public void receiveSelectResult(final P2PSocket<LowerIdentifier> socket, boolean canRead, boolean canWrite) throws IOException {
          if (canWrite) throw new IOException("Never asked to write!");
          if (!canRead) throw new IOException("Can't read!");
          
          // read the TO field
          if (socket.read(buf) == -1) {
            // socket closed
            if (logger.level <= Logger.INFO) handler.receivedException(socket.getIdentifier(), new IOException("Socket closed while incoming."));
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
                
                UpperIdentifier from;
                try {
                  // add to intendedDest, add option index                  
                  from = serializer.deserialize(sib, socket.getIdentifier());                  
                  newOptions.put(NODE_HANDLE_FROM_INDEX, addIntendedDest(from));
                } catch (InsufficientBytesException ibe) {
                  socket.register(true, false, this); 
                  return;
                }
                
                // once we are here, we have succeeded in deserializing the NodeHanlde, and added it to the new options
                if (addBinding(from, socket.getIdentifier(), socket.getOptions())) {
                  // no problem, sending message
                } else {
                  // this is bad, a previous instance is trying to open a socket
                  if (logger.level <= Logger.WARNING) 
                    logger.log("Serious error.  There was an attempt to open a socket from a supposedly stale identifier:"+
                        from+". Current identifier is "+bindings.get(socket.getIdentifier())+" lower:"+socket.getIdentifier());
                  socket.close();
                  return;
                }

                
                // now write Success
                byte[] result = {SUCCESS};
                final ByteBuffer writeMe = ByteBuffer.wrap(result);
                socket.register(false, true, new P2PSocketReceiver<LowerIdentifier>() {
                  public void receiveException(P2PSocket<LowerIdentifier> socket, IOException ioe) {
                    if (logger.level <= Logger.INFO) handler.receivedException(socket.getIdentifier(), ioe);                
                  }

                  public void receiveSelectResult(P2PSocket<LowerIdentifier> socket, boolean canRead, boolean canWrite) throws IOException {
                    if (canRead) throw new IOException("Not expecting to read.");
                    if (!canWrite) throw new IOException("Expecting to write.");
                    
                    if (socket.write(writeMe) == -1) {
                      // socket closed
                      if (logger.level <= Logger.INFO) handler.receivedException(socket.getIdentifier(), new ClosedChannelException("Error on incoming socket."));
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
            byte[] result = new byte[1+localIdentifier.length];
            result[0] = FAILURE;
            System.arraycopy(localIdentifier, 0, result, 1, localIdentifier.length);
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
                  if (logger.level <= Logger.INFO) handler.receivedException(socket.getIdentifier(), new ClosedChannelException("Error on incoming socket."));
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
        final Map<String, Integer> options) {

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
   
//      Integer index = null;
//      if (options != null) {
//        index = options.get(NODE_HANDLE_TO_INDEX);
//      }
      
      byte[] msgWithHeader;
      if (options.containsKey(NODE_HANDLE_TO_INDEX)) {
        // don't include an id
        msgWithHeader = new byte[1+localIdentifier.length+m.remaining()];    
        msgWithHeader[0] = NO_ID;        
        
        System.arraycopy(localIdentifier, 0, msgWithHeader, 1, localIdentifier.length); // write the FROM
        m.get(msgWithHeader, 1+localIdentifier.length, m.remaining()); // write the message

//        m.get(msgWithHeader, 1, m.remaining());
      } else {
        // don't include an id
        UpperIdentifier dest = getIntendedDest(options);
      
        if (dest == null) {
          if (deliverAckToMe != null) deliverAckToMe.sendFailed(ret, new MemoryExpiredException("No record of the upper identifier for "+i+" index="+options.get(NODE_HANDLE_TO_INDEX))); 
          return ret;
        }

        if (addBinding(dest, i, options)) {
          // no problem, sending message
        } else {
          deliverAckToMe.sendFailed(ret, new NodeIsFaultyException(i, m));
          return ret;
        }
        
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
      Map<String, Integer> newOptions = OptionsFactory.copyOptions(options);
      
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

          if (addBinding(from, i, options)) {
//            from = serializer.coalesce(from);
            
            // need to do this so the boostrapper knows the proper identity
//            overrideLiveness.setLiveness(i, LIVENESS_ALIVE, OptionsFactory.addOption(options, NODE_HANDLE_FROM_INDEX, addIntendedDest(from)));
          } else {
            if (logger.level <= Logger.WARNING) logger.log("Warning.  Received message from stale identifier:"+
                from+". Current identifier is "+bindings.get(i)+" lower:"+i+" Probably a delayed message, dropping.");
            handler.receivedUnexpectedData(i, m.array(), m.position(), newOptions);
            return;
          }
          
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
          UpperIdentifier oldDest = bindings.get(i);
          
          UpperIdentifier newDest = serializer.deserialize(new SimpleInputBuffer(m.array(),m.position()), i);
          if (logger.level <= Logger.INFO) logger.log(
              "received INCORRECT_IDENTITY:"+i+
              " old:"+oldDest+
              " new:"+newDest);
          
          if (addBinding(newDest, i, options)) { // should call setDeadForever
            
//            newDest = serializer.coalesce(newDest);
            
            // need to do this so the boostrapper knows the proper identity
//            overrideLiveness.setLiveness(i, LIVENESS_ALIVE, OptionsFactory.addOption(options, NODE_HANDLE_FROM_INDEX, addIntendedDest(newDest)));
            //          upper.notifyLivenessListeners(newDest, LIVENESS_ALIVE, options);
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
      ProximityProvider<MiddleIdentifier> prox,
      OverrideLiveness<LowerIdentifier> overrideLiveness) {
    if (upper != null) throw new IllegalStateException("upper already initialized:"+upper);    
    upper = new UpperIdentityImpl(localIdentifier, tl, live, prox);
    
    setOverrideLiveness(overrideLiveness);
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
        new SocketRequestHandleImpl<UpperIdentifier>(i, options, logger);

      synchronized(deadForever) {
        if (deadForever.contains(i)) {
          deliverSocketToMe.receiveException(handle, new NodeIsFaultyException(i));
          return handle;
        }
      }
      
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

      IdentityMessageHandle ret;
      synchronized(deadForever) {
        if (deadForever.contains(i)) {
          MessageRequestHandle<UpperIdentifier, UpperMsgType> mrh =             
            new MessageRequestHandleImpl<UpperIdentifier, UpperMsgType>(i, m, options);
          deliverAckToMe.sendFailed(mrh, new NodeIsFaultyException(i, m));
          return mrh;
        }
      
        options = OptionsFactory.copyOptions(options);
        options.put(NODE_HANDLE_TO_INDEX, addIntendedDest(i));      
        ret = new IdentityMessageHandle(i, m, options, deliverAckToMe);
        addPendingMessage(i, ret);
      }
      
      // the synchronization problem is here, if it goes dead now, then this message won't 
      // be cancelled... TODO: get this correct, it's probably currently broken, maybe 
      // need cancelled bit in ret so that it knows it's cancelled.
      ret.setSubCancellable(tl.sendMessage(serializer.translateDown(i), m, ret, options));        
      return ret;
    }
    
    public void incomingSocket(P2PSocket<MiddleIdentifier> s) throws IOException {
      if (logger.level <= Logger.FINE) logger.log("incomingSocket("+s+")");
//      int index = s.getOptions().get(NODE_HANDLE_FROM_INDEX);
      final UpperIdentifier from = getIntendedDest(s.getOptions()); //intendedDest.get(index).get();

      if (from == null) {
        errorHandler.receivedException(null, new MemoryExpiredException("No record of the upper identifier for "+s.getIdentifier()+" index="+s.getOptions().get(NODE_HANDLE_FROM_INDEX))); 
        s.close();
        return;
      }

      
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
//      int index = options.get(NODE_HANDLE_FROM_INDEX);
      final UpperIdentifier from = getIntendedDest(options); //intendedDest.get(index).get();

      if (from == null) {
        errorHandler.receivedException(null, new MemoryExpiredException("No record of the upper identifier for "+i+" index="+options.get(NODE_HANDLE_FROM_INDEX)+" dropping message"+m)); 
        return;
      }
      
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

    public void livenessChanged(MiddleIdentifier i, int val, Map<String, Integer> options) {
      if (deadForever.contains(i)) {
        if (val < LIVENESS_DEAD) {
          if (logger.level <= Logger.SEVERE) logger.log("Node "+i+" came back from the dead!  It's a miracle! "+val+" Ignoring."); 
        }
        return;
      }
      
      UpperIdentifier upper = getIntendedDest(options);
//      if (val >= LIVENESS_DEAD_FOREVER) {
//        // options is required to have the NODE_HANDLE_FROM_INDEX
//        if (!options.containsKey(NODE_HANDLE_FROM_INDEX)) throw new IllegalArgumentException("Options doesn't have NODE_HANDLE_INDEX"+options);
//        upper = intendedDest.get(options.get(NODE_HANDLE_FROM_INDEX)).get();        
        if (upper == null) {
          if (logger.level <= Logger.WARNING) logger.logException("Memory for index "+options.get(NODE_HANDLE_FROM_INDEX)+" collected suppressing livenessChanged()", new Exception("Stack Trace"));
          return;
        }
//      } else {
//        if (options.containsKey(NODE_HANDLE_FROM_INDEX)) {
//          upper = intendedDest.get(options.get(NODE_HANDLE_FROM_INDEX)).get();        
//        }
//      }
//      if (upper == null) {
//        upper = serializer.translateUp(i);
//      }
      notifyLivenessListeners(upper, val, options);          
    }


    
    private void notifyLivenessListeners(UpperIdentifier i, int liveness, Map<String, Integer> options) {
      if (logger.level <= Logger.FINER) logger.log("notifyLivenessListeners("+i+","+liveness+")");
      List<LivenessListener<UpperIdentifier>> temp;
      synchronized(livenessListeners) {
        temp = new ArrayList<LivenessListener<UpperIdentifier>>(livenessListeners);
      }
      for (LivenessListener<UpperIdentifier> listener : temp) {
        listener.livenessChanged(i, liveness, options);
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
    
    public int proximity(UpperIdentifier i, Map<String, Integer> options) {
      if (logger.level <= Logger.FINE) logger.log("proximity("+i+")");
      if (deadForever.contains(i)) return Integer.MAX_VALUE;
      return prox.proximity(serializer.translateDown(i), OptionsFactory.addOption(options, NODE_HANDLE_FROM_INDEX, addIntendedDest(i)));
    }

    public void proximityChanged(MiddleIdentifier i, int newProx, Map<String, Integer> options) {
      UpperIdentifier upper = getIntendedDest(options);
      if (upper == null) {
        if (logger.level <= Logger.WARNING) logger.logException("Memory for index "+options.get(NODE_HANDLE_FROM_INDEX)+" collected suppressing proximityChanged()", new Exception("Stack Trace"));
        return;
      }
      
      notifyProximityListeners(upper, newProx, options);    
//      notifyProximityListeners(reverseIntendedDest.get(options.get(NODE_HANDLE_FROM_INDEX))serializer.translateUp(i), newProx, options);    
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
      removePendingMessage(identifier, this);
      return subCancellable.cancel();
    }

    public void setSubCancellable(Cancellable cancellable) {
      this.subCancellable = cancellable;
    }
    
    public Cancellable getSubCancellable() {
      return subCancellable;
    }

    public void ack(MessageRequestHandle<MiddleIdentifier, UpperMsgType> msg) {
      removePendingMessage(identifier, this);
      if (deliverAckToMe != null) deliverAckToMe.ack(this);
    }

    public void sendFailed(MessageRequestHandle<MiddleIdentifier, UpperMsgType> msg, IOException reason) {
      removePendingMessage(identifier, this);
      if (deliverAckToMe != null) deliverAckToMe.sendFailed(this, reason);
    }
    
    public String toString() {
      return "IdMsgHdl{"+message+"}->"+identifier;
    }
  }
}
