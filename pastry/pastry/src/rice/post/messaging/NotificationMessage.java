package rice.post.messaging;

import rice.post.*;
import rice.pastry.messaging.*;
import java.io.*;
import rice.post.messaging.*;
 
/**
 * This class represents an abstract message in the Post system
 * which serves as a notification.  Each Post application should
 * extend this class with each type of relevant notification 
 * message.
 */
public abstract class NotificationMessage extends PostMessage {
  
  private PostClientAddress clientAddress;
  private PostEntityAddress destination;
  
  /**
   * Constructs a NotificationMessage for the given Email.
   *
   * @param clientAddress The address of the service to which this message
   *	    should be delivered.
   * @param destination The address of the user or group to which this
   *        message should be delivered. 
   */
  public NotificationMessage(PostClientAddress clientAddress, PostEntityAddress sender, PostEntityAddress destination) {
    super(sender);
    this.clientAddress = clientAddress;
    this.destination = destination;
  }
  
  /**
   * Returns the PostEntityAddress of the user or group 
   * to which this noticiation should be delivered.
   *
   * @return The address of the user or group to which this should be delivered
   */
  public PostEntityAddress getDestination() {
    return destination;
  }
  
  /**
   * Returns the PostClientAddress of the application to 
   * which this message should be delievered
   *
   * @return The address of the service to which this message
   *         should be delivered 
   */
   public PostClientAddress getClientAddress(){
     return clientAddress;
   }
    
}
