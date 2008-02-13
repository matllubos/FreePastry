package rice.p2p.saar.singletree;

import rice.p2p.saar.*;
//import rice.replay.*;
import rice.pastry.PastryNode;
import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;
import rice.p2p.scribe.messaging.*;
import java.util.*;

/**
 */
public class PublishMsg extends SaarDataplaneMessage {

    public static byte FALSE = 0;
    public static byte TRUE = 1;

    public static byte NULL = 2;
    public static byte NONNULL = 3;

    public Block block;


//    public void dump(rice.replay.ReplayBuffer buffer, PastryNode pn) {
//
//    }
//    
//    public PublishMsg(ReplayBuffer buffer, PastryNode pn) {
//	super(buffer,pn);
//
//    }

    
    public PublishMsg(NodeHandle source, Topic topic, Block block) {
	super(source, topic);
	this.block = new Block(block);
    }

    public Block getBlock() {
	return block;
    }



    // Note that the Block does not include the actual data, it only contains seqnum/stripeId
    public int getSizeInBytes() {
	int val;
	val  = Block.getSizeInBytes() + rice.p2p.saar.singletree.SingletreeClient.DATAMSGSIZEINBYTES;
	return val;
    }

    /**
     * Returns a String representation of this ack
     *
     * @return A String
     */
    public String toString() {
	return "PublishMsg: " + topic + " source= " + source + " block: " + block;
    }

}

