
package rice.p2p.scribe;

import java.util.*;

import rice.p2p.commonapi.*;
import rice.p2p.scribe.messaging.*;

/**
 * @(#) ScribePolicy.java This interface represents a policy for Scribe, which is asked whenever a
 * child is about to be added or removed, or when the the local node is about to be implicitly
 * subscribed to a new topic.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public interface ScribePolicy {

  /**
   * This method is called when the newChild is about to become our child, and the policy should
   * return whether or not the child should be allowed to become our child. If the length of
   * children and clients is both 0, allowing the child to join will have the effect of implicitly
   * subscribing this node the the given topic.
   *
   * @param message The subscribe message in question
   * @param children The list of children who are currently subscribed to this topic
   * @param clients The list of clients are are currently subscribed to this topic
   * @return Whether or not this child should be allowed add.
   */
  public boolean allowSubscribe(SubscribeMessage message, ScribeClient[] clients, NodeHandle[] children);

  /**
   * This method is called when an anycast is received which is not satisfied at the local node.
   * This method should add both the parent and child nodes to the anycast's to-search list, but
   * this method allows different policies concerning the order of the adding as well as selectively
   * adding nodes.
   *
   * @param message The anycast message in question
   * @param parent Our current parent for this message's topic
   * @param children Our current children for this message's topic
   */
  public void directAnycast(AnycastMessage message, NodeHandle parent, NodeHandle[] children);
  
  /**
   * Informs this policy that a child was added to a topic - the topic is free to ignore this
   * upcall if it doesn't care.
   *
   * @param topic The topic to unsubscribe from
   * @param child The child that was added
   */
  public void childAdded(Topic topic, NodeHandle child);
  
  /**
   * Informs this policy that a child was removed from a topic - the topic is free to ignore this
   * upcall if it doesn't care.
   *
   * @param topic The topic to unsubscribe from
   * @param child The child that was removed
   */
  public void childRemoved(Topic topic, NodeHandle child);

  /**
   * The default policy for Scribe, which always allows new children to join and adds children in
   * the order in which they are provided, implicitly providing a depth-first search.
   *
   * @version $Id$
   * @author amislove
   */
  public static class DefaultScribePolicy implements ScribePolicy {
    
    // random number generator for directing anycasts
    protected Random random = new Random();

    /**
     * This method always return true;
     *
     * @param message The subscribe message in question
     * @param children The list of children who are currently subscribed
     * @param clients The list of clients are are currently subscribed
     * @return True.
     */
    public boolean allowSubscribe(SubscribeMessage message, ScribeClient[] clients, NodeHandle[] children) {
      return true;
    }

    /**
     * Simply adds the parent and children in order, which implements a depth-first-search.
     *
     * @param message The anycast message in question
     * @param parent Our current parent for this message's topic
     * @param children Our current children for this message's topic
     */
    public void directAnycast(AnycastMessage message, NodeHandle parent, NodeHandle[] children) {
      if (parent != null) {
        message.addLast(parent);
      }
      
      // now randomize the children list
      for (int i=0; i<children.length; i++) {
        int j = random.nextInt(children.length);
        int k = random.nextInt(children.length);
        NodeHandle tmp = children[j];
        children[j] = children[k];
        children[k] = tmp;
      }

      for (int l=0; l<children.length; l++) {
        message.addFirst(children[l]);
      }
    }
    
    /**
     * Informs this policy that a child was added to a topic - the topic is free to ignore this
     * upcall if it doesn't care.
     *
     * @param topic The topic to unsubscribe from
     * @param child The child that was added
     */
    public void childAdded(Topic topic, NodeHandle child) {
    }
    
    /**
     * Informs this policy that a child was removed from a topic - the topic is free to ignore this
     * upcall if it doesn't care.
     *
     * @param topic The topic to unsubscribe from
     * @param child The child that was removed
     */
    public void childRemoved(Topic topic, NodeHandle child) {
    }
  }

  /**
   * An optional policy for Scribe, which allows up to a specified number of children per topic.
   *
   * @version $Id$
   * @author amislove
   */
  public static class LimitedScribePolicy extends DefaultScribePolicy {

    /**
     * The number of children to allow per topic
     */
    protected int maxChildren;

    /**
     * Construtor which takes a maximum number
     *
     * @param max The maximum number of children
     */
    public LimitedScribePolicy(int max) {
      this.maxChildren = max;
    }

    /**
     * This method returns (children.length < maxChildren-1);
     *
     * @param message The subscribe message in question
     * @param children The list of children who are currently subscribed
     * @param clients The list of clients are are currently subscribed
     * @return True.
     */
    public boolean allowSubscribe(SubscribeMessage message, ScribeClient[] clients, NodeHandle[] children) {
      return (children.length < (maxChildren - 1));
    }
  }
}

