
package rice.pastry.socket.messaging;

import java.io.*;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.pastry.*;
import rice.pastry.leafset.*;

/**
* A response message to a LeafSetRequestMessage, containing the remote
* node's leafset.
*
* @version $Id$
*
* @author Alan Mislove
*/
public class LeafSetResponseMessage extends SocketMessage {
  public static final short TYPE = 5;

  private LeafSet leafset;

  /**
  * Constructor
  *
  * @param leafset The leafset of the receiver of the LeafSetRequestMessage.
  */
  public LeafSetResponseMessage(LeafSet leafset) {
    this.leafset = leafset;
  }

  /**
    * Returns the leafset of the receiver.
    *
    * @return The LeafSet of the receiver node.
    */
  public LeafSet getLeafSet() {
    return leafset;
  }
  
  /***************** Raw Serialization ***************************************/  
  public short getType() {
    return TYPE;
  }

  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0); // version    
    leafset.serialize(buf);
  }
  
  public LeafSetResponseMessage(InputBuffer buf, NodeHandleFactory nhf) throws IOException {
    byte version = buf.readByte();
    switch(version) {
      case 0:
        leafset = LeafSet.build(buf,nhf);
        break;
      default:
        throw new IOException("Unknown Version: "+version);
    }     
  }
  

}
