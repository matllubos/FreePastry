
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
public class RouteRowRequestMessage extends SocketMessage {

  protected int row;
  
  /**
  * Constructor
  *
  * @param nodeId The nodeId of the node requesting.
  */
  public RouteRowRequestMessage(int row) {
    this.row = row;
  }

  /**
   * Returns the row which this a request for
   */
  public int getRow() {
    return row;
  }
}
