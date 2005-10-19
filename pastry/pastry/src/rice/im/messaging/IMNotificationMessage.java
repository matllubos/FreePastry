package rice.im.messaging;

import rice.email.*;
import rice.post.*;
import rice.post.messaging.*;
import rice.im.*;
import rice.im.log.*;

import java.io.*;


/**
 * This class represents an notification in the im service that
 * a new im message X is available for the recipients of X
 */

public class IMNotificationMessage extends NotificationMessage {
    public IMContent content;
    
    
   /**
   * Constructs a IMNotificationMessage for the given IMContent.
   *
   * @param content The IMContent that this message encapsulates
   * @param recipient the PostUserAddress to recieve this message
   * @param service the IMService to use to send the message
   */

    public IMNotificationMessage (IMContent content, String recipient, IMService service)
    {
	super(service.getAddress(), content.getSender(), new PostUserAddress(recipient + "@rice.edu.post"));
	this.content = content;
    }

     
  /**
   * Returns the IMContent which this notification is for.
   *
   * @return The IMContent contained in this notification
   */
  public IMContent getIMContent() {
      return content;
  }


}


