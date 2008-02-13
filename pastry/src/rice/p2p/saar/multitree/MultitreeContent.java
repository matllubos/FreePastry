/*
 * Created on May 4, 2005
 */
package rice.p2p.saar.multitree;

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
public class MultitreeContent extends SaarContent {


    public static int MAXCHILDDEGREE = MultitreeClient.NUMSTRIPES*SaarClient.DEGREECAP;

    // These are 4 modes in which SaarContent will be used (mode '1' and '2' are defined in SaarContent and will be used for upward/downward propagation of metadata
    public static int ANYCASTNEIGHBOR = 3; // when using anycast for tree repair ,the anycastneighbor has further 3 submodes (onlyprimary/allowsecondary/acceptbydroping)
    public static int ANYCASTFORTREEIMPROVEMENT = 4; // when using anycast for tree structure (e.g depth) improvement. anycastneighbormode is onlyprimary
    public static int ANYCASTFORCONNECTIONIMPROVEMENT = 5; // when using anycast to remove the secondary connections in favor of primary connections, anycastneighbormode is onlyprimary
    public static int ANYCASTFORPREMPTDEGREEPUSHDOWN = 6; // when using anycast to remove the secondary connections in favor of primary connections, anycastneighbormode is onlyprimary



    

    /*****  AGGREGATION/DOWNWARDPROPAGATE variables **/
    public int sourceBroadcastSeq;   // this is the current value of the sequence number that is being broadcasted by the source
    public boolean aggregateFlag = false; // the metadata 
    public int descendants; // The usual meaning of Scribe tree descendants
   

    public int overallUsedSlots;  // this is the total slots used across all stripes
    public int overallTotalSlots; // this is the total slots across all stripes 

    public int[] usedSlots = new int[MultitreeClient.NUMSTRIPES];
    public int[] totalSlots = new int[MultitreeClient.NUMSTRIPES];
    public int[] numNonContributors = new int[MultitreeClient.NUMSTRIPES];
    public boolean[] allowNonContributors = new boolean[MultitreeClient.NUMSTRIPES];
    public int[] currMaximumSlots = new int[MultitreeClient.NUMSTRIPES]; // currMaximumSlots is less than totalSlots because some of the slots may be occupied by secondary children

    public int[] connectedStripeAvailableSlots = new int[MultitreeClient.NUMSTRIPES]; // These are the available slots advertised per node per sripe provided it is receiving the stripe. The purpose of these variables is that before issuing the anycast, the node can decided whether it needs to violate the primary-child constraint (i.e get a parent via the spare capacity in any stripe)
    public int[] connectedTotalAvailableSlots = new int[MultitreeClient.NUMSTRIPES]; // These are the total availale slots across all stripes, provided the node is receiving the stripe. The purpose of this is that we may have a condition of connectedStripeAvailableSlots[stripeId] as zero, in this case when an anycast is issues the anycast may fail if the connectedTotalAvailableSlots[stripeId] is zero, implying that even violating the primry-child condition will not work because the available slots on the nodes receiving this stripe are sealed. In this case we issue a special anycast in which the responder accepts the requestor and drops a child in a stripe that has non-zero connectedStripeAvailableSlots and preferably a child that is not carrying any children for the stripe from which it will be dropped


    public int[] cumulativeStreamingQuality = new int[MultitreeClient.NUMSTRIPES];     // This is the overall streaming quality over all tripes as estimated over the long term bitmap, given that we can lose certain number of stripes. overallStreamingQuality[NUMSTRIPES - 1] is always 100, since here it is fine even if we lose all NUMSTRIPES .. overallStreamingQuality[0] is the raw data performance assuming we cannot aford to lose any stripe, the most imporant is overallStreamingQuality[1], which is the quality assuming we can aford to lose one stripe
    
    public int[] streamingQuality = new int[MultitreeClient.NUMSTRIPES];


    public boolean[] isConnected = new boolean[MultitreeClient.NUMSTRIPES]; // This is true/false depending on whether the local node received a pkt in the last 2 second. Note that accepting a anycast request is based on this value and NOT the steady state streaming quality
   

    public int[] treeDepth = new int[MultitreeClient.NUMSTRIPES];
    public int[] minAvailableDepth = new int[MultitreeClient.NUMSTRIPES]; // This is the minimum depth of a node that has available bandwidth, this value is set to 'MAXVAL' is the node has no available bandwidth



   
    public Id[][] pathToRoot = new Id[MultitreeClient.NUMSTRIPES][]; // this variable will not be aggregated
    
    public int[][] availableSlotsAtDepth = new int[MultitreeClient.NUMSTRIPES][] ; // This will give me fine granularity information on the number of available slots at each level. The reason for keeping this information is that this can help stop the periodic anycasts for tree improvement in the steady state where all nodes higher up are saturated 


    public int[][] depthAtNodeHavingChildOfDegree = new int[MultitreeClient.NUMSTRIPES][]; // will be used for prempt degree pushdown

    public int totalSecondaryChildren = 0; // This is the total number of secondary children totalled over all nodes, it helps us check if the resource balancing across stripes is being beneficial by causing very few secondary children

    
    public static final int MAXSATURATEDDEPTH = 5; // Note that this value can be lower than that used in the singletree we keep numAvailableSlots information for this many levels changed from 50 to 5 on Sep29


