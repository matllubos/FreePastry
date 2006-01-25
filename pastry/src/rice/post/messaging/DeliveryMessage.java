package rice.post.messaging;

import java.io.*;
import java.security.*;

import rice.post.messaging.*;
import rice.post.*;

import rice.p2p.commonapi.*;

/**
 * This class wraps an EncrypedNotificationMessage and is
 * used after the receipt of a PresenceMessage.
 */
public class DeliveryMessage extends PostMessage {
  private static final long serialVersionUID = -863725686248756000L;

  private SignedPostMessage message;
  private Id id;
  private PostEntityAddress destination;

  /**
    * Constructs a DeliveryMessage
   *
   * @param sender The sender of this delivery 
   * @param message The message to deliver, in encrypted state
   */
  public DeliveryMessage(PostEntityAddress sender,
                         PostEntityAddress destination,
                         Id id,
                         SignedPostMessage message) {
    super(sender);
    this.destination = destination;
    this.message = message;
    this.id = id;
  }
  
  /**
   * Returns the destination of this message.
   *
   * @return The destination
   */
  public final PostEntityAddress getDestination() {
    return destination;
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

  public Id getId() {
    return id;
  }

  public void setId(Id id) {
    this.id = id;
  }
}

