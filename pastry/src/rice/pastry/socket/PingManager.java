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
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.WeakHashMap;

import rice.pastry.PastryObjectInputStream;
import rice.pastry.churn.FailedSetManager;
import rice.pastry.churn.NullFailedSetManager;
import rice.pastry.socket.exception.DeserializationException;
import rice.pastry.socket.exception.ImproperlyFormattedMessageException;
import rice.pastry.socket.exception.SerializationException;
import rice.pastry.socket.messaging.PingMessage;
import rice.pastry.socket.messaging.PingResponseMessage;
import rice.selector.SelectionKeyHandler;
import rice.selector.SelectorManager;

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
 * PingManager guarantees that Ping Responses have a valid Ping Sent time to help prevent
 * forging of proximity().  See pingSentTimes for the record of sent pings.
 * 
 * @author Jeff Hoye, Alan Mislove
 */
public class PingManager extends SelectionKeyHandler {

  /**
   * Link to myself for verification purposes.
   */
  private SocketNodeHandle localHandle;

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

  /**
   * The number of sent pings that we remember for each connection.  This is security so nobody can alter their proximity by sending a newer message.
   */
  private int NUM_PINGS_TO_REMEMBER = 15;


  // ************* Fields to handle notification/recording *******
  /**
   * SocketNodeHandle -> ArrayList of PingResponseListener
   */
  protected WeakHashMap pingListeners = new WeakHashMap();

  /**
   * The "queue" of pings/pingResponses
   */
  protected ArrayList pendingMsgs;

  /**
   * the list of the last time a ping was sent
   * SocketNodeHandle -> LinkedList of Long
   */
  private WeakHashMap pingSentTimes;

  /**
   * the list of the last time a ping was received
   * SocketNodeHandle -> Long
   */
  private WeakHashMap pingResponseTimes;

  /**
   * the list of cached proximities
   * SocketNodeHandle -> Integer
   * 
   */
  private WeakHashMap proximity;


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
   * Pointer to the node I'm servicing.
   */
  private SocketPastryNode spn;

  private FailedSetManager fsm;

  /**
   * Constructor for PingManager.
   * 
   * @param port The port to listen on
   * @param manager ref to SelectorManager
   * @param pool ref to SocketNodeHandlePool
   */
  public PingManager(int port, SocketNodeHandlePool pool, SocketNodeHandle localHandle, SocketPastryNode spn) throws BindException {
    this.spn = spn;
    this.port = port;
    this.pool = pool;
    this.localHandle = localHandle;
    this.fsm = new NullFailedSetManager();
    proximity = new WeakHashMap();
    pingSentTimes = new WeakHashMap();
    pingResponseTimes = new WeakHashMap();
    pendingMsgs = new ArrayList();
    // allocate enought bytes to read a node handle
    buffer = ByteBuffer.allocateDirect(DATAGRAM_SEND_BUFFER_SIZE);
    

    try {
      // bind to the appropriate port
      channel = DatagramChannel.open();
      channel.configureBlocking(false);
      InetSocketAddress isa = new InetSocketAddress(port);
      channel.socket().bind(isa); // throws BindException
      channel.socket().setSendBufferSize(DATAGRAM_SEND_BUFFER_SIZE);
      channel.socket().setReceiveBufferSize(DATAGRAM_RECEIVE_BUFFER_SIZE);
      
      SelectorManager.getSelectorManager().invoke(new Runnable() {
				public void run() {
          try {
            key = SelectorManager.getSelectorManager().register(channel,PingManager.this,SelectionKey.OP_READ);
            key.attach(PingManager.this);
          } catch (IOException e) {
            System.out.println("ERROR creating server socket key " + e);
          }
				}
			});

    } catch (BindException be) {
      kill();
      throw be;  
    } catch (IOException e) {
      System.out.println("PANIC: Error binding datagram server to port " + port + ": " + e);
      System.exit(-1);
    }
  }

  /**
   * @param fsm
   */
  public void setFailedSetManager(FailedSetManager fsm) {
    this.fsm = fsm;   
  }

