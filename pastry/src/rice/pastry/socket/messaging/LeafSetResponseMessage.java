
package rice.pastry.socket.messaging;

import java.io.*;

import rice.pastry.*;
import rice.pastry.leafset.*;

/**
* A response message to a LeafSetRequestMessage, containing the remote
* node's leafset.
*
* @version $Id$
*
* @author Alan Mislove
*/
public class LeafSetResponseMessage extends SocketMessage {

  private LeafSet leafset;

  /**
  * Constructor
  *
  * @param leafset The leafset of the receiver of the LeafSetRequestMessage.
  */
  public LeafSetResponseMessage(LeafSet leafset) {
    this.leafset = leafset;
  }

  /**
    * Returns the leafset of the receiver.
    *
    * @return The LeafSet of the receiver node.
    */
  public LeafSet getLeafSet() {
    return leafset;
  }
}
