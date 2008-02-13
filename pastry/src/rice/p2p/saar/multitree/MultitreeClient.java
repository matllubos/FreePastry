/*
 * Created on May 4, 2005
 */
package rice.p2p.saar.multitree;

import rice.p2p.saar.*;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.selector.SelectorManager;
//import rice.replay.*;
import java.util.Random;
import java.util.Vector;
import java.util.Hashtable;
import java.lang.String;
import java.io.*;
import java.net.*;
import java.util.prefs.*;
import rice.p2p.util.MathUtils;
import java.text.*;
import java.util.*;

import rice.pastry.PastryNodeFactory;
import rice.pastry.socket.SocketPastryNodeFactory;
import rice.pastry.socket.SocketNodeHandle;
import rice.p2p.commonapi.Application;
import rice.p2p.commonapi.CancellableTask;
import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.Id;
import rice.p2p.commonapi.Message;
import rice.p2p.commonapi.NodeHandle;
import rice.p2p.commonapi.NodeHandleSet;
import rice.p2p.commonapi.RouteMessage;
import rice.p2p.scribe.Scribe;
import rice.p2p.scribe.ScribeClient;
import rice.p2p.scribe.ScribeContent;
import rice.p2p.scribe.ScribeImpl;
import rice.p2p.scribe.Topic;
import rice.pastry.PastryNode;
import rice.pastry.commonapi.PastryIdFactory;
import rice.pastry.routing.RoutingTable;
import rice.pastry.routing.RouteSet;
import rice.pastry.socket.*;
import rice.pastry.leafset.*;
import rice.*;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.environment.params.Parameters;
import rice.p2p.commonapi.*;
import rice.p2p.scribe.messaging.*;


/**
 *
 * @author Animesh
 */
// The multi-tree implementation borrows lot of the code from the single tree. variables that are common across stripes are maintained as is, the rest stripe specific stuff is included in the StripeInformation class
public class MultitreeClient extends DataplaneClient {

    public static boolean ENABLEPRIMARYFRAGMENTRECONSTRUCTION = false;

    public static int NUMREDUNDANTSTRIPES = 1; // This information will be used by the in-network fragment regeneration
    
    public static final long ANYCASTTIMEOUT = 1500; // After issuing the previous anycast the node must wait for atleast thsi time

    public static int NUMSTRIPES = 7;  // Nov16, changed default to 7 stripes
    



    //public static boolean SPLITSTREAMCONFIG = false; // when set to true the slots are chosen in indegree * outdegree configuration, we also commented out a line to in the DROPCHILDREN mode to even drop from the primary stripe

    //public static int SPLITSTREAMOUTDEGREE = 6;


    public static boolean ENABLETREEIMPROVEMENT = true;

    public static boolean ENABLEANYCASTFORSECONDARYREPLACEMENT = true; 

    public static boolean ENABLELIMITNONCONTRIBUTORS = false; // we switch to using the periodic prempt-degree-pushdown

    public static boolean ENABLE_PREMPT_DEGREE_PUSHDOWN = true;

    public double FRACTIONNONCONTRIBUTORS = 0.5;   


    // In the neighbor mainenance, the anycastmode is one of the following three types. See their meanings in the neighborMaintenance() method
    public static int ONLYPRIMARY = 1;
    public static int ALLOWSECONDARY = 2;
    public static int ACCEPTBYDROPING = 3; // This anycastneighbor mode will be used for ANYCASTNEIGHBOR as well as in ANYCASTFORPREMPTDEGREEPUSHDOWN
    public static int ONLYPRIMARY_LIMITNONCONTRIBUTORS = 4; // this imposes an addiitonal constraint on the ONLYPRIMARY mode to restrict the number of non-contributors


    // We basically put together the variables from the single tree into the StripeInformation class
    public StripeInformation[] stripeinfo = new StripeInformation[NUMSTRIPES];


    // these 2 variables will be used determine the expected packet number, the expected pkt number is the same across all stripes 
    public long firstPktTime = 0;
    public int firstPktSequence = -1;
    public long firstBroadcastTime = 0;

    // This is a control pkt flowing down the Scribe tree telling me what the root is currently publishing
    public int rootSeqNumRecv = -1; 



    public static long PUBLISHPERIOD  = 1000; // this is the rate at which the multicast source publishes
    public static long BLOCKPERIOD = 200; // This is the gap between when we publish in consecutive stripes in the round-robbin publishing . It is calculated as PUBLISHPERIOD / NUMSTRIPES and it should be an integer

    public int nextStripeToPublishOn = 0;  // Used only by the multicast source

    public int currglobalSeq = 0; // This will be incremented every BLOCKPERIOD in sendMulticastTopic()


    public static int MAXADVERTISEDTREEDEPTH = 100; // We advertise this value when we are not connected to the source (i.e we do not have a valid pathToRoot)  



    //public static final long PARENTHEARTBEATPERIOD = 1000; // This is the period in which the parents sends a heartbeat to its children 

    //public static final long CHILDHEARTBEATPERIOD = 1000; // 1000, This is the period in which the children are excepted to send a heartbeat to their parents, this enables us to remove children who have left abruptly

    //public static int PARENTDEADTHRESHOLD = 2000; // We declare a neighbor dead if we have not heard from him for 2 sec

    //public static int CHILDDEADTHRESHOLD = 2000; // We declare a neighbor dead if we have not heard from him for 2 sec



    public static final long TREEIMPROVEMENTPERIOD = 30000; // This is the period at which the node issues anycast to improve the uality of the tree (i.e say DEPTH,RDP etc)


    public long CHILDRESERVATIONTIME = 2000; // we hold a reservation for 2 sec


    public Id zeroId;

  
    public boolean amSubscribed = false; // This variable will be set/unset in the join/leave methods


    public boolean chosenPrimaryStripe = false; // We set this to true, when it receives the grpSummary and then accordingly chooses which stripe is the primary stripe

    public Random rng = new Random();


    public OverallTemporalBufferMap overallsendbmap; // this is a buffer map over all the stripes

    public long lastSecondaryConnectionRemovalTime = 0; // Periodically the node anycasts to finds primary parent connections to replace any secondary connections (a connection in which the local node is a secondary child of its parent)

    public long lastPremptDegreePushdownTime = 0; 

    public long lastNeighborMaintenanceInvokationTime = 0;

    public long lastPushedReconstructedFragmentsTime = 0;

    public static final long SECONDARYCONNECTIONREMOVALPERIOD = 15000;


    public static final long PREMPTDEGREEPUSHDOWNPERIOD = 15000;

    public static boolean RESOURCEBASEDPRIMARYSELECTION = true; // true - we choose the primary stripe with the minimum available resources, false - we choose a random stripe (this emulates original splitstream's NodeId based stripe selection

    public int chosenStripeIndex = - 1; // this will be set to the primary Stripe index

    public Random linklossRng = new Random(); 

    public int numpktssent = 0;

    public Hashtable seqRecv; // This keeps track of the which fragments of a sequence number a node received

    public long EXPECTEDJITTER = 1000; // This is the maximum time bound after which a node should start in-network primary fragment generation 


    public Vector reconstructedFragmentsToPush; // This will be the fragments that could be reconstructred and should be pushed opportunistically on having spare bandwidth


    public static boolean ENABLESIMULATORGRPSUMMARY = false;

    public static boolean AVOIDWAITFORGRPUPDATE = false;

    public static int DATAMSGSIZEINBYTES;    

    public long lastAnycastForRepairTime[] = new long[NUMSTRIPES]; // only corresponds to detecting failure and issuing anycast. does not include cases for periodic tree repairs or sending anycast when receiving unsubscribe message

    public long expbackoffInterval[] = new long[NUMSTRIPES];
    public long maxExpbackoffInterval[] = new long[NUMSTRIPES];

    public double dataplaneriusedbycontrol; 

    public int NUMFRAGMENTSPERSECPERCHILD; // This is the number of pkts sent per child per sec. This is computed based on the publish period. 


    // This data structure keeps track of the fragments received for a particular sequence number
    public class SeqState {
	
	public int[] fragments = new int[NUMSTRIPES];

	public int seqNum;

	public long firstFragmentRecvTime;

	// This variable will be true only if the local node failed to receive the fragment on the primary stripe, was able to reconstruct this fragment from the other fragments and then pushed it down. Note that all the above actions shouldbe true for this variable to be true;
	public boolean reconstructedAndPushedPrimary; 

	public SeqState(int seqNum, long firstFragmentRecvTime) {
	    this.seqNum = seqNum;
	    this.reconstructedAndPushedPrimary = false;
	    this.firstFragmentRecvTime = firstFragmentRecvTime;
	    for(int i=0; i< NUMSTRIPES; i++) {
		fragments[i] = 0;
	    }

	}

	public void recvFragment(int stripeId) {
	    fragments[stripeId] = 1;
	}

	public boolean isMissing(int stripeId) {
	    if((stripeId >=0) && (stripeId < NUMSTRIPES)) {
		if(fragments[stripeId] == 0) {
		    return true;
		} else {
		    return false;
		}
	    } else {
		return false;
	    }
	}

	public int totalFragments() {
	    int sum = 0;
	    for(int i=0; i< NUMSTRIPES; i++) {
		sum = sum + fragments[i];
	    }
	    return sum; 

	}

	public boolean isBeyondJitter(long currtime) {
	    if((currtime - firstFragmentRecvTime) > EXPECTEDJITTER) {
		return true;
	    } else {
		return false;
	    }

	}

	
	public boolean canReconstructBlock() {
	    int sum = totalFragments();
	    if(sum >= (NUMSTRIPES - NUMREDUNDANTSTRIPES)) {
		return true;
	    } else {
		return false;
	    }
	}
	

	public boolean hasAll() {
	    int sum = totalFragments();
	    if(sum == NUMSTRIPES) {
		return true;
	    } else {
		return false;
	    }
	}

	public String toString() {
	    String s = "SEQSTATE(" + seqNum + "," + firstFragmentRecvTime + ", [";
	    for(int i= 0; i< NUMSTRIPES; i++) {
		if(fragments[i] == 0) {
		    s = s + "0,";
		} else {
		    s = s + "1,";
		}
	    }
	    s = s + "])";
	    return s;
	}
	


    }
	

    public class StripeInformation {

	public SaarTopic saartopic;

	public int stripeId;

	public boolean isPrimary = false; // only one stripe will be designated as primary, except for the multicast source in which all stripes are isPrimary

	public Hashtable numSequence; // this is a hashtbale of the sequence numbers I got and the number of times I got.


	public Hashtable blocks; // This hashtable stores the distribution tree of the blocks we receive

	/***  These variables track the current state of the node ****/
	public TemporalBufferMap sendbmap = null; // This bmap will be used only for debugging/tracking purposes, to help us track the quality the node is receiving
    
	public int streamingQuality = 99; // This is based on the left end bits (advertisedwindow - fetchwindow) of the temporalBMAP . We DO NOT initialize with '-1' because this might indicate very poor streaming Quality when the system is not bootstrapped (it has not received control pkt on Scribe tree)

	public int treeDepth = -1; 
	
	public int minAvailableDepth = -1;
	
	public boolean parentIsAlive = false; // This is true when we received a periodic heartbeat from the parent
	
	public boolean isConnected = false; // This is true if we are getting packets from the source via are pathToRoot in the recent past, note that having parentIsAlive does not guarantee isConnected
	
	
	public int[] grpAvailableSlotsAtDepth; // here when a node is not connected the treedepth is considered maxdepth, so this can be considered as a distribution of grpConnectedAvailableSlots 

	public int[] grpDepthAtNodeHavingChildOfDegree; 


	public int grpAvailableSlots; // This is a superset of grpConnectedStripeAvailableSlots

	public int grpConnectedStripeAvailableSlots;

	
	public int grpConnectedTotalAvailableSlots;

       
	public boolean grpAllowNonContributors; 

	public long lastPktRecvTime = 0; // This is the last time we received a pkt on the tree data plane
	
	public int lastPktRecvSeqNum = 0; // This is the last sequence number received on this stripe

	public long lastTreeImprovementTime = 0; // this is the last time we sent an anycast to do tree optimization on this stripe
	
	
	public TopicManager manager;
	
	private int maximumOutdegree = 0; // note that this maximumoutdegree might be violated if the anycast needs to be resolved via a spare capacity group. Thus the total slots allocated to a stripe might be less than maximumOutdegree. The maximumOutdegree of non-primary stripes are zero. We initalize the maximum outdegree as zero and then set one stripe's (i.e primary stripes) value to non-zero 

	
    

	public long lastParentHeartbeatSentTime = 0; // this is the last time the local node sent a heartbeat to its children

	public long lastChildHeartbeatSentTime = 0; // this is the last time the local node sent a heartbeat to its parent


	
    
	public StripeInformation(SaarTopic saartopic, int stripeId) {
            this.saartopic = saartopic;
            this.stripeId = stripeId;
	    maximumOutdegree = 0 ; // We initially set the maximumoutdegree to '0' but we will later set it to nodedegree*NUMSTRIPES on the stripe that is chosen as primary stripe, for the multicast source it will be set as nodedegree for each stripe
	    numSequence = new Hashtable();
	    blocks = new Hashtable(); 
	    grpAvailableSlotsAtDepth = new int[MultitreeContent.MAXSATURATEDDEPTH];
	    for(int i=0; i< MultitreeContent.MAXSATURATEDDEPTH; i++) {
		grpAvailableSlotsAtDepth[i] = -1;
	    }
	    grpDepthAtNodeHavingChildOfDegree = new int[MultitreeContent.MAXCHILDDEGREE + 1];
	    for(int i=0; i< (MultitreeContent.MAXCHILDDEGREE + 1); i++) {
		grpDepthAtNodeHavingChildOfDegree[i] = MAXADVERTISEDTREEDEPTH;
	    }


	    manager = new TopicManager(saartopic, stripeId);
	    
	}

	public void setMaximumOutdegree(int val) {
	    this.maximumOutdegree = val;

	}

	public int getMaximumOutdegree() {
	    return this.maximumOutdegree;

	}

	public void setIsPrimary(boolean val) {
	    isPrimary = val;
	}

	public boolean getIsPrimary() {
	    return isPrimary;
	}


    }

    public MultitreeClient(SaarClient saarClient, int tNumber, SaarTopic saartopic, String topicName, int dataplaneType, double nodedegreeControlAndData, boolean amMulticastSource, boolean amVirtualSource) {
	super(saarClient, tNumber, saartopic, topicName, dataplaneType, nodedegreeControlAndData, amMulticastSource, amVirtualSource);

	PUBLISHPERIOD  = NUMSTRIPES * BLOCKPERIOD; 

	this.nodedegree = nodedegreeControlAndData; // This is the initial value that can be used by the dataplane of which it will be later recomputed after subtracting the controlRI

	if(SaarTest.logLevel <= 880) myPrint("multitree: INITIAL nodedegree: " + nodedegree , 880);

	zeroId = rice.pastry.Id.build();

	for(int i=0; i < NUMSTRIPES; i++) {
	    stripeinfo[i] = new StripeInformation(saartopic,i);

	}

	for(int i=0; i < NUMSTRIPES; i++) {
	    lastAnycastForRepairTime[i] = 0;
	    expbackoffInterval[i] = 500;
	    maxExpbackoffInterval[i] = 500;
	}


	if(amMulticastSource) {
	    if(SaarTest.logLevel <= 880) myPrint("multitree: " + "I AM MULTITREE MULTICAST SOURCE", 880);
	}


	seqRecv = new Hashtable();

	reconstructedFragmentsToPush = new Vector();

	saarClient.reqRegister(saartopic, this);

	saarClient.viewer.setNumForegroundStripes(NUMSTRIPES);

	saarClient.viewer.setForegroundPublishPeriod(PUBLISHPERIOD);  // Added on Sep21-2007 after noticing that when experimenting with different publish periods in the singletree/mesh, the FOREGROUNFPUBLISHPERIOD value used in serveBlocks() in the BlockbasedClient was set always to a default value of 1000

	DATAMSGSIZEINBYTES = (int)(SaarClient.STREAMBANDWIDTHINBYTES / ((1000/PUBLISHPERIOD) * NUMSTRIPES));

	NUMFRAGMENTSPERSECPERCHILD = (int)(1000/PUBLISHPERIOD);
	

	if(SaarTest.logLevel <= 880) myPrint("multitree: " + "BLOCKPERIOD: " + BLOCKPERIOD + " PUBLISHPERIOD: " + PUBLISHPERIOD  + "NUMFRAGMENTSPERSECPERCHILD: " + NUMFRAGMENTSPERSECPERCHILD + " DATAMSGSIZEINBYTES: " + DATAMSGSIZEINBYTES, 880);

    }


    public long getCurrentTimeMillis() {
	return saarClient.getCurrentTimeMillis();
    }


    public void join() {
	if(SaarTest.logLevel <= 880) myPrint("multitree: " + "multitreeclient.join()", 880);
	chosenStripeIndex = -1;
	rootSeqNumRecv = -1;
	firstPktTime = 0;
	firstPktSequence = -1;
	for(int stripeId=0; stripeId < NUMSTRIPES; stripeId++) {
	    stripeinfo[stripeId] = new StripeInformation(saartopic,stripeId);
	}


	for(int i=0; i < NUMSTRIPES; i++) {
	    lastAnycastForRepairTime[i] = 0;
	    expbackoffInterval[i] = 500;
	    maxExpbackoffInterval[i] = 500;
	}


	// We will set the maximum outdegree on each stripe, for the source it is nodedegree on each stripe
	if(amMulticastSource) {
	    for(int stripeId=0; stripeId < NUMSTRIPES; stripeId++) {
		stripeinfo[stripeId].setMaximumOutdegree((int)this.nodedegree);
		stripeinfo[stripeId].setIsPrimary(true);
	    }
	    chosenPrimaryStripe = true;  
	} else {
	    // We will wait till we hear the available resources on each stripe
	    chosenPrimaryStripe = false;	
	    // We will initially set the maximumoutdegree for each stripe to be zero until we get the grpSummary and set the maximumOutdegree only on that node

	    for(int stripeId=0; stripeId < NUMSTRIPES; stripeId++) {
		stripeinfo[stripeId].setMaximumOutdegree(0);
		stripeinfo[stripeId].setIsPrimary(false);
	    }
    
	}

	numpktssent = 0;
	saarClient.reqSubscribe(tNumber);
	amSubscribed = true;
	
	//int expectingPkt = saarClient.viewer.getForegroundBroadcastSeqnum();
	//if(SaarTest.logLevel <= 875) myPrint("multitree: " + "firstForegroundPktToExpectAfterJoin(" + expectingPkt + ")", 875);
	//saarClient.viewer.setFirstForegroundPktToExpectAfterJoin(expectingPkt);
	
	saarClient.viewer.setFirstForegroundPktAfterJoin(-1);


	//if(ENABLESIMULATORGRPSUMMARY) {
	//  grpSummary(saartopic, null, (SaarContent)saarClient.simulator.grpSummaryMetadata.get(saartopic));

	//}


    }


