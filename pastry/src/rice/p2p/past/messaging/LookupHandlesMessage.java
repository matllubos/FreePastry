
package rice.p2p.past.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.past.*;

/**
 * @(#) LookupMessage.java
 *
 * This class represents a request for all of the replicas of a given object.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Ansley Post
 * @author Peter Druschel
 */
public class LookupHandlesMessage extends ContinuationMessage {

  // the id to fetch
  private Id id;

  // the number of replicas to fetch
  private int max;
   
  /**
   * Constructor
   *
   * @param uid The unique id
   * @param id The location to be stored
   * @param max The number of replicas
   * @param source The source address
   * @param dest The destination address
   */
  public LookupHandlesMessage(int uid, Id id, int max, NodeHandle source, Id dest) {
    super(uid, source, dest);

    this.id = id;
    this.max = max;
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
   * Method which returns the number of replicas
   *
   * @return The number of replicas to fetch
   */
  public int getMax() {
    return max;
  }

  /**
    * Returns a string representation of this message
   *
   * @return A string representing this message
   */
  public String toString() {
    return "[LookupHandlesMessage (response " + isResponse() + " " + response + ") for " + id + " max " + max + "]";
  }
}

