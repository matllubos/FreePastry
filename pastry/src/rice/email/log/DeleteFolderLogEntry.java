package rice.email.log;

import rice.post.log.*;
import rice.email.*;

/**
 * Node which marks a Folder deletion.
 * @author Joe Montgomery
 */
public class DeleteFolderLogEntry extends LogEntry {
  String _name;
    
  /**
   * Constructor for DeleteFolderLogEntry.  Marks that the child Folder of
   * the given name was deleted.   
   *
   * @param name the name of the deleted Folder
   */
  public DeleteFolderLogEntry(String name) {
    _name = name;
  }
  
  /**
   * Returns the name of the Folder that was deleted.
   *
   * @return the name of the deleted Folder
   */
  public String getName() {
    return _name;
  }
}
