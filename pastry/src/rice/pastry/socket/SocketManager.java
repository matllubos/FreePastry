/*
 * Created on Mar 25, 2004
 */
package rice.pastry.socket;

import java.io.IOException;
import java.net.BindException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.TimerTask;

import rice.pastry.Log;
import rice.pastry.NodeHandle;
import rice.pastry.messaging.Message;
import rice.pastry.socket.exception.AcceptFailedException;
import rice.pastry.socket.exception.SocketClosedByRemoteHostException;
import rice.pastry.socket.messaging.AddressMessage;
import rice.pastry.socket.messaging.LeafSetRequestMessage;
import rice.pastry.socket.messaging.LeafSetResponseMessage;
import rice.pastry.socket.messaging.NodeIdRequestMessage;
import rice.pastry.socket.messaging.NodeIdResponseMessage;
import rice.pastry.socket.messaging.RouteRowRequestMessage;
import rice.pastry.socket.messaging.RouteRowResponseMessage;
import rice.pastry.socket.messaging.SocketControlMessage;
import rice.pastry.socket.messaging.SocketTransportMessage;

/**
 * Private class which manages a single socket.  It can be used in 3 instances:
 * 1) control/routing traffic for a ConnectionManager
 * 2) data traffic for a ConnectionManager
 * 3) A temporary socket for the SocketPastryNodeFactory
 * 
 * When it is on the receiving end of a new socket, it doesn't know it's remote
 * address, or it's type of connection until it receives its first AddressMessage.
 * 
 * It can be closed() if it is idle().  It is idle() if it hasn't been used for 
 * 
 * It is a liveness listener so that it will not read unless the node was able to
 * respond to a ping, thus elevating the liveness to above UNKNOWN.
 * 
 * @author Jeff Hoye, Alan Mislove
 */
public class SocketManager implements SelectionKeyHandler, LivenessListener {


	boolean nagle = true;

	// the key to read/write from/to
  private SelectionKey key;

  // the reader reading data off of the stream
  private SocketChannelReader reader;

  // the writer (in case it is necessary)
  private SocketChannelWriter writer;

  // the node handle we're talking to
  InetSocketAddress address;

  private SocketCollectionManager scm;

  /** 
   * An anonamous inner class.  Invariant: if checkDeadTask != null, it is running and can be cancelled.
   */
  private TimerTask checkDeadTask = null;

  public Exception closedTrace;
  Exception openedTrace;
  Exception cmSet;

  /**
   * The type of SocketManager this represents.  It will be 
   * ConnectionManager.TYPE_CONTROL, TYPE_DATA
   */
  int type = 0;
  
  ConnectionManager connectionManager;

  /**
   * This variable is true if we have actually called connect on this SM.
   * It can be false, if there are not enough sockets available, and therefore
   * this SM is waiting to have createConnection() called on it.
   */
  boolean connecting = false;

  /**
   * This variable is true once we have actually connected to the remote socket
   * and can begin communication.
   */
  boolean connected = false;
  
  /**
   * This variable is true when the socket has been closed.
   */
  boolean closed = false;

  /**
   * This variable is true when the output channel has been closed.
   */
  private boolean halfClosed = false;

  /**
   * This variable keeps track of how many times we've tried to connect.  It is the 
   */
  int numTriesToConnect = 0;
  boolean bindFailure = false;  

  /**
   * The last object written... for debugging purposes.
   */
  Object lastWritten;

  /**
   * Which constructor was called... for debugging purposes.
   */
  int ctor;
  
