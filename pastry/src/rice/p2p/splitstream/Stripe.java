
package rice.p2p.splitstream;

import java.io.*;
import java.util.*;

import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;

/**
 * This class encapsulates all data about an individual stripe. It is the basic unit in the system.
 * It is rooted at the stripeId for the stripe. It is through the stripe that data is sent. It can
 * be subscribed to in which case data is recieved or it can be unsubscribed from. A stripe can have
 * some number of children and controlling this is the way that bandwidth is controlled. If a stripe
 * is dropped then it searches for a new parent. 
 *
 * @version $Id$
 * @author Ansley Post
 * @author Alan Mislove
 * @author Atul Singh
 */
public class Stripe implements ScribeClient {

  /**
   * The maximum number of failed subscriptions
   */
  public static int MAX_FAILED_SUBSCRIPTION = 5;

  /**
   * The stripeId for this stripe
   */
  protected StripeId stripeId;

  /**
   * The topic corresponding to this stripeId
   */
  protected Topic topic;

  /**
   * The scribe object
   */
  protected Scribe scribe;

  /**
   * A flag whether or not this stripe is the primary stripe for this node
   */
  protected boolean isPrimary;

  /**
   * The list of SplitStreamClients interested in data from this client
   */
  protected Vector clients;

  /**
   * This stripe's channel
   */
  protected Channel channel;

  /**
   * The count of failed subscribe messages
   */
  protected Hashtable failed;

  /**
   * The constructor used when creating a stripe from scratch.
   *
   * @param stripeId the stripeId that this stripe will be rooted at
   * @param scribe the scribe the stripe is running on top of
   */
  public Stripe(StripeId stripeId, Scribe scribe, Channel channel) {
    this.stripeId = stripeId;
    this.scribe = scribe;
    this.channel = channel;
    this.isPrimary = false;
    this.failed = new Hashtable();
    if(SplitStreamScribePolicy.getPrefixMatch(this.channel.getLocalId(), stripeId.getId()) > 0)
      this.isPrimary = true;
    
    this.clients = new Vector();
    this.topic = new Topic(stripeId.getId());
  }

  /**
   * gets the StripeID for this stripe
   *
   * @return theStripeID
   */
  public StripeId getStripeId() {
    return stripeId;
  }

  /**
   * Returns whether or not this stripe is the primary stripe for the local node
   *
   * @return Whether or not this stripe is primary
   */
  public boolean isPrimary() {
    return isPrimary;
  }

  /**
   * get the state of the Stripe
   *
   * @return the State the stripe is in
   */
  public boolean isSubscribed() {
    return (clients.size() != 0);
  }

  /**
   * Adds a client to this stripe - the client will be informed whenever data arrives for this stripe
   *
   * @param client The client to add
   */
  public void subscribe(SplitStreamClient client) {
    if (! clients.contains(client)) {
      if (clients.size() == 0) {
        scribe.subscribe(topic, this);
      }

      clients.add(client);
    }
  }

  /**
   * Removes a client from this stripe - the client will no longer be informed whenever data arrives for this stripe
   *
   * @param client The client to remove
   */
  public void unsubscribe(SplitStreamClient client) {
    if (clients.contains(client)) {
      clients.remove(client);

      if (clients.size() == 0) {
        scribe.unsubscribe(topic, this);
      }
    }
  }

  /**
   * Publishes the given data to this stripe
   *
   * @param data The data to publish
   */
  public void publish(byte[] data) {
    scribe.publish(topic, new SplitStreamContent(data)); 
  }

  /**
   * This method is invoked when an anycast is received for a topic which this client is interested
   * in. The client should return whether or not the anycast should continue.
   *
   * @param topic The topic the message was anycasted to
   * @param content The content which was anycasted
   * @return Whether or not the anycast should continue
   */
  public boolean anycast(Topic topic, ScribeContent content) {
      return false;
  }

  /**
   * This method is invoked when a message is delivered for a topic this client is interested in.
   *
   * @param topic The topic the message was published to
   * @param content The content which was published
   */
  public void deliver(Topic topic, ScribeContent content) {
    if (this.topic.equals(topic)) {
      if (content instanceof SplitStreamContent) {
        byte[] data = ((SplitStreamContent) content).getData();

        SplitStreamClient[] clients = (SplitStreamClient[]) this.clients.toArray(new SplitStreamClient[0]);

        for (int i=0; i<clients.length; i++) {
          clients[i].deliver(this, data);
        }
      } else {
        System.out.println("Received unexpected content " + content);
      }
    } else {
      System.out.println("Received update for unexcpected topic " + topic + " content " + content);
    }
  }

  /**
   * Informs this client that a child was added to a topic in which it was interested in.
   *
   * @param topic The topic to unsubscribe from
   * @param child The child that was added
   */
  public void childAdded(Topic topic, NodeHandle child) {
  }

  /**
   * Informs this client that a child was removed from a topic in which it was interested in.
   *
   * @param topic The topic to unsubscribe from
   * @param child The child that was removed
   */
  public void childRemoved(Topic topic, NodeHandle child) {
  }


  /**
   * Informs this client that a subscription failed
   *
   * @param topic The topic that failed
   */
  public void subscribeFailed(Topic topic) {
    Integer count = (Integer) failed.get(topic);

    if (count == null) {
      count = new Integer(0);
    }

    if (count.intValue() < MAX_FAILED_SUBSCRIPTION) {
      count = new Integer(count.intValue() + 1);

      System.out.println("DEBUG :: Subscription failed at " + channel.getLocalId() + " for topic " + topic + " - retrying.");
      scribe.subscribe(topic, this);

      failed.put(topic, count);
    }
  }

  /**
    * Returns a String representation of this Stripe
   *
   * @return A String representing this stripe
   */
  public String toString() {
    return "Stripe " + stripeId;
  }

  /**
   * Utility method. Returns the list of children for this stripe.
   * @return A array of children.
   */
  public NodeHandle[] getChildren(){
    return this.scribe.getChildren(new Topic(this.getStripeId().getId()));
  }

  /**
   * Utility method. Returns the parent for this topic in the scribe tree.
   * @return Parent for this topic.
   */
  public NodeHandle getParent(){
    return ((ScribeImpl) this.scribe).getParent(new Topic(this.getStripeId().getId()));
  }

  /**
   * Utility method. Checks if local node is root for this topic.
   * @return True/False depending on if local node is root for this topic
   */
  public boolean isRoot(){
    return ((ScribeImpl) this.scribe).isRoot(topic);
  }
}

