package rice.email;

import java.security.*;

import rice.post.*;
import rice.post.messaging.*;
import rice.post.email.messaging.*;

/**
 * This class serves as the entry point into the email service written
 * on top of Post.
 */
public class EmailService extends PostClient {
 
  /** 
   * Constructor
   *
   * @param post The Post service to use
   */
  public EmailService(Post post) {
  }

  /**
   * Sends the email to the recipient. The Email object has a notion
   * of who its recipients are.
   *
   * @param email The email to send
   */
  public void sendMessage(Email email) throws PostException {
  }
  
  /**
   * Method which appends an email to the user's inbox.
   *
   * @param email The email to insert.
   */
  public void addMessage(Email email) throws PostException {
  }
  
  /**
   * Moves a message into a given folder.
   *
   * @param email The email to move.
   * @param destFolder The destination folder for the message.
   */
  public void moveMessage(Email email, String destFolder) throws PostException {
  } 
  
  /** 
   * Deletes a message from the user's mailbox.
   *
   * @param email The email to delete.
   */
  public void deleteMessage(Email email) throws PostException {
  }
  
  /**
   * Creates a new folder in the user's mailbox.
   *
   * @param name The name of the folder to create.
   */
  public void createFolder(String name) throws PostException {
  }
  
  /**
   * Deletes a folder from the user's mailbox.
   *
   * @param name The name of the folder to delete.
   */
  public void deleteFolder(String name) throws PostException {
  }
  
  /**
   * This method is how the Post layer informs the EmailService
   * layer that there is an incoming notification of new email. 
   *
   * @param nm The incoming notification.
   */
  public void notificationReceived(NotificationMessage nm) {
  }
  
  /**
   * This method adds an object that will be notified of events that occur in this 
   * post client.
   * 
   * @param esl is the object that will be notified.
   */
  public void addEmailServiceListener(EmailServiceListener esl) {
  }
  
  /**
   * This method removes an object from the set of objects that are notified by
   * this email service of events that occur in it.
   * 
   * @param esl is the object that will no longer be notified.
   */
  public void removeEmailServiceListener(EmailServiceListener esl) {
  }
}
