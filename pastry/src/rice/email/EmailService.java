package rice.email;

import java.security.*;
import java.util.HashSet;

import rice.Continuation;
import rice.email.log.*;
import rice.email.messaging.*;
import rice.post.*;
import rice.post.log.*;
import rice.post.messaging.*;
import rice.post.storage.*;

/**
 * This class serves as the entry point into the email service written on top of
 * Post.<br>
 * <br>
 * The EmailService uses the observer pattern to notify other objects of newly
 * received emails. The event generated will contain an {@link Email} object as
 * its argument.
 *
 * @version   $Id: pretty.settings,v 1.1 2003/07/10 02:18:11 amislove Exp $
 * @author    Joe Montgomery
 * @author    Derek Ruths
 */
public class EmailService extends PostClient {

  // the Emails Service's Post object
  Post _post;

  // the root folder
  private Folder folder;

  // the inbox folder
  private Folder inbox;

  // the keypair used to encrypt the log
  private KeyPair keyPair;

  // the name of the Inbox's log
  /**
   * DESCRIBE THE FIELD
   */
  public final static String INBOX_NAME = "INBOX";

  /**
   * Constructor
   *
   * @param post     The Post service to use
   * @param keyPair  DESCRIBE THE PARAMETER
   */
  public EmailService(Post post, KeyPair keyPair) {
    _post = post;

    this.keyPair = keyPair;

    post.addClient(this);

    Continuation listener =
      new Continuation() {
        public void receiveResult(Object o) {
        }

        public void receiveException(Exception e) {
          System.out.println("Fetching of root folder failed with " + e);
        }
      };

    getRootFolder(listener);
  }

  /**
   * @return   the post object this serivce is using.
   */
  public Post getPost() {
    return _post;
  }

  /**
   * Returns the Log for ePost's root folder.
   *
   * @param command  is the object notified of the result of the folder
   *      retrieval.
   */
  public void getRootFolder(final Continuation command) {
    if (folder != null) {
      command.receiveResult(folder);
      return;
    }

    // find the Client Id for this client
    final PostClientAddress pca = PostClientAddress.getAddress(this);

    final Continuation getLog =
      new Continuation() {
        public void receiveResult(Object o) {
          PostLog mainLog = (PostLog) o;

          if (mainLog == null) {
            command.receiveException(new Exception("PostLog was null - aborting."));
          } else {
            // use the main root log to try to fetch the ePost root log
            LogReference emailLogRef = mainLog.getChildLog(pca);

            // if ePost does not yet have a root log, add one
            // JM is it correct to add the new log at the same location as the previous one?
            if (emailLogRef == null) {

              // fetch the folder, and then create an "inbox" folder
              final Continuation fetch =
                new Continuation() {
                  public void receiveResult(Object o) {
                    try {
                      if (folder == null) {
                        folder = new Folder((EmailLog) o, _post, keyPair);

                        folder.createChildFolder(INBOX_NAME, this);
                      } else {
                        inbox = (Folder) o;
                        command.receiveResult(folder);
                      }
                    } catch (ClassCastException e) {
                      command.receiveException(new ClassCastException("In getLog, expected a Log, got a " + o.getClass()));
                    }
                  }

                  public void receiveException(Exception e) {
                    command.receiveException(e);
                  }
                };

              // build a continuation to receive the result of the log storing
              Continuation store =
                new Continuation() {
                  public void receiveResult(Object o) {

                    try {
                      LogReference emailLogRef = (LogReference) o;
                      _post.getStorageService().retrieveSigned(emailLogRef, fetch);
                    } catch (ClassCastException e) {
                      command.receiveException(new ClassCastException("In getLog, expected a Log, got a " + o.getClass()));
                    }
                  }

                  public void receiveException(Exception e) {
                    command.receiveException(e);
                  }
                };

              EmailLog emailRootLog = new EmailLog(pca, _post.getStorageService().getRandomNodeId(), _post, keyPair);
              mainLog.addChildLog(emailRootLog, store);
            } else {

              // simply fetch the existing folder
              Continuation fetch =
                new Continuation() {
                  public void receiveResult(Object o) {

                    try {
                      if (folder == null) {
                        folder = new Folder((EmailLog) o, _post, keyPair);
                        folder.getChildFolder(INBOX_NAME, this);
                      } else {
                        inbox = (Folder) o;
                        command.receiveResult(folder);
                      }
                    } catch (ClassCastException e) {
                      command.receiveException(new ClassCastException("In getLog, expected a Log, got a " + o.getClass()));
                    }
                  }

                  public void receiveException(Exception e) {
                    command.receiveException(e);
                  }
                };

              _post.getStorageService().retrieveSigned(emailLogRef, fetch);
            }
          }
        }

        public void receiveException(Exception e) {
          command.receiveException(e);
        }
      };

    _post.getPostLog(getLog);
  }

  /**
   * Sends the email to the recipient. The Email object has a notion of who its
   * recipients are.
   *
   * @param email              The email to send
   * @param command            is the object that will be notified of errors
   *      that occur during the send procedure, or Boolean(true) if it succeeds.
   * @exception PostException  DESCRIBE THE EXCEPTION
   */
  public void sendMessage(final Email email, final Continuation command) throws PostException {
    // build a continuation to send the email once it's properly stored
    Continuation send =
      new Continuation() {
        public void receiveResult(Object o) {
          if (o.equals(new Boolean(true))) {
            // send the notification messages to each of the recipients
            PostEntityAddress[] recipients = email.getRecipients();

            for (int i = 0; i < recipients.length; i++) {
              // create the Notification message, notification should go to ePost
              EmailNotificationMessage msg = new EmailNotificationMessage(email, recipients[i], EmailService.this);

              // use POST to send the Delivery message
              _post.sendNotification(msg);
            }

            // pass any result from the Store Data (there should be none) to the handler.
            command.receiveResult(new Boolean(true));
          } else {
            command.receiveException(new Exception("Storing of Email did not succeed: " + o));
          }
        }

        public void receiveException(Exception e) {
          command.receiveException(e);
        }
      };

    // get the storage service, and let the Email itself know about the Service
    email.setStorage(_post.getStorageService());

    // start storing the data
    email.storeData(send);
  }

  /**
   * This method is how the Post layer informs the EmailService layer that there
   * is an incoming notification of new email.
   *
   * @param nm  The incoming notification.
   */
  public void notificationReceived(NotificationMessage nm) {

    if (nm instanceof EmailNotificationMessage) {
      EmailNotificationMessage enm = (EmailNotificationMessage) nm;
      enm.getEmail().setStorage(_post.getStorageService());

      System.out.println("Received email from " + enm.getEmail().getSender());

      // notify the observers that an email has been received.
      this.setChanged();
      this.notifyObservers(enm);

      Continuation listener =
        new Continuation() {
          public void receiveResult(Object o) {
          }

          public void receiveException(Exception e) {
            System.out.println("Insertion of new message failed with " + e);
          }
        };

      if (inbox != null) {
        inbox.addMessage(enm.getEmail(), listener);
      } else {
        System.out.println("Recieved message, but was unable to insert due to null inbox...");
      }
    } else {
      System.err.println("EmailService received unknown notification "
        + nm +
        " - dropping on floor.");
    }
  }
}

