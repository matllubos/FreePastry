package rice.post;

/**
 * Represents a notion of a message in the POST system.  This class is designed 
 * to be a small representation of an Email, with pointers to all of the content.
 * Additionally, this class is intelligent in that it is able to encrypt, sign, 
 * decrypt, and verify itself.
 */
public class Email {

    // whether or not this email is in encrypted state
    private boolean encrypted;

    /**
     * Constructs an Email.
     *
     * @param sender The address of the sender of the mail.
     * @param recipient The addresses of the recipient of the mail.
     * @param copiesTo Other people included in this mailing.
     * @param subject The subject of the message.
     * @param body The body of the message.
     * @param attachments The attachments to the message (could be zero-length.)
     */
    public Email(EmailAddress sender, 
                 EmailAddress recipient, 
                 EmailAddress[] copiesTo, 
                 String subject, 
                 EmailBody body, 
                 EmailAttachment[] attachments) {
    }
    
  /**
   * Returns the sender of this message.
   *
   * @return The sender of this email.
   */
  public EmailAddress getSender() { 
  }
    
  /**
   * Returns the recipient of this message.
   *
   * @return The recipient of this email.
   */
  public EmailAddress getRecipient() { 
  }
     
  /**
   * Returns the other recipients of this message.
   *
   * @return The other recipients of this email.
   */      
  public EmailAddress[] getCopyTo() { 
  }
     
  /**
   * Returns the subject of this message.
   *
   * @return The subject of this email.
   */ 
  public String getSubject() { 
  }
     
  /**
   * Returns the other body of this message.
   *
   * @return The body of this email.
   */ 
  public EmailBody getBody() { 
  }
     
  /**
   * Returns the attachments of this message.
   *
   * @return The attachments of this email.
   */ 
  public EmailAttachment[] getAttachments() { 
  }
    
  /**
   * Encrypts this Email to the given public key and signs it with our private key.
   *
   * @param signWith The sender's private key, with which to sign the message
   * @param encryptWith The recipient's public key, with which to encrypt the message
   */
  public void signAndEncrypt(PrivateKey signWith, PublicKey encryptWith) throws CryptoException { 
  }
    
  /**
   * Decrypts this Email with our private key and verifies it with the sender's public key.
   *
   * @param decryptWith The recipient's private key, with which to decrypt the message
   * @param verifyWith The sender's public key, with which to verify the message   
   */
  public void decryptAndVerify(PrivateKey decryptWith, PublicKey verifyWith) throws CryptoException { 
  }
     
  /*
   * Returns whether or not this message is encrypted.
   */
  public boolean isEncrypted() { 
  }
}
