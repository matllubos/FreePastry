package rice.persistence;
/*
 * @(#) StorageManager.java
 *
 * @author Ansley Post
 * @author Alan Mislove
 */
import java.io.*;

import rice.*;
import rice.pastry.*;

/**
 * This class provides both persistent and caching services to
 * external applications. 
 */
public class StorageManager {

  /**
   * Builds a StorageManager given a Storage object to provide
   * caching services and another Storage object to provide
   * persistent storage services.
   *
   * @param cache The Storage object which will serve as the cache
   * @param persist The Storage object which will servce as the
   *        persistent storage.
   */
  public StorageManager(Storage cache, Storage persist) {
  }

  /**
   * Makes the object persistent to disk and stored permanantly.
   * If the object is already persistent, this method will
   * simply update the object's serialized image. 
   *
   * This is implemented atomically so that this may succeed
   * and store the new object, or fail and leave the previous
   * object intact.
   *
   * This method completes by calling recieveResult() of the provided continuation
   * with the succcess or failure.
   *
   * @param obj The object to be made persistent.
   * @param id The object's id.
   * @param c The command to run once the operation is complete
   * @return <code>true</code> if the action succeeds, else
   * <code>false</code>.
   */
  public void persist(NodeId id, Serializable obj, Continuation c) {
  }

  /**
   * Request to remove the object from the list of persistend objects.
   * Delete the serialized image of the object from stable storage. If
   * necessary. If the object was not in the cached list in the first place,
   * nothing happens and <code>false</code> is returned.
   *
   * This method also guarantees that the data on disk will remain consistent,
   * even after a crash by performing the delete atomically.
   *
   * This method completes by calling recieveResult() of the provided continuation
   * with the succcess or failure.
   *
   * @param id The object's persistence id
   * @param c The command to run once the operation is complete
   * @return <code>true</code> if the action succeeds, else
   * <code>false</code>.
   */
  public void unpersist(NodeId id, Continuation c) {
  }

  /**
   * Caches the object under the key <code>id</code>.  If there is already
   * an object under <code>id</code>, that object is replaced. This method
   * guarantees that the persistent copy of the cache will be consistent
   * in the event of a crash.
   *
   * This method also may event other objects in order to store the given
   * object, or may not store the object at all (for instance, if the object
                                                 * is bigger than the cache size).
   *
   * This method completes by calling recieveResult() of the provided continuation
   * with the succcess or failure.
   *   
   * @param obj The object to be made persistent.
   * @param id The object's id.
   * @param c The command to run once the operation is complete
   * @return <code>true</code> if the action succeeds, else
   * <code>false</code>.
   */
  public void cache(NodeId id, Serializable obj, Continuation c) {
  }

  /**
   * Removes the object from the list of stored objects. If the object was not
   * in the cached list in the first place, nothing happens and <code>false</code>
   * is returned. As with the <code>store</code> method, this method guarantees
   * that the persistent copy of the cache is always consistent, even in the event
   * of a crash, by performing the delete atomically.
   *
   * This method completes by calling recieveResult() of the provided continuation
   * with the succcess or failure.
   *
   * @param id The object's persistence id
   * @param c The command to run once the operation is complete
   * @return <code>true</code> if the action succeeds, else
   * <code>false</code>.
   */
  public void uncache(NodeId id, Continuation c) {
  }

  /**
   * Returns whether or not the given object is cached.
   *
   * @param id The id of the object in question.
   * @return Whether or not an object is cached under the location id.
   */
  public boolean isCached(NodeId id) {
    return false;
  }

  /**
   * Returns whether or not the given object is stored in persistent
   * storag.
   *
   * @param id The id of the object in question.
   * @return Whether or not an object is stored in the location id.
   */
  public boolean isPersisted(NodeId id) {
    return false;
  }

  /**
   * Returns the object identified by the given id.
   *
   * This method completes by calling recieveResult() of the provided continuation
   * with the result object.
   *
   * @param id The id of the object in question.
   * @param c The command to run once the operation is complete
   * @return The object, or <code>null</code> if there is no cooresponding
   * object.
   */
  public void getObject(NodeId id, Continuation c) {
  }

  /**
   * Return the objects identified by the given range of ids. The array
   * returned contains the NodeIds of all of the stored object within the
   * given range.
   *
   * @param start The staring id of the range.
   * @param end The ending id of the range.
   * @return The nodeIds (keys) within the range.
   */
  public NodeId[] getObject(NodeId start, NodeId end) {
    return new NodeId[0];
  }
}
