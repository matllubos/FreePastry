package rice.email;

import java.security.*;
import java.util.HashSet;

import rice.post.*;
import rice.post.log.*;
import rice.post.messaging.*;
import rice.post.storage.*;
import rice.email.log.*;
import rice.email.messaging.*;


/**
 * This class serves as the entry point into the email service written
 * on top of Post.
 */
public class EmailService extends PostClient {
  // the name for ePOST's root log
  public static final String EPOST_NAME="ePOST";

  // the name of the Inbox's log
  public static final String INBOX_NAME="Inbox";
  
  // the Emails Service's Post object
  Post _post = null;
  
  private HashSet emailServiceListeners = new HashSet(); 
  
  /** 
   * Constructor
   *
   * @param post The Post service to use
   */
  public EmailService(Post post) {
    _post = null;
  }

  /**
   * Sends the email to the recipient. The Email object has a notion
   * of who its recipients are.
   * JM Could use the optimization to
   * first check to see if the user is online?  Actually, that should
   * probably be in POST.
   * JM Again, not sure if we should perform the recipient expansion here or
   * in POST.
   * JM How to manage the email data/references? Thick and thin Email
   * client, or a mongrel?
   *
   * @param email The email to send
   */
  public void sendMessage(Email email, EmailData body, EmailData[] attachments) throws PostException {
    StorageService storage = _post.getStorageService();
    // insert the email body into PAST
    storage.storeContentHash(body);
    // insert the email attachments into PAST
    for (int i = 0; i < attachments.length; i++) {
      storage.storeContentHash(attachments[i]); 
    }

    // send the notification messages to each of the recipients
    EmailUserAddress[] recipients = email.getRecipientUsers();
    EmailNotificationMessage msg;
    for (int i = 0; i < recipients.length; i++) {
      // create the Notification message, notification should go to ePost
      msg = new EmailNotificationMessage(email, recipients[i], this);
      
      // use POST to send the Delivery message
      _post.sendNotification(msg);
    }
  }

  /**
   * Returns the Log for ePost's root folder.
   * JM this and getFolderLog need some error checking for when
   * the given folder is not found
   *
   * @param folderName the folder to fetch the Log for
   * @return the fetched Log
   */
  private Log getRootFolderLog() {
    Log log = _post.getLog();
    StorageService storage = _post.getStorageService();
    return (Log)storage.retrieveSigned(log.getChildLog(EPOST_NAME));
  }

  /**
   * Returns the Log for the given folder name.
   * 
   * @param folderName the folder to fetch the Log for
   * @return the fetched Log
   */
  private Log getFolderLog(String folderName) {
    Log log = getRootFolderLog(); 
    StorageService storage = _post.getStorageService();
    return (Log)storage.retrieveSigned(log.getChildLog(folderName));
  }

  /**
   * Given a Log for a Folder, builds up the Folder and returns it.
   * 
   * @param the Log to build from
   * @return the resulting Folder
   */
  private Folder makeFolder(Log log) {
    return null;
  }
  
  /**
   * Method which appends an email to the user's inbox.
   *
   * @param email The email to insert.
   */
  public void addMessage(Email email) throws PostException {
    addMessage(email, INBOX_NAME);
  }

  /**
   * Method which appends an email to the specified Folder.
   *
   * @param email The email to insert.
   */
  public void addMessage(Email email, String folder) throws PostException {
    Log log = getFolderLog(folder);
    log.addLogEntry(new InsertMailLogEntry(email));
  }

  /** 
   * Deletes a message from the user's mailbox.
   *
   * @param email The email to delete.
   */
  public void deleteMessage(Email email, String folder) throws PostException {
    Log log = getFolderLog(folder);
    log.addLogEntry(new DeleteMailLogEntry(email));
  }
  
  /**
   * Moves a message into a given folder.
   *
   * @param email The email to move.
   * @param srcFolder The source folder for the message.
   * @param destFolder The destination folder for the message.
   */
  public void moveMessage(Email email, String srcFolder, String destFolder) throws PostException {
    addMessage(email, destFolder);
    deleteMessage(email, srcFolder);
  }    
  
  /**
   * Creates a new folder in the user's mailbox.
   *
   * @param name The name of the folder to create.
   */
  public void createFolder(String name) throws PostException {
    Log log = getRootFolderLog();
    log.addChildLog(new Log(name, _post.getNodeId()));
  }
  
  /**
   * Deletes a folder from the user's mailbox.
   *
   * @param name The name of the folder to delete.
   */
  public void deleteFolder(String name) throws PostException {
    Log log = getRootFolderLog();
    log.removeChildLog(name);
  }
  
  /**
   * This method is how the Post layer informs the EmailService
   * layer that there is an incoming notification of new email. 
   *
   * @param nm The incoming notification.
   */
  public void notificationReceived(NotificationMessage nm) {
    
    if(nm instanceof EmailNotificationMessage) {
      EmailNotificationMessage enm = (EmailNotificationMessage) enm;

      // notify the observers that an email has been received.
      this.notifyObservers(enm);
    }
  }
}
