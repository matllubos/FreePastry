package rice.post.messaging;
import rice.post.messaging.*;
import java.security.*;
import java.io.*;
import rice.post.*;
import rice.pastry.*;
import rice.pastry.messaging.*;

/**
 * This message is broadcast to the sender of a NotificationMessage in
 * order to inform the sender that the message has been received.
 */
public class ReceiptMessage extends PostMessage {
  
  private EncryptedNotificationMessage message;
  
  /**
   * Constructs a PresenceMessage
   *
   * @param message The notification message which this is a receipt for
   */
  public ReceiptMessage(PostEntityAddress sender, EncryptedNotificationMessage message) {
    super(sender);
    this.message = message;
  }
    
  /**
   * Gets the EncryptedNotificationMessage which this ReceiptMessage is a receipt
   * for.
   *
   * @return The message which this receipt is for.
   */
  public EncryptedNotificationMessage getEncryptedNotificationMessage() {
    return message;
  }
}

