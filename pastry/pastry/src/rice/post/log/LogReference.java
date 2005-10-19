package rice.post.log;

import java.security.*;

import rice.p2p.commonapi.*;
import rice.past.*;
import rice.post.storage.*;

/**
 * This class serves as a reference to a Log
 * stored in the Post system.  This class knows the
 * location in the network of the Log object.
 * 
 * @version $Id$
 */
public class LogReference extends SignedReference {

  /**
   * Creates a new reference to a particular Log object.
   */
  public LogReference(Id location) {
    super(location);
  }
  
}

