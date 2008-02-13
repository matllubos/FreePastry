/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate 

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/

package rice.p2p.saar.blockbased;

import rice.p2p.saar.*;
//import rice.replay.*;
import rice.pastry.PastryNode;
import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;
import rice.p2p.scribe.messaging.*;
import java.util.*;


/**
 * @(#) MyAnycastAckMessage.java The ack for anycast message.
 *
 */
public class BMAPAvailabilityMsg extends SaarDataplaneMessage {

    public static byte FALSE = 0;
    public static byte TRUE = 1;

    public static byte NULL = 2;
    public static byte NONNULL = 3;

    public CoolstreamingBufferMap bmap;

    public int uStatic; // [0,100] this is the staic utilization

    public int uDynamic; // [0,100] this is the dynamic utilization

    public int streamingQuality; // this is actually redundant information and is available from the left portion of the bitmap

    public int avgMeshDepth; 

    public int numGoodNeighbors; 

    public Vector pendingNotifySeqNums;

    public int minDistanceToSource; // This is the minimum distance to the source amongst all paths in the random graph
 

    //  public void dump(rice.replay.ReplayBuffer buffer, PastryNode pn) {
    // buffer.appendByte(rice.pastry.commonapi.PastryEndpointMessage.idLibraBMAPAvailabilityMsg);
    // super.dump(buffer,pn);
    // if(bmap == null) {
    //     buffer.appendByte(Verifier.NULL);
    // } else {
    //     buffer.appendByte(Verifier.NONNULL);
    //     bmap.dump(buffer,pn);
    /// }
    // buffer.appendInt(uStatic);
    // buffer.appendInt(uDynamic);
    // buffer.appendInt(streamingQuality);
    // buffer.appendInt(avgMeshDepth);
    // buffer.appendInt(numGoodNeighbors);
    //}
    
    //public BMAPAvailabilityMsg(ReplayBuffer buffer, PastryNode pn) {
    //super(buffer,pn);
    //byte nullVal = buffer.getByte();
    //if(nullVal == Verifier.NULL) {
    //    bmap = null;
    //}else {
    //    bmap = new CoolstreamingBufferMap(buffer,pn);
    //}
    //uStatic = buffer.getInteger();
    //uDynamic = buffer.getInteger();
    //streamingQuality = buffer.getInteger();
    //avgMeshDepth = buffer.getInteger();
    //numGoodNeighbors = buffer.getInteger();
    //}


	public BMAPAvailabilityMsg(NodeHandle source, Topic topic, CoolstreamingBufferMap bmap, int uStatic, int uDynamic, int streamingQuality, int avgMeshDepth, int numGoodNeighbors, Vector pendingNotifySeqNums, int minDistanceToSource) {
	    super(source, topic);
	    this.bmap = bmap;
	    this.uStatic = uStatic;
	    this.uDynamic = uDynamic;
	    this.streamingQuality = streamingQuality;
	    this.avgMeshDepth = avgMeshDepth;
	    this.numGoodNeighbors = numGoodNeighbors;
	    this.pendingNotifySeqNums = new Vector();
	    for(int i=0; i< pendingNotifySeqNums.size(); i++) {
		int val = ((Integer) pendingNotifySeqNums.elementAt(i)).intValue();
		this.pendingNotifySeqNums.add(new Integer(val));
		
	    }
	    this.minDistanceToSource = minDistanceToSource; 
	    

	}
    

    public CoolstreamingBufferMap getBMAP() {
	return bmap;
    }


    public int getUStatic() {
	return uStatic; 
    }

    public int getUDynamic() {
	return uDynamic;
    }

    public int getStreamingQuality() {
	return streamingQuality;
    }

    public int getAvgMeshDepth() {
	return avgMeshDepth;
    }


    public int getNumGoodNeighbors() {
	return numGoodNeighbors;
    }


    public Vector getPendingNotifySeqNums() {
	return pendingNotifySeqNums;
    }

    public String getPendingNotifySeqNumsString() {
	String s = "PendingNotify(";
	for(int i=0; i< pendingNotifySeqNums.size(); i++) {
	    int val = ((Integer) pendingNotifySeqNums.elementAt(i)).intValue();
	    s = s + val +",";
	}	
	s = s + ")";
	return s;
    }

    public int getMinDistanceToSource() {
	return minDistanceToSource;

    }


    public int getSizeInBytes() {
	int val = 0;
	val = val + bmap.getSizeInBytes();
	val = val + 1; // uStatic
	val = val + 1; //uDynamic
	val = val + 4; //
	val = val + pendingNotifySeqNums.size();
	return val;
    }

  /**
   * Returns a String representation of this ack
   *
   * @return A String
   */
    public String toString() {
	return "BMAPAvailabilityMsg: " + topic + " source= " + source + " bmap= " + bmap + " uStatic= " + uStatic + " uDynamic= " + uDynamic + " streamingQuality= " + streamingQuality + " avgMeshDepth= " + avgMeshDepth + " numGoodNeighbors= " + numGoodNeighbors + " pN= " + getPendingNotifySeqNumsString() + " minDistanceToSource= " + minDistanceToSource;
    }

}

