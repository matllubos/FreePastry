/*
 * Created on May 4, 2005
 */
package rice.p2p.saar.singletree;

import rice.p2p.saar.*;
//import rice.replay.*;
import rice.p2p.commonapi.Id;
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
public class SingletreeContent extends SaarContent {

    public static int MAXCHILDDEGREE = SaarClient.DEGREECAP;

    // These are 4 modes in which SaarContent will be used (mode '1' and '2' are defined in SaarContent and will be used for upward/downward propagation of metadata
    public static int ANYCASTNEIGHBOR = 3; // when using anycast for tree repair
    public static int ANYCASTFORTREEIMPROVEMENT = 4; // when using anycast for tree structure (e.g depth) improvement
    public static int ANYCASTFORPREMPTDEGREEPUSHDOWN = 5; // when using anycast for tree structure (e.g depth) improvement
    public static int ANYCASTFORPRMNEIGHBOR = 6; // when using anycast to establish a PRM neighbor (random neighbr in mesh)


    /*****  AGGREGATION/DOWNWARDPROPAGATE variables **/
    public int sourceBroadcastSeq;   // this is the current value of the sequence number that is being broadcasted by the source
    public boolean aggregateFlag = false; // the metadata 
    public int descendants; // The usual meaning of Scribe tree descendants
    public TemporalBufferMap bmap = null;
    public int totalSlots; 
    public int usedSlots; 
    public int streamingQuality = -1;     // This is the streaming loss as estimated over the long term bitmap
    public boolean isConnected = false; // This is true/false depending on whether the local node received a pkt in the last 2 second. Note that accepting a anycast request is based on this value and NOT the steady state streaming quality
    public int treeDepth = -1;
    public int minAvailableDepth; // This is the minimum depth of a node that has available bandwidth and has a valid path to root since otherwise the getTreeDepth() function returns a MAX value, this value is set to 'MAXVAL' is the node has no available bandwidth
    public Id[] pathToRoot; // this variable will not be aggregated
    
    public int[] availableSlotsAtDepth ; // This will give me fine granularity information on the number of available slots at each level. The reason for keeping this information is that this can help stop the periodic anycasts for tree improvement in the steady state where all nodes higher up are saturated 
    
    public int[] depthAtNodeHavingChildOfDegree; // will be used for prempt degree pushdown

    public static final int MAXSATURATEDDEPTH = 10; // we keep numAvailableSlots information for this many levels Changed from 50 to 10 on Sep 29  


    /***** ANYCAST variables ***/
    public int responderDepth = -1;  // When we send an ack the anycast requestor can check the depth of the responder, this is helpful when the requestor decided to accept the connection that lowers his depth more

    public int requestorsDegree; // this information will be used in the Prempt-degree pushdown
    
  
  
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
//	    buffer.appendShort(bindIndex);
//	    buffer.appendShort(jvmIndex);
//	    buffer.appendShort(vIndex);
//
//	}
//
//	public NodeIndex(ReplayBuffer buffer, PastryNode pn) {
//	    bindIndex = buffer.getShort();
//	    jvmIndex = buffer.getShort();
//	    vIndex = buffer.getShort();
//	}


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
    public SingletreeContent(SingletreeContent o) {
	super(o);

	this.sourceBroadcastSeq = o.sourceBroadcastSeq;   
	this.aggregateFlag = o.aggregateFlag; 
	this.descendants = o.descendants; 
	if(o.bmap == null) {
	    this.bmap = null;
	} else {
	    this.bmap = new TemporalBufferMap(o.bmap);
	}
	this.totalSlots = o.totalSlots;
	this.usedSlots = o.usedSlots;
	this.streamingQuality = o.streamingQuality; 
	this.isConnected = o.isConnected;
	this.treeDepth = o.treeDepth;
	this.minAvailableDepth = o.minAvailableDepth;

	if(o.pathToRoot == null) {
	    this.pathToRoot = null;
	} else {
	    this.pathToRoot = new Id[o.pathToRoot.length];
	    for(int i=0; i<this.pathToRoot.length; i++) {
		this.pathToRoot[i] = o.pathToRoot[i];
	    }
	}

	if(o.availableSlotsAtDepth == null) {
	    this.availableSlotsAtDepth = null;
	} else {
	    this.availableSlotsAtDepth = new int[MAXSATURATEDDEPTH];
	    for(int i=0; i< MAXSATURATEDDEPTH; i++) {
		this.availableSlotsAtDepth[i] = o.availableSlotsAtDepth[i];
	    }
	}


	if(o.depthAtNodeHavingChildOfDegree == null) {
	    this.depthAtNodeHavingChildOfDegree = null;
	} else {
	    this.depthAtNodeHavingChildOfDegree = new int[MAXCHILDDEGREE + 1];
	    for(int i=0; i< (MAXCHILDDEGREE + 1); i++) {
		this.depthAtNodeHavingChildOfDegree[i] = o.depthAtNodeHavingChildOfDegree[i];
	    }
	}
	

	    
	this.responderDepth = o.responderDepth;
	this.requestorsDegree = o.requestorsDegree;
	
    }


