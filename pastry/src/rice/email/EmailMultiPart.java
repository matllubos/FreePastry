package rice.email;

import java.io.*;
import java.security.*;
import java.util.*;

import rice.*;
import rice.Continuation.*;
import rice.pastry.*;
import rice.post.storage.*;

/**
 * Represents the content of an email which is a multi-part entry
 *
 * @author Alan Mislove
 */
public class EmailMultiPart extends EmailContentPart {

  /**
   * The string used to seperate the parts of this multipart
   */
  protected String type;

  /**
   * The actual content of this email part
   */
  public EmailHeadersPart[] content;

  /**
   * Constructor which takes in an Emailpart list 
   */
  public EmailMultiPart(EmailHeadersPart[] content, String type) {
    super(0);
    
    int size = 0;
    for (int i=0; i<content.length; i++) {
      size += content[i].getSize();
    }

    setSize(size);

    this.type = type;
    this.content = content;

    if ((content == null) || (content.length == 0)) {
      throw new IllegalArgumentException("Content[] must contain at least one element!");
    }
  }

  /**
   * Returns the seperator used for this multipart
   *
   * @return The seperator for this multipart
   */
  public String getType() {
    return type;
  }
  
  /**
   * This method returns a list of all the handles stored in this part
   * by adding them to the specified set.
   *
   * @param set The set to add the PastContentHandles to.
   */
  public void getContentHashReferences(Set set) {
    for (int i=0; i<content.length; i++)
      content[i].getContentHashReferences(set);
  }

  /**
   * Method which sets this part's storage service
   *
   * @param storage The local storage service
   */
  public void setStorage(StorageService storage) {
    super.setStorage(storage);

    for (int i=0; i<content.length; i++) {
      content[i].setStorage(storage);
    }
  }

  /**
   * Method which writes this part's content out to disk
   * and retrieves a reference to it.
   *
   * @param command The command to run once the data has been stored, and
   *   is returned the success or failure of this command
   */
  public void storeData(Continuation command) {
    content[0].storeData(new StandardContinuation(command) {
      private int i=0;
      
      public void receiveResult(Object o) {
        if (! (Boolean.TRUE.equals(o))) {
          parent.receiveResult(o);
          return;
        }

        i++;

        if (i < content.length) {
          content[i].storeData(this);
        } else {
          parent.receiveResult(new Boolean(true));
        }
      }
    });
  }

  /**
   * Method which retrieves and returns this content's EmailData
   *
   * @param command The command to run once the data is available
   */
  public void getContent(Continuation command) {
    command.receiveResult(content);
  }

  /**
   * Returns whether or not this EmailPart is equal to the object
   *
   * @return The equality of this and o
   */
  public boolean equals(Object o) {
    if (! (o instanceof EmailMultiPart))
      return false;

    EmailMultiPart part = (EmailMultiPart) o;

    return Arrays.equals(content, part.content);
  }
}
