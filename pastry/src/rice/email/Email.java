package rice.email;

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
  public void getBody(Continuation command) {
    // if the body has not been fetched already, fetch it
    if ((this.body == null) && (this.bodyRef != null)) { 
      // make a new command to store the returned body and 
      // then return the body once it has been stored
      EmailGetBodyTask preCommand = new EmailGetBodyTask(command);
      // start the fetching process
      storage.retrieveContentHash(bodyRef, preCommand);
    } else {
      command.receiveResult(this.body);
    }
  }
     
  /**
   * Returns the attachments of this message.
   *
   * @return The attachments of this email.
   */
  public void getAttachments(Continuation command) {
    // if the attachments have not been fetched already, and there are refs to the attachments, 
    // fetch the attachments
    if ((this.attachments == null) && (this.attachmentRefs != null)) {
      // make a new command to store the returned attachment and start to fetch the next attachment.
      // Once the attachments have all been fetch, call the user's command
      EmailGetAttachmentsTask preCommand = new EmailGetAttachmentsTask(0, command);
      // start the fetching process
      storage.retrieveContentHash(attachmentRefs[0], preCommand);
    } else {
      command.receiveResult(this.attachments);
    }
  }

  /**
   * Stores the content of the Email into PAST and 
   * saves the references to the content in the email.  
   * Should be called before the Email is sent over the wire.
   *
   * // JM errorListener?  Wrong, wrong, wrong. Hrumph.
   * @param errorListener os the object notified of any errors in
   * storage.  If the storage process is successful, then this
   * listener will not be notified of anything.
   */
  protected void storeData(Continuation errorListener) {   
    // if the body has not already been inserted into PAST
    if (this.bodyRef == null) {
      EmailStoreDataTask command = new EmailStoreDataTask(EmailStoreDataTask.BODY, errorListener);
      storage.storeContentHash(body, command);
    }
    else if ((this.attachmentRefs == null) && (attachments != null) && (attachments.length > 0)) {      
      // make a new task to store the email's contents (body and attachments)
      EmailStoreDataTask command = new EmailStoreDataTask(EmailStoreDataTask.ATTACHMENT, errorListener);
      // begin storing the body, execute the rest of the task once this is complete
      storage.storeContentHash(attachments[0], command);
    }
  }

  /**
   * This class is used to fetch an email body, and then store the result.  
   * To return the result to the user, the user's given command is called once
   * the body has been stored.
   */
  protected class EmailGetBodyTask implements Continuation {
    private Continuation _command;
    
    /**
     * Constructs a EmailGetBodyTask given a user-command.
     */
    public EmailGetBodyTask(Continuation command) {
      _command = command;
    }

    /**
     * Starts the processing of this task.
     */
    public void start() {
      // JM I don't believe anything needs to be done here
    }

    /**
     * Stores the result, and then calls the user's command.
     */
    public void receiveResult(Object o) {
      // store the fetched body
      try {
	body = (EmailData)o;      
      } catch (Exception e) {
	System.out.println("The email body was fetched, but had problems " + o);
      }
      // pass the result along to the caller
      _command.receiveResult(o);
    }

    /**
     * Simply prints out an error message.
     */  
    public void receiveException(Exception e) {
      System.out.println("Exception " + e + "  occured while trying to fetch an email body");
    }
  }

  /**
   * This class is used to fetch an email attachment, and store the result,
   * and then fetch the next attachment.  Once each of the attachments have
   * been fetched and stored, calls the user's given command.
   */
  protected class EmailGetAttachmentsTask implements Continuation {
    private int _index;
    private Continuation _command;
    
    /**
     * Constructs a EmailGetAttachmentsTask given a user-command.
     */
    public EmailGetAttachmentsTask(int i, Continuation command) {
      _index = i;
      _command = command;
    }

    /**
     * Starts the processing of this task.
     */
    public void start() {
      // JM I don't believe anything needs to be done here
    }

    /**
     * Stores the result, and then fetches the next attachment.  If there are no more
     * attachments, calls the user's provided command.
     */
    public void receiveResult(Object o) {
      // store the fetched attachment
      try {
	attachments[_index] = (EmailData)o;  
	// if there are more attachments, fetch the next one
	if (_index < attachmentRefs.length) {
	  _index = _index + 1;
	  EmailGetAttachmentsTask preCommand = new EmailGetAttachmentsTask(_index, _command);
	  storage.retrieveContentHash(attachmentRefs[_index], preCommand);
	// otherwise pass the result along to the caller
	} else {
	  _command.receiveResult(attachments);
	}    
      } catch (Exception e) {
	System.out.println("The email attachment " + _index + " was fetched, but had problems. " + o);
      }      
    }

    /**
     * Simply prints out an error message.
     */  
    public void receiveException(Exception e) {
      System.out.println("Exception " + e + "  occured while trying to fetch email attachment " + _index);
    }
  }

  /**
   * Carries out the remaining work of storing the data of an email (its body and attacments).
   * The first call stores the email, the remaining calls store the attachments one by one.
   */
  protected class EmailStoreDataTask implements Continuation {
    // looks like it can be changed, but it can't. Don't change it. Please.
    protected static final int BODY = -1;
    protected static final int ATTACHMENT = 0;

    // the current position in the continuation
    private int _index;
    private Continuation _resultListener;
      
    /**
     * Constructs a EmailStoreDataTask.
     */
    public EmailStoreDataTask(int index, Continuation resultListener) {
      System.out.println("Created new EmailStoreDataTask");
      _index = index;
      _resultListener = resultListener;
    }

    /**
     * Starts the processing of this task.
     */
    public void start() {
      // JM I don't believe anything needs to be done here
    }

    /**
     * The email body has been stored by the time the first result is finished, so go on to
     * storing the attachments.  Once each of the attachments is stored, the method is done.
     */
    public void receiveResult(Object o) {
      System.out.println("StoreDataTask received result, now storing data at index " + _index);
      System.out.println("received result was " + o); 
      // save the returned reference
      if (_index == BODY) {
        bodyRef = (EmailDataReference)o;
        _resultListener.receiveResult(new Boolean(true));
      } else {
        attachmentRefs[_index] = (EmailDataReference)o;
      }

      // store the next data item in the email
      _index = _index + 1;      
      if ((attachments != null) && (_index < attachments.length)) {
	EmailStoreDataTask command = new EmailStoreDataTask(_index, _resultListener);
	storage.storeContentHash(attachments[_index], command);	
      } 
      // if there are no more data items, pass an empty result to the given continuation
      else {
	_resultListener.receiveResult(o);
      }
    }

    /**
     * Simply prints out an error message.
     */  
    public void receiveException(Exception e) {
      System.out.println("Exception " + e +
			 "  occured while trying to store email body or attachment " + _index);

      if(_resultListener != null) {
	  _resultListener.receiveException(e);
      }
    }
  }
}
