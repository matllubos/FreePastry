package rice.p2p.splitstream;

import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;

/**
 * This class is responsible for freeing bandwidth when it is needed. Right now the notion of
 * bandwidth is slightly ill-defined. It can be defined in terms of stripes or bytes. This can also
 * be per application, per channel or globally. The first cut at freeing badwidth is handled by the
 * fact that you can drop children from a stripe. After that you can start dropping non-primary
 * stripes for each channel. Finally you must come up with some method for dropping channels. You
 * must handle user requests at some higher priority than what is going on in the background. There
 * are many ways to weigh each of these priorities and there must be some more discussion on which
 * is best.
 *
 * @version $Id$
 * @author Ansley Post
 * @author Alan Mislove
 */
public class BandwidthManagerImpl implements BandwidthManager {

  /**
   * This is the default amount of outgoing bandwidth that a channel may have if no call to
   * setDefaultBandwidth has been made. Channels may individually call setDefaultBandwidth to change
   * the number of outgoing bandwidth they may take on.
   */
  public static int DEFAULT_BANDWIDTH = 16;

  /**
   * Hashtable to keep track of all Channels registered with the bandwidth manager's max bandwidth.
   */
  protected int maxBandwidth;

  /**
   * Hashtable to keep track of all Channels registered with the bandwidth manager's used bandwidth.
   */
  protected int usedBandwidth;

  /**
   * Constructor
   */
  public BandwidthManagerImpl() {
    maxBandwidth = DEFAULT_BANDWIDTH;
    usedBandwidth = 0;
  }

  /**
   * Gets the value of the default bandwidth for a newly created channel
   *
   * @return int the value of defaultBandwidth
   */
  public int getDefaultBandwidth() {
    return DEFAULT_BANDWIDTH;
  }

  /**
   * Gets the bandwidth a channel is currently using.
   *
   * @return int the bandwidth used
   */
  public int getUsedBandwidth() {
    return usedBandwidth;
  }

  /**
   * Gets the max bandwidth for a channel.
   *
   * @return int the bandwidth used
   */
  public int getMaxBandwidth() {
    return maxBandwidth;
  }

  /**
   * Define the Default Bandwidth for a newly created Channel
   *
   * @param bandwidth The new DefaultBandwidth value
   */
  public void setDefaultBandwidth(int bandwidth) {
    DEFAULT_BANDWIDTH = bandwidth;
  }

  /**
   * Adjust the max bandwidth for this channel.
   *
   * @param bandwidth DESCRIBE THE PARAMETER
   */
  public void setMaxBandwidth(int bandwidth) {
    maxBandwidth = bandwidth;
  }

  /**
   * This method makes an attempt to free up bandwidth when it is needed. It follows the basic
   * outline as describe above,not completely defined.
   *
   * @return The number of bandwidth units freed
   */
  public int freeBandwidth() {
    return 0;
  }

  /**
   * Determines based upon capacity information whether the system can take on another child.
   *
   * @return whether we can take on another child
   */
  public boolean canTakeChild() {
    return (getUsedBandwidth() < getMaxBandwidth());
  }

  /**
   * Should be called when the channel takes on a new child. Adds a unit of used bandwidth.
   */
  public void incrementUsedBandwidth() {
    usedBandwidth++;
  }

  /**
   * Should be called when the channel drops a child. Removes a unit of used bandwidth.
   */
  public void decrementUsedBandwidth() {
    usedBandwidth--;
  }
}

