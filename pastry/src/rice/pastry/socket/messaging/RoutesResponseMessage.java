
package rice.pastry.socket.messaging;

import java.io.*;

import rice.pastry.*;
import rice.pastry.leafset.*;
import rice.pastry.socket.*;

/**
* A response message to a RoutesRequestMessage, containing the remote
 * node's routes.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class RoutesResponseMessage extends SocketMessage {
  
  private SourceRoute[] routes;
  
  /**
  * Constructor
   *
   * @param leafset The leafset of the receiver of the RoutesRequestMessage.
   */
  public RoutesResponseMessage(SourceRoute[] routes) {
    this.routes = routes;
  }
  
  /**
    * Returns the leafset of the receiver.
   *
   * @return The LeafSet of the receiver node.
   */
  public SourceRoute[] getRoutes() {
    return routes;
  }
}
