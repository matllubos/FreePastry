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

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;
import java.net.*;

/**
 * The SocketNodeHandlePool controls all of the node handles in
 * use by the SocketPastryNode.  It ensures that there is only one
 * node handle for each respective pastry node.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class SocketNodeHandlePool {

  private HashMap handles;
  private SocketPastryNode node;

  /**
   * Constructor.
   *
   * @param spn The SocketPastryNode this pool will serve.
   */
  public SocketNodeHandlePool(SocketPastryNode spn) {
    handles = new HashMap();
    node = spn;

  }

  /**
   * The method verifies a SocketNodeHandle.  If a node handle
   * to the pastry node has never been seen before, an entry is
   * added, and this node handle is referred to in the future.
   * Otherwise, this method returns the previously verified
   * node handle to the pastry node.
   *
   * @param handle The node handle to verify.
   * @return The node handle to use to talk to the pastry node.
   */
  public SocketNodeHandle coalesce(SocketNodeHandle handle) {
    InetSocketAddress address = handle.getAddress();

    if (handles.get(address) == null) {
      handles.put(address, handle);
      node.getSocketManager().register(handle);
    }

    return (SocketNodeHandle) handles.get(address);
  }

  /**
   * Returns the SocketNodeHandle cooresponding to the
   * given address.  Returns null if there is no node handle
   * found.
   *
   * @param address The address to retrieve the node handle for.
   * @return The SocketNodeHandle for the given address.
   */
  public SocketNodeHandle get(InetSocketAddress address) {
    return (SocketNodeHandle) handles.get(address);
  }
}
