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
 * is dropped then it searches for a new parent. A stripe recieves all the scribe messages for the
 * topicId It handles subscribtions and data received through scribe
 *
 * @version $Id$
 * @author Ansley Post
 */
public class Stripe implements ScribeClient {

  /**
   * DESCRIBE THE FIELD
   */
  public final static int STRIPE_SUBSCRIBED = 0;

  /**
   * The constant status code associated with the unsubscribed state
   */
  public final static int STRIPE_UNSUBSCRIBED = 1;

  /**
   * The constant status code associated with the dropped state
   */
  public final static int STRIPE_DROPPED = 2;

  /**
   * The stripeId for this stripe
   */
  protected StripeId stripeId;

  /**
   * The channel this stripe is a part of.
   */
  protected Channel channel;

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
   * The stripe state, whether it is dropped, connected, etc.
   */
  protected int stripeState = STRIPE_UNSUBSCRIBED;

  /**
   * The constructor used when creating a stripe from scratch.
   *
   * @param stripeId the stripeId that this stripe will be rooted at
   * @param channel the channel this stripe belongs to
   * @param scribe DESCRIBE THE PARAMETER
   */
  public Stripe(StripeId stripeId, Scribe scribe, Channel channel) {
    this.channel = channel;
    this.stripeId = stripeId;
    this.stripeState = STRIPE_UNSUBSCRIBED;
    this.isPrimary = false;

    this.clients = new Vector();
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
   * gets the Channel that this stripe is a part of
   *
   * @return Channel Object
   */
  public Channel getChannel() {
    return channel;
  }

  /**
   * get the state of the Stripe
   *
   * @return int the State the stripe is in
   */
  public int getState() {
    return stripeState;
  }

  /**
   * Adds a client to this stripe - the client will be informed whenever data arrives for this strip
   *
   * @param client DESCRIBE THE PARAMETER
   */
  public void subscribe(SplitStreamClient client) {
    if (!clients.contains(client)) {
      clients.add(client);
    }
  }

  /**
   * Adds a client to this stripe - the client will be informed whenever data arrives for this strip
   *
   * @param client DESCRIBE THE PARAMETER
   */
  public void unsubscribe(SplitStreamClient client) {
    clients.remove(client);
  }

  /**
   * Leaves this stripe This causes us to stop getting data and to leave the scribe topic group
   */
  public void leaveStripe() {
    scribe.unsubscribe(new Topic(stripeId.getId()), this);
  }

  /**
   * Joins this stripe It causes this stripe to become subscribed and allows data to start being
   * received
   */
  public void joinStripe() {
    scribe.subscribe(new Topic(stripeId.getId()), this);
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
    // deliver the content here
  }

  /**
   * Informs this client that a child was added to a topic in which it was interested in.
   *
   * @param topic The topic to unsubscribe from
   * @param child The child that was added
   */
  public void childAdded(Topic topic, NodeHandle child) {
    // do some stuff
  }

  /**
   * Informs this client that a child was removed from a topic in which it was interested in.
   *
   * @param topic The topic to unsubscribe from
   * @param child The child that was removed
   */
  public void childRemoved(Topic topic, NodeHandle child) {
    // do stuff here
  }

  /**
   * Sets the State attribute of the Stripe object
   *
   * @param state The new State value
   */
  protected void setState(int state) {
    stripeState = state;
  }

  /**
   * Method called when this stripe is dropped
   */
  protected void dropped() {
    stripeState = STRIPE_DROPPED;
  }

}

