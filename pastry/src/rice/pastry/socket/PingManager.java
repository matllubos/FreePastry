/*
 *  Created on Mar 10, 2004
 */
package rice.pastry.socket;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;

import rice.pastry.socket.messaging.PingMessage;
import rice.pastry.socket.messaging.PingResponseMessage;
import rice.pastry.wire.WireNodeHandle;
import rice.pastry.wire.exception.DeserializationException;
import rice.pastry.wire.exception.ImproperlyFormattedMessageException;
import rice.pastry.wire.exception.SerializationException;

/**
 * 
 * This manages pinging remote nodes.
 * 
 * What is the difference between ping() and forcePing()?
 * 
 * ping is only allowed to ping every PING_THROTTLE millis (default 10 seconds)
 * forcePing will ping right away.
 * 
 * in ping() PingResponseListener.pingResponse() is called immeadiately if 
 * we are still under the PING_THROTTLE time limit, and it will be called with 
 * slightly "stale" cached values.
 * 
 * in forcePing() we will never call PRL.pingResponse() with cached values, but
 * the values may be "faster" than expected becasue there may have already been a 
 * ping issued, and PRL.pingResponse() will be called with the "sooner" ping.  
 * 
 * The important thing to remember is to never calculate your own "proximity"
 * metrics, becasue there could be delay for sending, and you may get a ping
 * response that is a response to a ping sent out previously.
 * 
 * forcePing() is not exposed outside the package so nobody will be able to call 
 * forcePing() except ConnectionManager which has its own guarantees to never call 
 * forcePing if it has an existing ping in flight (unless there is a timeout).
 * 
 * in contrast, ping() is exposed through the SocketNodeHandle, but we keep it 
 * from sending too many udp messages ith PING_THOTTLE, so user code can call
 * that all day without concern about flooding the network.
 * 
 * Synchronization:
 *   EVERYTHING is called on the SelectorThread.
 * ping() may be called on a different thread, but is "invoked" on the SelectorThread.  
 * 
 * @author Jeff Hoye, Alan Mislove
 */
public class PingManager implements SelectionKeyHandler {

  private InetSocketAddress returnAddress;

	// ********************** User Contorl "Tweak" Fields ****************
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
   * The amount of time (in millis) to wait before sending another ping.
   */
  public static int PING_THROTTLE = 10000;


  // ************* Fields to handle notification/recording *******
  /**
   * InetSocketAddress -> ArrayList of PingResponseListener
   */
  protected Hashtable pingListeners = new Hashtable();

  /**
   * The "queue" of pings/pingResponses
   */
  protected ArrayList pendingMsgs;

  /**
   * the list of the last time a ping was sent
   * address -> Long
   */
  private Hashtable pingSentTimes;

  /**
   * the list of the last time a ping was received
   * address -> Long
   */
  private Hashtable pingResponseTimes;

  /**
   * the list of cached proximities
   * address -> Integer
   * 
   */
  private Hashtable proximity;


  // ********************** Technical fields ********************
  /**
   * the port number to receive pings on
   */
  private int port;

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


  // ***************** Reference fields ****************
  /**
   * the node handle pool on this node
   * used to keep proximity info up to date.
   */
  private SocketNodeHandlePool pool;

  /**
   * the selector manager to manage the worker thread
   */
  private SelectorManager manager;

  /**
   * Constructor for PingManager.
   * 
   * @param port The port to listen on
   * @param manager ref to SelectorManager
   * @param pool ref to SocketNodeHandlePool
   */
  public PingManager(int port, SelectorManager manager, SocketNodeHandlePool pool, InetSocketAddress proxyAddress) throws BindException {
    this.port = port;
    this.manager = manager;
    this.pool = pool;
    proximity = new Hashtable();
    pingSentTimes = new Hashtable();
    pingResponseTimes = new Hashtable();
    this.returnAddress = proxyAddress;
    pendingMsgs = new ArrayList();
    // allocate enought bytes to read a node handle
    buffer = ByteBuffer.allocateDirect(DATAGRAM_SEND_BUFFER_SIZE);

    try {
      // bind to the appropriate port
      channel = DatagramChannel.open();
      channel.configureBlocking(false);
      InetSocketAddress isa = new InetSocketAddress(port);
      if (returnAddress == null) {
        returnAddress = new InetSocketAddress(InetAddress.getLocalHost(),port);
      }
      channel.socket().bind(isa); // throws BindException
      channel.socket().setSendBufferSize(DATAGRAM_SEND_BUFFER_SIZE);
      channel.socket().setReceiveBufferSize(DATAGRAM_RECEIVE_BUFFER_SIZE);

      Selector selector = manager.getSelector();

      synchronized (selector) {
        key = channel.register(selector, SelectionKey.OP_READ);
      }

      key.attach(this);
    } catch (BindException be) {
      kill();
      throw be;  
    } catch (IOException e) {
      System.out.println("PANIC: Error binding datagram server to port " + port + ": " + e);
      System.exit(-1);
    }
  }

