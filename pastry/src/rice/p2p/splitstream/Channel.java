package rice.p2p.splitstream;

import java.io.Serializable;
import java.util.*;

import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;

/**
 * The channel controls all the meta data associated with a group of stripes. It contains the
 * stripes themselves plus any sparecapcity groups associated with the group of stripes. It also
 * manages the amount of bandwidth that is used by this collection of stripes. A Channel is created
 * by giving it a name which is then hashed to come up with a channelId which uniquely identifies
 * this channel. If other nodes want to join the channel they attach to it. ( Join the scribe group
 * ) This is the channel object that represents a group of stripes in SplitStream.
 *
 * @version $Id$
 * @author Ansley Post
 * @author Alan Mislove
 */
public class Channel {

  /**
   * The default number of stripes to create
   */
  public static int STRIPE_BASE = 4;
  
  /**
   * ChannelId for this channel
   */
  protected ChannelId channelId;

  /**
   * The list of stripeIds for this channel
   */
  protected StripeId[] stripeIds;

  /**
   * The list of stripeIds for this channel
   */
  protected Stripe[] stripes;

  /**
   * The scribe object associated with this node
   */
  protected Scribe scribe;

  /**
   * The splitStreamImpl object associated with this node, this is needed to have access to the
   * pastry messages
   */
  protected SplitStream splitStream;

  /**
   * The bandwidth manager for this channel, responsible for keeping track of the number of
   * children, and then deciding when to take on children.
   */
  protected BandwidthManager bandwidthManager;

  /**
   * Constructor to create a new channel from scratch
   *
   * @param splitStream the splitStream instance for this node
   * @param channelId DESCRIBE THE PARAMETER
   * @param scribe DESCRIBE THE PARAMETER
   */
  public Channel(ChannelId channelId, Scribe scribe, SplitStream splitStream) {

    /*
     *  Initialize Member variables
     */
    this.bandwidthManager = new BandwidthManagerImpl();
    this.scribe = scribe;
    this.splitStream = splitStream;
    this.channelId = channelId;

    /*
     *  Create the stripe id and stripe arrays
     */
    stripeIds = generateStripeIds(channelId, splitStream.getNode().getIdFactory());
    stripes = new Stripe[stripeIds.length];

    /*
     *  Create the stripes
     */
    for (int i = 0; i < stripeIds.length; i++) {
      stripes[i] = new Stripe(stripeIds[i], scribe, this, splitStream);
    }
  }

  /**
   * Gets the bandwidth manager for this channel.
   *
   * @return BandwidthManager the BandwidthManager for this channel
   */
  public BandwidthManager getBandwidthManager() {
    return bandwidthManager;
  }

  /**
   * Gets the channelId for this channel
   *
   * @return ChannelId the channelId for this channel
   */
  public ChannelId getChannelId() {
    return channelId;
  }

  /**
   * At any moment a node is subscribed to at least 1 but possibly more stripes. They will always be
   * subscribed to their primary Stripe.
   *
   * @return Vector the Stripes this node is subscribed to.
   */
  public Stripe[] getStripes() {
    return stripes;
  }

  /**
   * The primary stripe is the stripe that the user must have.
   *
   * @return Stripe The Stripe object that is the primary stripe.
   */
  protected Stripe getPrimaryStripe() {
    return null;
    //INSERT LOGIC HERE
  }

  /**
   * Creates and returns the Ids associated with the provided channelId
   *
   * @param channelId The id of the channel
   * @return The array of stripeIds based on this channelId
   */
  protected static StripeId[] generateStripeIds(ChannelId id, IdFactory factory) {
/*    int num = Math.exp(2, STRIPE_BASE);
    StripeId[] stripeIds = new StripeId[num];

    for (int i=0; i<num; i++) {
      byte[] array = id.toByteArray();
      // NEED TO SET ARRAY HERE TO BE APPROPRIATELY PREFIXED
      stripeIds[i] = new StripeId(array);
    }

    return stripeIds; */
    return null;
  }
}
