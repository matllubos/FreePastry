
package rice.p2p.scribe;

import rice.*;
import rice.p2p.commonapi.*;

/**
 * @(#) ScribeClient.java
 *
 * This interface represents a client using the Scribe system.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public interface ScribeClient {

  /**
   * This method is invoked when an anycast is received for a topic
   * which this client is interested in.  The client should return
   * whether or not the anycast should continue.
   *
   * @param topic The topic the message was anycasted to
   * @param content The content which was anycasted
   * @return Whether or not the anycast should continue
   */
  public boolean anycast(Topic topic, ScribeContent content);

  /**
   * This method is invoked when a message is delivered for a topic this
   * client is interested in.
   *
   * @param topic The topic the message was published to
   * @param content The content which was published
   */
  public void deliver(Topic topic, ScribeContent content);

  /**
   * Informs this client that a child was added to a topic in
   * which it was interested in.
   *
   * @param topic The topic to unsubscribe from
   * @param child The child that was added
   */
  public void childAdded(Topic topic, NodeHandle child);

  /**
   * Informs this client that a child was removed from a topic in
   * which it was interested in.
   *
   * @param topic The topic to unsubscribe from
   * @param child The child that was removed
   */
  public void childRemoved(Topic topic, NodeHandle child);

  /**
   * Informs the client that a subscribe on the given topic failed
   * - the client should retry the subscribe or take appropriate
   * action.
   *
   * @param topic The topic which the subscribe failed on
   */
  public void subscribeFailed(Topic topic);

}

