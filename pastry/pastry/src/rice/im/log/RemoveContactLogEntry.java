package rice.im.log;

import rice.post.log.*;
import rice.im.*;


/**
 * Removes a contact in the  LogEntry chain.  Holds the name of the contact removed and a  pointer
 * to the next LogEntry.
 * 
 */


public class RemoveContactLogEntry extends LogEntry {

    Buddy _buddy;


    public RemoveContactLogEntry(Buddy bud) {
	_buddy = bud;

    }


    /**
     * Returns the name of the contact that is removed. 
     */
    public Buddy getBuddy() {
	return _buddy;
    }


}
