
package rice.p2p.scribe.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;

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

}

