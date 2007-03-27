package rice.p2p.scribe;

import java.util.Collection;

import rice.p2p.commonapi.NodeHandle;

/**
 * The new interface for scribe as of FreePastry 2.1.  Handles multiple concurrent Joins/Failures.
 * 
 * @author Jeff Hoye
 *
 */
public interface ScribeMultiClient extends ScribeClient {
  
  /**
   * Informs the client that a subscribe on the given topic failed
   * - the client should retry the subscribe or take appropriate
   * action.
   *
   * @param topic The topic which the subscribe failed on
   */
  public void subscribeFailed(Collection<Topic> topics);

  /**
   * Informs the client that a subscribe on the given topic failed
   * - the client should retry the subscribe or take appropriate
   * action.
   *
   * @param topic The topic which the subscribe failed on
   */
  public void subscribeSuccess(Collection<Topic> topics);

}

