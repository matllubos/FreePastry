
package rice.pastry.socket.messaging;

import java.io.*;

import rice.pastry.*;

/**
 * A response message to a NodeIdRequestMessage, containing the remote
 * node's nodeId.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class NodeIdResponseMessage extends SocketMessage {

  private NodeId nid;
  
  private long epoch;

  /**
   * Constructor
   *
   * @param nid The nodeId of the receiver of the NodeIdRequestMessage.
   */
  public NodeIdResponseMessage(NodeId nid, long epoch) {
    this.nid = nid;
    this.epoch = epoch;
  }

  /**
   * Returns the nodeId of the receiver.
   *
   * @return The NodeId of the receiver node.
   */
  public NodeId getNodeId() {
    return nid;
  }
  
  /**
   * Returns the epoch of this address
   *
   * @return The epoch
   */
  public long getEpoch() {
    return epoch;
  }
}
