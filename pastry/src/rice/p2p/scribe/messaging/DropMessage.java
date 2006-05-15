
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
 * The drop message, which tells a child that it's parent can no longer
 * support it.  Note that this does not necessarily mean that the parent
 * has failed.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class DropMessage extends ScribeMessage {
  public static final short TYPE = 6;

  /**
   * Constructor which takes a unique integer Id
   *
   * @param id The unique id
   * @param source The source address
   * @param dest The destination address
   */
  public DropMessage(NodeHandle source, Topic topic) {
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
  
  public static DropMessage build(InputBuffer buf, Endpoint endpoint) throws IOException {
    byte version = buf.readByte();
    switch(version) {
      case 0:
        return new DropMessage(buf, endpoint);        
      default:
        throw new IOException("Unknown Version: "+version);
    }
  }
  
  /**
   * Private because it should only be called from build(), if you need to extend this,
   * make sure to build a serializeHelper() like in AnycastMessage/SubscribeMessage, and properly handle the 
   * version number.
   */
  private DropMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint); 
  }
  
}

