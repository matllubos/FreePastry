
package rice.p2p.past.gc;

import java.io.Serializable;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.past.*;

/**
 * @(#) PastContentHandle.java
 * 
 * This interface represents a content handle which is used in the versioned and
 * garbage-collected version of Past.  It adds support for a version number as 
 * well as the current expiration time.
 *
 * @version $Id$
 * @author Alan Mislove
 * @author Andreas Haeberlen
 */
public interface GCPastContentHandle extends PastContentHandle {
  
  /**
   * Returns the version number associated with this PastContentHandle - 
   * version numbers are designed to be monotonically increasing numbers which
   * signify different versions of the same object.
   *
   * @return The version number of this object
   */
  public long getVersion();
  
  /**
   * Returns the current expiration time of this object.
   *
   * @return The current expiration time of this object
   */
  public long getExpiration();

}





