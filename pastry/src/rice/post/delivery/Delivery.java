
package rice.post.delivery;

import java.io.*;

import rice.*;
import rice.post.*;
import rice.post.messaging.*;
import rice.post.security.*;
import rice.p2p.past.*;
import rice.p2p.past.gc.*;
import rice.p2p.commonapi.*;
import rice.p2p.multiring.*;

/**
 * The delivery stored in Past
 *
 * @version $Id$
 * @author Alan Mislove
 * @author Ansley Post
 * @author Peter Druschel
 */
public class Delivery extends ContentHashPastContent implements GCPastContent {
  
  /**
   * The internal encrypted message
   */
  protected SignedPostMessage message;
  
  /**
   * Constructor which takes the wrapped message
   *
   * @param message The message to deliver
   */
  protected Delivery(SignedPostMessage message, IdFactory factory) {
    this.message = message;
    
    try {
      if (factory instanceof MultiringIdFactory) {
        MultiringIdFactory mFactory = (MultiringIdFactory) factory;
        EncryptedNotificationMessage enm = (EncryptedNotificationMessage) message.getMessage();
        this.myId = mFactory.buildRingId(((RingId) enm.getDestination().getAddress()).getRingId(), 
                                         SecurityUtils.hash(SecurityUtils.serialize(message)));
      } else {
        this.myId = factory.buildId(SecurityUtils.hash(SecurityUtils.serialize(message)));
      }
    } catch (IOException e) {
      throw new IllegalArgumentException("Setting myId caused: " + e);
    }
  }
  
  /**
   * Returns the internal signed message
   *
   * @return The wrapped message
   */
  public SignedPostMessage getSignedMessage() {
    return message;
  }
  
  /**
   * Returns the internal message
   *
   * @return The wrapped message
   */
  public EncryptedNotificationMessage getMessage() {
    return (EncryptedNotificationMessage) message.getMessage();
  }
  
  /**
   * Returns the version number associated with this PastContent object - 
   * version numbers are designed to be monotonically increasing numbers which
   * signify different versions of the same object.
   *
   * @return The version number of this object
   */
  public long getVersion() {
    return 0L;
  }
  
  /**
    * Produces a handle for this content object. The handle is retrieved and returned to the
   * client as a result of the Past.lookupHandles() method.
   *
   * @param The local GCPast service which the content is on.
   * @return the handle
   */
  public GCPastContentHandle getHandle(GCPast local, long expiration) {
    return new DeliveryHandle(getId(), local.getLocalNodeHandle(), expiration);
  }
  
  /**
   * Returns the metadata which should be stored with this object.  Allows applications
   * to add arbitrary items into the object's metadata.
   *
   * @param The local GCPast service which the content is on.
   * @return the handle
   */
  public GCPastMetadata getMetadata(long expiration) {
    return new DeliveryMetadata(expiration, getMessage().getDestination());
  }
  
  protected static class DeliveryHandle implements GCPastContentHandle {
    
    // config variables
    protected Id id;
    protected NodeHandle handle;
    protected long expiration;
    
    /**
     * Constructor
     *
     */
    public DeliveryHandle(Id id, NodeHandle handle, long expiration) {
      this.id = id;
      this.handle = handle;
      this.expiration = expiration;
    }
    
    /**
     * get the id of the PastContent object associated with this handle
     * @return the id
     */
    public Id getId() { 
      return id; 
    }
    
    /**
     * get the NodeHandle of the Past node on which the object associated with this handle is stored
     * @return the id
     */
    public NodeHandle getNodeHandle() {
      return handle;
    }
    
    /**
     * Returns the version number associated with this PastContentHandle - 
     * version numbers are designed to be monotonically increasing numbers which
     * signify different versions of the same object.
     *
     * @return The version number of this object
     */
    public long getVersion() {
      return 0L;
    }
    
    /**
     * Returns the current expiration time of this object.
     *
     * @return The current expiration time of this object
     */
    public long getExpiration() {
      return expiration;
    }
  }
}





