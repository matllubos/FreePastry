/**************************************************************************

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

package rice.pastry.socket;

import rice.pastry.*;
import rice.pastry.routing.*;
import rice.pastry.messaging.*;
import rice.pastry.socket.messaging.*;

import java.net.*;
import java.util.*;

/**
 * Class which assists node handles in sending and receiving ping
 * messages in socket-based pastry.  Any incoming ping message gets
 * dispatched to this classes - if the message is a PingMessage, the
 * manager generates the PingResponseMessage and sends it back over
 * the wire.  If the message is a PingResponseMessage, this class
 * informs the appropriate node handle of the returned ping via the
 * pingResponse() method.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class SocketPingManager implements MessageReceiver {

  // the pastry node
  private SocketPastryNode pastryNode;

  // the pastry node's security manager
  private SocketPastrySecurityManager securityManager;

  /**
   * Constructor.
   *
   * @param spn The pastry node this manager will server.
   * @param manager The security manager for this pastry node.
   */
  public SocketPingManager(SocketPastryNode spn, SocketPastrySecurityManager manager) {
    pastryNode = spn;
    securityManager = manager;
  }

  public Address getAddress() {
    return SocketPingManagerAddress.instance();
  }

  /**
   * Is called whenever a message arrives. This class either generates and
   * writes out a response, or informs the appropriate node handle of this
   * ping response.
   *
   * @param msg The incoming message.
   */
  public void receiveMessage(Message msg) {
    if (msg instanceof PingMessage) {
      PingMessage pm = (PingMessage) msg;

      SocketNodeHandle handle = new SocketNodeHandle(pm.getLocalAddress(), pm.getNodeId(), pastryNode);
      handle = (SocketNodeHandle) securityManager.verifyNodeHandle(handle);

      handle.receiveMessage(pm.getResponse());
    } else if (msg instanceof PingResponseMessage) {
      PingResponseMessage prm = (PingResponseMessage) msg;

      SocketNodeHandle handle = pastryNode.getNodeHandlePool().get(prm.getRemoteAddress());

      if (handle != null) {
        handle.pingResponse(prm.getTime());
      }
    }
  }
}
