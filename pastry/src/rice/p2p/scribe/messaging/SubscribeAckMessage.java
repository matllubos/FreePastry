
package rice.p2p.scribe.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;

/**
 * @(#) SubscribeAckMessage.java
 *
 * The ack for a subscribe message.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class SubscribeAckMessage extends AbstractSubscribeMessage {

  /**
   * The contained path to the root
   */
  protected Id[] pathToRoot;

  /**
   * Constructor which takes a unique integer Id
   *
   * @param id The unique id
   * @param source The source address
   * @param dest The destination address
   */
  public SubscribeAckMessage(NodeHandle source, Topic topic, Id[] pathToRoot, int id) {
    super(source, topic, id);

    this.pathToRoot = pathToRoot;
  }

  /**
   * Returns the path to the root for the node receiving
   * this message
   *
   * @return The new path to the root for the node receiving this
   * message
   */
  public Id[] getPathToRoot() {
    return pathToRoot;
  }
  
  /**
   * Returns a String representation of this ack
   *
   * @return A String
   */
  public String toString() {
    return "SubscribeAckMessage " + topic + " ID: " + id; 
  }

}

