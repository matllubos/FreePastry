/*
 *  Created on Mar 10, 2004
 *
 *  To change the template for this generated file go to
 *  Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.pastry.socket;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;

import rice.pastry.messaging.Message;
import rice.pastry.socket.messaging.PingMessage;
import rice.pastry.socket.messaging.PingResponseMessage;
import rice.pastry.wire.WireNodeHandle;
import rice.pastry.wire.WireNodeHandlePool;
import rice.pastry.wire.exception.DeserializationException;
import rice.pastry.wire.exception.ImproperlyFormattedMessageException;
import rice.pastry.wire.exception.SerializationException;
import rice.pastry.wire.messaging.datagram.AcknowledgementMessage;
import rice.pastry.wire.messaging.datagram.DatagramMessage;
import rice.pastry.wire.messaging.datagram.DatagramTransportMessage;

/**
 * @version $Id$
 * @author jeffh To change the template for this generated type comment go to
 *      Window>Preferences>Java>Code Generation>Code and Comments
 */
public class PingManager implements SelectionKeyHandler {

  /**
   * InetSocketAddress -> ArrayList of PingResponseListener
   */
  protected Hashtable pingListeners = new Hashtable();

  /**
   * DESCRIBE THE FIELD
   */
  protected ArrayList pendingMsgs;

  // the list of the last time a ping was sent
  private Hashtable pingtimes;

  private Hashtable pingResponseTimes;

  // the list of cached ping values
  private Hashtable pings;

  /**
   * the port number
   */
  private int port;

  /**
   * the selector manager
   */
  private SelectorManager manager;

  /**
   * a buffer used for read/writing datagrams
   */
  private ByteBuffer buffer;

  /**
   * the channel used from talking to the network
   */
  private DatagramChannel channel;

  /**
   * the key used to determine what has taken place
   */
  private SelectionKey key;

  // the node handle pool on this node
  private SocketNodeHandlePool pool;

  /**
   * the size of the buffer used to read incoming datagrams must be big enough
   * to encompass multiple datagram packets
   */
  public static int DATAGRAM_RECEIVE_BUFFER_SIZE = 131072;

  /**
   * the size of the buffer used to send outgoing datagrams this is also the
   * largest message size than can be sent via UDP
   */
  public static int DATAGRAM_SEND_BUFFER_SIZE = 65536;

  // the ping throttle, or how often to actually ping a remote node
  /**
   * DESCRIBE THE FIELD
   */
  public static int PING_THROTTLE = 10000;

  /**
   * @param port DESCRIBE THE PARAMETER
   * @param manager DESCRIBE THE PARAMETER
   * @param pool DESCRIBE THE PARAMETER
   */
  public PingManager(int port, SelectorManager manager, SocketNodeHandlePool pool) {
    this.port = port;
    this.manager = manager;
    this.pool = pool;
    pings = new Hashtable();
    pingtimes = new Hashtable();
    pingResponseTimes = new Hashtable();

    pendingMsgs = new ArrayList();
    // allocate enought bytes to read a node handle
    buffer = ByteBuffer.allocateDirect(DATAGRAM_SEND_BUFFER_SIZE);

    try {
      // bind to the appropriate port
      channel = DatagramChannel.open();
      channel.configureBlocking(false);
      InetSocketAddress isa = new InetSocketAddress(port);
      channel.socket().bind(isa);
      channel.socket().setSendBufferSize(DATAGRAM_SEND_BUFFER_SIZE);
      channel.socket().setReceiveBufferSize(DATAGRAM_RECEIVE_BUFFER_SIZE);

      Selector selector = manager.getSelector();

      synchronized (selector) {
        key = channel.register(selector, SelectionKey.OP_READ);
      }

      key.attach(this);
    } catch (IOException e) {
      System.out.println("PANIC: Error binding datagram server to port " + port + ": " + e);
      System.exit(-1);
    }
  }

  /**
   * Gets the LastTimePinged attribute of the PingManager object
   *
   * @param address DESCRIBE THE PARAMETER
   * @return The LastTimePinged value
   */
  public long getLastTimePinged(InetSocketAddress address) {
    return ((Long) pingtimes.get(address)).longValue();
  }

  /**
   * Gets the LastTimeHeardFrom attribute of the PingManager object
   *
   * @param address DESCRIBE THE PARAMETER
   * @return The LastTimeHeardFrom value
   */
  public long getLastTimeHeardFrom(InetSocketAddress address) {
    synchronized (pingResponseTimes) {
      Long l = (Long) pingResponseTimes.get(address);
      if (l != null) {
        return l.longValue();
      } else {
        return 0;
      }
    }
  }