    public void dataplaneMaintenance() {
	if(SaarTest.logLevel <= 875) myPrint("multitree: " + "multitreeclient.dataplaneMaintenance()", 875);
	if(amMulticastSource) {
	    sendMulticastTopic();
	}
	sendHeartbeats();
	neighborMaintenance(); // Here it attempts to remove dead children / maintain a 'parent' for the singletree data plane. Note that having to remove dead children implies even the multicast source would invoke this
	
    }


    public void controlplaneUpdate(boolean forceUpdate) {
	if(SaarTest.logLevel <= 875) myPrint("multitree: " + "multitreeclient.controlplaneUpdate()", 875);

	int overallUsedSlots = 0;
	int overallNonContributors = 0;
	int overallTotalSlots = 0;

	int numSecondaryChildren = 0; // these are children in the non-primary stripes, identified as numChildren > maximumOutdegree
	for(int stripeId=0; stripeId < NUMSTRIPES; stripeId ++) {
	    StripeInformation currstripe = stripeinfo[stripeId];
	    int usedSlots = currstripe.manager.numChildren();
	    overallUsedSlots = overallUsedSlots + usedSlots;
	    int totalSlots = currstripe.maximumOutdegree;
	    overallTotalSlots = overallTotalSlots + totalSlots;
	    
	    if((totalSlots == 0) && (usedSlots > totalSlots)) {
		numSecondaryChildren = numSecondaryChildren + usedSlots;
	    }
	}
	if(SaarTest.logLevel <= 880) myPrint("multitree: " + "NUMSECONDARYCHILDREN: " + numSecondaryChildren, 880);


	int[] usedSlots = new int[NUMSTRIPES];
	int[] numNonContributors = new int[NUMSTRIPES];
	boolean[] allowNonContributors = new boolean[NUMSTRIPES];
	int[] currMaximumSlots = new int[NUMSTRIPES];  // currMaximumSlots < maximumOutdegree. It is the maximumOutdegree - (total number of sparecapacityChildren). The toal number of spare capacity children over all stripes is the sum of the children in the stripes where usedSlots > maximumOutdegree
	int[] totalSlots = new int[NUMSTRIPES];
	int[] connectedStripeAvailableSlots = new int[NUMSTRIPES];
	int[] connectedTotalAvailableSlots = new int[NUMSTRIPES];
	int[] cumulativeStreamingQuality = new int[NUMSTRIPES]; 
	int[] streamingQuality = new int[MultitreeClient.NUMSTRIPES];
	boolean[] isConnected = new boolean[MultitreeClient.NUMSTRIPES]; 
	int[] treeDepth = new int[MultitreeClient.NUMSTRIPES];
	int[] minAvailableDepth = new int[MultitreeClient.NUMSTRIPES]; 
	Id[][] pathToRoot = new Id[MultitreeClient.NUMSTRIPES][]; 
	int[][] availableSlotsAtDepth = new int[MultitreeClient.NUMSTRIPES][] ;
	int[][] depthAtNodeHavingChildOfDegree = new int[MultitreeClient.NUMSTRIPES][] ;
	String[] depthAtNodeHavingChildOfDegreeStripeString = new String[MultitreeClient.NUMSTRIPES] ;


	for(int stripeId=0; stripeId < NUMSTRIPES; stripeId ++) {
	    StripeInformation currstripe = stripeinfo[stripeId];
	    currstripe.sendbmap = computeSendBMAP(stripeId);
	    
	    currstripe.streamingQuality = getStreamingQualityToAdvertise(currstripe.sendbmap);
	    streamingQuality[stripeId] = currstripe.streamingQuality;
	    currstripe.isConnected = evaluateIsConnected(stripeId);
	    isConnected[stripeId] = currstripe.isConnected;
	    currstripe.treeDepth = currstripe.manager.getTreeDepth();
	    treeDepth[stripeId] = currstripe.treeDepth;

	    usedSlots[stripeId] = currstripe.manager.numChildren();
	    totalSlots[stripeId] = currstripe.maximumOutdegree;

	    numNonContributors[stripeId] = currstripe.manager.numNonContributors();


	    if(totalSlots[stripeId] == 0) {
		// for secondary stripes, it is the current exceeded consumption
		currMaximumSlots[stripeId] = usedSlots[stripeId];
	    } else {
		// for the primary stripes, it is the total slots minus the total number of secondry children
		currMaximumSlots[stripeId] = totalSlots[stripeId] - numSecondaryChildren;
	    }


	    if(!isConnected[stripeId]) {
		allowNonContributors[stripeId] = false;
	    } else if(usedSlots[stripeId] >= currMaximumSlots[stripeId]) { // Note that for the non-primary stripes allowNonContributors will always be zero. This is fine since the LIMITNONCONTRIBUTOR mode is a subset of ONLYPRIMARY mode. If there is no remaning available bandwidth then allowNonContributors[stripeId] should be false
		allowNonContributors[stripeId] = false;
	    } else if((currstripe.treeDepth < 3) && (numNonContributors[stripeId] >= ((int)(FRACTIONNONCONTRIBUTORS * currMaximumSlots[stripeId])))) {
		allowNonContributors[stripeId] = false;
	    } else {
		allowNonContributors[stripeId] = true; // this implies that there are empty slots for non-contributors on connected nodes even in the restrictive mode of ONLYPRIMARY_LIMITNONCONTRIBUTORS
	    }




	    if(currstripe.isConnected) {
		connectedStripeAvailableSlots[stripeId] = currMaximumSlots[stripeId] - usedSlots[stripeId];
	    } else {
		connectedStripeAvailableSlots[stripeId] = 0;
	    }

	    if(currstripe.isConnected) {
		connectedTotalAvailableSlots[stripeId] = overallTotalSlots - overallUsedSlots;
	    } else {
		connectedTotalAvailableSlots[stripeId] = 0;
	    }
 
	    if(usedSlots[stripeId] < totalSlots[stripeId]) {
		minAvailableDepth[stripeId] = currstripe.treeDepth; // currstripe.treeDepth also takes into account that the node is connected
	    } else {
		minAvailableDepth[stripeId] = Integer.MAX_VALUE;
	    }
	    pathToRoot[stripeId] = currstripe.manager.getPathToRoot();

 
	    availableSlotsAtDepth[stripeId] = new int[MultitreeContent.MAXSATURATEDDEPTH];
	    for(int i=0; i< MultitreeContent.MAXSATURATEDDEPTH; i++) {
		availableSlotsAtDepth[stripeId][i] = 0;
		if(currstripe.treeDepth == i) {
		    availableSlotsAtDepth[stripeId][i] = currMaximumSlots[stripeId] - usedSlots[stripeId];
		}
	    }

	    depthAtNodeHavingChildOfDegree[stripeId] = currstripe.manager.getDepthAtNodeHavingChildOfDegree();
	    depthAtNodeHavingChildOfDegreeStripeString[stripeId] = "[";
	    for(int i=0; i< (MultitreeContent.MAXCHILDDEGREE + 1); i++) {
		depthAtNodeHavingChildOfDegreeStripeString[stripeId] = depthAtNodeHavingChildOfDegreeStripeString[stripeId] + depthAtNodeHavingChildOfDegree[stripeId][i] + ", ";
	    }
	    depthAtNodeHavingChildOfDegreeStripeString[stripeId] = depthAtNodeHavingChildOfDegreeStripeString[stripeId] + "] ";



	    //String pathToRootAsString = getPathToRootAsString(pathToRoot[stripeId]);
	    String pathToRootAsString = currstripe.manager.getPathToRootAsString();

	    if(SaarTest.logLevel <= 850) myPrint("multitree: " + "Stripe[" + stripeId + "]" + " isPrimary: " + currstripe.isPrimary + " SENDBMAP " + " BMAP: " + currstripe.sendbmap + " usedSlots: " + usedSlots[stripeId] + " numNonContributors: " + numNonContributors[stripeId] + " allowNonContributors: " + allowNonContributors[stripeId] + " currMaximumSlots: " + currMaximumSlots[stripeId] + " totalSlots: " + totalSlots[stripeId] + " connectedStripeAvailableSlots: " + connectedStripeAvailableSlots[stripeId] + " connectedTotalAvailableSlots: " + connectedTotalAvailableSlots[stripeId] + " StreamingQuality: " + streamingQuality[stripeId] + " isConnected: " + isConnected[stripeId] + " treeDepth: " + treeDepth[stripeId] + " minAvailableDepth: " + minAvailableDepth[stripeId] + " rootSeqNumRecv: " + rootSeqNumRecv + " pathToRoot: " + pathToRootAsString + " depthAtNodeHavingChildOfDegreeStripeString: " + depthAtNodeHavingChildOfDegreeStripeString, 850);

	}


	String numNonContributorsString = "[";
	for(int stripeId=0; stripeId < NUMSTRIPES; stripeId++) {
	    numNonContributorsString = numNonContributorsString + numNonContributors[stripeId] + ",";
	    overallNonContributors = overallNonContributors + numNonContributors[stripeId];

	}
	numNonContributorsString = numNonContributorsString + "]";

	String allowNonContributorsString = "[";
	for(int stripeId=0; stripeId < NUMSTRIPES; stripeId++) {
	    allowNonContributorsString = allowNonContributorsString + allowNonContributors[stripeId] + ",";

	}
	allowNonContributorsString = allowNonContributorsString + "]";



	String stripeStreamingQualityString = "[";
	for(int stripeId=0; stripeId < NUMSTRIPES; stripeId++) {
	    stripeStreamingQualityString = stripeStreamingQualityString + streamingQuality[stripeId] + ",";

	}
	stripeStreamingQualityString = stripeStreamingQualityString + "]";


	String treeDepthString = "[";
	for(int stripeId=0; stripeId < NUMSTRIPES; stripeId++) {
	    treeDepthString = treeDepthString + treeDepth[stripeId] + ",";

	}
	treeDepthString = treeDepthString + "]";


	String connectedStripeAvailableSlotsString = "[";
	for(int stripeId=0; stripeId < NUMSTRIPES; stripeId++) {
	    connectedStripeAvailableSlotsString = connectedStripeAvailableSlotsString + connectedStripeAvailableSlots[stripeId] + ",";

	}
	connectedStripeAvailableSlotsString = connectedStripeAvailableSlotsString + "]";


	String connectedTotalAvailableSlotsString = "[";
	for(int stripeId=0; stripeId < NUMSTRIPES; stripeId++) {
	    connectedTotalAvailableSlotsString = connectedTotalAvailableSlotsString + connectedTotalAvailableSlots[stripeId] + ",";

	}
	connectedTotalAvailableSlotsString = connectedTotalAvailableSlotsString + "]";



	String zerodegreeChildString = "[";
	for(int stripeId=0; stripeId < NUMSTRIPES; stripeId++) {
	    zerodegreeChildString = zerodegreeChildString + depthAtNodeHavingChildOfDegree[stripeId][0] + ",";

	}
	zerodegreeChildString = zerodegreeChildString + "]";




	
	// We will now form the aggregate buffermap over the stripes
	computeOverallTemporalBufferMap(); // this updates the global overallsendbmap vriable using the recent stripeinfo[i].sendbmap 

	String cumulativeStreamingQualityString = "[";
	for(int numRedundantStripes = 0; numRedundantStripes < NUMSTRIPES; numRedundantStripes ++) {
	    cumulativeStreamingQuality[numRedundantStripes] =  getCumulativeStreamingQualityToAdvertise(overallsendbmap,numRedundantStripes);
	    cumulativeStreamingQualityString = cumulativeStreamingQualityString + cumulativeStreamingQuality[numRedundantStripes] + ",";
	}
	cumulativeStreamingQualityString = cumulativeStreamingQualityString + "]";

	if(SaarTest.logLevel <= 850) myPrint("multitree: " + " OVERALLSENDBMAP: " + overallsendbmap + " StripeQuality: " + stripeStreamingQualityString + " connectedStripeAvailableSlots: " + connectedStripeAvailableSlotsString + " connectedTotalAvailableSlots: " + connectedTotalAvailableSlotsString +  " overallUsedSlots: " + overallUsedSlots + " overallNonContributors: " + overallNonContributors + " overallTotalSlots: " + overallTotalSlots + " allowNonContributors: " + allowNonContributorsString + " cumulativeStreamingQuality: " + cumulativeStreamingQualityString + " chosenStripeIndex: " + chosenStripeIndex + " numSecondaryChildren: " + numSecondaryChildren + " treeDepth: " + treeDepthString + " numNonContributors: " + numNonContributorsString + " zerodegreeChildString: " + zerodegreeChildString, 850);

	int selfBroadcastSeq = saarClient.allTopics[tNumber].pSeqNum -1; // this value will be -1 for nonsources always and incremented every timeperiod for the source in the sendMulticastTopic()

	MultitreeContent saarContent = new MultitreeContent(SaarContent.UPWARDAGGREGATION, topicName, tNumber, false, 1, overallUsedSlots, overallTotalSlots, usedSlots, currMaximumSlots, totalSlots, connectedStripeAvailableSlots, connectedTotalAvailableSlots, cumulativeStreamingQuality, streamingQuality, isConnected, treeDepth, minAvailableDepth, selfBroadcastSeq, pathToRoot, availableSlotsAtDepth, numSecondaryChildren, numNonContributors, allowNonContributors, depthAtNodeHavingChildOfDegree);

	saarClient.reqUpdate(tNumber,saarContent,forceUpdate);

    }




    public void leave() {
	if(SaarTest.logLevel <= 880) myPrint("multitree: " + "multitreeclient.leave()", 880);
	Topic topic = saartopic.baseTopic;
	if(!amMulticastSource) {
	    saarClient.reqUnsubscribe(tNumber);

	    if(SaarClient.LEAVENOTIFY) {
		for(int stripeId=0; stripeId < NUMSTRIPES; stripeId++) {
		    StripeInformation currstripe = stripeinfo[stripeId];
		    // We will also send out Unsubscribe messages to our parent/children
		    NodeHandle myparent = currstripe.manager.getParent();
		    if(myparent!= null) {
			if(SaarTest.logLevel <= 875) myPrint("multitree: " + "Stripe[" + stripeId + "]" + " Sending UnsubscribeMsg to parent " + myparent, 875);
			saarClient.endpoint.route(null, new UnsubscribeMsg(saarClient.endpoint.getLocalNodeHandle(),topic,stripeId), myparent);
			currstripe.manager.setParent(null,false);
		    }
		    NodeHandle[] mychildren = currstripe.manager.getChildren();
		    for(int i=0; i<mychildren.length; i++) {
			if(SaarTest.logLevel <= 875) myPrint("multitree: " + "Stripe[" + stripeId + "]" + " Sending UnsubscribeMsg to child " + mychildren[i], 875);
			saarClient.endpoint.route(null, new UnsubscribeMsg(saarClient.endpoint.getLocalNodeHandle(),topic,stripeId), mychildren[i]);
			currstripe.manager.removeChild(mychildren[i]);
		    }
		    
		    // We will also clear off the Hashtables blocks/numSequence
		    currstripe.blocks.clear();
		    currstripe.numSequence.clear();
		    
		}
	    }

	    for(int stripeId=0; stripeId < NUMSTRIPES; stripeId++) {
		StripeInformation currstripe = stripeinfo[stripeId];
		currstripe.blocks.clear();
		currstripe.numSequence.clear();
		currstripe.manager.initialize();
	    }
	    seqRecv.clear();
	    reconstructedFragmentsToPush.clear();
	    amSubscribed = false;
	    chosenStripeIndex = -1;
	    saarClient.viewer.initialize();
	}


    }


