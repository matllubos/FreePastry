package rice.post.storage;

import java.io.*;
import java.security.*;
import java.util.*;

import rice.p2p.commonapi.*;
import rice.p2p.past.*;

/**
 * This class is the class which serves a reference to objects stored in past.
 * It is currently not used.
 *
 * @version $Id$
 */
class StorageServiceDataHandle implements PastContentHandle {

  // the location where the data is stored
  protected Id id;

  // the handle where the data is locationed
  protected NodeHandle handle;

  /**
   * Contstructor
   *
   * @param id The id
   * @param handle The handle where the data is
   */
  public StorageServiceDataHandle(NodeHandle handle, Id id) {
    this.id = id;
    this.handle = handle;
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

}

