
package rice.persistence;
/*
 * @(#) Cache.java
 *
 * @author Ansley Post
 * @author Alan Mislove
 * @version $Id$
 */
import java.io.*;

import rice.*;
import rice.p2p.commonapi.*;

/**
 * This interface is the abstraction of something which provides a
 * caching service.  Implementations should take in parameters specific
 * to the cache algorithm.  Two implementations are provided,
 * the LRUCache and GDSCache. This interface extends the Catalog
 * interface, as the cache provides a Catalog service.
 *
 * @version $Id$
 */
public interface Cache extends Catalog {
  
  /**
   * Caches an object in this storage. This method is non-blocking.
   * If the object has already been stored at the location id, this
   * method has the effect of calling <code>uncachr(id)</code> followed
   * by <code>cache(id, obj)</code>. This method finishes by calling
   * receiveResult() on the provided continuation with whether or not
   * the object was cached.  Note that the object may not actually be
   * cached due to the cache replacement policy.
   *
   * Returns <code>True</code> if the cache actaully stores the object, else
   * <code>False</code> (through receiveResult on c).
   *
   * @param id The object's id.
   * @param metadata The object's metdatadata
   * @param obj The object to cache.
   * @param c The command to run once the operation is complete
   */
  public void cache(Id id, Serializable metadata, Serializable obj, Continuation c);

  /**
   * Removes the object from the list of cached objects. This method is
   * non-blocking. If the object was not in the cached list in the first place,
   * nothing happens and <code>False</code> is returned.
   *
   * Returns <code>True</code> if the action succeeds, else
   * <code>False</code>  (through receiveResult on c).
   *
   * @param pid The object's id
   * @param c The command to run once the operation is complete
   */
  public void uncache(Id id, Continuation c);

  /**
   * Returns the maximum size of the cache, in bytes. The result
   * is returned via the receiveResult method on the provided
   * Continuation with an Integer representing the size.
   *
   * @param c The command to run once the operation is complete
   */
  public long getMaximumSize();

  /**
   * Sets the maximum size of the cache, in bytes. Setting this
   * value to a smaller value than the current value may result in
   * object being evicted from the cache.
   *
   * Returns the success or failure of the setSize operation
   * (through receiveResult on c).
   *
   * @param size The new maximum size, in bytes, of the cache.
   * @param c The command to run once the operation is complete
   */
  public void setMaximumSize(int size, Continuation c);  
}
