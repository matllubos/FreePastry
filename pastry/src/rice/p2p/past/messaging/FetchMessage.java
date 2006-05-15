
package rice.p2p.past.messaging;

import java.io.IOException;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.past.*;
import rice.p2p.past.rawserialization.*;

/**
 * @(#) FetchMessage.java
 *
 * This class represents a message which is a fetch request in past, based
 * on a handle).
 *
 * response should be a PastContent
 *
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Ansley Post
 * @author Peter Druschel
 */
public class FetchMessage extends ContinuationMessage {
  public static final short TYPE = 3;

  // the id to fetch
  private RawPastContentHandle handle;

  // whether or not this message has been cached
  private boolean cached = false;
  
  /**
   * Constructor 
   *
   * @param uid The unique id
   * @param handle The handle to the data to be looked up
   * @param source The source address
   * @param dest The destination address
   */
  public FetchMessage(int uid, PastContentHandle handle, NodeHandle source, Id dest) {
    this(uid, handle instanceof RawPastContentHandle ? (RawPastContentHandle)handle : new JavaSerializedPastContentHandle(handle), source, dest);
  }
  public FetchMessage(int uid, RawPastContentHandle handle, NodeHandle source, Id dest) {
    super(uid, source, dest);

    this.handle = handle;
  }

  /**
   * Method which returns the handle
   *
   * @return The contained handle
   */
  public PastContentHandle getHandle() {
//  if (content == null) 
    if (handle.getType() == 0) return ((JavaSerializedPastContentHandle)handle).getPCH();
    return handle;
  }

  /**
   * Returns whether or not this message has been cached
   *
   * @return Whether or not this message has been cached
   */
  public boolean isCached() {
    return cached;
  }

  /**
   * Sets this message as having been cached.
   */
  public void setCached() {
    cached = true;
  }

  /**
    * Returns a string representation of this message
   *
   * @return A string representing this message
   */
  public String toString() {
    return "[FetchMessage for " + handle + " cached? " + cached + "]";
  }

  /***************** Raw Serialization ***************************************/
  public short getType() {
    return TYPE; 
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0); // version    
    if (response != null && response instanceof RawPastContent) {
      super.serialize(buf, false); 
      RawPastContent rpc = (RawPastContent)response;
      buf.writeShort(rpc.getType());
      rpc.serialize(buf);
    } else {
      super.serialize(buf, true);       
    }
    
    buf.writeBoolean(cached);
    
//    System.out.println("FetchMessage.serialize() handle = "+handle);
    buf.writeShort(handle.getType());
    handle.serialize(buf);
  }
  
  public static FetchMessage build(InputBuffer buf, Endpoint endpoint, PastContentDeserializer pcd, PastContentHandleDeserializer pchd) throws IOException {
    byte version = buf.readByte();
    switch(version) {
      case 0:
        return new FetchMessage(buf, endpoint, pcd, pchd);
      default:
        throw new IOException("Unknown Version: "+version);        
    }
  }  
  
  private FetchMessage(InputBuffer buf, Endpoint endpoint, PastContentDeserializer pcd, PastContentHandleDeserializer pchd) throws IOException {
    super(buf, endpoint);
    // if called super.serializer(x, true) these will be set
    if (serType == S_SUB) {
      short type2 = buf.readShort();      
      response = pcd.deserializePastContent(buf, endpoint, type2); 
    }
    
    cached = buf.readBoolean();
    
    short type = buf.readShort();
    if (type == 0) {
      handle = new JavaSerializedPastContentHandle(pchd.deserializePastContentHandle(buf, endpoint, type));
    } else {
      handle = (RawPastContentHandle)pchd.deserializePastContentHandle(buf, endpoint, type); 
    }
  }
}

