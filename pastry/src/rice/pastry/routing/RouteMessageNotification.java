package rice.pastry.routing;

public interface RouteMessageNotification {

  void sendFailed(RouteMessage message, Exception e);

  void sendSuccess(RouteMessage message);

}
