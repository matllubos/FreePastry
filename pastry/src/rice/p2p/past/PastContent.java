
package rice.p2p.past;

import java.io.Serializable;

import rice.*;
import rice.p2p.commonapi.*;


/**
 * @(#) PastContent.java
 *
 * This interface must be implemented by all content objects stored in
 * Past.
 *
 * The interface allows applications to control the semantics of an
 * instance of Past. For instance, it allows applications to control
 * which objects can be inserted (e.g., content-hash objects only),
 * what happens when an object is inserted that already exists in
 * Past, etc.
 *
 * @version $Id$
 * @author Alan Mislove
 * @author Ansley Post
 * @author Peter Druschel
 */
public interface PastContent extends Serializable {

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
  public PastContent checkInsert(Id id, PastContent existingContent) throws PastException;

  /**
   * Produces a handle for this content object. The handle is retrieved and returned to the
   * client as a result of the Past.lookupHandles() method.
   *
   * @param The local Past service which the content is on.
   * @return the handle
   */
  public PastContentHandle getHandle(Past local);

  /**
   * Returns the Id under which this object is stored in Past.
   *
   * @return the id
   */
  public Id getId();

  /**
   * States if this content object is mutable. Mutable objects are not subject to dynamic caching in Past.
   *
   * @return true if this object is mutable, else false
   */
  public boolean isMutable();

}





