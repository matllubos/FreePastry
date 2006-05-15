
package rice.pastry.socket.messaging;

import java.io.*;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.pastry.*;
import rice.pastry.leafset.*;
import rice.pastry.socket.*;

/**
* A response message to a RoutesRequestMessage, containing the remote
 * node's routes.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class RoutesResponseMessage extends SocketMessage {
  public static final short TYPE = 13;

  private SourceRoute[] routes;
  
  /**
  * Constructor
   *
   * @param leafset The leafset of the receiver of the RoutesRequestMessage.
   */
  public RoutesResponseMessage(SourceRoute[] routes) {
    this.routes = routes;
  }
  
  /**
    * Returns the leafset of the receiver.
   *
   * @return The LeafSet of the receiver node.
   */
  public SourceRoute[] getRoutes() {
    return routes;
  }

  /***************** Raw Serialization ***************************************/  
  public short getType() {
    return TYPE;
  }

  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0); // version    
    buf.writeInt(routes.length);
    for (int i = 0; i < routes.length; i++) {
      routes[i].serialize(buf); 
    }    
  }

  public RoutesResponseMessage(InputBuffer buf) throws IOException {
    byte version = buf.readByte();
    switch(version) {
      case 0:
        int numRoutes = buf.readInt();
        routes = new SourceRoute[numRoutes];
        for (int i = 0; i<numRoutes; i++) {
          routes[i] = SourceRoute.build(buf); 
        }
        break;
      default:
        throw new IOException("Unknown Version: "+version);
    }     
  }  
}
