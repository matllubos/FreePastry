package rice.pastry.standard;

import java.io.IOException;

import rice.pastry.routing.RouteMessage;

public class TooManyRouteAttempts extends IOException {

  public TooManyRouteAttempts(RouteMessage rm, int max_retries) {
    super("Too many attempts to route the message "+rm);
  }

}
