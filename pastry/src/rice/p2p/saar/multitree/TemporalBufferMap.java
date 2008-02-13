/*
 * Created on May 4, 2005
 */
package rice.p2p.saar.multitree;

import rice.p2p.saar.*;
//import rice.replay.*;
import rice.pastry.PastryNode;
import rice.p2p.commonapi.NodeHandle;
import rice.p2p.scribe.ScribeContent;
import java.util.*;
import java.io.Serializable;


// This bmap is used to only track the overall system quality, it will not be used for the actual dataplane formation in contrast to the bmap in coolstreaming
public class TemporalBufferMap implements Serializable{
    public int lWindow;
    public byte[] present;

    // WARNING: the (ADVERTISEDWINDOWSIZE - FETCHWINDOWSIZE) bits in the sendbmap are used to determine steady state streaming quality. So you should not set these values to almost equal. Maintain a gap of atleast 15.   
    public static final int ADVERTISEDWINDOWSIZE = 30;
    public static final int FETCHWINDOWSIZE = 15; // we still maintain the FETCHWINDOW of 15-30 since a node can lag the multicast source by 15-30 pkt if it is at depth 30 in the dataplane
    
    public int windowsize; // this is the windowsize that will be used, it will be either advertisedwindow/fetchwindow


//    public void dump(ReplayBuffer buffer, PastryNode pn) {
//	buffer.appendInt(lWindow);
//	buffer.appendInt(windowsize);
//	for(int i=0; i< windowsize; i++) {
//	    buffer.appendByte(present[i]);
//	}
//
//    }
//
//    public TemporalBufferMap(ReplayBuffer buffer, PastryNode pn) {
//	lWindow = buffer.getInteger();
//	windowsize = buffer.getInteger();
//	present = new byte[windowsize];
//	for(int i=0; i< windowsize; i++) {
//	    present[i] = buffer.getByte();
//	}
//    }


    public TemporalBufferMap(int lWindow, Hashtable numSequence, int windowsize) {
	this.lWindow = lWindow;
	this.windowsize = windowsize;
	present = new byte[windowsize];
	for(int i= 0; i< windowsize; i++) {
	    int seqNum = lWindow + i;
	    if(numSequence.containsKey(new Integer(seqNum))) {
		present[i] = 1;
	    } else {
		present[i] = 0;
	    }
	}
    }


    public TemporalBufferMap(TemporalBufferMap o) {
	this.lWindow = o.lWindow;
	this.windowsize  = o.windowsize;
	this.present = new byte[windowsize];
	for(int i= 0; i< windowsize; i++) {
	    present[i] = o.present[i];
	}

    }

    // We aggregate two bitmaps using the OR operator. The windowsize is chosen such that the domain is the lWindow = MIN(lWindow1,lWindow2) and rWindow = MAX(rWindow1, rWindow2) 
    public static TemporalBufferMap aggregate(TemporalBufferMap bmap1, TemporalBufferMap bmap2) {
	TemporalBufferMap bmap = null;
	if((bmap1 == null) && (bmap2 == null)) {
	    return null;
	} else if(bmap1 == null) {
	    bmap = new TemporalBufferMap(bmap2);
	} else if(bmap2 == null) {
	    bmap = new TemporalBufferMap(bmap1);
	} else {
	    int lWindow;
	    int rWindow;
	    int windowsize;
	    Hashtable numSequence = new Hashtable();
	    if(bmap1.lWindow < bmap2.lWindow) {
		lWindow = bmap1.lWindow;
	    } else {
		lWindow = bmap2.lWindow;
	    }
	    if((bmap1.lWindow + bmap1.windowsize) > (bmap2.lWindow + bmap2.windowsize)) {
		rWindow = bmap1.lWindow + bmap1.windowsize;
	    } else {
		rWindow = bmap2.lWindow + bmap2.windowsize;
	    }
	    windowsize = rWindow - lWindow;
	    for(int i= 0; i< windowsize; i++) {
		int seqNum = lWindow + i;
		int offset1 = seqNum - bmap1.lWindow;
		int offset2 = seqNum - bmap2.lWindow;
		if(((offset1 >=0) && (offset1 < bmap1.windowsize) && (bmap1.present[offset1] == 1)) || ((offset2 >=0) && (offset2 < bmap2.windowsize) && (bmap2.present[offset2] == 1))) {
		    numSequence.put(new Integer(seqNum), new Integer(1));
		} 
	    }
	    bmap = new TemporalBufferMap(lWindow, numSequence, windowsize); 
	    
	}
	return bmap;

    }

    // this function will be called at a prospecxtive responder to a Chunkcast anycast query, it fills in a vector of the blocks that it has locally based on the moving window. Note that we still use the concept of moving window it might be interesting to see how varying the size of this buffer affects things
    public Vector blocksCanProvide(Vector missingBlocks) {
	//System.out.println("myPresentBlocks: " + getPresentBlocks());
	Vector blocksCanProvide = new Vector();
	for(int i=0; i< missingBlocks.size();i++) {
	    int seqNum = ((Integer)missingBlocks.elementAt(i)).intValue();
	    int offset = seqNum - lWindow;
	    //System.out.println("seqNum: " + seqNum + ", offset: " + offset + ", windowsize: " + windowsize);
	    if((offset >0) && (offset < windowsize)) {
		//System.out.println("present[offset]: " + present[offset]);
	    }
	    if((offset >0) && (offset < windowsize) && (present[offset] == 1)) {
		blocksCanProvide.add(new Integer(seqNum));
	    }
	}
	return blocksCanProvide;
    }


    // The missing blocks are in order of the most critical to least critical by first iterating the left end sequence numbers. 
    public Vector missingBlocks() {
	Vector val = new Vector();
	for(int i= 0; i< windowsize; i++) {

	    int seqNum = lWindow + i;
	    if(present[i] == 0) {
		val.add(new Integer(seqNum));
	    }
	}	
	return val;
    }

    public boolean containsSeqNum(int val) {
	int offset = val - lWindow;
	if((offset < 0) || (offset >=windowsize)) {
	    return false;
	} else {
	    if(present[offset] == 0) {
		return false;
	    } else {
		return true;
	    }
	}
    }
    
    public String getPresentBlocks() {
	String s = "(";
	for(int i= 0; i< windowsize; i++) {
	    if(present[i] == 1) {
		s = s + (lWindow + i) +",";
	    }
	}
	s = s + ")";
	return s;	
    }

    // this tells us the fraction of '1's in the bitmap, considering only the present[0 ...thresh] 
    public int fractionFilled(int thresh) {
	int streamingQuality = 0;
	int numSet = 0;
	if(thresh > windowsize) {
	    thresh = windowsize;
	}
	
	for(int i=0; i< thresh; i++) {
	    if(present[i] == 1) {
		numSet ++;
	    }
	}
	streamingQuality = (int)(((double)numSet)/((double)thresh)*100);
	return streamingQuality;
    }


    // The tree based protocols use this buffermap only for debugging purpose, not used in practice
    public static int getSizeInBytes() {
	return 1;
    }
    
    public String toString() {
	String s = "TEMPORALBMAP(" + windowsize + "," + lWindow + ", [";
	for(int i= 0; i< windowsize; i++) {
	    if(present[i] == 0) {
		s = s + "0,";
	    } else {
		s = s + "1,";
	    }
	}
	s = s + "])";
	return s;
    }
    
}