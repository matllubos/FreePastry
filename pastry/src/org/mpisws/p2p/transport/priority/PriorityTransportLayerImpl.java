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
import java.net.SocketException;
import java.nio.ByteBuffer;
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
import org.mpisws.p2p.transport.liveness.LivenessListener;
import org.mpisws.p2p.transport.liveness.LivenessProvider;
import org.mpisws.p2p.transport.priority.PriorityTransportLayerImpl.EntityManager.MessageWrapper;
import org.mpisws.p2p.transport.util.DefaultErrorHandler;
import org.mpisws.p2p.transport.util.SocketRequestHandleImpl;
import org.mpisws.p2p.transport.wire.WireTransportLayer;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.exception.NodeIsDeadException;
import rice.p2p.util.SortedLinkedList;
import rice.selector.SelectorManager;

/**
 * 
 * @author Jeff Hoye
 *
 */
public class PriorityTransportLayerImpl<Identifier> implements PriorityTransportLayer<Identifier>, LivenessListener<Identifier>, TransportLayerCallback<Identifier, ByteBuffer> {

  TransportLayer<Identifier, ByteBuffer> tl;
  LivenessProvider<Identifier> livenessProvider;
  
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
        int ret = (int)socket.read(hdr);
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
    
    final SocketRequestHandleImpl<Identifier> handle = new SocketRequestHandleImpl<Identifier>(i, options);    
    handle.setSubCancellable(tl.openSocket(i, new SocketCallback<Identifier>() {
      public void receiveResult(SocketRequestHandle<Identifier> cancellable, P2PSocket<Identifier> sock) {
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
        if (s != handle.getSubCancellable()) throw new IllegalArgumentException("s != handle.getSubCancellable() must be a bug. s:"+s+" sub:"+handle.getSubCancellable());
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
    // if it is to be sent UDP, just pass it through
    if (options != null && 
        options.containsKey(WireTransportLayer.OPTION_TRANSPORT_TYPE)) {
        Integer val = options.get(WireTransportLayer.OPTION_TRANSPORT_TYPE);
        if (val != null &&
            val.intValue() == WireTransportLayer.TRANSPORT_TYPE_DATAGRAM) {
          return tl.sendMessage(i, m, deliverAckToMe, options);
        }
    }
    
    return getEntityManager(i).send(m, deliverAckToMe, options);
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

  /**
   * Problem?: this method should perhaps take the EntityManager as an arg.
   * @param i
   * @param options
   * @return
   */
  protected SocketRequestHandle<Identifier> openPrimarySocket(Identifier i, Map<String, Integer> options) {
//    if (livenessProvider.getLiveness(i, options) >= LIVENESS_DEAD) {
//      if (logger.level <= Logger.WARNING) logger.log("Not opening primary socket to "+i+" because it is dead.");  
//      return null;
//    }     
    if (logger.level <= Logger.FINE) logger.log("Opening Primary Socket to "+i);
    final SocketRequestHandleImpl<Identifier> handle = new SocketRequestHandleImpl<Identifier>(i, options);
    handle.setSubCancellable(tl.openSocket(i, new SocketCallback<Identifier>() {
      public void receiveResult(SocketRequestHandle<Identifier> cancellable, P2PSocket<Identifier> sock) {
        sock.register(false, true, new P2PSocketReceiver<Identifier>() {

          public void receiveSelectResult(P2PSocket<Identifier> socket, boolean canRead, boolean canWrite) throws IOException {
            if (canRead || !canWrite) throw new IllegalArgumentException("expected to write!  canRead:"+canRead+" canWrite:"+canWrite);
            socket.write(ByteBuffer.wrap(PRIMARY_SOCKET));
            getEntityManager(socket.getIdentifier()).incomingSocket(socket, handle);
          }        
          
          public void receiveException(P2PSocket<Identifier> socket, IOException e) {
            getEntityManager(socket.getIdentifier()).receiveSocketException(handle, e);
          }
        });
      } // receiveResult()
      public void receiveException(SocketRequestHandle<Identifier> s, IOException ex) {
        if (s != handle.getSubCancellable()) throw new IllegalArgumentException(
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
          if (logLevel <= Logger.FINER) {            
            logger.log("EM{"+em.identifier+","+livenessProvider.getLiveness(em.identifier, null)+","+em.writingSocket+","+em.pendingSocket+"} queue:"+queueSize);
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
    SortedLinkedList<MessageWrapper> queue; 
    Collection<P2PSocket<Identifier>> sockets;
    
    Identifier identifier;
    
    SocketRequestHandle<Identifier> pendingSocket; // the receipt that we are opening a socket
    P2PSocket<Identifier> writingSocket; // don't try to write to multiple socktes, it will confuse things
    P2PSocket<Identifier> closeWritingSocket; // could be a boolean, but we store the writingSocket here just for debugging, == writingSocket if should close it after the current write
    MessageWrapper messageThatIsBeingWritten; // the current message we are sending, if this is null, we aren't in the middle of sending a message
    // Invariant: if (messageThatIsBeingWritten != null) then (writingSocket != null)
    private boolean registered = false;  // true if registed for writing
    
    EntityManager(Identifier identifier) {
      this.identifier = identifier;
      queue = new SortedLinkedList<MessageWrapper>();
      sockets = new HashSet<P2PSocket<Identifier>>();
    }

    public String toString() {
      return "EM{"+identifier+"}";
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
      if (pendingSocket != null) pendingSocket.cancel();
      pendingSocket = null;
    }

    /**
     * Read an error, or socket was closed.
     * 
     * The purpose of this method is to let the currently written message to complete.
     * 
     * @param socket
     * @return true if we did it now
     */
    public boolean closeMe(P2PSocket<Identifier> socket) {
      if (socket == writingSocket) {
        if (messageThatIsBeingWritten == null) {
          sockets.remove(socket);
          socket.close();
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

      // set pendingSocket to null if possible
      if (receipt != null) {
        if (receipt == pendingSocket) {
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

    /**
     * Must be called on selectorManager.
     *
     * A) finds a writingSocket if possible
     *   opens one if needed
     */
    protected void scheduleToWriteIfNeeded() {
      if (!selectorManager.isSelectorThread()) throw new IllegalStateException("Must be called on the selector");
      
      // make progress acquiring a writingSocket
      if (writingSocket == null) {
        if (!sockets.isEmpty()) {
          writingSocket = sockets.iterator().next();
        } else {
          // we need to get a writingSocket
          if (pendingSocket == null) {
            MessageWrapper peek = peek();
            if (peek != null) {
              pendingSocket = openPrimarySocket(identifier, peek.options);
            }
          }
        }
      }
      
      // register on the writingSocket if needed
      if (!registered && writingSocket != null) {
        if (haveMessageToSend()) {
          //logger.log(this+" registering on "+writingSocket);
          // maybe we should remember if we were registered, and don't reregister, but for now it doesn't hurt
          writingSocket.register(false, true, this);
          registered = true;
        }
      }      
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

    public void receiveException(P2PSocket<Identifier> socket, IOException ioe) {
      if (ioe instanceof NodeIsFaultyException) {
        markDead();
        return; 
      }
      registered = false;
      sockets.remove(socket);
      socket.close();
      
      if (socket == writingSocket) {
        clearAndEnqueue(messageThatIsBeingWritten);
      }
      scheduleToWriteIfNeeded();
    }

    public void receiveSelectResult(P2PSocket<Identifier> socket, boolean canRead, boolean canWrite) throws IOException {
      registered  = false;
      if (canRead || !canWrite) throw new IllegalStateException(this+" Expected only to write. canRead:"+canRead+" canWrite:"+canWrite+" socket:"+socket);
      if (socket != writingSocket) {
        if (logger.level <= Logger.WARNING) logger.log("receivedSelectResult("+socket+","+canRead+","+canWrite);
      }
      MessageWrapper current = poll();
      while (current != null && current.receiveSelectResult(socket)) {
        current = poll();
      }
      scheduleToWriteIfNeeded();
    }
    
    /**
     * TODO: The synchronization here may need work.
     * 
     * @param handle
     * @param ex
     */
    public void receiveSocketException(SocketRequestHandleImpl<Identifier> handle, IOException ex) {      
      if (ex instanceof NodeIsFaultyException) {
        markDead();
        return; 
      }
      if (handle == pendingSocket) {
        pendingSocket = null; 
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
      synchronized(queue) {
        // return NodeIsFaultyException to all of the message(s) deliverAckToMe(s)
        if (messageThatIsBeingWritten != null) {
          if (messageThatIsBeingWritten.deliverAckToMe != null) 
            messageThatIsBeingWritten.deliverAckToMe.sendFailed(messageThatIsBeingWritten, new NodeIsFaultyException(identifier));           
          messageThatIsBeingWritten = null;
        }
        for (MessageWrapper msg : queue) {
          if (msg.deliverAckToMe != null) msg.deliverAckToMe.sendFailed(msg, new NodeIsFaultyException(identifier)); 
        }
        queue.clear();
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
      writingSocket = null;
      if (pendingSocket != null) pendingSocket.cancel();
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
        ret = new MessageWrapper(message, deliverAckToMe, options, priority, 0);
        if (deliverAckToMe != null) 
          deliverAckToMe.sendFailed(ret, 
            new SocketException("Message too large. msg:"+message+" size:"+remaining+" max:"+MAX_MSG_SIZE));

        return ret; 
      }
      
      // make sure it's alive
      if (livenessProvider.getLiveness(identifier, options) >= LIVENESS_DEAD) {
        ret = new MessageWrapper(message, deliverAckToMe, options, priority, 0);
        if (deliverAckToMe != null) 
          deliverAckToMe.sendFailed(ret, new NodeIsFaultyException(identifier, message));
        return ret;
      }
      
      // enqueue the message
      ret = new MessageWrapper(message, deliverAckToMe, options, priority, seq++);        
      enqueue(ret);
      if (selectorManager.isSelectorThread()) {
        scheduleToWriteIfNeeded();
      } else {
        selectorManager.invoke(new Runnable() { public void run() {scheduleToWriteIfNeeded();}});
      }
      
      return ret;
    }

    protected void complete(MessageWrapper wrapper) {
      if (wrapper != messageThatIsBeingWritten) throw new IllegalArgumentException("Wrapper:"+wrapper+" messageThatIsBeingWritten:"+messageThatIsBeingWritten);
      
      messageThatIsBeingWritten = null;
        
      // notify deliverAckToMe
      wrapper.complete();
      
      // close the socket if we need to 
      if (closeWritingSocket == writingSocket) {
        writingSocket.close();
        writingSocket = null;
        closeWritingSocket = null;          
      }
    }

    public void clearAndEnqueue(MessageWrapper wrapper) {
      if (wrapper != messageThatIsBeingWritten) throw new IllegalArgumentException("Wrapper:"+wrapper+" messageThatIsBeingWritten:"+messageThatIsBeingWritten);
      messageThatIsBeingWritten = null;
      if (writingSocket != null) {
        writingSocket.close();
        sockets.remove(writingSocket);
        writingSocket = null;
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
      
      P2PSocket socket; // null if we aren't registered, aka, we aren't pending/writing
      
      ByteBuffer originalMessage;
      ByteBuffer message;
      MessageCallback<Identifier, ByteBuffer> deliverAckToMe;
      Map<String, Integer> options;      
      
      boolean cancelled = false; // true when cancel is called
      
      MessageWrapper(
          ByteBuffer message, 
          MessageCallback<Identifier, ByteBuffer> deliverAckToMe, 
          Map<String, Integer> options, int priority, int seq) {

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
        if (deliverAckToMe != null) deliverAckToMe.ack(this);
      }

      /**
       * When is this registered?  May be registered too often.
       * 
       * @return true if should keep writing
       */
      public boolean receiveSelectResult(P2PSocket<Identifier> socket) throws IOException {
        if (this.socket != null && this.socket != socket) {
          // this shouldn't happen
          logger.log(this+"Socket changed!!! socket:"+socket+" writingSocket:"+writingSocket);
          socket.shutdownOutput();
          
          // do we need to reset?
          return false;
        }

        // in case we don't complete the write, remember where we are writing
        this.socket = socket;
        
        if (cancelled && message.position() == 0) {
          // cancel
          return true;
        } else {
          long bytesWritten;
          if ((bytesWritten = socket.write(message)) == -1) {
            // socket was closed, need to register new socket
            
            clearAndEnqueue(this); //             messageThatIsBeingWritten = null;            
            return false;
          }
          if (logger.level <= Logger.FINER) logger.log(this+" wrote "+bytesWritten+" bytes of "+message.capacity()+" remaining:"+message.remaining());

          if (message.hasRemaining()) {
            return false;
          }
        }
                
        EntityManager.this.complete(this); 
        return true;
      }
      
      public void drop() {
        // TODO: make sure we've done evrything necessary here to clean this up        
        if (deliverAckToMe != null) deliverAckToMe.sendFailed(this, new QueueOverflowException(identifier, originalMessage));
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
        return identifier;
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
        return "MessagWrapper{"+message+"}->"+identifier+" pri:"+priority+" seq:"+seq; 
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
        if (e instanceof NodeIsFaultyException) {
          markDead();
          return; 
        }

        errorHandler.receivedException(socket.getIdentifier(), e);
        closeMe(socket);
      }                    
      
      public void done(P2PSocket<Identifier> socket) throws IOException {
        if (logger.level <= Logger.FINER) logger.log(EntityManager.this+" read message of size "+buf.capacity()+" from "+socket);        
        callback.messageReceived(identifier, buf, socket.getOptions()); 
        new SizeReader(socket);
      }
      
      public String toString() {
        return "BufferReader{"+buf+"}";
      }
    }
  } // EntityManager
}