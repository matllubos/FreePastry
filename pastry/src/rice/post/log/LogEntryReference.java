package rice.post.log;

import java.security.*;

import rice.pastry.*;
import rice.past.*;

/**
 * This class serves as a reference to a LogEntry
 * stored in the Post system.  This class knows the
 * location in the network of the LogEntry object.
 */
public class LogEntryReference {

  /**
   * Constructs a LogEntryReference given a pointer to object
   *
   * @param location The location of the object
   */
  protected LogEntryReference(NodeId location) {
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

