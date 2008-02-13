package rice.p2p.saar.blockbased;

import rice.p2p.saar.*;
import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;
import rice.p2p.scribe.messaging.*;
import java.util.*;


// This is a request to unsubscribe and can be denied and is different from the graceful depart Unsubscribe message which is unconditional. 
public class CoolstreamingRequestUnsubscribeMessage extends SaarDataplaneMessage{


    public CoolstreamingRequestUnsubscribeMessage(NodeHandle source, Topic topic) {
	super(source, topic);
    }

    public int getSizeInBytes() {
	return 1;
    }

    public String toString() {
	return "CoolstreamingRequestUnsubscribeMessage: " + " source= " + source; 
    }
    
}
