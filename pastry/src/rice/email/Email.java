package rice.email;

import rice.post.*;

/**
 * Represents a notion of a message in the POST system.  This class is designed 
 * to be a small representation of an Email, with pointers to all of the content.
 */
public class Email implements java.io.Serializable {

    private PostUserAddress sender;
    private PostUserAddress[] recipientUsers;
    private PostGroupAddress[] recipientGroups;
    private String subject;
    private EmailDataReference body;
    private EmailDataReference[] attachments;
    
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
               PostUserAddress[] recipientUsers, 
               PostGroupAddress[] recipientGroups, 
               String subject, 
               EmailDataReference body, 
               EmailDataReference[] attachments) {
                
     this.sender = sender;
     this.recipientUsers = recipientUsers;
     this.recipientGroups = recipientGroups;
     this.subject = subject;
     this.body = body;
     this.attachments = attachments;
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
  public PostUserAddress[] getRecipientUsers() {
    return this.recipientUsers;
  }
    
  /**
   * Returns the recipient users of this message.
   *
   * @return The recipient users of this email.
   */
  public PostGroupAddress[] getRecipientGroups() {
    return this.recipientGroups;
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
   * Returns the body of this message.  Should be text.
   *
   * @return The body of this email.
   */
  public EmailDataReference getBody() {
    return this.body;
  }
     
  /**
   * Returns the attachments of this message.
   *
   * @return The attachments of this email.
   */
  public EmailDataReference[] getAttachments() {
    return this.attachments;
  }
}
