/*
 * Created on Mar 25, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.pastry.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.TimerTask;

import rice.pastry.Log;
import rice.pastry.messaging.Message;
import rice.pastry.socket.messaging.AckMessage;
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
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
/**
 * Private class which is tasked with reading the greeting message off of a
 * newly connected socket. This greeting message says who the socket is coming
 * from, and allows the connected to hand the socket off the appropriate node
 * handle.
 *
 * @version $Id$
 * @author jeffh
 */
public class SocketManager implements SelectionKeyHandler {

  // the key to read from
  private SelectionKey key;

  // the reader reading data off of the stream
  private SocketChannelReader reader;

  // the writer (in case it is necessary)
  private SocketChannelWriter writer;

  // the node handle we're talking to
  private InetSocketAddress address;

  // the acks we are waiting for
  // Integer -> AckTimeoutEvent
  private Hashtable pendingAcks = new Hashtable();

  private int currentAckNumber = 0;
  
  private SocketCollectionManager scm;


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
    this(scm);
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
  public SocketManager(InetSocketAddress address, SocketCollectionManager scm) throws IOException {
    this(scm);
    createConnection(address);
  }

  /**
   * Private constructor which builds the socket channel reader and writer, as
   * well other bookkeeping objects for this socket manager.
   */
  private SocketManager(SocketCollectionManager scm) {
    this.scm = scm;
    reader = new SocketChannelReader(scm.pastryNode);
    writer = new SocketChannelWriter(scm.pastryNode);
  }

  /**
   * Method which closes down this socket manager, by closing the socket,
   * cancelling the key and setting the key to be interested in nothing
   */
  public void close() {
    try {
      synchronized (scm.manager.getSelector()) {
        if (key != null) {
          key.channel().close();
          key.cancel();
          key.attach(null);
          key = null;
        }
      }

      if (address != null) {
        scm.socketClosed(address, this);

        Iterator i = writer.getQueue().iterator();
        writer.reset();

        /**
         * Here, if we have not been declared dead, then we attempt to resend
         * the messages. However, if we have been declared dead, we reroute
         * the route messages via the pastry node, but delete any messages
         * routed directly.
         */
        while (i.hasNext()) {
          Object o = i.next();

          if (o instanceof Message) {
            scm.reroute(address, (Message) o);
          }
        }

        address = null;
      }
    } catch (IOException e) {
      System.out.println("ERROR: Recevied exception " + e + " while closing socket!");
    }
  }

  public void ackReceived(int i) {
    synchronized(pendingAcks) {
      AckTimeoutEvent ate = (AckTimeoutEvent)pendingAcks.remove(new Integer(i));
      if (ate != null) {
        ate.cancel();      
      } else {
        //System.out.println("SM.ackReceived("+i+"):Ack received late");
      }
    }
  }

  public void ackNotReceived(AckTimeoutEvent ate) {
//    System.out.println(this+"SM.ackNotReceived("+ate.ackNum+")");
    synchronized(pendingAcks) {
      AckTimeoutEvent nate = (AckTimeoutEvent)pendingAcks.remove(new Integer(ate.ackNum));
      if (nate == null) {
        // already received ack
        return;
      }
      scm.checkDead(address);
    //TODO: Reroute ate.msg
    }
  }

  protected void registerMessageForAck(SocketTransportMessage msg) {
    synchronized(pendingAcks) {
      AckTimeoutEvent ate = new AckTimeoutEvent(msg, this);
      pendingAcks.put(new Integer(ate.ackNum),ate);
      scm.pastryNode.scheduleTask(ate, scm.ACK_DELAY);
    }
  }

  protected void sendAck(SocketTransportMessage smsg) {
    writer.enqueue(new AckMessage(smsg.seqNumber));      
  }

  /**
   * The entry point for outgoing messages - messages from here are enqueued
   * for transport to the remote node
   *
   * @param message DESCRIBE THE PARAMETER
   */
  public void send(final Object message) {
    if (message instanceof SocketControlMessage) {
      writer.enqueue(message);      
    } else {
      try {
      SocketTransportMessage stm = new SocketTransportMessage((Message)message,currentAckNumber++);
      writer.enqueue(stm);      
      registerMessageForAck(stm);
      } catch (ClassCastException cce) {
        cce.printStackTrace();
        System.out.println(message.getClass().getName());
      }
    }
    if (key != null) {
      scm.manager.modifyKey(key);
    }
  }

  /**
   * Method which should change the interestOps of the handler's key. This
   * method should *ONLY* be called by the selection thread in the context of
   * a select().
   *
   * @param key The key in question
   */
  public void modifyKey(SelectionKey key) {
    if (!writer.isEmpty()) {
      key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
    }
  }



  /**
   * Specified by the SelectionKeyHandler interface. Is called whenever a key
   * has become acceptable, representing an incoming connection.
   *
   * @param key The key which is acceptable.
   */
  public void accept(SelectionKey key) {
    System.out.println("PANIC: read() called on SocketCollectionManager!");
  }

  /**
   * Specified by the SelectionKeyHandler interface - calling this tells this
   * socket manager that the connection has completed and we can now
   * read/write.
   *
   * @param key The key which is connectable.
   */
  public void connect(SelectionKey key) {
    try {
      if (((SocketChannel) key.channel()).finishConnect()) {
        // deregister interest in connecting to this socket
        key.interestOps(key.interestOps() & ~SelectionKey.OP_CONNECT);
      }

      scm.markAlive(address);

      debug("Found connectable channel - completed connection");
    } catch (Exception e) {
      debug("Got exception " + e + " on connect - marking as dead");
      System.out.println("Mark Dead due to failure to connect");
      scm.markDead(address);

      close();
    }
  }

