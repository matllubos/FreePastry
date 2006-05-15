package rice.post.messaging;

import java.io.*;
import java.security.*;

import rice.post.messaging.*;
import rice.post.*;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;

/**
 * This class wraps an EncrypedNotificationMessage and is
 * used after the receipt of a PresenceMessage.
 */
public class DeliveryMessage extends PostMessage {
  public static final short TYPE = 4;
  private static final long serialVersionUID = -863725686248756000L;

  private SignedPostMessage message;
  private Id id;
  private PostEntityAddress destination;

  /**
    * Constructs a DeliveryMessage
   *
   * @param sender The sender of this delivery 
   * @param message The message to deliver, in encrypted state
   */
  public DeliveryMessage(PostEntityAddress sender,
                         PostEntityAddress destination,
                         Id id,
                         SignedPostMessage message) {
    super(sender);
    this.destination = destination;
    this.message = message;
    this.id = id;
  }
  
  /**
   * Returns the destination of this message.
   *
   * @return The destination
   */
  public final PostEntityAddress getDestination() {
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

  public Id getId() {
    return id;
  }

  public void setId(Id id) {
    this.id = id;
  }

  public DeliveryMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint);
    
    id = endpoint.readId(buf, buf.readShort());
    
    destination = PostEntityAddress.build(buf, endpoint, buf.readShort());     
    
    message = new SignedPostMessage(buf, endpoint);
  }

  public void serialize(OutputBuffer buf) throws IOException {
    super.serialize(buf); 
    
    buf.writeShort(id.getType());
    id.serialize(buf);
    
    buf.writeShort(destination.getType());     
    destination.serialize(buf);     
    
    message.serialize(buf);
  }

  public short getType() {
    return TYPE;
  }
  
}