    /***** ANYCAST variables ***/
    public int responderDepth = -1;  // When we send an ack the anycast requestor can check the depth of the responder, this is helpful when the requestor decided to accept the connection that lowers his depth more
    public int requestorsDesiredStripeId;
    public int requestorsPrimaryStripeId; // We need this so that the node can addiitionally prefer primary-contribuotrs in the tree to other non-contributors
    public int requestorsDegree; // this information will be used in the Prempt-degree pushdown
    public int requestorsTreeDepth;
    public Id[] requestorsPathToRoot;
    public int anycastneighbormode;
    public boolean parentEstablishedViaPrimaryChildViolation; // Note that even if the mode is set to ALLOWSECONDARY/ACCEPTBYDROPING, we might just be lucky due to a race condition to establish a primary child,so we check if the violation did actually happen or not


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
    public MultitreeContent(MultitreeContent o) {
	super(o);

	this.sourceBroadcastSeq = o.sourceBroadcastSeq;   
	this.aggregateFlag = o.aggregateFlag; 
	this.descendants = o.descendants; 

	this.overallUsedSlots = o.overallUsedSlots;
	this.overallTotalSlots = o.overallTotalSlots;

	for(int stripeId= 0; stripeId < MultitreeClient.NUMSTRIPES; stripeId ++) {
	    this.usedSlots[stripeId] = o.usedSlots[stripeId];
	    this.numNonContributors[stripeId] = o.numNonContributors[stripeId];
	    this.allowNonContributors[stripeId] = o.allowNonContributors[stripeId];
	    this.currMaximumSlots[stripeId] = o.currMaximumSlots[stripeId];
	    this.totalSlots[stripeId] = o.totalSlots[stripeId];
	    this.connectedStripeAvailableSlots[stripeId] = o.connectedStripeAvailableSlots[stripeId];
	    this.connectedTotalAvailableSlots[stripeId] = o.connectedTotalAvailableSlots[stripeId];
	    
	}

	for(int numRedundantStripes= 0; numRedundantStripes < MultitreeClient.NUMSTRIPES; numRedundantStripes ++) {
	    this.cumulativeStreamingQuality[numRedundantStripes] = o.cumulativeStreamingQuality[numRedundantStripes];
	}

 	for(int stripeId= 0; stripeId < MultitreeClient.NUMSTRIPES; stripeId ++) {
	    this.streamingQuality[stripeId] = o.streamingQuality[stripeId];
	    this.isConnected[stripeId] = o.isConnected[stripeId];
	    this.treeDepth[stripeId] = o.treeDepth[stripeId];
	    this.minAvailableDepth[stripeId] = o.minAvailableDepth[stripeId];

	}


	for(int stripeId =0; stripeId < MultitreeClient.NUMSTRIPES; stripeId++) {
	    if(o.pathToRoot[stripeId] == null) {
		this.pathToRoot[stripeId] = null;
	    } else {
		this.pathToRoot[stripeId] = new Id[o.pathToRoot[stripeId].length];
		for(int i=0; i<this.pathToRoot[stripeId].length; i++) {
		    this.pathToRoot[stripeId][i] = o.pathToRoot[stripeId][i];
		}
	    }
	    
	    if(o.availableSlotsAtDepth[stripeId] == null) {
		this.availableSlotsAtDepth[stripeId] = null;
	    } else {
		this.availableSlotsAtDepth[stripeId] = new int[MAXSATURATEDDEPTH];
		for(int i=0; i< MAXSATURATEDDEPTH; i++) {
		this.availableSlotsAtDepth[stripeId][i] = o.availableSlotsAtDepth[stripeId][i];
		}
	    }

   
	    if(o.depthAtNodeHavingChildOfDegree[stripeId] == null) {
		this.depthAtNodeHavingChildOfDegree[stripeId] = null;
	    } else {
		this.depthAtNodeHavingChildOfDegree[stripeId] = new int[MAXCHILDDEGREE + 1];
		for(int i=0; i< (MAXCHILDDEGREE + 1); i++) {
		    this.depthAtNodeHavingChildOfDegree[stripeId][i] = o.depthAtNodeHavingChildOfDegree[stripeId][i];
		}
	    }
	    

	}
	this.totalSecondaryChildren = o.totalSecondaryChildren;
	
	    
	this.responderDepth = o.responderDepth;
	this.requestorsDesiredStripeId = o.requestorsDesiredStripeId;
	this.requestorsPrimaryStripeId = o.requestorsPrimaryStripeId;
	this.requestorsDegree = o.requestorsDegree;
	this.requestorsTreeDepth = o.requestorsTreeDepth;
	if(o.requestorsPathToRoot == null) {
	    this.requestorsPathToRoot = null;
	} else {
	    this.requestorsPathToRoot = new Id[o.requestorsPathToRoot.length];
	    for(int i=0; i<this.requestorsPathToRoot.length; i++) {
		this.requestorsPathToRoot[i] = o.requestorsPathToRoot[i];
	    }
	}
	this.anycastneighbormode = o.anycastneighbormode;
	this.parentEstablishedViaPrimaryChildViolation = o.parentEstablishedViaPrimaryChildViolation;

    }



    // Constructor used in ANYCASTNEIGHBOR/ANYCASTFORTREEIMPROVEMENT
    public MultitreeContent(int mode, String topicName, int tNumber, rice.p2p.commonapi.Id[] requestorsPathToRoot, int requestorsTreeDepth, int requestorsDesiredStripeId, int anycastneighbormode, int requestorsPrimaryStripeId, int requestorsDegree) {
	super(mode,topicName,tNumber);
	if((this.mode != ANYCASTNEIGHBOR) && (this.mode !=ANYCASTFORTREEIMPROVEMENT) && (this.mode != ANYCASTFORCONNECTIONIMPROVEMENT) && (this.mode != ANYCASTFORPREMPTDEGREEPUSHDOWN)) {
	    System.out.println("ERROR: Constructor(mode=MultitreeContent.ANYCASTNEIGHBOR/ANYCASTFORTREEIMPROVEMENT/ANYCASTFORCONNECTIONIMPROVEMENT/ANYCASTFORPREMPTDEGREEPUSHDOWN) does not have right parameters");
	    System.exit(1);
	}

	if(requestorsPathToRoot == null) {
	    this.requestorsPathToRoot = null;
	} else {
	    this.requestorsPathToRoot = new Id[requestorsPathToRoot.length];
	    for(int i=0; i<this.requestorsPathToRoot.length; i++) {
		this.requestorsPathToRoot[i] = requestorsPathToRoot[i];
	    }
	}

	this.requestorsTreeDepth = requestorsTreeDepth;
	this.requestorsDesiredStripeId = requestorsDesiredStripeId;
	this.requestorsPrimaryStripeId = requestorsPrimaryStripeId;
	this.requestorsDegree = requestorsDegree;
	this.anycastneighbormode = anycastneighbormode;
	
    }






