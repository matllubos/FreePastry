package rice.post.storage;

import java.io.*;
import java.security.*;
import java.util.*;

import rice.p2p.commonapi.*;
import rice.p2p.past.*;
import rice.p2p.past.gc.*;

/**
 * This class is used internally by the storage package to
 * store data using a secure scheme.
 *
 * @version $Id$
 */
class SecureData extends StorageServiceData {

  /**
   * Builds a SecureData from a byte array and the credentials of data
   *
   * @param data The data to store
   * @param credentials Credentials of the data
   */
  public SecureData(Id location, byte[] data) {
    super(location, data);
  }

  public boolean equals(Object o) {
    if (! (o instanceof SecureData))
      return false;

    return Arrays.equals(data, ((SecureData) o).getData());
  }

  public String toString() {
    return "SecureData[" + data.length + "]";
  }

  /**
   * Checks if a insert operation should be allowed.  Invoked when a
   * Past node receives an insert request and it is a replica root for
   * the id; invoked on the object to be inserted.  This method
   * determines the effect of an insert operation on an object that
   * already exists: it computes the new value of the stored object,
   * as a function of the new and the existing object.
   *
   * @param id the key identifying the object
   * @param newObj the new object to be stored
   * @param existingObj the existing object stored on this node (null if no object associated with id is stored on this node)
   * @return null, if the operation is not allowed; else, the new
   * object to be stored on the local node.
   */
  public PastContent checkInsert(rice.p2p.commonapi.Id id, PastContent existingContent) throws PastException {
    if (existingContent == null) {
      return this;
    } else {
      return existingContent;
    }
  }

  /**
   * States if this content object is mutable. Mutable objects are not subject to dynamic caching in Past.
   *
   * @return true if this object is mutable, else false
   */
  public boolean isMutable() {
    return false;
  }
}
