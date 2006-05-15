package rice.post.messaging;

import java.io.IOException;
import java.util.*;

import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.rawserialization.*;
import rice.post.*;

/**
 * This class represents a notification message which is in encrypted state.
 */
public class EncryptedNotificationMessage extends PostMessage {
  public static final short TYPE = 6;

  private static final long serialVersionUID = -6105218787584438214L;

  private byte[] key;
  
  private byte[] data;
  
  private PostEntityAddress destination;

  /**
    * Constructs a NotificationMessage for the given Email.
   *
   * @param key The encrypted key
   * @param data The encrypted NotificationMessage
   */
  public EncryptedNotificationMessage(PostEntityAddress sender, PostEntityAddress destination, byte[] key, byte[] data) {
    super(sender);
    this.data = data;
    this.key = key;
    this.destination = destination;
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
    * Returns the encrypted key of the NotificationMessage
   *
   * @return The encrypted key.
   */
  public byte[] getKey() {
    return key;
  }

  /**
   * Returns the ciphertext of the NotificationMessage
   *
   * @return The ciphertext.
   */
  public byte[] getData() {
    return data;
  }

  public boolean equals(Object o) {
    if (! (o instanceof EncryptedNotificationMessage))
      return false;

    return Arrays.equals(data, ((EncryptedNotificationMessage) o).getData());
  }

  
  public EncryptedNotificationMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint);
    
//    System.out.println("EncryptedNotificationMessage.deserialize()");
    
    destination = PostEntityAddress.build(buf, endpoint, buf.readShort());
    
    key = new byte[buf.readInt()];
    buf.read(key);
    
    data = new byte[buf.readInt()];
    buf.read(data);
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    super.serialize(buf);
    
//    System.out.println("EncryptedNotificationMessage.serialize()");
    
    buf.writeShort(destination.getType()); 
    destination.serialize(buf);

    buf.writeInt(key.length);
    buf.write(key, 0, key.length);
    
    buf.writeInt(data.length);
    buf.write(data, 0, data.length);
  }

  public short getType() {
    return TYPE;
  }
}