  /**
   * Method which returns the last cached proximity value for the given address.
   * If there is no cached value, then DEFAULT_PROXIMITY is returned.
   *
   * @param address The address to return the value for
   * @return DESCRIBE THE RETURN VALUE
   */
  public int proximity(InetSocketAddress address) {
    Integer i = (Integer) pings.get(address);

    if (i == null) {
      return SocketNodeHandle.DEFAULT_PROXIMITY;
    }

    return i.intValue();
  }

  /**
   * Adds a feature to the PingResponseListener attribute of the PingManager
   * object
   *
   * @param address The feature to be added to the PingResponseListener
   *      attribute
   * @param prl The feature to be added to the PingResponseListener attribute
   */
  public void addPingResponseListener(InetSocketAddress address, PingResponseListener prl) {
    if (prl == null) {
      return;
    }
    synchronized (pingResponseTimes) {
      ArrayList list = (ArrayList) pingListeners.get(address);
      if (list == null) {
        list = new ArrayList();
        pingListeners.put(address, list);
      }
      list.add(prl);
    }
  }


  /**
   * DESCRIBE THE METHOD
   *
   * @param address DESCRIBE THE PARAMETER
   * @param msg DESCRIBE THE PARAMETER
   */
  public void enqueue(InetSocketAddress address, Object msg) {
    synchronized (pendingMsgs) {
      pendingMsgs.add(new PendingWrite(address, msg));
    }
    manager.modifyKey(key);
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param message DESCRIBE THE PARAMETER
   * @param address DESCRIBE THE PARAMETER
   */
  public void receiveMessage(Object message, InetSocketAddress address) {
    //System.out.println("PingMgr.receiveMessage("+message.getClass().getName()+")");
    if (message instanceof PingMessage) {
      enqueue(address, new PingResponseMessage(((PingMessage) message).getStartTime()));
    }
    if (message instanceof PingResponseMessage) {
      long curTime = System.currentTimeMillis();
      long startTime = ((PingResponseMessage) message).getStartTime();
      int time = (int) (curTime - startTime);

      if ((pings.get(address) == null) || (((Integer) pings.get(address)).intValue() > time)) {
        pings.put(address, new Integer(time));
        pool.update(address, SocketNodeHandle.PROXIMITY_CHANGED);
      }
      pingResponse(address, curTime);
    }
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param address DESCRIBE THE PARAMETER
   * @param curTime DESCRIBE THE PARAMETER
   */
  public void pingResponse(InetSocketAddress address, long curTime) {
    synchronized (pingResponseTimes) {
      pingResponseTimes.put(address, new Long(curTime));
      notifyPingResponseListeners(address, proximity(address), curTime);
    }
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param key DESCRIBE THE PARAMETER
   */
  public void write(SelectionKey key) {
    try {

      synchronized (pendingMsgs) {
        // last, write all pending datagrams
        Iterator i = pendingMsgs.iterator();
        //i = transmissionManager.getReady();

        while (i.hasNext()) {
          PendingWrite write = (PendingWrite) i.next();
          ByteBuffer buf = serialize(write.message);
          int num = channel.send(buf, write.address);
          i.remove();

          if (num == 0) {
            System.out.println("ERROR: 0 bytes were written (not fatal, but bad) - full buffer.");
          }
          //          debug("Wrote message " + write.getObject() + " to " + write.getDestination());
        }
        if (pendingMsgs.isEmpty()) {
          key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
        }
      }
    } catch (IOException e) {
      System.err.println("ERROR (datagrammanager:write): " + e);
    }
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param key DESCRIBE THE PARAMETER
   */
  public void accept(SelectionKey key) {
    throw new RuntimeException("accept() should not be called on PingManager");
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param key DESCRIBE THE PARAMETER
   */
  public void connect(SelectionKey key) {
    throw new RuntimeException("connect() should not be called on PingManager");
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param key DESCRIBE THE PARAMETER
   */
  public void modifyKey(SelectionKey key) {
    // this may be a synchronization issue, maybe the enqueue message should not hold pendingMsgs when calling
    synchronized (pendingMsgs) {
      if (!pendingMsgs.isEmpty()) {
        key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
      }
    }
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param key DESCRIBE THE PARAMETER
   */
  public void read(SelectionKey key) {

    WireNodeHandle handle = null;

    try {
      InetSocketAddress address = null;

      while ((address = (InetSocketAddress) channel.receive(buffer)) != null) {

        //debug("Received data from address " + address);
        buffer.flip();

        if (buffer.remaining() > 0) {
          Object o = deserialize(buffer);
//              System.out.println("REC:"+o);
          receiveMessage(o, address);
        } else {
//              debug("Read from datagram channel, but no bytes were there - no bad, but wierd.");
          buffer.clear();
//              System.out.println("Read from datagram channel, but no bytes were there - no bad, but wierd.");
          break;
        }
      }
    } catch (IOException e) {
      System.out.println("ERROR (datagrammanager:read): " + e);
//          debug("ERROR (datagrammanager:read): " + e);
      e.printStackTrace();
    }
  }


  /**
   * Method which initiates a ping to the remote node. Once the ping is
   * complete, the result will be available via the proximity() call.
   *
   * @param address The address to ping
   * @param prl DESCRIBE THE PARAMETER
   */
  protected void ping(InetSocketAddress address, PingResponseListener prl) {
    ping(address, prl, false);
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param address DESCRIBE THE PARAMETER
   * @param prl DESCRIBE THE PARAMETER
   */
  protected void forcePing(InetSocketAddress address, PingResponseListener prl) {
    ping(address, prl, true);
  }

  /**
   * caller must synchronized(pingResponseTimes)
   *
   * @param address
   * @param proximity
   * @param lastTimePinged
   */
  protected void notifyPingResponseListeners(InetSocketAddress address, int proximity, long lastTimePinged) {
    ArrayList list = (ArrayList) pingListeners.get(address);
    if (list != null) {
      Iterator i = list.iterator();
      while (i.hasNext()) {
        PingResponseListener prl = (PingResponseListener) i.next();
        prl.pingResponse(address, proximity, lastTimePinged);
        i.remove();
      }
    }
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param address DESCRIBE THE PARAMETER
   * @param prl DESCRIBE THE PARAMETER
   * @param force DESCRIBE THE PARAMETER
   */
  private void ping(InetSocketAddress address, PingResponseListener prl, boolean force) {
    synchronized (pingResponseTimes) {
      if (force ||
        (pingtimes.get(address) == null) ||
        (System.currentTimeMillis() - getLastTimePinged(address) > PING_THROTTLE)) {
        pingtimes.put(address, new Long(System.currentTimeMillis()));
        addPingResponseListener(address, prl);
        enqueue(address, new PingMessage());
      } else {
        if (getLastTimeHeardFrom(address) >= getLastTimePinged(address)) {
          // we just pinged them, and got a response
          if (prl != null) {
            prl.pingResponse(address, proximity(address), getLastTimeHeardFrom(address));
          }
        } else {
          // we are still waiting to hear from someone
          addPingResponseListener(address, prl);
        }
      }
    }
  }


  /**
   * Method which serializes a given object into a ByteBuffer, in order to
   * prepare it for writing.
   *
   * @param o The object to serialize
   * @return A ByteBuffer containing the object
   * @exception IOException if the object can't be serialized
   */
  public static ByteBuffer serialize(Object o) throws IOException {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);

      // write out object and find its length
      oos.writeObject(o);

      int len = baos.toByteArray().length;
      //System.out.println("serializingD " + o + " len=" + len);

      return ByteBuffer.wrap(baos.toByteArray());
    } catch (InvalidClassException e) {
      System.out.println("PANIC: Object to be serialized was an invalid class!");
      throw new SerializationException("Invalid class during attempt to serialize.");
    } catch (NotSerializableException e) {
      System.out.println("PANIC: Object to be serialized was not serializable! [" + o + "]");
      throw new SerializationException("Unserializable class " + o + " during attempt to serialize.");
    }
  }

  /**
   * Method which takes in a ByteBuffer read from a datagram, and deserializes
   * the contained object.
   *
   * @param buffer The buffer read from the datagram.
   * @return The deserialized object.
   * @exception IOException if the buffer can't be deserialized
   */
  public static Object deserialize(ByteBuffer buffer) throws IOException {
    int len = buffer.remaining();
    byte[] array = new byte[len];

    // copy the data into the buffer
    buffer.get(array);
    buffer.clear();

    ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(array));

    try {
      Object o = ois.readObject();

      return o;
    } catch (ClassNotFoundException e) {
      System.out.println("PANIC: Unknown class type in serialized message!");
      throw new ImproperlyFormattedMessageException("Unknown class type in message - closing channel.");
    } catch (InvalidClassException e) {
      System.out.println("PANIC: Serialized message was an invalid class!");
      throw new DeserializationException("Invalid class in message - closing channel.");
    }
  }

  /**
   * DESCRIBE THE CLASS
   *
   * @version $Id$
   * @author jeffh
   */
  public class PendingWrite {
    InetSocketAddress address;
    Object message;

    /**
     * Constructor for PendingWrite.
     *
     * @param adr DESCRIBE THE PARAMETER
     * @param m DESCRIBE THE PARAMETER
     */
    public PendingWrite(InetSocketAddress adr, Object m) {
      address = adr;
      message = m;
    }
  }

}