    // Constructor used in UPWARDAGGREGATION
    public MultitreeContent(int mode, String topicName, int tNumber, boolean aggregateFlag, int descendants, int overallUsedSlots, int overallTotalSlots, int[] usedSlots, int[] currMaximumSlots, int[] totalSlots, int[] connectedStripeAvailableSlots, int[] connectedTotalAvailableSlots, int[] cumulativeStreamingQuality, int[] streamingQuality, boolean[] isConnected, int[] treeDepth, int[] minAvailableDepth, int sourceBroadcastSeq, rice.p2p.commonapi.Id[][] pathToRoot, int[][] availableSlotsAtDepth, int totalSecondaryChildren, int[] numNonContributors, boolean[] allowNonContributors,int[][] depthAtNodeHavingChildOfDegree) {
	super(mode,topicName,tNumber);

	if(this.mode != SaarContent.UPWARDAGGREGATION) {
	    System.out.println("ERROR: Constructor(mode=SingletreeContent.UPWARDAGGREGATION) does not have right parameters");
	    System.exit(1);
	}
	this.sourceBroadcastSeq = sourceBroadcastSeq;
	this.aggregateFlag = aggregateFlag;
	this.descendants = descendants;
	this.overallUsedSlots = overallUsedSlots;
	this.overallTotalSlots = overallTotalSlots;
	for(int stripeId =0; stripeId < MultitreeClient.NUMSTRIPES; stripeId ++) {
	    this.usedSlots[stripeId] = usedSlots[stripeId];
	    this.numNonContributors[stripeId] = numNonContributors[stripeId];
	    this.allowNonContributors[stripeId] = allowNonContributors[stripeId];
	    this.currMaximumSlots[stripeId] = currMaximumSlots[stripeId];
	    this.totalSlots[stripeId] = totalSlots[stripeId];
	    this.connectedStripeAvailableSlots[stripeId] = connectedStripeAvailableSlots[stripeId];
	    this.connectedTotalAvailableSlots[stripeId] = connectedTotalAvailableSlots[stripeId];

	}


	for(int numRedundantStripes =0; numRedundantStripes < MultitreeClient.NUMSTRIPES; numRedundantStripes ++) {
	    this.cumulativeStreamingQuality[numRedundantStripes] = cumulativeStreamingQuality[numRedundantStripes];
	}

	for(int stripeId =0; stripeId < MultitreeClient.NUMSTRIPES; stripeId ++) {
	    this.streamingQuality[stripeId] = streamingQuality[stripeId];
	    this.isConnected[stripeId] = isConnected[stripeId];
	    this.treeDepth[stripeId] = treeDepth[stripeId];
	    this.minAvailableDepth[stripeId] = minAvailableDepth[stripeId];

	}

	for(int stripeId =0; stripeId < MultitreeClient.NUMSTRIPES; stripeId ++) {
	    if(pathToRoot[stripeId] == null) {
		this.pathToRoot[stripeId] = null;
	    } else {
		this.pathToRoot[stripeId] = new Id[pathToRoot[stripeId].length];
		for(int i=0; i<this.pathToRoot[stripeId].length; i++) {
		    this.pathToRoot[stripeId][i] = pathToRoot[stripeId][i];
		}
	    }


	    if(availableSlotsAtDepth[stripeId] == null) {
		this.availableSlotsAtDepth[stripeId] = null;
	    } else {
		this.availableSlotsAtDepth[stripeId] = new int[MAXSATURATEDDEPTH];
		for(int i=0; i< MAXSATURATEDDEPTH; i++) {
		    this.availableSlotsAtDepth[stripeId][i] = availableSlotsAtDepth[stripeId][i];
		}
	    }


	    if(depthAtNodeHavingChildOfDegree[stripeId] == null) {
		this.depthAtNodeHavingChildOfDegree[stripeId] = null;
	    } else {
		this.depthAtNodeHavingChildOfDegree[stripeId] = new int[MAXCHILDDEGREE + 1];
		for(int i=0; i< (MAXCHILDDEGREE + 1); i++) {
		    this.depthAtNodeHavingChildOfDegree[stripeId][i] = depthAtNodeHavingChildOfDegree[stripeId][i];
		}
	    }



	}

	this.totalSecondaryChildren = totalSecondaryChildren;
	

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
	MultitreeContent retContent = null;
	if(otherContent == null) {
	    retContent = new MultitreeContent(this);
	    return retContent;
	}
	

	MultitreeContent o = (MultitreeContent)otherContent;
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


	int myoverallUsedSlots = this.overallUsedSlots + o.overallUsedSlots;
	int myoverallTotalSlots = this.overallTotalSlots + o.overallTotalSlots;
	int[] myusedSlots = new int[MultitreeClient.NUMSTRIPES];
	int[] mynumNonContributors = new int[MultitreeClient.NUMSTRIPES];
	boolean[] myallowNonContributors = new boolean[MultitreeClient.NUMSTRIPES];
	int[] mycurrMaximumSlots = new int[MultitreeClient.NUMSTRIPES];
	int[] mytotalSlots = new int[MultitreeClient.NUMSTRIPES];
	int[] myconnectedStripeAvailableSlots = new int[MultitreeClient.NUMSTRIPES];
	int[] myconnectedTotalAvailableSlots = new int[MultitreeClient.NUMSTRIPES];


	for(int stripeId =0; stripeId < MultitreeClient.NUMSTRIPES; stripeId ++) {
	    myusedSlots[stripeId] = this.usedSlots[stripeId] + o.usedSlots[stripeId];
	    mynumNonContributors[stripeId] = this.numNonContributors[stripeId] + o.numNonContributors[stripeId];
	    if(this.allowNonContributors[stripeId] || o.allowNonContributors[stripeId]) { // OR aggregator
		myallowNonContributors[stripeId] = true;
	    } else {
		myallowNonContributors[stripeId] = false;
	    }
	    mycurrMaximumSlots[stripeId] = this.currMaximumSlots[stripeId] + o.currMaximumSlots[stripeId];
	    mytotalSlots[stripeId] = this.totalSlots[stripeId] + o.totalSlots[stripeId];
	    myconnectedStripeAvailableSlots[stripeId] = this.connectedStripeAvailableSlots[stripeId] + o.connectedStripeAvailableSlots[stripeId];
	    myconnectedTotalAvailableSlots[stripeId] = this.connectedTotalAvailableSlots[stripeId] + o.connectedTotalAvailableSlots[stripeId];

	}


	int[] mycumulativeStreamingQuality = new int[MultitreeClient.NUMSTRIPES];
	for(int numRedundantStripes =0; numRedundantStripes < MultitreeClient.NUMSTRIPES; numRedundantStripes ++) {
	    mycumulativeStreamingQuality[numRedundantStripes] =  ((this.descendants * this.cumulativeStreamingQuality[numRedundantStripes]) + (o.descendants * o.cumulativeStreamingQuality[numRedundantStripes]))/ mydescendants;
	}

	int[] mystreamingQuality = new int[MultitreeClient.NUMSTRIPES];
	boolean[] myisConnected = new boolean[MultitreeClient.NUMSTRIPES];
	int[] mytreeDepth = new int[MultitreeClient.NUMSTRIPES];
	int[] myminAvailableDepth = new int[MultitreeClient.NUMSTRIPES];


	for(int stripeId =0; stripeId < MultitreeClient.NUMSTRIPES; stripeId ++) {
	    mystreamingQuality[stripeId] = ((this.descendants * this.streamingQuality[stripeId]) + (o.descendants * o.streamingQuality[stripeId]))/ mydescendants;
	    if(this.isConnected[stripeId] || o.isConnected[stripeId]) { // aggregated using OR
		myisConnected[stripeId] = true;
	    } else {
		myisConnected[stripeId] = false;
	    }


	    mytreeDepth[stripeId] = ((this.descendants * this.treeDepth[stripeId]) + (o.descendants * o.treeDepth[stripeId]))/ mydescendants;
	    if(this.minAvailableDepth[stripeId] < o.minAvailableDepth[stripeId]) {
		myminAvailableDepth[stripeId] = this.minAvailableDepth[stripeId];
	    } else {
		myminAvailableDepth[stripeId] = o.minAvailableDepth[stripeId];
	    }

	}


	Id[][] mypathToRoot = new Id[MultitreeClient.NUMSTRIPES][];
	for(int stripeId=0; stripeId < MultitreeClient.NUMSTRIPES; stripeId++) {
	    mypathToRoot[stripeId] = new Id[0]; // this is not aggregated
	}

	int[][] myavailableSlotsAtDepth = new int[MultitreeClient.NUMSTRIPES][MAXSATURATEDDEPTH];
	for(int stripeId=0; stripeId < MultitreeClient.NUMSTRIPES; stripeId ++) {
	    for(int i=0; i< MAXSATURATEDDEPTH; i++) {
		myavailableSlotsAtDepth[stripeId][i] = this.availableSlotsAtDepth[stripeId][i] + o.availableSlotsAtDepth[stripeId][i];
	    }
	}


	int[][] mydepthAtNodeHavingChildOfDegree = new int[MultitreeClient.NUMSTRIPES][MAXCHILDDEGREE+1];
	for(int stripeId=0; stripeId < MultitreeClient.NUMSTRIPES; stripeId ++) {
	    for(int i =0; i < (MAXCHILDDEGREE + 1); i ++) {
		if(this.depthAtNodeHavingChildOfDegree[stripeId][i] < o.depthAtNodeHavingChildOfDegree[stripeId][i]) {
		    mydepthAtNodeHavingChildOfDegree[stripeId][i] = this.depthAtNodeHavingChildOfDegree[stripeId][i];
		} else {
		    mydepthAtNodeHavingChildOfDegree[stripeId][i] = o.depthAtNodeHavingChildOfDegree[stripeId][i];
		}
	    }
	}





	int mytotalSecondaryChildren = this.totalSecondaryChildren + o.totalSecondaryChildren;


	retContent = new MultitreeContent(SaarContent.UPWARDAGGREGATION, myTopicName, myTNumber,  true, mydescendants, myoverallUsedSlots, myoverallTotalSlots, myusedSlots, mycurrMaximumSlots, mytotalSlots, myconnectedStripeAvailableSlots, myconnectedTotalAvailableSlots, mycumulativeStreamingQuality, mystreamingQuality, myisConnected, mytreeDepth, myminAvailableDepth, mySourceBroadcastSeq, mypathToRoot, myavailableSlotsAtDepth, mytotalSecondaryChildren, mynumNonContributors, myallowNonContributors, mydepthAtNodeHavingChildOfDegree);

	return retContent;

    }


