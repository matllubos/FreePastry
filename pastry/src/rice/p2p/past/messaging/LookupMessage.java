
package rice.p2p.past.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.past.*;

/**
 * @(#) LookupMessage.java
 *
 * This class is the representation of a lookup request (by Id) in Past.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Ansley Post
 * @author Peter Druschel
 */
public class LookupMessage extends ContinuationMessage {

  // the id to fetch
  private Id id;

  // whether or not this message has been cached
  private boolean cached = false;

  // the list of nodes where this message has been
  private NodeHandle handle;
  
  /**
   * Constructor
   *
   * @param uid The unique id
   * @param id The location to be stored
   * @param useReplicas Whether or not to look for nearest replicas
   * @param source The source address
   * @param dest The destination address
   */
  public LookupMessage(int uid, Id id, NodeHandle source, Id dest) {
    super(uid, source, dest);

    this.id = id;
  }

  /**
   * Method which returns the id
   *
   * @return The contained id
   */
  public Id getId() {
    return id;
  }

  /**
   * Returns whether or not this message has been cached
   *
   * @return Whether or not this message has been cached
   */
  public boolean isCached() {
    return cached;
  }

  /**
   * Sets this message as having been cached.
   */
  public void setCached() {
    cached = true;
  }

  /**
   * Method which is designed to be overridden by subclasses if they need
   * to keep track of where they've been.
   *
   * @param handle The current local handle
   */
  public void addHop(NodeHandle handle) {
    this.handle = handle;
  }

  /**
   * Method which returns the previous hop (where the message was just at)
   *
   * @return The previous hop
   */
  public NodeHandle getPreviousNodeHandle() {
    return handle;
  }

  /**
    * Returns a string representation of this message
   *
   * @return A string representing this message
   */
  public String toString() {
    return "[LookupMessage for " + id + " data " + response + "]";
  }
}

