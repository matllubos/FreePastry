package rice.p2p.saar.blockbased;

import rice.p2p.saar.*;
import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;
import rice.p2p.scribe.messaging.*;
import java.util.*;


public class CoolstreamingReplyUnsubscribeMessage extends SaarDataplaneMessage{

    public boolean permitted = false;

    public CoolstreamingReplyUnsubscribeMessage(NodeHandle source, Topic topic, boolean permitted) {
	super(source, topic);
	this.permitted= permitted;
    }

    public int getSizeInBytes() {
	return 1;
    }

    public String toString() {
	return "CoolstreamingReplyUnsubscribeMessage: " + " source= " + source + " permitted: " + permitted; 
    }
    
}

