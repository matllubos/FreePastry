/**
 * "FreePastry" Peer-to-Peer Application Development Substrate Copyright 2002,
 * Rice University. All rights reserved. Redistribution and use in source and
 * binary forms, with or without modification, are permitted provided that the
 * following conditions are met: - Redistributions of source code must retain
 * the above copyright notice, this list of conditions and the following
 * disclaimer. - Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution. -
 * Neither the name of Rice University (RICE) nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission. This software is provided by RICE and the
 * contributors on an "as is" basis, without any representations or warranties
 * of any kind, express or implied including, but not limited to,
 * representations or warranties of non-infringement, merchantability or fitness
 * for a particular purpose. In no event shall RICE or contributors be liable
 * for any direct, indirect, incidental, special, exemplary, or consequential
 * damages (including, but not limited to, procurement of substitute goods or
 * services; loss of use, data, or profits; or business interruption) however
 * caused and on any theory of liability, whether in contract, strict liability,
 * or tort (including negligence or otherwise) arising in any way out of the use
 * of this software, even if advised of the possibility of such damage.
 */

package rice.pastry.wire;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

import rice.pastry.*;
import rice.pastry.dist.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;

/**
 * The WireNodeHandlePool controls all of the node handles in use by the
 * WirePastryNode. It ensures that there is only one node handle for each
 * respective pastry node.
 *
 * @version $Id: WireNodeHandlePool.java,v 1.6 2003/12/22 03:24:49 amislove Exp
 *      $
 * @author Alan Mislove
 */
public class WireNodeHandlePool extends DistNodeHandlePool {

  private HashMap handles;

  private WirePastryNode pastryNode;

  /**
   * Constructor.
   *
   * @param spn The WirePastryNode this pool will serve.
   */
  public WireNodeHandlePool(WirePastryNode spn) {
    super();

    pastryNode = spn;
    handles = new HashMap();
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param nodeId DESCRIBE THE PARAMETER
   * @return DESCRIBE THE RETURN VALUE
   */
  public WireNodeHandle get(NodeId nodeId) {
    return (WireNodeHandle) handles.get(nodeId);
  }

  public void notifyKilled() {
    synchronized(handles) {
      Iterator i = handles.values().iterator();
      while (i.hasNext()) {
        ((WireNodeHandle)i.next()).notifyKilled();
      }
    }
  }

  /**
   * The method verifies a WireNodeHandle. If a node handle to the pastry node
   * has never been seen before, an entry is added, and this node handle is
   * referred to in the future. Otherwise, this method returns the previously
   * verified node handle to the pastry node.
   *
   * @param han DESCRIBE THE PARAMETER
   * @return The node handle to use to talk to the pastry node.
   */
  public synchronized DistNodeHandle coalesce(DistNodeHandle han) {
    DistCoalesedNodeHandle handle = (DistCoalesedNodeHandle) han;
    if ((handles.get(handle.getNodeId()) == null) || (handles.get(handle.getNodeId()) == handle)) {
      handles.put(handle.getNodeId(), handle);
      handle.setIsInPool(true);
    } else {
      handle.setIsInPool(false);
    }

    DistNodeHandle response = (DistNodeHandle) handles.get(handle.getNodeId());

    return response;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @return DESCRIBE THE RETURN VALUE
   */
  public String toString() {
    String response = "";

    Iterator i = handles.keySet().iterator();

    while (i.hasNext()) {
      Object o = i.next();
      response += o + "\t->\t" + handles.get(o) + "\n";
    }

    return response;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param s DESCRIBE THE PARAMETER
   */
  private void debug(String s) {
    if (Log.ifp(6)) {
      System.out.println(pastryNode.getNodeId() + " (P): " + s);
    }
  }
}
