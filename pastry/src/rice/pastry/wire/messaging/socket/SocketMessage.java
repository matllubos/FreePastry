
package rice.pastry.wire.messaging.socket;

import java.io.*;

import rice.pastry.*;

/**
 * Class which abstracts out a message sent across the socket-based pastry
 * protocol.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public abstract class SocketMessage implements Serializable {

  /**
   * Constructor for SocketMessage.
   */
  public SocketMessage() {
  }

}
