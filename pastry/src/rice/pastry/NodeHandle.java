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

package rice.pastry;

import rice.pastry.messaging.*;

import java.io.*;
import java.util.*;

/**
 * Interface for handles to remote nodes.
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 */
public abstract class NodeHandle extends Observable implements MessageReceiver, LocalNodeI, rice.p2p.commonapi.NodeHandle {

  // constants defining types of observable events
  public static final Integer PROXIMITY_CHANGED = new Integer(1); 
  public static final Integer DECLARED_DEAD = new Integer(2);
  public static final Integer DECLARED_LIVE = new Integer(3);

  // the local pastry node
  protected transient PastryNode localnode;

  /**
   * Gets the nodeId of this Pastry node.
   *
   * @return the node id.
   */
  public abstract NodeId getNodeId();

  public rice.p2p.commonapi.Id getId() {
    return getNodeId();
  }

  /**
   * Returns the last known liveness information about the Pastry node associated with this handle.
   * Invoking this method does not cause network activity.
   *
   * @return true if the node is alive, false otherwise.
   */
  public abstract boolean isAlive();

  /**
   * Returns the last known proximity information about the Pastry node associated with this handle.
   * Invoking this method does not cause network activity.
   *
   * Smaller values imply greater proximity. The exact nature and interpretation of the proximity metric
   * implementation-specific.
   *
   * @return the proximity metric value
   */
  public abstract int proximity();

  /**
   * Ping the node. Refreshes the cached liveness status and proximity value of the Pastry node associated
   * with this.
   * Invoking this method causes network activity.
   *
   * @return true if node is currently alive.
   */
  public abstract boolean ping();

  /**
   * Set the local PastryNode.
   *
   * @param pn local pastrynode
   */
  public final void setLocalNode(PastryNode pn) {
    localnode = pn;
    if (localnode != null) afterSetLocalNode();
  }

  /**
   * Method that can be overridden by handle to set isLocal, etc.
   */
  public void afterSetLocalNode() {}

  /**
   * Accessor method.
   */
  public final PastryNode getLocalNode() {
    return localnode;
  }

  /**
   * May be called from handle etc methods to ensure that local node has
   * been set, either on construction or on deserialization/receivemsg.
   */
  public final void assertLocalNode() {
    if (localnode == null) {
      System.out.println("PANIC: localnode is null in " + this);
      (new Exception()).printStackTrace();
    }
  }

  /**
   * Equality operator for nodehandles.
   *
   * @param obj a nodehandle object
   * @return true if they are equal, false otherwise.
   */
  public abstract boolean equals(Object obj);


  /**
   * Hash codes for nodehandles.
   *
   * @return a hash code.
   */
  public abstract int hashCode();

  /**
   * Called on deserialization. Adds itself to a pending-setLocalNode
   * list. This list is in a static (global) hash, indexed by the
   * ObjectInputStream. Refer to README.handles_localnode for details.
   */
  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
      in.defaultReadObject();
      LocalNodeI.pending.addPending(in, this);
  }
}



