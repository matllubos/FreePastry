package rice.email;

import java.security.*;
import java.util.HashSet;

import rice.Continuation;
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

    // inner classes
    private class ESRootFolderCont implements Continuation {

	Continuation resultHandler;

	ESRootFolderCont(Continuation c) {
	    this.resultHandler = c;
	}
	
	/**
	 * Called when a previously requested result is now availble.
	 *
	 * @param result The result of the command.
	 */
	public void receiveResult(Object result) {
	    Log emailLog = (Log) result;
	    
	    Folder f = new Folder(emailLog, _post);

	    this.resultHandler.receiveResult(f);
	}

	/**
	 * Called when an execption occured as a result of the
	 * previous command.
	 *
	 * @param result The exception which was caused.
	 */
	public void receiveException(Exception result) {
	    this.resultHandler.receiveException(result);
	}

    }
  
  // the name of the Inbox's log
  public static final String INBOX_NAME="Inbox";
    
  // the Emails Service's Post object
  Post _post;
  
  /** 
   * Constructor
   *
   * @param post The Post service to use
   */  
  public EmailService(Post post) {
    _post = post;
  }
  
  /**
   * @return the post object this serivce is using.
   */
  public Post getPost() {
    return _post;
  }

  /**
   * Sends the email to the recipient. The Email object has a notion
   * of who its recipients are.
   *
   * @param email The email to send
   * @param errorListener is the object that will be notified of
   * errors that occur during the send procedure.
   */
  public void sendMessage(Email email, Continuation errorListener) throws PostException {

    // store the Email's data before sending it
    StorageService storage = _post.getStorageService();
    email.setStorage(storage);
    email.storeData(errorListener);

    // send the notification messages to each of the recipients
    PostEntityAddress[] recipients = email.getRecipients();
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
   * @param c is the object notified of the result of the folder retrieval.
   */
  public void getRootFolder(Continuation c) {
    
      // find the Client Id for this client
      PostClientAddress pca = PostClientAddress.getAddress(this);
      
      // use the Id to fetch the root log
      PostLog mainLog = _post.getLog();
      LogReference emailLogRef = mainLog.getChildLog(pca);

      _post.getStorageService().retrieveSigned(emailLogRef, new ESRootFolderCont(c));
  }

  /**
   * This method is how the Post layer informs the EmailService
   * layer that there is an incoming notification of new email. 
   *
   * @param nm The incoming notification.
   */
  public void notificationReceived(NotificationMessage nm) {
    
    if(nm instanceof EmailNotificationMessage) {
      EmailNotificationMessage enm = (EmailNotificationMessage) nm;

      // notify the observers that an email has been received.
      this.notifyObservers(enm);
    } else {
      System.err.println("EmailService received unknown notification "
			 + nm +
			 " - dropping on floor.");
    }
  }    
}
