package rice.post.log;

import java.security.*;

import rice.pastry.*;
import rice.past.*;
import rice.post.storage.*;

/**
 * This class serves as a reference to a LogEntry
 * stored in the Post system.  This class knows the
 * location in the network of the LogEntry object and
 * the encryption key.
 */
public class LogEntryReference extends ContentHashReference {

  public LogEntryReference(NodeId location, Key key) {
    super(location, key);
  }

}

