package rice.email;

import java.util.*;

import rice.*;
import rice.post.*;
import rice.post.log.*;
import rice.post.messaging.*;
import rice.post.storage.*;
import rice.email.log.*;
import rice.email.messaging.*;

/**
 * Represents a notion of a message in the POST system.  This class is designed 
 * to be a small representation of an Email, with pointers to all of the content.
 * @author Joe Montgomery
 * @author Derek Ruths
 */
public class Email implements java.io.Serializable {

  PostUserAddress sender;
  PostEntityAddress[] recipients;
  String subject;
  EmailDataReference bodyRef;
  EmailDataReference[] attachmentRefs;
  private transient EmailData body;
  private transient EmailData[] attachments;
  private int attachmentCount;
  private transient StorageService storage;
  
  /**
   * Constructs an Email.
   *
   * @param sender The address of the sender of the mail.
   * @param recipientUsers The addresses of the recipient users of the mail.
   * @param recipientGroups The addresses of the recipient groups of the mail.
   * @param subject The subject of the message.
   * @param body The body of the message.
   * @param attachments The attachments to the message (could be zero-length.)
   */
  public Email(PostUserAddress sender, 
               PostEntityAddress[] recipients, 
               String subject, 
               EmailData body, 
               EmailData[] attachments) {
    
    this.sender = sender;
    this.recipients = recipients;
    this.subject = subject;
    this.body = body;
    this.attachments = attachments;
    this.bodyRef = null;
    this.attachmentRefs = null;

    if (attachments != null)
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
   * Sets the storage service for the email.  I (JM) added this method
   * so that the EmailService can set the Email's storage whenever the
   * email is sent or received, which lets the EmailClient be
   * effectively ignorant of the storage service (which is good, since
   * this Service is part of the POST layer).
   *
   * @param s the StorageService the email is to use
   */
  protected void setStorage(StorageService s) {
    storage = s;
  }
  
  /**
   * Returns the subject of this message.
   *
   * @return The subject of this email.
   */
  public String getSubject() {
    return this.subject;
  }

  /**
   * Sets the equality of this email.
   *
   */
  public boolean equals(Object o) {
    if (! (o instanceof Email))
      return false;

    Email email = (Email) o;

    return (sender.equals(email.sender) &&
            Arrays.equals(recipients, email.recipients) &&
            subject.equals(email.subject) &&
            bodyRef.equals(email.bodyRef) &&
            Arrays.equals(attachmentRefs, email.attachmentRefs));
  }
     
  /**
   * Returns the  body of this message.  Should be text.
   *
   * @return The body of this email.
   */
  public void getBody(final Continuation command) {
    // if the body has not been fetched already, fetch it
    if ((this.body == null) && (this.bodyRef != null)) {
      
      // build a Continuation to receive the body, and process it
      Continuation receiveBody = new Continuation() {
        public void receiveResult(Object o) {
          try {
            body = (EmailData) o;
            command.receiveResult(body);
          } catch (ClassCastException e) {
            command.receiveException(new ClassCastException("Expected a EmailData, got a " + o.getClass()));
          }          
        }

        public void receiveException(Exception e) {
          command.receiveException(e);
        }
      };
      
      // start the fetching process
      storage.retrieveContentHash(bodyRef, command);
    } else {
      command.receiveResult(this.body);
    }
  }
     
  /**
   * Returns the attachments of this message.
   *
   * @return The attachments of this email.
   */
  public void getAttachments(final Continuation command) {
    // if the attachments have not been fetched already, and there are refs to the attachments, 
    // fetch the attachments
    if ((this.attachments == null) && (this.attachmentRefs != null)) {
      // start the fetching process
      this.attachments = new EmailData[this.attachmentCount];

      // build a Continuation to receive the attachments, and process it
      Continuation receiveAttachments = new Continuation() {
        private int i=0;
        
        public void receiveResult(Object o) {
          try {
            // store the fetched attachment
            attachments[i] = (EmailData) o;
            i++;
            
            // if there are more attachments, fetch the next one
            if (i < attachmentCount) {
              storage.retrieveContentHash(attachmentRefs[i], this);
            } else {
              command.receiveResult(attachments);
            }
          } catch (ClassCastException e) {
            command.receiveException(new ClassCastException("Expected a EmailData in getAttachments, got a " + o.getClass()));
          }
        }

        public void receiveException(Exception e) {
          command.receiveException(e);
        }
      };
      
      // make a new command to store the returned attachment and start to fetch the next attachment.
      // Once the attachments have all been fetch, call the user's command
      storage.retrieveContentHash(attachmentRefs[0], receiveAttachments);
    } else {
      command.receiveResult(this.attachments);
    }
  }

  /**
   * Stores the content of the Email into PAST and 
   * saves the references to the content in the email.  
   * Should be called before the Email is sent over the wire.
   *
   * @param command This command is called when the storage is done,
   * with the Boolean value of the success of the operation, or an
   * exception is passed to the command.
   */
  protected void storeData(final Continuation command) {
    if ((bodyRef == null) && (attachmentRefs == null)) {
      attachmentRefs = new EmailDataReference[attachmentCount];

      // build a continuation to store the data
      Continuation store = new Continuation() {
        private int i=0;
        
        public void receiveResult(Object o) {
          try {
            if (bodyRef == null) {
              bodyRef = (EmailDataReference) o;

              if (attachments != null) {
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
      storage.storeContentHash(body, store);
    } else {
      command.receiveResult(new Boolean(true));
    }
  }

  public String toString() {
    String result = "From:\t" + sender + "\n";
    result += "To:\t" + recipients[0];
    for (int i=1; i < recipients.length; i++) {
      result += "\n" + recipients[i];
    }

    result += "\nSubject:\t" + subject;
    
    return result;
  }
}
