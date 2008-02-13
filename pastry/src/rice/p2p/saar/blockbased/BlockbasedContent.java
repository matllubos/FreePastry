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
import rice.environment.logging.Logger;


/**
 * @author Animesh Nandi
 */

// This is used for anycasting
public class BlockbasedContent extends SaarContent {
    // These are 4 modes in which SaarContent will be used (mode '1' and '2' are defined in SaarContent and will be used for upward/downward propagation of metadata
    public static int ANYCASTNEIGHBOR = 3; // when using anycast for mesh neighbor establishment
    public static int ANYCASTBLOCKS = 4; // when using anycast for block recovery



    /*****  AGGREGATION/DOWNWARDPROPAGATE variables **/
    public int sourceBroadcastSeq;   // this is the current value of the sequence number that is being broadcasted by the source
    public boolean aggregateFlag = false; // the metadata 
    public int descendants; // The usual meaning of Scribe tree descendants
    public CoolstreamingBufferMap bmap = null;
    public int uStatic = -1;
    public int uDynamic = -1;
    // This is the streaming loss as estimated over the long term bitmap
    public int streamingQuality = -1; 
    public int avgMeshDepth = -1;


    /***** ANYCAST variables ***/
    public Vector anycastRequestBlocks; // assume it contains integers when doing serialization
    public Vector respondBlocks;
    public boolean amMulticastSource; // this field is set by the anycast responder to guide the anycast requestor to know if it should timeout this neighbor connection or not. Connections with the multicast sources are not timed out, others are to keep the mesh structure updated with time

    

    public static class NodeIndex implements Serializable {
	public int bindIndex; // This is the node index corresponding to the bindAddress
	public int jvmIndex; // This is one instance of Pastry JVM
	public int vIndex; // This is one instance of a Pastry virtual node

	public NodeIndex(int bindIndex, int jvmIndex, int vIndex) {
	    this.bindIndex = bindIndex;
	    this.jvmIndex = jvmIndex;
	    this.vIndex = vIndex;
	}

	//	public void dump(ReplayBuffer buffer, PastryNode pn) {
	//  buffer.appendShort(bindIndex);
	//  buffer.appendShort(jvmIndex);
	//  buffer.appendShort(vIndex);
	//
	//}
	//
	//public NodeIndex(ReplayBuffer buffer, PastryNode pn) {
	//  bindIndex = buffer.getShort();
	//  jvmIndex = buffer.getShort();
	//  vIndex = buffer.getShort();
	//}


	public boolean equals(Object obj) {
	    if ((obj == null) || (!(obj instanceof NodeIndex)))
		return false;
	    NodeIndex nIndex = (NodeIndex) obj;
	    if ((bindIndex != nIndex.bindIndex) || (jvmIndex != nIndex.jvmIndex) || (vIndex != nIndex.vIndex)) {
		return false;
	    }
	    return true;
	}

	public String toString() {
	    String s = "(";
	    s = s +bindIndex +":" + jvmIndex + ":" + vIndex;
	    s = s + ")";
	    return s;
	}

    }



    // This constructor should copy over al fields (over all modes)
    public BlockbasedContent(BlockbasedContent o) {
	super(o);
	this.sourceBroadcastSeq = o.sourceBroadcastSeq;   
	this.aggregateFlag = o.aggregateFlag; 
	this.descendants = o.descendants; 
	if(o.bmap == null) {
	    this.bmap = null;
	} else {
	    this.bmap = new CoolstreamingBufferMap(o.bmap);
	}
	this.uStatic = o.uStatic;
	this.uDynamic = o.uDynamic;
	this.streamingQuality = o.streamingQuality; 
	this.avgMeshDepth = o.avgMeshDepth;
	if(o.anycastRequestBlocks == null) {
	    this.anycastRequestBlocks = null;
	} else {
	    this.anycastRequestBlocks = new Vector(); 
	    for(int i=0; i<o.anycastRequestBlocks.size();i++) {
		this.anycastRequestBlocks.add(o.anycastRequestBlocks.elementAt(i));
	    }
	}

	if(o.respondBlocks == null) {
	    this.respondBlocks = null;
	} else {
	    this.respondBlocks = new Vector();
	    for(int i=0; i<o.respondBlocks.size();i++) {
		this.respondBlocks.add(o.respondBlocks.elementAt(i));
	    }
	}

    }