    public boolean recvAnycast(SaarTopic saartopic, Topic topic, SaarContent requestorcontent) {

	MultitreeContent myContent = (MultitreeContent)requestorcontent;
	int stripeId = myContent.requestorsDesiredStripeId;
	StripeInformation currstripe = stripeinfo[stripeId];
	int[] overallSlots = getOverallUsedAndTotalSlots();
	currstripe.treeDepth = currstripe.manager.getTreeDepth();

	if(SaarTest.logLevel <= 850) myPrint("multitree: " + "multitreeclient.recvAnycast(" + "gId:" + requestorcontent.anycastGlobalId + ", saartopic:" + saartopic + ", topic:" + topic + ", mode:" + requestorcontent.mode + ", stripeId: " + stripeId  + ", anycastneighbormode: " + myContent.anycastneighbormode + ")", 850);


	if(saarClient.endpoint.getLocalNodeHandle().equals(myContent.anycastRequestor)) {
	    // We do not accept the anycast made by ourself
	    if(SaarTest.logLevel <= 875) myPrint("multitree: " + "anycast(self=requestor, ret=false)", 875);
	    return false;
	    
	}

	if(currstripe.manager.containsChild(myContent.anycastRequestor)) {
	    if(SaarTest.logLevel <= 875) myPrint("multitree: " + "anycast(self=requestorIsExistingChild, ret=false) ", 875);
	    return false;
	    
	}

	// For the multicast source we strictly accept only primary children, that is the same number of children for each stripe. We also ensure only primary children in the modes ONLYPRIMARY and ONLYPRIMARY_LIMITNONCONTRIBUTORS
	if((myContent.anycastneighbormode == ONLYPRIMARY) || ( amMulticastSource && !((myContent.mode== MultitreeContent.ANYCASTFORPREMPTDEGREEPUSHDOWN) && (myContent.anycastneighbormode == ACCEPTBYDROPING)))) {       // for the multicast source, it does not accept secondary children even for the mode ALLOWSECONDARY, so this constraint is applied on  stripe basis even for mode ALLOWSECONDARY
	    if(currstripe.manager.numChildren() >= currstripe.maximumOutdegree) { 
		
		if(SaarTest.logLevel <= 875) myPrint("multitree: " + "anycast(uStaticLimitReachedBasedOnStripesTotalSlots, ret=false) ", 875);
		return false;
	    }
	} 

	
	// For the ONLYPRIMARY_LIMITNONCONTRIBUTORS, we enforce a stricted constraint onthe number of non-contributors we have
	if(myContent.anycastneighbormode == ONLYPRIMARY_LIMITNONCONTRIBUTORS) {
	    System.out.println("anycastneighbormode: limitnoncontributors");
	    if((currstripe.treeDepth < 3) && (currstripe.manager.numNonContributors() >= ((int)(FRACTIONNONCONTRIBUTORS * currstripe.maximumOutdegree)))) { // We should have ideally used the currMaximumSlots but the hard constraint that the total number of children across allstripes is less than total slots will be enforced later
		
		if(SaarTest.logLevel <= 875) myPrint("multitree: " + "anycast(uStaticLimitReachedBasedOnStripeNONCONTRIBUTORSTHRESHOLD, ret=false) ", 875);
		return false;
	    }
	}


	// Note here that for a ALLOWSECONDARY anycast request, the request reaches this point bypassing the above ONLYPRIMARY restrictive check


	
	// We check to see if we are getting good performance 
	currstripe.isConnected = evaluateIsConnected(stripeId);
	if(!currstripe.isConnected) {
	    if(SaarTest.logLevel <= 875) myPrint("multitree: " + "anycast(poorInstantaneousQuality, ret=false) ", 875);
	    return false;
	}



	if(currstripe.manager.hasLoops(myContent.anycastRequestor.getId())) {
	    if(SaarTest.logLevel <= 875) myPrint("multitree: " + "anycast(hasLoops, ret=false) ", 875);
	    return false;

	}



	// We additionally check for depth when the the mode is ANYCASTFORTREEIMPROVEMENT

	if((myContent.mode == MultitreeContent.ANYCASTFORTREEIMPROVEMENT) && ((currstripe.treeDepth + 1) >= myContent.requestorsTreeDepth)) {
	    if(SaarTest.logLevel <= 875) myPrint("multitree: " + "anycast(mode=TREEIMPROVEMENT, hasGreaterOREqualDepth, ret=false) ", 875);
	    return false;

	}


	if((myContent.mode== MultitreeContent.ANYCASTFORPREMPTDEGREEPUSHDOWN) && (myContent.anycastneighbormode == ACCEPTBYDROPING)) {
	    // We need to prempt somebody if the condition for premption satisfies

	    boolean willAcceptByDroping = true;
	    int[] depthAtNodeHavingChildOfDegree = currstripe.manager.getDepthAtNodeHavingChildOfDegree();;
	    
	    
	    // We assume here the requestorsdegree >0 and requestors has available capacity (thse conditions are checked before the requestor can issue an anycast)
	    if(currstripe.manager.numChildren() < currstripe.maximumOutdegree) {
		if(SaarTest.logLevel <= 875) myPrint("multitree: " + "PREMPTDEGREEPUSHDOWN-whypremptsincefreeslotsavailable-CONDITION-FAILED", 875);
		willAcceptByDroping = false;

	    } else if(myContent.requestorsTreeDepth <= (depthAtNodeHavingChildOfDegree[0] + 1)) {
		if(SaarTest.logLevel <= 875) myPrint("multitree: " + "PREMPTDEGREEPUSHDOWN-depth-CONDITION-FAILED", 875);
		willAcceptByDroping = false;
	    } else if(currstripe.maximumOutdegree == 0) {
		if(SaarTest.logLevel <= 875) myPrint("multitree: " + "PREMPTDEGREEPUSHDOWN-maximumOutdegree-CONDITION-FAILED", 875);
		willAcceptByDroping = false;
	    } else {
		if(SaarTest.logLevel <= 875) myPrint("multitree: " + "PREMPTDEGREEPUSHDOWN-DEPTH-CONDITION-PASSED", 875);
		willAcceptByDroping = true;
		
	    }
	    if(!willAcceptByDroping) {
		if(SaarTest.logLevel <= 875) myPrint("multitree: " + "anycast(mode=ANYCASTFORPREMPTDEGREEPUSHDOWN, anycastneighbormode=ACCEPTBYDROPING condition failed, ret=false) ", 875);
		return false;
	    } else {
		
		// We will choose a zero-degree child and give it the address of the anycast requestor as the prospective parent
		if(SaarTest.logLevel <= 875) myPrint("multitree: " + "COOL: Accepted PREMPTDEGREEPUSHDOWN/ACCEPTBYDROPPING request", 875);
		
		NodeHandle premptedChild = currstripe.manager.getZeroDegreeChild();
		if(premptedChild == null) {
		    System.out.println("ERROR: if no such child existed, the previous check of myContent.requestorsTreeDepth <= (depthAtNodeHavingChildOfDegree[0] + 1) should have resulted in willAcceptByDroping = false");
		    System.exit(1);
		}
		if(SaarTest.logLevel <= 875) myPrint("multitree: " + "Stripe[" + stripeId + "]" + " Sending PreemptChildMsg to child " + premptedChild, 875);
		saarClient.endpoint.route(null, new PremptChildMsg(saarClient.endpoint.getLocalNodeHandle(),topic,stripeId,myContent.anycastRequestor), premptedChild);
		stripeinfo[stripeId].manager.removeChild(premptedChild);
		
	    }
	    
	}
	

	// Even if the number of children is less that the absolute maximum for the stripe, we might have ot deny the request if some children are held in the non-primary stripes

	// In the Prempt-Degree-Pushdown case, this condition will not happen, since we would have already dropped a child
	if(overallSlots[0] >= overallSlots[1]) {
	    if(myContent.anycastneighbormode != ACCEPTBYDROPING) {
		if(SaarTest.logLevel <= 875) myPrint("multitree: " + "anycast(uStaticLimitReachedBasedOnChildrenAcrossAllStripes, ret=false) ", 875);
		return false;
	    } else {

		if(myContent.mode == MultitreeContent.ANYCASTNEIGHBOR) {
		    // We will check to see if we have some child we can drop. Such a child is a secondary child for which grpConnectedStipeAvailableSlots[stripeId] is non-zero
		    boolean willAcceptByDroping = false;
		    for(int k=0; k < NUMSTRIPES; k ++) {
			//if((!stripeinfo[k].isPrimary) && (stripeinfo[k].manager.numChildren() > 0) && (stripeinfo[k].grpConnectedStripeAvailableSlots > 0)) { // This will cause a bug when the primary stripe is sealed by non-contributors (i.e all people receiving this stripe are full of children, the leaves of this stripe are full becuase they are contributors in another stripe and have children in their own primary stripes that is different from the stripe that is being looked for), thus we relaxed the primary stripe constraint 
			if((stripeinfo[k].manager.numChildren() > 0) && (stripeinfo[k].grpConnectedStripeAvailableSlots > 0)) {
			    
			    willAcceptByDroping = true;
			    // We will drop the child[0]
			    if(SaarTest.logLevel <= 875) myPrint("multitree: " + "COOL: Accepted ANYCASTNEIGHBOR/ACCEPTBYDROPING request", 875);
			    
			    NodeHandle[] mychildren = stripeinfo[k].manager.getChildren();
			    if(SaarTest.logLevel <= 875) myPrint("multitree: " + "Stripe[" + k + "]" + " Sending UnsubscribeMsg to child " + mychildren[0], 875);
			    saarClient.endpoint.route(null, new UnsubscribeMsg(saarClient.endpoint.getLocalNodeHandle(),topic,k), mychildren[0]);
			    stripeinfo[k].manager.removeChild(mychildren[0]);
			    
			    break;
			}
		    
		    }
		    if(!willAcceptByDroping) {
			if(SaarTest.logLevel <= 875) myPrint("multitree: " + "anycast(mode=ANYCASTNEIGHBORMODE, anycastneighbormode=ACCEPTBYDROPING condition failed, ret=false) ", 875);
			return false;
		    }
		}
	    }
	}

    
      
	// We accept the anycast, add the new neighbor connection, and notify the anycast requestor
	boolean isPrimaryContributor = false;
	if(myContent.requestorsPrimaryStripeId == myContent.requestorsDesiredStripeId) {
	    isPrimaryContributor = true;
	}



	currstripe.manager.addChild(myContent.anycastRequestor, isPrimaryContributor, myContent.requestorsDegree, myContent.requestorsPrimaryStripeId);
	boolean parentViaPrimaryChildViolation = false; 
	if(currstripe.maximumOutdegree == 0) {
	    myContent.parentEstablishedViaPrimaryChildViolation = true;
	} else {
	    myContent.parentEstablishedViaPrimaryChildViolation = false;
	}
	

	myContent.setResponderDepth(currstripe.treeDepth);
	saarClient.endpoint.route(null, new MyAnycastAckMsg(myContent, saarClient.endpoint.getLocalNodeHandle(), saartopic.baseTopic), myContent.anycastRequestor);
    


	// Instead of sending the pathToRootinfo to the child rightaway, we will send it when we receive the ChildIsAliveMsg. This is because of a race condition where if the PathToRootInfoMsg reaches earlier than the MyAnycastAckMsg then the path information is disrgarded since it came from an unknown parent



	// Since our state has change we proactively invoke controlplaneUpdate to update local state on the local node. Note that this does not mean that the update will be sent to the parent immediately
	controlplaneUpdate(true); 


	return true;

    }


    
    public void grpSummary(SaarTopic saartopic, Topic topic, SaarContent content) {
	Topic myTopic = saartopic.baseTopic;	
	if(SaarTest.logLevel <= 850) myPrint("multitree: " + "multitreeclient.grpSummary(" + "saartopic:" + saartopic + ", topic:" + topic + ", saarcontent: " + content, 850);
	MultitreeContent myContent = (MultitreeContent) content;

	int topicNumber = myContent.tNumber; 
	if(tNumber != topicNumber) {
	    System.out.println("tNumber:" + tNumber + ", topicNumber:" + topicNumber);
	    System.out.println("ERROR: The tNumbers should match, else indicates some error in aggregation");
	    System.exit(1);
	}
	int[] grpAvailableSlots = new int[NUMSTRIPES];
	int[] grpConnectedStripeAvailableSlots = new int[NUMSTRIPES];
	int[] grpConnectedTotalAvailableSlots = new int[NUMSTRIPES];

	String grpAvailableSlotsString = "[";
	String grpConnectedStripeAvailableSlotsString = "[";
	String grpConnectedTotalAvailableSlotsString = "[";
	

	for(int stripeId=0; stripeId< NUMSTRIPES; stripeId++) {
	    grpAvailableSlots[stripeId] = myContent.currMaximumSlots[stripeId] - myContent.usedSlots[stripeId];
	    grpAvailableSlotsString = grpAvailableSlotsString + grpAvailableSlots[stripeId] + ",";
	    grpConnectedStripeAvailableSlots[stripeId] = myContent.connectedStripeAvailableSlots[stripeId];
	    grpConnectedStripeAvailableSlotsString = grpConnectedStripeAvailableSlotsString + grpConnectedStripeAvailableSlots[stripeId] + ",";
	    grpConnectedTotalAvailableSlots[stripeId] = myContent.connectedTotalAvailableSlots[stripeId];
	    grpConnectedTotalAvailableSlotsString = grpConnectedTotalAvailableSlotsString + grpConnectedTotalAvailableSlots[stripeId] + ",";


	}
	grpAvailableSlotsString = grpAvailableSlotsString  + "]";
	grpConnectedStripeAvailableSlotsString = grpConnectedStripeAvailableSlotsString  + "]";
	grpConnectedTotalAvailableSlotsString = grpConnectedTotalAvailableSlotsString  + "]";






	// We will choose the primary stripe based on the stripe that has overall minimum available slots (i.e currMaximumSlots[stripeId] - usedSlots[stripeId]. This code will be executed only for the non-multicast sources, for the source the chosenPrimaryStripe is set to true in the join() itself
	if(amSubscribed && !chosenPrimaryStripe) {
	    if(RESOURCEBASEDPRIMARYSELECTION) {
		int minStripeIndex = -1;
		int minAvailableSlots = Integer.MAX_VALUE;
		for(int stripeId=0; stripeId< NUMSTRIPES; stripeId++) {
		    if(grpAvailableSlots[stripeId] < minAvailableSlots) {
			minStripeIndex = stripeId;
			minAvailableSlots = grpAvailableSlots[stripeId];
		    }
		}
		//chosenStripeIndex = minStripeIndex;


		// Sep20-2007 modification : Amongst the stripes that lie amongst [minAvailableSlots, minAvailableSlots + THRESHOLD] we shall randomly choose one stripe. This is to dampen oscillations. Previously, say all stripes had resource=3, in that case the loop iteration would result in all nodes choosing the stripe0 
		Vector minresourceStripes = new Vector();
		for(int stripeId=0; stripeId < NUMSTRIPES; stripeId++) {
		    int diffval = Math.abs(grpAvailableSlots[stripeId] - minAvailableSlots);
		    if(diffval < 5) {
			minresourceStripes.add(new Integer(stripeId));
		    }
		}
		if(SaarTest.logLevel <= 875) myPrint("multitree: " + "numminresourcestripes: " + minresourceStripes.size() + " minStripeIndex: " + minStripeIndex, 875);
		int pos = rng.nextInt(minresourceStripes.size());
		chosenStripeIndex = ((Integer)minresourceStripes.elementAt(pos)).intValue();
		

	    } else {
		chosenStripeIndex = rng.nextInt(NUMSTRIPES);
	    }
	    int primaryMaximumOutdegree;
	    //if(!SPLITSTREAMCONFIG) {
	    primaryMaximumOutdegree = (int)(this.nodedegree*NUMSTRIPES);
	    //} else {
	    //primaryMaximumOutdegree = SPLITSTREAMOUTDEGREE;
	    //}
	    
	    stripeinfo[chosenStripeIndex].setMaximumOutdegree(primaryMaximumOutdegree);
	    
	    if(SaarTest.logLevel <= 880) myPrint("multitree: " + "MAXIMUMOUTDEGREE: " + stripeinfo[chosenStripeIndex].getMaximumOutdegree(), 880);
	    stripeinfo[chosenStripeIndex].setIsPrimary(true);

	    // We will also notify the parent in this stripe about our newly set maximumOutdegree, requestorsPrimaryStripeId

	    for(int stripeId=0; stripeId< NUMSTRIPES; stripeId++) {
		NodeHandle parent = stripeinfo[stripeId].manager.getParent();
		if(parent != null) {
		    // We will update this parent of our newly set isPrimaryContributor, maximumOutdegree
		    if(SaarTest.logLevel <= 875) myPrint("multitree: " + " Stripe[" + stripeId + "]" + " Sending UpdateChildInfoMsg to parent " + parent, 875);
		    saarClient.endpoint.route(null, new UpdateChildInfoMsg(saarClient.endpoint.getLocalNodeHandle(), myTopic, stripeId, stripeinfo[stripeId].getIsPrimary(), stripeinfo[stripeId].getMaximumOutdegree(), chosenStripeIndex), parent);	    
		    
		}
	    }
		
	    chosenPrimaryStripe = true;
	    if(SaarTest.logLevel <= 880) myPrint("multitree: " + "Chose primary stripe index: " + chosenStripeIndex, 880);
	}



	int saardepth = myContent.numScribeIntermediates;
	long currtime = getCurrentTimeMillis();
	double grpuStatic = 100*((double)myContent.overallUsedSlots)/((double)myContent.overallTotalSlots);

	String[] grpSlotsAtDepthString = new String[NUMSTRIPES];
	for(int stripeId =0; stripeId < NUMSTRIPES; stripeId ++) {
	    grpSlotsAtDepthString[stripeId] = myContent.getSlotsAtDepthString(stripeId);
	}

	String[] grpDepthAtNodeHavingChildOfDegreeString = new String[NUMSTRIPES];
	for(int stripeId =0; stripeId < NUMSTRIPES; stripeId ++) {
	    grpDepthAtNodeHavingChildOfDegreeString[stripeId] = myContent.getDepthAtNodeHavingChildOfDegreeString(stripeId);
	}



	// We will copy over the grpAvailableSlots information
	for(int stripeId=0; stripeId < NUMSTRIPES; stripeId++) {
	    stripeinfo[stripeId].grpAvailableSlots = grpAvailableSlots[stripeId];
	}

	// We will copy over the grpConnectedStripeAvailableSlots information
	for(int stripeId=0; stripeId < NUMSTRIPES; stripeId++) {
	    stripeinfo[stripeId].grpConnectedStripeAvailableSlots = grpConnectedStripeAvailableSlots[stripeId];
	}


	// We will copy over the grpConnectedTotalAvailableSlots information
	for(int stripeId=0; stripeId < NUMSTRIPES; stripeId++) {
	    stripeinfo[stripeId].grpConnectedTotalAvailableSlots = grpConnectedTotalAvailableSlots[stripeId];
	}


	// We will copy over the grpAvailableSlotsAtDepth information
	for(int stripeId=0; stripeId < NUMSTRIPES; stripeId++) {
	    for(int i=0; i< MultitreeContent.MAXSATURATEDDEPTH; i++) {
		stripeinfo[stripeId].grpAvailableSlotsAtDepth[i] = myContent.availableSlotsAtDepth[stripeId][i];
	    }
	}

	// We will copy over the grpDepthAtNodeHavingChildOfDegree information
	for(int stripeId=0; stripeId < NUMSTRIPES; stripeId++) {
	    for(int i=0; i< (MultitreeContent.MAXCHILDDEGREE + 1); i++) {
		stripeinfo[stripeId].grpDepthAtNodeHavingChildOfDegree[i] = myContent.depthAtNodeHavingChildOfDegree[stripeId][i];
	    }
	}

	String grpCumulativeStreamingQualityString = "[";
	for(int numRedundantStripes = 0; numRedundantStripes < NUMSTRIPES; numRedundantStripes ++) {
	    grpCumulativeStreamingQualityString = grpCumulativeStreamingQualityString + myContent.cumulativeStreamingQuality[numRedundantStripes] + ",";
	}
	grpCumulativeStreamingQualityString = grpCumulativeStreamingQualityString + "]";	


	String grpStripeStreamingQualityString = "[";
	for(int stripeId=0; stripeId < NUMSTRIPES; stripeId++) {
	    grpStripeStreamingQualityString = grpStripeStreamingQualityString + myContent.streamingQuality[stripeId] + ",";

	}
	grpStripeStreamingQualityString = grpStripeStreamingQualityString + "]";


	// We will copy over the allowNonContributors information
	String grpAllowNonContributorsString = "[";
	for(int stripeId=0; stripeId < NUMSTRIPES; stripeId++) {
	    stripeinfo[stripeId].grpAllowNonContributors = myContent.allowNonContributors[stripeId];
	    grpAllowNonContributorsString = grpAllowNonContributorsString + myContent.allowNonContributors[stripeId] + ",";
	}
	grpAllowNonContributorsString = grpAllowNonContributorsString + "]";



	String zerodegreeChildString = "[";
	for(int stripeId=0; stripeId < NUMSTRIPES; stripeId++) {
	    zerodegreeChildString = zerodegreeChildString +  myContent.depthAtNodeHavingChildOfDegree[stripeId][0] + ",";

	}
	zerodegreeChildString = zerodegreeChildString + "]";


	String s = "sourceIsBroadcasting(" + "tNumber: " + topicNumber+ "," + currtime + ", saardepth: " + saardepth + ", sourceBroadcastSeq: " + myContent.sourceBroadcastSeq+ ", grpSize: " + myContent.descendants + ", grpTotalSecondaryChildren: " + myContent.totalSecondaryChildren + ", grpStripeStreamingQuality: " + grpStripeStreamingQualityString +  ", grpAvailableSlots: " + grpAvailableSlotsString + ", grpConnectedStripeAvailableSlots: " + grpConnectedStripeAvailableSlotsString + ", grpConnectedTotalAvailableSlots: " + grpConnectedTotalAvailableSlotsString + ", grpCumulativeStreamingQuality: " + grpCumulativeStreamingQualityString + ", grpuStatic: " + grpuStatic + ", grpAllowNonContributorsString: " + grpAllowNonContributorsString + ", grpDepthAtNodeHavingChildOfDegree-zero: " + zerodegreeChildString ;
	
	//for(int stripeId =0; stripeId < NUMSTRIPES; stripeId ++) {
	//  s = s + " {{{ Stripe[" + stripeId + "]: ";
	//  s = s + ", grptreeDepth: " + myContent.treeDepth[stripeId] +  ", grpSlotsAtDepth: " + slotsAtDepthString[stripeId];
	//  s = s + " }}}";

	//}

	if(SaarTest.logLevel <= 850) myPrint("multitree: " + s, 850);
	
	rootSeqNumRecv = myContent.sourceBroadcastSeq;	    
	if(firstPktSequence == -1) {
	    firstPktSequence = myContent.sourceBroadcastSeq;
	    firstPktTime = currtime;
	}

	int selfestimatedRWindow = firstPktSequence + (int)((currtime - firstPktTime)/PUBLISHPERIOD);
	int controlPktRWindow = rootSeqNumRecv;
	//if(SaarTest.logLevel <= 875) myPrint("multitree: " + "RWINDOW-ESTIMATION:" + "controlPkt:" + controlPktRWindow + ", selfestimate: " + selfestimatedRWindow, 875); // This is because we changed to use dampening of upward/downward updates
	
    }
    

