package rice.post.messaging;

import java.security.*;

import rice.post.*;
import rice.pastry.*;

/**
 * This message is broadcast to the sender of a NotificationMessage in
 * order to inform the sender that the message has been received.
 */
public class DeliveryRequestMessage extends PostMessage {
    
  /**
   * Constructs a DeliveryRequestMessage
   *
   * @param sender The sender of this delivery request
   * @param destination The destination address to deliver the notification to
   * @param message The message to deliver 
   */
  public DeliveryRequestMessage(PostUserAddress sender, 
                                PostUserAddress destination, 
                                NotificationMessage message) {
  }
    
  /**
   * Gets the sender of this delivery request
   *
   * @return The address of the sender of this delivery request
   */
  public PostUserAddress getSender() {
    return null;
  }
    
  /**
   * Gets the destination of this notification
   *
   * @return The address of the destination of this notification
   */
  public PostUserAddress getDestination() {
    return null;
  }
    
  /**
   * Gets the NoticifcationMessage which this ReceiptMessage is a receipt
   * for.
   *
   * @return The message which this receipt is for.
   */
  public NotificationMessage getNotificationMessage() {
    return null;
  }
}

