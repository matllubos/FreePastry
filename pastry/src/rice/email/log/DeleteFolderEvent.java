package rice.email.log;

import rice.email.*;
/**
 * A Folder Deletion Event.  Signifies that the referenced Folder was
 * deleted. 
 */
public class DeleteFolderEvent implements Event {
  String _name;
    
  /**
   * Constructor for the Event.  
   *
   * @param name the name of the Folder deleted
   */
  public DeleteFolderEvent(String name) {
    _name = name;
  }

  /**
   * Returns the name of the referenced Folder.
   *
   * @return The Folder referenced
   */
  public String getName() {
    return _name;
  }

  /**
   * Returns a String representation of this Event.
   * @return the String for this Event
   */
  public String toString() {
    return "Delete Folder Event for: " + _name;
  }
}
