
package rice.pastry.socket.messaging;

import java.io.*;

import rice.p2p.commonapi.rawserialization.*;
import rice.pastry.*;

/**
 * Message which represents a request to get a node Id from the remote node.
 * This is necessary because even though a client might know the address of a
 * remote node, it does not know it's node Id.  Therefore, the first message
 * that is sent across the wire is the NodeIdRequestMessage.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class NodeIdRequestMessage extends SocketMessage {

  public static final short TYPE = 6;

  /**
   * Constructor
   *
   * @param nodeId The nodeId of the node requesting.
   */
  public NodeIdRequestMessage() {
  }

  /***************** Raw Serialization ***************************************/  
  public short getType() {
    return TYPE;
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0); // version    
  }
  
  // Note: Deserialized in SocketManager.SMDeserializer.deserialize()
}