    public void recvAnycastFail(SaarTopic saartopic, Topic topic, NodeHandle failedAtNode, SaarContent content) {
	if(SaarTest.logLevel <= 880) myPrint("multitree: " + "multitreeclient.recvAnycastFail(" + "saartopic:" + saartopic + ", topic:" + topic + ", anycastId:" + content.anycastGlobalId + ")", 880);

    }


    public void recvDataplaneMessage(SaarTopic saartopic, Topic topic, SaarDataplaneMessage message) {
	if(message instanceof MyAnycastAckMsg) {
	    // We might get an ack to a anycast request we had sent out before unsubscribing, we do not react upon such acks
	    if(amSubscribed) {

		Topic myTopic = saartopic.baseTopic;	
		MyAnycastAckMsg  ackMsg = (MyAnycastAckMsg)message;
		if(SaarTest.logLevel <= 880) myPrint("multitree: " + " Received " + ackMsg, 880);
		long currtime = getCurrentTimeMillis();
		MultitreeContent content = (MultitreeContent) ackMsg.content;
		int stripeId = content.requestorsDesiredStripeId;

		expbackoffInterval[stripeId] = 500;
		maxExpbackoffInterval[stripeId] = 500;


		StripeInformation currstripe = stripeinfo[stripeId];
		boolean acceptedNewParent = false;
		long difftime = currtime - currstripe.manager.getLastReverseHeartbeatTimeParent();
		NodeHandle prevparent = currstripe.manager.getParent();
		    

		
		if((currstripe.manager.getParent() == null) || (difftime >= SaarClient.NEIGHBORDEADTHRESHOLD)) {
		    // We do not have a parent, so we set the responder as parent
		    currstripe.manager.setParent(ackMsg.getSource(), content.parentEstablishedViaPrimaryChildViolation);
		    acceptedNewParent = true;
		} else {

		    // Here we might replace a functional paent if the new parent is better

		    
		    // We will set the responder in response to ANYCASTFORTREEIMPROVEMENT/ANYCASTFORCONNECTIONIMPROVEMENT/ANYCASTFORPREMPDEGREEPUSHDOWN . TREEIMPROVEMENT (if its depth is better than current depth. We ensure that the node is connected to ensure that the current depth is well defined) or CONNECTIONIMPROVEMENT (if the current parent connection is a secondary connection)  
		    int newExpectedDepth = content.responderDepth + 1;		
		    currstripe.treeDepth = currstripe.manager.getTreeDepth();
		    currstripe.isConnected = evaluateIsConnected(stripeId);
		    if(currstripe.isConnected && (newExpectedDepth < currstripe.treeDepth) && (!content.parentEstablishedViaPrimaryChildViolation)) {
			// We will switch to the newparent at better depth, tree depth improvements are established using ONLYPRIMARY anycastneighbormode
			if(SaarTest.logLevel <= 875) myPrint("multitree: " + " Stripe[" + stripeId + "]" + " Switching to better parent via ANYCASTFORTREEIMPROVEMENT , prevDepth: " + currstripe.treeDepth + " newExpectedDepth: " + newExpectedDepth + " PSWITCH dueto mode:" + content.mode, 875);
			
			currstripe.manager.setParent(ackMsg.getSource(), content.parentEstablishedViaPrimaryChildViolation);
			acceptedNewParent = true;

			

		    } else {
		       
			if(currstripe.isConnected && (currstripe.manager.getParentEstablishedViaPrimaryChildViolation() && !content.parentEstablishedViaPrimaryChildViolation)) {
			    // We will switch to the newparent
			    if(SaarTest.logLevel <= 875) myPrint("multitree: " + " Stripe[" + stripeId + "]" + " Switching to better parent via ANYCASTFORCONNECTIONIMPROVEMENT, prevConnection: " + currstripe.manager.getParentEstablishedViaPrimaryChildViolation() + " newConnection: " + content.parentEstablishedViaPrimaryChildViolation + " prevDepth: " + currstripe.treeDepth + " newExpectedDepth: " + newExpectedDepth, 875);
			    currstripe.manager.setParent(ackMsg.getSource(), content.parentEstablishedViaPrimaryChildViolation);
			    acceptedNewParent = true;
			}
		    }

		}
		
		// We will send an ChilsIsAliveMsg instantly to denote acceptance of the conenction
		if(acceptedNewParent) {
		    
		    if((prevparent != null) && !prevparent.equals(ackMsg.getSource())) {
			if(SaarTest.logLevel <= 875) myPrint("multitree: " + "Stripe[" + stripeId + "]" + " Sending UnsubscribeMsg to prevparent " + prevparent, 875);
			saarClient.endpoint.route(null, new UnsubscribeMsg(saarClient.endpoint.getLocalNodeHandle(),topic,stripeId), prevparent);
		    }

		    if(content.parentEstablishedViaPrimaryChildViolation) {
			if(SaarTest.logLevel <= 850) myPrint("multitree: " + " Parent for Stripe[ " + stripeId + "]" + " has been established via primaryChildViolation", 875);
		    }
		    NodeHandle myparent = currstripe.manager.getParent();
		    if(SaarTest.logLevel <= 850) myPrint("multitree: " + " Stripe[" + stripeId + "]" + " Sending ChildIsAliveMsg to parent " + myparent, 850);
		    saarClient.endpoint.route(null, new ChildIsAliveMsg(saarClient.endpoint.getLocalNodeHandle(), myTopic, stripeId), myparent);	    
		}
		
		
	    }
	}

	if(message instanceof PathToRootInfoMsg) {
	    PathToRootInfoMsg pathMsg = (PathToRootInfoMsg) message;
	    int stripeId = pathMsg.stripeId;
	    StripeInformation currstripe = stripeinfo[stripeId];
	    if(SaarTest.logLevel <= 850) myPrint("multitree: " + " Stripe[" + stripeId + "]" + " Received " + pathMsg, 850);
	    if(pathMsg.getSource().equals(currstripe.manager.getParent())) {
		if(!loopDetected(pathMsg.getPathToRoot(), stripeId)) {
		    currstripe.manager.appendPathToRoot(pathMsg.getPathToRoot());
		    currstripe.manager.setLastReverseHeartbeatTimeParent(getCurrentTimeMillis());
		} else {
		    if(SaarTest.logLevel <= 875) myPrint("multitree: " + "ERROR: Stripe[" + stripeId + "]" + " A loop has been detected in the dataplane, pathToRoot: " + pathMsg.getPathToRoot(), 875);
		    // To break the loop We will unsubscribe from our parent, setParent as null. We do not issue anycast for repair here, the neighborMaintenance should trigger off to do this.
		    NodeHandle prevparent = currstripe.manager.getParent();
		    if(prevparent!= null) {
			if(SaarTest.logLevel <= 875) myPrint("multitree: " + "Stripe[" + stripeId + "]" + " Sending UnsubscribeMsg to prevparent " + prevparent, 875);
			saarClient.endpoint.route(null, new UnsubscribeMsg(saarClient.endpoint.getLocalNodeHandle(),topic, stripeId), prevparent);
			currstripe.manager.setParent(null, false);
		    }
		}

	    } else {
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("multitree: " + "Stripe[" + stripeId + "]" + " Received PathToRootInfoMsg from unknown parent " + pathMsg.getSource(), Logger.WARNING);
	    }

	}


	if(message instanceof RequestChildInfoMsg) {
	    Topic myTopic = saartopic.baseTopic;	
	    RequestChildInfoMsg rMsg = (RequestChildInfoMsg) message;
	    int stripeId = rMsg.stripeId;
	    StripeInformation currstripe = stripeinfo[stripeId];
	    if(SaarTest.logLevel <= 850) myPrint("multitree: " + "Stripe[" + stripeId + "] " + " Received " + rMsg, 850);
	    if(chosenPrimaryStripe && rMsg.getSource().equals(currstripe.manager.getParent())) {
		// We will update this parent of our newly set isPrimaryContributor, maximumOutdegree
		if(SaarTest.logLevel <= 875) myPrint("multitree: " + " Stripe[" + stripeId + "]" + " Sending UpdateChildInfoMsg to parent " + currstripe.manager.getParent(), 875);
		saarClient.endpoint.route(null, new UpdateChildInfoMsg(saarClient.endpoint.getLocalNodeHandle(), myTopic, stripeId, currstripe.getIsPrimary(), currstripe.getMaximumOutdegree(), chosenStripeIndex), currstripe.manager.getParent());	    
		

	    } else {
		if(SaarTest.logLevel <= 875) myPrint("multitree: " + "Not entertaining RequestChildInfoMsg since chosenPrimaryStripe= " + chosenPrimaryStripe, 875);
	    }

	}

	if(message instanceof UpdateChildInfoMsg) {
	    UpdateChildInfoMsg uMsg = (UpdateChildInfoMsg) message;
	    int stripeId = uMsg.stripeId;
	    StripeInformation currstripe = stripeinfo[stripeId];
	    if(SaarTest.logLevel <= 850) myPrint("multitree: " + "Stripe[" + stripeId + "] " + " Received " + uMsg, 850);
	    if(currstripe.manager.containsChild(uMsg.getSource())) {
		currstripe.manager.updateChild(uMsg.getSource(), uMsg.getIsPrimaryContributor(), uMsg.getChildDegree(), uMsg.getChildsPrimaryStripeId());


	    } else {
		if(SaarTest.logLevel <= 875) myPrint("multitree: " + "Received UpdateChildInfoMsg from unknown child", 875);
	    }

	}



	



	if(message instanceof ChildIsAliveMsg) {
	    ChildIsAliveMsg hMsg = (ChildIsAliveMsg) message;
	    int stripeId = hMsg.stripeId;
	    StripeInformation currstripe = stripeinfo[stripeId];
	    if(SaarTest.logLevel <= 850) myPrint("multitree: " + "Stripe[" + stripeId + "] " + " Received " + hMsg, 850);
	    if(currstripe.manager.containsChild(hMsg.getSource())) {
		
		// When the child acknowledges the establishment of the parent-child link by sending the ChildIsAliveMsg, we send the child the currpathtoRoot. Note that we do not send this rightaway when we send the MyAnycastAckMsg because of a race condition of the PathToRoot reaching the child before the AnycastAckMsg and the child disregarding the message thinking it came from an unknown parent
		if(!currstripe.manager.gotChildAck(hMsg.getSource())) {
		    if(SaarTest.logLevel <= 875) myPrint("multitree: " + " Sending PathToRootInfoMsg[" + stripeId + "] to newly added child " + hMsg.getSource(), 875);
		    saarClient.endpoint.route(null, new PathToRootInfoMsg(saarClient.endpoint.getLocalNodeHandle(), topic, currstripe.manager.getPathToRoot(), stripeId), hMsg.getSource());	  
		    
		}

		currstripe.manager.setLastForwardHeartbeatTimeChild(hMsg.getSource(),getCurrentTimeMillis());
	    } else {
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("multitree: " + "Stripe[" + stripeId + "]" + " Received ChildIsAliveMsg from unknown child " + hMsg.getSource(), Logger.WARNING);
	    }

	}

	
	if(message instanceof UnsubscribeMsg) {
	    // We have the same unsubscribe msg for a parent as well as a child unsubscribing
	    UnsubscribeMsg uMsg = (UnsubscribeMsg) message;
	    int stripeId = uMsg.stripeId;
	    StripeInformation currstripe = stripeinfo[stripeId]; 
	    if(SaarTest.logLevel <= 875) myPrint("multitree: " + " Stripe[" + stripeId + "]" + " Received " + uMsg, 875);
	    if(uMsg.getSource().equals(currstripe.manager.getParent())) {
		currstripe.manager.setParent(null, false);

		int anycastneighbormode;
		boolean denyAnycast = false;
		
		if(currstripe.grpConnectedStripeAvailableSlots != 0) {
		    anycastneighbormode = ONLYPRIMARY;			
		} else if(currstripe.grpConnectedTotalAvailableSlots != 0) {
		    anycastneighbormode = ALLOWSECONDARY;
		} else {
		    int overallUsedSlots = 0;
		    int overallTotalSlots = 0;
		    for(int k=0; k < NUMSTRIPES; k ++) {
			overallUsedSlots = overallUsedSlots + stripeinfo[k].manager.numChildren();
			overallTotalSlots = overallTotalSlots + stripeinfo[k].maximumOutdegree;
		    }
		    
		    
		    anycastneighbormode = ACCEPTBYDROPING;
		    if((!currstripe.isPrimary) || (overallUsedSlots == overallTotalSlots)) {
			
			denyAnycast = true;
		    }
		}
		
		if(!denyAnycast) {
		    NodeHandle anycastHint = null;
		    SaarContent reqContent = new MultitreeContent(MultitreeContent.ANYCASTNEIGHBOR, topicName, tNumber, currstripe.manager.getPathToRoot(), currstripe.manager.getTreeDepth(), stripeId, anycastneighbormode, chosenStripeIndex, currstripe.maximumOutdegree);
		    saarClient.reqAnycast(tNumber,reqContent, anycastHint, 2, 1, 10);
		}		
		
	    }
	    if(currstripe.manager.containsChild(uMsg.getSource())) {
		currstripe.manager.removeChild(uMsg.getSource());
	    } 
	}



	
	if(message instanceof PremptChildMsg) { // sent from parent to the child that he prempted
	    Topic myTopic = saartopic.baseTopic;
	    // We have the same unsubscribe msg for a parent as well as a child unsubscribing
	    PremptChildMsg premptMsg = (PremptChildMsg) message;
	    int stripeId = premptMsg.stripeId;
	    StripeInformation currstripe = stripeinfo[stripeId];
	    NodeHandle newProspectiveParent = premptMsg.newProspectiveParent; 
	    NodeHandle prevparent = currstripe.manager.getParent();
	    if(SaarTest.logLevel <= 875) myPrint("multitree: " + " Stripe[" + stripeId + "]" + " Received " + premptMsg, 875);
	    if(premptMsg.getSource().equals(prevparent)) {
		// We will send him an UnsubscribeMsg
		if(SaarTest.logLevel <= 875) myPrint("multitree: " + "Stripe[" + stripeId + "]" + " Sending UnsubscribeMsg to prevparent " + prevparent, 875);
		saarClient.endpoint.route(null, new UnsubscribeMsg(saarClient.endpoint.getLocalNodeHandle(),topic, stripeId), prevparent);
		
		currstripe.manager.setParent(null, false);
		// We will send him a message to accept us
		if(SaarTest.logLevel <= 875) myPrint("multitree: " + " Sending AcceptPremptedChildMsg to " + newProspectiveParent, 875);
		saarClient.endpoint.route(null, new AcceptPremptedChildMsg(saarClient.endpoint.getLocalNodeHandle(), myTopic, stripeId, currstripe.maximumOutdegree, chosenStripeIndex), newProspectiveParent);	

	    }
	}


	if(message instanceof AcceptPremptedChildMsg) { // request msg sent from prempted child to the node that took its slot 
	    Topic myTopic = saartopic.baseTopic;
	    // We have the same unsubscribe msg for a parent as well as a child unsubscribing
	    AcceptPremptedChildMsg acceptPremptedChildMsg = (AcceptPremptedChildMsg) message;
	    NodeHandle premptedChild = acceptPremptedChildMsg.getSource();
	    int stripeId = acceptPremptedChildMsg.stripeId;
	    StripeInformation currstripe = stripeinfo[stripeId];
	    boolean acceptRequest = true;
	    // We will check the child addition constraints once
	    

	    if(currstripe.manager.numChildren() >= currstripe.maximumOutdegree) { // If we do reservation then we must check agaist the reservation
		
		if(SaarTest.logLevel <= 875) myPrint("multitree: " + "acceptPremptedChild(uStaticLimitReachedBasedOnStripesTotalSlots, ret=false) ", 875);
		
		acceptRequest = false;
	    }
	
	    


	    if(saarClient.endpoint.getLocalNodeHandle().equals(premptedChild)) {
		// We do not accept the anycast made by ourself
		if(SaarTest.logLevel <= 875) myPrint("multitree: " + "acceptPremptedChild(self=requestor, ret=false)", 875);
		acceptRequest = false;
		
	    }
	    
	    if(currstripe.manager.containsChild(premptedChild)) {
		if(SaarTest.logLevel <= 875) myPrint("multitree: " + "acceptPremptedChild(self=requestorIsExistingChild, ret=false) ", 875);
		acceptRequest = false;
		
	    }


	
	    // We check to see if we are getting good performance 
	    currstripe.isConnected = evaluateIsConnected(stripeId);
	    if(!currstripe.isConnected) {
		if(SaarTest.logLevel <= 875) myPrint("multitree: " + "acceptPremptedChild(poorInstantaneousQuality, ret=false) ", 875);
		acceptRequest = false;
	    }
	    

	    
	    if(currstripe.manager.hasLoops(premptedChild.getId())) {
		if(SaarTest.logLevel <= 875) myPrint("multitree: " + "acceptPremptedChild(hasLoops, ret=false) ", 875);
		acceptRequest = false;
		
	    }

	    if(acceptRequest) {
		// We accept the anycast, add the new neighbor connection, and notify the anycast requestor
		boolean isPrimaryContributor = false;
		if(acceptPremptedChildMsg.premptedChildPrimaryStripeId == stripeId) {
		    isPrimaryContributor = true; // This should not happen if a zero degree child is prempted
		}
		
		if(SaarTest.logLevel <= 875) myPrint("multitree: " + " Accepting prempted child " + premptedChild, 875);
		currstripe.manager.addChild(premptedChild, isPrimaryContributor, acceptPremptedChildMsg.premptedChildDegree, acceptPremptedChildMsg.premptedChildPrimaryStripeId);
		saarClient.endpoint.route(null, new PremptedChildWasAcceptedMsg(saarClient.endpoint.getLocalNodeHandle(), myTopic, stripeId), premptedChild);	

	    } else {
		if(SaarTest.logLevel <= 875) myPrint("multitree: " + " Denying prempted child", 875);
		// We will send him a message deny acceptance
		saarClient.endpoint.route(null, new PremptedChildWasDeniedMsg(saarClient.endpoint.getLocalNodeHandle(), myTopic, stripeId), premptedChild);	


	    }


	}


	if(message instanceof PremptedChildWasAcceptedMsg) { // msg from node that took the slot ->  the prempted child
	    Topic myTopic = saartopic.baseTopic;
	    // We have the same unsubscribe msg for a parent as well as a child unsubscribing
	    PremptedChildWasAcceptedMsg premptedChildWasAcceptedMsg = (PremptedChildWasAcceptedMsg) message;
	    int stripeId = premptedChildWasAcceptedMsg.stripeId;
	    StripeInformation currstripe = stripeinfo[stripeId];
	    if(SaarTest.logLevel <= 875) myPrint("multitree: " + " Received PremptedChildWasAcceptedMsg from " + premptedChildWasAcceptedMsg.getSource(), 875);
	    NodeHandle prevparent = currstripe.manager.getParent();

	    if(prevparent != null) {
		if(SaarTest.logLevel <= 875) myPrint("multitree: " + " WARNING: Acquired parent via anycast when prempted BEFORE receiving PremptedChildWasAcceptedMsg", 875);

		// We will send this previous parent an unsubscribe msg
		if(SaarTest.logLevel <= 875) myPrint("multitree: " + "Stripe[" + stripeId + "]" + " Sending UnsubscribeMsg to prevparent " + prevparent, 875);
		saarClient.endpoint.route(null, new UnsubscribeMsg(saarClient.endpoint.getLocalNodeHandle(),myTopic, stripeId), prevparent);

	    }
	    currstripe.manager.setParent(premptedChildWasAcceptedMsg.getSource(), false); // the way of accepting prempted child ensures that there is no primary stripe violation

	    NodeHandle myparent = currstripe.manager.getParent();

	    if(SaarTest.logLevel <= 850) myPrint("multitree: " + " Stripe[" + stripeId + "]" + " Sending ChildIsAliveMsg to parent " + myparent, 850);
	    saarClient.endpoint.route(null, new ChildIsAliveMsg(saarClient.endpoint.getLocalNodeHandle(), myTopic, stripeId), myparent);	    	    
	    

	}


	if(message instanceof PremptedChildWasDeniedMsg) { // msg from node that took the slot ->  the prempted child
	    Topic myTopic = saartopic.baseTopic;
	    // We have the same unsubscribe msg for a parent as well as a child unsubscribing
	    PremptedChildWasDeniedMsg premptedChildWasDeniedMsg = (PremptedChildWasDeniedMsg) message;
	    if(SaarTest.logLevel <= 875) myPrint("multitree: " + " Received PremptedChildWasDeniedMsg from " + premptedChildWasDeniedMsg.getSource(), 875);

	}





	
	if(message instanceof PublishMsg) {
	    long currtime = getCurrentTimeMillis();
	    Topic myTopic = saartopic.baseTopic;
	    PublishMsg publishMsg = (PublishMsg) message;
	    int stripeId = publishMsg.stripeId;
	    StripeInformation currstripe = stripeinfo[stripeId];
	    if(SaarTest.logLevel <= 850) myPrint("multitree: " + " Stripe[" + stripeId + "]" + " Received " + publishMsg, 850);
	    if(publishMsg.getSource().equals(currstripe.manager.getParent())) {

		currstripe.manager.setLastReverseHeartbeatTimeParent(getCurrentTimeMillis());

		Block recvBlock = publishMsg.getBlock();
		int depth = recvBlock.getDepth();
		recvBlock.addToPath(saarClient.bindIndex, saarClient.jvmIndex, saarClient.vIndex);
		currstripe.blocks.put(new Integer(recvBlock.seqNum), recvBlock);
		int numPkts = 0; 
		if(currstripe.numSequence.containsKey(new Integer(recvBlock.seqNum))) {
		    numPkts = ((Integer)currstripe.numSequence.get(new Integer(recvBlock.seqNum))).intValue();
		} 
		boolean firstinstanceofpacket = false; 
		if(numPkts == 0) {
		    firstinstanceofpacket = true;
		}
		currstripe.numSequence.put(new Integer(recvBlock.seqNum), new Integer(numPkts + 1));
		multitreeDeliver(stripeId, recvBlock.seqNum, depth, publishMsg.getSource(), numPkts + 1, recvBlock); 
		if(firstinstanceofpacket) {

		    currstripe.lastParentHeartbeatSentTime = currtime;
		    // We will recursively push out the block to our children
		    NodeHandle handles[] = currstripe.manager.getChildren();
		    for(int i=0; i < handles.length; i++) {			
			if(shouldForwardConsideringLinkLoss()) {
			    numpktssent++;
			    if(SaarTest.logLevel <= 850) myPrint("multitree: " + "Stripe[" + stripeId + "] " + " Publishing Block to child " + handles[i], 850);
			    saarClient.endpoint.route(null, new PublishMsg(saarClient.endpoint.getLocalNodeHandle(), myTopic, recvBlock, stripeId), handles[i]);	    
			} else {
			    if(SaarTest.logLevel <= 850) myPrint("multitree: " +  "Stripe[" + stripeId + "] " + " , Not Publishing Block (because of link loss) to child " + handles[i] , 850);
			}
		    }
		} else {
		    if(SaarTest.logLevel <= 850) myPrint("multitree: " +  "No Publishing Block because it is not first instanceofpacket " + ", numpkts: " + numPkts + "Stripe[" + stripeId + "] " + " , seqNum: " + recvBlock.seqNum , 850);
		}
		
	    } else {
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("multitree: " + "Stripe[" + stripeId + "]" + " Received PublishMsg from unknown parent " + publishMsg.getSource(), Logger.WARNING);
	    }
	    
	}
	

    }



