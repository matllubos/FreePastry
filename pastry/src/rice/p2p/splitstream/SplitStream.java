package rice.p2p.splitstream;

import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;

/**
 * The interface defines the methods that a splitStream object must implement. The methods are for
 * creating a channel and for attaching to an already existing channel
 *
 * @version $Id$
 * @author Ansley Post
 */
public interface SplitStream {

  /**
   * Method which returns the underlying node for this splitstream object
   *
   * @return The node underlying this splitstream
   */
  public Node getNode();

  /**
   * Method which returns the underlying endpoint for this splitstream object
   *
   * @return The endpoint underlying this splitstream
   */
  public Endpoint getEndpoint();

  /**
   * A SplitStream application calls this method to join a channel.
   *
   * @param id DESCRIBE THE PARAMETER
   * @return A channel object used for subsequent operations on the desired content stream
   */
  public Channel attachChannel(ChannelId id);

  /**
   * A SplitStream application calls this method when it wishes to distribute content, creating a
   * new channel object.
   *
   * @param id DESCRIBE THE PARAMETER
   * @return A new channel object
   */
  public Channel createChannel(ChannelId id);

}
