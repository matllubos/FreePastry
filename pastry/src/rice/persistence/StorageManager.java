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
 * external applications. Building the StorageManager requires a
 * Storage object, to provide the back-end storage, and a Cache
 * to serve as a cache.  Note that this implementation allows
 * the cache to use the unused storage space of cached objects.
 */
public class StorageManager implements Cache, Storage{

  /**
   * Builds a StorageManager given a Storage object to provide
   * storage services and a Cache object to provide caching
   * services.  
   *
   * @param storagae The Storage object which will serve as the
   *        persistent storage.
   * @param cache The Cache object which will serve as the cache.
   * @param size The maximum size, in bytes, of data to store.
   */
  public StorageManager(Storage storage, Cache cache, int size) {
  }

  /**
   * Returns whether or not an object is present in the location <code>id</code>.
   * The result is returned via the receiveResult method on the provided
   * Continuation with an Boolean represnting the result.
   *
   * @param c The command to run once the operation is complete
   * @param id The id of the object in question.
   * @return Whether or not an object is present at id.
   */
  public void exists(Comparable id, Continuation c) {
  }

  /**
   * Returns the object identified by the given id.
   *
   * @param id The id of the object in question.
   * @param c The command to run once the operation is complete
   * @return The object, or <code>null</code> if there is no cooresponding
   * object (through receiveResult on c).
   */
  public void getObject(Comparable id, Continuation c) {
  }

  /**
   * Return the objects identified by the given range of ids. The array
   * returned contains the Comparable ids of the stored objects. The range is
   * completely inclusive, such that if the range is (A,B), objects with
   * ids of both A and B would be returned.
   *
   * Note that the two Comparable objects should be of the same class
   * (otherwise no range can be created).
   *
   * When the operation is complete, the receiveResult() method is called
   * on the provided continuation with a Comparable[] result containing the
   * resulting IDs.
   *
   * @param start The staring id of the range.
   * @param end The ending id of the range.
   * @param c The command to run once the operation is complete
   * @return The objects
   */
  public void scan(Comparable start, Comparable end, Continuation c) {
  }

  /**
   * Returns the total size of the stored data in bytes.The result
   * is returned via the receiveResult method on the provided
   * Continuation with an Integer representing the size.
   *
   * @param c The command to run once the operation is complete
   * @return The total size, in bytes, of data stored.
   */
  public void getTotalSize(Continuation c) {
  }

  /**
   * Stores an object in this storage. This method is non-blocking.
   * If the object has already been stored at the location id, this
   * method has the effect of calling <code>unstore(id)</code> followed
   * by <code>store(id, obj)</code>. This method finishes by calling
   * receiveResult() on the provided continuation with the success
   * or failure of the store.
   *
   * @param id The object's id.
   * @param obj The object to store.
   * @param c The command to run once the operation is complete
   * @return <code>True</code> if the action succeeds, else
   * <code>False</code> (through receiveResult on c).
   */
  public void store(Comparable id, Serializable obj, Continuation c) {
  }

  /**
   * Removes the object from the list of stored objects. This method is
   * non-blocking. If the object was not in the stored list in the first place,
   * nothing happens and <code>False</code> is returned.
   *
   * @param pid The object's persistence id
   * @param c The command to run once the operation is complete
   * @return <code>true</code> if the action succeeds, else
   * <code>false</code>  (through receiveResult on c).
   */
  public void unstore(Comparable id, Continuation c) {
  }
  
  /**
   * Caches an object in this storage. This method is non-blocking.
   * If the object has already been stored at the location id, this
   * method has the effect of calling <code>uncachr(id)</code> followed
   * by <code>cache(id, obj)</code>. This method finishes by calling
   * receiveResult() on the provided continuation with whether or not
   * the object was cached.  Note that the object may not actually be
   * cached due to the cache replacement policy.
   *
   * @param id The object's id.
   * @param obj The object to cache.
   * @param c The command to run once the operation is complete
   * @return <code>True</code> if the cache actaully stores the object, else
   * <code>False</code> (through receiveResult on c).
   */
  public void cache(Comparable id, Serializable obj, Continuation c) {
  }

  /**
   * Removes the object from the list of cached objects. This method is
   * non-blocking. If the object was not in the cached list in the first place,
   * nothing happens and <code>False</code> is returned.
   *
   * @param pid The object's id
   * @param c The command to run once the operation is complete
   * @return <code>True</code> if the action succeeds, else
   * <code>False</code>  (through receiveResult on c).
   */
  public void uncache(Comparable id, Continuation c) {
  }

  /**
   * Returns the maximum size of the cache, in bytes. The result
   * is returned via the receiveResult method on the provided
   * Continuation with an Integer representing the size.
   *
   * @param c The command to run once the operation is complete
   * @return The maximum size, in bytes, of the cache.
   */
  public void getMaximumSize(Continuation c) {
  }

  /**
   * Sets the maximum size of the cache, in bytes. Setting this
   * value to a smaller value than the current value may result in
   * object being evicted from the cache.
   *
   * @param size The new maximum size, in bytes, of the cache.
   * @param c The command to run once the operation is complete
   * @return The success or failure of the setSize operation
   * (through receiveResult on c).
   */
  public void setMaximumSize(int size, Continuation c) {
  }
}
