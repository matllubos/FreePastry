package rice.post.messaging;

import java.util.*;

import rice.post.*;

/**
 * This class represents a notification message which is in encrypted state.
 */
public class EncryptedNotificationMessage extends PostMessage {

  private byte[] data;

  /**
    * Constructs a NotificationMessage for the given Email.
   *
   * @param data The encrypted NotificationMessage
   */
  public EncryptedNotificationMessage(PostEntityAddress sender, byte[] data) {
    super(sender);
    this.data = data;
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
