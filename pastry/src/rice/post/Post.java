package rice.post;

import java.security.*;

import rice.*; 

import rice.p2p.commonapi.*;

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
   * @param command The command to execute once done
   */
  public void sendNotification(NotificationMessage message, Continuation command);

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
   * @param command The command to execute once done
   */
  public void sendNotificationDirect(NodeHandle handle, NotificationMessage message, Continuation command);

  /**
   * This method causes the local POST service to subscribe to the specified address, and
   * use the specified shared key in order to decrypt messages.  If the key is null, then
   * messages are assumed to be unencrypted.  Incoming messages, once verified, will be
   * passed up to the appropriate applciation through the notificationReceived() method.
   *
   * @param address The address to join
   * @param key The shared key to use (or null, if unencrypted)
   */
  public void joinGroup(PostGroupAddress address, byte[] key);

  /**
   * This method multicasts the provided notification message to the destination
   * group.  However, this local node *MUST* have already joined this
   * group (through the joinGroup method) in order for this to work properly.
   *
   * @param message The message to send
   * @param command The command to execute once done
   */
  public void sendGroup(NotificationMessage message, Continuation command);
}
