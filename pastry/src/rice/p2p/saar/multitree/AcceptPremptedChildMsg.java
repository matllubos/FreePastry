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
public class AcceptPremptedChildMsg extends SaarDataplaneMessage {

    public static byte FALSE = 0;
    public static byte TRUE = 1;

    public static byte NULL = 2;
    public static byte NONNULL = 3;

    public int stripeId;

    public int premptedChildPrimaryStripeId;

    public int premptedChildDegree;



//    public void dump(rice.replay.ReplayBuffer buffer, PastryNode pn) {
//
//    }
//    
//    public AcceptPremptedChildMsg(ReplayBuffer buffer, PastryNode pn) {
//	super(buffer,pn);
//    }


    public AcceptPremptedChildMsg(NodeHandle source, Topic topic, int stripeId, int premptedChildDegree, int premptedChildPrimaryStripeId) {
	super(source, topic);
	this.stripeId = stripeId;
	this.premptedChildPrimaryStripeId = premptedChildPrimaryStripeId;
	this.premptedChildDegree = premptedChildDegree;
    }


    public int getSizeInBytes() {
	int val = 0;
	val = 3;
	return val;
    }

    
    /**
     * Returns a String representation of this ack
     *
     * @return A String
     */
    public String toString() {
	return "AcceptPremptedChildMsg: " + topic + " source= " + source + " stripeId= " + stripeId + " premptedChildPrimaryStripeId: " + premptedChildPrimaryStripeId + " premptedChildDegree: " + premptedChildDegree;
    }

}
