package rice.post;

import rice.post.messaging.*;

/**
 * This interface is a callback mechanism for clients running on top
 * of the Post object.
 */
public interface PostClient {

  /**
   * This method is how the Post object informs the clients
   * that there is an incoming notification. 
   *
   * @param nm The incoming notification.
   */
  public void notificationReceived(NotificationMessage nm);

  /**
   * This method returns the unique PostApplicationAddress
   * of this PostClient.
   *
   * @return The address of this client.
   */
  public PostClientAddress getAddress();
}
