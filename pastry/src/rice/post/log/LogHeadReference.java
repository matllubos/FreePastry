package rice.post.log;

import java.security.*;

import rice.pastry.*;
import rice.past.*;

/**
* This class serves as a reference to a LogHead
 * stored in the Post system.  This class knows the
 * location in the network of the LogHead object.
 */
public class LogHeadReference {

  /**
  * Constructs a LogHeadReference given a pointer to object
   *
   * @param location The location of the object
   */
  protected LogHeadReference(NodeId location) {
  }

  /**
  * Returns the location of the entry referenced by this object
   *
   * @return The location of the entry
   */
  public NodeId getLocation() {
    return null;
  }

}