    // Creates a new copy
    public SaarContent duplicate() {
	return new MultitreeContent(this);

    }



    
    // Given the anycast requestor's content, the anycast traversal logic requires the dataplane to implement if the content satisfies the boolean predicate 
    public boolean predicateSatisfied(SaarContent anycastrequestorContent) {
        MultitreeContent mycontent = (MultitreeContent) anycastrequestorContent;
	int stripeId = mycontent.requestorsDesiredStripeId;
	boolean toReturn = false;
	if(aggregateFlag) {
	    // We will not use streamingQuality since in a single-tree an huge fraction of nodes could demonstrate bad quality

	    if(mycontent.anycastneighbormode == MultitreeClient.ONLYPRIMARY) {
		// We will also not use pathToRoot information because this varibale is undefined
		if(((currMaximumSlots[stripeId] - usedSlots[stripeId]) == 0) || (!isConnected[stripeId])) {
		    toReturn = false;
		} else if((mycontent.mode == ANYCASTFORTREEIMPROVEMENT) && ((minAvailableDepth[stripeId] + 1) >= mycontent.requestorsTreeDepth) ) {
		    toReturn = false;
		} else {
		    toReturn = true;
		}
	    } else if(mycontent.anycastneighbormode == MultitreeClient.ALLOWSECONDARY){
		// We will base it on the overall used/total slots
		if(((overallTotalSlots - overallUsedSlots) > 0) && isConnected[stripeId]) {
		    toReturn = true;
		} else {
		    toReturn = false;
		}

	    } else if(mycontent.anycastneighbormode == MultitreeClient.ONLYPRIMARY_LIMITNONCONTRIBUTORS) {
		if(!allowNonContributors[stripeId]) { // this takes into account slots availability and fact of being connected (i.e getting streaming content)
		    toReturn = false;
		} else if((mycontent.mode == ANYCASTFORTREEIMPROVEMENT) && ((minAvailableDepth[stripeId] + 1) >= mycontent.requestorsTreeDepth) ) {
		    System.out.println("ERROR: ANYCASTFORTREEIMPROVEMENT is issued only with mode ONLYPRIMARY");
		    System.exit(1);
		} else {
		    toReturn = true;
		}


	    } else if((mycontent.mode == ANYCASTFORPREMPTDEGREEPUSHDOWN) && (mycontent.anycastneighbormode == MultitreeClient.ACCEPTBYDROPING)) {

		//System.out.println("predicateSatisfiedCheckOnAggregate(" + this + ")");
		
		// We have assumed here that the requestors degree is greater than 0, thus we check only against the depthAtNodeHavingOfDegree[stripeId][0]. In general we should check against requestors_degree and existence of some 'd' s.t depthAtNodeHavingChildOfDegree[stripeId][d] 
		if(mycontent.requestorsTreeDepth <= (depthAtNodeHavingChildOfDegree[stripeId][0] + 1)) {
		    //System.out.println("depthcheckfailed, requestorsTreeDepth:" + mycontent.requestorsTreeDepth);
		    toReturn = false;
		} else if(totalSlots[stripeId] == 0) {
		    //System.out.println("totalslotscheckfailed");
		    toReturn = false;

		} else {
		    //System.out.println("checkspassed");
		    toReturn = true;

		}
		

	    } else {
		toReturn = true;
	    }

	} else {
	    if(anycastrequestorContent.anycastRequestor.equals(aggregator)) {
		toReturn = false; // The anycast requestor can be left out if the aggregateflag is false
	    } else if(mycontent.anycastneighbormode == MultitreeClient.ONLYPRIMARY) {
		if((currMaximumSlots[stripeId] - usedSlots[stripeId]) == 0) {
		    toReturn = false;
		} else if(!isConnected[stripeId]) { // Note that we do not use average streaming quality, e use instantaneous information
		    toReturn = false;
		} else if(hasLoops((rice.pastry.Id)anycastrequestorContent.anycastRequestor.getId(), stripeId)) {
		    toReturn = false;
		} else if((mycontent.mode == ANYCASTFORTREEIMPROVEMENT)   && ((treeDepth[stripeId] + 1) >= mycontent.requestorsTreeDepth)){ // We add 1 because the expected depth of the requestor will become responderDepth + 1
		    toReturn = false;
		} else  {
		    toReturn = true;
		}
	    } else if(mycontent.anycastneighbormode == MultitreeClient.ALLOWSECONDARY){
		if((overallTotalSlots - overallUsedSlots) == 0) {
		    toReturn = false;
		} else if(!isConnected[stripeId]) { // Note that we do not use average streaming quality, e use instantaneous information
		    toReturn = false;
		} else if(hasLoops((rice.pastry.Id)anycastrequestorContent.anycastRequestor.getId(), stripeId)) {
		    toReturn = false;
		} else {
		    // Note that we need not check for mode = TREEIMPROVEMENT since here the anycastneighbormode is effectively onlyprimary
		    toReturn = true;
		}

	    } else if(mycontent.anycastneighbormode == MultitreeClient.ONLYPRIMARY_LIMITNONCONTRIBUTORS ) {
		if(!allowNonContributors[stripeId]) { // this takes into account slots availability and fact of being connected (i.e getting streaming content)
		    toReturn = false;
		} else if(hasLoops((rice.pastry.Id)anycastrequestorContent.anycastRequestor.getId(), stripeId)) {
		    toReturn = false;
		} else if((mycontent.mode == ANYCASTFORTREEIMPROVEMENT)   && ((treeDepth[stripeId] + 1) >= mycontent.requestorsTreeDepth)){ // We add 1 because the expected depth of the requestor will become responderDepth + 1
		    System.out.println("ERROR: ANYCASTFORTREEIMPROVEMENT is issued only with mode ONLYPRIMARY");
		    System.exit(1);
		} else  {
		    toReturn = true;
		}



	    } else if((mycontent.mode == ANYCASTNEIGHBOR) && (mycontent.anycastneighbormode == MultitreeClient.ACCEPTBYDROPING)){
		// Because of lack of sufficient information, in this mode many of the actal decision will be done in recvAnycast with more knowledge
		if(!isConnected[stripeId]) {
		    toReturn = false;
		} else if(hasLoops((rice.pastry.Id)anycastrequestorContent.anycastRequestor.getId(), stripeId)) {
		    toReturn = false;
		} else if(overallTotalSlots == 0) {
		    toReturn = false;
		} else {
		    toReturn = true;
		}
		

	    } else if((mycontent.mode == ANYCASTFORPREMPTDEGREEPUSHDOWN) && (mycontent.anycastneighbormode == MultitreeClient.ACCEPTBYDROPING)) {
		
		//System.out.println("predicateSatisfiedCheckOnLeaf(" + this + ")");

		// We have assumed here that the requestors degree is greater than 0, thus we check only against the depthAtNodeHavingOfDegree[stripeId][0]. In general we should check against requestors_degree and existence of some 'd' s.t depthAtNodeHavingChildOfDegree[stripeId][d] 
		
		if(mycontent.requestorsTreeDepth <= (depthAtNodeHavingChildOfDegree[stripeId][0] + 1)) {
		    //System.out.println("depthcheckfailed, requestorsTreeDepth:" + mycontent.requestorsTreeDepth);
		    toReturn = false;
		} else if(totalSlots[stripeId] == 0) { // we consider attaching by prempting only n the primary stripe
		    //System.out.println("totalslotscheckfailed");
		    toReturn = false;

		} else if(!isConnected[stripeId]) {
		    //System.out.println("isconnectedcheckfailed");
		    toReturn = false;
		} else if(hasLoops((rice.pastry.Id)anycastrequestorContent.anycastRequestor.getId(), stripeId)) {
		    //System.out.println("loopcheckfailed");
		    toReturn = false;
		} else {
		    //System.out.println("allcheckspassed");
		    toReturn = true;
		}
	       

	    } else {
		System.out.println("ERROR: Unknown anycastneighbor mode: " + mycontent.anycastneighbormode);
		System.exit(1);
		
	    }

	}
	return toReturn;
	
    }
    


