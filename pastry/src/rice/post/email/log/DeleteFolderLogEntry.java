package rice.post.email.log;

import rice.post.email.*;
import rice.post.log.*;

/**
 * Creates a folder in the user's log.
 */
public class DeleteFolderLogEntry extends LogEntry {

  /**
   * Constructor for DeleteFolderLogEntry.  Deletes the specified
   * folder.
   *
   * @param folder The folder to delete
   * @param prev The reference to the previous log entry in the chain
   */
  public DeleteFolderLogEntry(Folder folder, LogEntryReference prev) {
    super(prev);
  }
  
  /**
   * Returns the deleted folder
   *
   * @return The folder to be deleted
   */
  public Folder getFolder() {
    return null;
  }
}
