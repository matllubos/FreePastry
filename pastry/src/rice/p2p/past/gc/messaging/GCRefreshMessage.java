
package rice.p2p.past.gc.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.past.*;
import rice.p2p.past.messaging.*;
import rice.p2p.past.gc.*;

/**
 * @(#) GCRefreshMessage.java
 *
 * This class represents a message which is an request to extend the lifetime
 * of a set of keys stored in GCPast.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class GCRefreshMessage extends ContinuationMessage {

  // the list of keys which should be refreshed
  protected GCIdSet keys;
  
  /**
   * Constructor which takes a unique integer Id, as well as the
   * keys to be refreshed
   *
   * @param uid The unique id
   * @param keys The keys to be refreshed
   * @param expiration The new expiration time
   * @param source The source address
   * @param dest The destination address
   */
  public GCRefreshMessage(int uid, GCIdSet keys, NodeHandle source, Id dest) {
    super(uid, source, dest);

    this.keys = keys;
  }
  
  /**
   * Method which returns the list of keys
   *
   * @return The list of keys to be refreshed
   */
  public GCIdSet getKeys() {
    return keys;
  }

  /**
    * Returns a string representation of this message
   *
   * @return A string representing this message
   */
  public String toString() {
    return "[GCRefreshMessage of " + keys.numElements() + "]";
  }
}

