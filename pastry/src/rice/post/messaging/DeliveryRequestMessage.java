package rice.post.messaging;

import java.security.*;
import rice.post.messaging.*;
import rice.post.*;
import rice.pastry.*;
import rice.pastry.messaging.*;
import java.io.*;

/**
 * This message is broadcast to the sender of a NotificationMessage in
 * order to inform the sender that the message has been received.
 */
public class DeliveryRequestMessage extends PostMessage {
  
  private PostUserAddress destination;
  private EncryptedNotificationMessage message;    

  /**
   * Constructs a DeliveryRequestMessage
   *
   * @param sender The sender of this delivery request
   * @param destination The destination address to deliver the notification to
   * @param message The message to deliver, in encrypted state
   */
  public DeliveryRequestMessage(PostEntityAddress sender, 
                                PostUserAddress destination, 
                                EncryptedNotificationMessage message) {
    super(sender);
    this.destination = destination;
    this.message = message;
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
   * Gets the EncryptedNotificationMessage which this is a Request for. 
   * for.
   *
   * @return The internal message, in encrypted state
   */
  public EncryptedNotificationMessage getEncryptedMessage() {
    return message;
  }
}