    // Constructor used in ANYCASTFORPRMNEIGHBOR
    //public SingletreeContent(int mode, String topicName, int tNumber) {
    //super(mode,topicName,tNumber);
    //if(this.mode != ANYCASTFORPRMNEIGHBOR) {
    //    System.out.println("ERROR: Constructor(mode=SingletreeContent.ANYCASTFORPRMNEIGHBOR) does not have right parameters");
    //    System.exit(1);
    //}
    //}


    // Constructor used in ANYCASTNEIGHBOR/ANYCASTFORTREEIMPROVEMENT
    public SingletreeContent(int mode, String topicName, int tNumber, rice.p2p.commonapi.Id[] pathToRoot, int treeDepth, int requestorsDegree) {
	super(mode,topicName,tNumber);
	if((this.mode != ANYCASTNEIGHBOR) && (this.mode !=ANYCASTFORTREEIMPROVEMENT) && (this.mode != ANYCASTFORPREMPTDEGREEPUSHDOWN) && (this.mode !=ANYCASTFORPRMNEIGHBOR) ) {
	    System.out.println("ERROR: Constructor(mode=SingletreeContent.ANYCASTNEIGHBOR/ANYCASTFORTREEIMPROVEMENT/ANYCASTFORPREMPTDEGREEPUSHDOWN) does not have right parameters");
	    System.exit(1);
	}
	if(pathToRoot == null) {
	    this.pathToRoot = null;
	} else {
	    this.pathToRoot = new Id[pathToRoot.length];
	    for(int i=0; i<this.pathToRoot.length; i++) {
		this.pathToRoot[i] = pathToRoot[i];
	    }
	}

	this.treeDepth = treeDepth;
	this.requestorsDegree = requestorsDegree;
	
    }






