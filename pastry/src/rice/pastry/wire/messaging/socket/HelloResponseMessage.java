
package rice.pastry.wire.messaging.socket;

import java.net.*;
import rice.pastry.*;

import rice.pastry.wire.*;

/**
 * Class which represents a greeting response in the socket-based pastry
 * protocol. It contains the InetSocketAddress and nodeId of the
 * socket-accepting node.
 *
 * @version $Id: HelloResponseMessage.java,v 1.1 2002/09/13 03:23:56 amislove
 *      Exp $
 * @author Alan Mislove
 */
public class HelloResponseMessage extends SocketCommandMessage {

  private InetSocketAddress address;

  private NodeId nodeId;

  private NodeId dest;

  /**
   * Constructor
   *
   * @param pn DESCRIBE THE PARAMETER
   * @param dest DESCRIBE THE PARAMETER
   */
  public HelloResponseMessage(WirePastryNode pn, NodeId dest) {
    super();
    address = ((WireNodeHandle) pn.getLocalHandle()).getAddress();
    nodeId = pn.getNodeId();
    this.dest = dest;
  }

  /**
   * Returns the address of the source of this message.
   *
   * @return The address of the source of the message.
   */
  public InetSocketAddress getAddress() {
    return address;
  }

  /**
   * Returns the NodeId of the source
   *
   * @return The NodeId of the source of this message.
   */
  public NodeId getNodeId() {
    return nodeId;
  }

  /**
   * Returns the NodeId of the source
   *
   * @return The NodeId of the source of this message.
   */
  public NodeId getDestination() {
    return dest;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @return DESCRIBE THE RETURN VALUE
   */
  public String toString() {
    return "HelloResponseMessage from " + address + "(" + nodeId + ") to " + dest;
  }
}
