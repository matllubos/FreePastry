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
import org.mpisws.p2p.transport.util.DefaultErrorHandler;
import org.mpisws.p2p.transport.util.SocketRequestHandleImpl;
import org.mpisws.p2p.transport.wire.WireTransportLayer;

import rice.environment.Environment;
import rice.environment.logging.Logger;
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
  
  private SelectorManager selectorManager;
  
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
      ErrorHandler<Identifier> handler) {
    entityManagers = new HashMap<Identifier, EntityManager>();
    this.logger = env.getLogManager().getLogger(PriorityTransportLayerImpl.class, null);
    this.selectorManager = env.getSelectorManager();
    this.MAX_MSG_SIZE = maxMsgSize;
    this.tl = tl;    
    this.livenessProvider = livenessProvider;
    tl.setCallback(this);
    livenessProvider.addLivenessListener(this);
    this.errorHandler = handler;
    if (this.errorHandler == null) {
      this.errorHandler = new DefaultErrorHandler<Identifier>(logger); 
    }
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

  protected SocketRequestHandle<Identifier> openPrimarySocket(Identifier i, Map<String, Integer> options) {
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
        if (s != handle.getSubCancellable()) throw new IllegalArgumentException("s != handle.getSubCancellable() must be a bug. s:"+s+" sub:"+handle.getSubCancellable());
        getEntityManager(s.getIdentifier()).receiveSocketException(handle, ex);
      }
    }, options));
    
    return handle;
  }

  public void incomingSocket(final P2PSocket<Identifier> s) throws IOException {
    s.register(true, false, new P2PSocketReceiver<Identifier>() {
      public void receiveSelectResult(P2PSocket<Identifier> socket, boolean canRead, boolean canWrite) throws IOException {
        if (socket != s) throw new IllegalArgumentException("Sockets not equal!!! s:"+s+" socket:"+socket);
        if (canWrite || !canRead) throw new IllegalArgumentException("Should only be able to read! canRead:"+canRead+" canWrite:"+canWrite);
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
   * Responsible for writing messages to the socket.
   * 
   * Synchronization: all state is changed on the selector thread, except the queue, which must be carefully 
   * synchronized.
   * 
   * If we have something to write that means !queue.isEmpty() || messageThatIsBeingWritten != null, 
   *   we should have a writingSocket, or a pendingSocket
   * 
   * 
   * @author Jeff Hoye
   */
  class EntityManager {
    // TODO: think about the behavior of this when it wraps around...
    int seq = Integer.MIN_VALUE;
    Queue<MessageWrapper> queue; 
    Collection<P2PSocket<Identifier>> sockets;
    
    Identifier identifier;
    
    SocketRequestHandle<Identifier> pendingSocket; // the receipt that we are opening a socket
    P2PSocket<Identifier> writingSocket; // don't try to write to multiple socktes, it will confuse things
    P2PSocket<Identifier> closeWritingSocket; // could be a boolean, but we store the writingSocket here just for debugging, == writingSocket if should close it after the current write
    MessageWrapper messageThatIsBeingWritten; // the current message we are sending, if this is null, we aren't in the middle of sending a message
    // Invariant: if (messageThatIsBeingWritten != null) then (writingSocket != null)
    
    EntityManager(Identifier identifier) {
      this.identifier = identifier;
      queue = new PriorityQueue<MessageWrapper>();
      sockets = new HashSet<P2PSocket<Identifier>>();
    }

    public void clearState() {
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

    public String toString() {
      return "EM{"+identifier+"}";
    }
    
    /**
     * Read an error, or socket was closed.
     * 
     * @param socket
     * @return
     */
    public boolean closeMe(P2PSocket<Identifier> socket) {
      if (socket == writingSocket) {
        if (messageThatIsBeingWritten == null) {
          sockets.remove(socket);
          socket.close();
          writingSocket = null;
          if (!sockets.isEmpty()) {
            writingSocket = sockets.iterator().next();
          }
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
    
    public void incomingSocket(P2PSocket<Identifier> s, SocketRequestHandle<Identifier> receipt) {
      if (receipt != null) {
        if (receipt == pendingSocket) {
          pendingSocket = null;  // this is the one we requested
        } else {
          logger.log("receipt != pendingSocket!!! receipt:"+receipt+" pendingSocket:"+pendingSocket);
        } 
      }
      
      sockets.add(s);
      if (writingSocket == null) {
        if (messageThatIsBeingWritten != null) {
          throw new IllegalStateException("This is a bug, if there is no writingSocket, there should be no messageThatIsBeingWritten. writingSocket:"+writingSocket+" pending:"+messageThatIsBeingWritten);
        } 
        
        writingSocket = s;        
        
        // now that we have a socket, let's send the message
        synchronized(queue) {
          if (!queue.isEmpty()) {
            messageThatIsBeingWritten = queue.poll();
          }
        }
        
        // don't forget to register the message
        if (messageThatIsBeingWritten != null) {
          messageThatIsBeingWritten.register(writingSocket);
        }
      }
      
      // also, be able to read incoming messages on every socket
      new SizeReader(s);
    }

    /**
     * TODO: The synchronization here may need work.
     * 
     * @param handle
     * @param ex
     */
    public void receiveSocketException(SocketRequestHandleImpl<Identifier> handle, IOException ex) {      
      if (handle == pendingSocket) {
        pendingSocket = null; 
        if (messageThatIsBeingWritten == null && queue.isEmpty()) { 
          return;
        }
        if (sockets.isEmpty()) {
          // if we have a pending socket, we are expecting one to be opened          
          if (pendingSocket == null) {
            pendingSocket = openPrimarySocket(identifier, handle.getOptions());
          } 
        } else {
          // use an existing socket
          writingSocket = sockets.iterator().next();          
          if (messageThatIsBeingWritten == null) {
            synchronized(queue) {
            // our pendingSocket failed, and we don't have a messagThatIsBeingWritten because
            // it got cancelled or something... don't know 
              messageThatIsBeingWritten = queue.poll();
              if (messageThatIsBeingWritten != null)
                messageThatIsBeingWritten.register(writingSocket);
            }
          } else {
            if (messageThatIsBeingWritten.socket == null) {
              messageThatIsBeingWritten.register(writingSocket);  
            }
          }
        }
      }
    }

    public MessageRequestHandle<Identifier, ByteBuffer> send(ByteBuffer message, MessageCallback<Identifier, ByteBuffer> deliverAckToMe, final Map<String, Integer> options) {      
      if (logger.level <= Logger.FINER) logger.log(this+"send("+message+")");

      int priority = DEFAULT_PRIORITY;
      if (options != null) {
        if (options.containsKey(OPTION_PRIORITY)) {
          priority = options.get(OPTION_PRIORITY);          
        }
      }

      MessageWrapper ret;

      int remaining = message.remaining();
      if (remaining > MAX_MSG_SIZE) {
        ret = new MessageWrapper(message, deliverAckToMe, options, priority, 0);
        if (deliverAckToMe != null) 
          deliverAckToMe.sendFailed(ret, 
            new SocketException("Message too large. msg:"+message+" size:"+remaining+" max:"+MAX_MSG_SIZE));

        return ret; 
      }
      
      if (livenessProvider.getLiveness(identifier, options) >= LIVENESS_DEAD) {
        ret = new MessageWrapper(message, deliverAckToMe, options, priority, 0);
        if (deliverAckToMe != null) 
          deliverAckToMe.sendFailed(ret, new NodeIsFaultyException(identifier, message));
        return ret;
      }
      
      synchronized(queue) {
        ret = new MessageWrapper(message, deliverAckToMe, options, priority, seq++);        
        queue.add(ret);        
        if (queue.size() > MAX_MSG_SIZE) {          
          Iterator<MessageWrapper> it = queue.iterator();
          int ctr = 0;
          while(it.hasNext()) {
            MessageWrapper w = it.next();
            if (ctr>=MAX_QUEUE_SIZE) {
              it.remove();
              if (logger.level <= Logger.CONFIG) logger.log("Dropping "+w+" because queue is full. MAX_QUEUE_SIZE:"+MAX_QUEUE_SIZE);
              w.drop();
            }
            ctr++;
          }
        }
      }

      // schedule to start delivering on the selectorManager
      selectorManager.invoke(new Runnable(){      
        public void run() {
          // do we need to try to send messages?
          if ((messageThatIsBeingWritten == null) && (queue.isEmpty())) {
            return;
          }
          // we know we need to send messages
          
          // do we have a socket?          
          if (sockets.isEmpty()) {
            // if we have a pending socket, we are expecting one to be opened
            if (pendingSocket == null) {
              pendingSocket = openPrimarySocket(identifier, options);
            } 
          } else {
            if (writingSocket == null) {
              // use an existing socket
              writingSocket = sockets.iterator().next();
            }
          }
          
          if (writingSocket != null && messageThatIsBeingWritten == null) {
            synchronized(queue) {
              messageThatIsBeingWritten = queue.poll();
              messageThatIsBeingWritten.register(writingSocket);
            }
          }
        }      
      }); 
      
      return ret;
    }
    
    public void markDead() {
      synchronized(queue) {
        for (MessageWrapper msg : queue) {
          if (msg.deliverAckToMe != null) msg.deliverAckToMe.sendFailed(msg, new NodeIsFaultyException(identifier)); 
        }
      }      
    }
    
    class MessageWrapper implements 
        Comparable<MessageWrapper>, 
        MessageRequestHandle<Identifier, ByteBuffer>, 
        P2PSocketReceiver<Identifier> {
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
      
      public void receiveSelectResult(P2PSocket<Identifier> socket, boolean canRead, boolean canWrite) throws IOException {
        if (canRead || !canWrite) throw new IllegalStateException(this+" Expected only to write. canRead:"+canRead+" canWrite:"+canWrite+" socket:"+socket);
        if (this.socket != null && this.socket != socket) {
          // this must be because of a previous registration
          logger.log(this+"Socket changed!!! socket:"+socket+" writingSocket:"+writingSocket);
          socket.shutdownOutput();
          return;
        }

        // in case we don't complete the write, remember where we are writing
        this.socket = socket;
        
        if (cancelled && message.position() == 0) {
          // continue
        } else {
            
          long bytesWritten;
          if ((bytesWritten = socket.write(message)) == -1) {
            // socket was closed, need to register new socket
            sockets.remove(socket);
            socket.close();
            reset();
            writingSocket = null;
            socket = null;
            messageThatIsBeingWritten = null;
            
            synchronized(queue) {
              queue.add(this);
            } 
            
            if (sockets.isEmpty()) {
              // if we have a pending socket, we are expecting one to be opened
              if (pendingSocket == null) {
                pendingSocket = openPrimarySocket(identifier, socket.getOptions());
              } 
            } else {
              // use an existing socket
              writingSocket = sockets.iterator().next();
              synchronized(queue) {
                messageThatIsBeingWritten = queue.poll();
              }
            }
            return;
          }
          if (logger.level <= Logger.FINER) logger.log(this+" wrote "+bytesWritten+" bytes of "+message.capacity()+" remaining:"+message.remaining());

          if (message.hasRemaining()) {
            // can't write anymore, re-register
            socket.register(false, true, this);
            return;
          }
        }
        
        
        // done sending me
        if (!cancelled) {
          if (deliverAckToMe != null) deliverAckToMe.ack(this);
        }
        messageThatIsBeingWritten = null;
        
        // close the socket if we need to 
        if (closeWritingSocket == writingSocket) {
          writingSocket.close();
          writingSocket = null;
          closeWritingSocket = null;          
          if (sockets.isEmpty()) {
            // if we have a pending socket, we are expecting one to be opened
            
            boolean emptyQueue = queue.isEmpty();
            if (!emptyQueue) {
              if (pendingSocket == null) {
                pendingSocket = openPrimarySocket(identifier, null);
                return;
              } 
            }
          } else {
            // use an existing socket
            writingSocket = sockets.iterator().next();
          }          
        }

        if (writingSocket != null) {
          synchronized(queue) {
            if (!queue.isEmpty()) {
              messageThatIsBeingWritten = queue.poll();
            }
          }
          if (messageThatIsBeingWritten != null) messageThatIsBeingWritten.receiveSelectResult(socket, canRead, canWrite); // using recursion
        }
      }
      
      public void drop() {
        // TODO: make sure we've done evrything necessary here to clean this up        
        if (deliverAckToMe != null) deliverAckToMe.sendFailed(this, new QueueOverflowException(identifier, originalMessage));
      }
            
      public void receiveException(P2PSocket<Identifier> socket, IOException e) {
        sockets.remove(socket);
        socket.close();
        
        // make sure this is the socket we were writing on before resetting
        if (this.socket == socket) {
          this.socket = null;
          reset();
          if (this == messageThatIsBeingWritten) {
            messageThatIsBeingWritten = null;
            synchronized(queue) {
              queue.add(this);
            }
          }
        }
        
        if (socket == writingSocket) {
          // assert(pending == null)
          // should be true, because if I am getting this exception, then:
          //   a) this shouldn't be the writingSocket, or 
          //   b) I should have been pending, and set it to null above
          if (messageThatIsBeingWritten != null) throw new IllegalStateException("Pending should be null! pending:"+messageThatIsBeingWritten+" this:"+this+" socket:"+socket);
          
          writingSocket = null; 
          closeWritingSocket = null;          
          if (sockets.isEmpty()) {
            // if we have a pending socket, we are expecting one to be opened
            
            boolean emptyQueue = queue.isEmpty();
            if (!emptyQueue) {
              if (pendingSocket == null) {
                pendingSocket = openPrimarySocket(identifier, queue.peek().getOptions());
                return;
              } 
            }
          } else {
            // use an existing socket
            writingSocket = sockets.iterator().next();
            
            // keep things going
            // we already know pending is null
            synchronized(queue) {
              if (!queue.isEmpty()) {
                messageThatIsBeingWritten = queue.poll();
              }
            }
            messageThatIsBeingWritten.register(writingSocket); 
          }          
        }        
      }

      public void register(P2PSocket<Identifier> socket) {
        if (socket != this.socket) {
          reset();
        }
        
        this.socket = socket;
        socket.register(false, true, this);
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