/*
 * Created on Aug 5, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.pastry.churn;

import java.io.Serializable;

import rice.pastry.NodeHandle;

/**
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class LivenessHandle implements Serializable {
  int liveness;
  NodeHandle nh;
  
  public LivenessHandle(NodeHandle nh) {
    this.liveness = nh.getLiveness();  
    this.nh = nh;
  }
}
