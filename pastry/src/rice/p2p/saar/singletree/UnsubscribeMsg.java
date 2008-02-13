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
public class UnsubscribeMsg extends SaarDataplaneMessage {

    public static byte FALSE = 0;
    public static byte TRUE = 1;

    public static byte NULL = 2;
    public static byte NONNULL = 3;


//    public void dump(rice.replay.ReplayBuffer buffer, PastryNode pn) {
//
//    }
//    
//    public UnsubscribeMsg(ReplayBuffer buffer, PastryNode pn) {
//	super(buffer,pn);
//    }


    public UnsubscribeMsg(NodeHandle source, Topic topic) {
	super(source, topic);
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
	return "UnsubscribeMsg: " + topic + " source= " + source ;
    }

}
