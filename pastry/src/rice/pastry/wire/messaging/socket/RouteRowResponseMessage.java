
package rice.pastry.wire.messaging.socket;

import java.io.*;

import rice.pastry.*;
import rice.pastry.leafset.*;
import rice.pastry.routing.*;

/**
 * A response message to a RouteRowRequestMessage, containing the remote node's
 * routerow.
 *
 * @version $Id: RouteRowResponseMessage.java,v 1.1 2003/08/25 04:24:27 amislove
 *      Exp $
 * @author Alan Mislove
 */
public class RouteRowResponseMessage extends SocketCommandMessage {

  private RouteSet[] set;

  /**
   * Constructor
   *
   * @param set DESCRIBE THE PARAMETER
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
