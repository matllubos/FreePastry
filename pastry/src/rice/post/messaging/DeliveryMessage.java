package rice.post.messaging;

import java.security.*;
import rice.post.messaging.*;
import rice.post.*;
import rice.pastry.*;
import rice.pastry.messaging.*;
import java.io.*;

/**
 * This class wraps an EncrypedNotificationMessage and is
 * used after the receipt of a PresenceMessage.
 */
public class DeliveryMessage extends PostMessage {

  private SignedPostMessage message;
  private NodeId location;

  /**
    * Constructs a DeliveryMessage
   *
   * @param sender The sender of this delivery 
   * @param message The message to deliver, in encrypted state
   */
  public DeliveryMessage(PostEntityAddress sender,
                         NodeId location,
                         SignedPostMessage message) {
    super(sender);
    this.location = location;
    this.message = message;
  }

  /**
    * Gets the location of the user.
   *
   * @return The location in the Pastry ring of the user.
   */
  public NodeId getLocation() {
    return location;
  }

  /**
   * Gets the EncryptedNotificationMessage which this is a Request for.
   * for.
   *
   * @return The internal message, in encrypted state
   */
  public SignedPostMessage getEncryptedMessage() {
    return message;
  }
}

