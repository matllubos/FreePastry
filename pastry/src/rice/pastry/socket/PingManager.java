/*
 *  Created on Mar 10, 2004
 *
 *  To change the template for this generated file go to
 *  Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.pastry.socket;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

import rice.pastry.messaging.*;
import rice.pastry.socket.messaging.*;
import rice.selector.*;

/**
 * @version $Id$
 * @author jeffh To change the template for this generated type comment go to
 *      Window>Preferences>Java>Code Generation>Code and Comments
 */
public class PingManager extends SelectionKeyHandler {

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
   * The socket pastry noed
   */
  private SocketPastryNode spn;

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

  /**
   * the ping throttle, or how often to actually ping a remote node
   */
  public static int PING_THROTTLE = 600000;

  /**
   * when to declare a ping lost
   */
  public static int PING_TIMEOUT = 30000;
  
  /**
   * @param port DESCRIBE THE PARAMETER
   * @param manager DESCRIBE THE PARAMETER
   * @param pool DESCRIBE THE PARAMETER
   */
  public PingManager(int port, SocketNodeHandlePool pool, SocketPastryNode spn, InetSocketAddress localAddress) {
    this.port = port;
    this.pool = pool;
    this.spn = spn;
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
      channel.socket().bind(localAddress);
      channel.socket().setSendBufferSize(DATAGRAM_SEND_BUFFER_SIZE);
      channel.socket().setReceiveBufferSize(DATAGRAM_RECEIVE_BUFFER_SIZE);

      key = SelectorManager.getSelectorManager().register(channel, this, SelectionKey.OP_READ);
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
   * Resets the last pinged time, only should be called when a node is marked 
   * dead.
   *
   * @param address The address to reset
   */
  protected void resetLastTimePinged(InetSocketAddress address) {
    pingtimes.remove(address);
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
    
    SelectorManager.getSelectorManager().modifyKey(key);
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
          
          if (spn != null)
            spn.broadcastSentListeners(write.message, write.address, buf.limit());
          
          System.out.println("COUNT: " + System.currentTimeMillis() + " Sent message " + write.message.getClass() + " of size " + buf.limit()  + " to " + write.address);

          int num = channel.send(buf, write.address);
          i.remove();

          if (num == 0) {
            System.out.println("ERROR: 0 bytes were written (not fatal, but bad) - full buffer.");
          }
          //          debug("Wrote message " + write.getObject() + " to " + write.getDestination());
        }
      }
    } catch (IOException e) {
      System.err.println("ERROR (datagrammanager:write): " + e);
    } finally {
      if (pendingMsgs.isEmpty()) {
        key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
      }
    }
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
    try {
      InetSocketAddress address = null;

      while ((address = (InetSocketAddress) channel.receive(buffer)) != null) {

        //debug("Received data from address " + address);
        buffer.flip();

        if (buffer.remaining() > 0) {
          int size = buffer.remaining();
          Object o = deserialize(buffer);
//              System.out.println("REC:"+o);
          
          if (spn != null)
            spn.broadcastReceivedListeners(o, address, size);
          
          System.out.println("COUNT: " + System.currentTimeMillis() + " Read message " + o.getClass() + " of size " + size + " from " + address);
          
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
      oos.close();
      
      ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
      DataOutputStream dos = new DataOutputStream(baos2);
      
      dos.write(SocketChannelWriter.MAGIC_NUMBER);
      dos.flush();
      dos.write(baos.toByteArray());
      dos.flush();

      return ByteBuffer.wrap(baos2.toByteArray());
    } catch (InvalidClassException e) {
      System.out.println("PANIC: Object to be serialized was an invalid class!");
      throw new IOException("Invalid class during attempt to serialize.");
    } catch (NotSerializableException e) {
      System.out.println("PANIC: Object to be serialized was not serializable! [" + o + "]");
      throw new IOException("Unserializable class " + o + " during attempt to serialize.");
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
    byte[] magic = new byte[SocketChannelWriter.MAGIC_NUMBER.length];
    buffer.get(magic);
    
    // copy the data into the buffer
    byte[] array = new byte[buffer.remaining()];
    buffer.get(array);
    buffer.clear();

    if (! Arrays.equals(SocketChannelWriter.MAGIC_NUMBER, magic)) {
      System.out.println("WARNING: Received unrecognized message header - ignoring.");
      throw new IOException("Improper message header received - ignoring. Read " + ((byte) magic[0]) + " " + ((byte) magic[1]) + " " + ((byte) magic[2]) + " " + ((byte) magic[3]));
    }
    
    ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(array));

    try {
      Object o = ois.readObject();

      return o;
    } catch (ClassNotFoundException e) {
      System.out.println("PANIC: Unknown class type in serialized message!");
      throw new IOException("Unknown class type in message - closing channel.");
    } catch (InvalidClassException e) {
      System.out.println("PANIC: Serialized message was an invalid class!");
      throw new IOException("Invalid class in message - closing channel.");
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
