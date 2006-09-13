/*
 * Created on Feb 22, 2006
 */
package rice.pastry.socket;

import java.io.*;
import java.nio.ByteBuffer;

import rice.environment.logging.Logger;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.util.MathUtils;
import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;

/**
 * Holds 1 serialized message for receiving or sending.
 * 
 * Has specialized code for RouteMessage, Liveness Message, and byte arrays.
 *  
 * @author Jeff Hoye
 */
public class SocketBuffer implements RawMessageDelivery {
  private MessageDeserializer defaultDeserializer;
  private NodeHandleFactory nhf;
  private SocketPastryNode spn;
  
  private int address;
  private short type;
  byte priority;
  private NodeHandle sender;
  
  // RouteMessage stuff
  private SendOptions sendOpts;
  
  short rmSubType = -2;
  int rmSubAddress = -2;

  // low level stuff
  public static final int DEFAULT_BUFFER_SIZE = 1024;
  // Hack to not have to allocate buffers I know to be zero
  public static final byte[] ZERO = new byte[8];
  SocketDataInputStream str;
  private ByteBuffer buffer;
  ExposedDataOutputStream o;
  ExposedByteArrayOutputStream ebaos;

  /**
   * True if was just part of session initiation.
   */
  boolean discard = false;
  
  // writing bytes
  public SocketBuffer(byte[] output) {
    buffer = ByteBuffer.wrap(output);
    priority = -1;
    discard = true;
  }
  
  // from a read
  public SocketBuffer(byte[] input, SocketPastryNode spn) throws IOException {
    str = new SocketDataInputStream(new ByteArrayInputStream(input));
    nhf = spn;
    this.spn = spn;
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  +            Appl   Address    (0 is the router)                +
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  +  HasSender?   +   Priority    +  Type (Application specifc)   + // zero is java serialization
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  +            NodeHandle sender                                  + 
//  +                                                               +
//                    ...  flexable size  
//  +                                                               +
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    
    address = str.readInt();
    boolean hasSender = str.readBoolean();
    priority = str.readByte();
    type = str.readShort();
    if (hasSender) {
      if (spn == null) {
        sender = SocketNodeHandle.build(str);
      } else {
        sender = spn.readNodeHandle(str);
      }
    }
  }
    
  /**
   * For quick write
   * @param rm
   * @param logger
   */
  public SocketBuffer(PRawMessage rm) throws IOException {
    initialize(DEFAULT_BUFFER_SIZE);
    serialize(rm, true); 
  }
  
  /**
   * For a quick write.
   * @throws IOException
   */
  public SocketBuffer(EpochInetSocketAddress address, SourceRoute path) throws IOException {
    initialize(DEFAULT_BUFFER_SIZE);
    
    o.write(SocketCollectionManager.PASTRY_MAGIC_NUMBER);
    o.writeInt(0); // version
//    o.write(PingManager.HEADER_PING, 0, PingManager.HEADER_PING.length);
    o.writeByte((byte) 1);
    int numHops = path.getNumHops() + 1;
    o.writeByte((byte) (numHops));
    
//    System.out.println("SB<ctor> numHops:"+numHops);
    
    short length = address.getSerializedLength();    
    for (int i=0; i<path.getNumHops(); i++) 
      length+=path.getHop(i).getSerializedLength();
    
    o.writeShort(length);
    address.serialize(o);
    
    for (int i=0; i<path.getNumHops(); i++) 
      path.getHop(i).serialize(o);
  }
  
  public SocketBuffer(EpochInetSocketAddress address, SourceRoute path, PRawMessage msg) throws IOException {
    this(address, path);
//    System.out.println("SB "+msg);
    serialize(msg, false);
  }
  
  /**
   * Used to initialize the sourceroute path.  Counterpart reading of the header is found in SocketChannelRepeater.read()
   * @param path
   * @param appId
   * @throws IOException
   */
  public SocketBuffer(SourceRoute path, int appId) throws IOException {
    initialize(DEFAULT_BUFFER_SIZE);
    o.write(SocketCollectionManager.PASTRY_MAGIC_NUMBER);
    o.writeInt(0); // version
    for (int i=1; i<path.getNumHops(); i++) {
      o.write(SocketCollectionManager.HEADER_SOURCE_ROUTE,0,SocketCollectionManager.HEADER_SOURCE_ROUTE.length);
      o.writeShort(path.getHop(i).getSerializedLength());
      path.getHop(i).serialize(o);
    }     
    o.write(SocketCollectionManager.HEADER_DIRECT,0,SocketCollectionManager.HEADER_DIRECT.length);
    o.write(MathUtils.intToByteArray(appId), 0, 4);    
  }
  
  /**
   * Main Constructor
   * @param defaultDeserializer
   */
  public SocketBuffer(MessageDeserializer defaultDeserializer, NodeHandleFactory nhf) {
    this.defaultDeserializer = defaultDeserializer;
    this.nhf = nhf;
    initialize(DEFAULT_BUFFER_SIZE);
  }

  protected void initialize(int size) {
//    System.out.println("SB.initialize("+size+")");
    ebaos = new ExposedByteArrayOutputStream(size);
    o = new ExposedDataOutputStream(ebaos, size);
  }

  
  
  public boolean isRouteMessage() {
    return address == RouterAddress.getCode() && type == RouteMessage.TYPE;
  }

  public SendOptions getOptions() {
    return sendOpts;
  }

  public RouteMessage getRouteMessage() {
    try {
      return (RouteMessage)deserialize(null);
    } catch (IOException ioe) {
      throw new RuntimeException(ioe); 
    }
  }

