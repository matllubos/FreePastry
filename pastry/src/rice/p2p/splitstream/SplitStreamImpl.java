package rice.p2p.splitstream;

import java.io.*;
import java.util.*;

import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;

/**
 * This is the implementing class of the ISplitStream interface. It provides the functionality of
 * creating and attaching to channels. It also provides a lot of implementation details. It handles
 * the creation of Channel objects in the path of the Channel tree. It also monitors the creation of
 * stripes interior to the tree and keeps track of the bandwidth used until the user subscribes to
 * the channel. It implements the IScribeApp interface for this reason. Since it only need to keep
 * track of these numbers it does not need to implement the entire IScribeApp interface only the
 * subscribe handler. @(#) SplitStreamImpl.java
 *
 * @version $Id$
 * @author Ansley Post
 */
public class SplitStreamImpl implements SplitStream, Application {

  /**
   * The scribe instance for this SplitStream Object
   */
  private Scribe scribe = null;

  /**
   * The node that this application is running on
   */
  private Node node;

  /**
   * The endpoint that this application is running on
   */
  private Endpoint endpoint;

  /**
   * Hashtable of all the channels currently created on this node implicitly or explicitly.
   */
  private Hashtable channels;

  /**
   * A mapping between channelId -> Vector of clients
   */
  private Hashtable clients;

  /**
   * The constructor for building the splitStream object
   *
   * @param node the pastry node that we will use
   * @param scribe the scribe instance to use
   * @param instance DESCRIBE THE PARAMETER
   */
  public SplitStreamImpl(Node node, Scribe scribe, String instance) {
    this.scribe = scribe;
    this.node = node;
    this.endpoint = node.registerApplication(this, instance);
    this.channels = new Hashtable();
  }

  /**
   * This method is used by a peer who wishes to distribute the content using SplitStream. It
   * creates a Channel Object consisting of numStripes number of Stripes, one for each stripe's
   * content. A Channel object is responsible for implementing SplitStream functionality, like
   * maintaining multiple multicast trees, bandwidth management and discovering parents having spare
   * capacity. One Channel object should be created for each content distribution which wishes to
   * use SplitStream.
   *
   * @param id DESCRIBE THE PARAMETER
   * @return an instance of a Channel class.
   */
  public Channel createChannel(ChannelId id) {
    Channel channel = new Channel(id, scribe, this);
    return channel;
  }

  /**
   * This method is used by peers who wish to listen to content distributed by some other peer using
   * SplitStream. It attaches the local node to the Channel which is being used by the source peer
   * to distribute the content. Essentially, this method finds out the different parameters of
   * Channel object which is created by the source, (the peer distributing the content) , and then
   * creates a local Channel object with these parameters and returns it. This is a non-blocking
   * call so the returned Channel object may not be initialized with all the parameters, so
   * applications should wait for channelIsReady() notification made by channels when they are
   * ready.
   *
   * @param id DESCRIBE THE PARAMETER
   * @return An instance of Channel object.
   */
  public Channel attachChannel(ChannelId id) {
    Channel channel = (Channel) channels.get(id);

    if (channel == null) {
      channel = new Channel(id, scribe, this);
      channels.put(id, channel);
    }

    return channel;
  }

  /**
   * This method is invoked on applications when the underlying node is about to forward the given
   * message with the provided target to the specified next hop. Applications can change the
   * contents of the message, specify a different nextHop (through re-routing), or completely
   * terminate the message.
   *
   * @param message The message being sent, containing an internal message along with a destination
   *      key and nodeHandle next hop.
   * @return Whether or not to forward the message further
   */
  public boolean forward(RouteMessage message) {
    return true;
  }

  /**
   * This method is called on the application at the destination node for the given id.
   *
   * @param id The destination id of the message
   * @param message The message being sent
   */
  public void deliver(Id id, Message message) {
    // DO stuff here
  }

  /**
   * This method is invoked to inform the application that the given node has either joined or left
   * the neighbor set of the local node, as the set would be returned by the neighborSet call.
   *
   * @param handle The handle that has joined/left
   * @param joined Whether the node has joined or left
   */
  public void update(NodeHandle handle, boolean joined) {
  }
}

