package rice.p2p.splitstream;

import java.util.*;

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
 */
public interface BandwidthManager {

  /**
   * This method makes an attempt to free up bandwidth when it is needed. It follows the basic
   * outline as describe above, not completely defined.
   *
   * @return int The amount of bandwidth that was freed
   */
  public int freeBandwidth();

  /**
   * Define the Default Bandwidth for a newly created Channel
   *
   * @param bandwidth The new DefaultBandwidth value
   */
  public void setDefaultBandwidth(int bandwidth);

  /**
   * Gets the value of the default bandwidth for a newly created channel
   *
   * @return the value of defaultBandwidth
   */
  public int getDefaultBandwidth();

  /**
   * Gets the max bandwidth for a channel.
   *
   * @return The amount of bandwidth used
   */
  public int getMaxBandwidth();

  /**
   * Adjust the max bandwidth for this channel.
   *
   * @param bandwidth The new maximum bandwidth for this channel
   */
  public void setMaxBandwidth(int bandwidth);

  /**
   * Should be called when the channel takes on a new child. Adds a unit of used bandwidth.
   */
  public void incrementUsedBandwidth();

  /**
   * Should be called when the channel drops a child. Removes a unit of used bandwidth.
   */
  public void decrementUsedBandwidth();

  /**
   * Determines based upon capacity information whether the system can take on another child.
   *
   * @return whether we can take on another child
   */
  public boolean canTakeChild();

  /**
   * Gets the bandwidth a channel is currently using.
   *
   * @return int the bandwidth used
   */
  public int getUsedBandwidth();

}

