package rice.p2p.glacier;

import rice.*;
import rice.p2p.commonapi.*;

/**
 * @(#) VersioningPast.java
 * 
 * This interface is exported by a PAST instance that offers access to
 * specific versions of an object. 
 *
 * @version $Id$
 * @author Andreas Haeberlen
 */

public interface VersioningPast {

  /**
   * Retrieves the object stored in this instance of Past with the
   * given ID and the specified version.  Asynchronously returns 
   * a PastContent object as the result to the provided Continuation, 
   * or a PastException.
   * 
   * @param id the key to be queried
   @ @param version the requested version
   * @param command Command to be performed when the result is received
   */
  public void lookup(Id id, long version, Continuation command);

}

