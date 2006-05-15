
package rice.p2p.past.messaging;

import java.io.IOException;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.multiring.MultiringNodeHandleSet;
import rice.p2p.past.*;

/**
 * @(#) LookupMessage.java
 *
 * This class represents a request for all of the replicas of a given object.
 *
 * @version $Id$
 *
 * result should be MultiringNodeHandleSet
 *
 * @author Alan Mislove
 * @author Ansley Post
 * @author Peter Druschel
 */
public class LookupHandlesMessage extends ContinuationMessage {
  public static final short TYPE = 5;

  // the id to fetch
  private Id id;

  // the number of replicas to fetch
  private int max;
   
  /**
   * Constructor
   *
   * @param uid The unique id
   * @param id The location to be stored
   * @param max The number of replicas
   * @param source The source address
   * @param dest The destination address
   */
  public LookupHandlesMessage(int uid, Id id, int max, NodeHandle source, Id dest) {    
    super(uid, source, dest);

    this.id = id;
    this.max = max;
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
   * Method which returns the number of replicas
   *
   * @return The number of replicas to fetch
   */
  public int getMax() {
    return max;
  }

  /**
    * Returns a string representation of this message
   *
   * @return A string representing this message
   */
  public String toString() {
    return "[LookupHandlesMessage (response " + isResponse() + " " + response + ") for " + id + " max " + max + "]";
  }

  /***************** Raw Serialization ***************************************/
  public short getType() {
    return TYPE;
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0); // version        
    serializeHelper(buf);
  }
  
  /**
   * So that it can be subclassed without serializing a version here
   * @param buf
   * @throws IOException
   */
  protected void serializeHelper(OutputBuffer buf) throws IOException {
    if (response != null && response instanceof NodeHandleSet) {
      super.serialize(buf, false); 
      NodeHandleSet set = (NodeHandleSet)response;
      buf.writeShort(set.getType());
      set.serialize(buf);
    } else {
      super.serialize(buf, true);       
    }
    buf.writeInt(max);
    buf.writeShort(id.getType());
    id.serialize(buf);
  }
  
  public static LookupHandlesMessage build(InputBuffer buf, Endpoint endpoint) throws IOException {
    byte version = buf.readByte();
    switch(version) {
      case 0:
        return new LookupHandlesMessage(buf, endpoint);
      default:
        throw new IOException("Unknown Version: "+version);        
    }
  }  
  
  protected LookupHandlesMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint);    
    // if called super.serializer(x, true) these will be set
    if (serType == S_SUB) {
      short type = buf.readShort();
      response = endpoint.readNodeHandleSet(buf, type);
    }
    max = buf.readInt();    
    id = endpoint.readId(buf, buf.readShort());
  }
}

