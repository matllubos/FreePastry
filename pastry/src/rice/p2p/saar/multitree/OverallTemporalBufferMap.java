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


// This bmap is used to only track the overall multitree quality across all stripes, it is an array of pairs (seqNum, #of stripes on which it received this seqNum). It need not be used in practice
public class OverallTemporalBufferMap implements Serializable{
    public int lWindow;
    public byte[] present; // instead of a zero or 1 in TemporalBufferMap it will be [0,NUMSTRIPES]

    // WARNING: the (ADVERTISEDWINDOWSIZE - FETCHWINDOWSIZE) bits in the sendbmap are used to determine steady state streaming quality. So you should not set these values to almost equal. Maintain a gap of atleast 15.   
    //public static final int ADVERTISEDWINDOWSIZE = 30; // it should use the same one as TemporalBuffermap
    //public static final int FETCHWINDOWSIZE = 15; // it should use the same one as TemporalBufferMap
    
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
//    public OverallTemporalBufferMap(ReplayBuffer buffer, PastryNode pn) {
//	lWindow = buffer.getInteger();
//	windowsize = buffer.getInteger();
//	present = new byte[windowsize];
//	for(int i=0; i< windowsize; i++) {
//	    present[i] = buffer.getByte();
//	}
//    }



    public OverallTemporalBufferMap(int lWindow, Hashtable numSequence, int windowsize) {
	this.lWindow = lWindow;
	this.windowsize = windowsize;
	present = new byte[windowsize];
	for(int i= 0; i< windowsize; i++) {
	    int seqNum = lWindow + i;
	    if(numSequence.containsKey(new Integer(seqNum))) {
		int val = ((Integer)numSequence.get(new Integer(seqNum))).intValue();
		present[i] = (byte)val;
	    } else {
		present[i] = 0;
	    }
	}
    }


    public OverallTemporalBufferMap(OverallTemporalBufferMap o) {
	this.lWindow = o.lWindow;
	this.windowsize  = o.windowsize;
	this.present = new byte[windowsize];
	for(int i= 0; i< windowsize; i++) {
	    present[i] = o.present[i];
	}

    }

    // We initialize the overalltemporalbuffermap with the temporalbuffermap of a single stripe
     public OverallTemporalBufferMap(TemporalBufferMap o) {
	this.lWindow = o.lWindow;
	this.windowsize  = o.windowsize;
	this.present = new byte[windowsize];
	for(int i= 0; i< windowsize; i++) {
	    present[i] = o.present[i];
	}

    }

    

    // We aggregate two bitmaps using the OR operator. The windowsize is chosen such that the domain is the lWindow = MIN(lWindow1,lWindow2) and rWindow = MAX(rWindow1, rWindow2) 
    public static OverallTemporalBufferMap sum(OverallTemporalBufferMap bmap1, OverallTemporalBufferMap bmap2) {
	OverallTemporalBufferMap bmap = null;
	if((bmap1 == null) && (bmap2 == null)) {
	    return null;
	} else if(bmap1 == null) {
	    bmap = new OverallTemporalBufferMap(bmap2);
	} else if(bmap2 == null) {
	    bmap = new OverallTemporalBufferMap(bmap1);
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
		int count = 0;
		if((offset1 >=0) && (offset1 < bmap1.windowsize)) {
		    count = count + bmap1.present[offset1];
		} 
		if((offset2 >=0) && (offset2 < bmap2.windowsize)) {
		    count = count + bmap2.present[offset2];
		} 
		numSequence.put(new Integer(seqNum), new Integer(count));
		 
	    }
	    bmap = new OverallTemporalBufferMap(lWindow, numSequence, windowsize); 
	    
	}
	return bmap;

    }


    // this tells us the fraction of '1's in the bitmap, considering only the present[0 ...thresh] 
    public int fractionFilled(int thresh, int numRedundantStripes) {
	int streamingQuality = 0;
	int numSet = 0;
	if(thresh > windowsize) {
	    thresh = windowsize;
	}
	
	for(int i=0; i< thresh; i++) {
	    if(present[i] >= (MultitreeClient.NUMSTRIPES - numRedundantStripes)) {
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
	String s = "OVERALLTEMPORALBMAP(" + windowsize + "," + lWindow + ", [";
	for(int i= 0; i< windowsize; i++) {
	    s = s + present[i] + ",";
	}
	s = s + "])";
	return s;
    }
    
}