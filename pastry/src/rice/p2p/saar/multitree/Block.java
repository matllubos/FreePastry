package rice.p2p.saar.multitree;

import rice.p2p.saar.*;
//import rice.replay.*;
import rice.pastry.PastryNode;
import rice.p2p.commonapi.NodeHandle;
import rice.p2p.scribe.ScribeContent;
import java.util.*;
import java.io.Serializable;


// This is different from rice.p2p.saar.blockbased.Block, but serves a similar functionality
public class Block implements Serializable{
    public int stripeId;
    public int seqNum;
    public Vector blockPath;
    public boolean reconstructed; // this is true if the block was generated in the network from the other fragments
    
    public boolean recoveredOutOfBand ; 


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
	this.reconstructed = false;
	this.stripeId = stripeId;
	seqNum = val;
	blockPath = new Vector();
	recoveredOutOfBand = false;
    }

    public Block(Block o) {
	this.reconstructed = o.reconstructed; 
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



    public void setReconstructed(boolean val) {
	reconstructed = val;
    }
    
    public void addToPath(int bindIndex, int jvmIndex, int vIndex) {
	blockPath.add(new SaarContent.NodeIndex(bindIndex, jvmIndex, vIndex));
    }

    public int getDepth() {
	return blockPath.size() - 1;
    }

    public static int getSizeInBytes() {
	return 2; // Note that we accoutn for the DATAMSGBYTES in PublishMsg
    }


    public String toString() {
	String s = "MultitreeBlock(" + stripeId + ", " + seqNum + ", " + recoveredOutOfBand + ", [";
	for(int i=0; i<blockPath.size();i++) {
	    SaarContent.NodeIndex nIndex = (SaarContent.NodeIndex)blockPath.elementAt(i);
	    s = s + nIndex + ", ";
	}
	s = s+ "]" + " reconstructed:" + reconstructed + " )";
	return s;
    }	    	


    
}