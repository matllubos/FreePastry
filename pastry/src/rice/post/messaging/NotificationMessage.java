package rice.post.messaging;

/**
 * Message serving as notification that the given email is
 * ready for delivery.
 */
public class NotificationMessage extends PostMessage {

  /**
   * Constructs a NotificationMessage for the given Email.
   */
  public NotificationMessage(Email email) {
  }

  /**
   * Gets the Email out of the message.
   */
  public Email getEmail() {
  }
}
