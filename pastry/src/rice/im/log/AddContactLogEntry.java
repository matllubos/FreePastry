package rice.im.log;

import rice.post.log.*;
import rice.im.*;


/**
 * Stores a contact in the  LogEntry chain.  Holds the name of the contact and a  pointer
 * to the next LogEntry.
 * 
 */


public class AddContactLogEntry extends LogEntry {

    Buddy _buddy;


    public AddContactLogEntry(Buddy buddy) {
	_buddy = buddy;

    }


    /**
     * Returns the name of the contact that is added. 
     */
    public Buddy getBuddy() {
	return _buddy;
    }


}
