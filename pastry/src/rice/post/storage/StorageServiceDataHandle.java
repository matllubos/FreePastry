package rice.post.storage;

import java.io.*;
import java.security.*;
import java.util.*;

import rice.p2p.commonapi.*;
import rice.p2p.past.*;
import rice.p2p.past.gc.*;

/**
 * This class is the class which serves a reference to objects stored in past.
 * It is currently not used.
 *
 * @version $Id$
 */
class StorageServiceDataHandle implements GCPastContentHandle {

  // the location where the data is stored
  protected Id id;

  // the handle where the data is locationed
  protected NodeHandle handle;
  
  // the time at which the handle was created
  protected long timestamp;
  
  // the version number of this data handle
  protected long version;
  
  // the time at which this handle expires
  protected long expiration;

  /**
   * Contstructor
   *
   * @param id The id
   * @param handle The handle where the data is
   * @param timestamp The time at which the handle was created
   * @param version The version number of the object
   * @param expiration The expiration time of the object
   */
  public StorageServiceDataHandle(NodeHandle handle, Id id, long version, long expiration) {
    this.id = id;
    this.handle = handle;
    this.timestamp = System.currentTimeMillis();
    this.version = version;
    this.expiration = expiration;
  }
  
  /**
   * get the id of the PastContent object associated with this handle
   * @return the id
   */
  public Id getId() {
    return id;
  }

  /**
   * get the NodeHandle of the Past node on which the object associated with this handle is stored
   * @return the handle
   */
  public NodeHandle getNodeHandle() {
    return handle;
  }

  /**
   * Returns the timestamp of this handle
   *
   * @return The timestamp for thsi handle
   */
  public long getTimestamp() {
    return timestamp;
  }
  
  /**
   * Returns the version number associated with this PastContentHandle - 
   * version numbers are designed to be monotonically increasing numbers which
   * signify different versions of the same object.
   *
   * @return The version number of this object
   */
  public long getVersion() {
    return version;
  }
  
  /**
    * Returns the current expiration time of this object.
   *
   * @return The current expiration time of this object
   */
  public long getExpiration() {
    return expiration;
  }

}

