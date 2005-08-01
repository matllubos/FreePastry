
package rice.p2p.past;

import java.io.Serializable;

import rice.*;
import rice.p2p.commonapi.*;

/**
 * @(#) ContentHashPastContent.java
 *
 * An abstract class for content-hash objects stored in Past.
 *
 * Provided as a convenience.
 *
 * @version $Id$
 * @author Peter Druschel
 * @author Alan Mislove
 */
public abstract class ContentHashPastContent implements PastContent {
  
  protected Id myId;

  // ----- PastCONTENT METHODS -----

  public ContentHashPastContent(Id myId) {
    this.myId = myId;
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
   * @param existingObj the existing object stored on this node (null
   *        if no object associated with id is stored on this node)
   * @return null, if the operation is not allowed; else, the new
   *         object to be stored on the local node.
   */
  public PastContent checkInsert(Id id, PastContent existingContent) throws PastException {
    // can't overwrite content hash objects
    if (existingContent != null) {
      throw new PastException("ContentHashPastContent: can't insert, object already exists");
    }
    
    // only allow correct content hash key
    if (!id.equals(getId())) {
      throw new PastException("ContentHashPastContent: can't insert, content hash incorrect");
    }
    return this;
  }

  /**
   * Produces a handle for this content object. The handle is retrieved and returned to the
   * client as a result of the Past.lookupHandles() method.
   *
   * @param local The local past service
   * @return the handle
   */
  public PastContentHandle getHandle(Past local) {
    return new ContentHashPastContentHandle(local.getLocalNodeHandle(), getId());
  }

  /**
   * Returns the Id under which this object is stored in Past.
   *
   * @return the id
   */
  public Id getId() {
    return myId;
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










