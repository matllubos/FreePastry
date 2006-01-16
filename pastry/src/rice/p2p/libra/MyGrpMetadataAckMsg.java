package rice.p2p.libra;

import rice.replay.*;
import rice.pastry.PastryNode;
import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;
import rice.p2p.scribe.messaging.*;

/**
 * @(#) MyAnycastAckMessage.java The ack for anycast message.
 *
 */
public class MyGrpMetadataAckMsg extends ScribeMessage {

    public static byte FALSE = 0;
    public static byte TRUE = 1;

    public static byte NULL = 2;
    public static byte NONNULL = 3;

    // This will contain the state of the anycast traversal etc
    public ESMContent content;

    public int seq;
 
    public void dump(rice.replay.ReplayBuffer buffer, PastryNode pn) {
	buffer.appendByte(rice.pastry.commonapi.PastryEndpointMessage.idLibraMyGrpMetadataAckMessage);
	super.dump(buffer,pn);
	content.dump(buffer,pn);
	buffer.appendInt(seq);
    }

    public MyGrpMetadataAckMsg(ReplayBuffer buffer, PastryNode pn) {
	super(buffer,pn);
	content = (ESMContent)Verifier.restoreScribeContent(buffer,pn);
	seq = buffer.getInteger();
	//System.out.println("Constructed MyGrpMetadatAckMessage: " + this);
    }


    public MyGrpMetadataAckMsg(ESMContent content, NodeHandle source, Topic topic, int seq) {
	super(source, topic);
	this.content = content;
	this.seq = seq;
    }


    public ESMContent getContent() {
	return content;
    }

    // This is the sequence corresponding to the GrpMetadataRequesting phase
    public int getSeq() {
	return seq;
    }
    
  /**
   * Returns a String representation of this ack
   *
   * @return A String
   */
    public String toString() {
	return "MyGrpMetadataAckMsg: " + topic + " source= " + source + " content= " + content + " seq= " + seq;
    }

}

