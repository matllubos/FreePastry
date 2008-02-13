package rice.p2p.saar.singletree;

import rice.p2p.saar.*;
//import rice.replay.*;
import rice.p2p.scribe.messaging.ScribeMessage;
import rice.pastry.PastryNode;
import rice.p2p.commonapi.Id;
import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;
import rice.p2p.scribe.messaging.*;

/**
 */
public class AcceptPremptedChildMsg extends SaarDataplaneMessage {

    public static byte FALSE = 0;
    public static byte TRUE = 1;

    public static byte NULL = 2;
    public static byte NONNULL = 3;

    public int premptedChildDegree;



//    public void dump(rice.replay.ReplayBuffer buffer, PastryNode pn) {
//
//    }
//    
//    public AcceptPremptedChildMsg(ReplayBuffer buffer, PastryNode pn) {
//	super(buffer,pn);
//    }


    public AcceptPremptedChildMsg(NodeHandle source, Topic topic, int premptedChildDegree) {
	super(source, topic);
	this.premptedChildDegree = premptedChildDegree;
    }


    public int getSizeInBytes() {
	return 1;
    }
    
    /**
     * Returns a String representation of this ack
     *
     * @return A String
     */
    public String toString() {
	return "AcceptPremptedChildMsg: " + topic + " source= " + source + " premptedChildDegree: " + premptedChildDegree;
    }

}
