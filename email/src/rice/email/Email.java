package rice.email;

/**
 * Represents a notion of a message in the POST system.  This class is designed 
 * to be a small representation of an Email, with pointers to all of the content.
 */
public class Email {

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
  public Email(EmailUserAddress sender, 
               EmailUserAddress[] recipientUsers, 
               EmailGroupAddress[] recipientGroups, 
               String subject, 
               EmailBody body, 
               EmailAttachment[] attachments) {
  }
    
  /**
   * Returns the sender of this message.
   *
   * @return The sender of this email.
   */
  public EmailUserAddress getSender() {
    return null;
  }
    
  /**
   * Returns the recipient users of this message.
   *
   * @return The recipient users of this email.
   */
  public EmailUserAddress[] getRecipientUsers() {
    return null;
  }
    
  /**
   * Returns the recipient users of this message.
   *
   * @return The recipient users of this email.
   */
  public EmailGroupAddress[] getRecipientGroups() {
    return null;
  }
     
  /**
   * Returns the subject of this message.
   *
   * @return The subject of this email.
   */
  public String getSubject() {
    return null;
  }
     
  /**
   * Returns the other body of this message.
   *
   * @return The body of this email.
   */
  public EmailBody getBody() {
    return null;
  }
     
  /**
   * Returns the attachments of this message.
   *
   * @return The attachments of this email.
   */
  public EmailAttachment[] getAttachments() {
    return null;
  }
}