  /**
   * Constructor which accepts an incoming connection, represented by the
   * selection key. This constructor builds a new SocketManager, and waits
   * until the greeting message is read from the other end. Once the greeting
   * is received, the manager makes sure that a socket for this handle is not
   * already open, and then proceeds as normal.
   *
   * @param key The server accepting key for the channel
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  public SocketManager(SelectionKey key, SocketCollectionManager scm) throws IOException {
    this(scm,0);
    ctor = 1;
    acceptConnection(key);
  }
  
  /**
   * Constructor which creates an outgoing connection to the given node
   * handle. This creates the connection by building the socket and sending
   * accross the greeting message. Once the response greeting message is
   * received, everything proceeds as normal.
   *
   * @param address DESCRIBE THE PARAMETER
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  public SocketManager(InetSocketAddress address, SocketCollectionManager scm, ConnectionManager cm, int type) {
    this(scm,type);    
    ctor = 2;
    connectionManager = cm;
    cmSet = new RuntimeException("Stack Trace");
    this.address = address;
    if (!sentAddress) {
      send(new AddressMessage(scm.getLocalNodeHandle(),connectionManager.snh,type));
      sentAddress = true;
    }
  }

  /**
   * Private constructor which builds the socket channel reader and writer, as
   * well other bookkeeping objects for this socket manager.
   */
  private SocketManager(SocketCollectionManager scm, int type) {
    ctor = 3;
    markActive();
    this.scm = scm;
    this.type = type;
//    if (type == ConnectionManager.TYPE_DATA)
//      System.out.println("SM.ctor("+type+")");
    //sThread.dumpStack();
    reader = new SocketChannelReader(scm.pastryNode, this);
    writer = new SocketChannelWriter(scm.pastryNode, this);
  }
  
  

  int INITIAL_CONNECTION_RETRY_WAIT_TIME = 5000;
  int MAX_NUM_RETRIES_FOR_CONNECTION = 5;
  double CONNECTION_RETRY_FACTOR = 2.0;

  /**
   * So what happened is the remote server is too busy to accept connections?  
   * Rather than giving up, we're going to try a few times to do this.  This 
   * implementation schedules a TimerTask for execution RETRY_TO_CONNECT_DELAY 
   * millis later.  That timer task then invokes another runnable which will 
   * actually requestToOpenSocket, but it does it on the Selector thread.
   * 
   * We can't call requestToOpen socket on the TimerThread becasue we will
   * be violating the policy that everything except invoke is done on the 
   * Selector Thread.
   *
   * @param manager the manager
   * @param numTriesToConnect how many failures we've had so far
   */
  void retryConnection(int numTriesToConnect, boolean delay) {
    scm.socketPoolManager.relenquishPermit(this);
    connectionManager.checkDead();
//    System.out.println("CM.retryConnectionLater("+manager.getType()+","+numTriesToConnect+")");
    if (numTriesToConnect > MAX_NUM_RETRIES_FOR_CONNECTION) {
      System.out.println(this+".retryConnectionLater() stopped trying to reconnect after "+numTriesToConnect+" failed attempts.");
      close();
    } else {
      if (delay) {
        scm.scheduleTask(new TimerTask() {
          public void run() {
  
            scm.manager.invoke(new Runnable() {
              public void run() {
  //              System.out.println("CM.retryConnectionLater():requestingToOpenSocket("+manager+")");
                if (!closed && (connectionManager.getLiveness() < NodeHandle.LIVENESS_FAULTY)) {  // this can happen if we markDead while we are waiting to connect                  
                  tryToCreateConnection();
                }
              }
            });
  
          }
        },(int)(INITIAL_CONNECTION_RETRY_WAIT_TIME*Math.pow(CONNECTION_RETRY_FACTOR, numTriesToConnect))); 
      } else {
        tryToCreateConnection();
//        scm.socketPoolManager.requestToOpenSocket(manager);        
      }
    }   
  }




  boolean sentAddress = false;

  // ***************** Connection Lifecycle ************************

  /**
   * Queues an AddressMessage with the identifier/type of this socket.  
   * Calls SocketPoolManager.requestToOpenSocket() which will call 
   * createConnection() now, or later.
   */
  public void tryToCreateConnection() {
    if (ctor != 2) {
      Thread.dumpStack();
    }
//    if (!sentAddress) {
//      send(new AddressMessage(scm.returnAddress,scm.getLocalNodeHandle(),connectionManager.getNodeId(),type));
//      sentAddress = true;
//    }
    scm.socketPoolManager.requestToOpenSocket(this);
  }

