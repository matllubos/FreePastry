package rice.email;

import rice.post.*;
import rice.post.log.*;
import rice.post.messaging.*;
import rice.post.storage.*;
import rice.email.log.*;
import rice.email.messaging.*;

/**
 * Represents a notion of a message in the POST system.  This class is designed 
 * to be a small representation of an Email, with pointers to all of the content.
 */
public class Email implements java.io.Serializable {

  private PostUserAddress sender;
  private PostEntityAddress[] recipients;
  private String subject;
  private transient EmailData body;
  private transient EmailData[] attachments;
  private EmailDataReference bodyRef;
  private EmailDataReference[] attachmentRefs;
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
   * Returns the  body of this message.  Should be text.
   *
   * @return The body of this email.
   */
  public EmailData getBody() throws StorageException {
    // if the body has not been fetched already, fetch it
    if (this.body == null) {
      body = (EmailData)storage.retrieveContentHash(bodyRef);
    }
    // return the body
    return this.body;
  }
     
  /**
   * Returns the attachments of this message.
   *
   * @return The attachments of this email.
   */
  public EmailData[] getAttachments() throws StorageException {
    // if the attachments have not been fetched already, fetch them
    if (this.attachments == null) {
      for (int i = 0; i < attachmentRefs.length; i++) {
	attachments[i] = (EmailData)storage.retrieveContentHash(attachmentRefs[i]);
      }
    }
    // return the attachments
    return this.attachments;
  }
  
  /**
   * Stores the content of the Email into PAST and 
   * saves the references to the content in the email.  
   * Should be called before the Email is sent over the wire.
   */
  protected void storeData() throws StorageException {   
    if (this.attachmentRefs == null) {
      attachmentRefs = new EmailDataReference[attachments.length];
      // insert the email attachments into PAST, store their references
      for (int i = 0; i < attachments.length; i++) {
	attachmentRefs[i] = (EmailDataReference)storage.storeContentHash(attachments[i]); 
      }
    }

    // if the body has not already been inserted into PAST
    // JM try replacing this with "if (bodyRef == null) { " for a laugh
    if (!(this.bodyRef instanceof EmailDataReference)) {
      // insert the email body into PAST, store the reference
      bodyRef = (EmailDataReference)storage.storeContentHash(body);
    }
  }
}
