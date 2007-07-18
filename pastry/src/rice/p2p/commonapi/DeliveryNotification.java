package rice.p2p.commonapi;

/**
 * Notified when the message is sent/failed.
 * Implemented by the user, can be included as a parameter in endpont.route()
 * 
 * @author Jeff Hoye
 *
 */
public interface DeliveryNotification {
  /**
   * Layer specific callback.
   * 
   * @param msg the message that is being acknowledged.
   */
  void sent(MessageReceipt msg);
  
  /**
   * Notification that the message can't be sent.
   * @param msg the message that can't be sent.
   * @param reason the reason it can't be sent (layer specific)
   */
  void sendFailed(MessageReceipt msg, Exception reason);

}