    public boolean negligibleChangeUpwardUpdate(SaarContent otherContent) {
	if(otherContent == null) {
	    return false;
	}
	MultitreeContent o = (MultitreeContent)otherContent;
	if((this.sourceBroadcastSeq != o.sourceBroadcastSeq) || (this.aggregateFlag != o.aggregateFlag) || (this.descendants != o.descendants) || (this.overallTotalSlots != o.overallTotalSlots) || (this.overallUsedSlots != o.overallUsedSlots) || (this.totalSecondaryChildren != o.totalSecondaryChildren)) {
	    return false;
	} 
	for(int i=0; i< MultitreeClient.NUMSTRIPES; i++) {
	    if (this.usedSlots[i] != o.usedSlots[i])
		return false;
	}

	for(int i=0; i< MultitreeClient.NUMSTRIPES; i++) {
	    if (this.totalSlots[i] != o.totalSlots[i])
		return false;
	}


	for(int i=0; i< MultitreeClient.NUMSTRIPES; i++) {
	    if (this.numNonContributors[i] != o.numNonContributors[i])
		return false;
	}


	for(int i=0; i< MultitreeClient.NUMSTRIPES; i++) {
	    if (this.allowNonContributors[i] != o.allowNonContributors[i])
		return false;
	}



	for(int i=0; i< MultitreeClient.NUMSTRIPES; i++) {
	    if (this.currMaximumSlots[i] != o.currMaximumSlots[i])
		return false;
	}

	for(int i=0; i< MultitreeClient.NUMSTRIPES; i++) {
	    if (this.connectedStripeAvailableSlots[i] != o.connectedStripeAvailableSlots[i])
		return false;
	}

	for(int i=0; i< MultitreeClient.NUMSTRIPES; i++) {
	    if (this.minAvailableDepth[i] != o.minAvailableDepth[i])
		return false;
	}

	for(int i=0; i< MultitreeClient.NUMSTRIPES; i++) {
	    if (this.isConnected[i] != o.isConnected[i])
		return false;
	}

	for(int i=0; i< MultitreeClient.NUMSTRIPES; i++) {
	    if (Math.abs(this.streamingQuality[i] - o.streamingQuality[i]) > 5)
		return false;
	}

	for(int i=0; i< MultitreeClient.NUMSTRIPES; i++) {
	    if (this.treeDepth[i] != o.treeDepth[i])
		return false;
	}


	for(int i=0; i< MultitreeClient.NUMSTRIPES; i++) {
	    for(int j=0; j < (MAXCHILDDEGREE + 1); j++) {
		if (this.depthAtNodeHavingChildOfDegree[i][j] != o.depthAtNodeHavingChildOfDegree[i][j]) {
		    return false;
		}
	    }
	}


	return true;

    }


