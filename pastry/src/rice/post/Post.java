package rice.post;

import java.security.*;

import rice.*;
import rice.pastry.*;
import rice.post.log.*;
import rice.post.messaging.*;
import rice.post.storage.*;
import rice.post.security.*;

/**
 * This interface represents the Post service layer.
 * 
 * @version $Id$
 *
 * @author Ansley Post
 * @author Alan Mislove
 */
public interface Post {
  
  /**
   * Returns the PostEntityAddress of this Post's local user.
   *
   * @return The PostEntityAddress of the local user.
   */
  public PostEntityAddress getEntityAddress();
  
  /**
   * Returns the certificate authority's public key.
   *
   * @return The CA's public key
   */
  public PublicKey getCAPublicKey();

  /**
   * Shortcut which returns the PostLog of the local user.
   *
   * @return The PostLog belonging to the this entity,
   */
  public void getPostLog(Continuation command);
  
  /**
   * Returns and verifies the PostLog of the named entity
   *
   * @return The PostLog belonging to the given entity, eg. to acquire
   * another user's public key.
   */
  public void getPostLog(PostEntityAddress entity, Continuation command);
  
  /**
   * This method returns the local storage service.
   *
   * @return The storage service.
   */
  public StorageService getStorageService();   
  
  /**
   * Registers a client with this Post 
   *
   * @param client The client to add
   */
  public void addClient(PostClient client);

  /**
   * Removes a client from this PostService.
   *
   * @param client The client to remove
   */
  public void removeClient(PostClient client);
  
  /**
   * This method announce's the local user's presence via the scribe tree
   */
  public void announcePresence();

  /**
   * Sends a notification message with destination specified by the members
   * of the NotificationMessage.  Destination parameters include a PostEntityAddress
   * which specifies the group or user to which the notification should go, and a
   * PostClientAddress which specifies the user application to which the notification
   * should go.  The NotificationMessage sent is signed by the sender and is then 
   * encrypted with the public key of each recipient.
   *
   * In this method, the notification is sent indirectly, through a group of
   * random nodes who subscribe to the recipient's scribe group.
   *
   * @param message The notification message to be sent.  Destination parameters
   * are encapsulated inside the message object.
   */
  public void sendNotification(NotificationMessage message);

  /**
   * Sends a notification message with destination specified by the members
   * of the NotificationMessage.  Destination parameters include a PostEntityAddress
   * which specifies the group or user to which the notification should go, and a
   * PostClientAddress which specifies the user application to which the notification
   * should go.  The NotificationMessage sent is signed by the sender and is then
   * encrypted with the public key of each recipient.
   *
   * In this method, the notification message is sent directly to the provided node handle,
   * instead of through a group of random nodes via the Scribe tree.
   *
   * @param message The notification message to be sent.  Destination parameters
   * are encapsulated inside the message object.
   */
  public void sendNotificationDirect(NodeHandle handle, NotificationMessage message);
}