   public boolean shouldForwardConsideringLinkLoss() {
	int valchosen =  linklossRng.nextInt(100); 
	if(valchosen >= (100* SaarClient.FIXEDAPPLICATIONLOSSPROBABILITY)) {
	    return true;
	} else {
	    return false;
	}

    }



   // todo: remove this when it's implemented
   boolean firstTimeRN = true;
    public void recomputeNodedegree() {
	double prevNodedegree  = nodedegree;
	int prevPrimaryMaximumOutdegree = -1;


	if(chosenStripeIndex != -1) {
	    prevPrimaryMaximumOutdegree = stripeinfo[chosenStripeIndex].getMaximumOutdegree();
	}

	int totalchildren = 0;
	for(int stripeId= 0; stripeId < NUMSTRIPES; stripeId ++) {
	    StripeInformation currstripe = stripeinfo[stripeId];
	    
	    NodeHandle[] mychildren = currstripe.manager.getChildren();
	    totalchildren = totalchildren + mychildren.length;
	}
  // This is the overhead that comes from the headers in the data pkts. We found this to be significant when the node has many children and is publishing at a finer rate (i.e many pkts)
	double pktheaderri = 1.0; 
	if (firstTimeRN && saarClient.logger.level <= Logger.WARNING) {
	  saarClient.logger.log("recomputeNodedegree() is not implemented");
	  firstTimeRN = false;
	}
	
//	pktheaderri = ((double)(totalchildren * NUMFRAGMENTSPERSECPERCHILD * rice.pastry.direct.DirectPastryNode.SIZEMSGHEADER)) / ((double)SaarClient.STREAMBANDWIDTHINBYTES); 


	dataplaneriusedbycontrol = saarClient.getDataplaneRIUsedByControl() ;
	nodedegree = nodedegreeControlAndData - dataplaneriusedbycontrol - pktheaderri;
	if(nodedegree < 0) {
	    nodedegree = 0;
	}


	if(amMulticastSource) {
	    for(int stripeId=0; stripeId < NUMSTRIPES; stripeId++) {
		stripeinfo[stripeId].setMaximumOutdegree((int)this.nodedegree);
	    }
	    if(SaarTest.logLevel <= 875) myPrint("multitree:  recomputeNodedegree() " + " nodedegreeControlAndData: " + nodedegreeControlAndData +  " dataplaneriusedbycontrol: " + dataplaneriusedbycontrol +  " pktheaderri: " + pktheaderri + " nodedegree: " + nodedegree  + " sourceOutdegreeOnEachStripe: " + ((int)nodedegree), 875);
	} else 	if(chosenStripeIndex != -1) {
	    int primaryMaximumOutdegree = prevPrimaryMaximumOutdegree;
	    double riforchild = ((double)1)/ ((double)NUMSTRIPES);
	    double spareri = nodedegree - (riforchild  * prevPrimaryMaximumOutdegree);
	    if(spareri >  (1.05 * riforchild)) {
		primaryMaximumOutdegree = prevPrimaryMaximumOutdegree + 1; 
	    }else if(spareri < (0.01 * riforchild)) {
		primaryMaximumOutdegree = prevPrimaryMaximumOutdegree - 1; 
	    }

	    stripeinfo[chosenStripeIndex].setMaximumOutdegree(primaryMaximumOutdegree);
	
	
	    if(SaarTest.logLevel <= 875) myPrint("multitree:  recomputeNodedegree() " + " nodedegreeControlAndData: " + nodedegreeControlAndData +  " dataplaneriusedbycontrol: " + dataplaneriusedbycontrol +  " pktheaderri: " + pktheaderri + " nodedegree: " + nodedegree  +  " primaryMaximumOutdegree: " + primaryMaximumOutdegree + " spareri: " + spareri , 875);
	    if(primaryMaximumOutdegree != prevPrimaryMaximumOutdegree) {
		if(SaarTest.logLevel <= 880) myPrint("multitree:  Resetting primaryMaximumOutdegree: " + primaryMaximumOutdegree + " prevval: " + prevPrimaryMaximumOutdegree + " nodedegree: " + nodedegree + " dataplaneriusedbycontrol: " + dataplaneriusedbycontrol +  " pktheaderri: " + pktheaderri, 880);
	    }

	}


    }
 


