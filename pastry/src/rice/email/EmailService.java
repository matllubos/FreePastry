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
 * on top of Post.<br>
 * <br>
 * The EmailService uses the observer pattern to notify other objects
 * of newly received emails.  The event generated will contain an
 * {@link Email} object as its argument.
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
   *
   * @param email The email to send
   */
  public void sendMessage(Email email, EmailData body, EmailData[] attachments) throws PostException {
    // store the Email's data before sending it
    StorageService storage = _post.getStorageService();
    email.setStorage(storage);
    email.storeData();

    // send the notification messages to each of the recipients
    EmailUserAddress[] recipients = (EmailUserAddress[])email.getRecipientUsers();
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
   * JM this needs some error checking for when the given folder is not found
   *
   * @param folderName the folder to fetch the Log for
   * @return the fetched Log
   */
  public Folder getRootFolder() {
    // find the Client Id for this client
    PostClientAddress pca = PostClientAddress.getAddress(this);
    // use the Id to fetch the root log
    //JM needs new method here:
    //Log mainLog = _post.getLog(pca);
    Log mainLog = _post.getLog();
    
    return new Folder(mainLog, _post.getStorageService());    
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

  /**
   * This method adds an object that will be notified of events that occur in this 
   * post client.
   * 
   * @param esl is the object that will be notified.
   */
  public void addEmailServiceListener(EmailServiceListener esl) {
    this.emailServiceListeners.add(esl);
  }
  
  /**
   * This method removes an object from the set of objects that are notified by
   * this email service of events that occur in it.
   * 
   * @param esl is the object that will no longer be notified.
   */
  public void removeEmailServiceListener(EmailServiceListener esl) {
    this.emailServiceListeners.remove(esl);
  }

}
