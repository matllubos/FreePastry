package rice.post.storage;

import java.io.*;
import java.security.*;
import java.util.*;

import rice.p2p.commonapi.*;
import rice.p2p.past.*;
import rice.p2p.past.gc.*;

/**
 * This class is used internally by the storage package to
 * store data as a content-hash.
 * 
 * @version $Id$
 */
class ContentHashData extends StorageServiceData {

  // serialver, for backwards compatibility
  private static final long serialVersionUID = -8274270442542322772L;
  
  /**
   * Builds a ContentHashData from a byte array and a location
   *
   * @param data The data to store
   * @param credentials Credentials of the data
   */
  public ContentHashData(Id location, byte[] data) {
    super(location, data);
  }

  /**
   * Checks to see if the provided object is equal to this one
   * 
   * @param o The object to check
   * @return Whether or not it's equal
   */
  public boolean equals(Object o) {
    if (! (o instanceof ContentHashData))
      return false;

    return Arrays.equals(data, ((ContentHashData) o).getData());
  }

  /**
   * Returns a string represetation of this object
   *
   * @return A string
   */
  public String toString() {
    return "ContentHashData[" + data.length + "]";
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
