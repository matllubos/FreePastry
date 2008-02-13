package rice.p2p.saar.blockbased;


import rice.p2p.saar.*;
//import rice.replay.*;
import rice.pastry.PastryNode;
import rice.p2p.commonapi.NodeHandle;
import rice.p2p.scribe.ScribeContent;
import java.util.*;
import java.io.Serializable;




public class RequestTuple implements Serializable{
    public static byte FALSE = 0;
    public static byte TRUE = 1;

    public static byte NULL = 2;
    public static byte NONNULL = 3;

    public int seqNum;
    public int pIndex;
    public int type;
    public Block responderBlock = null; // This will be filled by the responder when we responds successfull, the Block data structure encodes the distribution tree 
    static final int PRIMARY = 1; // these pkts are scheduled using strict BW monitoring and will definitely be answered by parent
    static final int SECONDARY = 2; // these pkts are scheduled using Weighted-Round-Robin on inverse static utilization of parents
    static final int MISSING = 3;
    static final int ANYCASTRECOVERED = 4;
    

    public static int getSizeInBytes() {
	int val = 0;
	val = 3;  // Note that we do not use respondBlock any more
	return val;
    }

    //    public void dump(rice.replay.ReplayBuffer buffer, PastryNode pn) {
    //buffer.appendInt(seqNum);
    //buffer.appendInt(pIndex);
    //buffer.appendInt(type);
    //if(responderBlock == null) {
    //    buffer.appendByte(NULL);
    //} else {
    //    buffer.appendByte(NONNULL);
    //    responderBlock.dump(buffer,pn);
    //}	
    //}

    //public RequestTuple(ReplayBuffer buffer, PastryNode pn) {
    //seqNum = buffer.getInteger();
    //pIndex = buffer.getInteger();
    //type = buffer.getInteger();
    //if(buffer.getByte() == NULL) {
    //    this.responderBlock = null;
    //}else {
    //    this.responderBlock = new Block(buffer, pn);
    //}
    //
    //}


    public RequestTuple(int seqNum, int pIndex, int type) {
	this.seqNum = seqNum;
	this.pIndex = pIndex;
	this.type = type;
	this.responderBlock = null; // this is set to null when the RequestTuple is created at the requestor
    }


    public void setResponderBlock(Block val) {
	if(val == null) {
	    System.out.println("ERROR: Setting the block to be null, this implies that the block was deleted from the temporary cache to early by mistake");
	    System.exit(1);
	}
	responderBlock = val;
    }


    public String toString() {
	String s = "";
	s = s+ "(" + seqNum +"," + pIndex + "," + type + ")";
	return s;
    }
    
    
}
