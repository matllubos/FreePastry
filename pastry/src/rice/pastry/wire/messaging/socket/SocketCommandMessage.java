
package rice.pastry.wire.messaging.socket;

import java.io.*;

/**
 * Class which represents a "command" message sent across the socket-based
 * pastry protocol.
 *
 * @version $Id: SocketCommandMessage.java,v 1.1 2002/08/13 18:09:57 amislove
 *      Exp $
 * @author Alan Mislove
 */
public abstract class SocketCommandMessage extends SocketMessage {

  /**
   * Constructor for SocketCommandMessage.
   */
  public SocketCommandMessage() {
    super();
  }

}
