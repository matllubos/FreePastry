
package rice.p2p.past.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.past.*;

/**
 * @(#) FetchMessage.java
 *
 * This class represents a message which is a fetch request in past, based
 * on a handle).
 *
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Ansley Post
 * @author Peter Druschel
 */
public class FetchMessage extends ContinuationMessage {

  // the id to fetch
  private PastContentHandle handle;

  // whether or not this message has been cached
  private boolean cached = false;
  
  /**
   * Constructor 
   *
   * @param uid The unique id
   * @param handle The handle to the data to be looked up
   * @param source The source address
   * @param dest The destination address
   */
  public FetchMessage(int uid, PastContentHandle handle, NodeHandle source, Id dest) {
    super(uid, source, dest);

    this.handle = handle;
  }

  /**
   * Method which returns the handle
   *
   * @return The contained handle
   */
  public PastContentHandle getHandle() {
    return handle;
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
    * Returns a string representation of this message
   *
   * @return A string representing this message
   */
  public String toString() {
    return "[FetchMessage for " + handle + " cached? " + cached + "]";
  }
}

