package rice.post.messaging;

import java.util.*;

import rice.post.*;

/**
 * This class represents a notification message which is in encrypted state.
 */
public class EncryptedNotificationMessage extends PostMessage {

  private byte[] key;
  
  private byte[] data;
  
  private PostEntityAddress destination;

  /**
    * Constructs a NotificationMessage for the given Email.
   *
   * @param key The encrypted key
   * @param data The encrypted NotificationMessage
   */
  public EncryptedNotificationMessage(PostEntityAddress sender, PostEntityAddress destination, byte[] key, byte[] data) {
    super(sender);
    this.data = data;
    this.key = key;
    this.destination = destination;
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
    * Returns the encrypted key of the NotificationMessage
   *
   * @return The encrypted key.
   */
  public byte[] getKey() {
    return key;
  }

  /**
   * Returns the ciphertext of the NotificationMessage
   *
   * @return The ciphertext.
   */
  public byte[] getData() {
    return data;
  }

  public boolean equals(Object o) {
    if (! (o instanceof EncryptedNotificationMessage))
      return false;

    return Arrays.equals(data, ((EncryptedNotificationMessage) o).getData());
  }

}
