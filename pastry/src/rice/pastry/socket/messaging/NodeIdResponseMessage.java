
package rice.pastry.socket.messaging;

import java.io.*;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.pastry.*;
import rice.pastry.Id;

/**
 * A response message to a NodeIdRequestMessage, containing the remote
 * node's nodeId.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class NodeIdResponseMessage extends SocketMessage {
  public static final short TYPE = 7;

  private Id nid;
  
  private long epoch;

  /**
   * Constructor
   *
   * @param nid The nodeId of the receiver of the NodeIdRequestMessage.
   */
  public NodeIdResponseMessage(Id nid, long epoch) {
    this.nid = nid;
    this.epoch = epoch;
  }

  /**
   * Returns the nodeId of the receiver.
   *
   * @return The NodeId of the receiver node.
   */
  public Id getNodeId() {
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
  
  public String toString() {
    return "NodeIdResponseMessage["+nid+","+epoch+"]";
  }

  /***************** Raw Serialization ***************************************/  
  public short getType() {
    return TYPE;
  }

  public NodeIdResponseMessage(InputBuffer buf) throws IOException {
    byte version = buf.readByte();
    switch(version) {
      case 0:
        nid = Id.build(buf);
        epoch = buf.readLong();
        break;
      default:
        throw new IOException("Unknown Version: "+version);
    }     
  }

  public void serialize(OutputBuffer buf) throws IOException {    
    buf.writeByte((byte)0); // version    
    nid.serialize(buf);
    buf.writeLong(epoch);
  }
}
