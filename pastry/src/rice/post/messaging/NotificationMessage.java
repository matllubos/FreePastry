package rice.post.messaging;

import rice.post.*;

/**
 * This class represents an abstract message in the Post system
 * which serves as a notification.  Each Post application should
 * extend this class with each type of relevant notification 
 * message.
 */
public abstract class NotificationMessage extends PostMessage {
  private PostClientAddress address = null;
 
  /**
   * Constructs a NotificationMessage for the given Email.
   *
   * @param address The address of the service to which this message
   *        should be delivered.
   */
  public NotificationMessage(PostClientAddress address) {
    this.address = address;
  }
  /**
   * Returns the PostApplicationAddress of the application
   * to which this noticiation should be delivered.
   *
   * @return The address of the application to which this should be delivered
   */
  public PostClientAddress getAddress() {
    return address;
  }

}
