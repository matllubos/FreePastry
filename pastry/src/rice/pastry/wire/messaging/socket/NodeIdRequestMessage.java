
package rice.pastry.wire.messaging.socket;

import java.io.*;

import rice.pastry.*;

/**
 * Message which represents a request to get a node Id from the remote node.
 * This is necessary because even though a client might know the address of a
 * remote node, it does not know it's node Id. Therefore, the first message that
 * is sent across the wire is the NodeIdRequestMessage.
 *
 * @version $Id: NodeIdRequestMessage.java,v 1.1 2002/08/28 02:34:59 amislove
 *      Exp $
 * @author Alan Mislove
 */
public class NodeIdRequestMessage extends SocketCommandMessage {

  /**
   * Constructor
   */
  public NodeIdRequestMessage() {
  }
}
