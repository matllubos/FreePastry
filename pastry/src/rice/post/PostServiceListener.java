package rice.post;

/**
 * This interface is a callback mechanism for clients running on top
 * of the PostService.
 */
public interface PostServiceListener {

  /**
   * This method is how the PostService layer informs the clients
   * that there is an incoming notification of new email. 
   *
   * @param nm The incoming notification.
   */
  public void incomingNotification(NotificationMessage nm);

}
