
package rice.p2p.past.gc.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.past.*;
import rice.p2p.past.messaging.*;
import rice.p2p.past.gc.*;

/**
 * @(#) GCInsertMessage.java
 *
 * This class represents a message which is an insert request in past, 
 * coupled with an expiration time for the object.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class GCInsertMessage extends InsertMessage {

  // the timestamp at which the object expires
  protected long expiration;
  
  /**
   * Constructor which takes a unique integer Id, as well as the
   * data to be stored
   *
   * @param uid The unique id
   * @param content The content to be inserted
   * @param expiration The expiration time
   * @param source The source address
   * @param dest The destination address
   */
  public GCInsertMessage(int uid, PastContent content, long expiration, NodeHandle source, Id dest) {
    super(uid, content, source, dest);

    this.expiration = expiration;
  }

  /**
   * Method which returns the expiration time
   *
   * @return The contained expiration time
   */
  public long getExpiration() {
    return expiration;
  }

  /**
    * Returns a string representation of this message
   *
   * @return A string representing this message
   */
  public String toString() {
    return "[GCInsertMessage for " + content + " exp " + expiration + "]";
  }
}

