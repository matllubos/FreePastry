package rice.email;

import java.util.*;

import rice.*;
import rice.Continuation.*;
import rice.email.log.*;
import rice.email.messaging.*;
import rice.post.*;
import rice.post.log.*;
import rice.post.messaging.*;
import rice.post.storage.*;

/**
 * Represents a notion of a message in the POST system. This class is designed
 * to be a small representation of an Email, with pointers to all of the
 * content.
 *
 * @version $Id: pretty.settings,v 1.2 2003/07/10 03:17:16 amislove Exp $
 * @author Joe Montgomery
 * @author Derek Ruths
 */
public class Email implements java.io.Serializable {

  PostUserAddress sender;
  PostEntityAddress[] recipients;
  EmailDataReference headersRef;
  EmailDataReference bodyRef;
  EmailDataReference[] attachmentRefs;
  private transient EmailData headers;
  private transient EmailData body;
  private transient EmailData[] attachments;
  private int attachmentCount;
  private transient StorageService storage;

  /**
   * Constructs an Email.
   *
   * @param sender The address of the sender of the mail.
   * @param recipients The recipients for this message
   * @param headers The RFC822 headers for this message
   * @param body The body of the message.
   * @param attachments The attachments to the message (could be zero-length.)
   */
  public Email(PostUserAddress sender,
               PostEntityAddress[] recipients,
               EmailData headers,
               EmailData body,
               EmailData[] attachments) {
    this.sender = sender;
    this.recipients = recipients;
    this.headers = headers;
    this.body = body;
    this.attachments = attachments;

    if (this.attachments == null) {
      this.attachments = new EmailData[0];
    }

    this.bodyRef = null;
    this.attachmentRefs = null;
    this.attachmentCount = attachments.length;
  }

  /**
   * Returns the sender of this message.
   *
   * @return The sender of this email.
   */
  public PostUserAddress getSender() {
    return this.sender;
  }

  /**
   * Returns the recipient users of this message.
   *
   * @return The recipient users of this email.
   */
  public PostEntityAddress[] getRecipients() {
    return this.recipients;
  }

  /**
   * Returns the headers of this message. Should be text.
   *
   * @param command The command to run once the headers are available
   */
  public void getHeaders(final Continuation command) {
    // if the body has not been fetched already, fetch it
    if ((this.headers == null) && (this.headersRef != null)) {

      // start the fetching process
      storage.retrieveContentHash(headersRef, new StandardContinuation(command) {
          public void receiveResult(Object o) {
            Email.this.headers = (EmailData) o;
            command.receiveResult(o);
          }
      });
    } else {
      command.receiveResult(this.headers);
    }
  }


  /**
   * Returns the body of this message. Should be text.
   *
   * @param command The command to run once the body is available
   */
  public void getBody(final Continuation command) {
    // if the body has not been fetched already, fetch it
    if ((this.body == null) && (this.bodyRef != null)) {

      // start the fetching process
      storage.retrieveContentHash(bodyRef, new StandardContinuation(command) {
          public void receiveResult(Object o) {
            Email.this.body = (EmailData) o;
            command.receiveResult(o);
          }
      });
    } else {
      command.receiveResult(this.body);
    }
  }

  /**
   * Returns the attachments of this message.
   *
   * @param command The command to run once the attachments is available
   */
  public void getAttachments(final Continuation command) {
    // if the attachments have not been fetched already, and there are refs to the attachments,
    // fetch the attachments
    if ((this.attachments == null) && (this.attachmentRefs.length > 0)) {
      
      // start the fetching process
      this.attachments = new EmailData[this.attachmentCount];

      // make a new command to store the returned attachment and start to fetch the next attachment.
      // Once the attachments have all been fetch, call the user's command
      storage.retrieveContentHash(attachmentRefs[0], new StandardContinuation(command) {
        private int i = 0;

        public void receiveResult(Object o) {
          // store the fetched attachment
          Email.this.attachments[i] = (EmailData) o;
          i++;

          // if there are more attachments, fetch the next one
          if (i < attachmentCount) {
            storage.retrieveContentHash(Email.this.attachmentRefs[i], this);
          } else {
            command.receiveResult(Email.this.attachments);
          }
        }
      });
    } else {
      if (this.attachments == null) {
        this.attachments = new EmailData[0];
      }

      command.receiveResult(this.attachments);
    }
  }

  /**
   * Determines equality on this email
   *
   * @param o The object to compare to
   * @return Whether or not this email is equal
   */
  public boolean equals(Object o) {
    if (!(o instanceof Email)) {
      return false;
    }

    Email email = (Email) o;

    return (sender.equals(email.sender) &&
            Arrays.equals(recipients, email.recipients) &&
            bodyRef.equals(email.bodyRef) &&
            headersRef.equals(email.headersRef) &&
            Arrays.equals(attachmentRefs, email.attachmentRefs));
  }

  /**
   * Returns a string representing this email
   *
   * @return A string representing this email
   */
  public String toString() {
    String result = "[Email from " + sender + " to " + recipients[0];
    for (int i = 1; i < recipients.length; i++) {
      result += ", " + recipients[i];
    }
    result += "]";
    
    return result;
  }

  /**
   * Sets the storage service for the email. I (JM) added this method so that
   * the EmailService can set the Email's storage whenever the email is sent or
   * received, which lets the EmailClient be effectively ignorant of the storage
   * service (which is good, since this Service is part of the POST layer).
   *
   * @param s the StorageService the email is to use
   */
  protected void setStorage(StorageService s) {
    storage = s;
  }

  /**
   * Stores the content of the Email into PAST and saves the references to the
   * content in the email. Should be called before the Email is sent over the
   * wire.
   *
   * @param command This command is called when the storage is done, with the
   *      Boolean value of the success of the operation, or an exception is
   *      passed to the command.
   */
  protected void storeData(final Continuation command) {
    if ((bodyRef == null) && (attachmentRefs == null)) {
      attachmentRefs = new EmailDataReference[attachmentCount];

      // build a continuation to store the data
      Continuation store = new Continuation() {
          private int i = 0;

          public void receiveResult(Object o) {
            try {
              if ((headersRef == null) && (headers != null)) {
                headersRef = (EmailDataReference) o;

                if (body != null) {
                  storage.storeContentHash(body, this);
                } else if (attachmentCount > 0) {
                  storage.storeContentHash(attachments[0], this);
                } else {
                  command.receiveResult(new Boolean(true));
                }
              } else if ((bodyRef == null) && (body != null)) {
                bodyRef = (EmailDataReference) o;

                if (attachmentCount > 0) {
                  storage.storeContentHash(attachments[0], this);
                } else {
                  command.receiveResult(new Boolean(true));
                }
              } else {
                attachmentRefs[i] = (EmailDataReference) o;
                i++;

                if (i < attachmentCount) {
                  storage.storeContentHash(attachments[i], this);
                } else {
                  command.receiveResult(new Boolean(true));
                }
              }
            } catch (ClassCastException e) {
              command.receiveException(new ClassCastException("Expected a EmailDataReference in storeData, got a " + o.getClass()));
            }
          }

          public void receiveException(Exception e) {
            command.receiveException(e);
          }
        };

      // store the body, and have the result go to the continuation
      if (headers != null) {
        storage.storeContentHash(headers, store);
      } else if (body != null) {
        storage.storeContentHash(body, store);
      } else if (attachmentCount > 0) {
        storage.storeContentHash(attachments[0], store);
      } else {
        command.receiveResult(new Boolean(true));
      }
    } else {
      command.receiveResult(new Boolean(true));
    }
  }
}































