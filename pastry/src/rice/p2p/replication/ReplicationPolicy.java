
package rice.p2p.replication;

import java.util.*;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.replication.messaging.*;

/**
 * @(#) ReplicationPolicy.java This interface represents a policy for Replication, 
 * which is asked whenever the replication manager need to make an application-specific
 * decision.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public interface ReplicationPolicy {
  
  /**
   * This method is given a list of local ids and a list of remote ids, and should return the
   * list of remote ids which need to be fetched.  Thus, this method should return the set
   * B-A, where the result is a subset of B.
   *
   * @param local The set of local ids
   * @param remote The set of remote ids
   * @param factory The factory to use to create IdSets
   * @return A subset of the remote ids which need to be fetched
   */
  public IdSet difference(IdSet local, IdSet remote, IdFactory factory);
  
  /**
   * The default policy for Replication, which simply does a direct diff between the sets
   *
   * @author Alan Mislove
   */
  public static class DefaultReplicationPolicy implements ReplicationPolicy {
    
    /**
     * This method simply returns remote-local.
     *
     * @param local The set of local ids
     * @param remote The set of remote ids
     * @param factory The factory to use to create IdSets
     * @return A subset of the remote ids which need to be fetched
     */
    public IdSet difference(IdSet local, IdSet remote, IdFactory factory) {
      IdSet result = factory.buildIdSet();
      Iterator i = remote.getIterator();
      
      while (i.hasNext()) {
        Id id = (Id) i.next();
        
        if (! local.isMemberId(id)) 
          result.addId(id);
      }
      
      return result;
    }
  }
}

