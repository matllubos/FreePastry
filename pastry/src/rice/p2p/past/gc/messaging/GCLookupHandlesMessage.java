
package rice.p2p.past.gc.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.past.*;
import rice.p2p.past.messaging.*;
import rice.p2p.past.gc.*;

/**
 * @(#) GCLookupHandlesMessage.java
 *
 * This class represents a message which is an request to find the leafset of
 * a remote node in the GC Past.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class GCLookupHandlesMessage extends LookupHandlesMessage {
  
  /**
   * Constructor
   *
   * @param uid The unique id
   * @param id The location to be stored
   * @param source The source address
   * @param dest The destination address
   */
  public GCLookupHandlesMessage(int uid, Id id, NodeHandle source, Id dest) {
    super(uid, id, Integer.MAX_VALUE, source, dest);
  }
  
  /**
    * Returns a string representation of this message
   *
   * @return A string representing this message
   */
  public String toString() {
    return "[GCLookupHandlesMessage for " + getId() + "]";
  }
}

