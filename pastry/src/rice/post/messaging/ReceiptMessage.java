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
  
  private SignedPostMessage message;
  
  /**
   * Constructs a PresenceMessage
   *
   * @param message The notification message which this is a receipt for
   */
  public ReceiptMessage(PostEntityAddress sender, SignedPostMessage message) {
    super(sender);
    this.message = message;
  }
    
  /**
   * Gets the SignedPostMessage which this ReceiptMessage is a receipt
   * for.
   *
   * @return The message which this receipt is for.
   */
  public SignedPostMessage getEncryptedMessage() {
    return message;
  }
}

