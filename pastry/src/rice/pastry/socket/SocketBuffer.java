/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
/*
 * Created on Feb 22, 2006
 */
package rice.pastry.socket;

import java.io.*;
import java.nio.ByteBuffer;

import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.util.MathUtils;
import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;

/**
 * Holds 1 serialized message for receiving or sending.  Has a growable buffer.
 * 
 * Has specialized code for RouteMessage, Liveness Message, and byte arrays.
 *  
 * This code is ugly and does too many things.  However right now this is what 
 * we have.
 * 
 * There are Several Different Constructors.  Depending which one you use, different
 * other methods will work.  Look at the docs on the individual constructors for more info.
 *  
 * @author Jeff Hoye
 */
public class SocketBuffer implements RawMessageDelivery {
  
  /**
   * Used for reverse compatibility.  So you can use the getMessage()/unpack() methods
   * of the RouteMessage and we will be able to deserialize the messages as java messages.
   */
  private MessageDeserializer defaultDeserializer;
  
  /**
   * Needed to deserialize the NodeHandle -- sender
   */
  private NodeHandleFactory nhf;
  
  /**
   * This is really ugly, and also for reverse compatibility.  It is needed
   * for a JavaDeserializer ... ugh...
   */
  private SocketPastryNode spn;
  
  // these variables contain details of the message that are necessary for 
  // further deserialization
  
  /**
   * The application address.
   */
  private int address;
  /**
   * The message type.
   */
  private short type;
  /**
   * The priority of the message
   */
  int priority;
  /**
   * The sender (can be null if there isn't a sender)
   */
  private NodeHandle sender;
  
  /**
   * RouteMessage stuff: tells if the message can be rapidly rerouted etc...
   */
  private SendOptions sendOpts;
  
  /**
   * If it is a routeMessage, the types of the internal message
   */
  short rmSubType = -2;
  int rmSubAddress = -2;

  // low level stuff
  /**
   * One way to hold bytes... ugh...
   */
  byte[] bytes = null;
  /**
   * The initial buffer size (it is growable)
   */
  public static final int DEFAULT_BUFFER_SIZE = 1024;
  /**
   * Hack to not have to allocate buffers I know to be zero
   */
  private static final byte[] ZERO = new byte[8];
  /**
   * To read the serialized object we got off the wire
   */
  SocketDataInputStream str;
  /**
   * Another way to hold bytes
   */
  private ByteBuffer buffer;
  /**
   * To read in bytes while we are serializing an object.
   */
  ExposedDataOutputStream o;
  /**
   * The guts of the EDOS
   */
  ExposedByteArrayOutputStream ebaos;

  /**
   * True if was just part of session initiation.  
   * 
   * The theory with this boolean is that we will eventually be able to reuse 
   * the SocketBuffer in some kind of pool.  This is not yet implemented.
   */
  boolean discard = false;
  
  /**
   * Main Constructor for writing an object.  
   * 
   * The purpose of the defaultDeserializer is to handle reverse compatibility 
   * with JavaSerialization and the old method calls.  If the code calls
   * RouteMessage.unpack() instead of RouteMessage.unpack(Deserializer) then 
   * the defaultDeserializer is used.
   * 
   * @param defaultDeserializer
   */
  public SocketBuffer(MessageDeserializer defaultDeserializer, NodeHandleFactory nhf) {
    this.defaultDeserializer = defaultDeserializer;
    this.nhf = nhf;
    initialize(DEFAULT_BUFFER_SIZE);
  }
  
  /**
   * When you expect to read a message.  This will deserialize the message
   * header.  Then you can call deserialize with an appropriate deserializer.
   */
  public SocketBuffer(byte[] input, SocketPastryNode spn) throws IOException {
    this.bytes = input;
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
   * This serializes UDP messages with the appropriate source route header.
   * 
   * @param address the local address
   * @param path the path to the destination
   * @param msg the message
   * @throws IOException
   */
  public SocketBuffer(EpochInetSocketAddress address, SourceRoute path, PRawMessage msg) throws IOException {
    this(address, path);
//    System.out.println("SB "+msg);
    serialize(msg, false);
  }
  
  /**
   * This constructor is a helper for the previous one.
   * Sets up the header part of a UDP message, without 
   * serializing the message itself.
   * 
   * @param address the local address
   * @param path the path to the destination
   * @throws IOException
   */
  private SocketBuffer(EpochInetSocketAddress address, SourceRoute path) throws IOException {
    initialize(DEFAULT_BUFFER_SIZE);
    
    o.write(SocketCollectionManager.PASTRY_MAGIC_NUMBER);
    o.writeInt(0); // version
//    o.write(PingManager.HEADER_PING, 0, PingManager.HEADER_PING.length);
    // current hop
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
  
  /**
   * Used to initialize a TCP stream header.
   * 
   * The counterpart reading of the header is found in 
   * SocketChannelRepeater.read()
   * 
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

  // these constructors are bogus, and should probably be gotten rid of, or documented better
  /**
   * Just bytes (no real concept of a message.)
   * This is for 
   *   a) sourcerouting bytes
   *   b) for a usually a stream header for getResponse().
   * 
   * TODO: The stream header version should probably use the one that is designed
   * to initialize a TCP stream.
   * 
   * @param output
   * @return
   */
  public SocketBuffer(byte[] output) {
    buffer = ByteBuffer.wrap(output);
    priority = -1;
    discard = true;
  }
  
  
  /**
   *   Serializes a SourceRoute (don't know why this isn't done in the stream
   *     header serializer.
   *   Serializes a Message for getResponse() should probably use normal constructor.
   * 
   * @param rm
   * @param logger
   */
  public SocketBuffer(PRawMessage rm) throws IOException {
    initialize(DEFAULT_BUFFER_SIZE);
    serialize(rm, true); 
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
    // consider backing with a DataOutputStream to properly handle String
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

        
        // range check priority
        priority = msg.getPriority();
        if (priority > Byte.MAX_VALUE) throw new IllegalStateException("Priority must be in the range of "+Byte.MIN_VALUE+" to "+Byte.MAX_VALUE+".  Lower values are higher priority. Priority of "+msg+" was "+priority+".");
        if (priority < Byte.MIN_VALUE) throw new IllegalStateException("Priority must be in the range of "+Byte.MIN_VALUE+" to "+Byte.MAX_VALUE+".  Lower values are higher priority. Priority of "+msg+" was "+priority+".");
        o.writeByte((byte)priority);
        
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
        int skipped = str.skipBytes(amtToSkip);
        if (skipped != amtToSkip) throw new RuntimeException("Couldn't skip the right amount of bytes. Attempted:"+amtToSkip+" skipped:"+skipped);
        
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
  
  public byte[] getBytes() {
    return bytes; 
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
//    ByteArrayInputStream bais;

    public SocketDataInputStream(ByteArrayInputStream arg0) {
      super(arg0);
//      bais = arg0;
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
