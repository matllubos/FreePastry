package rice.post.messaging;

import java.security.*;

import rice.post.*;
import rice.pastry.*;

/**
 * This message is broadcast to the sender of a NotificationMessage in
 * order to inform the sender that the message has been received.
 */
public class DeliveryRequestMessage extends PostMessage {
  
  private PostUserAddress sender      = null;
  private PostUserAddress destination = null;
  private NotificationMessage message = null;    

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
     this.sender = sender;
     this.destination = destination;
     this.message = message;

  }
    
  /**
   * Gets the sender of this delivery request
   *
   * @return The address of the sender of this delivery request
   */
  public PostUserAddress getSender() {
    return sender;
  }
    
  /**
   * Gets the destination of this notification
   *
   * @return The address of the destination of this notification
   */
  public PostUserAddress getDestination() {
    return destination;
  }
    
  /**
   * Gets the NoticifcationMessage which this is a Request for. 
   * for.
   *
   * @return The message which this receipt is for.
   */
  public NotificationMessage getNotificationMessage() {
    return message;
  }
}

