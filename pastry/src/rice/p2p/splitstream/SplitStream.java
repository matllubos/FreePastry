package rice.p2p.splitstream;

import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;

/**
 * The interface defines the methods that a splitStream object must implement. The methods are for
 * creating a channel and for attaching to an already existing channel
 *
 * @version $Id$
 * @author Ansley Post
 * @author Alan Mislove
 */
public interface SplitStream {

  /**
   * A SplitStream application calls this method to join a channel.
   *
   * @param id The id of the channel
   * @return A channel object used for subsequent operations on the desired content stream
   */
  public Channel attachChannel(ChannelId id);

  /**
   * A SplitStream application calls this method when it wishes to distribute content, creating a
   * new channel object.
   *
   * @param id The id of the channel
   * @return A new channel object
   */
  public Channel createChannel(ChannelId id);

  /**
   * Returns all of the channels on this local splitstream
   *
   * @return All of the channels currently being received by this splitstream
   */
  public Channel[] getChannels();

}
