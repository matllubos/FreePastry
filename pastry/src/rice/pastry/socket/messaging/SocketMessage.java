
package rice.pastry.socket.messaging;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.socket.*;

import java.net.*;

/**
 * Class which represents an abstract control message
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public abstract class SocketMessage extends PRawMessage {
  
  /**
   * Constructor
   */
  public SocketMessage() {
    super(0);
  }
}
