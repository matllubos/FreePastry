
package rice.p2p.scribe.messaging;

import java.io.IOException;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.scribe.*;
import rice.p2p.scribe.rawserialization.*;

/**
 * @(#) PublishMessage.java
 *
 * The publish message.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class PublishMessage extends ScribeMessage {
  public static final short TYPE = 8;

  // the content of this message
  protected RawScribeContent content;

  /**
   * Constructor which takes a unique integer Id
   *
   * @param id The unique id
   * @param source The source address
   * @param dest The destination address
   */
  public PublishMessage(NodeHandle source, Topic topic, ScribeContent content) {
    this(source, topic, content instanceof RawScribeContent ? (RawScribeContent)content : new JavaSerializedScribeContent(content));
  }
  
  public PublishMessage(NodeHandle source, Topic topic, RawScribeContent content) {
    super(source, topic);

    this.content = content;
  }

  /**
   * Returns the content
   *
   * @return The content
   */
  public ScribeContent getContent() {
//  if (content == null) 
    if (content.getType() == 0) return ((JavaSerializedScribeContent)content).getContent();
    return content;
  }
  
  public void setContent(ScribeContent content) {
    if (content instanceof RawScribeContent) {
      setContent(content); 
    } else {
      setContent(new JavaSerializedScribeContent(content));
    }
  }
  
  public String toString() {
    return "PublishMessage"+topic+":"+content;
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
   
  public static PublishMessage build(InputBuffer buf, Endpoint endpoint, ScribeContentDeserializer scd) throws IOException {
    byte version = buf.readByte();
    switch(version) {
      case 0:
        return new PublishMessage(buf, endpoint, scd);
      default:
        throw new IOException("Unknown Version: "+version);
    }
  }
  
  /**
   * Private because it should only be called from build(), if you need to extend this,
   * make sure to build a serializeHelper() like in AnycastMessage/SubscribeMessage, and properly handle the 
   * version number.
   */
  private PublishMessage(InputBuffer buf, Endpoint endpoint, ScribeContentDeserializer cd) throws IOException {
    super(buf, endpoint);
    
    // this can be done lazilly to be more efficient, must cache remaining bits, endpoint, cd, and implement own InputBuffer
    short contentType = buf.readShort();
    if (contentType == 0) {
      content = new JavaSerializedScribeContent(cd.deserializeScribeContent(buf, endpoint, contentType));
    } else {
      content = (RawScribeContent)cd.deserializeScribeContent(buf, endpoint, contentType); 
    }
    
  }
}

