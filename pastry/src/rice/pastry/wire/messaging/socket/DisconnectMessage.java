
package rice.pastry.wire.messaging.socket;

/**
 * Class which represents a disconnect notice in the Socket-based pastry wire
 * protocol.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class DisconnectMessage extends SocketCommandMessage {

  /**
   * Constructor
   */
  public DisconnectMessage() {
    super();
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @return DESCRIBE THE RETURN VALUE
   */
  public String toString() {
    return "DisconnectMessage";
  }
}
