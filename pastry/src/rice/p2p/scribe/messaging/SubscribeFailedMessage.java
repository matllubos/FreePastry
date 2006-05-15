
package rice.p2p.scribe.messaging;

import java.io.IOException;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.scribe.*;
import rice.p2p.scribe.rawserialization.ScribeContentDeserializer;

/**
 * @(#) SubscribeFailedMessage.java
 *
 * The ack for a subscribe message.
 *
 * @version $Id
 *
 * @author Alan Mislove
 */
public class SubscribeFailedMessage extends AbstractSubscribeMessage {
  public static final short TYPE = 4;

  /**
   * Constructor which takes a unique integer Id
   *
   * @param id The unique id
   * @param source The source address
   * @param dest The destination address
   */
  public SubscribeFailedMessage(NodeHandle source, Topic topic, int id) {
    super(source, topic, id);
  }

  /***************** Raw Serialization ***************************************/
  public short getType() {
    return TYPE; 
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0); // version
    
    super.serialize(buf);
  }
  
  public static SubscribeFailedMessage build(InputBuffer buf, Endpoint endpoint) throws IOException { 
    byte version = buf.readByte();
    switch(version) {
      case 0:
        return new SubscribeFailedMessage(buf, endpoint);
      default:
        throw new IOException("Unknown Version: "+version);
    }
  }
  
  /**
   * Private because it should only be called from build(), if you need to extend this,
   * make sure to build a serializeHelper() like in AnycastMessage/SubscribeMessage, and properly handle the 
   * version number.
   */
  private SubscribeFailedMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint); 
  }
  
}

