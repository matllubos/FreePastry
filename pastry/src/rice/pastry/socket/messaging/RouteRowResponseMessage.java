
package rice.pastry.socket.messaging;

import java.io.*;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.pastry.*;
import rice.pastry.leafset.*;
import rice.pastry.routing.*;

/**
* A response message to a RouteRowRequestMessage, containing the remote
* node's routerow.
*
* @version $Id$
*
* @author Alan Mislove
*/
public class RouteRowResponseMessage extends SocketMessage {
  public static final short TYPE = 11;

  private RouteSet[] set;

  /**
  * Constructor
  *
  * @param leafset The leafset of the receiver of the RouteRowRequestMessage.
  */
  public RouteRowResponseMessage(RouteSet[] set) {
    this.set = set;
  }

  /**
    * Returns the routeset of the receiver.
    *
    * @return The RouteSet of the receiver node.
    */
  public RouteSet[] getRouteRow() {
    return set;
  }

  /***************** Raw Serialization ***************************************/  
  public short getType() {
    return TYPE;
  }

  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0); // version    
    buf.writeInt(set.length);
    for (int i = 0; i < set.length; i++) {
      if (set[i] == null) {
        buf.writeBoolean(false);
      } else {
        buf.writeBoolean(true);
        set[i].serialize(buf); 
      }
    }    
  }
  
  public RouteRowResponseMessage(InputBuffer buf, NodeHandleFactory nhf) throws IOException {
    byte version = buf.readByte();
    switch(version) {
      case 0:
        int numRouteSets = buf.readInt();
        set = new RouteSet[numRouteSets];
        for (int i = 0; i<numRouteSets; i++) {      
          if (buf.readBoolean()) {
            set[i] = new RouteSet(buf, nhf); 
          }
        }
        break;
      default:
        throw new IOException("Unknown Version: "+version);
    }     
  }
  
}