  /**
   * Reads from the socket attached to this connector.
   *
   * @param key The selection key for this manager
   */
  public void read(SelectionKey key) {
    try {
      Object o = reader.read((SocketChannel) key.channel());

      if (o != null) {
        debug("Read message " + o + " from socket.");
        if (o instanceof AddressMessage) {
          if (address == null) {
            this.address = ((AddressMessage) o).address;
            scm.socketOpened(address, this);

            scm.markAlive(address);
          } else {
            System.out.println("SERIOUS ERROR: Received duplicate address assignments: " + this.address + " and " + o);
          }
        } else {
          if (o instanceof SocketTransportMessage) {
            SocketTransportMessage stm = (SocketTransportMessage)o;
            sendAck(stm);
            receive(stm.msg);
          } else if (o instanceof AckMessage) {
            ackReceived(((AckMessage)o).seqNumber);
          } else {
            receive((Message)o);
          }
        }
      }
    } catch (IOException e) {
      debug("ERROR " + e + " reading - cancelling.");
      scm.checkDead(address);
      close();
    }
  }


  /**
   * Writes to the socket attached to this socket manager.
   *
   * @param key The selection key for this manager
   */
  public void write(SelectionKey key) {
    try {
      if (writer.write((SocketChannel) key.channel())) {
        key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
      }
    } catch (IOException e) {
      debug("ERROR " + e + " writing - cancelling.");
      close();
    }
  }

  /**
   * Accepts a new connection on the given key
   *
   * @param serverKey The server socket key
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  protected void acceptConnection(SelectionKey serverKey) throws IOException {
    final SocketChannel channel = (SocketChannel) ((ServerSocketChannel) serverKey.channel()).accept();
    channel.socket().setSendBufferSize(scm.SOCKET_BUFFER_SIZE);
    channel.socket().setReceiveBufferSize(scm.SOCKET_BUFFER_SIZE);
    channel.configureBlocking(false);

    debug("Accepted connection from " + address);

    key = channel.register(scm.manager.getSelector(), SelectionKey.OP_READ);
    key.attach(this);
  }

  /**
   * Creates the outgoing socket to the remote handle
   *
   * @param address The accress to connect to
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  protected void createConnection(final InetSocketAddress address) throws IOException {
    final SocketChannel channel = SocketChannel.open();
    channel.socket().setSendBufferSize(scm.SOCKET_BUFFER_SIZE);
    channel.socket().setReceiveBufferSize(scm.SOCKET_BUFFER_SIZE);
    channel.configureBlocking(false);

    final boolean done = channel.connect(address);
    this.address = address;

    debug("Initiating socket connection to " + address);

    final SelectionKeyHandler handler = this;

    scm.manager.invoke(
      new Runnable() {
        public void run() {
          try {
            if (done) {
              key = channel.register(scm.manager.getSelector(), SelectionKey.OP_READ);
            } else {
              key = channel.register(scm.manager.getSelector(), SelectionKey.OP_READ | SelectionKey.OP_CONNECT);
            }

            key.attach(handler);
            scm.manager.modifyKey(key);
          } catch (IOException e) {
            System.out.println("ERROR creating server socket channel " + e);
          }
        }
      });

    send(new AddressMessage(scm.localAddress));
  }

  /**
   * Method which is called once a message is received off of the wire If it's
   * for us, it's handled here, otherwise, it's passed to the pastry node.
   *
   * @param message The receved message
   */
  protected void receive(Message message) {
    if (message instanceof NodeIdRequestMessage) {
      send(new NodeIdResponseMessage(scm.pastryNode.getNodeId()));
    } else if (message instanceof LeafSetRequestMessage) {
      send(new LeafSetResponseMessage(scm.pastryNode.getLeafSet()));
    } else if (message instanceof RouteRowRequestMessage) {
      RouteRowRequestMessage rrMessage = (RouteRowRequestMessage) message;
      send(new RouteRowResponseMessage(scm.pastryNode.getRoutingTable().getRow(rrMessage.getRow())));
    }
    /*
     *  else if (message instanceof PingMessage) {
     *  send(new PingResponseMessage(((PingMessage) message).getStartTime()));
     *  } else if (message instanceof PingResponseMessage) {
     *  int time = (int) (System.currentTimeMillis() - ((PingResponseMessage) message).getStartTime());
     *  if ((pings.get(address) == null) || (((Integer) pings.get(address)).intValue() > time)) {
     *  pings.put(address, new Integer(time));
     *  pool.update(address, SocketNodeHandle.PROXIMITY_CHANGED);
     *  }
     *  }
     */else {
      if (address != null) {
        scm.pastryNode.receiveMessage(message);
      } else {
        System.out.println("SERIOUS ERROR: Received no address assignment, but got message " + message);
      }
    }
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param s DESCRIBE THE PARAMETER
   */
  private void debug(String s) {
    if (Log.ifp(8)) {
      System.out.println(scm.pastryNode.getNodeId() + " (SM " + scm.pastryNode.getNodeId() + " -> " + address + "): " + s);
    }
  }

  /**
   * DESCRIBE THE CLASS
   *
   * @version $Id$
   * @author jeffh
   */
  class AckTimeoutEvent extends TimerTask {
    public int ackNum;
    SocketTransportMessage msg;
    SocketManager sman;

    /**
     * Constructor for DeadChecker.
     *
     * @param address DESCRIBE THE PARAMETER
     * @param numTries DESCRIBE THE PARAMETER
     * @param mgr DESCRIBE THE PARAMETER
     */
    public AckTimeoutEvent(SocketTransportMessage msg, SocketManager socketManager) {
      this.ackNum = msg.seqNumber;
      this.msg = msg;
      sman = socketManager;
    }

    /**
     * Main processing method for the DeadChecker object
     */
    public void run() {
      sman.ackNotReceived(this);
    }
  }
   
}
