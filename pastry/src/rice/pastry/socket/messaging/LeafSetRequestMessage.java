
package rice.pastry.socket.messaging;

import java.io.*;

import rice.pastry.*;
import rice.pastry.leafset.*;

/**
* Message which represents a request to get the leafset from the remote node.
*
* @version $Id$
*
* @author Alan Mislove
*/
public class LeafSetRequestMessage extends SocketMessage {

  /**
  * Constructor
  *
  * @param nodeId The nodeId of the node requesting.
  */
  public LeafSetRequestMessage() {
  }
}
