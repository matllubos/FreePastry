
package rice.p2p.replication;

import rice.p2p.commonapi.*;

/**
 * @(#) Replication.java
 *
 * This interface is exported by Replication Manager (RM) for any applications 
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
 * @author Alan Mislove
 */
public interface Replication {
  
  /**
   * Method which invokes the replication process.  This should not normally be called by
   * applications, as the Replication class itself periodicly invokes this process.  However,
   * applications are allowed to use this method to initiate a replication request.
   */
  public void replicate();
  
}








