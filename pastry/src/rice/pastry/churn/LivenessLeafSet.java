/*
 * Created on Aug 5, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.pastry.churn;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import rice.pastry.NodeHandle;
import rice.pastry.leafset.LeafSet;
import rice.pastry.socket.SocketNodeHandle;

/**
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class LivenessLeafSet implements Serializable {
  Collection leafSet;

  public LivenessLeafSet(LeafSet leafSet) {
    this.leafSet = new ArrayList();
    for (int i=-leafSet.ccwSize(); i<=leafSet.cwSize(); i++) {
      SocketNodeHandle snh = (SocketNodeHandle)leafSet.get(i); 
      this.leafSet.add(new LivenessHandle(snh));
    }
  }
  
  public int getLiveness(NodeHandle nh) {
    Iterator i = leafSet.iterator();
    while(i.hasNext()) {
      LivenessHandle lh = (LivenessHandle)i.next();
      if (lh.nh.equals(nh))
        return lh.liveness;
    }
    return NodeHandle.LIVENESS_UNKNOWN;
  }

  /**
   * 
   * @param liveness the liveness to match
   * @return Collection of the list matching liveness.
   */
	public Collection getSet(int liveness) {
    Collection list = new ArrayList();
    Iterator i = leafSet.iterator();
    while(i.hasNext()) {
      LivenessHandle lh = (LivenessHandle)i.next();
      if (lh.liveness > liveness)
        list.add(lh);
    }
    return list;
	}
}
