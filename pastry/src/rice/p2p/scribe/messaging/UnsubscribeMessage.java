
package rice.p2p.scribe.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;

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

}

