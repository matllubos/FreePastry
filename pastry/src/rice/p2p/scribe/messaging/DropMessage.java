
package rice.p2p.scribe.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;

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

}

