
package rice.pastry.socket.messaging;

import java.io.*;

import rice.pastry.*;

/**
 * Message which represents a request to get a node Id from the remote node.
 * This is necessary because even though a client might know the address of a
 * remote node, it does not know it's node Id.  Therefore, the first message
 * that is sent across the wire is the NodeIdRequestMessage.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class NodeIdRequestMessage extends SocketMessage {

  /**
   * Constructor
   *
   * @param nodeId The nodeId of the node requesting.
   */
  public NodeIdRequestMessage() {
  }
}
