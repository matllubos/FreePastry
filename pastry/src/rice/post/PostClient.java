package rice.post;

import rice.post.messaging.*;

/**
 * This class is a superclass mechanism for clients running on top
 * of the Post object.
 */
public abstract class PostClient {

  /**
   * Builds a PostClient.
   */
  public PostClient() {
  }
  
  /**
   * This method is how the Post object informs the clients
   * that there is an incoming notification. 
   *
   * @param nm The incoming notification.
   */
  public abstract void notificationReceived(NotificationMessage nm);

  /**
   * Returns the address of this PostClient.  This method is
   * automatically provided in order to allow address to happen
   * transparently.
   *
   * @return The unique address of this PostClient.
   */
  public final PostClientAddress getAddress() {
    return PostClientAddress.getAddress(this);
  }
  
}
