

package rice.rm;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;

/**
 * @(#) RM.java
 *
 * This interface is exported by Replica Manager(RM) for any applications 
 * which need to replicate objects across k+1 nodes closest to the object 
 * identifier in the NodeId space. The 'closest' (to the object identifier)
 * of the k+1 nodes is referred to as the 0-root in which the object is
 * stored by default when not using the replica manager. 
 * Additionally the RM assists in maintaining the invariant that the object
 * is also stored in the other k nodes referred to as the i-roots (1<=i<=k).
 * In the RM literature, k is called the ReplicaFactor and is used when
 * an instance of the replica manager is being instantiated.
 *
 * @version $Id$
 *
 * @author Animesh Nandi
 */

public interface RM {

    /**
     * Called by client(RMClient) to notify the RM substrate of the
     * presence of a key corresponding to a object that was 'recently'
     * inserted at itself. The RM substrate algorithm is designed on a 
     * Pull model. This call however gives the RM substrate to implement
     * the Push model if it desires so in future. The current implementation
     * this method is non-operational since we believe that the Pull model 
     * behaves sufficiently well. 
     * @param key the object identifier
     */
    public void registerKey(Id key);

    /**
     * Called by client(RMClient) to enable optimizations to route to the
     * nearest replica. Should be called by client in the context of the 
     * forward method of a lookup message. Should only be called if the local
     * client does not have the desired object. This call could change the 
     * nextHop field in the RouteMessage.
     * @param msg the RouteMessage 
     */
    public void lookupForward(RouteMessage msg);



}








