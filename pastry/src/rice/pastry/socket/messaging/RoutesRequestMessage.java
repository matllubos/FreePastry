
package rice.pastry.socket.messaging;

import java.io.*;

import rice.p2p.commonapi.rawserialization.OutputBuffer;
import rice.pastry.*;
import rice.pastry.leafset.*;

/**
* Message which represents a request to get the leafset from the remote node.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class RoutesRequestMessage extends SocketMessage {
  public static final short TYPE = 12;

  /**
  * Constructor
   *
   * @param nodeId The nodeId of the node requesting.
   */
  public RoutesRequestMessage() {
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