    // Constructor used in ANYCASTNEIGHBOR
    public BlockbasedContent(int mode, String topicName, int tNumber) {
	super(mode,topicName,tNumber);
	if(this.mode != ANYCASTNEIGHBOR) {
	    System.out.println("ERROR: Constructor(mode=BlockbasedContent.ANYCASTNEIGHBOR) does not have right parameters");
	    System.exit(1);
	}
    }


    // Constructor used in UPWARDAGGREGATION
    public BlockbasedContent(int mode, String topicName, int tNumber, boolean aggregateFlag, int descendants, CoolstreamingBufferMap bmap, int uStatic, int uDynamic, int streamingQuality, int avgMeshDepth, int sourceBroadcastSeq) {
	super(mode,topicName,tNumber);
	if(this.mode != SaarContent.UPWARDAGGREGATION) {
	    System.out.println("ERROR: Constructor(mode=BlockbasedContent.UPWARDAGGREGATION) does not have right parameters");
	    System.exit(1);
	}
	this.aggregateFlag = aggregateFlag;
	this.descendants = descendants;
	if (bmap == null) {
	    this.bmap = null;
	} else {
	    this.bmap = new CoolstreamingBufferMap(bmap);
	}
	this.uStatic = uStatic;
	this.uDynamic = uDynamic;
	this.streamingQuality = streamingQuality;
	this.avgMeshDepth = avgMeshDepth;
	this.sourceBroadcastSeq = sourceBroadcastSeq;

    }




    //public void dump(ReplayBuffer buffer, PastryNode pn) {
    //
    //
    //
    //}



    public SaarContent aggregate(SaarContent otherContent) {
	if(this.mode != SaarContent.UPWARDAGGREGATION) {
	    System.out.println("ERROR: aggregate should only be used with mode=BlockbasedContent.UPWARDAGGREGATION");
	    System.exit(1);
	}
	BlockbasedContent retContent = null;
	if(otherContent == null) {
	    retContent = new BlockbasedContent(this);
	    return retContent;
	}
	

	BlockbasedContent o = (BlockbasedContent)otherContent;
	if(o.mode != SaarContent.UPWARDAGGREGATION) {
	    System.out.println("ERROR: aggregate should only be used with mode=BlockbasedContent.UPWARDAGGREGATION");
	    System.exit(1);
	}


	if((!this.topicName.equals(o.topicName)) || (this.descendants == 0) || (o.descendants == 0)) {
	    System.out.println("aggregate() failed because topicNames dont match or descendants is zero");
	    System.exit(1);
	}
	String myTopicName = this.topicName;
	int myTNumber = this.tNumber;
	int mydescendants = this.descendants + o.descendants;
	// the BMAP will be aggregated using the OR operator
	CoolstreamingBufferMap mybmap = CoolstreamingBufferMap.aggregate(this.bmap, o.bmap);

	int myuStatic = ((this.descendants * this.uStatic) + (o.descendants * o.uStatic))/ mydescendants;
	int myuDynamic = ((this.descendants * this.uDynamic) + (o.descendants * o.uDynamic))/ mydescendants;
	int mystreamingQuality = ((this.descendants * this.streamingQuality) + (o.descendants * o.streamingQuality))/ mydescendants;
	int myavgMeshDepth = ((this.descendants * this.avgMeshDepth) + (o.descendants * o.avgMeshDepth))/ mydescendants;
	int mySourceBroadcastSeq; // consider the greater of the two
	if(this.sourceBroadcastSeq > o.sourceBroadcastSeq) {
	    mySourceBroadcastSeq = this.sourceBroadcastSeq;
	} else {
	    mySourceBroadcastSeq = o.sourceBroadcastSeq;
	}
	

	retContent = new BlockbasedContent(SaarContent.UPWARDAGGREGATION, myTopicName, myTNumber,  true, mydescendants, mybmap,  myuStatic, myuDynamic, mystreamingQuality, myavgMeshDepth, mySourceBroadcastSeq);

	return retContent;

    }


    // Creates a new copy
    public SaarContent duplicate() {
	return new BlockbasedContent(this);

    }



    
    // Given the anycast requestor's content, the anycast traversal logic requires the dataplane to implement if the content satisfies the boolean predicate 
    public boolean predicateSatisfied(SaarContent anycastrequestorContent) {
	boolean toReturn = false;
	if(aggregateFlag) {
	    if((uStatic >= 100) || (streamingQuality < BlockbasedClient.PARENTSTREAMINGQUALITYTHRESHOLD)) {
		toReturn = false;
	    } else {
		toReturn = true;
	    }  

	} else {
	    if(anycastrequestorContent.anycastRequestor.equals(aggregator)) {
		toReturn = false; // The anycast requestor can be left out if the aggregateflag is false
	    } else if((uStatic >= 100) || (streamingQuality < BlockbasedClient.PARENTSTREAMINGQUALITYTHRESHOLD)) {
		toReturn = false;
	    } else {
		toReturn = true;
	    }
	}
	return toReturn;

    }



