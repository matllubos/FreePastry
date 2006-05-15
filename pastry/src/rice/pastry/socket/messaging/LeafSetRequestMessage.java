
package rice.pastry.socket.messaging;

import java.io.*;

import rice.p2p.commonapi.rawserialization.*;
import rice.pastry.*;
import rice.pastry.leafset.*;

/**
* Message which represents a request to get the leafset from the remote node.
*
* @version $Id$
*
* @author Alan Mislove
*/
public class LeafSetRequestMessage extends SocketMessage {

  public static final short TYPE = 4;
  /**
  * Constructor
  *
  * @param nodeId The nodeId of the node requesting.
  */
  public LeafSetRequestMessage() {
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
