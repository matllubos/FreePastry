package rice.post;

import java.security.*;
import rice.past.*;

/**
 * This class is created when a user wishes to use a specific PostService object.
 * This class keeps track of a user's state (including optimization such as caching
 * of the mailbox, etc), and translates calls to the PostService layer.
 */
public class PostClient implements PostServiceListener {
 
  /** 
   * Constructor
   *
   * @param service The PostService which this client runs on top of
   * @param emailAddr The user's address. 
   * @param keyPair The user's public and private keys.
   */
  public PostClient(PostService service, EmailAddress emailAddr, KeyPair keyPair) {
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
   * Sends a message to the Scribe group for this user informing all nodes
   * with pending notifications of this user's presence.
   */ 
  public void sendPresence() throws PostException {
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
   * This method is how the PostService layer informs the PostClient
   * layer that there is an incoming notification of new email. This
   * method will send a reciept back to the Scribe group.
   *
   * @param nm The incoming notification.
   */
  public void incomingNotification(NotificationMessage nm) {
  }
  
  /**
   * This method adds an object that will be notified of events that occur in this 
   * post client.
   * 
   * @param pcl is the object that will be notified.
   */
  public void addPostClientListener(PostClientListener pcl) {
    
  }
  
  /**
   * This method removes an object from the set of objects that are notified by
   * this post client of events that occur in it.
   * 
   * @param pcl is the object that will no longer be notified.
   */
  public void removePostClientListener(PostClientListener pcl) {
    
  }
}
