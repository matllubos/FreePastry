package rice.post.messaging;

import rice.post.*;
import rice.pastry.*;
import rice.pastry.messaging.*;

/**
 * This is the message broadcast to the Scribe group of
 * the user to inform replica holders that that user is available
 * at the given nodeid.
 */
public class PresenceMessage implements Serializable{
  private PostUserAddress address;
  private NodeId location;    
  /**
   * Constructs a PresenceMessage
   *
   * @param address The address of the user asserted to be present.
   * @param location The user's asserted location.
   */
  public PresenceMessage(PostUserAddress address, NodeId location) {
     this.adress =address;
     this.location = location;
  }
    
  /**
   * Gets the PostUserAddress of the user.
   *
   * @return The address of the user who sent this message.
   */
  public PostUserAddress getUserAddress() {
    return address;
  }
    
  /**
   * Gets the location of the user.
   *
   * @return The location in the Pastry ring of the user.
   */
  public NodeId getLocation() {
    return location;
  }
}
