package rice.post.messaging;

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
   * @param eaddr The EmailAddress of the user asserted to be present.
   * @param nodeid The user's asserted location.
   */
  public PresenceMessage(EmailAddress eaddr, NodeId nodeid) {
  }
    
  /**
   * Gets the EmailAddress of the user.
   */
  public EmailAddress getEmailAddress() {
  }
    
  /**
   * Gets the NodeId where the user is.
   */
  public NodeId getNodeId() {
  }
}
