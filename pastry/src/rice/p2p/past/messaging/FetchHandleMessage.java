
package rice.p2p.past.messaging;

import java.io.IOException;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.past.*;
import rice.p2p.past.gc.GCId;
import rice.p2p.past.rawserialization.*;

/**
 * @(#) FetchHandleMessage.java
 *
 * This class represents a handle request in Past.
 *
 * response should be a PastContentHandle
 *
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Ansley Post
 * @author Peter Druschel
 */
public class FetchHandleMessage extends ContinuationMessage {
  public static final short TYPE = 2;

  
  
  // the id to fetch
  private Id id;
  
  /**
   * Constructor 
   *
   * @param uid The unique id
   * @param id The id of the object to be looked up
   * @param source The source address
   * @param dest The destination address
   */
  public FetchHandleMessage(int uid, Id id, NodeHandle source, Id dest) {
    super(uid, source, dest);

    this.id = id;
  }

  /**
   * Method which returns the id
   *
   * @return The contained id
   */
  public Id getId() {
    return id;
  }

  /**
    * Returns a string representation of this message
   *
   * @return A string representing this message
   */
  public String toString() {
    return "[FetchHandleMessage for " + id + "]";
  }  
  
  /***************** Raw Serialization ***************************************/
  public short getType() {
    return TYPE; 
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0); // version    
    if (response != null && response instanceof RawPastContentHandle) {
      super.serialize(buf, false); 
      RawPastContentHandle rpch = (RawPastContentHandle)response;
      buf.writeShort(rpch.getType());
      rpch.serialize(buf);
    } else {
      super.serialize(buf, true);       
    }
    buf.writeShort(id.getType());
    id.serialize(buf);
  }
  
  public static FetchHandleMessage build(InputBuffer buf, Endpoint endpoint, PastContentHandleDeserializer pchd) throws IOException {
    byte version = buf.readByte();
    switch(version) {
      case 0:
        return new FetchHandleMessage(buf, endpoint, pchd);
      default:
        throw new IOException("Unknown Version: "+version);        
    }
  }  
  
  private FetchHandleMessage(InputBuffer buf, Endpoint endpoint, PastContentHandleDeserializer pchd) throws IOException {
    super(buf, endpoint);
    // if called super.serializer(x, true) these will be set
    if (serType == S_SUB) {
      short type = buf.readShort();      
      response = pchd.deserializePastContentHandle(buf, endpoint, type); 
    }
    id = endpoint.readId(buf, buf.readShort());
  }
  
}

