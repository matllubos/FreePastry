package rice.p2p.saar.singletree;

import rice.p2p.saar.*;
//import rice.replay.*;
import rice.pastry.PastryNode;
import rice.p2p.commonapi.NodeHandle;
import rice.p2p.scribe.ScribeContent;
import java.util.*;
import java.io.Serializable;


// This is different from rice.p2p.saar.blockbased.Block, but serves a similar functionality
public class Block implements Serializable{
    public int stripeId; // We introduce stripeId even in single-tree because we will use encoding techniques like encoding in the time domain
    public int seqNum;

    public boolean recoveredOutOfBand ; 

    public Vector blockPath;
    
//    public void dump(ReplayBuffer buffer, PastryNode pn) {
//	buffer.appendInt(stripeId);
//	buffer.appendInt(seqNum);
//	buffer.appendInt(blockPath.size());
//	for(int i=0; i< blockPath.size(); i++) {
//	    SaarContent.NodeIndex val = (SaarContent.NodeIndex)blockPath.elementAt(i);
//	    val.dump(buffer,pn);
//	}
//    }
//    
//    public Block(ReplayBuffer buffer, PastryNode pn) {
//	stripeId = buffer.getInteger();
//	seqNum = buffer.getInteger();
//	int blockPathSize = buffer.getInteger();
//	blockPath = new Vector();
//	for(int i=0; i< blockPathSize; i++) {
//	    blockPath.add(new SaarContent.NodeIndex(buffer,pn));
//	}
//
//    }
    
    public Block(int stripeId, int val) {
	this.stripeId = stripeId;
	seqNum = val;
	blockPath = new Vector();
	recoveredOutOfBand = false;
	
    }

    public Block(Block o) {
	this.stripeId = o.stripeId;
	this.seqNum = o.seqNum;
	blockPath= new Vector();
	for(int i=0; i< o.blockPath.size(); i++) {
	    blockPath.add(o.blockPath.elementAt(i));
	}
	this.recoveredOutOfBand = o.recoveredOutOfBand;

    }
    

    public void setRecoveredOutOfBand() {
	recoveredOutOfBand = true;
    }

    
    public void addToPath(int bindIndex, int jvmIndex, int vIndex) {
	blockPath.add(new SaarContent.NodeIndex(bindIndex, jvmIndex, vIndex));
    }

    public int getDepth() {
	return blockPath.size() - 1;
    }

    public static int getSizeInBytes() {
	return 2; // Note that we accoutn for the DATAMSGBYTES in publishmsg
    }


    public String toString() {
	String s = "SingletreeBlock(" + stripeId + ", " + seqNum + ", " + recoveredOutOfBand +  ", [";
	for(int i=0; i<blockPath.size();i++) {
	    SaarContent.NodeIndex nIndex = (SaarContent.NodeIndex)blockPath.elementAt(i);
	    s = s + nIndex + ", ";
	}
	s = s+ "])";
	return s;
    }	    	


    
}