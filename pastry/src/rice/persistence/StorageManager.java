/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/

package rice.persistence;

/*
 * @(#) StorageManager.java
 *
 * @author Ansley Post
 * @author Alan Mislove
 *
 * @version $Id$
 */
import java.io.*;

import rice.*;
import rice.pastry.*;

/**
 * This class provides both persistent and caching services to
 * external applications. Building the StorageManager requires a
 * Storage object, to provide the back-end storage, and a Cache
 * to serve as a cache.  Note that this implementation has seperate
 * areas for the Cache and Storage, but the next version will allow
 * the cache to use the unused storage space.
 */
public class StorageManager implements Cache, Storage {

  // the storage used by this manager
  private Storage storage;

  // the cache used by this manager
  private Cache cache;
  
  /**
   * Builds a StorageManager given a Storage object to provide
   * storage services and a Cache object to provide caching
   * services.  
   *
   * @param storagae The Storage object which will serve as the
   *        persistent storage.
   * @param cache The Cache object which will serve as the cache.
   */
  public StorageManager(Storage storage, Cache cache) {
    this.storage = storage;
    this.cache = cache;
  }

  /**
   * Returns the permantent storage object used by this StorageManager
   *
   * @return The storage of this storage manager
   */
  public Storage getStorage() {
    return storage;
  }

  /**
   * Returns the cache object used by this StorageManager
   *
   * @return The cache of this storage manager
   */
  public Cache getCache() {
    return cache;
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
  public void exists(final Id id, final Continuation c) {
    Continuation inCache = new Continuation() {
      public void receiveResult(Object o) {
        if (o.equals(new Boolean(true))) {
          c.receiveResult(o);
        } else {
          storage.exists(id, c);
        }
      }

      public void receiveException(Exception e) {
        c.receiveException(e);
      }
    };

    cache.exists(id, inCache);
  }

  /**
   * Returns the object identified by the given id.
   *
   * @param id The id of the object in question.
   * @param c The command to run once the operation is complete
   * @return The object, or <code>null</code> if there is no cooresponding
   * object (through receiveResult on c).
   */
  public void getObject(final Id id, final Continuation c) {
    Continuation inCache = new Continuation() {
      public void receiveResult(Object o) {
        if (o != null) {
          c.receiveResult(o);
        } else {
          storage.getObject(id, c);
        }
      }

      public void receiveException(Exception e) {
        c.receiveException(e);
      }
    };

    cache.getObject(id, inCache);    
  }

  /**
   * Return the objects identified by the given range of ids. The array
   * returned contains the Comparable ids of the stored objects. The range is
   * completely inclusive, such that if the range is (A,B), objects with
   * ids of both A and B would be returned.  The resulting array of keys in
   * *NOT* guaranteed to be in any order.
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
  public void scan(final IdRange range, final Continuation c) {
    Continuation scanner = new Continuation() {
      private Comparable[] fromCache;
      
      public void receiveResult(Object o) {
        if (fromCache == null) {
          fromCache = (Comparable[]) o;

          storage.scan(range, this);
        } else {
          Comparable[] fromStorage = (Comparable[]) o;

          Comparable[] result = new Comparable[fromCache.length + fromStorage.length];

          for (int i=0; i<fromCache.length; i++) {
            result[i] = fromCache[i];
          }

          for (int i=fromCache.length; i<result.length; i++) {
            result[i] = fromStorage[i - fromCache.length];
          }

          c.receiveResult(result);
        }
      }

      public void receiveException(Exception e) {
        c.receiveException(e);
      }
    };

    cache.scan(range, scanner);   
  }

  /**
   * Returns the total size of the stored data in bytes.The result
   * is returned via the receiveResult method on the provided
   * Continuation with an Integer representing the size.  This sum is
   * the total of the stored data and the cached data.
   *
   * @param c The command to run once the operation is complete
   * @return The total size, in bytes, of data stored.
   */
  public void getTotalSize(final Continuation c) {
    Continuation getSize = new Continuation() {
      private int cacheSize = -1;

      public void receiveResult(Object o) {
        if (cacheSize == -1) {
          cacheSize = ((Integer) o).intValue();

          storage.getTotalSize(this);
        } else {
          int storageSize = ((Integer) o).intValue();

          c.receiveResult(new Integer(cacheSize + storageSize));
        }
      }

      public void receiveException(Exception e) {
        c.receiveException(e);
      }
    };

    cache.getTotalSize(getSize);    
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
  public void store(Id id, Serializable obj, Continuation c) {
    storage.store(id, obj, c);
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
  public void unstore(Id id, Continuation c) {
    storage.unstore(id, c);
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
  public void cache(Id id, Serializable obj, Continuation c) {
    cache.cache(id, obj, c);
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
  public void uncache(Id id, Continuation c) {
    cache.uncache(id, c);
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
    cache.getMaximumSize(c);
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
    cache.setMaximumSize(size, c);
  }
}
