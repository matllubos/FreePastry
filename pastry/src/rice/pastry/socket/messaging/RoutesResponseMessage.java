/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate 

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/

package rice.pastry.socket.messaging;

import java.io.*;

import rice.p2p.commonapi.rawserialization.*;
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
  /**
   * 
   */
  private static final long serialVersionUID = 5047209709790420662L;

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
