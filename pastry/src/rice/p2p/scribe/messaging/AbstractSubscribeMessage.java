
package rice.p2p.scribe.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;

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

}

