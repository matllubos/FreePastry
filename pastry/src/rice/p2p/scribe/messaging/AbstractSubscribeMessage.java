
package rice.p2p.scribe.messaging;

import java.io.IOException;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.scribe.*;
import rice.p2p.scribe.rawserialization.ScribeContentDeserializer;

/**
 * @(#) AbstractSubscribeMessage.java
 *
 * The ack for a subscribe message.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public abstract class AbstractSubscribeMessage extends ScribeMessage {

  /**
  * The id of this subscribe message
   */
  protected int id;

  /**
  * Constructor which takes a unique integer Id
   *
   * @param id The unique id
   * @param source The source address
   * @param dest The destination address
   */
  public AbstractSubscribeMessage(NodeHandle source, Topic topic, int id) {
    super(source, topic);

    this.id = id;
  }

  /**
    * Returns this subscribe lost message's id
   *
   * @return The id of this subscribe lost message
   */
  public int getId() {
    return id;
  }
  
  /**
   * Protected because it should only be called from an extending class, to get version
   * numbers correct.
   */
  protected AbstractSubscribeMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint);
    id = buf.readInt();    
  }

  public void serialize(OutputBuffer buf) throws IOException {
    super.serialize(buf);
    buf.writeInt(id);
  }  
}

