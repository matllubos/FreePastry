package rice.post.email.messaging;

import rice.post.email.*;
import rice.post.messaging.*;

/**
 * This class represents an notification in the email service that
 * a new email is available for the recipient of this email.
 */
public class EmailNotificationMessage extends NotificationMessage {

  /**
   * Constructs a EmailNotificationMessage for the given Email.
   *
   * @param email The email that is available
   */
  public EmailNotificationMessage(Email email, EmailService service) {
    super(service.getAddress());
  }
  
  /**
   * Returns the email which this notification is for.
   *
   * @return The Email contained in this notification
   */
  public Email getEmail() {
    return null;
  }
}
