package rice.email.log;

import rice.post.log.*;
import rice.email.*;

/**
 * Stores an email in the LogEntry chain.  Holds the email and a pointer
 * to the next LogEntry.
 */
public class InsertFolderLogEntry extends LogEntry {
  String _name;
    
  /**
   * Constructor for InsrtFolderLogEntry. 
   *
   * @param name the name of the Folder created
   */
  public InsertFolderLogEntry(String name) {
    _name = name;
  }
  
  /**
   * Returns the name of the Folder which this log entry references
   *
   * @return The folder name inserted
   */
  public String getName() {
    return _name;
  }
}