  /**
   * Method which returns the last cached proximity value for the given address.
   * If there is no cached value, then DEFAULT_PROXIMITY is returned.
   *
   * @param address The address to return the value for
   * @return RTT proximity in millis
   */
  public int proximity(InetSocketAddress address) {
    Integer i = (Integer) proximity.get(address);

    if (i == null) {
      return SocketNodeHandle.DEFAULT_PROXIMITY;
    }

    return i.intValue();
  }



  /**
   * Gets the last time we sent a ping
   *
   * @param address the address we last pinged
   * @return The LastTimePinged value
   */
  public long getLastTimePinged(InetSocketAddress address) {
    return ((Long) pingSentTimes.get(address)).longValue();
  }
  
  /**
   * Gets the last time we heard a PingResponse from the 
   * message.
   *
   * @param address the address in question
   * @return the last time we heard from that address
   */
  public long getLastTimePingReceived(InetSocketAddress address) {
      Long l = (Long) pingResponseTimes.get(address);
      if (l != null) {
        return l.longValue();
      } else {
        return 0;
      }
  }


  // *********** Lifecycle of a ping *********************
  /**
   * Method which initiates a ping to the remote node. Once the ping is
   * complete, the result will be available via the proximity() call.  
   * 
   * Note that the ping will not actually be issued if we already heard
   * a ping within the PING_THROTTLE.
   * 
   * The PingResponseListener may receive cached information, if the ping
   * was issued more recently than PING_THROTTLE time ago.
   *
   * @param address The address to ping
   * @param prl DESCRIBE THE PARAMETER
   */
  protected void ping(InetSocketAddress address, PingResponseListener prl) {
    ping(address, prl, false);
  }

  /**
   * ForcePing means to bypass the PING_THROTTLE minimum time between pings.
   * The prl will be notified when the first PingResponse comes back from the 
   * speceived address.  Note that this may be a different instance of a 
   * ping/response than before, and due to queueing delays, the proximity
   * should _not_ be measured as the difference between the times forcePing
   * and prl.pingResponse() are called.  Always use getProximity() for the most
   * accurate proximity information.
   * 
   * Note that forcePing() should not be exposed to the user code, as it 
   * sends a ping every time it is called, and could flood the network if 
   * not used properly.
   * 
   * As of the writing, it is only used by ConnectionManager to determine
   * liveness quickly.
   *
   * @param address the address to ping
   * @param prl the PRL to notify when the response arrives
   */
  protected void forcePing(InetSocketAddress address, PingResponseListener prl) {
    ping(address, prl, true);
  }


  /**
   * Helper method for ping()/forcePing()
   *
   * @param address the address to ping
   * @param prl the response listener to notify
   * @param force bypass the PING_THROTTLE rule.
   */
  private void ping(final InetSocketAddress address, final PingResponseListener prl, final boolean force) {
    manager.invoke(new Runnable() {
			public void run() {
        long curTime = System.currentTimeMillis();
        if (force ||
          (pingSentTimes.get(address) == null) ||
          (curTime - getLastTimePinged(address) > PING_THROTTLE)) {
          pingSentTimes.put(address, new Long(curTime));
          addPingResponseListener(address, prl);
          enqueue(address, new PingMessage(returnAddress));        
        } else {
          if (getLastTimePingReceived(address) >= getLastTimePinged(address)) {
            // we just pinged them, and got a response
            if (prl != null) {
              prl.pingResponse(address, proximity(address), getLastTimePingReceived(address));
            }
          } else {
            // we are still waiting to hear from someone
            addPingResponseListener(address, prl);
          }
        }
			}
		});
  }

  /**
   * Queues the message into pendingMsgs, and registers 
   * the key for writing
   *
   * @param address The address to send to
   * @param msg the message to send to the address
   */
  private void enqueue(InetSocketAddress address, Object msg) {
    pendingMsgs.add(new PendingWrite(address, msg));
    manager.modifyKey(key);
  }

  /**
   * Register's PingManager for writing
   *
   * @param key DESCRIBE THE PARAMETER
   */
  public void modifyKey(SelectionKey key) {
    if (!pendingMsgs.isEmpty()) {
      key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
    }
  }

  /**
   * Called on SelectorThread to write, uses serialize() as a helper.
   *
   * @param key the key that was called on.
   */
  public boolean write(SelectionKey key) {
    try {
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
    } catch (IOException e) {
      System.err.println("ERROR (PingManager:write): " + e);      
    }
    return true;
  }

