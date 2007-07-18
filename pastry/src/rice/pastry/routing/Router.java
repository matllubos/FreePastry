package rice.pastry.routing;

import java.util.Map;

import rice.pastry.transport.PMessageNotification;
import rice.pastry.transport.PMessageReceipt;

/**
 * Router is no longer just an application.  It is privilaged.
 * 
 * @author Jeff Hoye
 *
 */
public interface Router {

  void route(RouteMessage rm);

//  boolean routeMessage(RouteMessage rm);

}
