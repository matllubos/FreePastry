
package rice.pastry.dist;

import rice.pastry.*;

/**
 * The DistNodeHandlePool controls all of the node handles in
 * use by the DistPastryNode.  It ensures that there is only one
 * "active" node handle for each remote pastry node.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public abstract class DistNodeHandlePool {

  /**
   * Constructor.
   */
  public DistNodeHandlePool() {
  }

  /**
   * The method verifies a DistNodeHandle.  If a node handle
   * to the pastry node has never been seen before, an entry is
   * added, and this node handle is referred to in the future.
   * Otherwise, this method returns the previously verified
   * node handle to the pastry node.
   *
   * @param handle The node handle to verify.
   * @return The node handle to use to talk to the pastry node.
   */
  public abstract DistNodeHandle coalesce(DistNodeHandle handle);
}
