package rice.email;

import java.io.*;
import java.security.*;
import java.util.*;

import rice.*;
import rice.Continuation.*;
import rice.pastry.*;
import rice.post.storage.*;

/**
 * Abstract class which represents a part of an email with headers
 *
 * @author Alan Mislove
 */
public class EmailHeadersPart extends EmailContentPart {

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
    * Constructor. Takes in a emailData representing the headers and
   * a EmailContentPart representing the content
   *
   * @param headers The headers of this part
   * @param content The content of this part
   */
  public EmailHeadersPart(EmailData headers, EmailContentPart content) {
    super(content.getSize());
    this.headers = headers;
    this.content = content;
  }

  /**
   * Method which sets this part's storage service
   *
   * @param storage The local storage service
   */
  public void setStorage(StorageService storage) {
    super.setStorage(storage);
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
   * Method which writes this part's headers out to disk
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
   * Returns whether or not this emailHeadersPart is equal to the
   * given object
   *
   * @param o The object to compare to
   * @return Whether or not this is equal to o
   */
  public boolean equals(Object o) {
    if (! (o instanceof EmailHeadersPart)) {
      return false;
    }

    EmailHeadersPart part = (EmailHeadersPart) o;

    if (headersReference != null) {
      return (headersReference.equals(part.headersReference) && content.equals(part.content));
    } else {
      return (headers.equals(part.headers) && content.equals(part.content));
    }
  }
}
