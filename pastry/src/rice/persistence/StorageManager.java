
package rice.persistence;

import java.io.*;
import java.util.Iterator;

import rice.*;
import rice.Continuation.*;
import rice.p2p.commonapi.*;

/*
 * @(#) StorageManager.java
 *
 * This interface represents a "smart" storage manger, which represents a storage
 * attached to a cache.  Objects inserted and retrieved from the storage are auto-
 * matically cached, to speed up future accesses.
 *
 * @author Ansley Post
 * @author Alan Mislove
 *
 * @version $Id$
 */
public interface StorageManager extends Cache, Storage {

  /**
   * Returns the permantent storage object used by this StorageManager
   *
   * @return The storage of this storage manager
   */
  public Storage getStorage();

  /**
   * Returns the cache object used by this StorageManager
   *
   * @return The cache of this storage manager
   */
  public Cache getCache();

}
