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
 * @(#) LRUCache.java
 *
 * @author Ansley Post
 * @author Alan Mislove
 *
 * @version $Id$
 */
import java.io.*;
import java.util.*;

import rice.*;
import rice.p2p.commonapi.*;

/**
 * This class is an encapsulation of a least-recently-used (LRU)
 * cache.  It uses the provided storage service in order to
 * store the cached data.  If the Storage provides non-corruption
 * services, these services will also be provided by this cache.
 */
public class LRUCache implements Cache {

  // the maximum size of the cache
  private int maximumSize;

  // the back-end storage used by this cache
  private Storage storage;

  // the list of keys, in MRU -> LRU order
  private LinkedList order;

  /**
   * Builds a LRU cache given a storage object to store the cached
   * data in and a maximum cache size.
   *
   * @param storage The storage service to use as a back-end storage
   * @param maximumSize The maximum size, in bytes, of storage to use
   */
  public LRUCache(Storage storage, int maximumSize) {
    this.storage = storage;
    this.maximumSize = maximumSize;

    this.order = new LinkedList();
  }
  
  /**
   * Caches an object in this Cache. This method is non-blocking.
   * If the object has already been stored at the location id, this
   * method has the effect of calling <code>uncachr(id)</code> followed
   * by <code>cache(id, obj)</code>. This method finishes by calling
   * receiveResult() on the provided continuation with whether or not
   * the object was cached.  Note that the object may not actually be
   * stored (if it is bigger than the entire cache size).
   *
   * @param id The object's id.
   * @param obj The object to cache.
   * @param c The command to run once the operation is complete
   * @return <code>True</code> if the cache actaully stores the object, else
   * <code>False</code> (through receiveResult on c).
   */
  public synchronized void cache(final Id id, final Serializable obj, final Continuation c) {
    final int size = getSize(obj);

    if (size > maximumSize) {
      c.receiveResult(new Boolean(false));
      return;
    }

    
    //System.out.println("\nCaching object of size " + size + " with ID " + id);
    
    final Continuation store = new Continuation() {
      public void receiveResult(Object o) {
        order.addFirst(id);
        storage.store(id, obj, c);
      }

      public void receiveException(Exception e) {
        c.receiveException(e);
      }
    };

    Continuation resize = new Continuation() {
      public void receiveResult(Object o) {
        int totalSize = ((Integer) o).intValue();

        if (maximumSize - size < totalSize) {
          resize(maximumSize - size, store);
        } else {
          store.receiveResult(new Boolean(true));
        }
      }

      public void receiveException(Exception e) {
        c.receiveException(e);
      }
    };

    storage.getTotalSize(resize);
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
  public synchronized void uncache(Id id, Continuation c) {
    order.remove(id);
    storage.unstore(id, c);
  }

  /**
   * Returns whether or not an object is cached in the location <code>id</code>.
   * The result is returned via the receiveResult method on the provided
   * Continuation with an Boolean represnting the result.
   *
   * @param c The command to run once the operation is complete
   * @param id The id of the object in question.
   * @return Whether or not an object is present at id.
   */
  public void exists(Id id, Continuation c) {
    c.receiveResult(new Boolean(order.contains(id)));
  }

  /**
   * Returns the object identified by the given id.
   *
   * @param id The id of the object in question.
   * @param c The command to run once the operation is complete
   * @return The object, or <code>null</code> if there is no cooresponding
   * object (through receiveResult on c).
   */
  public synchronized void getObject(Id id, Continuation c) {
    if (! order.contains(id)) {
      c.receiveResult(null);
      return;
    }

    order.remove(id);
    order.addFirst(id);

    storage.getObject(id, c);
  }

  /**
   * Return the objects identified by the given range of ids. The IdSet 
   * returned contains the Ids of the stored objects. The range is
   * partially inclusive, the lower range is inclusive, and the upper
   * exclusive.
   *
   *
   * When the operation is complete, the receiveResult() method is called
   * on the provided continuation with a Comparable[] result containing the
   * resulting IDs.
   *
   * @param start The staring id of the range. (inclusive)
   * @param end The ending id of the range. (exclusive) 
   * @param c The command to run once the operation is complete
   * @return The idset containg the keys 
   */
   public synchronized void scan(IdRange range, Continuation c) {
     storage.scan(range, c);
   }

  /**
   * Return the objects identified by the given range of ids. The IdSet 
   * returned contains the Ids of the stored objects. The range is
   * partially inclusive, the lower range is inclusive, and the upper
   * exclusive.
   *
   *
   * NOTE: This method blocks so if the behavior of this method changes and
   * uses the disk, this method may be deprecated.
   *
   * @param range The range to query  
   * @return The idset containg the keys 
   */
   public synchronized IdSet scan(IdRange range){
     return(storage.scan(range));
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
    c.receiveResult(new Integer(maximumSize));
  }

  /**
   * Returns the total size of the stored data in bytes. The result
   * is returned via the receiveResult method on the provided
   * Continuation with an Integer representing the size.
   *
   * @param c The command to run once the operation is complete
   * @return The total size, in bytes, of data stored.
   */
  public void getTotalSize(Continuation c) {
    storage.getTotalSize(c);
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
  public void setMaximumSize(final int size, final Continuation c) {
    Continuation local = new Continuation() {
      public void receiveResult(Object o) {
        maximumSize = size;

        c.receiveResult(new Boolean(true));
      }

      public void receiveException(Exception e) {
        c.receiveException(e);
      }
    };

    if (size < maximumSize) {
      resize(size, local);
    } else {
      local.receiveResult(new Boolean(true));
    }
  }

  /**
   * Internal method which removes objects from the cache until the cache
   * is smaller than the specified size
   *
   * @param size The maximum number of bytes to make the cache
   * @param c The command to run once the operation is complete
   */
  private void resize(final int size, final Continuation c) {
    
    final Continuation remove = new Continuation() {
      private boolean waitingForSize = true;
      
      public void receiveResult(Object o) {
        if (waitingForSize) {
          waitingForSize = false;
          
          if (((Integer) o).intValue() > size) {
            Comparable thisID = (Comparable) order.getLast();

            //System.out.println("Evicting object with ID " + thisID);

            uncache((Id) thisID, this);
          } else {
            c.receiveResult(new Boolean(true));
          }
        } else {
          waitingForSize = true;
          storage.getTotalSize(this);
        }
      }

      public void receiveException(Exception e) {
        c.receiveException(e);
      }
    };

    storage.getTotalSize(remove);
  }

  /**
   * Returns the size of the given object, in bytes.
   *
   * @param obj The object to determine the size of
   * @return The size, in bytes
   */
  private int getSize(Object obj) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);

      oos.writeObject(obj);
      oos.flush();

      return baos.toByteArray().length;
    } catch (IOException e) {
      throw new RuntimeException("Object " + obj + " was not serialized correctly!");
    }
  }
}
