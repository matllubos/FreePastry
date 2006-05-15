package rice.p2p.aggregation.raw;

import rice.p2p.past.gc.GCPastContent;
import rice.p2p.past.*;
import rice.p2p.past.gc.*;
import rice.p2p.past.gc.rawserialization.*;
import rice.p2p.past.rawserialization.*;
import rice.p2p.util.rawserialization.SimpleOutputBuffer;
import rice.p2p.aggregation.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.glacier.VersionKey;
import java.security.*;
import java.io.*;

public class RawAggregate extends Aggregate implements RawGCPastContent {
  
  private RawGCPastContent[] rawComponents;
  
  private static final long serialVersionUID = -4891386773008082L;
  public static final short TYPE = 1;
  
  public RawAggregate(GCPastContent[] components, Id[] pointers) {
    super(components, pointers);
    buildRawComponents(components);
    this.myId = null;
    this.pointers = pointers;
  }
  
  public RawAggregate(RawGCPastContent[] components, Id[] pointers) {
    super(components,pointers);
    this.rawComponents = components;
    this.myId = null;
    this.pointers = pointers;
  }
  
  public int numComponents() {
    return rawComponents.length;
  }
  
  public byte[] getContentHash() {
    byte[] bytes = null;
    int numBytes = 0;
    try {
//      ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
//      ObjectOutputStream objectStream = new ObjectOutputStream(byteStream);
//
//      objectStream.writeObject(components);
//      objectStream.writeObject(pointers);
//      objectStream.flush();
//
//      bytes = byteStream.toByteArray();
      SimpleOutputBuffer buf = new SimpleOutputBuffer();
      serializeHelper(buf);
      bytes = buf.getBytes();      
      numBytes = buf.getWritten();
    } catch (IOException ioe) {
      return null;
    }
    
    MessageDigest md = null;
    try {
      md = MessageDigest.getInstance("SHA");
    } catch (NoSuchAlgorithmException e) {
      return null;
    }

    md.reset();
    md.update(bytes,0,numBytes);
    
    return md.digest();
  }
  
  /***************** Raw Serialization ***************************************/
  public short getType() {
    return TYPE; 
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0); // version    
    buf.writeShort(myId.getType());
    myId.serialize(buf);
    serializeHelper(buf);
  }
  
  /**
   * Used in serialize() and getContentHash()
   * @param buf
   * @throws IOException
   */
  private void serializeHelper(OutputBuffer buf) throws IOException {
    buf.writeShort((short)pointers.length);
    for (int i = 0; i < pointers.length; i++) {
      buf.writeShort(pointers[i].getType());
      pointers[i].serialize(buf);
    }
    
    buf.writeShort((short)rawComponents.length);
    for (int i = 0; i < rawComponents.length; i++) {
      buf.writeShort(rawComponents[i].getType());
      rawComponents[i].serialize(buf);
    }    
  }
    
  public RawAggregate(InputBuffer buf, Endpoint endpoint, PastContentDeserializer contentDeserializer) throws IOException {
    super(null, null);
    
    byte version = buf.readByte();
    switch(version) {
      case 0:
        myId = endpoint.readId(buf, buf.readShort());
        
        pointers = new Id[buf.readShort()];
        for (int i = 0; i < pointers.length; i++) {
          pointers[i] = endpoint.readId(buf, buf.readShort());
        }
        
        rawComponents = new RawGCPastContent[buf.readShort()];
        components = new GCPastContent[rawComponents.length];
        for (int i = 0; i < rawComponents.length; i++) {
          short type = buf.readShort();
          GCPastContent temp = (GCPastContent)contentDeserializer.deserializePastContent(buf, endpoint, type);
          if (type == 0) {
            components[i] = temp;
            rawComponents[i] = new JavaSerializedGCPastContent(temp);
          } else {
            components[i] = temp;
            rawComponents[i] = (RawGCPastContent)temp;
          }
        }
        break;
      default:
        throw new IOException("Unknown Version: "+version);
    }     
  }
  
  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    buildRawComponents(components);
  }  
  
  private void buildRawComponents(GCPastContent[] components) {
    this.rawComponents = new RawGCPastContent[components.length];
    for(int i = 0; i < rawComponents.length; i++) {
      if (rawComponents[i] instanceof RawGCPastContent) {
        this.rawComponents[i] = (RawGCPastContent)components[i];
      } else {
        this.rawComponents[i] = new JavaSerializedGCPastContent(components[i]); 
      }
    }    
  }
};
