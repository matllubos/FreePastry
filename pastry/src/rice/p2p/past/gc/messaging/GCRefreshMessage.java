
package rice.p2p.past.gc.messaging;

import java.io.IOException;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.past.*;
import rice.p2p.past.messaging.*;
import rice.p2p.past.gc.*;

/**
 * @(#) GCRefreshMessage.java
 *
 * This class represents a message which is an request to extend the lifetime
 * of a set of keys stored in GCPast.
 *
 * response is expected to be Boolean[]
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class GCRefreshMessage extends ContinuationMessage {
  public static final short TYPE = 11;

  // the list of keys which should be refreshed
  protected GCId[] keys;
  
  /**
   * Constructor which takes a unique integer Id, as well as the
   * keys to be refreshed
   *
   * @param uid The unique id
   * @param keys The keys to be refreshed
   * @param expiration The new expiration time
   * @param source The source address
   * @param dest The destination address
   */
  public GCRefreshMessage(int uid, GCIdSet keys, NodeHandle source, Id dest) {
    super(uid, source, dest);
    
    this.keys = new GCId[keys.numElements()];
    System.arraycopy(keys.asArray(),0,this.keys, 0, this.keys.length);
  }

  /**
   * Method which returns the list of keys
   *
   * @return The list of keys to be refreshed
   */
  public GCId[] getKeys() {
    return keys;
  }

  /**
    * Returns a string representation of this message
   *
   * @return A string representing this message
   */
  public String toString() {
    return "[GCRefreshMessage of " + keys.length + "]";
  }
  
  /***************** Raw Serialization ***************************************/
  public short getType() {
    return TYPE;
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0); // version    
    if (response != null && response instanceof Boolean[]) {
      super.serialize(buf, false);       
      Boolean[] array = (Boolean[])response;
      buf.writeInt(array.length);
      for (int i = 0; i < array.length; i++) {
        buf.writeBoolean(array[i].booleanValue()); 
      }
    } else {
      super.serialize(buf, true);       
    }

    buf.writeInt(keys.length);
    for (int i = 0; i < keys.length; i++) {
      buf.writeShort(keys[i].getType());      
      keys[i].serialize(buf);
    }
  }
  
  public static GCRefreshMessage build(InputBuffer buf, Endpoint endpoint) throws IOException {
    byte version = buf.readByte();
    switch(version) {
      case 0:
        return new GCRefreshMessage(buf, endpoint);
      default:
        throw new IOException("Unknown Version: "+version);        
    }
  }  
  
  private GCRefreshMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint);
    
    if (serType == S_SUB) {      
      int arrayLength = buf.readInt();      
      Boolean[] array = new Boolean[arrayLength];
      for (int i = 0; i < arrayLength; i++) {
        array[i] = new Boolean(buf.readBoolean()); 
      }
    }
    
    keys = new GCId[buf.readInt()];
    for (int i = 0; i < keys.length; i++) {
      keys[i] = (GCId)endpoint.readId(buf, buf.readShort());
    }
  }
}

