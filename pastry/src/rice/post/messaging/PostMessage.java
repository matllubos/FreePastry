package rice.post.messaging;

import java.io.*;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.post.*;

/**
 * This is abstraction of all messages in the Post system.  
 */
public abstract class PostMessage implements Serializable {

  // the sender of this PostMessage
  private PostEntityAddress sender;

  /**
   * Constructs a PostMessage given the name of the
   * sender.
   *
   * @param sender The sender of this message.
   */
  public PostMessage(PostEntityAddress sender) {
    if (sender == null) {
      throw new IllegalArgumentException("Attempt to build PostMessage with null sender!");
    }
    
    this.sender = sender;
  }

  /**
   * Returns the sender of this message.
   *
   * @return The sender
   */
  public final PostEntityAddress getSender() {
    return sender;
  }

  public PostMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    sender = PostEntityAddress.build(buf, endpoint, buf.readShort()); 
  }

  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeShort(sender.getType());
    sender.serialize(buf);
  }
  
  public abstract short getType();

  public static PostMessage build(InputBuffer buf, Endpoint endpoint, short type) throws IOException {
    switch(type) {
      case DeliveryLookupMessage.TYPE:
        return new DeliveryLookupMessage(buf, endpoint);
      case DeliveryLookupResponseMessage.TYPE:
        return new DeliveryLookupResponseMessage(buf, endpoint);
      case DeliveryMessage.TYPE:
        return new DeliveryMessage(buf, endpoint);
      case DeliveryRequestMessage.TYPE:
        return new DeliveryRequestMessage(buf, endpoint);
      case EncryptedNotificationMessage.TYPE:
        return new EncryptedNotificationMessage(buf, endpoint);
      case GroupNotificationMessage.TYPE:
        return new GroupNotificationMessage(buf, endpoint);
      case PresenceMessage.TYPE:
        return new PresenceMessage(buf, endpoint);        
      case ReceiptMessage.TYPE:
        return new ReceiptMessage(buf, endpoint);
    }
    throw new RuntimeException("Unknown type:"+type);
  }  
}