    public boolean negligibleChangeUpwardUpdate(SaarContent otherContent) {
	if(otherContent == null) {
	    return false;
	}
	BlockbasedContent o = (BlockbasedContent)otherContent;
	if((this.sourceBroadcastSeq != o.sourceBroadcastSeq) || (this.aggregateFlag != o.aggregateFlag) || (this.descendants != o.descendants) || (this.uStatic != o.uStatic) || (Math.abs(this.streamingQuality - o.streamingQuality) > 5)) {
	    return false;
	} else {
	    return true;
	}
    }


    public boolean negligibleChangeDownwardPropagate(SaarContent otherContent) {
	if(otherContent == null) {
	    return false;
	}
	BlockbasedContent o = (BlockbasedContent)otherContent;
	if((this.aggregateFlag != o.aggregateFlag) || (Math.abs(this.descendants - o.descendants) > 10)) {
	    return false;  // WARNING: We commented this out during the NSDI camera ready to reduce the tail in the control overhead. The fields are merely for aesthetic beauty and does not have any impact on the block-based results
	} 
	if(Math.abs(this.streamingQuality - o.streamingQuality) > 25) {
	    return false;
	}
	if(Math.abs(this.sourceBroadcastSeq - o.sourceBroadcastSeq) > 10) {
	    return false;
	} else {
	    return true;
	}
    }
    


    // Given the anycast requestor's content, the anycast traversal logic requires the dataplane to implement an ordering function.
    // Returns  0 : both are equal (the underlying SaarPolicy takes care of randomizing equal entries)
    //         -1 : this ('>' or 'isSuperiorTo') otherContent
    //         +1 : this ('>' or 'isSuperiorTo') otherContent 
    // Also note that you may want to prioritize non-agregate metadata over aggregate metadata so that the anycast traversal completes quicker

    public int compare(SaarContent anycastrequestorContent, SaarContent otherContent) {
	// We treat all children as equal (given ofcourse that they have satisfied the boolean predicate), however we do bias towards the contents with aggregate flag NOT set (i.e leafs)
	BlockbasedContent o = (BlockbasedContent)otherContent;
	
	if((aggregateFlag == false) && (o.aggregateFlag == true)) {
	    return +1;
	} else if((aggregateFlag == true) && (o.aggregateFlag == false)) {
	    return -1;
	} else {
	    return 0;
	}
	
    }

    // Note that we dont use the anycastRequestBlocks and respondBlocks and bmap in practice. These were added to test the idea of Chunkcast
    public int getSizeInBytes() {
	int val = getbasecostofsaarcontentInBytes(); // initialized for the mode
	if(this.mode == ANYCASTNEIGHBOR) {
	    val = val + 1 ;  // note that we do not use the anycastrequestblocks for Chubkcast
	} else if((mode == SaarContent.UPWARDAGGREGATION) || (mode == SaarContent.DOWNWARDPROPAGATE)) {
	    val = val + 8;
	}
	return val; 

    }




    public String toString() {
	String s = "BlockbaseContent:";
	s = s + " mode:" + mode;
	if(mode == BlockbasedContent.ANYCASTNEIGHBOR) {
	    s = s + " anycastGlobalId:" + anycastGlobalId; 
	} else if(mode == SaarContent.UPWARDAGGREGATION) {
	    s = s + " aggregateFlag:" + aggregateFlag;
	    s = s + " sourceBroadcastSeq:" + sourceBroadcastSeq;
	    s = s + " descendants:" + descendants;
	    s = s + " bmap:" + bmap;
	    s = s + " uStatic:" + uStatic;
	    s = s + " uDynamic:" + uDynamic;
	    s = s + " streamingQuality:" + streamingQuality;
	    s = s + " avgMeshDepth:" + avgMeshDepth;

	} else if(mode == SaarContent.DOWNWARDPROPAGATE) {
	    s = s + " grpDescendants:" + descendants;
	    s = s + " grpUStatic:" + uStatic;
	    s = s + " grpStreamingQuality:" + streamingQuality;

	}
	return s;

    }



}
