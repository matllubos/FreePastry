
package rice.pastry.wire.messaging.socket;

import java.net.*;
import rice.pastry.*;

import rice.pastry.wire.*;

/**
 * Class which represents a greeting in the socket-based pastry protocol. It
 * contains the InetSocketAddress and nodeId of the socket-initiating node.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class HelloMessage extends SocketCommandMessage {

  private InetSocketAddress address;

  private NodeId nodeId;

  private NodeId dest;

  /**
   * Constructor
   *
   * @param pn DESCRIBE THE PARAMETER
   * @param dest DESCRIBE THE PARAMETER
   */
  public HelloMessage(WirePastryNode pn, NodeId dest) {
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
   * Returns the appropriate response for this HelloMessage
   *
   * @param pn The local pastry node
   * @return A response message that should be sent to sender
   */
  public HelloResponseMessage getResponse(WirePastryNode pn) {
    return new HelloResponseMessage(pn, nodeId);
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @return DESCRIBE THE RETURN VALUE
   */
  public String toString() {
    return "HelloMessage from " + address + "(" + nodeId + ") to " + dest;
  }
}