    // todo: remove this when it's implemented
    boolean firstTimeNM = true;
    public void neighborMaintenance() {

	// In the HybridDEBUG setting the trees will not be used, so why establish them
	if((!amMulticastSource) && SaarClient.HYBRIDDEBUG) {
	    return;
	}

	Topic myTopic = saartopic.baseTopic;	

	// Sep19-2007 - We modified this to issueAnycast without waiting for the grpSummary, since the zaptimes otherwise were high

	if(!AVOIDWAITFORGRPUPDATE) {
	    if(rootSeqNumRecv == -1) {
		//The node first needs to start receiving the aggregate grp information inorder to decide if it should make an anycast violating primaryChildConstraint or not
		if(SaarTest.logLevel <= 875) myPrint("multitree: " + "Postponing neighborMaintenance() since grpSummary() not received yet", 875);
		return;
		
	    }
	}

	long currtime = getCurrentTimeMillis();

	// This interval is 1 sec and independent of the publish period
	if((currtime - lastNeighborMaintenanceInvokationTime) < 250) {  // Updted 10 250 from 1000 onOct02-2007 since we are using abrupt-leaves
	    if(SaarTest.logLevel <= 850) myPrint("multitree: " + " Skipping neighborMaintenance()", 850);
	    return;
	}
	lastNeighborMaintenanceInvokationTime = currtime;

	if(SaarTest.logLevel <= 875) myPrint("multitree: " + "neighborMaintenance()", 875);
	recomputeNodedegree();

	//if(ENABLEPRIMARYFRAGMENTRECONSTRUCTION) {
	//  missingPrimaryFragmentReconstruction();
	//  pushReconstructedFragments();
	//}

	int totalchildren = 0;
	for(int stripeId= 0; stripeId < NUMSTRIPES; stripeId ++) {
	    StripeInformation currstripe = stripeinfo[stripeId];
	    
	    NodeHandle[] mychildren = currstripe.manager.getChildren();
	    totalchildren = totalchildren + mychildren.length;
	}

	// This is the overhead that comes from the headers in the data pkts. We found this to be significant when the node has many children and is publishing at a finer rate (i.e many pkts)
  double pktheaderri = 1.0; 
  if (firstTimeNM && saarClient.logger.level <= Logger.WARNING) {
    saarClient.logger.log("neighborMaintenance() is not implemented");
    firstTimeNM = false;
  }
	//pktheaderri = ((double)(totalchildren * NUMFRAGMENTSPERSECPERCHILD * rice.pastry.direct.DirectPastryNode.SIZEMSGHEADER)) / ((double)SaarClient.STREAMBANDWIDTHINBYTES); 

	double bandwidthcommitment = ((double)totalchildren)/((double)NUMSTRIPES);
	double foregroundreservedri = bandwidthcommitment + pktheaderri; 

	// We will report the foreground reservation to the channelviewer	
	saarClient.viewer.setForegroundReservedRI(foregroundreservedri);



	if(SaarTest.logLevel <= 880) myPrint("multitree: " + "BWUtilization: Nodedegree: " + nodedegree + " dataplaneriusedbycontrol: " + dataplaneriusedbycontrol + " pktheaderri: " + pktheaderri + "  numchildren: " + totalchildren +  " foregroundreservedri: " + foregroundreservedri + " published: " + numpktssent, 880);	
	numpktssent = 0;




	// Important: We added this recently after having adjustable totalmaximumOutdegree
	// If total children exceeds the total degree drop children
	int[] overallSlots = getOverallUsedAndTotalSlots();
	if((totalchildren > 0) && (overallSlots[0] > overallSlots[1])) {
	    int numtodrop = overallSlots[0] - overallSlots[1];
	    if(SaarTest.logLevel <= 880) myPrint("multitree: WARNING: TotalMaximumOutdegree violated,numtodrop: " + numtodrop, 880);
	    //int numdropped = 0;
	    // We will just drop from the primary stripe for now, ideally it should be a secondary child		
	    //while(numdropped < numtodrop) {  // For now we drop only one child at a time
	    boolean chosenvictimstripe = false;
	    int stripeToDropFrom = -1;
	    int count = 0;
	    while((count < 20) && !chosenvictimstripe ) {
		count ++;
		stripeToDropFrom = rng.nextInt(NUMSTRIPES);
		//System.out.println("stripeToDropFrom: " + stripeToDropFrom);
		if(SaarTest.logLevel <= 875) myPrint("multitree: stripeToDropFrom: " + stripeToDropFrom, 875);
		if(stripeinfo[stripeToDropFrom].manager.numChildren() > 0) {
		    chosenvictimstripe = true;
		}
	    }
	    if(stripeToDropFrom == -1) {
		
		System.out.println("WARNING: stripeToDropFrom should not be -1 here since the node already has children");
		if(SaarTest.logLevel <= 880) myPrint("multitree: WARNING: stripeToDropFrom should not be -1 here since the node already has children" , 880);	
	    } else {
		NodeHandle droppedchild = stripeinfo[stripeToDropFrom].manager.removeRandomChild();
		if(SaarTest.logLevel <= 880) myPrint("multitree: WARNING: TotalMaximumOutdegree violated , Removing child: " + droppedchild + " from stripe[" + stripeToDropFrom + "]", 880);
	    }	
	    //numdropped++;
	    //}
	    
	}








	long difftime;
	int childDegree;
	boolean childIsPrimaryContributor; // This tells us if the child is a primarycontributor in this stripe
	int childsPrimaryStripeId; // this will be '-1' if there is a pending UpdateChildInfoMsg from child to be expected

	for(int stripeId= 0; stripeId < NUMSTRIPES; stripeId ++) {
	    StripeInformation currstripe = stripeinfo[stripeId];

	    
	    // NOTE: setParent(null) if called on the multicastsource will not add the srcheader in the pathToRoot, so its important that we do this only for non source nodes

	    difftime = currtime - currstripe.manager.getLastReverseHeartbeatTimeParent();
	    if(!amMulticastSource) {
		// Step 0: Remove dead parent
		if(difftime >= SaarClient.NEIGHBORDEADTHRESHOLD) {  
		    // We will set our parent to null
		    currstripe.manager.setParent(null,false);    
		}
	    }
	    
	    
	    // Step 1: We will first remove dead children (i.e children from whom I have not received periodic heartbeats). For the alive children we will request them to send an updated ChildInfoMsg if they had issued the anycast before getting the grpSummary
	    
	    NodeHandle[] mychildren = currstripe.manager.getChildren();
	    for(int i=0; i<mychildren.length; i++) {
		difftime = currtime - currstripe.manager.getLastForwardHeartbeatTimeChild(mychildren[i]);
		childDegree = currstripe.manager.getChildDegree(mychildren[i]);
		childIsPrimaryContributor = currstripe.manager.isPrimaryContributor(mychildren[i]);
		childsPrimaryStripeId = currstripe.manager.getChildsPrimaryStripeId(mychildren[i]);

		if(SaarTest.logLevel <= 850) myPrint("multitree: " + "Stripe[" + stripeId + "]" + " dataplanechild[" + i + "] (" + mychildren[i] + ", difftime: " + difftime + ", childDegree: " + childDegree + ", childIsPrimaryContributor: " + childIsPrimaryContributor + ", childsPrimaryStripeId: " + childsPrimaryStripeId + ")", 850);

		if(childsPrimaryStripeId == -1) {
		    // We will request for an update from the child
		    if(SaarTest.logLevel <= 875) myPrint("multitree: " + "Stripe[" + stripeId + "]" + " Sending RequestChildInfoMsg to child " + mychildren[i], 875);
		    saarClient.endpoint.route(null, new RequestChildInfoMsg(saarClient.endpoint.getLocalNodeHandle(), myTopic, stripeId), mychildren[i]);		    
		}



		// Child Removal Policy:
		//     a) Until the child has acknowledge the connection by sending a ChildIsAliveMsg to the anycastresponder, the child reservation is held only for 'CHILDRESERVATIONTIME' 
		//     b) If the child acknowledge the connection, then the difftime is 4*CHILDHEARTBEATPERIOD
		if( ((!currstripe.manager.gotChildAck(mychildren[i])) && (difftime > CHILDRESERVATIONTIME)) || (difftime >= SaarClient.NEIGHBORDEADTHRESHOLD)) {
		    currstripe.manager.removeChild(mychildren[i]);
		}
	    }
	




	    if(!amMulticastSource) {
		// Step 2: We will now establish a parent via 'anycast' if required

		difftime = currtime - currstripe.manager.getLastReverseHeartbeatTimeParent();
		if(SaarTest.logLevel <= 875) myPrint("multitree: " + "Stripe[" + stripeId +"]" + " dataplaneparent (" + currstripe.manager.getParent() + "," + difftime + "," + currstripe.manager.getParentEstablishedViaPrimaryChildViolation() + ")", 875);
		
		
		currstripe.isConnected = evaluateIsConnected(stripeId);
		if((currstripe.manager.getParent()!= null) && currstripe.isConnected && currstripe.manager.hasValidPathToRoot()) {
		    // Since we have a parent and are getting packets dont worry, we will however invoke an anycast to improve the tree quality (e.g tree depth)
		    
		    // In a multi-tree, it does not make sense to do tree improvement for nodes in their non-primary stripes. This is because the motivation for doing depth improvment is to reduce the impact of churn by lowering depth. For the non-contributors they do not have children, so their depth does not matter. Infact we discovered that optimizing their depths results in further problems since they seal off the slots higher up in the tree causing depp trees. At the higher levels of the tree you ideally want just primary-contributors.
		    if(ENABLETREEIMPROVEMENT && currstripe.isPrimary && ((currtime - currstripe.lastTreeImprovementTime) > TREEIMPROVEMENTPERIOD)) {
			currstripe.lastTreeImprovementTime = currtime;
			
			// We will issue the tree improvment anycast request only if we know the group has available slots at levels lower than the local node's treeDepth
			currstripe.treeDepth = currstripe.manager.getTreeDepth();
			int sumAvailableSlotsAtLowerDepth = 0;
			int probeDepth;
			if((currstripe.treeDepth-1) >= MultitreeContent.MAXSATURATEDDEPTH) {
			    probeDepth = MultitreeContent.MAXSATURATEDDEPTH;
			} else {
			    probeDepth = currstripe.treeDepth - 1;
			}
			for(int i=0; i< probeDepth; i++) {
			    sumAvailableSlotsAtLowerDepth = sumAvailableSlotsAtLowerDepth + currstripe.grpAvailableSlotsAtDepth[i];
			}
			// Note that these slots are at nodes that are receiving their respective stripe since otherwise the depth is considered as MAXADVERTISEDDEPTH
			if(sumAvailableSlotsAtLowerDepth > 0) {
			    NodeHandle anycastHint = getRandomLeafsetMember();
			    SaarContent reqContent = new MultitreeContent(MultitreeContent.ANYCASTFORTREEIMPROVEMENT, topicName, tNumber, currstripe.manager.getPathToRoot(), currstripe.manager.getTreeDepth(), stripeId, ONLYPRIMARY, chosenStripeIndex, currstripe.maximumOutdegree); // we DO NOT violate primary child constraint during tree improvement
			    if(SaarTest.logLevel <= 875) myPrint("multitree: " + "StripeId[" + stripeId +"]" + " Issuing anycast for tree IMPROVEMENT, currDepth= " + currstripe.treeDepth , 875);
			    saarClient.reqAnycast(tNumber,reqContent, anycastHint, 2, 25, 10);
			} else {
			    if(SaarTest.logLevel <= 875) myPrint("multitree: " + "StripeId[" + stripeId + "]" + " Damping anycast for tree IMPROVEMENT because of saturation at lower depths", 875);
			}
		    }



		    if(ENABLEANYCASTFORSECONDARYREPLACEMENT && (currstripe.grpConnectedStripeAvailableSlots != 0) && currstripe.manager.getParentEstablishedViaPrimaryChildViolation() && ((currtime - lastSecondaryConnectionRemovalTime) > SECONDARYCONNECTIONREMOVALPERIOD)) {
			lastSecondaryConnectionRemovalTime = currtime;

			NodeHandle anycastHint = null;
			SaarContent reqContent = new MultitreeContent(MultitreeContent.ANYCASTFORCONNECTIONIMPROVEMENT, topicName, tNumber, currstripe.manager.getPathToRoot(), currstripe.manager.getTreeDepth(), stripeId, ONLYPRIMARY, chosenStripeIndex, currstripe.maximumOutdegree);
			if(SaarTest.logLevel <= 875) myPrint("multitree: " + "StripeId[" + stripeId +"]" + " Issuing anycast for CONNECTION IMPROVEMENT", 875);
			saarClient.reqAnycast(tNumber,reqContent, anycastHint, 2, 1, 10);


		    }

		    
		    if(ENABLE_PREMPT_DEGREE_PUSHDOWN && currstripe.isPrimary && (((currstripe.grpDepthAtNodeHavingChildOfDegree[0] + 1) < currstripe.manager.getTreeDepth()) && (currstripe.manager.numChildren() < currstripe.maximumOutdegree) && (currtime - lastPremptDegreePushdownTime) > PREMPTDEGREEPUSHDOWNPERIOD)) { // For now we preempt only zero degree nodes
			lastPremptDegreePushdownTime = currtime;

			NodeHandle anycastHint = null;
			SaarContent reqContent = new MultitreeContent(MultitreeContent.ANYCASTFORPREMPTDEGREEPUSHDOWN, topicName, tNumber, currstripe.manager.getPathToRoot(), currstripe.manager.getTreeDepth(), stripeId, ACCEPTBYDROPING, chosenStripeIndex, currstripe.maximumOutdegree);
			if(SaarTest.logLevel <= 875) myPrint("multitree: " + "StripeId[" + stripeId +"]" + " Issuing anycast for PREMPTDEGREEPUSHDOWN", 875);
			saarClient.reqAnycast(tNumber,reqContent, anycastHint, 2, 1, 10);


		    }


		   
		    
		} else if((currstripe.manager.getParent() != null) && (difftime < SaarClient.NEIGHBORDEADTHRESHOLD)) {
		    // We do have an intact parent, we are not getting packets because the tree disconnect is higher up. Since only subtree roots repair the tree, we dont do anything here
		    
		} else {
		    // Our parent probably left the group, we anycast for a new parent

		    // Note: There are 3 modes of anycast
		    // a) When the grpConnectedStripeAvailableSlots[stripeId] is nonzero, implying that we can find a parent without violating primary child constraint
		    // b) When the grpConnectedStripeAvailableSlots[stripeId] is zero but grpConnectedTotalAvailableSlots[stripeId] is non-zero, implying that we need to violate the primary child constraint but some node that is successfully receiving the stripe can accept the requestor as a secondary child
		    // c) When grpConnectedStripeAvailableSlots[stripeId] is zero and grpConnectedTotalAvailableSlots[stripeId] is zero, then even violating the primary-child constraint will not work since all nodes receiving this stripe are sealed. In this case we will drop a child from a secondary stripe 's' where grpConnectedStripeAvailableSlots[s] is non-zero and  where the child has no children in 's'. Also in this mode only the nodes that are primary stripes for the desired stripe and have available capacity are allowed to issue this anycast so that it leads to creation of slots
 
		    // However, before anycasting we check to see if we need to relax the primary-child constraint. We relax this when the currstripe.grpConnectedAvailableSlots is zero

		    int anycastneighbormode;
		    boolean denyAnycast = false;
		    double determineAllowSecondaryFrac = ((double)currstripe.grpConnectedStripeAvailableSlots) / ((double)currstripe.grpConnectedTotalAvailableSlots); 



		    int overallUsedSlots = 0;
		    int overallTotalSlots = 0;
		    for(int k=0; k < NUMSTRIPES; k ++) {
			overallUsedSlots = overallUsedSlots + stripeinfo[k].manager.numChildren();
			overallTotalSlots = overallTotalSlots + stripeinfo[k].maximumOutdegree;
		    }





		    //if(currstripe.grpConnectedStripeAvailableSlots != 0) { // Sep18-2007 Added the additonal chosenPrimaryStripe in order for node to issue Anycast immediately after join() and introduced threshold
		    if((currstripe.grpConnectedStripeAvailableSlots > 10) || (!chosenPrimaryStripe)) { // Sep18-2007 Added the additonal chosenPrimaryStripe in order for node to issue Anycast immediately after join(). Also on Sep20 we started using a fraction to be more robust while determining if we should use ALLOWSECONDARY
 			anycastneighbormode = ONLYPRIMARY;
			// We further constrain this mode if the currstripeIndex is our non-primary stripe
			if(ENABLELIMITNONCONTRIBUTORS) {
			    //if((!currstripe.isPrimary) && currstripe.grpAllowNonContributors) { // Note that grpAllowNonContributors takes into acount that the free slots for non-contributors are on nodes that are connected (i.e currently receiving streaming content)
			    if(!currstripe.isPrimary) { // Tenmporary disabling check on currstripe.grpAllowNonContributors
				anycastneighbormode = ONLYPRIMARY_LIMITNONCONTRIBUTORS;
			    }

			}

			
			//} else if(currstripe.grpConnectedTotalAvailableSlots != 0) {
			//} else if( (currstripe.grpConnectedTotalAvailableSlots > 0) || (!chosenPrimaryStripe)) {
		    } else if((!currstripe.isPrimary) || (overallUsedSlots == overallTotalSlots) || (currstripe.grpConnectedTotalAvailableSlots > 10) || (!chosenPrimaryStripe)) {

			anycastneighbormode = ALLOWSECONDARY;
		    } else {

			anycastneighbormode = ACCEPTBYDROPING;
			if((!currstripe.isPrimary) || (overallUsedSlots == overallTotalSlots)) {
			    System.out.println("ERROR:We moved the denyanycast condition when true to issue an ALLOWSECONDARY anycast, so should not come here");
			    System.exit(1);
			    denyAnycast = true;
			}
		    }

		    if(!denyAnycast) {
			NodeHandle anycastHint = null;
			SaarContent reqContent = new MultitreeContent(MultitreeContent.ANYCASTNEIGHBOR, topicName, tNumber, currstripe.manager.getPathToRoot(), currstripe.manager.getTreeDepth(), stripeId, anycastneighbormode, chosenStripeIndex, currstripe.maximumOutdegree);

			if((currtime - lastAnycastForRepairTime[stripeId]) > ANYCASTTIMEOUT) {
			    //Previously before expbackoffcode lastAnycastForRepairTime[stripeId] = currtime;
			    //Previously before expbackoffcode saarClient.reqAnycast(tNumber,reqContent, anycastHint, 2, 1, 10);


			    // We do exponential backoff 
			    if((currtime - lastAnycastForRepairTime[stripeId]) > expbackoffInterval[stripeId]) {
				maxExpbackoffInterval[stripeId] = maxExpbackoffInterval[stripeId] * 2;
				expbackoffInterval[stripeId] = rng.nextInt((int)maxExpbackoffInterval[stripeId]);
				lastAnycastForRepairTime[stripeId] = currtime;
				 if(SaarTest.logLevel <= 875) myPrint("multitree: Issuing ANYCASTNEIGHBOR  EXPBACKOFF(nextmax:" + maxExpbackoffInterval[stripeId] + ", nextchosen: " + expbackoffInterval[stripeId] + ") , currDepth= " + currstripe.manager.getTreeDepth() + " maximumoutdegree: " + currstripe.maximumOutdegree, 875);
				 saarClient.reqAnycast(tNumber,reqContent, anycastHint, 2, 1, 10);

			    } else {
				if(SaarTest.logLevel <= 875) myPrint("singletree: Dampening ANYCASTNEIGHBOR EXPBACKOFF(max:" + maxExpbackoffInterval[stripeId] + ", chosen: " + expbackoffInterval[stripeId] + ", expired: " + (currtime - lastAnycastForRepairTime[stripeId]) + ")" , 875);
			    }


			}
		    }
		}
		
		
		
	    }
	    
	}
    }



    // This method is intended to break the correlated packet losses in a multitree where if a node does not get a fragment then all its descendants dont get the fragment. Note that its the responsibility of the node to only forward a packet in its primary stripe, so we just monitor for that.
    //public void missingPrimaryFragmentReconstruction() {
    //
    //long currtime = getCurrentTimeMillis();
    //if(SaarTest.logLevel <= 875) myPrint("multitree: " + " missingPrimaryFragmentReconstruction", 875);
    ///Vector toRemove = new Vector(); // This vector is for fragments in which it already got the primary
    //Enumeration mykeys = seqRecv.keys();
    //while(mykeys.hasMoreElements()) {
    //    int myseqnum = ((Integer)mykeys.nextElement()).intValue();
    //    SeqState sState = (SeqState) seqRecv.get(new Integer(myseqnum));
    //    boolean isMissingPrimary = sState.isMissing(chosenStripeIndex);
    //    boolean isBeyondJitter = sState.isBeyondJitter(currtime);
    //    if(isMissingPrimary && isBeyondJitter) {
    //	int totalFragments = sState.totalFragments();
    //	boolean canReconstruct = false;
    //	if(totalFragments >= (NUMSTRIPES - NUMREDUNDANTSTRIPES)) {
    //	    canReconstruct = true;
    //	    // We will schedule this packet for future opportunistic push
    //	    reconstructedFragmentsToPush.add(new Integer(myseqnum));
    //	    toRemove.add(new Integer(myseqnum)); // Since we have already added it to the other buffer we can remove it from here
    //	}
    //	if(SaarTest.logLevel <= 875) myPrint("multitree: " + " PrimaryMissingBeyondJitter: " + sState + " canReconstruct: " + canReconstruct , 875);
    //    } 
    //    if(!isMissingPrimary) {
    //	toRemove.add(new Integer(myseqnum));
    //    }
    //}
    //
    //for(int i=0; i< toRemove.size(); i++) {
    //    seqRecv.remove(toRemove.elementAt(i));
    //}
    //   
    //
    //}



    // pushes the required blocks from the reconstructedFragmentsToPush
    //public void pushReconstructedFragments() {
    //Topic myTopic = saartopic.baseTopic;
    //long currtime = getCurrentTimeMillis();
    //if((currtime - lastPushedReconstructedFragmentsTime) < 1000) {
    //    if(SaarTest.logLevel <= 850) myPrint("multitree: " + " Skipping pushReconstructedFragments()", 850);
    //    return;
    //}
    //
    //if(SaarTest.logLevel <= 875) myPrint("multitree: " + "pushReconstructedFragments()", 875);
    //lastPushedReconstructedFragmentsTime = currtime; 
    //int numCanPush = 100; // This number should eventually be based on te amount of free bandwidth
    //int numPushed = 0;
    //while((numPushed <= numCanPush) && (reconstructedFragmentsToPush.size() > 0)) {
    //    // Remove elements fro the head
    //    int seqNum = ((Integer) reconstructedFragmentsToPush.remove(0)).intValue();
    //    int stripeId = chosenStripeIndex;
    //    
    //    // We deliver the packet locally
    //    StripeInformation currstripe = stripeinfo[stripeId];
    //    int numPkts = 0; 
    //    if(currstripe.numSequence.containsKey(new Integer(seqNum))) {
    //	numPkts = ((Integer)currstripe.numSequence.get(new Integer(seqNum))).intValue();
    //    } 
    //    currstripe.numSequence.put(new Integer(seqNum), new Integer(numPkts + 1));
    //    Block block = new Block(stripeId,seqNum);
    //    // We set this as reconstructed
    //    block.setReconstructed(true);
    //    block.addToPath(saarClient.bindIndex, saarClient.jvmIndex, saarClient.vIndex);
    //    currstripe.blocks.put(new Integer(block.seqNum), block);
    //    multitreeDeliver(stripeId,seqNum, block.getDepth(), saarClient.endpoint.getLocalNodeHandle(), numPkts +1, block); 
    //    NodeHandle handles[] = currstripe.manager.getChildren();
    //    for(int i=0; i < handles.length; i++) {
    //	numpktssent++;
    //	numPushed++;
    //	if(SaarTest.logLevel <= 875) myPrint("multitree: " + " Stripe[" + stripeId + "]: " + " SeqNum: " + seqNum + " Publishing Reconstructed Block to child " + handles[i], 875);
    //	saarClient.endpoint.route(null, new PublishMsg(saarClient.endpoint.getLocalNodeHandle(), myTopic, block, stripeId), handles[i]);	    
    //	
    //    }
    //    
    //}
    //
    //}


    





    
    // Here it advertises its pathToRoot info to its children, Additonally this heartbeat also serves the purpose of enabling only parentless nodes to issue repairs. Thus for a disconnected subtree other than the root, all other nodes will be receiving the heartbeats and thus will not issue anycasts for repair
    public void sendHeartbeats() {
	long currtime = getCurrentTimeMillis();
	for(int stripeId=0; stripeId < NUMSTRIPES; stripeId++) {
	    StripeInformation currstripe = stripeinfo[stripeId];
	    
	    boolean requiresPathToRootPush = currstripe.manager.requiresPathToRootPush();
	    if(requiresPathToRootPush || ((currtime - currstripe.lastParentHeartbeatSentTime) >= (SaarClient.NEIGHBORHEARTBEATPERIOD + 500))) { // We add 500 since in the multitree the publishperiod can be close to the neighborheartbeatperiod
		Topic myTopic = saartopic.baseTopic;	
		NodeHandle handles[] = currstripe.manager.getChildren();		
		if(SaarTest.logLevel <= 875) myPrint("multitree: " + " Pushing CURRPATHTOROOT[" + stripeId + "]: " + currstripe.manager.getPathToRootAsString() + " to " + handles.length + " children because lastheartbeatsenttime: " + currstripe.lastParentHeartbeatSentTime + " OR requiresPathToRootPush: " + requiresPathToRootPush , 875);
		currstripe.manager.setLastPushedPathToRoot(currstripe.manager.getPathToRoot());
		currstripe.lastParentHeartbeatSentTime = currtime;


		for(int i=0; i < handles.length; i++) {
		    if(SaarTest.logLevel <= 875) myPrint("multitree: " + " Sending PathToRootInfoMsg[" + stripeId + "] to child " + handles[i], 875);
		    saarClient.endpoint.route(null, new PathToRootInfoMsg(saarClient.endpoint.getLocalNodeHandle(), myTopic, currstripe.manager.getPathToRoot(), stripeId), handles[i]);	    
		    
		}
	    } else {
		//if(SaarTest.logLevel <= 875) myPrint("multitree: " + " DAMPING PATHTOROOT,  CURRPATHTOROOT: " + manager.getPathToRootAsString() +  " LASTPUSHED: " + manager.getLastPushedPathToRootAsString(), 875);		
		if(SaarTest.logLevel <= 875) myPrint("multitree: " + " DAMPING redundant PATHTOROOT msg", 875);
		
	    }
	    
	    

	    
	    if((currtime - currstripe.lastChildHeartbeatSentTime) >= SaarClient.NEIGHBORHEARTBEATPERIOD) {
		currstripe.lastChildHeartbeatSentTime = currtime;
		Topic myTopic = saartopic.baseTopic;	
		NodeHandle myparent = currstripe.manager.getParent();
		if(myparent!= null) {
		    if(SaarTest.logLevel <= 850) myPrint("multitree: " + "Stripe[" + stripeId + "]" + " Sending ChildIsAliveMsg to parent " + myparent, 850);
		    saarClient.endpoint.route(null, new ChildIsAliveMsg(saarClient.endpoint.getLocalNodeHandle(), myTopic, stripeId), myparent);	    
		    
		}
	    }
	    
	}
    }





