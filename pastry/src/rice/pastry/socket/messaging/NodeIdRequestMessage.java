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

  /**
   * 
   */
  private static final long serialVersionUID = -8098453067976812984L;
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
