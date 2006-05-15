package rice.post.messaging;

import java.io.*;
import rice.post.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;

/**
 * This is the message broadcast to the Scribe group of
 * the user to inform replica holders that that user is available
 * at the given nodeid.
 */
public class PresenceMessage extends PostMessage {
  public static final short TYPE = 10;

  private Id location;

  static final long serialVersionUID = -2972426454617508369L;

  private NodeHandle handle;
  
  /**
   * Constructs a PresenceMessage
   *
   * @param sender The address of the user asserted to be present.
   * @param location The user's asserted location.
   */
  public PresenceMessage(PostEntityAddress sender, NodeHandle handle) {
    super(sender);
    this.location = handle.getId();
    this.handle = handle;
  }

  /**
   * Gets the location of the user.
   *
   * @return The location in the Pastry ring of the user.
   */
  public Id getLocation() {
    return location;
  }
  
  /**
   * Gets the handle to this user.
   *
   * @return The location in the Pastry ring of the user.
   */
  public NodeHandle getHandle() {
    return handle;
  }
  
  public PresenceMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint);
    
    location = endpoint.readId(buf, buf.readShort());
    
    handle = endpoint.readNodeHandle(buf);
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    super.serialize(buf);

    buf.writeShort(location.getType());
    location.serialize(buf);
    
    handle.serialize(buf);
  }
  
  public short getType() {
    return TYPE;
  }
}
