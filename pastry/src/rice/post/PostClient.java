package rice.post;

import java.util.Observable;

import rice.*;
import rice.post.messaging.*;

/**
 * This class is a superclass for clients running on top
 * of the Post object.
 * 
 * @version $Id$
 */
public abstract class PostClient extends Observable {

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
   * @param command THe command to return whether or not the notification should be accepted (Boolean true or false)
   */
  public abstract void notificationReceived(NotificationMessage nm, Continuation command);
  
  /**
   * This method is periodically invoked by Post in order to get a list of
   * all handles under which the application has live objects.  This used to
   * implement the garbage collection service, thus, the application must
   * ensure that all data which it is still interested in is returned.
   *
   * The applications should return a ContentHashReference[] containing all of 
   * the handles The application is still interested in to the provided continatuion.
   */
  public abstract void getContentHashReferences(Continuation command);
  
  /**
   * This method is periodically invoked by Post in order to get a list of
   * all mutable data which the application is interested in.
   *
   * The applications should return a Log[] containing all of 
   * the data The application is still interested in to the provided continatuion.
   */
  public abstract void getLogs(Continuation command);

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
