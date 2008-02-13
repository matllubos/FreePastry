package rice.p2p.saar.blockbased;

import rice.p2p.saar.*;
//import rice.replay.*;
import rice.pastry.PastryNode;
import rice.p2p.commonapi.NodeHandle;
import rice.p2p.scribe.ScribeContent;
import java.util.*;
import java.io.Serializable;


public class Block implements Serializable{
     public int stripeId; // We introduce stripeId even in blockbased/mesh because we can use encoding techniques like MDC encoding in the time domain
    public int seqNum;
    public Vector blockPath;
    
    public boolean recoveredOutOfBand ; 

    public boolean anycastShortCircuit; // this is set to true if the block anytime in the path was got via an anycast short circuit, this implies that this block should not be considered to improve the quality of the mesh using the depth optimization
    
    // public void dump(ReplayBuffer buffer, PastryNode pn) {
    //buffer.appendInt(seqNum);
    //buffer.appendInt(blockPath.size());
    //for(int i=0; i< blockPath.size(); i++) {
	//    SaarContent.NodeIndex val = (SaarContent.NodeIndex)blockPath.elementAt(i);
    //   val.dump(buffer,pn);
    //}
    //if(anycastShortCircuit) {
    //    buffer.appendByte(Verifier.TRUE);
    //} else {
    //    buffer.appendByte(Verifier.FALSE);
    //}
    //}
    //
    //public Block(ReplayBuffer buffer, PastryNode pn) {
    //seqNum = buffer.getInteger();
    //int blockPathSize = buffer.getInteger();
    //blockPath = new Vector();
    //for(int i=0; i< blockPathSize; i++) {
    //    blockPath.add(new SaarContent.NodeIndex(buffer,pn));
    //}
    //byte booleanVal = buffer.getByte();
    //	if(booleanVal == Verifier.TRUE) {
    //    anycastShortCircuit = true;
    //} else {
    //    anycastShortCircuit = false;
    //}
    //
    //}
    
    public Block(int stripeId, int val) {
	this.stripeId = stripeId;
	seqNum = val;
	blockPath = new Vector();
	anycastShortCircuit = false;
	recoveredOutOfBand = false;
    }



    
    // This will be specially used in the simulator which needs to explicitly clone the blocks
    public Block(Block o) {
	this.stripeId = stripeId;
	this.seqNum = o.seqNum;
	this.blockPath = new Vector();
	for(int i=0; i< o.blockPath.size(); i++) {
	    SaarContent.NodeIndex nIndex = (SaarContent.NodeIndex) o.blockPath.elementAt(i);
	    this.blockPath.add(nIndex);
	}
	this.anycastShortCircuit = o.anycastShortCircuit;
	this.recoveredOutOfBand = o.recoveredOutOfBand;

    }


    public void setRecoveredOutOfBand() {
	recoveredOutOfBand = true;
    }


    public void setAnycastShortCircuit() {
	anycastShortCircuit = true;
    }
    
    public void addToPath(int bindIndex, int jvmIndex, int vIndex) {
	blockPath.add(new SaarContent.NodeIndex(bindIndex, jvmIndex, vIndex));
    }

    public int getDepth() {
	return blockPath.size() - 1;
    }

    public static int getSizeInBytes() {
	return 2; // Note that we accoutn for the DATAMSGBYTES in RespondBlocksMsg
    }


    public String toString() {
	String s = "MeshbasedBlock(" + stripeId + ", " + seqNum + ", " + recoveredOutOfBand + ", [";
	for(int i=0; i<blockPath.size();i++) {
	    SaarContent.NodeIndex nIndex = (SaarContent.NodeIndex)blockPath.elementAt(i);
	    s = s + nIndex + ", ";
	}
	s = s+ "])";
	return s;
    }	    	


    
}