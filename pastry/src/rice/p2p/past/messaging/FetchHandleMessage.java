
package rice.p2p.past.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.past.*;

/**
 * @(#) FetchHandleMessage.java
 *
 * This class represents a handle request in Past.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Ansley Post
 * @author Peter Druschel
 */
public class FetchHandleMessage extends ContinuationMessage {

  // the id to fetch
  private Id id;
  
  /**
   * Constructor 
   *
   * @param uid The unique id
   * @param id The id of the object to be looked up
   * @param source The source address
   * @param dest The destination address
   */
  public FetchHandleMessage(int uid, Id id, NodeHandle source, Id dest) {
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
    * Returns a string representation of this message
   *
   * @return A string representing this message
   */
  public String toString() {
    return "[FetchHandleMessage for " + id + "]";
  }
}

