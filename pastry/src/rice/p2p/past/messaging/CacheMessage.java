
package rice.p2p.past.messaging;

import java.io.IOException;

import rice.*;
import rice.environment.Environment;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.past.*;
import rice.p2p.past.rawserialization.*;

/**
 * @(#) CacheMessage.java
 *
 * This class represents message which pushes an object forward one hop in order
 * to be cached.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Ansley Post
 * @author Peter Druschel
 */
public class CacheMessage extends PastMessage {

  public static final short TYPE = 1;
  
  // the content to be cached
  protected RawPastContent content;
  
  /**
   * Constructor which takes a unique integer Id and the local id
   *
   * @param uid The unique id
   */
  public CacheMessage(int uid, PastContent content, NodeHandle source, Id dest) {
    this(uid, content instanceof RawPastContent ? (RawPastContent)content : new JavaSerializedPastContent(content), source, dest);    
  }
  
  public CacheMessage(int uid, RawPastContent content, NodeHandle source, Id dest) {
    super(uid, source, dest);

    this.content = content;
  }

  /**
   * Method which returns the content
   *
   * @return The content
   */
  public PastContent getContent() {
//  if (content == null) 
    if (content.getType() == 0) return ((JavaSerializedPastContent)content).getContent();
    return content;
  }

  /**
    * Method by which this message is supposed to return it's response.
   *
   * @param c The continuation to return the reponse to.
   */
  public void returnResponse(Continuation c, Environment env, String instance) {
    throw new RuntimeException("ERROR: returnResponse should not be called on cacheMessage!");
  }

  /**
   * Returns a string representation of this message
   *
   * @return A string representing this message
   */
  public String toString() {
    return "[CacheMessage for " + content + "]";
  }
  
  /***************** Raw Serialization ***************************************/
  public short getType() {
    return TYPE;
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0); // version
    super.serialize(buf);
    buf.writeShort(content.getType());
    content.serialize(buf);      
  }
  
  public static CacheMessage build(InputBuffer buf, Endpoint endpoint, PastContentDeserializer pcd) throws IOException {
    byte version = buf.readByte();
    switch(version) {
      case 0:
        return new CacheMessage(buf, endpoint, pcd);
      default:
        throw new IOException("Unknown Version: "+version);        
    }
  }
  
  private CacheMessage(InputBuffer buf, Endpoint endpoint, PastContentDeserializer pcd) throws IOException {
    super(buf, endpoint);
    // this can be done lazilly to be more efficient, must cache remaining bits, endpoint, cd, and implement own InputBuffer
    short contentType = buf.readShort();
    if (contentType == 0) {
      content = new JavaSerializedPastContent(pcd.deserializePastContent(buf, endpoint, contentType));
    } else {
      content = (RawPastContent)pcd.deserializePastContent(buf, endpoint, contentType); 
    }
  }   
  
}

