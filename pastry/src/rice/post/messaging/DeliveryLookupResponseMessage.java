package rice.post.messaging;

import java.io.*;
import java.security.*;

import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.rawserialization.*;
import rice.post.messaging.*;
import rice.post.*;

/**
 * This message is broadcast to the sender of a NotificationMessage in
 * order to inform the sender that the message has been received.
 */
public class DeliveryLookupResponseMessage extends PostMessage {
  public static final short TYPE = 3;

  private DeliveryRequestMessage message;

  /**
    * Constructs a DeliveryLookupResponseMessage
   *
   * @param sender The sender of this delivery request
   * @param message The requested message
   */
  public DeliveryLookupResponseMessage(PostEntityAddress sender,
                                       DeliveryRequestMessage message) {
    super(sender);
    this.message = message;
  }

  /**
    * Gets the random locaiton of this drm
   *
   * @return The locaiton
   */
  public DeliveryRequestMessage getEncryptedMessage() {
    return message;
  }
  
  public DeliveryLookupResponseMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint); 
    
    message = new DeliveryRequestMessage(buf, endpoint);
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    super.serialize(buf);
    
    message.serialize(buf);
  }

  public short getType() {
    return TYPE;
  }


}

