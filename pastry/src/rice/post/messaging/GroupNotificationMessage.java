package rice.post.messaging;

import java.io.IOException;
import java.util.*;

import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.rawserialization.*;
import rice.post.*;

/**
 * This class represents a notification message which is sent to a group.
 */
public class GroupNotificationMessage extends PostMessage {
  public static final short TYPE = 7;

  private PostGroupAddress group;

  private byte[] data;

  /**
   * Constructs a GroupNotificationMessage
   *
   * @param key The encrypted key
   * @param data The encrypted NotificationMessage
   */
  public GroupNotificationMessage(PostEntityAddress sender, PostGroupAddress group, byte[] data) {
    super(sender);
    this.group = group;
    this.data = data;
  }

  /**
   * Returns the group of the NotificationMessage
   *
   * @return The group
   */
  public PostGroupAddress getGroup() {
    return group;
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
    if (! (o instanceof GroupNotificationMessage))
      return false;

    return Arrays.equals(data, ((GroupNotificationMessage) o).getData());
  }


  public GroupNotificationMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint);
    
    group = new PostGroupAddress(buf, endpoint);
    
    data = new byte[buf.readInt()];
    buf.read(data);
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    super.serialize(buf); 
    
    group.serialize(buf);
    
    buf.writeInt(data.length);
    buf.write(data, 0, data.length);
  }
  
  public short getType() {
    return TYPE;
  }
}
