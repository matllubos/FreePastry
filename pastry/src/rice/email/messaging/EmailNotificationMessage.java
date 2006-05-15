package rice.email.messaging;

import rice.email.*;
import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.rawserialization.*;
import rice.post.*;
import rice.post.messaging.*;
import rice.post.rawserialization.*;

import java.io.*;

/**
 * This class represents an notification in the email service that
 * a new email is available for the recipient of this email.
 */
public class EmailNotificationMessage extends NotificationMessage implements Raw, Serializable {
  public static final short TYPE = 15;
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
  
  /**
   * Returns the email which this notification is for.
   *
   * @return The Email contained in this notification
   */
  public Email getEmail() {
    return _email;
  }
  
  public EmailNotificationMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint);
    _email = new Email(buf, endpoint);
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    super.serialize(buf);
    
    _email.serialize(buf);
  }
  
  public short getType() {
    return TYPE; 
  }
}
