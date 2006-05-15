
package rice.p2p.scribe.messaging;

import java.io.IOException;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.scribe.*;
import rice.p2p.scribe.rawserialization.ScribeContentDeserializer;

/**
 * @(#) UnsubscribeMessage.java
 *
 * The unsubscribe message.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class UnsubscribeMessage extends ScribeMessage {
  public static final short TYPE = 10;

  /**
   * Constructor which takes a unique integer Id
   *
   * @param id The unique id
   * @param source The source address
   * @param dest The destination address
   */
  public UnsubscribeMessage(NodeHandle source, Topic topic) {
    super(source, topic);
  }

  
  /***************** Raw Serialization ***************************************/
  public short getType() {
    return TYPE;
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0); // version
    super.serialize(buf);    
  }  

  public static UnsubscribeMessage build(InputBuffer buf, Endpoint endpoint) throws IOException {
    byte version = buf.readByte();
    switch(version) {
      case 0:
        return new UnsubscribeMessage(buf, endpoint);
      default:
        throw new IOException("Unknown Version: "+version);
    }
  }
  
  /**
   * Private because it should only be called from build(), if you need to extend this,
   * make sure to build a serializeHelper() like in AnycastMessage/SubscribeMessage, and properly handle the 
   * version number.
   */
  private UnsubscribeMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint);
  }
}

