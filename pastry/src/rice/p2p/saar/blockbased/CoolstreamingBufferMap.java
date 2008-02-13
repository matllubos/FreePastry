/*
 * Created on May 4, 2005
 */
package rice.p2p.saar.blockbased;

import rice.p2p.saar.*;
//import rice.replay.*;
import rice.pastry.PastryNode;
import rice.p2p.commonapi.NodeHandle;
import rice.p2p.scribe.ScribeContent;
import java.util.*;
import java.io.Serializable;



public class CoolstreamingBufferMap implements Serializable{
    public int lWindow;
    public MyBitSet bitset;
    
    // WARNING: the (ADVERTISEDWINDOWSIZE - FETCHWINDOWSIZE) bits in the sendbmap are used to dtermine streaming quality. So you should not set these values to almost equal. Maintain a gap of atleast 15.   
    public static int ADVERTISEDWINDOWSIZE = 90;
    public static int FETCHWINDOWSIZE = 60;
    
    public int windowsize; // this is the windowsize that will be used, it will be either advertisedwindow/fetchwindow


    
    public CoolstreamingBufferMap(int lWindow, MyBitSet numSequence, int windowsize) {
	this.lWindow = lWindow;
	this.windowsize = windowsize;
	bitset = new MyBitSet();;
	for(int i= 0; i< windowsize; i++) {
	    int seqNum = lWindow + i;
	    //if(numSequence.containsKey(new Integer(seqNum))) {
	    if(numSequence.get(seqNum)) {
		bitset.set(seqNum);
	    }
	}
    }


    public CoolstreamingBufferMap(CoolstreamingBufferMap o) {
	this.lWindow = o.lWindow;
	this.windowsize  = o.windowsize;
	this.bitset = new MyBitSet(o.bitset);

    }


      // We aggregate two bitmaps using the OR operator. The windowsize is chosen such that the domain is the lWindow = MIN(lWindow1,lWindow2) and rWindow = MAX(rWindow1, rWindow2) 
    public static CoolstreamingBufferMap aggregate(CoolstreamingBufferMap bmap1, CoolstreamingBufferMap bmap2) {
	CoolstreamingBufferMap bmap = null;
	if((bmap1 == null) && (bmap2 == null)) {
	    return null;
	} else if(bmap1 == null) {
	    bmap = new CoolstreamingBufferMap(bmap2);
	} else if(bmap2 == null) {
	    bmap = new CoolstreamingBufferMap(bmap1);
	} else {
	    int lWindow;
	    int rWindow;
	    int windowsize;
	    //Hashtable numSequence = new Hashtable();
	    MyBitSet numSequence = new MyBitSet();
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
		if(((offset1 >=0) && (offset1 < bmap1.windowsize) && bmap1.bitset.get(seqNum)) || ((offset2 >=0) && (offset2 < bmap2.windowsize) && bmap2.bitset.get(seqNum))) {
		    //numSequence.put(new Integer(seqNum), new Integer(1));
		    numSequence.set(seqNum);
		} 
	    }
	    bmap = new CoolstreamingBufferMap(lWindow, numSequence, windowsize); 
	    
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
	    //if((offset >=0) && (offset < windowsize)) {
		//System.out.println("present[offset]: " + present[offset]);
	    //}
	    if((offset >=0) && (offset < windowsize) && bitset.get(seqNum)) {
		blocksCanProvide.add(new Integer(seqNum));
	    }
	}
	return blocksCanProvide;
    }


    // The missing blocks are in order of the most critical to least critical by first iterating the left end sequence numbers. We however consider only in a subwindow which is aligned to the right. For example, we can form the SendBMAP with ADVERTISEDWINDOWSIZE, and calculate the missing blocks based on the right aligned subwindow of size  FETCHWINDOWSIZE, thus the leftOffsetSubWindow = ADVERTISEDWINDOW - FETCHWINDOW will  
    public Vector missingBlocks(int leftOffsetSubWindow) {
	Vector val = new Vector();
	for(int i= leftOffsetSubWindow; i< windowsize; i++) {

	    int seqNum = lWindow + i;
	    if(!bitset.get(seqNum)) {
		val.add(new Integer(seqNum));
	    }
	}	
	return val;
    }


    public boolean containsSeqNum(int val) {
	//System.out.println("containsSeqNum(" + val + "," + lWindow + "," + windowsize + "," + this);
	
	int offset = val - lWindow;
	if((offset < 0) || (offset >=windowsize)) {
	    return false;
	} else {
	    if(!bitset.get(val)) {
		return false;
	    } else {
		return true;
	    }
	}
    }
    
    public String getPresentBlocks() {
	String s = "(";
	for(int i= 0; i< windowsize; i++) {
	    int seqNum = lWindow + i;
	    if(bitset.get(seqNum)) {
		s = s + seqNum +",";
	    }
	}
	s = s + ")";
	return s;	
    }


     // this returns the maximum sequence number of the block that is available in this buffermap, return -1 if all positions are empty
    public int maxBlockPresent() {
	for(int i= (windowsize - 1); i >= 0; i--) {
	    int seqNum = lWindow + i;
	    if(bitset.get(seqNum)) {
		return seqNum;
	    }
	}	
	return -1;

    }
    
     // this tells us the fraction of '1's in the bitmap, considering only the present[0 ...thresh] 
    public int fractionFilled(int thresh) {
	int streamingQuality = 0;
	int numSet = 0;
	if(thresh > windowsize) {
	    thresh = windowsize;
	}
	
	for(int i=0; i< thresh; i++) {
	    int seqNum = lWindow + i;
	    if(bitset.get(seqNum)) {
		numSet ++;
	    }
	}
	streamingQuality = (int)(((double)numSet)/((double)thresh)*100);
	return streamingQuality;
    }

    // buffermap in bits + lWindow + windowsize
    public int getSizeInBytes() {
	return (int) ((windowsize/8) + 1 + 1); 
    }
    
    public String toString() {
	String s = "BMAP(" + windowsize + "," + lWindow + ", [";
	for(int i= 0; i< windowsize; i++) {
	    int seqNum = lWindow + i;
	    if(!bitset.get(seqNum)) {
		s = s + "0,";
	    } else {
		s = s + "1,";
	    }
	}
	s = s + "])";
	return s;
    }
    
}