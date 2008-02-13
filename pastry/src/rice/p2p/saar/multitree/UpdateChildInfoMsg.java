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
public class UpdateChildInfoMsg extends SaarDataplaneMessage {

    public static byte FALSE = 0;
    public static byte TRUE = 1;

    public static byte NULL = 2;
    public static byte NONNULL = 3;

    public int stripeId;

    public boolean isPrimaryContributor;

    public int childDegree; // This is the maximumOutdegree of the child on this stripe


    public int childsPrimaryStripeId;



    //public int childDegree; // this is the degree of the child, will be used in implementing prempt-degree-pushdown

//    public void dump(rice.replay.ReplayBuffer buffer, PastryNode pn) {
//
//    }
//    
//    public ChildIsAliveMsg(ReplayBuffer buffer, PastryNode pn) {
//	super(buffer,pn);
//    }


    public UpdateChildInfoMsg(NodeHandle source, Topic topic, int stripeId, boolean isPrimaryContributor, int childDegree, int childsPrimaryStripeId) {
	super(source, topic);
	this.stripeId = stripeId;
	this.isPrimaryContributor = isPrimaryContributor;
	this.childDegree = childDegree;
	this.childsPrimaryStripeId = childsPrimaryStripeId; 
    }

    
    public boolean getIsPrimaryContributor() {
	return isPrimaryContributor;
    }

    public int getChildDegree() {
	return childDegree;
    }


    public int getChildsPrimaryStripeId() {
	return childsPrimaryStripeId;
    }

    // The tree based protocols use this buffermap only for debugging purpose, not used in practice
    public int getSizeInBytes() {
	return 4;
    }

    /**
     * Returns a String representation of this ack
     *
     * @return A String
     */
    public String toString() {
	return "UpdateChildInfoMsg: " + topic + " source= " + source + " stripeId: " + stripeId + " isPrimaryContributor: " + isPrimaryContributor + " childDegree: " + childDegree + " childsPrimaryStripeId: " + childsPrimaryStripeId;
    }

}
