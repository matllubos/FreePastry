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
 * @author Joe Montgomery
 * @author Derek Ruths
 */
public class EmailService extends PostClient {

  // inner classes
  private class ESRootFolderCont implements Continuation {

    Continuation resultHandler;

    ESRootFolderCont(Continuation c) {
      System.out.println("Starting a new ESRootFolderCont");
      this.resultHandler = c;
    }

    /**
      * Called when a previously requested result is now availble.
     *
     * @param result The result of the command.
     */
    public void receiveResult(Object result) {
      System.out.println("ESRootFolderCont received a result.");
      System.out.println("Result is: " + (Log)result);
      Log emailLog = (Log) result;
      emailLog.setPost(_post);

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

  /**
  * Used to add a email root folder when one does not previously exist.
   * Calls ESRootFolderCont to returns the actual email root Folder rather
   * than a ref to it.
   */
  private class ESAddRootFolderCont implements Continuation {
    Continuation resultHandler;
    ESAddRootFolderCont(Continuation c) {
      System.out.println("Starting a new ESAddRootFolderCont");
      this.resultHandler = c;
    }

    /**
      * Called when a previously requested result is now available.
     *
     * @param result The result of the command.
     */
    public void receiveResult(Object result) {
      System.out.println("ESAddRootFolderCont received a result.");
      System.out.println("Result is: " + ((LogReference)result));
      LogReference emailLogRef = (LogReference)result;
      StorageService storage = _post.getStorageService();
      storage.retrieveSigned(emailLogRef, new ESRootFolderCont(resultHandler));
    }

    /**
      * Called when an execption occured as a result of the previous command.
     *
     * @param result The exception which was caused.
     */
    public void receiveException(Exception result) {
      System.out.println("ESAddRootFolderCont received an exception.");
      this.resultHandler.receiveException(result);
    }
  }

  /**
  * Used to add a email root folder when one does not previously exist.
   * Calls ESRootFolderCont to returns the actual email root Folder rather
   * than a ref to it.
   */
  private class ESSendMessageCont implements Continuation {
    Continuation resultHandler;
    Email email;
    EmailService emailService;

    ESSendMessageCont(Email e, EmailService es, Continuation c) {
      System.out.println("Starting a new ESSendMessageCont");
      this.email = e;
      this.emailService = es;
      this.resultHandler = c;
    }

    /**
      * Called when a previously requested result is now available.
     *
     * @param result The result of the command.
     */
    public void receiveResult(Object result) {
      System.out.println("ESSendMessageCont received a result.");
      System.out.println("Result is: " + result);

      // send the notification messages to each of the recipients
      PostEntityAddress[] recipients = email.getRecipients();
      EmailNotificationMessage msg;

      for (int i = 0; i < recipients.length; i++) {
        // create the Notification message, notification should go to ePost
        msg = new EmailNotificationMessage(email, recipients[i], this.emailService);

        // use POST to send the Delivery message
        _post.sendNotification(msg);
      }
      // pass any result from the Store Data (there should be none) to the handler.
      this.resultHandler.receiveResult(result);
    }

    /**
      * Called when an execption occured as a result of the previous command.
     *
     * @param result The exception which was caused.
     */
    public void receiveException(Exception result) {
      System.out.println("ESSendMessageCont received an exception.");
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

    post.addClient(this);
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

    // get the storage service, and let the Email itself know about the Service
    StorageService storage = _post.getStorageService();
    email.setStorage(storage);
    // make a continuation to handle the notification to the recipients
    Continuation preCommand = new ESSendMessageCont(email, this, errorListener);
    // start storing the data
    email.storeData(preCommand);
  }

  /**
    * Returns the Log for ePost's root folder.
   *
   * @param c is the object notified of the result of the folder retrieval.
   */
  public void getRootFolder(Continuation c) {
    System.out.println("Starting to get root Folder");

    // find the Client Id for this client
    PostClientAddress pca = PostClientAddress.getAddress(this);

    // use the Id to fetch the root log
    PostLog mainLog = _post.getLog();
    // use the main root log to try to fetch the ePost root log
    LogReference emailLogRef = mainLog.getChildLog(pca);

    System.out.println("Fetched the initial emailLogRef");

    // if ePost does not yet have a root log, add one
    // JM is it correct to add the new log at the same location as the previous one?
    if (emailLogRef == null) {
      System.out.println("Email Root Log did not exist, adding one");
      StorageService storage = _post.getStorageService();
      Log emailRootLog = new Log(pca, storage.getRandomNodeId(), _post);
      mainLog.addChildLog(emailRootLog, new ESAddRootFolderCont(c));
    }
    // otherwise fetch and return the ePost root log
    else {
      System.out.println("Fetching the Email Root Log");
      StorageService storage = _post.getStorageService();
      storage.retrieveSigned(emailLogRef, new ESRootFolderCont(c));
    }
  }

  /**
    * This method is how the Post layer informs the EmailService
   * layer that there is an incoming notification of new email.
   *
   * @param nm The incoming notification.
   */
  public void notificationReceived(NotificationMessage nm) {
    System.out.println("Received notification message " + nm);

    if(nm instanceof EmailNotificationMessage) {
      EmailNotificationMessage enm = (EmailNotificationMessage) nm;
      enm.getEmail().setStorage(_post.getStorageService());

      // notify the observers that an email has been received.
      this.setChanged();
      this.notifyObservers(enm);
    } else {
      System.err.println("EmailService received unknown notification "
                         + nm +
                         " - dropping on floor.");
    }
  }

  private class SendEmailTask implements Continuation {

    private Email email;
    private Continuation command;

    public SendEmailTask(Email email, Continuation command) {
      this.email = email;
      this.command = command;
    }

    public void start() {

      // store the Email's data before sending it
      StorageService storage = _post.getStorageService();
      email.setStorage(storage);
      email.storeData(this);
    }

    public void receiveResult(Object o) {

      // send the notification messages to each of the recipients
      PostEntityAddress[] recipients = email.getRecipients();
      EmailNotificationMessage msg;

      for (int i = 0; i < recipients.length; i++) {
        // create the Notification message, notification should go to ePost
        msg = new EmailNotificationMessage(email, recipients[i], EmailService.this);

        // use POST to send the Delivery message
        _post.sendNotification(msg);
      }

      if (command != null) {
        command.receiveResult(new Boolean(true));
      }
    }

    public void receiveException(Exception e) {
      command.receiveException(e);
    }
  }

}
