package rice.email;

import java.io.*;
import java.security.*;
import java.util.*;

import rice.*;
import rice.Continuation.*;
import rice.pastry.*;
import rice.post.storage.*;

/**
 * Represents the content of an email
 *
 * @author Alan Mislove
 */
public abstract class EmailContentPart implements Serializable {

  /**
   * The size of this part, in bytes
   */
  protected int size;
  
  /**
   * The local storage service
   */
  protected transient StorageService storage;

  /**
   * Constructor which takes in an EmailData
   */
  public EmailContentPart(int size) {
    this.size = size;
  }

  /**
   * Sets the size of this part, in bytes
   *
   * @param size The size of this part
   */
  protected void setSize(int size) {
    this.size = size;
  }

  /**
   * Returns the size of this part, in bytes
   *
   * @return The size of this part
   */
  public int getSize() {
    return size;
  }
  
  /**
   * Method which sets this part's storage service
   *
   * @param storage The local storage service
   */
  public void setStorage(StorageService storage) {
    this.storage = storage;
  }
  
  /**
   * Method which writes this part's content out to disk
   * and retrieves a reference to it.
   *
   * @param command The command to run once the data has been stored, and
   *   is returned the success or failure of this command
   */
  public abstract void storeData(Continuation command);

  /**
   * Method which retrieves and returns this content's EmailData
   *
   * @param command The command to run once the data is available
   */
  public abstract void getContent(Continuation command);

  /**
   * Overridden to enforce subclasses have a valid equals
   */
  public abstract boolean equals(Object o);
  
  /**
   * This method returns a list of all the handles stored in this part
   * by adding them to the specified set.
   *
   * @param set The set to add the PastContentHandles to.
   */
  public abstract void getContentHashReferences(Set set);
}
