
package rice.pastry.socket.messaging;

import java.io.*;

import rice.pastry.*;
import rice.pastry.leafset.*;
import rice.pastry.routing.*;

/**
* A response message to a RouteRowRequestMessage, containing the remote
* node's routerow.
*
* @version $Id$
*
* @author Alan Mislove
*/
public class RouteRowResponseMessage extends SocketMessage {

  private RouteSet[] set;

  /**
  * Constructor
  *
  * @param leafset The leafset of the receiver of the RouteRowRequestMessage.
  */
  public RouteRowResponseMessage(RouteSet[] set) {
    this.set = set;
  }

  /**
    * Returns the routeset of the receiver.
    *
    * @return The RouteSet of the receiver node.
    */
  public RouteSet[] getRouteRow() {
    return set;
  }
}
