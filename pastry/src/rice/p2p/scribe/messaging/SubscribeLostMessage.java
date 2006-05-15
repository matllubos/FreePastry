
package rice.p2p.scribe.messaging;

import java.io.IOException;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.scribe.*;
import rice.p2p.scribe.rawserialization.ScribeContentDeserializer;

/**
 * @(#) SubscribeLostMessage.java
 *
 * The ack for a subscribe message.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class SubscribeLostMessage extends AbstractSubscribeMessage {
  public static final short TYPE = 5;

  /**
   * Constructor which takes a unique integer Id
   *
   * @param id The unique id
   * @param source The source address
   * @param dest The destination address
   */
  public SubscribeLostMessage(NodeHandle source, Topic topic, int id) {
    super(source, topic, id);
  }

  /***************** Raw Serialization ***************************************/
  public short getType() {
    return TYPE; 
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    throw new RuntimeException("serialize() not supported in SubscribeLostMessage"); 
  }  
}

