
package rice.pastry.wire.messaging.socket;

import java.io.*;

import rice.pastry.*;

/**
 * Class which represents a wrapper message sent across the socket-based
 * protocol - it has another message inside of it.
 *
 * @version $Id: SocketTransportMessage.java,v 1.3 2003/06/10 18:29:03 amislove
 *      Exp $
 * @author Alan Mislove
 */
public class SocketTransportMessage extends SocketMessage {

  private Object o;

  private NodeId destination;

  /**
   * Constructs a new message wrapping another object.
   *
   * @param o The object to be wrapped.
   * @param destination DESCRIBE THE PARAMETER
   */
  public SocketTransportMessage(Object o, NodeId destination) {
    super();
    this.o = o;
    this.destination = destination;
  }

  /**
   * Returns the wrapped message
   *
   * @return The internally wrapped message.
   */
  public Object getObject() {
    return o;
  }

  /**
   * Returns the destination node id
   *
   * @return The destination node id
   */
  public NodeId getDestination() {
    return destination;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @return DESCRIBE THE RETURN VALUE
   */
  public String toString() {
    return "{" + o + "}";
  }

}
