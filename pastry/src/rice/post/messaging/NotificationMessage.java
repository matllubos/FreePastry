package rice.post.messaging;

import rice.post.*;
import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.rawserialization.*;
import rice.pastry.messaging.*;
import java.io.*;
import rice.post.messaging.*;
 
/**
 * This class represents an abstract message in the Post system
 * which serves as a notification.  Each Post application should
 * extend this class with each type of relevant notification 
 * message.
 */
public abstract class NotificationMessage /*extends PostMessage*/ {
  private PostEntityAddress sender;  
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
    if (sender == null) {
      throw new IllegalArgumentException("Attempt to build PostMessage with null sender!");
    }
    
    this.sender = sender;
    this.clientAddress = clientAddress;
    this.destination = destination;
  }
  
  /**
   * Returns the sender of this message.
   *
   * @return The sender
   */
  public final PostEntityAddress getSender() {
    return sender;
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
   
   public NotificationMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
     clientAddress = new PostClientAddress(buf);
     destination = PostEntityAddress.build(buf, endpoint, buf.readShort());
     sender = PostEntityAddress.build(buf, endpoint, buf.readShort());
//     System.out.println("NotificationMessage.deserialize("+clientAddress+","+destination+","+sender+")");
   }
   
   public void serialize(OutputBuffer buf) throws IOException {
//     System.out.println("NotificationMessage.serialize("+clientAddress+","+destination+","+sender+")");
     clientAddress.serialize(buf);

     buf.writeShort(destination.getType());
     destination.serialize(buf);

     buf.writeShort(sender.getType());
     sender.serialize(buf);
   }    
}
