
package rice.p2p.replication;

import rice.p2p.commonapi.*;

/**
 * @(#) ReplicationClient.java
 *
 * This interface should be implemented by all applications that interact
 * with the Replica Manager.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public interface ReplicationClient {
  
  /**
   * This upcall is invoked to notify the application that is should
   * fetch the cooresponding keys in this set, since the node is now
   * responsible for these keys also.
   *
   * @param keySet set containing the keys that needs to be fetched
   * @param hint A hint as to where to find the ids in the key set.  This
   *           is where the local node heard about the keys from.
   */
  public void fetch(IdSet keySet, NodeHandle hint);
  
  /**
   * This upcall is to notify the application of the range of keys for 
   * which it is responsible. The application might choose to react to 
   * call by calling a scan(complement of this range) to the persistance
   * manager and get the keys for which it is not responsible and
   * call delete on the persistance manager for those objects.
   *
   * @param range the range of keys for which the local node is currently 
   *              responsible  
   */
  public void setRange(IdRange range);
  
  /**
   * This upcall should return the set of keys that the application
   * currently stores in this range. Should return a empty IdSet (not null),
   * in the case that no keys belong to this range.
   *
   * @param range the requested range
   */
  public IdSet scan(IdRange range);
}















































