  /**
   * Will grow the buffer as needed
   * 
   * @param msg
   */
  public void serialize(PRawMessage msg, boolean reset) throws IOException {
    // asdf consider backing with a DataOutputStream to properly handle String
//    boolean done = false;
//    while (!done) {
//      try {
    boolean includeSize = reset;
        if (reset) {
          o.reset();
          ebaos.reset();
        }
        
        // mark stream location so we can add the size at the beginning
        int o_offset = o.bytesWritten();
        
        // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        // + Payload Length +
        // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        // version, header, Reserved, space for the length, which will be filled
        // in later
        if (includeSize)
          o.write(ZERO, 0, 4);

        // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        // + Appl Address (0 is the router)                                +
        // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        // + Next Header + Priority + Type (Application specifc) + // zero is
        // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        
        
        address = msg.getDestination();
        o.writeInt(address);
        
        sender = msg.getSender();
        boolean hasSender = (sender != null);
        o.writeBoolean(hasSender);

        priority = msg.getPriority();
        o.writeByte(priority);
        
        type = msg.getType();        
        o.writeShort(type);
        
        if (isRouteMessage()) {
          RouteMessage rm = (RouteMessage)msg; 
          sendOpts = rm.getOptions(); 
          rmSubAddress = rm.getAuxAddress();
          rmSubType = rm.getInternalType();
        }

        // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        // + NodeHandle sender +
        // + +
        // ... flexable size
        // + +
        // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        if (hasSender) {
          msg.getSender().serialize(o);
        }
        
        // so we know where to advance the str a few lines down
        int amtToSkip = o.bytesWritten();
        
        msg.serialize(o);
        o.flush();

        // go back and fill in the size
        int totalSize = ebaos.size();
        buffer = ByteBuffer.wrap(ebaos.buf());
        buffer.clear();
        if (includeSize) {
          buffer.position(o_offset);
          int messageSize = totalSize - 4;
          buffer.putInt(messageSize); // messageSize
        }
        // set up the buffer for writing
        buffer.clear();
        buffer.limit(totalSize);

        // to be able to deserialize if we need to reroute
        str = new SocketDataInputStream(
            new ByteArrayInputStream(buffer.array()));
       
        // don't forget to advance the stream past the header info
        str.skipBytes(amtToSkip);
        
        
//        done = true;
//      } catch (Exception e) {
//        if (logger.level <= Logger.WARNING)
//          logger
//              .logException("Growing buffer to " + (buffer.capacity() * 2), e);
//        initialize(buffer.capacity() * 2);
//      } // try
//    } // while
  }
  
//  public void serialize(PRawMessage msg) {
//    address = msg.getDestination();
//    type = msg.getType();
//    priority = msg.getPriority();
//    sender = msg.getSender();
//  }

  public ByteBuffer getBuffer() {
    if (buffer != null) return buffer;    
    buffer = (ByteBuffer)ByteBuffer.wrap(ebaos.buf()).limit(o.bytesWritten());
    return buffer; 
  }
  
  public int getAddress() {
    return address;
  }

  public Message deserialize(MessageDeserializer md) throws IOException {
    if (md == null) md = defaultDeserializer;

//    try {
//      str.mark(100000000);
      if (isRouteMessage()) {
        RouteMessage rm = RouteMessage.build(str, nhf, spn);
        rmSubAddress = rm.getAuxAddress();
        rmSubType = rm.getInternalType();
        return rm;
      }
  //    try {
      
        Message m = (Message)md.deserialize(str, type, priority, sender);
  //    } catch (IOException ioe) {
  //      if (logger.level <= Logger.SEVERE) logger.log("Error deserializing address:"+address+" type:"+type); 
  //    }
  //    System.out.println("SB.deserialize("+m+")");
      return m;
//    } finally {
//      str.reset(); 
//    }
  }

  
  static class ExposedByteArrayOutputStream extends ByteArrayOutputStream {
    public ExposedByteArrayOutputStream(int size) {      
      super(size);
    }

    public byte[] buf() {
      return buf;
    }
  }

  static class ExposedDataOutputStream extends DataOutputStream implements
      OutputBuffer {
    int capacity;

    public ExposedDataOutputStream(OutputStream sub, int capacity) {
      super(sub);
      this.capacity = capacity;
    }

    public int bytesRemaining() {
      return capacity - size();
    }

    public void reset() {
      written = 0;
    }

    public int bytesWritten() {
      return written; 
    }
    
    public void writeByte(byte v) throws IOException {
      this.write(v);
    }

    public void writeChar(char v) throws IOException {
      writeChar((int) v);
    }

    public void writeShort(short v) throws IOException {
      writeShort((int) v);
    }
  }

  static class SocketDataInputStream extends DataInputStream implements
      InputBuffer {
    ByteArrayInputStream bais;

    public SocketDataInputStream(ByteArrayInputStream arg0) {
      super(arg0);
      bais = arg0;
    }

//    public short peakShort() throws IOException {
//      bais.mark(2);
//      short temp = readShort();
//      bais.reset();
//      return temp;
//    }
    
    /**
     * I'm not sure this will always work. May need to contain the BAIS, and
     * check it directly.
     * 
     */
    public int bytesRemaining() {
      try {
        return this.available();
      } catch (IOException ioe) {
        ioe.printStackTrace();
      }
      return -1;
    }

  }

  public short getInnermostType() {
    if (isRouteMessage()) return rmSubType;
    return type;
  }

  public int getInnermostAddress() {
    if (isRouteMessage()) return rmSubAddress;
    return address;
  }

  public short getType() {
    return type;
  }

  public void setType(short type) {
    this.type = type;
  }

  public String toString() {
//    if (internalMessage != null) return "SocketBuffer ["+internalMessage+"]";
    if (isRouteMessage()) return "SocketBuffer[RouteMessage["+rmSubType+"@"+rmSubAddress+"]]";
    return "SocketBuffer a:"+address+" t:"+type;
  }

  
}
