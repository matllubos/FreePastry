package rice.email;

import java.io.*;
import java.lang.ref.*;
import java.security.*;
import java.util.*;

import rice.*;
import rice.Continuation.*;
import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.rawserialization.*;
import rice.pastry.*;
import rice.post.storage.*;

/**
 * Abstract class which represents a part of an email with headers
 *
 * @author Alan Mislove
 */
public class EmailHeadersPart extends EmailContentPart {
  public static final short TYPE = 1;
  
  // serialver uid
  private static final long serialVersionUID = 1186745194337869017L;

  /**
   * The data representing the haeders (transient as it is stored).
   */
  protected transient SoftReference headers;

  /**
   * A reference to the headers of this email part
   */
  public EmailDataReference headersReference;

  /**
   * The content of this part
   */
  public EmailContentPart content;
  
  /**
   * A reference to the headers which is non-soft
   */
  protected transient EmailData unstoredHeaders;

  /**
    * Constructor. Takes in a emailData representing the headers and
   * a EmailContentPart representing the content
   *
   * @param headers The headers of this part
   * @param content The content of this part
   */
  public EmailHeadersPart(EmailData headers, EmailContentPart content) {
    super(headers.getData().length + content.getSize());
    this.unstoredHeaders = headers;
    this.headers = new SoftReference(headers);
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
   * This method returns a list of all the handles stored in this part
   * by adding them to the specified set.
   *
   * @param set The set to add the PastContentHandles to.
   */
  public void getContentHashReferences(Set set) {
    set.add(headersReference);
    content.getContentHashReferences(set);
  }

  /**
   * Returns the headers of this EmailPart to the continuation
   *
   * @param commmand The command to run once the result has been
   *   obtained
   */
  public void getHeaders(Continuation command) {
    EmailData data = null;

    if (((data = unstoredHeaders) != null) ||
        ((headers != null) && ((data = (EmailData) headers.get()) != null))) {
      command.receiveResult(data);
      return;
    }
    
    storage.retrieveContentHash(headersReference, new StandardContinuation(command) {
      public void receiveResult(Object o) {
        headers = new SoftReference((EmailData) o);

        parent.receiveResult(o);
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
    if ((headersReference != null) || (unstoredHeaders == null)) {
      command.receiveResult(Boolean.TRUE);
      return;
    }

    storage.storeContentHash(unstoredHeaders, new StandardContinuation(command) {
      public void receiveResult(Object o) {
        headersReference = (EmailDataReference) o;
        unstoredHeaders = null;
        
        content.storeData(parent);
      }
    });
  }
  
  /**
   * Returns the hashCode
   *
   */
  public int hashCode() {
    return headersReference.hashCode() ^ content.hashCode();
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
      return (unstoredHeaders.equals(part.unstoredHeaders) && content.equals(part.content));
    }
  }
  
  
  public EmailHeadersPart(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf);
    headersReference = new EmailDataReference(buf, endpoint);
    
    content = EmailContentPart.build(buf, endpoint, buf.readShort());
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    super.serialize(buf);
    headersReference.serialize(buf);
    
    buf.writeShort(content.getRawType());
    content.serialize(buf);
  }
  
  public short getRawType() {
    return TYPE; 
  }
}
