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
package org.mpisws.p2p.transport.priority;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;

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
import org.mpisws.p2p.transport.identity.MemoryExpiredException;
import org.mpisws.p2p.transport.liveness.LivenessListener;
import org.mpisws.p2p.transport.liveness.LivenessProvider;
import org.mpisws.p2p.transport.priority.PriorityTransportLayerImpl.EntityManager.MessageWrapper;
import org.mpisws.p2p.transport.proximity.ProximityProvider;
import org.mpisws.p2p.transport.util.DefaultErrorHandler;
import org.mpisws.p2p.transport.util.SocketRequestHandleImpl;
import org.mpisws.p2p.transport.wire.WireTransportLayer;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.Cancellable;
import rice.p2p.commonapi.exception.NodeIsDeadException;
import rice.p2p.util.SortedLinkedList;
import rice.p2p.util.tuples.Tuple;
import rice.selector.SelectorManager;
import rice.selector.TimerTask;

/**
 * 
 * @author Jeff Hoye
 *
 */
public class PriorityTransportLayerImpl<Identifier> implements PriorityTransportLayer<Identifier>, LivenessListener<Identifier>, TransportLayerCallback<Identifier, ByteBuffer> {

  TransportLayer<Identifier, ByteBuffer> tl;
  LivenessProvider<Identifier> livenessProvider;
  ProximityProvider<Identifier> proximityProvider;
  
  public static final byte PASSTHROUGH_SOCKET_B = 0;
  public static final byte PRIMARY_SOCKET_B = 1;
  public static final byte[] PASSTHROUGH_SOCKET = {PASSTHROUGH_SOCKET_B};
  public static final byte[] PRIMARY_SOCKET = {PRIMARY_SOCKET_B};
  
  public int MAX_MSG_SIZE = 10000;
  public int MAX_QUEUE_SIZE = 30;
  
  // maps a SelectionKey -> SocketConnector
  public Hashtable sockets;
  
  public Logger logger;
  
  protected Map<Identifier, EntityManager> entityManagers;

  private TransportLayerCallback<Identifier, ByteBuffer> callback;

  private ErrorHandler<Identifier> errorHandler;
  
  protected SelectorManager selectorManager;
  protected Environment environment;
  
  /**
   * The maximum message size;
   * 
   * @param env
   * @param maxMsgSize
   */
  public PriorityTransportLayerImpl(TransportLayer<Identifier, ByteBuffer> tl, 
      LivenessProvider<Identifier> livenessProvider,
      ProximityProvider<Identifier> proximityProvider,
      Environment env, 
      int maxMsgSize,
      int maxQueueSize,
      ErrorHandler<Identifier> handler) {
    entityManagers = new HashMap<Identifier, EntityManager>();
    this.logger = env.getLogManager().getLogger(PriorityTransportLayerImpl.class, null);
    this.selectorManager = env.getSelectorManager();
    this.environment = env;
    this.MAX_MSG_SIZE = maxMsgSize;
    this.MAX_QUEUE_SIZE = maxQueueSize;
    this.tl = tl;    
    if (logger.level <= Logger.INFO) logger.log("MAX_QUEUE_SIZE:"+MAX_QUEUE_SIZE+" MAX_MSG_SIZE:"+MAX_MSG_SIZE);
    this.livenessProvider = livenessProvider;
    this.proximityProvider = proximityProvider;
    tl.setCallback(this);
    livenessProvider.addLivenessListener(this);
    this.errorHandler = handler;
    if (this.errorHandler == null) {
      this.errorHandler = new DefaultErrorHandler<Identifier>(logger); 
    }
  }

  /**
   * We have to read the first byte and see if this is a 
   * passthrough (the layer higher than us asked to open it) socket or a 
   * primary (our layer tried to open it) socket.
   */
  public void incomingSocket(final P2PSocket<Identifier> s) throws IOException {
    s.register(true, false, new P2PSocketReceiver<Identifier>() {
      public void receiveSelectResult(P2PSocket<Identifier> socket, boolean canRead, boolean canWrite) throws IOException {
        if (socket != s) throw new IllegalArgumentException("Sockets not equal!!! s:"+s+" socket:"+socket);
        if (canWrite || !canRead) throw new IllegalArgumentException("Should only be able to read! canRead:"+canRead+" canWrite:"+canWrite);
        // the first thing we need to do is to find out if this is a primary socket or a passthrough
        ByteBuffer hdr = ByteBuffer.allocate(1);
        int ret;
        try {
          ret = (int)socket.read(hdr);
        } catch (IOException ioe) {
//          errorHandler.receivedException(s.getIdentifier(), new IOException("Incoming socket terminated early."));
          socket.close();
          return;
        }
        switch (ret) {
        case -1: 
          // closed... strange
          socket.close();
          break;
        case 0:
          // reregister
          socket.register(true, false, this);
          break;
        case 1:
          // success
          hdr.flip();
          byte val = hdr.get();
          switch(val) {
          case PASSTHROUGH_SOCKET_B:
            callback.incomingSocket(s);
            break;
          case PRIMARY_SOCKET_B:
            if (logger.level <= Logger.FINE) logger.log("Opened Primary Socket from "+s.getIdentifier());
            getEntityManager(s.getIdentifier()).incomingSocket(s, null);
            break;
          }
          break;
        default:
          //Whisky Tango Foxtrot?
          socket.close();
          throw new IllegalStateException("Read "+ret+" bytes?  Not good.  Expected to read 1 byte.");
        }
      }
      
      public void receiveException(P2PSocket<Identifier> socket, IOException e) {
        errorHandler.receivedException(socket.getIdentifier(), e);
      }      
    });
  }

