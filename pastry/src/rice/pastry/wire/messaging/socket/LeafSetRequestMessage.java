
package rice.pastry.wire.messaging.socket;

import java.io.*;

import rice.pastry.*;
import rice.pastry.leafset.*;

/**
 * Message which represents a request to get the leafset from the remote node.
 *
 * @version $Id: LeafSetRequestMessage.java,v 1.1 2003/08/25 04:24:26 amislove
 *      Exp $
 * @author Alan Mislove
 */
public class LeafSetRequestMessage extends SocketCommandMessage {

  /**
   * Constructor
   */
  public LeafSetRequestMessage() {
  }
}
