package rice.p2p.splitstream;

import java.util.*;

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
   * The default maximum number of children per channel
   */
  public static int DEFAULT_MAXIMUM_CHILDREN = 20;

  /**
   * A reference to this policy's splitstream object
   */
  protected SplitStream splitStream;
  
  /**
   * A reference to this policy's scribe object
   */
  protected Scribe scribe;

  /**
   * A mapping from channelId -> maximum children
   */
  protected Hashtable policy;

  /**
   * Constructor which takes a splitStream object
   *
   * @param splitStream The local splitstream
   */
  public SplitStreamScribePolicy(Scribe scribe, SplitStream splitStream) {
    this.scribe = scribe;
    this.splitStream = splitStream;
    this.policy = policy;
  }

  /**
   * Gets the max bandwidth for a channel.
   *
   * @param id The id to get the max bandwidth of
   * @return The amount of bandwidth used
   */
  public int getMaxChildren(ChannelId id) {
    Integer max = (Integer) policy.get(id);

    if (max == null) {
      return DEFAULT_MAXIMUM_CHILDREN;
    } else {
      return max.intValue();
    }
  }

  /**
   * Adjust the max bandwidth for this channel.
   *
   * @param id The id to get the max bandwidth of
   * @param children The new maximum bandwidth for this channel
   */
  public void setMaxChildren(ChannelId id, int children) {
    policy.put(id, new Integer(children));
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
    if ((clients.length == 0) && (children.length == 0)) {
      return false;
    }

    Channel channel = getChannel(message.getTopic());

    if (channel == null) {
      return false;
    } else {
      int max = getMaxChildren(channel.getChannelId());

      return (getTotalChildren(channel) < (max - 1));
    }
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

  /**
   * Returns the Channel which contains the stripe cooresponding to the
   * provided topic.
   *
   * @param topic The topic in question
   * @return The channel which contains a cooresponding stripe
   */
  private Channel getChannel(Topic topic) {
    Channel[] channels = splitStream.getChannels();

    for (int i=0; i<channels.length; i++) {
      Channel channel = channels[i];
      Stripe[] stripes = channel.getStripes();

      for (int j=0; j<stripes.length; j++) {
        Stripe stripe = stripes[j];

        if (stripe.getStripeId().getId().equals(topic.getId())) {
          return channel;
        }
      }
    }

    return null;
  }

  /**
   * Returns the total number of children for the given channel
   *
   * @param channel The channel to get the children for
   * @return The total number of children for that channel
   */
  private int getTotalChildren(Channel channel) {
    int total = 0;
    Stripe[] stripes = channel.getStripes();

    for (int j=0; j<stripes.length; j++) {
      total += scribe.getChildren(new Topic(stripes[j].getStripeId().getId())).length;
    }

    return total;
  }
}

