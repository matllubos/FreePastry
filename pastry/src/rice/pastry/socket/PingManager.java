/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
  contributors may be  used to endorse or promote  products derived from
  this software without specific prior written permission.

  This software is provided by RICE and the contributors on an "as is"
  basis, without any representations or warranties of any kind, express
  or implied including, but not limited to, representations or
  warranties of non-infringement, merchantability or fitness for a
  particular purpose. In no event shall RICE or contributors be liable
  for any direct, indirect, incidental, special, exemplary, or
  consequential damages (including, but not limited to, procurement of
  substitute goods or services; loss of use, data, or profits; or
  business interruption) however caused and on any theory of liability,
  whether in contract, strict liability, or tort (including negligence
  or otherwise) arising in any way out of the use of this software, even
  if advised of the possibility of such damage.

********************************************************************************/
package rice.pastry.socket;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.socket.messaging.*;
import rice.selector.*;

/**
 * @version $Id$
 * @author jeffh To change the template for this generated type comment go to
 *      Window>Preferences>Java>Code Generation>Code and Comments
 */
public class PingManager extends SelectionKeyHandler {
  
  // the header which signifies a normal socket
  protected static byte[] HEADER_PING = new byte[] {0x49, 0x3A, 0x09, 0x5C};
  
  // the length of the ping header
  public static int HEADER_SIZE = HEADER_PING.length;

  // the size of the buffer used to read incoming datagrams must be big enough
  // to encompass multiple datagram packets
  public static int DATAGRAM_RECEIVE_BUFFER_SIZE = 131072;
  
  // the size of the buffer used to send outgoing datagrams this is also the
  // largest message size than can be sent via UDP
  public static int DATAGRAM_SEND_BUFFER_SIZE = 65536;
  
  // the ping throttle, or how often to actually ping a remote node
  public static int PING_THROTTLE = 600000;

  // InetSocketAddress -> ArrayList of PingResponseListener
  protected Hashtable pingListeners = new Hashtable();

  // The list of pending meesages
  protected ArrayList pendingMsgs;

  // the list of the last time a ping was sent
  private Hashtable pingtimes;

  // the list of response times
  private Hashtable pingResponseTimes;

  // the list of cached ping values
  private Hashtable pings;
  
  // the local pastry node
  private PastryNode spn;

  // the buffer used for writing datagrams
  private ByteBuffer buffer;

  // the channel used from talking to the network
  private DatagramChannel channel;

  // the key used to determine what has taken place
  private SelectionKey key;

  // the node handle pool on this node
  private SocketNodeHandlePool pool;
  
  // the source route manager
  private SocketSourceRouteManager manager;
  
  // the local address of this node
  private EpochInetSocketAddress localAddress;
  
  /**
   * @param port DESCRIBE THE PARAMETER
   * @param manager DESCRIBE THE PARAMETER
   * @param pool DESCRIBE THE PARAMETER
   */
  public PingManager(SocketNodeHandlePool pool, SocketSourceRouteManager manager, PastryNode spn, EpochInetSocketAddress bindAddress, EpochInetSocketAddress proxyAddress) {
    this.pool = pool;
    this.spn = spn;
    this.manager = manager;
    this.pings = new Hashtable();
    this.pingtimes = new Hashtable();
    this.pingResponseTimes = new Hashtable();
    this.pendingMsgs = new ArrayList();
    this.localAddress = proxyAddress;
    
    // allocate enought bytes to read a node handle
    this.buffer = ByteBuffer.allocateDirect(DATAGRAM_SEND_BUFFER_SIZE);

    try {
      // bind to the appropriate port
      channel = DatagramChannel.open();
      channel.configureBlocking(false);
      channel.socket().bind(bindAddress.getAddress());
      channel.socket().setSendBufferSize(DATAGRAM_SEND_BUFFER_SIZE);
      channel.socket().setReceiveBufferSize(DATAGRAM_RECEIVE_BUFFER_SIZE);

      key = SelectorManager.getSelectorManager().register(channel, this, SelectionKey.OP_READ);
    } catch (IOException e) {
      System.out.println("PANIC: Error binding datagram server to address " + localAddress + ": " + e);
    }
  }
  
  /**
    * Makes this node resign from the network.  Is designed to be used for
   * debugging and testing.
   */
  public void resign() throws IOException {
    key.channel().close();
    key.cancel();
  }

  /**
   * Gets the LastTimePinged attribute of the PingManager object
   *
   * @param address DESCRIBE THE PARAMETER
   * @return The LastTimePinged value
   */
  public long getLastTimePinged(SourceRoute path) {
    return ((Long) pingtimes.get(path)).longValue();
  }
  
