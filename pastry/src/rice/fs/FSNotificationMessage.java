package rice.fs;

import rice.post.*;
import rice.post.messaging.*;
import rice.post.storage.*;

import java.io.*;

/**
 * This class represents a notification in the fs service that
 * a new file or dir has been stored 
 */
public class FSNotificationMessage extends NotificationMessage {

   /* The reference to the data stored */
   protected ContentHashReference data = null; 
   
   /* The name of the file stored */
   protected String file = null;
   
   /* Wheter the file stored is a directory or not */ 
   protected boolean isDirectory = false;

  /**
   *
   * Constructor for and FSNotication Mesage
   *
   * @param service the fsservice used to send the message
   * @param sender the PostEntity sending the message
   * @param recipient the destination for the message
   * @param c the reference to be stored in this notification
   * @param file the file name of the data
   *
   */
  public FSNotificationMessage( FSService service, PostEntityAddress sender, PostEntityAddress recipient, ContentHashReference c, String file) {
     this(service, sender, recipient, c, file, false);
  }

  /**
   *
   * Constructor for and FSNotication Mesage
   *
   * @param service the fsservice used to send the message
   * @param sender the PostEntity sending the message
   * @param recipient the destination for the message
   * @param c the reference to be stored in this notification
   * @param file the file name of the data
   * @param isDirectory flag telling whether this notification is for a dir
   *
   */
  public FSNotificationMessage( FSService service, PostEntityAddress sender, PostEntityAddress recipient, ContentHashReference c, String file, boolean isDirectory) {
    super(service.getAddress(), sender, recipient);
    this.data = c;
    this.file = file;
    this.isDirectory = isDirectory;
    }

  /**
   *
   * Gets the content hash reference stored inside the message 
   *
   * @return the reference to the content 
   */
  public ContentHashReference getContent(){
        return data;
  }
 
  /**
   *
   * Gets the file name stored inside the message 
   *
   * @return the name of the content 
   */
  public String getFileName(){
   return file;
  }

   
  /**
   *
   * Gets the dir flage inside the message 
   *
   * @return whether this message represents a dir or not 
   */
  public boolean isDirectory(){
    return isDirectory;
  }
}
