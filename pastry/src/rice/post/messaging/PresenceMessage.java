package rice.post.messaging;

import rice.post.*;
import rice.pastry.*;

/**
 * This is the message broadcast to the Scribe group of
 * the user to inform replica holders that that user is available
 * at the given nodeid.
 */
public class PresenceMessage extends PostMessage {
    
  /**
   * Constructs a PresenceMessage
   *
   * @param address The address of the user asserted to be present.
   * @param location The user's asserted location.
   */
  public PresenceMessage(PostUserAddress address, NodeId location) {
  }
    
  /**
   * Gets the PostUserAddress of the user.
   *
   * @return The address of the user who sent this message.
   */
  public PostUserAddress getUserAddress() {
    return null;
  }
    
  /**
   * Gets the location of the user.
   *
   * @return The location in the Pastry ring of the user.
   */
  public NodeId getLocation() {
    return null;
  }
}
