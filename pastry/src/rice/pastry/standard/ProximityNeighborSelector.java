package rice.pastry.standard;

import java.util.Collection;

import rice.Continuation;
import rice.pastry.NodeHandle;

/**
 * Finds a near neighbor (usually for bootstrapping)
 * 
 * @author Jeff Hoye
 *
 */
public interface ProximityNeighborSelector {

  void getNearHandles(Collection<NodeHandle> bootHandles, Continuation<Collection<NodeHandle>, Exception> deliverResultToMe);

}