	  /**
	   * Called on the selector thread to read.  Keep reading until 
     * the buffer is empty.  Uses deserialize() as a helper.
	   *
	   * @param key the key that was selected
	   */
	  public boolean read(SelectionKey key) {
	
	    WireNodeHandle handle = null;
	
	    try {
	      InetSocketAddress address = null;
	
	      while ((address = (InetSocketAddress) channel.receive(buffer)) != null) {
	
	        //debug("Received data from address " + address);
	        buffer.flip();
	
	        if (buffer.remaining() > 0) {
	          Object o = deserialize(buffer);
	//              System.out.println("REC:"+o);
	          receiveMessage(o);
	        } else {
	//              debug("Read from datagram channel, but no bytes were there - no bad, but wierd.");
	          buffer.clear();
	//              System.out.println("Read from datagram channel, but no bytes were there - no bad, but wierd.");
	          break;
	        }
	      }
	    } catch (IOException e) {
	      System.out.println("ERROR (PingManager:read): " + e);
	//          debug("ERROR (datagrammanager:read): " + e);
	      e.printStackTrace();
	    }
      return true;
	  }

  /**
   * Called by read when we got a full message from the wire.
   * Handles sending a PingResponseMessage in response to a PingMessage
   * Handles calling pingResponse and updating proximities based on 
   * reception of a PingResponseMessage
   *
   * @param message DESCRIBE THE PARAMETER
   * @param address DESCRIBE THE PARAMETER
   */
  public void receiveMessage(Object message) {
    //System.out.println("PingMgr.receiveMessage("+message.getClass().getName()+")");
    if (message instanceof PingMessage) {
      PingMessage pm = (PingMessage) message;
      enqueue(pm.returnAddress, new PingResponseMessage(pm.getStartTime(), returnAddress));
    }
    if (message instanceof PingResponseMessage) {
      PingResponseMessage prm = (PingResponseMessage) message;
      long curTime = System.currentTimeMillis();
      long startTime = prm.getStartTime();
      int time = (int) (curTime - startTime);

      if ((proximity.get(prm.returnAddress) == null) || (((Integer) proximity.get(prm.returnAddress)).intValue() > time)) {
        proximity.put(prm.returnAddress, new Integer(time));
        pool.update(prm.returnAddress, SocketNodeHandle.PROXIMITY_CHANGED);
      }
      pingResponse(prm.returnAddress, curTime);
    }
  }


  /**
   * Calls notifyPingResponseListeners, and sets pingResponseTimes.
   *
   * @param address DESCRIBE THE PARAMETER
   * @param curTime DESCRIBE THE PARAMETER
   */
  public void pingResponse(InetSocketAddress address, long curTime) {
    pingResponseTimes.put(address, new Long(curTime));
    notifyPingResponseListeners(address, proximity(address), curTime);
  }

  /**
   * Calls PingResponseListener.pingResponse()
   * 
   *  called by PingManager.pingResponse()
   *
   * @param address
   * @param proximity
   * @param lastTimePinged
   */
  protected void notifyPingResponseListeners(InetSocketAddress address, int proximity, long lastTimeHeardFrom) {
    ArrayList list = (ArrayList) pingListeners.get(address);
    if (list != null) {
      Iterator i = list.iterator();
      while (i.hasNext()) {
        PingResponseListener prl = (PingResponseListener) i.next();
        prl.pingResponse(address, proximity, lastTimeHeardFrom);
        i.remove();
      }
    }
  }

  /**
   * Addes a pingResponseListener for the particular address.  pingResponse() will
   * be called next time we receive a ping from the node.  The prl will be removed
   * from pingListeners after it is notified of a response.
   *
   * @param address The address waiting to hear from
   * @param prl The feature to be added to the PingResponseListener attribute
   */
  private void addPingResponseListener(InetSocketAddress address, PingResponseListener prl) {
    if (prl == null) {
      return;
    }
    ArrayList list = (ArrayList) pingListeners.get(address);
    if (list == null) {
      list = new ArrayList();
      pingListeners.put(address, list);
    }
    list.add(prl);
  }

  /**
   * Class to hold the message and address while in the pendingMsgs queue.
   *
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

  // *************** Serialization Logic ***********************

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


  // ****************** Additional (Unused) Key Handler Attributes **********************
  /**
   * Not used, an implementation of SelectionKeyHandler
   *
   * @param key 
   */
  public boolean accept(SelectionKey key) {
    throw new RuntimeException("accept() should not be called on PingManager");
  }

  /**
   * Not used, an implementation of SelectionKeyHandler
   *
   * @param key 
   */
  public boolean connect(SelectionKey key) {
    throw new RuntimeException("connect() should not be called on PingManager");
  }

	public void kill() {
    try {
      Selector selector = manager.getSelector();

      synchronized (selector) {
        channel.close();
        key.cancel();
      }
    } catch (IOException ioe) {
      ioe.printStackTrace();
    }
	}
}