  /**
   * Creates the outgoing socket to the remote handle
   *
   * @param address The accress to connect to
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  private void createConnection() throws IOException {
    //Thread.dumpStack();
    try {
      if (numTriesToConnect > 0) {
        //System.out.println("SM.createConnection():"+numTriesToConnect);
      }
      if (connecting) {
        Thread.dumpStack();
        return;
      }
      connecting = true;
  
      SocketChannel channel = SocketChannel.open();
      channel.socket().setSendBufferSize(SocketCollectionManager.SOCKET_BUFFER_SIZE);
      channel.socket().setReceiveBufferSize(SocketCollectionManager.SOCKET_BUFFER_SIZE);
      if (!nagle) {
        channel.socket().setTcpNoDelay(true);
      }
      channel.configureBlocking(false);
      
      boolean done = channel.connect(address);
  
      debug("Initiating socket connection to " + address);
  
      SelectionKeyHandler handler = this;
  
      if (done) {
        key = channel.register(scm.manager.getSelector(), SelectionKey.OP_READ);
        //System.out.println("SM.createConnection(): Registering "+this);
      } else {
        if (connectionManager != null) {
          if (checkDeadTask == null) {
            checkDeadTask = 
              new TimerTask() {
                /**
                 * This runs while we are connecting every 5 seconds.  It calls checkDead() on the selector thread.
                 */
                public void run() {
                  scm.manager.invoke(new Runnable() {
                    public void run() {
                      connectionManager.checkDead();
                    }
                  });
                }
    					};
            scm.scheduleTask(checkDeadTask,5000,5000);
          }
        }
        key = channel.register(scm.manager.getSelector(), SelectionKey.OP_READ | SelectionKey.OP_CONNECT);