  /**
   * Method which returns the last cached proximity value for the given address.
   * If there is no cached value, then DEFAULT_PROXIMITY is returned.
   *
   * @param address The address to return the value for
   * @return RTT proximity in millis
   */
  public int proximity(SocketNodeHandle snh) {
    Integer i = (Integer) proximity.get(snh);

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
  public long getLastTimePinged(SocketNodeHandle snh) {
    LinkedList ll = (LinkedList)pingSentTimes.get(snh);
    Long l = (Long)ll.getLast();
    return l.longValue();
  }
  
  /**
   * Gets the last time we heard a PingResponse from the 
   * message.
   *
   * @param address the address in question
   * @return the last time we heard from that address
   */
  public long getLastTimePingReceived(SocketNodeHandle snh) {
      Long l = (Long) pingResponseTimes.get(snh);
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
  protected void ping(SocketNodeHandle snh, PingResponseListener prl) {
    ping(snh, prl, false);
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
  protected void forcePing(SocketNodeHandle snh, PingResponseListener prl) {
    ping(snh, prl, true);
  }


  /**
   * Helper method for ping()/forcePing()
   *
   * @param address the address to ping
   * @param prl the response listener to notify
   * @param force bypass the PING_THROTTLE rule.
   */
  private void ping(final SocketNodeHandle snh, final PingResponseListener prl, final boolean force) {
    if (snh.equals(localHandle)) {
//      Thread.dumpStack();
      return;
    }
    Runnable r = new Runnable() {
			public void run() {
        long curTime = System.currentTimeMillis();
        LinkedList pstList = (LinkedList)pingSentTimes.get(snh);
        if (force ||
          (pstList == null) ||
          (pstList.size() == 0) ||
          (curTime - ((Long)pstList.getLast()).longValue() > PING_THROTTLE)) {
          
          LinkedList ll = pstList; //pingSentTimes.get(snh);
          if(ll == null) {
            ll = new LinkedList();
            pingSentTimes.put(snh, ll);
          }
          ll.addLast(new Long(curTime));
          while (ll.size() > NUM_PINGS_TO_REMEMBER) {
            ll.removeFirst();
          }
          addPingResponseListener(snh, prl);
          enqueue(snh, new PingMessage(localHandle, snh, curTime, spn.getLeafSet(), fsm.getFailedSet()));        
        } else {
          if (getLastTimePingReceived(snh) >= getLastTimePinged(snh)) {
            // we just pinged them, and got a response
            if (prl != null) {
              prl.pingResponse(snh, proximity(snh), getLastTimePingReceived(snh));
            }
          } else {
            // we are still waiting to hear from someone
            addPingResponseListener(snh, prl);
          }
        }
			}
		};
    SelectorManager.getSelectorManager().invoke(r);      
  }

  /**
   * Queues the message into pendingMsgs, and registers 
   * the key for writing
   *
   * @param address The address to send to
   * @param msg the message to send to the address
   */
  private void enqueue(SocketNodeHandle snh, Object msg) {
    if (!spn.isAlive()) return;
    pendingMsgs.add(new PendingWrite(snh, msg));
    enableWriteOpIfNecessary(key);
  }

  /**
   * Register's PingManager for writing
   *
   * @param key DESCRIBE THE PARAMETER
   */
  private void enableWriteOpIfNecessary(SelectionKey key) {
    if (!pendingMsgs.isEmpty()) {
      key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
    }
  }

  /**
   * Called on SelectorThread to write, uses serialize() as a helper.
   *
   * @param key the key that was called on.
   */
  public void write(SelectionKey key) {
    try {
      // last, write all pending datagrams
      Iterator i = pendingMsgs.iterator();
      //i = transmissionManager.getReady();

      while (i.hasNext()) {
        PendingWrite write = (PendingWrite) i.next();
        ByteBuffer buf = serialize(write.message);
        int num = channel.send(buf, write.snh.getAddress());
        spn.broadcastSentListeners(write.message, write.snh.getAddress(), num);
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
  }

	  /**
	   * Called on the selector thread to read.  Keep reading until 
     * the buffer is empty.  Uses deserialize() as a helper.
	   *
	   * @param key the key that was selected
	   */
	  public void read(SelectionKey key) {
	
	    try {
	      InetSocketAddress address = null;
	
	      while ((address = (InetSocketAddress) channel.receive(buffer)) != null) {
          int len = buffer.limit();
          
	        //debug("Received data from address " + address);
	        buffer.flip();
	
	        if (buffer.remaining() > 0) {
	          Object o = deserialize(buffer);
            //              System.out.println("REC:"+o);
            spn.broadcastReceivedListeners(o, address, len);
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
      if ((pm.receiver == null) || (pm.receiver.equals(localHandle))) {
        enqueue(pm.sender, new PingResponseMessage(pm.getStartTime(), localHandle, pm.sender, spn.getLeafSet(), fsm.getFailedSet()));
      } else {
//        System.out.println(this+" ignoring ping message with incorrect receiver");
      }
    }
    if (message instanceof PingResponseMessage) {
      PingResponseMessage prm = (PingResponseMessage) message;
      long curTime = System.currentTimeMillis();
      long startTime = prm.getStartTime();
      int time = (int) (curTime - startTime);
      LinkedList ll = (LinkedList)pingSentTimes.get(prm.sender);
      if (ll == null) {
        System.out.println("WARNING: "+this+" received unexpected ping response from "+prm.sender+" with unexpected starttime "+startTime);
        return;
      }

      if (!ll.contains(new Long(startTime))) {
        System.out.println("WARNING: "+this+" received ping response from "+prm.sender+" with unexpected starttime "+startTime);
        return;
      }

      if ((proximity.get(prm.sender) == null) || (((Integer) proximity.get(prm.sender)).intValue() > time)) {
        proximity.put(prm.sender, new Integer(time));
        pool.update(prm.sender, SocketNodeHandle.PROXIMITY_CHANGED);
      }
      pingResponse(prm.sender, curTime, startTime);
    }
  }


  /**
   * Calls notifyPingResponseListeners, and sets pingResponseTimes.
   *
   * @param address DESCRIBE THE PARAMETER
   * @param curTime DESCRIBE THE PARAMETER
   */
  public void pingResponse(SocketNodeHandle snh, long curTime, long timeSentPing) {
    pingResponseTimes.put(snh, new Long(curTime));
    notifyPingResponseListeners(snh, proximity(snh), curTime);
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
  protected void notifyPingResponseListeners(SocketNodeHandle snh, int proximity, long lastTimeHeardFrom) {
    ArrayList list = (ArrayList) pingListeners.get(snh);
    if (list != null) {
      Iterator i = list.iterator();
      while (i.hasNext()) {
        PingResponseListener prl = (PingResponseListener) i.next();
        prl.pingResponse(snh, proximity, lastTimeHeardFrom);
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
  private void addPingResponseListener(SocketNodeHandle snh, PingResponseListener prl) {
    if (prl == null) {
      return;
    }
    ArrayList list = (ArrayList) pingListeners.get(snh);
    if (list == null) {
      list = new ArrayList();
      pingListeners.put(snh, list);
    }
    list.add(prl);
  }

  /**
   * Class to hold the message and address while in the pendingMsgs queue.
   *
   */
  public class PendingWrite {
    SocketNodeHandle snh;
    Object message;

    /**
     * Constructor for PendingWrite.
     *
     * @param adr DESCRIBE THE PARAMETER
     * @param m DESCRIBE THE PARAMETER
     */
    public PendingWrite(SocketNodeHandle snh, Object m) {
      this.snh = snh;
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
  public Object deserialize(ByteBuffer buffer) throws IOException {
    int len = buffer.remaining();
    byte[] array = new byte[len];

    // copy the data into the buffer
    buffer.get(array);
    buffer.clear();

    ObjectInputStream ois = new PastryObjectInputStream(new ByteArrayInputStream(array),spn);

    try {
      Object o = ois.readObject();

      return o;
    } catch (ClassNotFoundException e) {
      System.out.println("PANIC: Unknown class type in serialized message!");
      throw new ImproperlyFormattedMessageException("Unknown class type in message - closing channel.");
    } catch (InvalidClassException e) {
      System.out.println("PANIC(socket.PingManager): Serialized message was an invalid class!"+e.getMessage()+":"+e.classname);
      throw new DeserializationException("Invalid class in message - closing channel.");
    }
  }


  // ****************** Additional (Unused) Key Handler Attributes **********************
	public void kill() {
    SelectorManager.getSelectorManager().invoke(new Runnable() {
			public void run() {
        try {
          channel.close();
          if (key != null) 
            key.cancel();
        } catch (IOException ioe) {
          ioe.printStackTrace();
        }
      }
		});
	}
  
  public String toString() {
    return "PingManger for "+localHandle;
  }

}
