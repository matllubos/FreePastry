
package rice.p2p.past.gc;

import java.io.Serializable;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.past.*;

/**
 * @(#) GCPastContent.java
 *
 * This interface represents an object which is storable in the GCPast
 * system.  This interface adds on methods which return the object's 
 * version number, which to used to disambiguate multiple versions of
 * the same object.
 *
 * @version $Id$
 * @author Alan Mislove
 * @author Andreas Haeberlen
 */
public interface GCPastContent extends PastContent {

  /**
   * Returns the version number associated with this PastContent object - 
   * version numbers are designed to be monotonically increasing numbers which
   * signify different versions of the same object.
   *
   * @return The version number of this object
   */
  public long getVersion();

  /**
   * Produces a handle for this content object. The handle is retrieved and returned to the
   * client as a result of the Past.lookupHandles() method.
   *
   * @param The local GCPast service which the content is on.
   * @return the handle
   */
  public GCPastContentHandle getHandle(GCPast local, long expiration);
  
  /**
   * Returns the metadata which should be stored with this object.  Allows applications
   * to add arbitrary items into the object's metadata.
   *
   * @param The local GCPast service which the content is on.
   * @return the handle
   */
  public GCPastMetadata getMetadata(long expiration);

}





