
package rice.pastry.wire.messaging.socket;

import java.io.*;

import rice.pastry.*;

/**
 * A response message to a NodeIdRequestMessage, containing the remote node's
 * nodeId.
 *
 * @version $Id: NodeIdResponseMessage.java,v 1.1 2002/08/28 02:34:59 amislove
 *      Exp $
 * @author Alan Mislove
 */
public class NodeIdResponseMessage extends SocketCommandMessage {

  private NodeId nid;

  /**
   * Constructor
   *
   * @param nid The nodeId of the receiver of the NodeIdRequestMessage.
   */
  public NodeIdResponseMessage(NodeId nid) {
    this.nid = nid;
  }

  /**
   * Returns the nodeId of the receiver.
   *
   * @return The NodeId of the receiver node.
   */
  public NodeId getNodeId() {
    return nid;
  }
}
