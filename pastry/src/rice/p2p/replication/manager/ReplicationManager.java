
package rice.p2p.replication.manager;

import rice.p2p.commonapi.*;
import rice.p2p.replication.*;

/**
 * @(#) Replication.java
 *
 * This interface represents a service run on top of the basic replication
 * service which queues the list of keys to be fetched, inserts delays between
 * successive fetches, performs backoffs in the case of network congestion,
 * and generally makes it easier for replication clients.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public interface ReplicationManager {
  
  /**
   * Returns the internal replication object used by this manager.  This internal
   * object should not be messed with.
   *
   * @return The internal replication utility
   */
  public Replication getReplication();
  
}








