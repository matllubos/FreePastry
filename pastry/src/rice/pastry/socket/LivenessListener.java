/*
 * Created on Jul 12, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.pastry.socket;

import rice.pastry.NodeHandle;

/**
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public interface LivenessListener {
  public void updateLiveness(NodeHandle nh, int liveness);
}
