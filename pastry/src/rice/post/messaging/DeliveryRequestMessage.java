package rice.post.messaging;

import java.io.*;
import java.security.*;

import rice.post.messaging.*;
import rice.post.*;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;

/**
 * This message is broadcast to the sender of a NotificationMessage in
 * order to inform the sender that the message has been received.
 */
public class DeliveryRequestMessage extends PostMessage {
  public static final short TYPE = 5;
 
  private PostUserAddress destination;
  private SignedPostMessage message;
  private Id id;

  /**
   * Constructs a DeliveryRequestMessage
   *
   * @param sender The sender of this delivery request
   * @param destination The destination address to deliver the notification to
   * @param message The message to deliver, in encrypted state
   * @param location The random location of this message
   */
  public DeliveryRequestMessage(PostEntityAddress sender, 
                                PostUserAddress destination, 
                                SignedPostMessage message,
                                Id id) {
    super(sender);
    this.destination = destination;
    this.message = message;
    this.id = id;
  }

  /**
   * Gets the destination of this notification
   *
   * @return The address of the destination of this notification
   */
  public PostUserAddress getDestination() {
    return destination;
  }
    
  /**
   * Gets the EncryptedNotificationMessage which this is a Request for. 
   * for.
   *
   * @return The internal message, in encrypted state
   */
  public SignedPostMessage getEncryptedMessage() {
    return message;
  }

  /**
   * Gets the random locaiton of this drm
   *
   * @return The locaiton
   */
  public Id getId() {
    return id;
  }
  
  public DeliveryRequestMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint);
    
    id = endpoint.readId(buf, buf.readShort());
    
    destination = new PostUserAddress(buf, endpoint);
    message = new SignedPostMessage(buf, endpoint);
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    super.serialize(buf); 

    buf.writeShort(id.getType());
    id.serialize(buf);
    
    destination.serialize(buf);
    message.serialize(buf);    
  }
  
  public short getType() {
    return TYPE;
  }
}