    public boolean negligibleChangeDownwardPropagate(SaarContent otherContent) {
	if(otherContent == null) {
	    return false;
	}
	MultitreeContent o = (MultitreeContent)otherContent;
	if((this.aggregateFlag != o.aggregateFlag) || (this.descendants != o.descendants) || (this.overallTotalSlots != o.overallTotalSlots) || (this.overallUsedSlots != o.overallUsedSlots) || (this.totalSecondaryChildren != o.totalSecondaryChildren)) {
	    return false;
	} 
	if(Math.abs(this.sourceBroadcastSeq - o.sourceBroadcastSeq) > 0) {
	    return false;
	}
	for(int i=0; i< MultitreeClient.NUMSTRIPES; i++) {
	    if (this.usedSlots[i] != o.usedSlots[i])
		return false;
	}
	for(int i=0; i< MultitreeClient.NUMSTRIPES; i++) {
	    if (this.totalSlots[i] != o.totalSlots[i])
		return false;
	}


	for(int i=0; i< MultitreeClient.NUMSTRIPES; i++) {
	    if (this.numNonContributors[i] != o.numNonContributors[i])
		return false;
	}


	for(int i=0; i< MultitreeClient.NUMSTRIPES; i++) {
	    if (this.allowNonContributors[i] != o.allowNonContributors[i])
		return false;
	}




	for(int i=0; i< MultitreeClient.NUMSTRIPES; i++) {
	    if (this.currMaximumSlots[i] != o.currMaximumSlots[i])
		return false;
	}

	for(int i=0; i< MultitreeClient.NUMSTRIPES; i++) {
	    if (this.connectedStripeAvailableSlots[i] != o.connectedStripeAvailableSlots[i])
		return false;
	}

	for(int i=0; i< MultitreeClient.NUMSTRIPES; i++) {
	    if (this.minAvailableDepth[i] != o.minAvailableDepth[i])
		return false;
	}



	for(int i=0; i< MultitreeClient.NUMSTRIPES; i++) {
	    if (this.isConnected[i] != o.isConnected[i])
		return false;
	}

	for(int i=0; i< MultitreeClient.NUMSTRIPES; i++) {
	    if (Math.abs(this.streamingQuality[i] - o.streamingQuality[i]) > 5)
		return false;
	}

	for(int i=0; i< MultitreeClient.NUMSTRIPES; i++) {
	    if (this.treeDepth[i] != o.treeDepth[i])
		return false;
	}


	for(int i=0; i< MultitreeClient.NUMSTRIPES; i++) {
	    for(int j=0; j < (MAXCHILDDEGREE + 1); j++) {
		if (this.depthAtNodeHavingChildOfDegree[i][j] != o.depthAtNodeHavingChildOfDegree[i][j]) {
		    return false;
		}
	    }
	}


	return true;
    }

    
    public boolean hasLoops(Id requestorId, int stripeId) {
	for(int i=0; i< pathToRoot[stripeId].length; i++) {
	    if(pathToRoot[stripeId][i].equals(requestorId)) {
		return true;
	    }
	}
	return false;
    }


