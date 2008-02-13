package rice.p2p.saar.multitree;

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
public class RequestChildInfoMsg extends SaarDataplaneMessage {

    public static byte FALSE = 0;
    public static byte TRUE = 1;

    public static byte NULL = 2;
    public static byte NONNULL = 3;

    public int stripeId;


    //public int childDegree; // this is the degree of the child, will be used in implementing prempt-degree-pushdown

//    public void dump(rice.replay.ReplayBuffer buffer, PastryNode pn) {
//
//    }
//    
//    public ChildIsAliveMsg(ReplayBuffer buffer, PastryNode pn) {
//	super(buffer,pn);
//    }


    public RequestChildInfoMsg(NodeHandle source, Topic topic, int stripeId) {
	super(source, topic);
	this.stripeId = stripeId;
    }


    // The tree based protocols use this buffermap only for debugging purpose, not used in practice
    public int getSizeInBytes() {
	return 1;
    }
    
    /**
     * Returns a String representation of this ack
     *
     * @return A String
     */
    public String toString() {
	return "RequestChildInfoMsg: " + topic + " source= " + source + " stripeId: " + stripeId ;
    }

}