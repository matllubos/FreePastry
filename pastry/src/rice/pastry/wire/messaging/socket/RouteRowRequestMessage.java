
package rice.pastry.wire.messaging.socket;

import java.io.*;

import rice.pastry.*;
import rice.pastry.leafset.*;

/**
 * Message which represents a request to get the leafset from the remote node.
 *
 * @version $Id: RouteRowRequestMessage.java,v 1.1 2003/08/25 04:24:26 amislove
 *      Exp $
 * @author Alan Mislove
 */
public class RouteRowRequestMessage extends SocketCommandMessage {

  /**
   * DESCRIBE THE FIELD
   */
  protected int row;

  /**
   * Constructor
   *
   * @param row DESCRIBE THE PARAMETER
   */
  public RouteRowRequestMessage(int row) {
    this.row = row;
  }

  /**
   * Returns the row which this a request for
   *
   * @return The Row value
   */
  public int getRow() {
    return row;
  }
}
