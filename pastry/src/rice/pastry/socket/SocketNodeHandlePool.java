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

package rice.pastry.socket;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Vector;
import java.util.WeakHashMap;

import rice.pastry.dist.DistNodeHandle;
import rice.pastry.dist.DistNodeHandlePool;

/**
 * The DistNodeHandlePool controls all of the node handles in use by the
 * DistPastryNode. It ensures that there is only one "active" node handle for
 * each remote pastry node, as well as proper memory management.
 *
 * @version $Id: SocketNodeHandlePool.java,v 1.4 2003/12/22 03:24:47 amislove
 *      Exp $
 * @author Alan Mislove, Jeff Hoye
 */
public class SocketNodeHandlePool extends DistNodeHandlePool {

  /**
   * The node which this pool serves
   */
  protected SocketPastryNode node;

  protected SocketCollectionManager scm;

  /**
   * A mapping containing references to all of the handles in the system
   */
  protected WeakHashMap handles;

  /**
   * Constructor.
   *
   * @param node DESCRIBE THE PARAMETER
   */
  public SocketNodeHandlePool(SocketPastryNode node) {
    this.node = node;
    node.setSocketNodeHandlePool(this);
    handles = new WeakHashMap();
  }

  /**
   * The method verifies a DistNodeHandle. If a node handle to the pastry node
   * has never been seen before, an entry is added, and this node handle is
   * referred to in the future. Otherwise, this method returns the previously
   * verified node handle to the pastry node.
   *
   * @param handle The node handle to verify.
   * @return The node handle to use to talk to the pastry node.
   */
  public DistNodeHandle coalesce(DistNodeHandle handle) {
    return handle;
  }

  /**
   * This method should be called by a newly constructed node handle, or by a
   * handle which has just arrived at a new node. It records the handle,
   * allowing observers on the handle to be properly updated.
   *
   * @param handle The newly created/deserialized handle
   */
  protected void record(SocketNodeHandle handle) {
    //System.out.println("SNHP.record("+handle+")");
    Vector vector = (Vector) handles.get(handle.getAddress());

    if (vector == null) {
      vector = new Vector();
      handles.put(handle, vector);
    }

    vector.addElement(new WeakReference(handle));
    removeNullReferences(vector);
  }
  
  /**
   * Helper for record(), removes all the WeakReferences() that are empty.
   * @param v
   */
  private void removeNullReferences(Vector v) {
    synchronized(v) {
    Iterator i = v.iterator();
      while (i.hasNext()) {
        WeakReference wr = (WeakReference)i.next();
        if (wr.get() == null) {
          i.remove();
        }
      }    
    }
  }

  /**
   * This method updates all of the handles to the given address with the
   * specified update.
   *
   * @param update The update to notify the handles of
   * @param address DESCRIBE THE PARAMETER
   */
  protected void update(SocketNodeHandle snh, Object update) {
    Vector vector = (Vector) handles.get(snh);
//    System.out.println("SNHP.update()"+vector);
    
    if (vector != null) {
      boolean printed = false;

      for (int i = 0; i < vector.size(); i++) {
        SocketNodeHandle handle = (SocketNodeHandle) ((WeakReference) vector.elementAt(i)).get();

        if (handle != null) {
          if (!printed) {
            if (update == SocketNodeHandle.DECLARED_DEAD) {
              System.out.println(node.getNodeId() + " found " + handle.getNodeId() + " to be dead.");
            } else if (update == SocketNodeHandle.DECLARED_LIVE) {
              System.out.println(node.getNodeId() + " found " + handle.getNodeId() + " to be alive again.");
            }

            printed = true;
          }

          handle.update(update);
        } else {
          vector.removeElementAt(i);
          i--;
        }
      }
    }
  }  
}