//        System.out.println("SM.createConnection(): Registering(2) "+this);
      }
  
      key.attach(handler);
      enableWriteOpIfNecessary(key);
      if (done) {
        if (numTriesToConnect > 0) {
          System.out.println("SM.createConnection():reconnection successful after "+numTriesToConnect+" attempts! "+bindFailure);
        }
        connected = true;        
        scm.socketPoolManager.socketOpened(this);
      }
    } catch (IOException ioe) {
      if (!tryToHandleIOException(ioe)) {
        throw ioe;
      }
    }
  }

  /**
	 * @param ioe
	 * @return
	 */
	private boolean tryToHandleIOException(IOException ioe) {
    if (ioe instanceof BindException) { // Address already in use: No further information        
      //be.printStackTrace();
      // This is because 2 sockets tried to accept with the same port "concurrently" retry again
      connecting = false;
      connected = false;
      bindFailure = true;
      numTriesToConnect++;
        
      if (connectionManager != null) {
        /*connectionManager.*/retryConnection(numTriesToConnect,false);
      }
      return true;
    }
    
    if (ioe instanceof ConnectException) {
      // This means that the remote server was busy, and we should try again later
      connecting = false;
      connected = false;
      numTriesToConnect++;
        
      if (connectionManager != null) {
        /*connectionManager.*/retryConnection(numTriesToConnect,true);
      }
      return true;
    }
		return false;
	}
  
  private void cancelCheckDeadTask() {
    if (checkDeadTask != null) {
      try {
        checkDeadTask.cancel();
      } catch (Exception e) {}
      checkDeadTask = null;
    }
  }
  /**
   * Specified by the SelectionKeyHandler interface - calling this tells this
   * socket manager that the connection has completed and we can now
   * read/write.
   *
   * @param key The key which is connectable.
   */
  public boolean connect(SelectionKey key) {    
    if (connected) {
      Thread.dumpStack();
      return true;
    }
    try {
      if (((SocketChannel) key.channel()).finishConnect()) {
        // deregister interest in connecting to this socket
        key.interestOps(key.interestOps() & ~SelectionKey.OP_CONNECT);
        cancelCheckDeadTask();
      }

      debug("Found connectable channel - completed connection");
    } catch (IOException e) {   
      if (!tryToHandleIOException(e)) {
        e.printStackTrace();
        if (connectionManager != null) {
          connectionManager.checkDead(); //markDead();
        }
        close();
        return true;
      } else {
        return true;
      }
    }
    if (numTriesToConnect > 0) {
      if (bindFailure) {
        if (ConnectionManager.LOG_LOW_LEVEL)
          System.out.println(this+".connect():reconnection successful after "+numTriesToConnect+" attempts! :"+bindFailure);
      } else {
        if (ConnectionManager.LOG_LOW_LEVEL)
          System.out.println(this+".connect():reconnection successful after "+numTriesToConnect+" attempts!");
      }
    }
    scm.socketPoolManager.socketOpened(this);
    return true;
  }

  

  /**
   * Accepts a new connection on the given key
   *
   * @param serverKey The server socket key
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  protected void acceptConnection(SelectionKey serverKey) throws IOException {
    if (connected) {
      Thread.dumpStack();
      return;
    }
    connecting = true;
    connected = true;
    SocketChannel channel = (SocketChannel) ((ServerSocketChannel) serverKey.channel()).accept();
    if ((channel == null) || (channel.socket() == null)) {
      throw new AcceptFailedException();
    }
    channel.socket().setSendBufferSize(SocketCollectionManager.SOCKET_BUFFER_SIZE);
    channel.socket().setReceiveBufferSize(SocketCollectionManager.SOCKET_BUFFER_SIZE);
    if (!nagle) {
      channel.socket().setTcpNoDelay(true);
    }
    channel.configureBlocking(false);

    debug("Accepted connection from " + address);

    key = channel.register(scm.manager.getSelector(), SelectionKey.OP_READ);
    //System.out.println("SM.acceptConnection(): Registering "+this);
    key.attach(this);
  }

  public void closeHalf() {
    if (ConnectionManager.LOG_LOW_LEVEL)
      System.out.println(this+".closeHalf()");
    try {
      halfClosed = true;
      try {
        ((SocketChannel)key.channel()).socket().shutdownOutput();
      } catch (NullPointerException npe) {
        npe.printStackTrace();
        System.out.println(this+".closeHalf():"+key+","+closed+","+ctor);
        //closedTrace.printStackTrace();
      }
      IDLE_THRESHOLD = HALF_CLOSED_IDLE_THRESHOLD;  // this code says that if we don't hear anything from the node for the HALF_CLOSED threshold, then the socket can be collected.
    } catch (IOException e) {
      System.out.println("ERROR: Recevied exception " + e + " while closing socket!");
    }
  }

  /**
   * Method which closes down this socket manager, by closing the socket,
   * cancelling the key and setting the key to be interested in nothing
   */
  public void close() {
    if (ConnectionManager.LOG_LOW_LEVEL)
      System.out.println(this+".close()");
    closedTrace = new RuntimeException("closed here");
    closed = true;
    cancelCheckDeadTask(); // stop checking dead on my account
    //Thread.dumpStack();
    try {
        if (key != null) {
          SocketChannel sc = (SocketChannel)key.channel();
          key.channel().close();
          key.cancel();
          if (ConnectionManager.LOG_LOW_LEVEL)
            System.out.println("SM.close() cancelled "+this);
          key.attach(null);
          key = null;
        }
    } catch (IOException e) {
      System.out.println("ERROR: Recevied exception " + e + " while closing socket!");
    }
    
    //System.out.println("SM.close()"+this+" @"+System.identityHashCode(this));
    if (connectionManager != null) {
//      System.out.println("Closing socket");
      connectionManager.removeLivenessListener(this);
      connectionManager.socketClosed(this);    
    } else {        
      scm.socketPoolManager.socketClosed(this);    
//      System.out.println("SM.close()"+this+" @"+System.identityHashCode(this));
//      Thread.dumpStack();
//      throw new RuntimeException("No connectionManager found.");
    }
  }


  // *************** Send/Receive Lifecycle *************************
  /**
   * The entry point for outgoing messages - messages from here are enqueued
   * for transport to the remote node.  Writer will call messageEnqueued() which will set the write bit
   *
   * @param message DESCRIBE THE PARAMETER
   */
  public boolean send(final Object message) {
    if (sentAddress == false && ctor == 2 && !(message instanceof AddressMessage)) {
      System.out.println(message);
      Thread.dumpStack();
    }
    if (ConnectionManager.LOG_LOW_LEVEL)
      System.out.println("ENQ2:@"+System.currentTimeMillis()+":"+this+":"+message+":"+message.getClass().getName());

//    if (thatHalfClosed) {
//      return false;
//    }

    lastWritten = message;
    
//    System.out.println("SM<"+type+">.send("+message+")");
      writer.enqueue(message); 
      markActive();
      return true;  
  }

  /**
   * Called by the writer when something is in queue to be written.
   *
   */
  void messageEnqueued() {
    if (key != null) {
      enableWriteOpIfNecessary(key);
    }
  }

  /**
   * Method which should change the interestOps of the handler's key. This
   * method should *ONLY* be called by the selection thread.
   *
   * @param key The key in question
   */
  private void enableWriteOpIfNecessary(SelectionKey key) {    
    try {
      if (connecting && !writer.isEmpty()) {
        key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
      }
    } catch (CancelledKeyException e) {
      e.printStackTrace();
      System.out.println("Error CKE:"+this);
    }    
  }

  /**
   * Writes to the socket attached to this socket manager.
   *
   * @param key The selection key for this manager
   */
  public boolean write(SelectionKey key) {
    try {
      if (writer.write((SocketChannel) key.channel())) {
        key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
      }
      markActive();
    } catch (IOException e) {
      debug("ERROR " + e + " writing - cancelling.");
      close();
    }    
    return true;
  }

  /**
   * Reads from the socket attached to this connector.
   *
   * @param key The selection key for this manager
   */
  public boolean read(SelectionKey key) {
    markActive();
    try {
      //System.out.println("SM<"+type+">.read():1");
      Object o = reader.read((SocketChannel) key.channel());
      //System.out.println("SM<"+type+">.read("+o+"):2");

      if (o != null) {
        debug("Read message " + o + " from socket.");
        if (ConnectionManager.LOG_LOW_LEVEL)
          System.out.println("REC:@"+System.currentTimeMillis()+":"+this+":"+o);

        if (o instanceof AddressMessage) {
          AddressMessage am = (AddressMessage) o;
          if (!am.receiver.equals(scm.getLocalNodeHandle())) {
            //System.out.println("ERROR: SocketManager recieved connection for incorrect NodeId expected "+scm.getLocalNodeHandle()+" received "+am.receiver);
            close();
          } else {
            if (address == null) {
              this.address = am.sender.getAddress();
              if (this.address == null) {
                Thread.dumpStack();
              }
  //            System.out.println("SM<"+type+">.read("+o+") -> "+am.type);
              type = am.type;
              
              scm.newSocketManager(am.sender, this);
              //if (connectionManager != null) {
              // socket could have been closed if there was already a connection pending going the other way
                if (!closed && connectionManager.liveness == NodeHandle.LIVENESS_UNKNOWN) {
                  if (ConnectionManager.LOG_LOW_LEVEL)
                    System.out.println(this+".read(): disabled read ops");
                  key.interestOps((key.interestOps() & ~SelectionKey.OP_READ));  // turn off read ops
                  connectionManager.addLivenessListener(this);
                }
                //connectionManager.markAlive();
              //}
              
            } else {
              System.out.println("SERIOUS ERROR: Received duplicate address assignments: " + this.address + " and " + o);
            }
          }
        } else {
          receive((Message)o);
        }
      }
    } catch (SocketClosedByRemoteHostException se) {
//      System.out.println("SocketManager.read() " + e + " - closing."+this);
      if (connectionManager != null) {
        if (!reader.readOnce)
          connectionManager.failedDuringOpen = true;
        connectionManager.checkDead();
      }
      close();      
    } catch (IOException e) {
      if (ConnectionManager.LOG_LOW_LEVEL)
        System.out.println("SocketManager.read() " + e + " - closing."+this);
      //e.printStackTrace();
      debug("ERROR " + e + " reading - cancelling.");
      if (connectionManager != null) {
        if (!reader.readOnce)
          connectionManager.failedDuringOpen = true;
        connectionManager.checkDead();
      }
      close(); 
    }
    return true;
    //System.out.println("SM<"+type+">.read():3");
  }



  /**
   * Specified by the SelectionKeyHandler interface. Is called whenever a key
   * has become acceptable, representing an incoming connection.
   *
   * @param key The key which is acceptable.
   */
  public boolean accept(SelectionKey key) {
    System.out.println("PANIC: read() called on SocketCollectionManager!");
    return true;
  }


  /**
   * Method which is called once a message is received off of the wire If it's
   * for us, it's handled here, otherwise, it's passed to the pastry node.
   *
   * @param message The receved message
   */
  protected void receive(Message message) {
    if (message instanceof SocketControlMessage) { // optimization
    
      if (message instanceof NodeIdRequestMessage) {
        send(new NodeIdResponseMessage((SocketNodeHandle)scm.pastryNode.getLocalHandle()));
      } else if (message instanceof LeafSetRequestMessage) {
        send(new LeafSetResponseMessage(scm.pastryNode.getLeafSet()));
      } else if (message instanceof RouteRowRequestMessage) {
        RouteRowRequestMessage rrMessage = (RouteRowRequestMessage) message;
        send(new RouteRowResponseMessage(scm.pastryNode.getRoutingTable().getRow(rrMessage.getRow())));
      } else {
        if (connectionManager != null) {
          connectionManager.receiveMessage(message);
        } else if (address != null) {
          scm.pastryNode.receiveMessage(message);
        } else {
          System.out.println("SERIOUS ERROR: Received no address assignment, but got message " + message);
        }
      }      
    } else {
      if (connectionManager != null) {
        connectionManager.receiveMessage(message);
      } else if (address != null) {
        scm.pastryNode.receiveMessage(message);
      } else {
        System.out.println("SERIOUS ERROR: Received no address assignment, but got message " + message);
      }
    }
  }
  
  // ************** Other methods ********************

  /**
   * Log trace.
   *
   * @param s trace to be logged.
   */
  private void debug(String s) {
    if (Log.ifp(8)) {
      System.out.println(scm.pastryNode.getNodeId() + " (SM " + scm.pastryNode.getNodeId() + " -> " + address + "): " + s);
    }
  }

  /**
   * True if hasn't been used for IDLE_THRESHOLD seconds,
   * and it doesn't have anything in its read/write bufferes,
   * and the connection manager agrees that it can be idle.
   * 
   * @return
   */
  public boolean isIdle() {
    if (System.currentTimeMillis() - lastTimeActive > IDLE_THRESHOLD) {    
      if (connectionManager != null && !connectionManager.isIdleControl(this)) {
        return false;
      }
      return writer.isEmpty() && !reader.isDownloading();        
    }
    return false; // busy
  }

  long lastTimeActive = 0;
  long IDLE_THRESHOLD = 1000;
  long HALF_CLOSED_IDLE_THRESHOLD = 60000;
  
  void markActive() {
    lastTimeActive = System.currentTimeMillis();
    if (connected) {
      scm.socketPoolManager.socketUpdated(this);
    }
  }

  /**
   * The type of connection this SocketManager represents: ConnectionManager.TYPE_CONTROL, TYPE_DATA
   * @return ConnectionManager.TYPE_CONTROL, TYPE_DATA
   */
  public int getType() {
    return type;
  }

	/**
   * Set's the ConnectionManager
	 * @param manager this SM's new CM
	 */
	public void setConnectionManager(ConnectionManager manager) {
    connectionManager = manager;		
    cmSet = new RuntimeException("Stack Trace2");
	}   
  
  /**
   * yee ol' toString()
   */
  public String toString() {
    String s = " key:";
    if (key == null) {
      s+="null";
    } else {
      s+=System.identityHashCode(key);
    }
    return "SocketManager<"+type+","+ctor+">@"+System.identityHashCode(this)+":"+scm.addressString()+" -> "+address+s+" "+getStatus();//+":"+lastWritten;
  }
  
  public String getStatus() {
    String s1 = "live: ";
    if (connectionManager != null) {
      s1 += connectionManager.getLiveness();
    } else {
      s1 = "live: null";
    }
    boolean cd = checkDeadTask != null;
    String s = null;
    
    try {
      s = ""+key.isWritable();
    } catch (Exception e) {}
    
    return "{"+s1+",ctor:"+ctor+",conn1:"+connecting+",conn2:"+connected+",idle:"+isIdle()+",clos:"+closed+",halfclosed:"+halfClosed+",numTries:"+numTriesToConnect+",deadChecker:"+cd /*+",w.q:"+writer.getSize()+",w.e:"+writer.isEmpty()+",w.k:"+s*/+"}";
  }

	/**
	 * Supposed to only be called from SocketPoolManager
	 */
	public void openSocket() throws IOException {
    createConnection();
	}

	/**
	 * @param o
	 */
	public void messageTooLarge(SocketTransportMessage stm, int len) {
		if (connectionManager != null) {
      connectionManager.messageTooLarge(stm, len);
		}
	}

  public Iterator getPendingMessages() {
    return writer.getQueue().iterator();
  }

  public int getNumberPendingMessages() {
    return writer.getQueue().size();    
  }

	public void updateLiveness(NodeHandle nh, int liveness) {
    if (liveness != NodeHandle.LIVENESS_UNKNOWN) {
      if (ConnectionManager.LOG_LOW_LEVEL)
        System.out.println(this+".updateLiveness():enabling read ops");
      key.interestOps(key.interestOps() | SelectionKey.OP_READ);      
      connectionManager.removeLivenessListener(this);
    }    
  }

}
