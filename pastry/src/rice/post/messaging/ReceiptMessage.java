package rice.post.messaging;

import java.security.*;
import java.io.*;

import rice.post.*;
import rice.post.messaging.*;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;

/**
 * This message is broadcast to the sender of a NotificationMessage in
 * order to inform the sender that the message has been received.
 */
public class ReceiptMessage extends PostMessage {
  public static final short TYPE = 11;

  private SignedPostMessage message;
  private Id id;
  
  /**
   * Constructs a PresenceMessage
   *
   * @param message The notification message which this is a receipt for
   */
  public ReceiptMessage(PostEntityAddress sender, Id id, SignedPostMessage message) {
    super(sender);
    this.id = id;
    this.message = message;
  }
    
  /**
   * Gets the SignedPostMessage which this ReceiptMessage is a receipt
   * for.
   *
   * @return The message which this receipt is for.
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
  
  public ReceiptMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint);
    message = new SignedPostMessage(buf, endpoint);
    
    id = endpoint.readId(buf, buf.readShort());
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    super.serialize(buf); 
    message.serialize(buf);
    
    buf.writeShort(id.getType());
    id.serialize(buf);
  }
  
  public short getType() {
    return TYPE; 
  }
}