    // Given the anycast requestor's content, the anycast traversal logic requires the dataplane to implement an ordering function.
    // Returns  0 : both are equal (the underlying SaarPolicy takes care of randomizing equal entries)
    //         -1 : this ('<' or 'isInferiorTo') otherContent
    //         +1 : this ('>' or 'isSuperiorTo') otherContent 
    // Also note that you may want to prioritize non-agregate metadata over aggregate metadata so that the anycast traversal completes quicker

    public int compare(SaarContent anycastrequestorContent, SaarContent otherContent) {
	// Given ofcourse that they have satisfied the boolean predicate, we bias towards leafs (i.e with aggregate flag NOT set. Amongst the leaves, we bias towards the ones with low treeDepth
	MultitreeContent o = (MultitreeContent)otherContent;
        MultitreeContent mycontent = (MultitreeContent) anycastrequestorContent;
	int stripeId = mycontent.requestorsDesiredStripeId;
	
	if((aggregateFlag == false) && (o.aggregateFlag == true)) {
	    return +1;
	} else if((aggregateFlag == true) && (o.aggregateFlag == false)) {
	    return -1;
	} else if((aggregateFlag == false) && (o.aggregateFlag == false)) {
	    // Both are agg=false
	    if((mycontent.mode == MultitreeContent.ANYCASTNEIGHBOR) || (mycontent.mode == MultitreeContent.ANYCASTFORTREEIMPROVEMENT) || (mycontent.mode == MultitreeContent.ANYCASTFORCONNECTIONIMPROVEMENT)) {
		if(treeDepth[stripeId] < o.treeDepth[stripeId]) {
		    return +1;
		} else if(treeDepth[stripeId] > o.treeDepth[stripeId]) {
		    return -1;
		} else {
		    // We temporarily threat these as equal but the lower layer might eventually use gnpdistance(requestor,prospective) to break the tie
		    
		    return 0;
		}
	    } else if((mycontent.mode == MultitreeContent.ANYCASTFORPREMPTDEGREEPUSHDOWN)) {
		if(depthAtNodeHavingChildOfDegree[stripeId][0] < o.depthAtNodeHavingChildOfDegree[stripeId][0]) {
		    return +1;
		} else if(depthAtNodeHavingChildOfDegree[stripeId][0] > o.depthAtNodeHavingChildOfDegree[stripeId][0]) {
		    return -1;
		} else {
		    // We temporarily threat these as equal but the lower layer might eventually use gnpdistance(requestor,prospective) to break the tie
		    
		    return 0;
		}	

	    } else {
		//default
		return 0;

	    }
	} else {
	    // both are agg=true
	      if((mycontent.mode == MultitreeContent.ANYCASTNEIGHBOR) || (mycontent.mode == MultitreeContent.ANYCASTFORTREEIMPROVEMENT) || (mycontent.mode == MultitreeContent.ANYCASTFORCONNECTIONIMPROVEMENT)) {
		  if(minAvailableDepth[stripeId] < o.minAvailableDepth[stripeId]) {
		      return +1;
		  } else if(minAvailableDepth[stripeId] > o.minAvailableDepth[stripeId]) {
		      return -1;
		  } else {
		      // We temporarily threat these as equal but the lower layer might eventually use gnpdistance(requestor,prospective) to break the tie
		    
		      return 0;
		  }
	      }  else if((mycontent.mode == MultitreeContent.ANYCASTFORPREMPTDEGREEPUSHDOWN)) {
		  if(depthAtNodeHavingChildOfDegree[stripeId][0] < o.depthAtNodeHavingChildOfDegree[stripeId][0]) {
		      return +1;
		  } else if(depthAtNodeHavingChildOfDegree[stripeId][0] > o.depthAtNodeHavingChildOfDegree[stripeId][0]) {
		      return -1;
		  } else {
		      // We temporarily threat these as equal but the lower layer might eventually use gnpdistance(requestor,prospective) to break the tie
		      
		      return 0;
		  }	
	      } else {
		  return 0;
	      }
	}
	
    }

    
    public void setResponderDepth(int val) {
	responderDepth = val;
    }