  public SocketRequestHandle<Identifier> openSocket(Identifier i, final SocketCallback<Identifier> deliverSocketToMe, Map<String, Integer> options) {
    if (deliverSocketToMe == null) throw new IllegalArgumentException("No handle to return socket to! (deliverSocketToMe must be non-null!)");
    
    final SocketRequestHandleImpl<Identifier> handle = new SocketRequestHandleImpl<Identifier>(i, options, logger);    
    handle.setSubCancellable(tl.openSocket(i, new SocketCallback<Identifier>() {
      public void receiveResult(SocketRequestHandle<Identifier> cancellable, final P2PSocket<Identifier> sock) {
        
        handle.setSubCancellable(new Cancellable() {        
          public boolean cancel() {
            sock.close();
            return true;
          }        
        });
        
        sock.register(false, true, new P2PSocketReceiver<Identifier>() {

          public void receiveSelectResult(P2PSocket<Identifier> socket, boolean canRead, boolean canWrite) throws IOException {
            if (canRead || !canWrite) throw new IllegalArgumentException("expected to write!  canRead:"+canRead+" canWrite:"+canWrite);
            socket.write(ByteBuffer.wrap(PASSTHROUGH_SOCKET));
            if (deliverSocketToMe != null) deliverSocketToMe.receiveResult(handle, socket);
          }        
          
          public void receiveException(P2PSocket<Identifier> socket, IOException e) {
            if (deliverSocketToMe != null) deliverSocketToMe.receiveException(handle, e);
          }
        });
      } // receiveResult()
      public void receiveException(SocketRequestHandle<Identifier> s, IOException ex) {
        if (handle.getSubCancellable() != null && s != handle.getSubCancellable()) throw new IllegalArgumentException("s != handle.getSubCancellable() must be a bug. s:"+s+" sub:"+handle.getSubCancellable());
        if (deliverSocketToMe != null) deliverSocketToMe.receiveException(handle, ex);
      }
    }, options));
    
    return handle;
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

  public void messageReceived(Identifier i, ByteBuffer m, Map<String, Integer> options) throws IOException {
    callback.messageReceived(i, m, options);
  }  

  public MessageRequestHandle<Identifier, ByteBuffer> sendMessage(Identifier i, ByteBuffer m, MessageCallback<Identifier, ByteBuffer> deliverAckToMe, Map<String, Integer> options) {
    if (logger.level <= Logger.FINE) logger.log("sendMessage("+i+","+m+","+deliverAckToMe+","+options+")");
    
//    if (options == null) throw new IllegalArgumentException("options is null"); // delme, only for debugging something else
    // if it is to be sent UDP, just pass it through
    if (options != null && 
        options.containsKey(WireTransportLayer.OPTION_TRANSPORT_TYPE)) {
        Integer val = options.get(WireTransportLayer.OPTION_TRANSPORT_TYPE);
        if (val != null &&
            val.intValue() == WireTransportLayer.TRANSPORT_TYPE_DATAGRAM) {
          return tl.sendMessage(i, m, deliverAckToMe, options);
        }
    }
    
    return getEntityManager(i).send(i, m, deliverAckToMe, options);
  }

  public void setCallback(TransportLayerCallback<Identifier, ByteBuffer> callback) {
    this.callback = callback;
  }

  public void setErrorHandler(ErrorHandler<Identifier> handler) {
    this.errorHandler = handler;
  }

  public void destroy() {
    tl.destroy();    
  }
  
  protected EntityManager getEntityManager(Identifier i) {
    synchronized(entityManagers) {
      EntityManager ret = entityManagers.get(i); 
      if (ret == null) {
        ret = new EntityManager(i);
        entityManagers.put(i, ret);
      }
      return ret;
    }
  }
  
  protected EntityManager deleteEntityManager(Identifier i) {
    synchronized(entityManagers) {
      EntityManager ret = entityManagers.get(i); 
      if (ret != null) {
        ret.clearState();
      }
      return ret;
    }
  }

  public void livenessChanged(Identifier i, int val, Map<String, Integer> options) {
    if (val >= LivenessListener.LIVENESS_DEAD) {
      getEntityManager(i).markDead();
    }
  }

  public void cancelLivenessChecker(Identifier i) {
    getEntityManager(i).stopLivenessChecker();
  }
  
  /**
   * Problem?: this method should perhaps take the EntityManager as an arg.
   * @param i
   * @param options
   * @return
   */
  protected SocketRequestHandle<Identifier> openPrimarySocket(final Identifier i, Map<String, Integer> options) {
//    if (livenessProvider.getLiveness(i, options) >= LIVENESS_DEAD) {
//      if (logger.level <= Logger.WARNING) logger.log("Not opening primary socket to "+i+" because it is dead.");  
//      return null;
//    }     
    if (logger.level <= Logger.FINE) logger.log("Opening Primary Socket to "+i);
    
    final SocketRequestHandleImpl<Identifier> handle = new SocketRequestHandleImpl<Identifier>(i, options, logger) {
      public boolean cancel() {
        cancelLivenessChecker(i);
        return super.cancel();
      }
    };
    
    handle.setSubCancellable(tl.openSocket(i, new SocketCallback<Identifier>() {
      public void receiveResult(SocketRequestHandle<Identifier> cancellable, final P2PSocket<Identifier> sock) {
        handle.setSubCancellable(new Cancellable(){        
          public boolean cancel() {
            sock.close();
            return true;
          }        
        });
        sock.register(false, true, new P2PSocketReceiver<Identifier>() {
          ByteBuffer writeMe = ByteBuffer.wrap(PRIMARY_SOCKET);
          public void receiveSelectResult(P2PSocket<Identifier> socket, boolean canRead, boolean canWrite) throws IOException {
            if (canRead || !canWrite) throw new IllegalArgumentException("expected to write!  canRead:"+canRead+" canWrite:"+canWrite);
            if (logger.level <= Logger.FINE) logger.log("Opened Primary socket "+socket+" to "+i);
//            if (logger.level <= Logger.FINE) logger.log("Opened Primary socket "+socket+" to "+i);
            cancelLivenessChecker(i);
            if (socket.write(writeMe) == -1) {
              cancelLivenessChecker(i);
              getEntityManager(socket.getIdentifier()).receiveSocketException(handle, new org.mpisws.p2p.transport.ClosedChannelException("Channel closed while writing."));
              return;
            }
            if (writeMe.hasRemaining()) {
              socket.register(false, true, this);
            } else {
              getEntityManager(socket.getIdentifier()).incomingSocket(socket, handle);
            }
          }        
          
          public void receiveException(P2PSocket<Identifier> socket, IOException e) {
            cancelLivenessChecker(i);
            getEntityManager(socket.getIdentifier()).receiveSocketException(handle, e);
          }
          
          public String toString() {
            return "PriorityTLi: Primary Socket shim to "+i;
          }
        });
      } // receiveResult()
      public void receiveException(SocketRequestHandle<Identifier> s, IOException ex) {
        if (handle.getSubCancellable() != null && s != handle.getSubCancellable()) throw new IllegalArgumentException(
            "s != handle.getSubCancellable() must be a bug. s:"+
            s+" sub:"+handle.getSubCancellable());
        getEntityManager(s.getIdentifier()).receiveSocketException(handle, ex);
      }
    }, options));
    
    return handle;
  }
  
  public void printMemStats(int logLevel) {
    if (logLevel <= Logger.FINE) {
      synchronized(entityManagers) {
        int queueSum = 0;
        for(EntityManager em : entityManagers.values()) {
          int queueSize = em.queue.size();
          queueSum+=queueSize;          
          if (logLevel <= Logger.FINEST ||   // finest prints empty queues 
             (queueSize > 0 && logLevel <= Logger.FINER)) {  // only prints non-empty queues
            Identifier temp = em.identifier.get();
            String s = "";
            Map<String, Integer> options = null; 
            if (temp != null) {
              MessageWrapper peek = em.peek();
              if (peek != null) {
                options = peek.options;
              }
              s = ""+livenessProvider.getLiveness(temp, options);
            }
            logger.log("EM{"+temp+","+s+","+em.writingSocket+","+em.pendingSocket+"} queue:"+queueSize+" reg:"+em.registered+" lChecker:"+em.livenessChecker);
          }
        }        
        logger.log("NumEMs:"+entityManagers.size()+" numPendingMsgs:"+queueSum);
      } // synchronized
    }
  }
  
  /**
   * Responsible for writing messages to the socket.
   * 
   * Synchronization: all state is changed on the selector thread, except the queue, which must be carefully 
   * synchronized.
   * 
   * If we have something to write that means !queue.isEmpty() || messageThatIsBeingWritten != null, 
   *   we should have a writingSocket, or a pendingSocket
   * 
   * We only touch writingSocket if there is an error, or on scheduleToWriteIfNeeded()
   * 
   * We only change messageThatIsBeingWritten as a result of a call from receiveResult(socket, false, true);
   * 
   * @author Jeff Hoye
   */
  class EntityManager implements P2PSocketReceiver<Identifier> {
    // TODO: think about the behavior of this when it wraps around...
    int seq = Integer.MIN_VALUE;
    SortedLinkedList<MessageWrapper> queue; // messages we want to send
    Collection<P2PSocket<Identifier>> sockets;
    
    WeakReference<Identifier> identifier;
    
    SocketRequestHandle<Identifier> pendingSocket; // the receipt that we are opening a socket
    P2PSocket<Identifier> writingSocket; // don't try to write to multiple socktes, it will confuse things
    P2PSocket<Identifier> closeWritingSocket; // could be a boolean, but we store the writingSocket here just for debugging, == writingSocket if should close it after the current write
    MessageWrapper messageThatIsBeingWritten; // the current message we are sending, if this is null, we aren't in the middle of sending a message
    // Invariant: if (messageThatIsBeingWritten != null) then (writingSocket != null)
    private boolean registered = false;  // true if registed for writing
    
    EntityManager(Identifier identifier) {
      this.identifier = new WeakReference<Identifier>(identifier);
      queue = new SortedLinkedList<MessageWrapper>();
      sockets = new HashSet<P2PSocket<Identifier>>();
    }

    public String toString() {
      return "EM{"+identifier.get()+"}";
    }
    
    public void clearState() {
      if (!selectorManager.isSelectorThread()) {
        selectorManager.invoke(new Runnable() {      
          public void run() {
            clearState();      
          }      
        });
        return;
      }
      
      for (P2PSocket socket : sockets) {
//        try {
          socket.close();
//        } catch (IOException ioe) {
//          errorHandler.receivedException(i, error)
//        }
      }
      queue.clear();
      messageThatIsBeingWritten = null;
      if (pendingSocket != null) {
        pendingSocket.cancel();
        stopLivenessChecker();
      }
      pendingSocket = null;
    }

    /**
     * Read an error, or socket was closed.
     * 
     * The purpose of this method is to let the currently writing message to complete.
     * 
     * @param socket
     * @return true if we did it now
     */
    public boolean closeMe(P2PSocket<Identifier> socket) {
      if (logger.level <= Logger.FINE) logger.logException("closeMe("+socket+"):"+(socket == writingSocket)+","+messageThatIsBeingWritten, new Exception("Stack Trace"));
      if (socket == writingSocket) {
        if (messageThatIsBeingWritten == null) {
          sockets.remove(socket);
          socket.close();
          setWritingSocket(null);
          return true;
        }
        closeWritingSocket = writingSocket;
        return false;
      } else {
        sockets.remove(socket);
        socket.close();
        return true;
      }
    }
    
    /**
     * Get's the socket, both when we open it, and when a remote node opens it.
     * 
     * @param s
     * @param receipt null if a remote node opened the socket
     */
    public void incomingSocket(P2PSocket<Identifier> s, SocketRequestHandle<Identifier> receipt) {
      // make sure we're on the selector thread so synchronization of writingSocket is simple
      if (!selectorManager.isSelectorThread()) throw new IllegalStateException("Must be called on the selector");

      if (logger.level <= Logger.FINE) logger.log("incomingSocket("+s+","+receipt+")");
      
      // set pendingSocket to null if possible
      if (receipt != null) {
        if (receipt == pendingSocket) {
          stopLivenessChecker();
          if (logger.level <= Logger.FINE) logger.log("got socket:"+s+" clearing pendingSocket:"+pendingSocket);
          pendingSocket = null;  // this is the one we requested
        } else {
          logger.log("receipt != pendingSocket!!! receipt:"+receipt+" pendingSocket:"+pendingSocket);
        } 
      }
      
      sockets.add(s);
      scheduleToWriteIfNeeded();
      
      // also, be able to read incoming messages on every socket
      new SizeReader(s);
    }
    
    public void setWritingSocket(P2PSocket<Identifier> s/*, String loc*/) {
//      logger.logException(this+".setWritingSocket("+s+")", new Exception());
      if (logger.level <= Logger.FINEST) logger.log(this+".setWritingSocket("+s+")");
//      if (logger.level <= Logger.INFO) logger.log(this+".setWritingSocket("+s+") loc:"+loc);
      writingSocket = s;
    }

    /**
     * Must be called on selectorManager.
     *
     * A) finds a writingSocket if possible
     *   opens one if needed
     */
    protected void scheduleToWriteIfNeeded() {
      if (!selectorManager.isSelectorThread()) throw new IllegalStateException("Must be called on the selector");

      Identifier temp = identifier.get();
      if (temp == null) {
        purge(new MemoryExpiredException("No record of identifier for "+this)); 
        return;
      }
      
      // make progress acquiring a writingSocket
      if (writingSocket == null) {
        registered = false;
        if (!sockets.isEmpty()) {
          setWritingSocket(sockets.iterator().next()/*, "scheduleToWriteIfNeeded"*/);
        } else {
          // we need to get a writingSocket
          if (pendingSocket == null) {
            MessageWrapper peek = peek();
            if (peek != null) {
              pendingSocket = openPrimarySocket(temp, peek.options);
              startLivenessChecker(temp, peek.options);              
            }
          }
        }
      }
      
      // register on the writingSocket if needed
      if (!registered && writingSocket != null) {
        if (haveMessageToSend()) {
          //logger.log(this+" registering on "+writingSocket);
          // maybe we should remember if we were registered, and don't reregister, but for now it doesn't hurt
          registered = true;  // may fail in this call and set registered back to false, so make sure to do this before calling register          
          if (logger.level <= Logger.FINEST) logger.log(this+".scheduleToWriteIfNeeded() registering to write on "+writingSocket);
          writingSocket.register(false, true, this);
        }
      }      
    }

    TimerTask livenessChecker = null;
    public void startLivenessChecker(final Identifier temp, final Map<String, Integer> options) {
      if (livenessChecker == null) {
        if (logger.level <= Logger.FINER) logger.log("startLivenessChecker("+temp+","+options+") pend:"+pendingSocket+" writingS:"+writingSocket+" theQueue:"+queue.size());
        livenessChecker = new TimerTask() {        
          @Override
          public void run() {
            stopLivenessChecker(); // sets livenssChecker back to null
//            Map<String, Integer> options;
//            MessageWrapper peek = peek();
//            if (peek != null) {
//              options = peek.options;
//            } else {
//              options = this.options;
//            }
            livenessProvider.checkLiveness(temp, options);        

            // if this throws a NPE, there is a bug, cause this should have been cancelled if pendingSocket == null
            pendingSocket.cancel();  
            pendingSocket = null;
            scheduleToWriteIfNeeded();  // will restart this livenessChecker, create a new pendingSocket
          }        
        };

        int delay = proximityProvider.proximity(temp, options)*4;
        if (delay < 5000) delay = 5000; // 1 second
        if (delay > 40000) delay = 40000; // 20 seconds
        
        selectorManager.schedule(livenessChecker, delay);
      }
    }
      
    public void stopLivenessChecker() {
      if (livenessChecker == null) return;
      if (logger.level <= Logger.FINER) logger.log("stopLivenessChecker("+identifier.get()+") pend:"+pendingSocket+" writingS:"+writingSocket+" theQueue:"+queue.size());

      livenessChecker.cancel();
      livenessChecker = null;
    }
    
    /**
     * Returns the messageThatIsBeingWritten, or the first in the queue, w/o setting messageThatIsBeingWritten
     * @return
     */
    private MessageWrapper peek() {
      if (messageThatIsBeingWritten == null) {
        return queue.peek();
      }
      return messageThatIsBeingWritten;
    }
    
    /**
     * Returns the messageThatIsBeingWritten, polls the queue if it is null
     * @return
     */
    private MessageWrapper poll() {
      if (messageThatIsBeingWritten == null) {
        messageThatIsBeingWritten = queue.poll();
        if (logger.level <= Logger.FINEST) logger.log("poll("+identifier.get()+") set messageThatIsBeingWritten = "+messageThatIsBeingWritten);
      }
      if (queue.size() >= (MAX_QUEUE_SIZE-1) && logger.level <= Logger.INFO) {
        logger.log(this+"polling from full queue (this is a good thing) "+messageThatIsBeingWritten);
      }      
      return messageThatIsBeingWritten;
    }
    
    /**
     * True if we have a message to send
     * @return
     */
    private boolean haveMessageToSend() {
      if (messageThatIsBeingWritten == null && queue.isEmpty()) return false; 
      return true;
    }

    /**
     * This is called when the socket has an exception but was already opened.
     */
    public void receiveException(P2PSocket<Identifier> socket, IOException ioe) {
//      if (ioe instanceof NodeIsFaultyException) {
//        if (livenessProvider.getLiveness(identifier, )
//            
//        markDead();
//        return; 
//      }
      
      if (logger.level <= Logger.FINER) {
        logger.logException(this+".receiveException("+socket+","+ioe+"):"+messageThatIsBeingWritten+" wrS:"+writingSocket, ioe);
      } else if (logger.level <= Logger.INFO) logger.log(this+".receiveException("+socket+","+ioe+"):"+messageThatIsBeingWritten+" wrS:"+writingSocket+" "+ioe);
      registered = false;
      sockets.remove(socket);
      if (ioe instanceof ClosedChannelException) {
        // don't close, will get cleaned up by the reader
      } else {
        socket.close();
      }
        
      if (socket == writingSocket) {
        clearAndEnqueue(messageThatIsBeingWritten);
      }
      scheduleToWriteIfNeeded();
    }

    public void receiveSelectResult(P2PSocket<Identifier> socket, boolean canRead, boolean canWrite) throws IOException {
      registered  = false;
      if (canRead || !canWrite) throw new IllegalStateException(this+" Expected only to write. canRead:"+canRead+" canWrite:"+canWrite+" socket:"+socket);
      if (socket != writingSocket) {
        // this is because the close() method calls receiveSelectResult
//        if (writingSocket == null) {          
//          scheduleToWriteIfNeeded();
//        }
//        logger.logException("receivedSelectResult("+socket+", r:"+canRead+" w:"+canWrite+") ws:"+writingSocket, new IOException());
        if (logger.level <= Logger.WARNING) logger.log("receivedSelectResult("+socket+", r:"+canRead+" w:"+canWrite+") ws:"+writingSocket);
        return;
      }
      
//      logger.log("receivedSelectResult("+socket+","+canRead+","+canWrite);
      if (logger.level <= Logger.FINEST) logger.log("receivedSelectResult("+socket+","+canRead+","+canWrite);
      MessageWrapper current = poll();
      while (current != null && current.receiveSelectResult(writingSocket)) {
        current = poll();
      }
      scheduleToWriteIfNeeded();
    }
    
    /**
     * TODO: The synchronization here may need work.
     * 
     * This is called while we are waiting to open the new socket.
     * 
     * @param handle
     * @param ex
     */
    public void receiveSocketException(SocketRequestHandleImpl<Identifier> handle, IOException ex) {      
//      if (ex instanceof NodeIsFaultyException) {
//        markDead();
//        return; 
//      }
      if (handle == pendingSocket) {
        pendingSocket = null; 
        stopLivenessChecker();
      }
      scheduleToWriteIfNeeded();
    }

    /**
     * Enqueue the message.
     * @param ret
     */
    private void enqueue(MessageWrapper ret) {
//      logger.log("enqueue("+ret+")");
      synchronized(queue) {
        queue.add(ret);       
        
        // drop the lowest priority message if the queue is overflowing        
        while (queue.size() > MAX_QUEUE_SIZE) {
          MessageWrapper w = queue.removeLast();
          if (logger.level <= Logger.CONFIG) logger.log("Dropping "+w+" because queue is full. MAX_QUEUE_SIZE:"+MAX_QUEUE_SIZE);
          w.drop();
        }
      }
    }

    /**
     * This method is a keeper, but may need some additional functions, and/or error handling.
     *
     */
    public void markDead() {
      purge(new NodeIsFaultyException(identifier.get()));
    }
    
    public void purge(IOException ioe) {
      if (logger.level <= Logger.FINE) logger.log(this+"purge("+ioe+"):"+messageThatIsBeingWritten);
      ArrayList<Tuple<MessageCallback<Identifier, ByteBuffer>, MessageWrapper>> callSendFailed = 
        new ArrayList<Tuple<MessageCallback<Identifier, ByteBuffer>, MessageWrapper>>();
      synchronized(queue) {
        // return NodeIsFaultyException to all of the message(s) deliverAckToMe(s)
        if (messageThatIsBeingWritten != null) {
          messageThatIsBeingWritten.reset();
          if (messageThatIsBeingWritten.deliverAckToMe != null) {
            callSendFailed.add(new Tuple(messageThatIsBeingWritten.deliverAckToMe, messageThatIsBeingWritten));
//            messageThatIsBeingWritten.deliverAckToMe.sendFailed(messageThatIsBeingWritten, ioe);           
          }
          messageThatIsBeingWritten = null;
        }
        for (MessageWrapper msg : queue) {
          if (msg.deliverAckToMe != null) {
            callSendFailed.add(new Tuple(msg.deliverAckToMe, msg));
//            msg.deliverAckToMe.sendFailed(msg, ioe); 
          }
        }
        queue.clear();
      }
      
      for (Tuple<MessageCallback<Identifier, ByteBuffer>, MessageWrapper> t : callSendFailed) {
        t.a().sendFailed(t.b(), ioe);
      }
      
      synchronized(sockets) {
        for (P2PSocket<Identifier> sock : sockets) {
//          try {
            sock.close();
//          } catch (IOException ioe) {
//            if (logger.level <= Logger.WARNING) logger.logException("Error closing "+sock,ioe);
//          }
        }
        sockets.clear();
      }
      setWritingSocket(null/*, "purge"*/);
      if (pendingSocket != null) {
        stopLivenessChecker();
//        logger.log("cancelling "+pendingSocket);
        pendingSocket.cancel();
      }
      pendingSocket = null;
    }
    


    /**
     * Note: We got to get rid of all the calls to poll().
     *  
     * @param message
     * @param deliverAckToMe
     * @param options
     * @return
     */
    public MessageRequestHandle<Identifier, ByteBuffer> send(
        Identifier temp,
        ByteBuffer message, 
        MessageCallback<Identifier, ByteBuffer> deliverAckToMe, 
        final Map<String, Integer> options) {      
      if (logger.level <= Logger.FINER) logger.log(this+"send("+message+")");

      // pick the priority
      int priority = DEFAULT_PRIORITY;
      if (options != null) {
        if (options.containsKey(OPTION_PRIORITY)) {
          priority = options.get(OPTION_PRIORITY);          
        }
      }

      MessageWrapper ret;

      // throw an error if it's too large
      int remaining = message.remaining();
      if (remaining > MAX_MSG_SIZE) {
        ret = new MessageWrapper(temp, message, deliverAckToMe, options, priority, 0);
        if (deliverAckToMe != null) 
          deliverAckToMe.sendFailed(ret, 
            new SocketException("Message too large. msg:"+message+" size:"+remaining+" max:"+MAX_MSG_SIZE));

        return ret; 
      }
      
      // make sure it's alive
      if (livenessProvider.getLiveness(temp, options) >= LIVENESS_DEAD) {
        ret = new MessageWrapper(temp, message, deliverAckToMe, options, priority, 0);
        if (deliverAckToMe != null) 
          deliverAckToMe.sendFailed(ret, new NodeIsFaultyException(temp, message));
        return ret;
      }
      
      // enqueue the message
      ret = new MessageWrapper(temp, message, deliverAckToMe, options, priority, seq++);        
      enqueue(ret);
      if (selectorManager.isSelectorThread()) {
        scheduleToWriteIfNeeded();
      } else {
        selectorManager.invoke(new Runnable() { public void run() {scheduleToWriteIfNeeded();}});
      }
      
      return ret;
    }

    protected boolean complete(MessageWrapper wrapper) {
      if (logger.level <= Logger.FINEST) logger.log(this+".complete("+wrapper+")");
      if (wrapper != messageThatIsBeingWritten) throw new IllegalArgumentException("Wrapper:"+wrapper+" messageThatIsBeingWritten:"+messageThatIsBeingWritten);
      
      messageThatIsBeingWritten = null;
        
      // notify deliverAckToMe
      wrapper.complete();
      
      // close the socket if we need to 
      if (closeWritingSocket == writingSocket) {
        writingSocket.close();
        setWritingSocket(null/*, "complete("+wrapper+")"*/);
        closeWritingSocket = null;          
        return false;
      }
      return true;
    }

    public void clearAndEnqueue(MessageWrapper wrapper) {
      if (wrapper != messageThatIsBeingWritten) throw new IllegalArgumentException("Wrapper:"+wrapper+" messageThatIsBeingWritten:"+messageThatIsBeingWritten);
      if (messageThatIsBeingWritten != null) messageThatIsBeingWritten.reset();
      messageThatIsBeingWritten = null;
      if (writingSocket != null) {
//        writingSocket.close();
        sockets.remove(writingSocket);
        setWritingSocket(null/*, "CaE("+wrapper+")"*/);
      }
      if (wrapper != null) {
        wrapper.reset();
        enqueue(wrapper);      
      }
    }

    class MessageWrapper implements 
        Comparable<MessageWrapper>, 
        MessageRequestHandle<Identifier, ByteBuffer> {
      int priority;
      int seq;
      Identifier myIdentifier;
      
      P2PSocket socket; // null if we aren't registered, aka, we aren't pending/writing
      
      ByteBuffer originalMessage;
      ByteBuffer message;
      MessageCallback<Identifier, ByteBuffer> deliverAckToMe;
      Map<String, Integer> options;      
      
      boolean cancelled = false; // true when cancel is called
      boolean completed = false; // true when completed is called
      
      MessageWrapper(
          Identifier temp,
          ByteBuffer message, 
          MessageCallback<Identifier, ByteBuffer> deliverAckToMe, 
          Map<String, Integer> options, int priority, int seq) {

//        if (options == null) throw new RuntimeException("options is null");  // debugging
        
        this.myIdentifier = temp;
        this.originalMessage = message;

        // head the message with the size
        int size = message.remaining();
        this.message = ByteBuffer.allocate(message.remaining()+4);
        this.message.put((byte)((size >>> 24) & 0xFF));
        this.message.put((byte)((size >>> 16) & 0xFF));
        this.message.put((byte)((size >>>  8) & 0xFF));
        this.message.put((byte)((size >>>  0) & 0xFF));
        this.message.put(message);
        this.message.clear();
        
        this.deliverAckToMe = deliverAckToMe;
        this.options = options;
        this.priority = priority;
        this.seq = seq;      
      }
      
      public void complete() {
        completed = true;
        if (deliverAckToMe != null) deliverAckToMe.ack(this);
      }

      /**
       * When is this registered?  May be registered too often.
       * 
       * @return true if should keep writing
       */
      public boolean receiveSelectResult(P2PSocket<Identifier> socket) throws IOException {
        if (logger.level <= Logger.FINEST) logger.log(this+".receiveSelectResult("+socket+")");
        try {
        if (this.socket != null && this.socket != socket) {
          // this shouldn't happen
          if (logger.level <= Logger.WARNING) logger.log(this+" Socket changed!!! can:"+cancelled+" comp:"+completed+" socket:"+socket+" writingSocket:"+writingSocket+" this.socket:"+this.socket);
          socket.shutdownOutput();
          
          // do we need to reset?
          return false;
        }

//        if (socket == null) logger.log("Starting to write "+this+" on "+socket);
        
        // in case we don't complete the write, remember where we are writing
        this.socket = socket;
        
        if (cancelled && message.position() == 0) {
          if (logger.level <= Logger.FINEST) logger.log(this+".rsr("+socket+") cancelled"); 
          // cancel
          return true;
        } else {
          long bytesWritten;
          if ((bytesWritten = socket.write(message)) == -1) {
            // socket was closed, need to register new socket
            if (logger.level <= Logger.FINEST) logger.log(this+".rsr("+socket+") socket was closed"); 
            clearAndEnqueue(this); //             messageThatIsBeingWritten = null;            
            return false;
          }
          if (logger.level <= Logger.FINER) logger.log(this+" wrote "+bytesWritten+" bytes of "+message.capacity()+" remaining:"+message.remaining());

          if (message.hasRemaining()) {
            if (logger.level <= Logger.FINEST) logger.log(this+".rsr("+socket+") has remaining"); 
            return false;
          }
        }
                
        return EntityManager.this.complete(this); 
        } catch (IOException ioe) {
          // note, clearAndEnqueue() gets called later by the writer when the stack unravels again
          if (logger.level <= Logger.FINEST) logger.logException(this+".rsr("+socket+")", ioe);
          throw ioe;
        }
      }
      
      public void drop() {
        // TODO: make sure we've done evrything necessary here to clean this up        
        if (deliverAckToMe != null) deliverAckToMe.sendFailed(this, new QueueOverflowException(identifier.get(), originalMessage));
      }
            
      /**
       * Compares first on priority, second on seq.
       */
      public int compareTo(MessageWrapper that) {
        if (this.priority == that.priority) {
          return this.seq-that.seq;        
        }
        return this.priority-that.priority;
      }

      public Identifier getIdentifier() {
        return myIdentifier;
      }

      public ByteBuffer getMessage() {
        return originalMessage;
      }

      public Map<String, Integer> getOptions() {
        return options;
      }
      
      public void reset() {
        message.clear();
        socket = null;
      }
      
      public boolean cancel() {
        cancelled = true;
        if (this.equals(messageThatIsBeingWritten)) {
          if (message.position() == 0) {
            // TODO: can still cancel the message, but have to have special behavior when the socket calls us back 
            return true;
          } else {
            return false;
          }
        }
        synchronized(queue) {
          return queue.remove(this);
        }
      }
      public String toString() {
        return "MessagWrapper{"+message+"}@"+System.identityHashCode(this)+"->"+identifier.get()+" pri:"+priority+" seq:"+seq+" s:"+this.socket; 
      }
    }
    
    // *********************** Reader ************************
    
    /**
     * Reads the size of the object, then launches a new ObjectReader with the appropriate buffer size.
     * 
     * @author Jeff Hoye
     */
    class SizeReader extends BufferReader {
      
      public SizeReader(P2PSocket<Identifier> socket) {
        super(4, socket); 
      }
      
      @Override
      public void done(P2PSocket<Identifier> socket) throws IOException {
        int msgSize = buf.asIntBuffer().get();
        if (logger.level <= Logger.FINER) logger.log(EntityManager.this+" reading message of size "+msgSize);

        if (msgSize > MAX_MSG_SIZE) {
          if (logger.level <= Logger.WARNING) logger.log(socket+" attempted to send a message of size "+msgSize+". MAX_MSG_SIZE = "+MAX_MSG_SIZE);
          closeMe(socket);
          return;
        }
        
        new BufferReader(msgSize, socket);
      }
      
      public String toString() {
        return "SizeReader";
      }
    }
    
    /**
     * Reads into the buf, or closes the socket.
     * 
     * @author Jeff Hoye
     */
    class BufferReader implements P2PSocketReceiver<Identifier> {
      ByteBuffer buf;
      
      public BufferReader(int size, P2PSocket<Identifier> socket) {
        buf = ByteBuffer.allocate(size);
        socket.register(true, false, this);
      }
      
      public void receiveSelectResult(P2PSocket<Identifier> socket, boolean canRead, boolean canWrite) throws IOException {
        if (canWrite || !canRead) throw new IllegalStateException(EntityManager.this+" Expected only to read. canRead:"+canRead+" canWrite:"+canWrite+" socket:"+socket);
        
        try {
          if (socket.read(buf) == -1) {
            closeMe(socket);
            return;
          }
        } catch (IOException ioe) {
          receiveException(socket, ioe);
          return;
        }
        
        if(buf.remaining() == 0) {
          buf.flip();
          done(socket);
        } else {
          socket.register(true, false, this); 
        }        
      }
      
      public void receiveException(P2PSocket<Identifier> socket, IOException e) {
//        if (e instanceof NodeIsFaultyException) {
//          markDead();
//          return; 
//        }

        if (e instanceof ClosedChannelException) {
          return;
        }
        
        if (!(e instanceof NodeIsFaultyException)) {
          errorHandler.receivedException(socket.getIdentifier(), e);
        }
        closeMe(socket);
      }                    
      
      public void done(P2PSocket<Identifier> socket) throws IOException {
        if (logger.level <= Logger.FINER) logger.log(EntityManager.this+" read message of size "+buf.capacity()+" from "+socket);        
        callback.messageReceived(socket.getIdentifier(), buf, socket.getOptions()); 
        new SizeReader(socket);
      }
      
      public String toString() {
        return "BufferReader{"+buf+"}";
      }
    }
  } // EntityManager
}