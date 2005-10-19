package rice.splitstream;

import rice.pastry.*;
/**
 * The interface defines the methods that a splitStream object
 * must implement. The methods are for creating
 * a channel and for attaching to an already existing channel
 *
 * @version $Id$
 * @author Ansley Post
 *
 * @deprecated This version of SplitStream has been deprecated - please use the version
 *   located in the rice.p2p.splitstream package.
 */
public interface ISplitStream{

   /**
    * A SplitStream application calls this method to join a
    * channel.
    * @param channelId The id of the channel to join
    * @return A channel object used for subsequent
    * operations on the desired content stream
    */
   public Channel attachChannel(ChannelId channelId);

   /**
    * A SplitStream application calls this method when it wishes
    * to distribute content, creating a new channel object.
    * @param numStripes The desired number of stripes to split
    * content into
    * @return A new channel object
    */
   Channel createChannel(int numStripes, String name);

}
