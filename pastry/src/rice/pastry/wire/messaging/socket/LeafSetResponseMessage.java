
package rice.pastry.wire.messaging.socket;

import java.io.*;

import rice.pastry.*;
import rice.pastry.leafset.*;

/**
 * A response message to a LeafSetRequestMessage, containing the remote node's
 * leafset.
 *
 * @version $Id: LeafSetResponseMessage.java,v 1.1 2003/08/25 04:24:26 amislove
 *      Exp $
 * @author Alan Mislove
 */
public class LeafSetResponseMessage extends SocketCommandMessage {

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
