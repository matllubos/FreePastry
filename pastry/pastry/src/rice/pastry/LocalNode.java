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

import java.io.*;
import java.util.*;

import rice.pastry.messaging.*;
import rice.pastry.rmi.*;

/**
 * Implementation of the LocalNodeI interface that some Serializable classes (such
 * as Certificate) extend, if they want to be kept informed of what
 * node they're on. If a class cannot use this provided implementation (for reasons
 * such as multiple inheritance), it should implement the method provided in the
 * LocalNode interface in the same manner as these.
 *
 * @version $Id$
 *
 * @author Sitaram Iyer
 * @author Alan Mislove
 */
public abstract class LocalNode implements LocalNodeI {

  // the local pastry node
  private transient PastryNode localnode;

  public LocalNode() { localnode = null; }

  /**
   * Accessor method.
   */
  public final PastryNode getLocalNode() { return localnode; }

  /**
   * Accessor method. Notifies the overridable afterSetLocalNode.
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
   * Called on deserialization. Adds itself to a pending-setLocalNode
   * list. This list is in a static (global) hash, indexed by the
   * ObjectInputStream. Refer to README.handles_localnode for details.
   */
  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    LocalNodeI.pending.addPending(in, this);
  }
}
