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
 * @author Atul Singh
 */
public class Channel {

  /**
   * The default number of stripes to create
   */
  public static int DEFAULT_NUM_STRIPES = 16;

  /**
   * ChannelId for this channel
   */
  protected ChannelId channelId;

  /**
   * The Node id the spare capacity tree is rooted at.
   */
  protected SpareCapacityId spareCapacityId;

  /**
   * The list of stripeIds for this channel
   */
  protected StripeId[] stripeIds;

  /**
   * The list of stripeIds for this channel
   */
  protected Stripe[] stripes;

  /**
   * The number of stripes contained in this channel.
   */
  protected int numStripes;

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
    this.numStripes = DEFAULT_NUM_STRIPES;
    this.scribe = scribe;
    this.splitStream = splitStream;
    this.channelId = channelId;

    /*
     *  Create the Spare Capacity Group
     */
    spareCapacityId = null;
    // (SpareCapacityId) splitStream.createId(name + "SPARE_CAPACITY");

    /*
     *  Create the stripe id and stripe arrays
     */
    stripeIds = new StripeId[numStripes];
    stripes = new Stripe[numStripes];
    //(StripeId[]) splitStream.createIds(name + "STRIPES", numStripes);

    /*
     *  Generate the StripeIds
     */
    // generateStripeIds();

    /*
     *  Create the stripes
     */
    for (int i = 0; i < numStripes; i++) {
      stripes[i] = new Stripe(stripeIds[i], scribe, this);
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
   * The spare capacity group id.
   *
   * @return The address of this channel's spare capacity group
   */
  protected SpareCapacityId getSpareCapacityId() {
    return spareCapacityId;
  }
}