    /*
    // Returns 1 if we actually did multicast (i.e if we were root. This broadcasts unique sequence number packets n different stripes. We reverted to using the conventional same sequence number on different stripes because of a emmory leak. Also the different sequence numbered fragments for the different stripe trees are pushed down at the same time instant 't' (which occurs every publishperiod). This resulted in a memory bug.
    public int sendMulticastTopic() {
	Topic myTopic = saartopic.baseTopic;
	long currTime = getCurrentTimeMillis();
	int currSeq = saarClient.allTopics[tNumber].pSeqNum;
	String myTopicName = topicName;

	int seqNumDue = (int) ((currTime - firstBroadcastTime)/PUBLISHPERIOD);
	//System.out.println("currSeq: " + currSeq +  ", seqNumDue: " + seqNumDue);
	if((currSeq ==0) || (currSeq <= seqNumDue)) {	
	    
	    if(firstBroadcastTime == 0) {
		firstBroadcastTime = currTime;
	    }
	
	    saarClient.allTopics[tNumber].setPSeqNum(currSeq + 1);
	    saarClient.allTopics[tNumber].setPTime(getCurrentTimeMillis());

	    for(int stripeId=0; stripeId< NUMSTRIPES; stripeId++) {
		// We deliver the packet locally
		StripeInformation currstripe = stripeinfo[stripeId];
	 	//int seqNum = currSeq ;
		int seqNum = currSeq * NUMSTRIPES + stripeId;
		saarClient.viewer.setForegroundBroadcastSeqnum(seqNum);
		if(SaarTest.logLevel <= 875) myPrint("multitree: " + "SysTime: " + getCurrentTimeMillis() + " Node "+saarClient.endpoint.getLocalNodeHandle()+" BROADCASTING for Topic[ "+ myTopicName + " ] " + myTopic + " Seq= " + seqNum + " on stripeId: " + stripeId, 875); 


		int numPkts = 0; 
		if(currstripe.numSequence.containsKey(new Integer(seqNum))) {
		    numPkts = ((Integer)currstripe.numSequence.get(new Integer(seqNum))).intValue();
		} 
		currstripe.numSequence.put(new Integer(seqNum), new Integer(numPkts + 1));
		Block block = new Block(stripeId,seqNum);
		block.addToPath(saarClient.bindIndex, saarClient.jvmIndex, saarClient.vIndex);
		currstripe.blocks.put(new Integer(block.seqNum), block);
		Id[] srcHeader = new Id[1];
		srcHeader[0] = rice.pastry.Id.build();
		currstripe.manager.appendPathToRoot(srcHeader); // the local node will be appended in the pathToRoot
		multitreeDeliver(stripeId,seqNum, block.getDepth(), saarClient.endpoint.getLocalNodeHandle(), numPkts +1, block); 
		NodeHandle handles[] = currstripe.manager.getChildren();
		for(int i=0; i < handles.length; i++) {
		    numpktssent++;
		    if(SaarTest.logLevel <= 850) myPrint("multitree: " + " Stripe[" + stripeId + "]: " + "Publishing Block to child " + handles[i], 850);
		    saarClient.endpoint.route(null, new PublishMsg(saarClient.endpoint.getLocalNodeHandle(), myTopic, block, stripeId), handles[i]);	    
		    
		}
	    }
	    
	    return 1;
	} else {
	    // We will dampen rate to only send at PUBLISHPERIOD
	    return 0;
	}
	
    }
    */



    /*
    // This version using a larger publishperiod = numstripes * blocksize. eg publishperiod= 1000, 5 stripes for .b200. However after every publishperiod it pushes down 1 fragment each down each of the stripe trees. Also each of the fragments pushed down the different stripe trees have the same sequence number.
    public int sendMulticastTopic() {
	Topic myTopic = saartopic.baseTopic;
	long currTime = getCurrentTimeMillis();
	int currSeq = saarClient.allTopics[tNumber].pSeqNum;
	String myTopicName = topicName;

	//if(SaarTest.logLevel <= 880) myPrint("multitree: " + "sendMulticastTopic() called ", 880);
	int seqNumDue = (int) ((currTime - firstBroadcastTime)/PUBLISHPERIOD);
	//System.out.println("currSeq: " + currSeq +  ", seqNumDue: " + seqNumDue);
	if((currSeq ==0) || (currSeq <= seqNumDue)) {	
	    //if(SaarTest.logLevel <= 875) myPrint("multitree: " + "SysTime: " + getCurrentTimeMillis() + " Node "+saarClient.endpoint.getLocalNodeHandle()+" BROADCASTING for Topic[ "+ myTopicName + " ] " + myTopic + " Seq= " + currSeq + " on stripeId: " + "all", 875); 
	    if(firstBroadcastTime == 0) {
		firstBroadcastTime = currTime;
	    }
	
	    saarClient.allTopics[tNumber].setPSeqNum(currSeq + 1);
	    saarClient.allTopics[tNumber].setPTime(getCurrentTimeMillis());

	    for(int stripeId=0; stripeId< NUMSTRIPES; stripeId++) {
		// We deliver the packet locally
		StripeInformation currstripe = stripeinfo[stripeId];
		int seqNum = currSeq ;
		//int seqNum = currSeq * NUMSTRIPES + stripeId;
		saarClient.viewer.setForegroundBroadcastSeqnum(seqNum);
		if(SaarTest.logLevel <= 880) myPrint("multitree: " + "SysTime: " + getCurrentTimeMillis() + " Node "+saarClient.endpoint.getLocalNodeHandle()+" BROADCASTING for Topic[ "+ myTopicName + " ] " + myTopic + " Seq= " + seqNum + " on stripeId: " + stripeId, 880); 


		int numPkts = 0; 
		if(currstripe.numSequence.containsKey(new Integer(seqNum))) {
		    numPkts = ((Integer)currstripe.numSequence.get(new Integer(seqNum))).intValue();
		} 
		currstripe.numSequence.put(new Integer(seqNum), new Integer(numPkts + 1));
		Block block = new Block(stripeId,seqNum);
		block.addToPath(saarClient.bindIndex, saarClient.jvmIndex, saarClient.vIndex);
		currstripe.blocks.put(new Integer(block.seqNum), block);
		Id[] srcHeader = new Id[1];
		srcHeader[0] = rice.pastry.Id.build();
		currstripe.manager.appendPathToRoot(srcHeader); // the local node will be appended in the pathToRoot
	 	multitreeDeliver(stripeId,seqNum, block.getDepth(), saarClient.endpoint.getLocalNodeHandle(), numPkts +1, block); 
		// When hybrid debug is true we intentionally do not send anything down the tree paths and expect that the mesh-recovery should cope with the complete failure of the tree component
		if(!SaarClient.HYBRIDDEBUG) {
		    
		    currstripe.lastParentHeartbeatSentTime = currTime;
		    NodeHandle handles[] = currstripe.manager.getChildren();
		    for(int i=0; i < handles.length; i++) {
			numpktssent++;
			if(SaarTest.logLevel <= 850) myPrint("multitree: " + " Stripe[" + stripeId + "]: " + "Publishing Block to child " + handles[i], 850);
			saarClient.endpoint.route(null, new PublishMsg(saarClient.endpoint.getLocalNodeHandle(), myTopic, block, stripeId), handles[i]);	    
			
		    }
		}
	    }
	    
	    return 1;
	} else {
	    // We will dampen rate to only send at PUBLISHPERIOD
	    return 0;
	}
	
    }
    */




    
    // This version using a larger publishperiod = numstripes * blocksize. eg publishperiod= 1000, 5 stripes for .b200. However instead of pushing 'numstripes' fragments every publishperiod, it pushes down 1 fragment every 'blockperiod' in a round robbin fashion. The sequence number of the fragments in different stripes is same in one round though.
    public int sendMulticastTopic() {
	Topic myTopic = saartopic.baseTopic;
	long currTime = getCurrentTimeMillis();
	int currSeq = saarClient.allTopics[tNumber].pSeqNum;
	String myTopicName = topicName;

	//if(SaarTest.logLevel <= 880) myPrint("multitree: " + "sendMulticastTopic() called ", 880);
	int globalseqNumDue = (int) ((currTime - firstBroadcastTime)/BLOCKPERIOD);
	//int seqNumDue = (int) ((currTime - firstBroadcastTime)/PUBLISHPERIOD);
	//System.out.println("currSeq: " + currSeq +  ", seqNumDue: " + seqNumDue + ", globalseqNumDue: " + globalseqNumDue);
	if((currglobalSeq ==0) || (currglobalSeq <= globalseqNumDue)) {	
	    //if(SaarTest.logLevel <= 875) myPrint("multitree: " + "SysTime: " + getCurrentTimeMillis() + " Node "+saarClient.endpoint.getLocalNodeHandle()+" BROADCASTING for Topic[ "+ myTopicName + " ] " + myTopic + " Seq= " + currSeq + " on stripeId: " + "all", 875); 
	    if(firstBroadcastTime == 0) {
		firstBroadcastTime = currTime;
	    }
	

	    int stripeId = nextStripeToPublishOn;
	    nextStripeToPublishOn = (nextStripeToPublishOn + 1) % NUMSTRIPES; 
	    // We deliver the packet locally
	    StripeInformation currstripe = stripeinfo[stripeId];
	    int seqNum = (int) (currglobalSeq / NUMSTRIPES) ;
	   

	    if(stripeId == 0) {
		saarClient.allTopics[tNumber].setPSeqNum(currSeq + 1);
		saarClient.allTopics[tNumber].setPTime(getCurrentTimeMillis());
	    }

	    //int seqNum = currSeq * NUMSTRIPES + stripeId;
	    saarClient.viewer.setForegroundBroadcastSeqnum(seqNum);
	    if(SaarTest.logLevel <= 880) myPrint("multitree: " + "SysTime: " + getCurrentTimeMillis() + " Node "+saarClient.endpoint.getLocalNodeHandle()+" BROADCASTING for Topic[ "+ myTopicName + " ] " + myTopic + " Seq= " + seqNum + " on stripeId: " + stripeId, 880); 


	    int numPkts = 0; 
	    if(currstripe.numSequence.containsKey(new Integer(seqNum))) {
		numPkts = ((Integer)currstripe.numSequence.get(new Integer(seqNum))).intValue();
	    } 
	    currstripe.numSequence.put(new Integer(seqNum), new Integer(numPkts + 1));
	    Block block = new Block(stripeId,seqNum);
	    block.addToPath(saarClient.bindIndex, saarClient.jvmIndex, saarClient.vIndex);
	    currstripe.blocks.put(new Integer(block.seqNum), block);
	    Id[] srcHeader = new Id[1];
	    srcHeader[0] = rice.pastry.Id.build();
	    currstripe.manager.appendPathToRoot(srcHeader); // the local node will be appended in the pathToRoot
	    multitreeDeliver(stripeId,seqNum, block.getDepth(), saarClient.endpoint.getLocalNodeHandle(), numPkts +1, block); 
	    // When hybrid debug is true we intentionally do not send anything down the tree paths and expect that the mesh-recovery should cope with the complete failure of the tree component
	    if(!SaarClient.HYBRIDDEBUG) {
		
		currstripe.lastParentHeartbeatSentTime = currTime;
		NodeHandle handles[] = currstripe.manager.getChildren();
		for(int i=0; i < handles.length; i++) {
		    numpktssent++;
		    if(SaarTest.logLevel <= 850) myPrint("multitree: " + " Stripe[" + stripeId + "]: " + "Publishing Block to child " + handles[i], 850);
		    saarClient.endpoint.route(null, new PublishMsg(saarClient.endpoint.getLocalNodeHandle(), myTopic, block, stripeId), handles[i]);	    
		    
		}
	    }
	    
	    
	    currglobalSeq ++;
	    
	    return 1;
	} else {
	    // We will dampen rate to only send at PUBLISHPERIOD
	    return 0;
	}
	
    }
    

 
    // It is possible that the node has already received this block and that this notification from the channelviewer was because of the multitreedeliver itself. Since right now we do not use the packets recovered via the mesh to re-push them down the tree, we need not do anything here
    public void alreadyReceivedSequenceNumber(int seqNum) {

	
	/* WARNING: In anycase use the right conversion fuction to translate the global blockbased seqnum to tree pkt #
	//for(int stripeId=0; stripeId < NUMSTRIPES; stripeId++) {
	  //  StripeInformation currstripe = stripeinfo[stripeId];
	    //if(!currstripe.numSequence.containsKey(seqNum)) {
		//currstripe.numSequence.put(new Integer(seqNum), new Integer(1));
		//Block block = new Block(stripeId, seqNum);
		//block.setRecoveredOutOfBand();
		//block.addToPath(saarClient.bindIndex, saarClient.jvmIndex, saarClient.vIndex);
		//currstripe.blocks.put(new Integer(seqNum), block);
	    //}
	//}
	*/

    }



    public void multitreeDeliver(int stripeId, int seqNum, int depth, NodeHandle parent, int numPkts, Block block) {

	long currtime = getCurrentTimeMillis();
	if(SaarTest.logLevel <= 880) myPrint("multitree: " + "multitreedeliver(" + stripeId + "," + currtime + "," + depth + "," + parent + "," + seqNum + "," + numPkts + "," + block + ")", 880);
	
	stripeinfo[stripeId].lastPktRecvTime = currtime;
	stripeinfo[stripeId].lastPktRecvSeqNum = seqNum;

	SeqState sState; 
	boolean canReconstructBlockBefore = false;
	boolean canReconstructBlockAfter = false;

	if(seqRecv.containsKey(new Integer(seqNum))) {
	    sState = (SeqState)seqRecv.get(new Integer(seqNum));
	    canReconstructBlockBefore = sState.canReconstructBlock();
	} else {
	    sState = new SeqState(seqNum, currtime);
	}
	sState.recvFragment(stripeId);
	canReconstructBlockAfter = sState.canReconstructBlock();

	seqRecv.put(new Integer(seqNum), sState);

	if(saarClient.viewer.getFirstForegroundPktAfterJoin() == -1) {
	    saarClient.viewer.setFirstForegroundPktAfterJoin(seqNum);
	    
	}

	// Implies that only missing fragments are recovered
	// We convert the (seqnum,stripeid) pair to a hybrid seqnum
	int hybridSeqNum = seqNum*NUMSTRIPES + stripeId;
	//int hybridSeqNum = seqNum;

	saarClient.viewer.setLastForegroundPktRecvTime(currtime, stripeId);
	saarClient.viewer.setLastForegroundPktRecvSeqnum(hybridSeqNum, stripeId);
	saarClient.viewer.receivedSequenceNumber(hybridSeqNum, dataplaneType);
	
	if(SaarClient.HYBRIDDEBUG) {
	    // We are not using our foreground bandwidth, so we dont notify the viewer
	} else {
	    // Note that detection of a data bottleneck is done only on the primary stripe
	    if((stripeId == chosenStripeIndex) || amMulticastSource) {
		saarClient.viewer.setLastForegroundPktPushedTime(currtime);

	    }
	}


    }



    public boolean evaluateIsConnected(int stripeId) {
	StripeInformation currstripe = stripeinfo[stripeId];
	long currtime = getCurrentTimeMillis();
	if((currtime - currstripe.lastPktRecvTime) > 2*PUBLISHPERIOD) {
	    return false;
	} else {
	    return true;
	}
	
    }


    // The loop detection checks to see if the local node's exists in the recvPathToRoot and also if the local node's predecessor is the same as the current parent. Note that due to stale information it is possible to receive the local node in the recvPathToRoot but this might not imply that a loop has actually been formed
    public boolean loopDetected(rice.p2p.commonapi.Id[] recvPathToRoot, int stripeId) {
	StripeInformation currstripe = stripeinfo[stripeId];
	if(currstripe.manager.getParent() == null) {
	    // There is no chance of a loop, since we do not have a parent
	    return false;
	}
	// We check from i=1 since we need to find the predecessor
	for(int i=1; i< recvPathToRoot.length; i++) {
	    if(recvPathToRoot[i].equals(saarClient.endpoint.getId())) {
		// Check to see if the predecessor is the local node's parent otherwise its fine
		rice.p2p.commonapi.Id predecessor = recvPathToRoot[i-1];
		if(predecessor.equals(currstripe.manager.getParent().getId())) {
		    // This implies a loop has been formed
		    return true;
		} else {
		    if(SaarTest.logLevel <= 875) myPrint("multitree: " + "WARNING: A stale pathToRoot could have MISLED into trigger a loop formation error", 875);
		}

	    }

	}
	return false;
    }





    public TemporalBufferMap computeSendBMAP(int stripeId) {
	TemporalBufferMap bmap = null; 
	long currtime = getCurrentTimeMillis();
	if(rootSeqNumRecv !=-1) {
	    //int selfestimatedRWindow = firstPktSequence + (int)((currtime - firstPktTime)/PUBLISHPERIOD); // Updated on Dec23-2007 when we found a memeory leak in numSequence and blocks where the blocks that didnt even arrive were being deleted. This was because the right window was based on the block at source
	    int selfestimatedRWindow = stripeinfo[stripeId].lastPktRecvSeqNum +  (int)((currtime - stripeinfo[stripeId].lastPktRecvTime)/PUBLISHPERIOD); 


	    int controlPktRWindow = rootSeqNumRecv;

	    if(stripeId ==0) { // We dont want to print this several times
		if(SaarTest.logLevel <= 880) myPrint("multitree: " + "RWINDOW-ESTIMATION:" + "controlPkt:" + controlPktRWindow + ", selfestimate[" + stripeId + "]" + ": " + selfestimatedRWindow, 880);
	    }

	    int rWindow = selfestimatedRWindow;
	    int lWindow = rWindow - TemporalBufferMap.ADVERTISEDWINDOWSIZE + 1;
	    // We will remove the sequence number outside the window from the blocks hashtable
	    StripeInformation currstripe = stripeinfo[stripeId];
	    

	    // The safemagin is because if we miss a particular lWindow then that block will remain
	   
	    //int maxblockselapsedatroot = 10 * ((int)((SaarClient.CONTROLPLANEUPDATEPERIOD/PUBLISHPERIOD))) ; // Note that the blocks we erase are blocks that are old and have already been received at this node, 10 is to allow maximum deliverydelay of 10 sec. 
	    int SAFEMARGIN = (int)((SaarClient.CONTROLPLANEUPDATEPERIOD/PUBLISHPERIOD) + 2);
	    for(int k =0; k < SAFEMARGIN; k++) {

		currstripe.blocks.remove(new Integer(lWindow - 30 - k)); // we keep a further safe window of 30 blocks because a node may advertise and other nodes when responding to the advertisement, may actually request a block that has past the window
		
		currstripe.numSequence.remove(new Integer(lWindow -30 -k));





		if(SaarTest.logLevel <= 875) myPrint("multitree: " + "Memory cleaning: stripeId: " + stripeId + " lWindow: " + lWindow + " SAFEMARGIN: " + SAFEMARGIN + " removingId: " + (lWindow - 30 -k) + ")", 875);

		
		if(stripeId == 0) {
		    seqRecv.remove(new Integer(lWindow -30 -k));
		    
				   
		}


	    }
	    
	    if(stripeId == 0) {
		if(SaarTest.logLevel <= 875) myPrint("multitree: " + "SeqRecvSize: " + seqRecv.size(), 875);
		
	    }
	    
	    // We will print the size of the hashtable to identfy memory leaks
	    if(SaarTest.logLevel <= 875) myPrint("multitree: " + "Hashtables[" + stripeId + "]: " + " numSequence: " + currstripe.numSequence.size() + ", blocks: " + currstripe.blocks.size() + ", reconstructedFragmentsToPush: " + reconstructedFragmentsToPush.size(), 875);
	    
		
	    // We will advertise the pkts in this window
	    bmap = new TemporalBufferMap(lWindow, currstripe.numSequence, TemporalBufferMap.ADVERTISEDWINDOWSIZE);
	
	} else {
	    if(SaarTest.logLevel <= Logger.WARNING) myPrint("multitree: " + "WARNING: rootSeqNumRecv is -1", Logger.WARNING);
	}
	return bmap;

    }


