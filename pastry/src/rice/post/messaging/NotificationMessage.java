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
public abstract class NotificationMessage extends Message implements Serializable {
  private PostClientAddress clientId = null;
  private PostEntityAddress address = null; 
  /**
   * Constructs a NotificationMessage for the given Email.
   *
   * @param clientId The address of the service to which this message
   *	    should be delivered.
   * @param address The address of the user or group to which this
   *        message should be delivered. 
   */
  public NotificationMessage(PostClientAddress clientId, PostEntityAddress address) {
   super(PostAddress.instance());
   this.clientId = clientId;
   this.address = address;
  }
  /**
   * Returns the PostEntityAddress of the user or group 
   * to which this noticiation should be delivered.
   *
   * @return The address of the user or group to which this should be delivered
   */
  public PostEntityAddress getAddress() {
    return address;
  }
  /**
   * Returns the PostClientAddress of the application to 
   * which this message should be delievered
   *
   * @return The address of the service to which this message
   *         should be delivered 
   */
   public PostClientAddress getClientId(){
     return clientId;
   }

   /**
    * Sets the PostEntityAddress to which this message should be delivered
    *
    * @param The address of the user or group to which this message should be delivered
    *
    */
    public void setAddress(PostEntityAddress address){
       this.address = address;
    }
  
   /**
    * Sets the PostClientAddress to which this message should be delivered
    *
    * @param The client address to which this message should be delivered
    *
    */
    public void setClientId(PostClientAddress clientId){
      this.clientId = clientId;
    }

    
}
