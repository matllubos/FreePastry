
package rice.p2p.past.messaging;

import rice.*;
import rice.environment.Environment;
import rice.p2p.commonapi.*;
import rice.p2p.past.*;

/**
 * @(#) CacheMessage.java
 *
 * This class represents message which pushes an object forward one hop in order
 * to be cached.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Ansley Post
 * @author Peter Druschel
 */
public class CacheMessage extends PastMessage {

  // the content to be cached
  protected PastContent content;
  
  /**
   * Constructor which takes a unique integer Id and the local id
   *
   * @param uid The unique id
   */
  public CacheMessage(int uid, PastContent content, NodeHandle source, Id dest) {
    super(uid, source, dest);

    this.content = content;
  }

  /**
   * Method which returns the content
   *
   * @return The content
   */
  public PastContent getContent() {
    return content;
  }

  /**
    * Method by which this message is supposed to return it's response.
   *
   * @param c The continuation to return the reponse to.
   */
  public void returnResponse(Continuation c, Environment env, String instance) {
    throw new RuntimeException("ERROR: returnResponse should not be called on cacheMessage!");
  }

  /**
   * Returns a string representation of this message
   *
   * @return A string representing this message
   */
  public String toString() {
    return "[CacheMessage for " + content + "]";
  }
}

