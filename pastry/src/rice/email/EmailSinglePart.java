package rice.email;

import java.io.*;
import java.lang.ref.*;
import java.security.*;
import java.util.*;

import rice.*;
import rice.Continuation.*;
import rice.pastry.*;
import rice.post.storage.*;

/**
 * Represents the content of an email which is a single entry
 *
 * @author Alan Mislove
 */
public class EmailSinglePart extends EmailContentPart {
  
  // serialver
  private static final long serialVersionUID = -8701317817146783297L;

  /**
   * The number of lines for this email single part
   */
  protected int lines;

  /**
   * The actual content of this email part
   */
  protected transient SoftReference content;

  /**
    * A reference to the content of this email part
   */
  public EmailDataReference contentReference;
  
  /**
   * A reference to the content which is non-soft
   */
  protected transient EmailData unstoredContent;

  /**
   * Constructor which takes in an EmailData
   */
  public EmailSinglePart(EmailData content) {
    super(content.getData().length);
    
    this.content = new SoftReference(content);
    this.unstoredContent = content;
    this.lines = new String(content.getData()).split("\n").length;
  }
  
  /**
   * This method returns a list of all the handles stored in this part
   * by adding them to the specified set.
   *
   * @param set The set to add the PastContentHandles to.
   */
  public void getContentHashReferences(Set set) {
    set.add(contentReference);    
  }

  /**
   * Method which writes this part's content out to disk
   * and retrieves a reference to it.
   *
   * @param command The command to run once the data has been stored, and
   *   is returned the success or failure of this command
   */
  public void storeData(Continuation command) {
    if ((contentReference != null) || (unstoredContent == null)) { 
      command.receiveResult(Boolean.TRUE);
      return;
    }
    
    storage.storeContentHash(unstoredContent, new StandardContinuation(command) {
      public void receiveResult(Object o) {
        contentReference = (EmailDataReference) o;
        unstoredContent = null;
        
        parent.receiveResult(Boolean.TRUE);
      }
    });
  }

  /**
   * Method which retrieves and returns this content's EmailData
   *
   * @param command The command to run once the data is available
   */
  public void getContent(Continuation command) {
    EmailData data = null;
    
    if (((data = unstoredContent) != null) ||
        ((content != null) && ((data = (EmailData) content.get()) != null))) {
      command.receiveResult(data);
      return;
    }

    storage.retrieveContentHash(contentReference, new StandardContinuation(command) {
      public void receiveResult(Object o) {
        content = new SoftReference((EmailData) o);
        
        parent.receiveResult(o);
      }
    });    
  }

  /**
   * Returns the number of lines for this email single part
   *
   * @return The number of lines of the data
   */
  public int getLines() {
    return lines;
  }

  /**
   * Returns whether or not this emailSinglePart is equal to the
   * given object
   *
   * @param o The object to compare to
   * @return Whether or not this is equal to o
   */
  public boolean equals(Object o) {
    if (! (o instanceof EmailSinglePart)) {
      return false;
    }

    EmailSinglePart part = (EmailSinglePart) o;

    if (contentReference != null) {
      return contentReference.equals(part.contentReference);
    } else {
      return content.equals(part.content);
    }
  }
  
  /**
    * Returns the hashCode
   *
   */
  public int hashCode() {
    return contentReference.hashCode();
  }
  
  /**
   * Method which sets this part's storage service
   *
   * @param storage The local storage service
   */
  public void setStorage(StorageService storage) {
    super.setStorage(storage);
    
    if ((unstoredContent == null) && (contentReference == null))
      throw new NullPointerException("Unstored Content and Reference NULL in EmailsinglePart!");
  }
}