  /**
   * Resets the last pinged time, only should be called when a node is marked 
   * dead.
   *
   * @param address The address to reset
   */
  protected void resetLastTimePinged(SourceRoute path) {
    pingtimes.remove(path);
  }

  /**
   * Gets the LastTimeHeardFrom attribute of the PingManager object
   *
   * @param address DESCRIBE THE PARAMETER
   * @return The LastTimeHeardFrom value
   */
  public long getLastTimeHeardFrom(SourceRoute path) {
    Long l = (Long) pingResponseTimes.get(path);
    
    if (l != null) {
      return l.longValue();
    } else {
      return 0;
    }
  }

  /**
   * Method which returns the last cached proximity value for the given address.
   * If there is no cached value, then DEFAULT_PROXIMITY is returned.
   *
   * @param address The address to return the value for
   * @return DESCRIBE THE RETURN VALUE
   */
  public int proximity(SourceRoute path) {
    Integer i = (Integer) pings.get(path);

    if (i == null) 
      return SocketNodeHandle.DEFAULT_PROXIMITY;

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
  public void addPingResponseListener(SourceRoute path, PingResponseListener prl) {
    if (prl == null) {
      return;
    }
    
    synchronized (pingResponseTimes) {
      ArrayList list = (ArrayList) pingListeners.get(path);
      
      if (list == null) {
        list = new ArrayList();
        pingListeners.put(path, list);
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
  public void enqueue(SourceRoute path, Object msg) {
    try {
      byte[] data = addHeader(path, msg, localAddress);
      
      synchronized (pendingMsgs) {
        pendingMsgs.add(new Envelope(path.getFirstHop(), data));
      }
      
      if ((spn != null) && (spn instanceof SocketPastryNode))
        ((SocketPastryNode) spn).broadcastSentListeners(msg, path.toArray(), data.length);
      
      if (SocketPastryNode.verbose) System.out.println("COUNT: " + System.currentTimeMillis() + " Sent message " + msg.getClass() + " of size " + data.length  + " to " + path);    
      
      SelectorManager.getSelectorManager().modifyKey(key);
    } catch (IOException e) {
      System.out.println("ERROR: Received exceptoin " + e + " while enqueuing ping " + msg);
    }
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param message DESCRIBE THE PARAMETER
   * @param address DESCRIBE THE PARAMETER
   */
  public void receiveMessage(Object message, int size, InetSocketAddress from) throws IOException {
    if (message instanceof DatagramMessage) {
      DatagramMessage dm = (DatagramMessage) message;      
      long start = dm.getStartTime();
      SourceRoute path = dm.getInboundPath();
      
      if (path == null)
        path = SourceRoute.build(new EpochInetSocketAddress(from));

      if ((spn != null) && (spn instanceof SocketPastryNode))
        ((SocketPastryNode) spn).broadcastReceivedListeners(dm, path.reverse().toArray(), size);
            
      if (dm instanceof PingMessage) {
        if (SocketPastryNode.verbose) System.out.println("COUNT: " + System.currentTimeMillis() + " Read message " + message.getClass() + " of size " + size + " from " + dm.getInboundPath().reverse());      

        enqueue(dm.getInboundPath(), new PingResponseMessage(dm.getOutboundPath(), dm.getInboundPath(), start));        
      } else if (dm instanceof PingResponseMessage) {
        if (SocketPastryNode.verbose) System.out.println("COUNT: " + System.currentTimeMillis() + " Read message " + message.getClass() + " of size " + size + " from " + dm.getOutboundPath().reverse());      

        long curTime = System.currentTimeMillis();
        int time = (int) (curTime - start);
        
        if ((pings.get(dm.getOutboundPath()) == null) || (((Integer) pings.get(dm.getOutboundPath())).intValue() > time)) {
          pings.put(dm.getOutboundPath(), new Integer(time));
          pool.update(dm.getOutboundPath().getLastHop(), SocketNodeHandle.PROXIMITY_CHANGED);
        }
        
        pingResponse(dm.getOutboundPath(), curTime);
      } else if (dm instanceof WrongEpochMessage) {
        WrongEpochMessage wem = (WrongEpochMessage) dm;
        
        System.out.println("COUNT: " + System.currentTimeMillis() + " Read message " + message.getClass() + " of size " + size + " from " + dm.getOutboundPath().reverse());      
        System.out.println("----- INFO: Received wrong epoch update from " + wem.getCorrect() + " was " + wem.getIncorrect());      

        manager.markAlive(dm.getOutboundPath());
        manager.markDead(wem.getIncorrect());
      } else if (dm instanceof IPAddressRequestMessage) {
        System.out.println("COUNT: " + System.currentTimeMillis() + " Read message " + message.getClass() + " of size " + size + " from " + SourceRoute.build(new EpochInetSocketAddress(from)));      
        
        enqueue(SourceRoute.build(new EpochInetSocketAddress(from)), new IPAddressResponseMessage(from)); 
      } else {
        System.out.println("ERROR: Received unknown DatagramMessage " + dm);
      }
    }
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param address DESCRIBE THE PARAMETER
   * @param curTime DESCRIBE THE PARAMETER
   */
  public void pingResponse(SourceRoute path, long curTime) {
    synchronized (pingResponseTimes) {
      manager.markAlive(path);
      pingResponseTimes.put(path, new Long(curTime));
      notifyPingResponseListeners(path, proximity(path), curTime);
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
        Iterator i = pendingMsgs.iterator();

        while (i.hasNext()) {
          Envelope write = (Envelope) i.next();
          ByteBuffer buf = ByteBuffer.wrap(write.data);
          
          int num = channel.send(buf, write.destination.getAddress());
          i.remove();

      //    System.out.println("NOTE: Writing " + num + " bytes of data to address " + write.destination.getAddress());
          
          if (num == 0) 
            System.out.println("ERROR: 0 bytes were written (not fatal, but bad) - full buffer.");
        }
      }
    } catch (IOException e) {
      System.err.println("ERROR (datagrammanager:write): " + e);
    } finally {
      if (pendingMsgs.isEmpty()) 
        key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
    }
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param key DESCRIBE THE PARAMETER
   */
  public void modifyKey(SelectionKey key) {
    synchronized (pendingMsgs) {
      if (! pendingMsgs.isEmpty()) 
        key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
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
        buffer.flip();

        if (buffer.remaining() > 0) {
          readHeader(address);
        } else {
          debug("Read from datagram channel, but no bytes were there - no bad, but wierd.");
          break;
        }
      }
    } catch (IOException e) {
      System.out.println("ERROR (datagrammanager:read): " + e);
      e.printStackTrace();
    } finally {
      buffer.clear();
    }
  }


  /**
   * Method which initiates a ping to the remote node. Once the ping is
   * complete, the result will be available via the proximity() call.
   *
   * @param address The address to ping
   * @param prl DESCRIBE THE PARAMETER
   */
  public void ping(SourceRoute path, PingResponseListener prl) {
    ping(path, prl, false);
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param address DESCRIBE THE PARAMETER
   * @param prl DESCRIBE THE PARAMETER
   */
  protected void forcePing(SourceRoute path, PingResponseListener prl) {
    ping(path, prl, true);
  }

  /**
   * caller must synchronized(pingResponseTimes)
   *
   * @param address
   * @param proximity
   * @param lastTimePinged
   */
  protected void notifyPingResponseListeners(SourceRoute path, int proximity, long lastTimePinged) {
    ArrayList list = (ArrayList) pingListeners.get(path);
    
    if (list != null) {
      Iterator i = list.iterator();
      
      while (i.hasNext()) {
        ((PingResponseListener) i.next()).pingResponse(path, proximity, lastTimePinged);
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
  private void ping(SourceRoute path, PingResponseListener prl, boolean force) {
    synchronized (pingResponseTimes) {
      if (force ||
          (pingtimes.get(path) == null) ||
          (System.currentTimeMillis() - getLastTimePinged(path) > PING_THROTTLE)) {
        pingtimes.put(path, new Long(System.currentTimeMillis()));
        addPingResponseListener(path, prl);
        
        debug("Actually sending ping via path " + path + " local " + localAddress);
        
        enqueue(path, new PingMessage(path, path.reverse(localAddress)));
      } else {
        // we just pinged them, and got a response
        if (getLastTimeHeardFrom(path) >= getLastTimePinged(path)) {
          if (prl != null) 
            prl.pingResponse(path, proximity(path), getLastTimeHeardFrom(path));
        } else {
          // we are still waiting to hear from someone
          addPingResponseListener(path, prl);
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
  public static byte[] serialize(Object message) throws IOException {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);      
      oos.writeObject(message);
      oos.close();
      
      return baos.toByteArray();
    } catch (InvalidClassException e) {
      System.out.println("PANIC: Object to be serialized was an invalid class!");
      throw new IOException("Invalid class during attempt to serialize.");
    } catch (NotSerializableException e) {
      System.out.println("PANIC: Object to be serialized was not serializable! [" + message + "]");
      throw new IOException("Unserializable class " + message + " during attempt to serialize.");
    }
  }
  
  /**
   * Method which adds a header for the provided path to the given data.
   *
   * @return The messag with a header attached
   */
  public static byte[] addHeader(SourceRoute path, Object data, EpochInetSocketAddress localAddress) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);      

    dos.write(HEADER_PING);
    dos.write((byte) 1);
    dos.write((byte) path.getNumHops() + 1);
    dos.write(SocketChannelRepeater.encodeHeader(localAddress));
    
    for (int i=0; i<path.getNumHops(); i++) 
      dos.write(SocketChannelRepeater.encodeHeader(path.getHop(i)));

    dos.write(serialize(data));
    dos.flush();
  
    return baos.toByteArray();
  }
  
  /**
   * Method which processes an incoming message and hands it off to the appropriate
   * handler.
   */
  protected void readHeader(InetSocketAddress address) throws IOException {
    byte[] header = new byte[HEADER_SIZE];
    buffer.get(header);
    
    if (Arrays.equals(header, HEADER_PING)) {
      byte[] metadata = new byte[2];
      buffer.get(metadata);
      
      // first, read all of the source route
      byte[] route = new byte[SocketChannelRepeater.HEADER_BUFFER_SIZE * metadata[1]];
      buffer.get(route);
      
      // now, check to make sure our hop is correct
      EpochInetSocketAddress eisa = SocketChannelRepeater.decodeHeader(route, metadata[0]);
      
      // if so, process the packet
      if ((eisa.equals(localAddress)) || (eisa.getAddress().equals(localAddress.getAddress()) &&
                                          (eisa.getEpoch() == EpochInetSocketAddress.EPOCH_UNKNOWN))) {
        // if the packet is at the end of the route, accept it
        // otherwise, forward it to the next hop (and increment the stamp)
        if (metadata[0] + 1 == metadata[1]) {
          byte[] array = new byte[buffer.remaining()];
          buffer.get(array);
          buffer.clear();
          
          receiveMessage(deserialize(array), array.length, address);
        } else {
          EpochInetSocketAddress next = SocketChannelRepeater.decodeHeader(route, metadata[0] + 1);
          buffer.position(0);
          byte[] packet = new byte[buffer.remaining()];
          buffer.get(packet);
          
          // increment the hop count
          packet[HEADER_SIZE]++;
          
          synchronized (pendingMsgs) {
            pendingMsgs.add(new Envelope(next, packet));
          }
          
          SelectorManager.getSelectorManager().modifyKey(key);
        }
      } else {
        // if this is an old epoch of ours, reply with an update
        if (eisa.getAddress().equals(localAddress.getAddress())) {
          SourceRoute back = SourceRoute.build(new EpochInetSocketAddress[0]);
          SourceRoute outbound = SourceRoute.build(new EpochInetSocketAddress[0]);
          
          for (int i=0; i<metadata[0]; i++) {
            back = back.append(SocketChannelRepeater.decodeHeader(route, i));
            if (i > 0)
              outbound = outbound.append(SocketChannelRepeater.decodeHeader(route, i));
          }
          
          outbound = outbound.append(localAddress);

          enqueue(back.reverse(), new WrongEpochMessage(outbound, back.reverse(), eisa, localAddress));
        } else {
          System.out.println("WARNING: Received packet destined for EISA (" + metadata[0] + " " + metadata[1] + ") " + eisa + " but the local address is " + localAddress + " - dropping silently.");
          throw new IOException("Received packet destined for EISA (" + metadata[0] + " " + metadata[1] + ") " + eisa + " but the local address is " + localAddress + " - dropping silently.");
        }
      }
    } else {
      System.out.println("WARNING: Received unrecognized message header - ignoring.");
      throw new IOException("Improper message header received - ignoring. Read " + ((byte) header[0]) + " " + ((byte) header[1]) + " " + ((byte) header[2]) + " " + ((byte) header[3]));
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
  public static Object deserialize(byte[] array) throws IOException {
    ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(array));
    
    try {
      return ois.readObject();
    } catch (ClassNotFoundException e) {
      System.out.println("PANIC: Unknown class type in serialized message!");
      throw new IOException("Unknown class type in message - closing channel.");
    } catch (InvalidClassException e) {
      System.out.println("PANIC: Serialized message was an invalid class!");
      throw new IOException("Invalid class in message - closing channel.");
    }
  }    
    
  /**
   * Internal class which holds a pending datagram
   *
   * @author amislove
   */
  public class Envelope {
    protected EpochInetSocketAddress destination;
    protected byte[] data;

    /**
     * Constructor for Envelope.
     *
     * @param adr DESCRIBE THE PARAMETER
     * @param m DESCRIBE THE PARAMETER
     */
    public Envelope(EpochInetSocketAddress destination, byte[] data) {
      this.destination = destination;
      this.data = data;
    }
  }
  
  /**
    * Debug method
   *
   * @param s The string to print
   */
  private void debug(String s) {
    if (Log.ifp(8)) {
      System.out.println(spn.getNodeId() + " (PM): " + s);
    }
  }

}
