package rice.email.proxy.mailbox.postbox;

import rice.email.proxy.mail.MovingMessage;

import rice.email.proxy.mailbox.*;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import rice.Continuation;

import rice.email.EmailService;
import rice.email.Folder;

/**
* This class serves as the main "glue" code between foedus and
 * the POST-based email implementation.
 */
public class PostMailboxManager implements MailboxManager {

  // the local email service to use
  EmailService email;

  // the local mailbox
  PostMailbox mailbox;

  /**
  * Constructs a PostMailbox given an emailservice
   * to run off of.
   *
   * @param email The email service on the local pastry node.
   */
  
  public PostMailboxManager(EmailService email) {
    if (email == null)
      throw new IllegalArgumentException("EmailService cannot be null in PostMailbox.");

    this.email = email;
    this.mailbox = new PostMailbox(email);
  }

  public String getMailboxType()
  {
    return PostMailboxManager.class.getName();
  }

  public Mailbox getMailbox(String username) throws NoSuchMailboxException {
    return mailbox; 
  }

  public void createMailbox(String username) throws MailboxException {
  }

  public void destroyMailbox(String username) throws MailboxException {
  }
}
