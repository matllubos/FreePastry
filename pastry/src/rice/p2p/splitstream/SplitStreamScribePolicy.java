package rice.p2p.splitstream;

import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;
import rice.p2p.scribe.messaging.*;

/**
 * This class represents SplitStream's policy for Scribe, which only allows children
 * according to the bandwidth manager and makes anycasts first traverse all nodes
 * who have the stripe in question as their primary stripe, and then the nodes
 * who do not.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class SplitStreamScribePolicy implements ScribePolicy {

  /**
   * A reference to this policy's splitstream object
   */
  protected SplitStream splitStream;

  /**
   * Constructor which takes a splitStream object
   *
   * @param splitStream The local splitstream
   */
  public SplitStreamScribePolicy(SplitStream splitStream) {
    this.splitStream = splitStream;
  }

  /**
   * This method only allows subscriptions if we are already subscribed to this topic -
   * if this would cause us to become implicitly subscribed, then it is not allowed.  Additionally,
   * this method asks the bandwidth manager for that topic if the child should be allowed.
   *
   * @param message The subscribe message in question
   * @param children The list of children who are currently subscribed to this topic
   * @param clients The list of clients are are currently subscribed to this topic
   * @return Whether or not this child should be allowed add.
   */
  public boolean allowSubscribe(SubscribeMessage message, ScribeClient[] clients, NodeHandle[] children) {
    return ((clients.length > 0) || (children.length > 0));
  }

  /**
   * This method adds the parent and child in such a way that the nodes who have this stripe as
   * their primary strpe are examined first.
   *
   * @param message The anycast message in question
   * @param parent Our current parent for this message's topic
   * @param children Our current children for this message's topic
   */
  public void directAnycast(AnycastMessage message, NodeHandle parent, NodeHandle[] children) {
    message.addFirst(parent);

    // NEED TO ADD CHILDREN/PARENTS SELECTIVELY HERE
  }
}

