package rice.post.email.log;

import rice.post.log.*;
import rice.post.email.*;

/**
 * Creates a folder in the user's log.
 */
public class CreateFolderLogEntry extends LogEntry {

  /**
   * Constructor for CreateFolderLogEntry.  Creates a folder
   * with the specified name and parent.  If the parent is null, this
   * folder is created at the top level.
   *
   * @param name The name of the folder
   * @param parent The parent folder (null if a top-level folder)
   * @param prev The reference the to previous log entry in the chain
   */
  public CreateFolderLogEntry(String name, Folder parent, LogEntryReference prev) {
    super(prev);
  }
  
  /**
   * Returns the name of this folder
   *
   * @return The folder name
   */
  public String getName() {
    return null;
  }
  
  /**
   * Returns the parent folder
   *
   * @return The folder in which this folder resides (null if a top-level folder)
   */
  public Folder getParent() {
    return null;
  }
}
