
package rice.p2p.replication.manager;

import rice.*;

import rice.p2p.commonapi.*;

/**
 * @(#) ReplicationManagerClient.java
 *
 * This interface represents client of the replication manager, which is the
 * ultimate user of the replication.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public interface ReplicationManagerClient {
  
  /**
   * This upcall is invoked to tell the client to fetch the given id, 
   * and to call the given command with the boolean result once the fetch
   * is completed.  The client *MUST* call the command at some point in the
   * future, as the manager waits for the command to return before continuing.
   *
   * @param id The id to fetch
   * @param hint A hint where to find the key from.  This is where the local node
   *           heard about the key.
   * @param command The command to return the result to
   */
  public void fetch(Id id, NodeHandle hint, Continuation command);
  
  /**
   * This upcall is to notify the client that the given id can be safely removed
   * from the storage.  The client may choose to perform advanced behavior, such
   * as caching the object, or may simply delete it.
   *
   * @param id The id to remove
   */
  public void remove(Id id, Continuation command);
  
  /**
   * This upcall should return the set of keys that the application
   * currently stores in this range. Should return a empty IdSet (not null),
   * in the case that no keys belong to this range.
   *
   * @param range the requested range
   */
  public IdSet scan(IdRange range);
  
  /**
   * This upcall should return whether or not the given id is currently stored
   * by the client.
   *
   * @param id The id in question
   * @return Whether or not the id exists
   */
  public boolean exists(Id id);
  
}

