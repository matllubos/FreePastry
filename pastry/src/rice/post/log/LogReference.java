package rice.post.log;

import java.security.*;

import rice.pastry.*;
import rice.past.*;
import rice.post.storage.*;

/**
 * This class serves as a reference to a Log
 * stored in the Post system.  This class knows the
 * location in the network of the Log object.
 */
public class LogReference extends SignedReference {

  public LogReference(NodeId location) {
    super(location);
  }
  
}

