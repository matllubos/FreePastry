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
public class DeliveryLookupResponseMessage extends PostMessage {

  private DeliveryRequestMessage message;

  /**
    * Constructs a DeliveryLookupResponseMessage
   *
   * @param sender The sender of this delivery request
   * @param message The requested message
   */
  public DeliveryLookupResponseMessage(PostEntityAddress sender,
                                       DeliveryRequestMessage message) {
    super(sender);
    this.message = message;
  }

  /**
    * Gets the random locaiton of this drm
   *
   * @return The locaiton
   */
  public DeliveryRequestMessage getEncryptedMessage() {
    return message;
  }
}