    // Constructor used in UPWARDAGGREGATION
    public SingletreeContent(int mode, String topicName, int tNumber, boolean aggregateFlag, int descendants, TemporalBufferMap bmap, int usedSlots, int totalSlots, int streamingQuality, boolean isConnected, int treeDepth, int minAvailableDepth, int sourceBroadcastSeq, rice.p2p.commonapi.Id[] pathToRoot, int[] availableSlotsAtDepth, int[] depthAtNodeHavingChildOfDegree) {
	super(mode,topicName,tNumber);

	if(this.mode != SaarContent.UPWARDAGGREGATION) {
	    System.out.println("ERROR: Constructor(mode=SingletreeContent.UPWARDAGGREGATION) does not have right parameters");
	    System.exit(1);
	}
	this.sourceBroadcastSeq = sourceBroadcastSeq;
	this.aggregateFlag = aggregateFlag;
	this.descendants = descendants;
	if (bmap == null) {
	    this.bmap = null;
	} else {
	    this.bmap = new TemporalBufferMap(bmap);
	}
	this.usedSlots = usedSlots;
	this.totalSlots = totalSlots;
	this.streamingQuality = streamingQuality;
	this.isConnected = isConnected;
	this.treeDepth = treeDepth;
	this.minAvailableDepth = minAvailableDepth;

	if(pathToRoot == null) {
	    this.pathToRoot = null;
	} else {
	    this.pathToRoot = new Id[pathToRoot.length];
	    for(int i=0; i<this.pathToRoot.length; i++) {
		this.pathToRoot[i] = pathToRoot[i];
	    }
	}

	if(availableSlotsAtDepth == null) {
	    this.availableSlotsAtDepth = null;
	} else {
	    this.availableSlotsAtDepth = new int[MAXSATURATEDDEPTH];
	    for(int i=0; i< MAXSATURATEDDEPTH; i++) {
		this.availableSlotsAtDepth[i] = availableSlotsAtDepth[i];
	    }
	}

	if(depthAtNodeHavingChildOfDegree == null) {
	    this.depthAtNodeHavingChildOfDegree = null;
	} else {
	    this.depthAtNodeHavingChildOfDegree = new int[MAXCHILDDEGREE + 1];
	    for(int i=0; i< (MAXCHILDDEGREE + 1); i++) {
		this.depthAtNodeHavingChildOfDegree[i] = depthAtNodeHavingChildOfDegree[i];
	    }
	}
	
	

    }




//    public void dump(ReplayBuffer buffer, PastryNode pn) {
//
//
//
//    }



    public SaarContent aggregate(SaarContent otherContent) {
	if(this.mode != SaarContent.UPWARDAGGREGATION) {
	    System.out.println("ERROR: aggregate should only be used with mode=BlockbasedContent.UPWARDAGGREGATION");
	    System.exit(1);
	}
	SingletreeContent retContent = null;
	if(otherContent == null) {
	    retContent = new SingletreeContent(this);
	    return retContent;
	}
	

	SingletreeContent o = (SingletreeContent)otherContent;
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

	int mySourceBroadcastSeq; // consider the greater of the two
	if(this.sourceBroadcastSeq > o.sourceBroadcastSeq) {
	    mySourceBroadcastSeq = this.sourceBroadcastSeq;
	} else {
	    mySourceBroadcastSeq = o.sourceBroadcastSeq;
	}
	// Aggregateflag will be set as true
	int mydescendants = this.descendants + o.descendants;
	// the BMAP will be aggregated using the OR operator
	TemporalBufferMap mybmap = TemporalBufferMap.aggregate(this.bmap, o.bmap);

	int myusedSlots = this.usedSlots + o.usedSlots;
	int mytotalSlots = this.totalSlots + o.totalSlots;
	int mystreamingQuality = ((this.descendants * this.streamingQuality) + (o.descendants * o.streamingQuality))/ mydescendants;
	//boolean myisConnected = true; // since this variable is not aggregated
	boolean myisConnected; // aggregated using OR
	if(this.isConnected || o.isConnected) {
	    myisConnected = true;
	} else {
	    myisConnected = false;
	}


	int mytreeDepth = ((this.descendants * this.treeDepth) + (o.descendants * o.treeDepth))/ mydescendants;
	int myminAvailableDepth;
	if(this.minAvailableDepth < o.minAvailableDepth) {
	    myminAvailableDepth = this.minAvailableDepth;
	} else {
	    myminAvailableDepth = o.minAvailableDepth;
	}
	Id[] mypathToRoot = new Id[0];

	int[] myavailableSlotsAtDepth = new int[MAXSATURATEDDEPTH];
	for(int i=0; i< MAXSATURATEDDEPTH; i++) {
	    myavailableSlotsAtDepth[i] = this.availableSlotsAtDepth[i] + o.availableSlotsAtDepth[i];
	}

	int[] mydepthAtNodeHavingChildOfDegree = new int[MAXCHILDDEGREE+1];
	for(int i =0; i < (MAXCHILDDEGREE + 1); i ++) {
	    if(this.depthAtNodeHavingChildOfDegree[i] < o.depthAtNodeHavingChildOfDegree[i]) {
		mydepthAtNodeHavingChildOfDegree[i] = this.depthAtNodeHavingChildOfDegree[i];
	    } else {
		mydepthAtNodeHavingChildOfDegree[i] = o.depthAtNodeHavingChildOfDegree[i];
	    }
	    
	}



	retContent = new SingletreeContent(SaarContent.UPWARDAGGREGATION, myTopicName, myTNumber,  true, mydescendants, mybmap,  myusedSlots, mytotalSlots, mystreamingQuality, myisConnected, mytreeDepth, myminAvailableDepth, mySourceBroadcastSeq, mypathToRoot, myavailableSlotsAtDepth, mydepthAtNodeHavingChildOfDegree);

	return retContent;

    }


