package rice.email;

import java.io.*;
import java.security.*;
import java.util.*;

import rice.*;
import rice.Continuation.*;
import rice.pastry.*;
import rice.post.storage.*;

/**
 * Represents a part of an email with headers
 *
 * @author Alan Mislove
 */
public class EmailMessagePart extends EmailContentPart {

  /**
   * The data representing the haeders (transient as it is stored).
   */
  protected transient EmailData headers;

  /**
   * A reference to the headers of this email part
   */
  protected EmailDataReference headersReference;

  /**
   * The content of this part
   */
  public EmailContentPart content;

  /**
   * The local storage service
   */
  protected transient StorageService storage;

  /**
   * Constructor. Takes in a emailData representing the headers and
   * a EmailContentPart representing the content
   *
   * @param headers The headers of this part
   * @param content The content of this part
   */
  public EmailMessagePart(EmailData headers, EmailContentPart content) {
    this.headers = headers;
    this.content = content;
  }

  /**
   * Method which sets this part's storage service
   *
   * @param storage The local storage service
   */
  public void setStorage(StorageService storage) {
    this.storage = storage;

    content.setStorage(storage);
  }
  

  /**
   * Returns the headers of this EmailPart to the continuation
   *
   * @param commmand The command to run once the result has been
   *   obtained
   */
  public void getHeaders(Continuation command) {
    if (headers != null) {
      command.receiveResult(headers);
      return;
    }

    storage.retrieveContentHash(headersReference, new StandardContinuation(command) {
      public void receiveResult(Object o) {
        headers = (EmailData) o;

        parent.receiveResult(headers);
      }
    }); 
  }

  /**
   * Returns the content of this part
   *
   * @return The content of this part
   */
  public void getContent(Continuation command) {
    command.receiveResult(content);
  }

  /**
   * Method which writes this part's content out to disk
   * and retrieves a reference to it.
   *
   * @param command The command to run once the data has been stored, and
   *   is returned the success or failure of this command
   */
  public void storeData(Continuation command) {
    if (headersReference != null) {
      command.receiveResult(new Boolean(true));
      return;
    }

    storage.storeContentHash(headers, new StandardContinuation(command) {
      public void receiveResult(Object o) {
        headersReference = (EmailDataReference) o;

        content.storeData(parent);
      }
    });
  }

  /**
   * Returns whether or not this EmailPart is equal to the object
   *
   * @return The equality of this and o
   */
  public boolean equals(Object o) {
    if (! (o instanceof EmailPart))
      return false;

    EmailPart part = (EmailPart) o;
    
    return content.equals(part.content);
  }

}
