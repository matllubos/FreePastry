
package rice.p2p.scribe.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;

/**
 * @(#) PublishRequestMessage.java
 *
 * The publish message.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class PublishRequestMessage extends ScribeMessage {

  // the content of this message
  protected ScribeContent content;

  /**
  * Constructor which takes a unique integer Id
   *
   * @param id The unique id
   * @param source The source address
   * @param dest The destination address
   */
  public PublishRequestMessage(NodeHandle source, Topic topic, ScribeContent content) {
    super(source, topic);

    this.content = content;
  }

  /**
   * Returns the content
   *
   * @return The content
   */
  public ScribeContent getContent() {
    return content;
  }

}