    public void computeOverallTemporalBufferMap() {
	overallsendbmap= null;
	for(int stripeId=0; stripeId < NUMSTRIPES; stripeId ++) {
	    if(stripeinfo[stripeId].sendbmap != null) {
		overallsendbmap = OverallTemporalBufferMap.sum(overallsendbmap,new OverallTemporalBufferMap(stripeinfo[stripeId].sendbmap));
	    }
	}

    }



    // We advertise based on the sendbmap provided we have already bootstrapped properly and made an attempt on fetching the blocks in the bitmap. We consider only the left end 
    public int getStreamingQualityToAdvertise(TemporalBufferMap bmap) {
	int val;
	long currtime = getCurrentTimeMillis();
	if((rootSeqNumRecv == -1) || (bmap == null) || (((currtime - firstPktTime)/PUBLISHPERIOD) < TemporalBufferMap.ADVERTISEDWINDOWSIZE)) {
	    val = 99;
	} else {
	    val = bmap.fractionFilled(TemporalBufferMap.ADVERTISEDWINDOWSIZE - TemporalBufferMap.FETCHWINDOWSIZE);
	}
	return val;
    }





    public int getCumulativeStreamingQualityToAdvertise(OverallTemporalBufferMap bmap, int numRedundantStripes) {
	int val;
	long currtime = getCurrentTimeMillis();
	if((rootSeqNumRecv == -1) || (bmap == null) || (((currtime - firstPktTime)/PUBLISHPERIOD) < TemporalBufferMap.ADVERTISEDWINDOWSIZE)) {
	    val = 99;
	} else {
	    val = bmap.fractionFilled(TemporalBufferMap.ADVERTISEDWINDOWSIZE - TemporalBufferMap.FETCHWINDOWSIZE, numRedundantStripes);
	}
	return val;

    }


    // ret[0] = total children in all stripes;
    // ret[1] = total slots permitted in all stripes
    public int[] getOverallUsedAndTotalSlots() {
	int[] ret = new int[2];
	ret[0] = 0;
	ret[1] = 0;
    
	for(int stripeId=0; stripeId < NUMSTRIPES; stripeId ++) {
	    StripeInformation currstripe = stripeinfo[stripeId];
	    int usedSlots = currstripe.manager.numChildren();
	    ret[0] = ret[0] + usedSlots;
	    int totalSlots = currstripe.maximumOutdegree;
	    ret[1] = ret[1] + totalSlots;
	}
	if(SaarTest.logLevel <= 875) myPrint("getOverallUsedAndTotalSlots():[" + ret[0] + "/" + ret[1] + "]", 875);	
	return ret;
    }


   public NodeHandle getRandomLeafsetMember() {
	NodeHandle ret = null;
	LeafSet localLeafset= saarClient.node.getLeafSet();
	int sgn = rng.nextInt(2);				    
	int val;
	if(localLeafset.size() >= 2) {
	    val = 1 + rng.nextInt(localLeafset.size()/2);
	} else {
	    val = 0;
	}

	int index;
	if (sgn == 0) {
	    index = -1 * val;
	} else {
	    index = 1 * val;
	}
	ret = localLeafset.get(index);
	return ret;

    }


    //public String getPathToRootAsString(Id[] arr) {
    //String s = "(";
    //if(arr!= null) {
    //    for(int i=0; i< arr.length; i++) {
    //	s = s + arr[i];
    //    }
    //}
    //s = s + ")";
    //return s;
    //}



    // This class contains metadata for the child related to this topic
    public class ChildState {
	NodeHandle child;
	boolean isPrimaryContributor; 
	int childDegree; 
	long lastForwardHeartbeatTime ; // this will also be used as the time when the connection was established
	boolean gotChildAck; // until we get the first ack, this varibale is true
	int childsPrimaryStripeId; 
	
	public ChildState(NodeHandle child, boolean isPrimaryContributor, int childDegree, int childsPrimaryStripeId) {
	    this.child = child;
	    this.isPrimaryContributor = isPrimaryContributor;
	    this.childDegree = childDegree;
	    this.lastForwardHeartbeatTime = getCurrentTimeMillis(); 
	    this.gotChildAck = false; // When a child connection is established we need the child to send a ChildIsAliveMsg instantly when it receives the anycastack to set this variable to true, otherwise all 
	    this.childsPrimaryStripeId = childsPrimaryStripeId;
	}

	public void setLastForwardHeartbeatTime(long val) {
	    gotChildAck = true;
	    lastForwardHeartbeatTime = val;
	}

	public long getLastForwardHeartbeatTime() {
	    return lastForwardHeartbeatTime;
	}

	public boolean isPrimaryContributor() {
	    return isPrimaryContributor;
	}

	public int getChildDegree() {
	    return childDegree;
	}

	public int getChildsPrimaryStripeId() {
	    return childsPrimaryStripeId;
	}

    }



    public class TopicManager {

        protected int stripeId; 
	
	protected SaarTopic saartopic;

	protected Id[] pathToRoot; // The staleness of this entry can be checked via the variable lastReverseHeartbeatTime, since it is the parent's message which updates this value


	protected Id[] lastPushedPathToRoot; // This is the last pathToRoot that was pushed to children. With the help of this field we can push updates on pathToRoot only when necessary. Also note, that a newly added child will be pushed the currPathToRoot. thus you need not keep track of what you pushed to each child individually

	protected Vector children;

	// This keeps track of the children metadata
	protected Hashtable childrenState;
	
	protected NodeHandle parent;

	protected boolean parentEstablishedViaPrimaryChildViolation;

	protected boolean parentViaSpareCapacity; // We set this to true if the parent was acquired via a spare capacity group, that is its parent for this stripe is not a primary stripe for this stripe. Setting this to true enables us to periodically remove such connections when better alternatives exist 
	
	// This is the last time when a reverse heartbeat has received from the parent 
	protected long lastReverseHeartbeatTimeParent; 


	public TopicManager(SaarTopic saartopic, int stripeId) {
	    this.saartopic = saartopic;
	    this.stripeId = stripeId;
	    this.children = new Vector();
	    this.childrenState = new Hashtable();
	    this.lastReverseHeartbeatTimeParent = 0;
	    appendPathToRoot(new Id[0]);
	    this.lastPushedPathToRoot = new Id[0];
	}

	public void initialize() {
	    parent = null;
	    appendPathToRoot(new Id[0]);
	    this.lastPushedPathToRoot = new Id[0];
	    children.clear();
	    childrenState.clear();
	}

	// parent sending to us, it is called 'reverse' since the flow is from top-to-down in the tree
	public long getLastReverseHeartbeatTimeParent() {
	    return lastReverseHeartbeatTimeParent;
	}
	
	// parent sending to us, it is called 'reverse' since the flow is from top-to-down in the tree
	public void setLastReverseHeartbeatTimeParent(long val) {
	    lastReverseHeartbeatTimeParent = val;
	    
	}
	
	// child sending to us, it is called 'forward' since the flow is from upwards in the tree
	public void setLastForwardHeartbeatTimeChild(NodeHandle child, long val) {
	    ChildState cState = (ChildState) childrenState.get(child);
	    if(cState != null) {
		cState.setLastForwardHeartbeatTime(val);
	    } else {
		if(SaarTest.logLevel <= 875) myPrint("multitree: " + "setLastForwardHeartbeatTimeChild() called on unknown child", 875);
	    }
	    
	}
	
	// child sending to us, it is called 'forward' since the flow is from upwards in the tree
	public long getLastForwardHeartbeatTimeChild(NodeHandle child) {
	  ChildState cState = (ChildState) childrenState.get(child);
	  if(cState != null) {
	      return cState.getLastForwardHeartbeatTime();
	  } else {
	      if(SaarTest.logLevel <= 875) myPrint("multitree: " + "getLastForwardHeartbeatTimeChild() called on unknown child", 875);
	      return 0;
	  }
	}

       	// child sending to us, it is called 'forward' since the flow is from upwards in the tree
	public boolean isPrimaryContributor(NodeHandle child) {
	  ChildState cState = (ChildState) childrenState.get(child);
	  if(cState != null) {
	      return cState.isPrimaryContributor();
	  } else {
	      if(SaarTest.logLevel <= 875) myPrint("multitree: " + "isPrimaryContributor() called on unknown child", 875);
	      return false;
	  }
	}
	


	// child sending to us, it is called 'forward' since the flow is from upwards in the tree
	public int getChildDegree(NodeHandle child) {
	  ChildState cState = (ChildState) childrenState.get(child);
	  if(cState != null) {
	      return cState.getChildDegree();
	  } else {
	      if(SaarTest.logLevel <= Logger.WARNING) myPrint("multitree: " + "ERROR: getChildDegree() called on unknown child", Logger.WARNING);
	      System.exit(1);
	      return -1;
	  }
	}


	// child sending to us, it is called 'forward' since the flow is from upwards in the tree
	public int getChildsPrimaryStripeId(NodeHandle child) {
	  ChildState cState = (ChildState) childrenState.get(child);
	  if(cState != null) {
	      return cState.getChildsPrimaryStripeId();
	  } else {
	      if(SaarTest.logLevel <= Logger.WARNING) myPrint("multitree: " + "ERROR: getChildsPrimaryStripeId() called on unknown child", Logger.WARNING);
	      System.exit(1);
	      return -1;
	  }
	}



	public NodeHandle getZeroDegreeChild() {
	    for(int i=0; i< children.size(); i++) {
		NodeHandle child = (NodeHandle)children.elementAt(i);
		if(getChildDegree(child) == 0) {
		    return child;
		}
	    }
	    return null;
	}

	
	public NodeHandle getParent() {
	    return parent;
	}


	public boolean getParentEstablishedViaPrimaryChildViolation() {
	    return parentEstablishedViaPrimaryChildViolation;
	}
    
	public NodeHandle[] getChildren() {
	    return (NodeHandle[]) children.toArray(new NodeHandle[0]);
	}


	public int numChildren() {
	    return children.size();
	}


	public int numNonContributors() {
	    int val = 0;
	    for(int i=0; i< children.size(); i++) {
		NodeHandle child = (NodeHandle)children.elementAt(i);
		if(!isPrimaryContributor(child)) {
		    val ++;
		}
	    }
	    return val;
	}
	
	public Id[] getPathToRoot() {
	    return pathToRoot;
	}





	// This returns the last successful pathToRoot information if it has valid pathToRoot, else returns MAXADVERTISEDTREEDEPTH
	public int getTreeDepth() {
	    if(!hasValidPathToRoot()) {
		return MAXADVERTISEDTREEDEPTH;
	    } else {
		return (pathToRoot.length -2);
	    }

	}


	public int[] getDepthAtNodeHavingChildOfDegree() {
	    boolean[] childOfDegreeExists = new boolean[MultitreeContent.MAXCHILDDEGREE + 1];
	    int ret[] = new int[MultitreeContent.MAXCHILDDEGREE + 1];
	    int currTreeDepth = getTreeDepth();

	    for(int i=0; i< (MultitreeContent.MAXCHILDDEGREE + 1); i++) {
		childOfDegreeExists[i] = false;

	    }

	    for(int i=0; i< children.size(); i++) {
		NodeHandle child = (NodeHandle)children.elementAt(i);
		int childdegree = getChildDegree(child);
		if((childdegree >= 0) && (childdegree <= MultitreeContent.MAXCHILDDEGREE)) {
		    childOfDegreeExists[childdegree] = true;
		}
	    }

	    for(int i=0; i< (MultitreeContent.MAXCHILDDEGREE + 1); i++) {
		if(childOfDegreeExists[i]) {
		    ret[i] = currTreeDepth;
		} else {
		    ret[i] = MAXADVERTISEDTREEDEPTH;
		}
	    }
	    return ret;

	}


	// A valid pathToRoot should include (0x0000) .... (0xlocalnode)
	public boolean hasValidPathToRoot() {
	    if((this.pathToRoot != null) && (this.pathToRoot.length >= 2) && (this.pathToRoot[0].equals(zeroId)) && (this.pathToRoot[pathToRoot.length-1].equals(saarClient.endpoint.getId()))) {
		return true;
	    } else {
		return false;
	    }

	}
	
	
	
	// This appends the local node's Id to the parent's pathToRoot
	public void appendPathToRoot(Id[] parentsPathToRoot) {
	    // build the path to the root for the new node
	    this.pathToRoot = new Id[parentsPathToRoot.length + 1];
	    for(int i=0; i< parentsPathToRoot.length; i++) {
		this.pathToRoot[i] = parentsPathToRoot[i];
	    }
	    //System.arraycopy(parentsPathToRoot, 0, this.pathToRoot, 0, parentsPathToRoot.length);
	    this.pathToRoot[pathToRoot.length -1] = saarClient.endpoint.getId();
	    if(SaarTest.logLevel <= 850) myPrint("multitree: " + "pathToRootAsString: " + getPathToRootAsString(), 850);
	    
	}


	public String getPathToRootAsString() {
	    String s = "currPathToRoot[" + stripeId + "]:" + pathToRoot.length + ", [";
	    for(int i=0; i< pathToRoot.length; i++) {
		s = s + pathToRoot[i] + ","; 
	    }
	    s = s +"]";
	    return s;
	}





	// This appends the local node's Id to the parent's pathToRoot
	public void setLastPushedPathToRoot(Id[] currPathToRoot) {
	    // build the path to the root for the new node
	    this.lastPushedPathToRoot = new Id[currPathToRoot.length ];
	    for(int i=0; i< currPathToRoot.length; i++) {
		this.lastPushedPathToRoot[i] = currPathToRoot[i];
	    }
	    if(SaarTest.logLevel <= 850) myPrint("multitree: " + "lastPushedPathToRootAsString: " + getLastPushedPathToRootAsString(), 850);
	    
	}



	public String getLastPushedPathToRootAsString() {
	    String s = "lastPushedPathToRoot:" + lastPushedPathToRoot.length + ", [";
	    for(int i=0; i< lastPushedPathToRoot.length; i++) {
		s = s + lastPushedPathToRoot[i] + ","; 
	    }
	    s = s +"]";
	    return s;
	}





	// returns true is currPathToRoot is different from the value last pushed
	public boolean requiresPathToRootPush() {
	    if(lastPushedPathToRoot.length != pathToRoot.length) {
		return true;
	    } else {
		for(int i=0; i< pathToRoot.length; i++) {
		    if(!pathToRoot[i].equals(lastPushedPathToRoot[i])) {
			return true;
		    }
		}	

	    }
	    return false;
	}




	public void setParent(NodeHandle handle, boolean parentEstablishedViaPrimaryChildViolation) {
	    NodeHandle prevParent = parent;
	    if(SaarTest.logLevel <= 880) myPrint("multitree: " + "setParent(" + saartopic + ", " + stripeId + ", " +  handle + ", " +  parentEstablishedViaPrimaryChildViolation + " )", 880);
	    if ((prevParent != null) && (handle !=null)) {
		if(SaarTest.logLevel <= 875) myPrint("multitree: " + "Changing parent for saartopic " + saartopic, 875);
	    }
	    
	    parent = handle;
	    this.parentEstablishedViaPrimaryChildViolation = parentEstablishedViaPrimaryChildViolation;
	    appendPathToRoot(new Id[0]);
	    lastReverseHeartbeatTimeParent = getCurrentTimeMillis();
	    
	}
	

	public boolean hasLoops(Id requestorId) {
	    for(int i=0; i< pathToRoot.length; i++) {
		if(pathToRoot[i].equals(requestorId)) {
		    return true;
		}
	    }
	    return false;
	}
	
	


	public boolean containsChild(NodeHandle child) {
	    if(children.contains(child)) {
		return true;
	    } else {
		return false;
	    }
	}

	public boolean gotChildAck(NodeHandle child) {
	    if(children.contains(child)) {
		ChildState cState = (ChildState) childrenState.get(child);
		return cState.gotChildAck;
	    } else {
		if(SaarTest.logLevel <= 875) myPrint("multitree: " + "gotChildAck() called on unknown child", 875);
		return false;
	    }
	}

	

	

	public void addChild(NodeHandle child, boolean isPrimaryContributor, int childDegree, int childsPrimaryStripeId) {
	    if(SaarTest.logLevel <= 880) myPrint("multitree: " + "addChild(" + stripeId + ", " + child + ", " + isPrimaryContributor + ", " + childDegree + ", " + childsPrimaryStripeId + ")" , 880);	    
	    if (!children.contains(child)) {
		if(!childrenState.containsKey(child)) {
		    childrenState.put(child, new ChildState(child, isPrimaryContributor,childDegree, childsPrimaryStripeId));
		}
		children.add(child);
		
	    } 

	}
      


	public void updateChild(NodeHandle child, boolean isPrimaryContributor, int childDegree, int childsPrimaryStripeId) {
	    if(SaarTest.logLevel <= 880) myPrint("multitree: " + "updateChild(" + stripeId + ", " + child + ", " + isPrimaryContributor + ", " + childDegree + ", " + childsPrimaryStripeId + ")" , 880);	    
	    if (children.contains(child)) {
		
		childrenState.put(child, new ChildState(child, isPrimaryContributor,childDegree, childsPrimaryStripeId));
	   
		
	    } else {
		if(SaarTest.logLevel <= 880) myPrint("multitree: " + "updateChild(" + child + ")" + " called on unknown child" , 880);	    
	    }

	}
      
	
	// Returns the removed child
	public NodeHandle removeRandomChild() {
	    if(children.size() > 0) {
		int pos = rng.nextInt(children.size());
		NodeHandle victimchild = (NodeHandle)children.elementAt(pos);
		removeChild(victimchild);		
		return victimchild;
	    } else {
		return null;
	    }
	}


	public void removeChild(NodeHandle child) {
	    if(SaarTest.logLevel <= 880) myPrint("multitree: " + "removeChild(" + stripeId + ", " + child + ")" , 880);	    
	    if(childrenState.containsKey(child)) {
		childrenState.remove(child);
	    }

	    children.remove(child);
	}
    }


    
}


