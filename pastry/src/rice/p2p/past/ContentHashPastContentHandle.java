
package rice.p2p.past;

import java.io.Serializable;

import rice.*;
import rice.p2p.commonapi.*;

/**
 * @(#) ContentHashPastContentHandle.java
 *
 * A handle class for content-hash objects stored in Past.
 *
 * @version $Id$
 * @author Peter Druschel
 */
public class ContentHashPastContentHandle implements PastContentHandle {

  // the node on which the content object resides
  private NodeHandle storageNode;

  // the object's id
  private Id myId;

  /**
   * Constructor
   *
   * @param nh The handle of the node which holds the object
   * @param id key identifying the object to be inserted
   */
  public ContentHashPastContentHandle(NodeHandle nh, Id id) {
    storageNode = nh;
    myId = id;
  }

  
  // ----- PastCONTENTHANDLE METHODS -----

  /**
   * Returns the id of the PastContent object associated with this handle
   *
   * @return the id
   */
  public Id getId() {
    return myId;
  }

  /**
   * Returns the NodeHandle of the Past node on which the object associated
   * with this handle is stored
   *
   * @return the id
   */
  public NodeHandle getNodeHandle() {
    return storageNode;
  }
}










