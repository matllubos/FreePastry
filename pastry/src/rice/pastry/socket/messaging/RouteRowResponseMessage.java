/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 

package rice.pastry.socket.messaging;

import java.io.*;

import rice.p2p.commonapi.rawserialization.*;
import rice.pastry.*;
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
  /**
   * 
   */
  private static final long serialVersionUID = 126923905375233048L;

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
  
  public RouteRowResponseMessage(InputBuffer buf, NodeHandleFactory nhf, PastryNode localNode) throws IOException {
    byte version = buf.readByte();
    switch(version) {
      case 0:
        int numRouteSets = buf.readInt();
        set = new RouteSet[numRouteSets];
        for (int i = 0; i<numRouteSets; i++) {      
          if (buf.readBoolean()) {
            set[i] = new RouteSet(buf, nhf, localNode); 
          }
        }
        break;
      default:
        throw new IOException("Unknown Version: "+version);
    }     
  }
  
}
