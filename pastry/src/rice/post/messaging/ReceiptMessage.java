package rice.post.messaging;

import java.security.*;

import rice.post.*;
import rice.pastry.*;
import rice.pastry.messaging.*;

/**
 * This message is broadcast to the sender of a NotificationMessage in
 * order to inform the sender that the message has been received.
 */
public class ReceiptMessage extends Message implements Serializable{
   private NotificationMessage message = null; 
  /**
   * Constructs a PresenceMessage
   *
   * @param message The notification message which this is a receipt for
   */
  public ReceiptMessage(NotificationMessage message) {
     this.message = message;
  }
    
  /**
   * Gets the NoticifcationMessage which this ReceiptMessage is a receipt
   * for.
   *
   * @return The message which this receipt is for.
   */
  public NotificationMessage getNotificationMessage() {
    return message;
  }
}

