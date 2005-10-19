package rice.post.messaging;

import java.security.*;
import java.io.*;

import rice.post.*;
import rice.post.messaging.*;

import rice.p2p.commonapi.*;

/**
 * This message is broadcast to the sender of a NotificationMessage in
 * order to inform the sender that the message has been received.
 */
public class ReceiptMessage extends PostMessage {
  
  private SignedPostMessage message;
  private Id id;
  
  /**
   * Constructs a PresenceMessage
   *
   * @param message The notification message which this is a receipt for
   */
  public ReceiptMessage(PostEntityAddress sender, Id id, SignedPostMessage message) {
    super(sender);
    this.id = id;
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

  /**
   * Gets the random locaiton of this drm
   *
   * @return The locaiton
   */
  public Id getId() {
    return id;
  }
}

