package rice.pastry.socket;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.environment.params.Parameters;
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
  
  // whether or not we should use short pings
  public final boolean USE_SHORT_PINGS;// = false;
  
  // the header which signifies a normal socket
  protected static byte[] HEADER_PING = new byte[] {0x49, 0x3A, 0x09, 0x5C};
  
  // the header which signifies a new, shorter ping
  protected static byte[] HEADER_SHORT_PING = new byte[] {0x31, 0x1C, 0x0E, 0x11};
  
  // the header which signifies a new, shorter ping
  protected static byte[] HEADER_SHORT_PING_RESPONSE = new byte[] {0x31, 0x1C, 0x0E, 0x12};  
  
  // the length of the ping header
  public static int HEADER_SIZE = HEADER_PING.length;

  // the size of the buffer used to read incoming datagrams must be big enough
  // to encompass multiple datagram packets
  public final int DATAGRAM_RECEIVE_BUFFER_SIZE;
  
  // the size of the buffer used to send outgoing datagrams this is also the
  // largest message size than can be sent via UDP
  public final int DATAGRAM_SEND_BUFFER_SIZE;
  
  // InetSocketAddress -> ArrayList of PingResponseListener
  protected Hashtable pingListeners = new Hashtable();

  // The list of pending meesages
  protected ArrayList pendingMsgs;

  // the buffer used for writing datagrams
  private ByteBuffer buffer;

  // the channel used from talking to the network
  private DatagramChannel channel;

  // the key used to determine what has taken place
  private SelectionKey key;
  
  // the source route manager
  private SocketSourceRouteManager manager;
  
  // the local address of this node
  private EpochInetSocketAddress localAddress;
  
  // the local node
  private SocketPastryNode spn;
  
  private Logger logger;
  
  /**
   * @param port DESCRIBE THE PARAMETER
   * @param manager DESCRIBE THE PARAMETER
   * @param pool DESCRIBE THE PARAMETER
   */
  public PingManager(SocketPastryNode spn, SocketSourceRouteManager manager, EpochInetSocketAddress bindAddress, EpochInetSocketAddress proxyAddress) {
    this.spn = spn;
    this.logger = spn.getEnvironment().getLogManager().getLogger(PingManager.class, null);
    this.manager = manager;
    this.pendingMsgs = new ArrayList();
    this.localAddress = proxyAddress;
    Parameters p = spn.getEnvironment().getParameters();
    USE_SHORT_PINGS = p.getBoolean("pastry_socket_pingmanager_smallPings"); 
    DATAGRAM_RECEIVE_BUFFER_SIZE = p.getInt("pastry_socket_pingmanager_datagram_receive_buffer_size");
    DATAGRAM_SEND_BUFFER_SIZE = p.getInt("pastry_socket_pingmanager_datagram_send_buffer_size");

    // allocate enought bytes to read data
    this.buffer = ByteBuffer.allocateDirect(DATAGRAM_SEND_BUFFER_SIZE);

    try {
      // bind to the appropriate port
      channel = DatagramChannel.open();
      channel.configureBlocking(false);
      channel.socket().bind(bindAddress.getAddress());
      channel.socket().setSendBufferSize(DATAGRAM_SEND_BUFFER_SIZE);
      channel.socket().setReceiveBufferSize(DATAGRAM_RECEIVE_BUFFER_SIZE);

      key = spn.getEnvironment().getSelectorManager().register(channel, this, 0);
      key.interestOps(SelectionKey.OP_READ);
    } catch (IOException e) {
      if (logger.level <= Logger.SEVERE) logger.log(
          "PANIC: Error binding datagram server to address " + localAddress + ": " + e);
    }
  }
  
  /**
   *        ----- EXTERNAL METHODS -----
   */
  
  /**
   * Method which actually sends a ping to over the specified path, and returns the result
   * to the specified listener.  Note that if no ping response is ever received, the 
   * listener is never called.
   *
   * @param path The path to send the ping over
   * @param prl The listener which should hear about the response
   */
  protected void ping(SourceRoute path, PingResponseListener prl) {
    if (logger.level <= Logger.FINE) logger.log(
        "(PM) Actually sending ping via path " + path + " local " + localAddress);

    addPingResponseListener(path, prl);
    
    if (USE_SHORT_PINGS)
      sendShortPing(path);
    else
      enqueue(path, new PingMessage(path, path.reverse(localAddress), spn.getEnvironment().getTimeSource().currentTimeMillis()));
  }
  
  /**
   * Makes this node resign from the network.  Is designed to be used for
   * debugging and testing.
   */
  protected void resign() throws IOException {
    key.channel().close();
    key.cancel();
  }
  
  /**
   * Internal testing method which simulates a stall. DO NOT USE!!!!!
   */
  public void stall() {
    key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
  }  
  
  /**
   *        ----- INTERNAL METHODS -----
   */
  
  /**
   * Builds the data for a short ping
   */
  protected void sendShortPing(SourceRoute route) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream dos = new DataOutputStream(baos);
      
      dos.write(HEADER_SHORT_PING);
      dos.writeLong(spn.getEnvironment().getTimeSource().currentTimeMillis());
      
      dos.flush();
      
      enqueue(route, baos.toByteArray());
    } catch (Exception canthappen) {
      if (logger.level <= Logger.SEVERE) logger.log(
          "CANT HAPPEN: " +canthappen);  
    }
  }
  
  /**
   * Builds the data for a short ping response
   */
  protected void shortPingReceived(SourceRoute route, byte[] payload) throws IOException {
    System.arraycopy(HEADER_SHORT_PING_RESPONSE, 0, payload, 0, HEADER_SHORT_PING_RESPONSE.length);
    enqueue(route.reverse(), payload);
  }
  
  /**
   * Processes a short ping response
   */
  protected void shortPingResponseReceived(SourceRoute route, byte[] payload) throws IOException {
    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(payload));
    dis.readFully(new byte[HEADER_SHORT_PING_RESPONSE.length]);
    long start = dis.readLong();
    int ping = (int) (spn.getEnvironment().getTimeSource().currentTimeMillis() - start);

    SourceRoute from = route.reverse();
    
    manager.markAlive(from);
    manager.markProximity(from, ping);
    notifyPingResponseListeners(from, ping, start);
  }

  /**
   * Adds a feature to the PingResponseListener attribute of the PingManager
   * object
   *
   * @param address The feature to be added to the PingResponseListener
   *      attribute
   * @param prl The feature to be added to the PingResponseListener attribute
   */
  protected void addPingResponseListener(SourceRoute path, PingResponseListener prl) {
    if (prl == null) 
      return;
    
    ArrayList list = (ArrayList) pingListeners.get(path);
    
    if (list == null) {
      list = new ArrayList();
      pingListeners.put(path, list);
    }
    
    list.add(prl);
  }
  
  /**
   * caller must synchronized(pingResponseTimes)
   *
   * @param address
   * @param proximity
   * @param lastTimePinged
   */
  protected void notifyPingResponseListeners(SourceRoute path, int proximity, long lastTimePinged) {
    ArrayList list = (ArrayList) pingListeners.remove(path);
    
    if (list != null) {
      Iterator i = list.iterator();
      
      while (i.hasNext()) 
        ((PingResponseListener) i.next()).pingResponse(path, proximity, lastTimePinged);
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
      byte[] data = addHeader(path, msg, localAddress, logger);
      
      synchronized (pendingMsgs) {
        pendingMsgs.add(new Envelope(path.getFirstHop(), data));
      }
      
      if ((spn != null) && (spn instanceof SocketPastryNode))
        ((SocketPastryNode) spn).broadcastSentListeners(msg, path.toArray(), data.length);
      
      if (! (msg instanceof byte[])) {
        if (logger.level <= Logger.FINER) logger.log(
          "COUNT: Sent message " + msg.getClass() + " of size " + data.length  + " to " + path);    
      } else if (((byte[]) msg)[3] == 0x11) {
        if (logger.level <= Logger.FINER) logger.log(
          "COUNT: Sent message rice.pastry.socket.messaging.ShortPingMessage of size " + data.length  + " to " + path);    
      } else if (((byte[]) msg)[3] == 0x12) {
        if (logger.level <= Logger.FINER) logger.log(
          "COUNT: Sent message rice.pastry.socket.messaging.ShortPingResponseMessage of size " + data.length  + " to " + path); 
      }
        
      spn.getEnvironment().getSelectorManager().modifyKey(key);
    } catch (IOException e) {
      if (logger.level <= Logger.SEVERE) logger.log(
          "ERROR: Received exceptoin " + e + " while enqueuing ping " + msg);
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
        if (logger.level <= Logger.FINER) logger.log(
            "COUNT: Read message " + message.getClass() + " of size " + size + " from " + dm.getInboundPath().reverse());      

        enqueue(dm.getInboundPath(), new PingResponseMessage(dm.getOutboundPath(), dm.getInboundPath(), start));        
      } else if (dm instanceof PingResponseMessage) {
        if (logger.level <= Logger.FINER) logger.log(
            "COUNT: Read message " + message.getClass() + " of size " + size + " from " + dm.getOutboundPath().reverse());      
        int ping = (int) (spn.getEnvironment().getTimeSource().currentTimeMillis() - start);
        
        manager.markAlive(dm.getOutboundPath());
        manager.markProximity(dm.getOutboundPath(), ping);
        notifyPingResponseListeners(dm.getOutboundPath(), ping, start);
      } else if (dm instanceof WrongEpochMessage) {
        WrongEpochMessage wem = (WrongEpochMessage) dm;
        
        if (logger.level <= Logger.FINER) logger.log(
            "COUNT: Read message " + message.getClass() + " of size " + size + " from " + dm.getOutboundPath().reverse());      

        manager.markAlive(dm.getOutboundPath());
        manager.markDead(wem.getIncorrect());
      } else if (dm instanceof IPAddressRequestMessage) {
        if (logger.level <= Logger.FINER) logger.log(
            "COUNT: Read message " + message.getClass() + " of size " + size + " from " + SourceRoute.build(new EpochInetSocketAddress(from)));      
        
        enqueue(SourceRoute.build(new EpochInetSocketAddress(from)), new IPAddressResponseMessage(from, spn.getEnvironment().getTimeSource().currentTimeMillis())); 
      } else {
        if (logger.level <= Logger.WARNING) logger.log(
            "ERROR: Received unknown DatagramMessage " + dm);
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
        buffer.flip();
        
 /*       if (address.getPort() % 2 == localAddress.getAddress().getPort() % 2) {
          buffer.clear();
          log(Logger.INFO,"Dropping packet");
          return;
        }  */
        
        if (buffer.remaining() > 0) {
          readHeader(address);
        } else {
          if (logger.level <= Logger.INFO) logger.log(
            "(PM) Read from datagram channel, but no bytes were there - no bad, but wierd.");
          break;
        }
      }
    } catch (IOException e) {
      if (logger.level <= Logger.WARNING) logger.logException(
          "ERROR (datagrammanager:read): ", e);
    } finally {
      buffer.clear();
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
          
          if (channel.send(ByteBuffer.wrap(write.data), write.destination.getAddress()) == write.data.length)
            i.remove();
          else
            break;
        }
      }
    } catch (IOException e) {
      if (logger.level <= Logger.WARNING) logger.log(
          "ERROR (datagrammanager:write): " + e);
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
   * Method which serializes a given object into a ByteBuffer, in order to
   * prepare it for writing.
   *
   * @param o The object to serialize
   * @return A ByteBuffer containing the object
   * @exception IOException if the object can't be serialized
   */
  public static byte[] serialize(Object message, Logger logger) throws IOException {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);      
      oos.writeObject(message);
      oos.close();
      
      return baos.toByteArray();
    } catch (InvalidClassException e) {
      if (logger.level <= Logger.SEVERE) logger.logException(
          "PANIC: Object to be serialized was an invalid class!",e);
      throw new IOException("Invalid class during attempt to serialize.");
    } catch (NotSerializableException e) {
      if (logger.level <= Logger.SEVERE) logger.logException(
          "PANIC: Object to be serialized was not serializable! [" + message + "]",e);
      throw new IOException("Unserializable class " + message + " during attempt to serialize.");
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
  public static Object deserialize(byte[] array, Environment env, SocketPastryNode spn, Logger logger) throws IOException {
    PastryObjectInputStream ois = new PastryObjectInputStream(new ByteArrayInputStream(array), spn);
    
    try {
      return ois.readObject();
    } catch (ClassNotFoundException e) {
      if (logger.level <= Logger.SEVERE) logger.logException(
          "PANIC: Unknown class type in serialized message!",e);
      throw new IOException("Unknown class type in message - closing channel.");
    } catch (InvalidClassException e) {
      if (logger.level <= Logger.SEVERE) logger.logException(
          "PANIC: Serialized message was an invalid class!",e);
      throw new IOException("Invalid class in message - closing channel.");
    }
  }
  
  /**
   * Method which adds a header for the provided path to the given data.
   *
   * @return The messag with a header attached
   */
  public static byte[] addHeader(SourceRoute path, Object data, EpochInetSocketAddress localAddress, Logger logger) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);      

    dos.write(HEADER_PING);
    dos.write((byte) 1);
    dos.write((byte) path.getNumHops() + 1);
    dos.write(SocketChannelRepeater.encodeHeader(localAddress));
    
    for (int i=0; i<path.getNumHops(); i++) 
      dos.write(SocketChannelRepeater.encodeHeader(path.getHop(i)));

    if (data instanceof byte[])
      dos.write((byte[]) data);
    else
      dos.write(serialize(data, logger));
    
    dos.flush();
  
    return baos.toByteArray();
  }
  
  /**
   * Method which adds a header for the provided path to the given data.
   *
   * @return The messag with a header attached
   */
  public SourceRoute decodeHeader(byte[] header) throws IOException {
    EpochInetSocketAddress[] route = new EpochInetSocketAddress[header.length / SocketChannelRepeater.HEADER_BUFFER_SIZE];
  
    for (int i=0; i<route.length; i++)
      route[i] = SocketChannelRepeater.decodeHeader(header, i);
    
    return SourceRoute.build(route);
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
          
          byte[] test = new byte[HEADER_SHORT_PING.length];
          System.arraycopy(array, 0, test, 0, test.length);
          
          SourceRoute sr = decodeHeader(route).removeLastHop();
          
          if (Arrays.equals(test, HEADER_SHORT_PING)) {
            if (logger.level <= Logger.FINER) logger.log(
                "COUNT: Read message rice.pastry.socket.messaging.ShortPingMessage of size " + (header.length + metadata.length + array.length + route.length)  + " from " + sr);    
            shortPingReceived(sr, array);
          } else if (Arrays.equals(test, HEADER_SHORT_PING_RESPONSE)) {
            if (logger.level <= Logger.FINER) logger.log(
                "COUNT: Read message rice.pastry.socket.messaging.ShortPingResponseMessage of size " + (header.length + metadata.length + array.length + route.length)  + " from " + sr);    
            shortPingResponseReceived(sr, array);
          } else {
            receiveMessage(deserialize(array, spn.getEnvironment(), spn, logger), array.length, address);
          }
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
          
          spn.getEnvironment().getSelectorManager().modifyKey(key);
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

          enqueue(back.reverse(), new WrongEpochMessage(outbound, back.reverse(), eisa, localAddress, spn.getEnvironment().getTimeSource().currentTimeMillis()));
        } else {
          if (logger.level <= Logger.WARNING) logger.log(
              "WARNING: Received packet destined for EISA (" + metadata[0] + " " + metadata[1] + ") " + eisa + " but the local address is " + localAddress + " - dropping silently.");
          throw new IOException("Received packet destined for EISA (" + metadata[0] + " " + metadata[1] + ") " + eisa + " but the local address is " + localAddress + " - dropping silently.");
        }
      }
    } else {
      if (logger.level <= Logger.WARNING) logger.log(
        "WARNING: Received unrecognized message header - ignoring from "+address+".");
      throw new IOException("Improper message header received - ignoring from "+address+". Read " + ((byte) header[0]) + " " + ((byte) header[1]) + " " + ((byte) header[2]) + " " + ((byte) header[3]));
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
}