    // Creates a new copy
    public SaarContent duplicate() {
	return new SingletreeContent(this);

    }



    
    // Given the anycast requestor's content, the anycast traversal logic requires the dataplane to implement if the content satisfies the boolean predicate 
    public boolean predicateSatisfied(SaarContent anycastrequestorContent) {
	SingletreeContent mycontent = (SingletreeContent) anycastrequestorContent;
	boolean toReturn = false;
	if(aggregateFlag) {
	    // We will not use streamingQuality since in a single-tree an huge fraction of nodes could demonstrate bad quality
	    //if(mycontent.anycastneighbormode == MultitreeClient.ONLYPRIMARY) {
	    // We will also not use pathToRoot information because this varibale is undefined
	    if(mycontent.mode == ANYCASTFORPRMNEIGHBOR) {
		toReturn = true;
	    } else if((mycontent.mode != ANYCASTFORPREMPTDEGREEPUSHDOWN) && ((totalSlots - usedSlots) == 0) || (!isConnected)) {
		toReturn = false;
	    } else if((mycontent.mode == ANYCASTFORTREEIMPROVEMENT) && ((minAvailableDepth + 1) >= mycontent.treeDepth)   ){
		toReturn = false;
	    } else if(mycontent.mode == ANYCASTFORPREMPTDEGREEPUSHDOWN) {
		//if((mycontent.requestorsDegree > 1) && (((depthAtNodeHavingChildOfDegree[0] + 1) < mycontent.treeDepth) || ((depthAtNodeHavingChildOfDegree[1] + 1) < mycontent.treeDepth))) {
		if( ((mycontent.requestorsDegree == 1) && ((depthAtNodeHavingChildOfDegree[0] + 1) < mycontent.treeDepth)) || ((mycontent.requestorsDegree > 1) && (((depthAtNodeHavingChildOfDegree[0] + 1) < mycontent.treeDepth) || ((depthAtNodeHavingChildOfDegree[1] + 1) < mycontent.treeDepth)))  ) { // Updated on Jan15-2008
		    toReturn = true;
		} else {
		    toReturn = false;
		}

	    } else {
		toReturn = true;
	    }
	    
	} else {
	    if(anycastrequestorContent.anycastRequestor.equals(aggregator)) {
		toReturn = false; // The anycast requestor can be left out if the aggregateflag is false
	    } else if(mycontent.mode == ANYCASTFORPRMNEIGHBOR) {
		toReturn = true;

	    } else if((mycontent.mode != ANYCASTFORPREMPTDEGREEPUSHDOWN) && (totalSlots - usedSlots) == 0) {
		toReturn = false;
	    } else if(!isConnected) { // Note that we do not use average streaming quality, e use instantaneous information
		toReturn = false;
	    } else if(hasLoops((rice.pastry.Id)anycastrequestorContent.anycastRequestor.getId())) {
		toReturn = false;
	    } else if((mycontent.mode == ANYCASTFORTREEIMPROVEMENT)   && ((treeDepth + 1) >= mycontent.treeDepth)){ // We add 1 because the expected depth of the requestor will become responderDepth + 1
		toReturn = false;
	    } else if(mycontent.mode == ANYCASTFORPREMPTDEGREEPUSHDOWN) {
		//if((mycontent.requestorsDegree > 1) && (((depthAtNodeHavingChildOfDegree[0] + 1) < mycontent.treeDepth) || ((depthAtNodeHavingChildOfDegree[1] + 1) < mycontent.treeDepth))) { Updated on Jan15-2008
		if( ((mycontent.requestorsDegree == 1) && ((depthAtNodeHavingChildOfDegree[0] + 1) < mycontent.treeDepth)) || ((mycontent.requestorsDegree > 1) && (((depthAtNodeHavingChildOfDegree[0] + 1) < mycontent.treeDepth) || ((depthAtNodeHavingChildOfDegree[1] + 1) < mycontent.treeDepth)))  ) { // Updated on Jan15-2008
		    toReturn = true;
		} else {
		    toReturn = false;
		}

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
	SingletreeContent o = (SingletreeContent)otherContent;
	if((this.sourceBroadcastSeq != o.sourceBroadcastSeq) || (this.aggregateFlag != o.aggregateFlag) || (this.descendants != o.descendants) || (this.totalSlots != o.totalSlots) || (this.usedSlots != o.usedSlots) || (this.isConnected != o.isConnected) || (this.treeDepth != o.treeDepth) || (this.minAvailableDepth != o.minAvailableDepth) || (Math.abs(this.streamingQuality - o.streamingQuality) > 5)) {
	    return false;
	} else {
	    return true;
	}
    } 


    // it is similar to neglibigleChangeUpwardUpdate but we relax the sourceBroadcastSeq tracking a little 
    public boolean negligibleChangeDownwardPropagate(SaarContent otherContent) {
	if(otherContent == null) {
	    return false;
	}
	SingletreeContent o = (SingletreeContent)otherContent;
	if((this.aggregateFlag != o.aggregateFlag) || (this.descendants != o.descendants) || (this.totalSlots != o.totalSlots) || (this.usedSlots != o.usedSlots) || (this.isConnected != o.isConnected) || (this.treeDepth != o.treeDepth) || (this.minAvailableDepth != o.minAvailableDepth) || (Math.abs(this.streamingQuality - o.streamingQuality) > 5) ) {
	    return false;
	} else if(Math.abs(this.sourceBroadcastSeq - o.sourceBroadcastSeq) > 10) {
	    return false;
	} else {
	    return true;
	}
    }

    
    public boolean hasLoops(Id requestorId) {
	for(int i=0; i< pathToRoot.length; i++) {
	    if(pathToRoot[i].equals(requestorId)) {
		return true;
	    }
	}
	return false;
    }

    // return the height-common-ancestor, so higher the value the paths are more disjoint. This uses the ' public Id[] pathToRoot' information in SingletreeContent; The path information is in the reverse order that is 0 is ideally the multicast source and the last node is ideally the local node itself.
    // In the computation of hca there is a spceial condition, if the local nodes are in the other path, there is a very 	 
    public int computehca(SingletreeContent c1, SingletreeContent c2) {
	int hca = 0; // this is the height of the common ancestor 
	Id commonancestor = null; 
	boolean foundhca = false;

	Id[] patharr1, patharr2;  // In this case these arrays should be filled in the reverse order as per the hca computation algorithm below
	if(c1.pathToRoot== null) {
	    patharr1 = null;
	} else {
	    patharr1 = new Id[c1.pathToRoot.length];
	    for(int i=0; i<patharr1.length; i++) {
		patharr1[i] = c1.pathToRoot[c1.pathToRoot.length - i - 1];
	    }

	}
	if(c2.pathToRoot== null) {
	    patharr2 = null;
	} else {
	    patharr2 = new Id[c2.pathToRoot.length];
	    for(int i=0; i<patharr2.length; i++) {
		patharr2[i] = c2.pathToRoot[c2.pathToRoot.length - i - 1];
	    }

	}




	if((patharr1 == null) || (patharr2 == null)) {
	    return 0;
	}


	// If any of the endpoints are in the other guys path, then one path is a subset is completeley contined in the other other, in this case we return hca=0 since there is a high correlation of lossesa
       	for(int i=0; i< patharr1.length; i++) {
	    if(patharr1[i].equals(patharr2[0])) {
		return 0;
	    }
	}
 	for(int i=0; i< patharr2.length; i++) {
	    if(patharr2[i].equals(patharr1[0])) {
		return 0;
	    }
	}


	// 
	for(int i=0; i< patharr1.length; i++) {
	    for(int j=0; j< patharr2.length; j++) {
		if(patharr1[i].equals(patharr2[j])) {
		    foundhca = true;
		    commonancestor = patharr1[i];
		    if(i>j) {
			hca = i;
		    } else {
			hca = j;
		    }
		    break;
		}
	    }
	    if(foundhca) {
		break;
	    }
	}
	// We will print the computation to make sure 
	String path1 = printpath(patharr1);
	String path2 = printpath(patharr2);
	//System.out.println("path1: " + path1 + " , " + "path2: " + path2 + " , commonancestor: " + commonancestor + " , hca: " + hca);
	return hca;

    }

    public String printpath(Id[] path) {
	String s = "";
	for(int i=0; i< path.length; i++) {
	    s = s + path[i] + ",";
	}
	return s;
    }

    // Given the anycast requestor's content, the anycast traversal logic requires the dataplane to implement an ordering function.
    // Returns  0 : both are equal (the underlying SaarPolicy takes care of randomizing equal entries)
    //         -1 : this ('<' or 'isInferiorTo') otherContent
    //         +1 : this ('>' or 'isSuperiorTo') otherContent 
    // Also note that you may want to prioritize non-agregate metadata over aggregate metadata so that the anycast traversal completes quicker

    public int compare(SaarContent anycastrequestorContent, SaarContent otherContent) {
	// Given ofcourse that they have satisfied the boolean predicate, we bias towards leafs (i.e with aggregate flag NOT set. Amongst the leaves, we bias towards the ones with low treeDepth

	SingletreeContent mycontent = (SingletreeContent) anycastrequestorContent;
	SingletreeContent o = (SingletreeContent)otherContent;

	if(mycontent.mode == ANYCASTFORPRMNEIGHBOR) {
	    // For prm neighbor requests
	    if((aggregateFlag == false) && (o.aggregateFlag == true)) {
		return 0;
	    } else if((aggregateFlag == true) && (o.aggregateFlag == false)) {
		return 0;
	    } else if((aggregateFlag == false) && (o.aggregateFlag == false)) {
		// for prm requests, we choose the one which has higher hca 
		int hca_this, hca_other; // height of common ancestor (the basic idea is that the higher this value the lower the loss corrrelation)
		hca_this = computehca((SingletreeContent)anycastrequestorContent, (SingletreeContent)this);
		hca_other = computehca((SingletreeContent)anycastrequestorContent, (SingletreeContent)otherContent);
		if(hca_this > hca_other) {
		    return -1;
		} else if(hca_this < hca_other) {
		    return +1;
		} else {
		    return 0;
		}
		   
	    } else {
		//when both are aggregate
		return 0;
	    }

	  
	} else {
	    // For single-tree requests
	    if((aggregateFlag == false) && (o.aggregateFlag == true)) {
		return +1;
	    } else if((aggregateFlag == true) && (o.aggregateFlag == false)) {
		return -1;
	    } else if((aggregateFlag == false) && (o.aggregateFlag == false)) {
		// We choose the one with lower depth
		if(treeDepth < o.treeDepth) {
		    return +1;
		} else if(treeDepth > o.treeDepth) {
		    return -1;
		} else {
		    // We temporarily threat these as equal but the lower layer might eventually use gnpdistance(requestor,prospective) to break the tie
		    return 0;
		}
	    } else {
		// When both are aggregate, we previosuly treat them as equal (Note that the underlying SaarImpl does the randomization). We now bias based on minAvailableDepth
		if(minAvailableDepth < o.minAvailableDepth) {
		    return +1;
		} else if(minAvailableDepth > o.minAvailableDepth) {
		    return -1;
		} else {
		    // We temporarily threat these as equal but the lower layer might eventually use gnpdistance(requestor,prospective) to break the tie
		    return 0;
		}
	    }
	}
	
    }


    public void setResponderDepth(int val) {
	responderDepth = val;
    }


    public String getSlotsAtDepthString() {
	String slotsAtDepthString = "[";
	for(int i=0; i< MAXSATURATEDDEPTH; i++) {
	    slotsAtDepthString = slotsAtDepthString + availableSlotsAtDepth[i] + ","; 
	}
	slotsAtDepthString = slotsAtDepthString + "]";
	return slotsAtDepthString;
    }


    public String getDepthAtNodeHavingChildOfDegreeString() {
	String depthAtNodeHavingChildOfDegreeString = "[";
	for(int i=0; i< (MAXCHILDDEGREE+1); i++) {
	    depthAtNodeHavingChildOfDegreeString = depthAtNodeHavingChildOfDegreeString + depthAtNodeHavingChildOfDegree[i] + ","; 
	}
	depthAtNodeHavingChildOfDegreeString = depthAtNodeHavingChildOfDegreeString + "]";
	return depthAtNodeHavingChildOfDegreeString;
    }


    public int getSizeInBytes() {
	int val = getbasecostofsaarcontentInBytes();
	
	int sizePathToRoot;
	if(pathToRoot == null) {
	    sizePathToRoot = 0;
	} else {
	    sizePathToRoot = pathToRoot.length;
	}
	if((this.mode == ANYCASTNEIGHBOR) || (this.mode == ANYCASTFORTREEIMPROVEMENT) || (this.mode == ANYCASTFORPREMPTDEGREEPUSHDOWN) || (this.mode == ANYCASTFORPRMNEIGHBOR) ) {
	    val = val + 2 + sizePathToRoot;
	} else if((mode == SaarContent.UPWARDAGGREGATION) || (mode == SaarContent.DOWNWARDPROPAGATE)) {
	    val = val + 9 + 2 + MAXSATURATEDDEPTH;  // we just use depthof node of child of degree atmost 2 to get the premption working, also note that pathToRoot is not aggregated
	}
	return val;
    }

    public String toString() {
	String s = "SingletreeContent:";
	s = s + " mode:" + mode;
	if((mode == SingletreeContent.ANYCASTNEIGHBOR) || (mode == SingletreeContent.ANYCASTFORTREEIMPROVEMENT) || (mode == SingletreeContent.ANYCASTFORPRMNEIGHBOR)) {
	    s = s + " anycastGlobalId:" + anycastGlobalId; 
	} else if(mode == SaarContent.UPWARDAGGREGATION) {
	    s = s + " aggregateFlag:" + aggregateFlag;
	    s = s + " sourceBroadcastSeq:" + sourceBroadcastSeq;
	    s = s + " descendants:" + descendants;
	    s = s + " bmap:" + bmap;
	    s = s + " usedSlots:" + usedSlots;
	    s = s + " totalSlots:" + totalSlots;
	    s = s + " streamingQuality:" + streamingQuality;
	    s = s + " treeDepth:" + treeDepth;
	    s = s + " minAvailableDepth:" + minAvailableDepth;

	} else if(mode == SaarContent.DOWNWARDPROPAGATE) {
	    s = s + " grpDescendants:" + descendants;
	    s = s + " grpUStatic(" + usedSlots + "/" + totalSlots + ")";
	    s = s + " grpStreamingQuality:" + streamingQuality;
	    s = s + " grpAvailableSlotsAtDepth:" + getSlotsAtDepthString();
 	    s = s + " grpDepthAtNodeHavingChildOfDegree:" + getDepthAtNodeHavingChildOfDegreeString();
	    s = s + " minAvailableDepth:" + minAvailableDepth;
	}
	return s;

    }



}