    public String getSlotsAtDepthString(int stripeId) {
	String slotsAtDepthString = "[";
	for(int i=0; i< MAXSATURATEDDEPTH; i++) {
	    slotsAtDepthString = slotsAtDepthString + availableSlotsAtDepth[stripeId][i] + ","; 
	}
	slotsAtDepthString = slotsAtDepthString + "]";
	return slotsAtDepthString;
    }

    
    public String getDepthAtNodeHavingChildOfDegreeString(int stripeId) {
	String depthAtNodeHavingChildOfDegreeString = "[";
	for(int i=0; i< (MAXCHILDDEGREE + 1); i++) {
	    depthAtNodeHavingChildOfDegreeString = depthAtNodeHavingChildOfDegreeString + depthAtNodeHavingChildOfDegree[stripeId][i] + ","; 
	}
	depthAtNodeHavingChildOfDegreeString = depthAtNodeHavingChildOfDegreeString + "]";
	return depthAtNodeHavingChildOfDegreeString;
    }


    public int getSizeInBytes() {
	int val = getbasecostofsaarcontentInBytes();

	int sizeRequestorsPathToRoot;
	if(requestorsPathToRoot == null) {
	    sizeRequestorsPathToRoot = 0;
	} else {
	    sizeRequestorsPathToRoot = requestorsPathToRoot.length;
	}

	
	if((this.mode == ANYCASTNEIGHBOR) || (this.mode ==ANYCASTFORTREEIMPROVEMENT) || (this.mode == ANYCASTFORCONNECTIONIMPROVEMENT) || (this.mode == ANYCASTFORPREMPTDEGREEPUSHDOWN)) {
	    val = val + 7 + sizeRequestorsPathToRoot;
	} else if((mode == SaarContent.UPWARDAGGREGATION) || (mode == SaarContent.DOWNWARDPROPAGATE)) {
	    val = val + 5 + MultitreeClient.NUMSTRIPES * (12 + 2 + MAXSATURATEDDEPTH);  // we just use depthof node of child of degree atmost 2 to get the premption working, alos note that pathToRoot is not aggregated
	    

	}
	return val;

    }


    



    public String toString() {
	String s = "MultitreeContent:";
	s = s + " mode:" + mode;
	if((mode == MultitreeContent.ANYCASTNEIGHBOR)||(mode == MultitreeContent.ANYCASTFORTREEIMPROVEMENT)||(mode == MultitreeContent.ANYCASTFORCONNECTIONIMPROVEMENT)) {
	    s = s + " anycastGlobalId:" + anycastGlobalId + " anycastneighbormode:" + anycastneighbormode; 
	} else if(mode == SaarContent.UPWARDAGGREGATION) {
	    s = s + " aggregateFlag:" + aggregateFlag;
	    s = s + " sourceBroadcastSeq:" + sourceBroadcastSeq;
	    s = s + " descendants:" + descendants;
	    s = s + " totalSecondaryChildren:" + totalSecondaryChildren;
	    s = s + " overallUsedSlots:" + overallUsedSlots;
	    s = s + " overallTotalSlots:" + overallTotalSlots;
	    s = s + " cumulativeStreamingQuality:[";
	    for(int numRedundantStripes=0; numRedundantStripes < MultitreeClient.NUMSTRIPES; numRedundantStripes ++) {
		s = s + cumulativeStreamingQuality[numRedundantStripes] + ",";
	    }
	    s = s + "]";

	    for(int stripeId=0; stripeId < MultitreeClient.NUMSTRIPES; stripeId ++) {
		s = s + " Stripe[" + stripeId + "] (";
		s = s + " usedSlots:" + usedSlots[stripeId];
		s = s + " currMaximumSlots:" + currMaximumSlots[stripeId];
		s = s + " totalSlots:" + totalSlots[stripeId];	    
		s = s + " connectedStripeAvailableSlots:" + connectedStripeAvailableSlots[stripeId];
		s = s + " connectedTotalAvailableSlots:" + connectedTotalAvailableSlots[stripeId];
		s = s + " streamingQuality:" + streamingQuality[stripeId];
		s = s + " treeDepth:" + treeDepth[stripeId];
		s = s + " minAvailableDepth:" + minAvailableDepth[stripeId];
		s = s + " depthAtNodeHavingChildOfDegree-zero:" + depthAtNodeHavingChildOfDegree[stripeId][0];
		s = s + ")";
	    }

	} else if(mode == SaarContent.DOWNWARDPROPAGATE) {
	    s = s + " grpDescendants:" + descendants;
	    s = s + " grpTotalSecondaryChildren:" + totalSecondaryChildren;
	    s = s + " grpOverallUStatic(" + overallUsedSlots + "/" + overallTotalSlots + ")";
	    s = s + " grpCmulativeStreamingQuality: [";
	    for(int numRedundantStripes=0; numRedundantStripes < MultitreeClient.NUMSTRIPES; numRedundantStripes ++) {
		s = s + cumulativeStreamingQuality[numRedundantStripes] + ",";
	    }
	    s = s + "]";

	    for(int stripeId = 0; stripeId < MultitreeClient.NUMSTRIPES; stripeId++) {
		s = s + " {{{ Stripe[" + stripeId + "] ";
		s = s + " slots:" + usedSlots[stripeId] + "/" + currMaximumSlots[stripeId] + "/" + totalSlots[stripeId] + "/" + connectedStripeAvailableSlots[stripeId] + "/" + connectedTotalAvailableSlots[stripeId];
		s = s + " stripeQuality:" + streamingQuality[stripeId]; 
		s = s + " grpAvailableSlotsAtDepth:" + getSlotsAtDepthString(stripeId);
		s = s + " grpDepthAtNodeHavingChildOfDegree:" + getDepthAtNodeHavingChildOfDegreeString(stripeId);
		s = s + ")";
	    }
	}
	return s;

    }



}
