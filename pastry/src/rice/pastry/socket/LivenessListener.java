/*
 * Created on Jul 12, 2004
 */
package rice.pastry.socket;

import rice.pastry.NodeHandle;

/**
 * This listener is notified whenever the liveness on a node changes.
 * You can register the livenessListener with a ConnectionManager.
 * 
 * @author Jeff Hoye
 */
public interface LivenessListener {
  /**
   * Called when the liveness of a Node changes.
   * @param nh the Node that changed
   * @param liveness the new liveness value
   */
  public void updateLiveness(NodeHandle nh, int liveness);
}
