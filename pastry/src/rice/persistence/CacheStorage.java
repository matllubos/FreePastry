package rice.persistence;
/*
 * @(#) PersistenceManager.java
 *
 * @author Ansley Post
 * @author Alan Mislove
 */
import java.io.*;
import java.util.*;

import rice.*;
import rice.pastry.*;

/**
 * This class is an implementation of Storage which is designed to
 * serve as a caching service.  Specifically, this class encapsulates
 * the caching policy logic.  This class stores the cached objects
 * both in-memory for fast access and on-disk for failure recovery.
 * The on-disk storage relies on the <code>PersistentStorage</code>
 * which stores data atomically, guaranteeing uncorrupted data even
 * in the event of a crash.
 */
public class CacheStorage implements Storage {

  /**
   * Builds a CacheStorage object which uses the provided
   * Storage classes to serve as memory and disk storage.
   * If the <code>persistent</code> argument is null, no
   * on-disk storage will be provided.
   *
   * @param memory The Storage to use as memory storage
   * @param persistent The Storage to use as persistent storage
   */
  public CacheStorage(Storage memory, Storage persistent) {
  }

  /**
   * Caches the object under the key <code>id</code>.  If there is already
   * an object under <code>id</code>, that object is replaced. This method
   * guarantees that the persistent copy of the cache will be consistent
   * in the event of a crash.
   *
   * This method also may event other objects in order to store the given
   * object, or may not store the object at all (for instance, if the object
   * is bigger than the cache size).  <code>False</code> is returned if the
   * object is not stored.
   *
   * This method completes by calling recieveResult() of the provided continuation
   * with the success or failure of the operation.
   *
   * @param obj The object to be made persistent.
   * @param id The object's id.
   * @param c The command to run once the operation is complete
   * @return <code>true</code> if the action succeeds, else
   * <code>false</code>.
   */
  public void store(NodeId id, Serializable obj, Continuation c) {
  }

  /**
   * Removes the object from the list of stored objects. If the object was not
   * in the cached list in the first place, nothing happens and <code>false</code>
   * is returned. As with the <code>store</code> method, this method guarantees
   * that the persistent copy of the cache is always consistent, even in the event
   * of a crash, by performing the delete atomically.
   *
   * This method completes by calling recieveResult() of the provided continuation
   * with the success or failure of the operation.
   *
   * @param id The object's persistence id
   * @param c The command to run once the operation is complete
   * @return <code>true</code> if the action succeeds, else
   * <code>false</code>.
   */
  public void unstore(NodeId id, Continuation c) {
  }

  /**
   * Returns whether or not an object is stored in the location <code>id</code>.
   *
   * @param id The id of the object in question.
   * @return Whether or not an object is stored at id.
   */
  public boolean isStored(NodeId id) {
    return false;
  }

  /**
   * Return the object identified by the given id.
   *
   * This method completes by calling recieveResult() of the provided continuation
   * with the result.
   *
   * @param id The id of the object in question.
   * @param c The command to run once the operation is complete
   * @return The object, or <code>null</code> if the pid is invalid.
   */
  public void getObject(NodeId id, Continuation c) {
  }

  /**
    * Return the nodeIds of objects identified by the given range of ids.
   *
   * @param start The staring id of the range.
   * @param end The ending id of the range.
   * @return The nodeIds of objects within the range of given nodeIds.
   */
  public NodeId[] getObject(NodeId start, NodeId end) {
    return new NodeId[0];
  }

  /**
  * Returns the total size of the stored data in bytes.
   *
   * @return The total size, in bytes, of data stored.
   */
  public int getTotalSize() {
    return 0;
  }
}
