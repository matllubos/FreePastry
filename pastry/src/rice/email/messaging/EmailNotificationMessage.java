package rice.email.messaging;

import rice.email.*;
import rice.post.*;
import rice.post.messaging.*;

import java.io.*;

/**
 * This class represents an notification in the email service that
 * a new email is available for the recipient of this email.
 */
public class EmailNotificationMessage extends NotificationMessage {
  private Email _email;
    
  /**
   * Constructs a EmailNotificationMessage for the given Email.
   *
   * @param email The email that is available
   * @param recipient the PostUserAddress to recieve the Email
   * @param service the EmailService to use to send the message
   */
  public EmailNotificationMessage(Email email, PostEntityAddress recipient, EmailService service) {
    super(service.getAddress(), email.getSender(), recipient);
    _email = email;
  }

  //public NotificationMessage(PostClientAddress clientAddress, PostUserAddress sender, PostEntityAddress destination) {
  
  /**
   * Returns the email which this notification is for.
   *
   * @return The Email contained in this notification
   */
  public Email getEmail() {
    return _email;
  }

    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
	ois.defaultReadObject();

	if (getClientAddress() == null) {
	    System.out.println("MONKEYS IN THE CODE!");
	}
    }
}
