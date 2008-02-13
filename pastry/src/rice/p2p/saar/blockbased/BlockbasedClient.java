/*
 * Created on May 4, 2005
 */
package rice.p2p.saar.blockbased;


import rice.p2p.saar.*;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.selector.SelectorManager;
//import rice.replay.*;
import java.util.Random;
import java.util.Vector;
import java.util.Hashtable;
import java.util.BitSet; 
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
public class BlockbasedClient extends DataplaneClient {

    public static boolean PIGGYBACKRESPONDBLOCKSNOTIFYMSG = true;

    //public static boolean COOLSTREAMINGCONFIG = false; // when true the nodes are chosen to be homegeneous with maximum outdegree 5 and indegree = 4 

    public static boolean ENABLECOOLSTREAMINGBLOCKRECOVERY = false;
    public static boolean ENABLECOOLSTREAMINGSWARMING = true;

    public static final int FLOODINGTHRESHOLD = 50;

    public static final int REMOVECACHEDBLOCKSTHRESHOLD = 60; 

    public MyBitSet numSequence; 
    //public Hashtable numSequence; // this is a hashtbale of the sequence numbers I got and the number of times I got. We use this to eliminate redundant flooding. A node does not forward a pkt that it had forwarded previpusly. Note that inspite of this, a node may receive a pkt twice. 

    // these 2 variables will be used determine the BMAP moving window
    public long firstPktTime = 0;
    public int firstPktSequence = -1;
    public long firstBroadcastTime = 0;

    //public static final int BANDWIDTHWINDOW = 12; // preferably a multiple of CoolstreamingScribePolicy.M, implying that in BANDWIDTHWINDOW periods, if have a reservation of getting (BANDWIDTHWINDOW/M) pkts from by parent
    public static long BWCONSUMPTIONPERIOD = 16000; // 12 seconds (preferably the same as BANDWIDTHWINDOW)
    public static long MESHDEPTHESTIMATIONPERIOD = 12000; // 12 seconds (preferably the same as BANDWIDTHWINDOW)

    public static boolean FLOODING = false; // this flag when true does the redundant flooding without the actual block-exchange protocol
    
    // This is a control pkt flowing down the Scribe tree telling me what the root is currently publishing
    public int rootSeqNumRecv = -1; 

    public long lastAnycastBlockRecoveryTime; // This is the last time we sent an anycast for block recovery

    public Hashtable blocks; // This hashtable stores the distribution tree of the blocks we receive

    /***  These variables track the current state of the node ****/
    public CoolstreamingBufferMap sendbmap; // This bmap uses ADVERTISED WINDOW and will be sent to neighbors
    public int streamingQuality = 99; // This is based on the left end bits (advertisedwindow - fetchwindow) of the sendBMAP . We DO NOT initialize with '-1' because this might indicate very poor streaming Quality when the system is not bootstrapped (it has not received control pkt on Scribe tree)
    public int avgMeshDepth = -1; 
    public int uStatic = -1;
    public int uDynamic = -1;
    public int numGoodNeighbors = 0; // We notify in the BMAP Exchange the current number of good neighbors to enable the removal of redundant neighbors

    public static long MAXANYCASTPERIOD = 5000; // This means we cannot send more than 1 anycast per MAXANYCASTPERIOD, we employ randmization within this period to ensure that all my parents do not synchronize together to get a block
    public long currAnycastPeriod = 0; //[0,MAXANYCASTPERIOD]


    public static double PUBLISHPERIOD  = 200; // this is the rate at which the multicast source publishes

    public static long BLOCKPERIOD = 200; 


    public static int PARENTSTREAMINGQUALITYTHRESHOLD = -1; // initialized at 25,  set dynamically based on avg grpQuality, if the parent's quality falls below this threshold it will be removed as parent, also this threshold is used to accept/deny requets from prospective children

    public Hashtable numUploaded; // the multicast source keeps track of the number of packets it uploaded for each sequence number, this is to prevent some packets not being sent out at all


    public TopicManager manager;

    
    public long lastPermitUnsubscribeTime = 0; // We do not allow the removal of passive neighbors (on requests from the active saarClient.endpoints using RequestUnsubscribeMessage)

    public long lastRequestUnsubscribeTime = 0; // When we have sent an outstanding (approximately using timeout of 2000) requestunsubscribe message to our active neighbor, then we do not entertain a requestunsubsubscribe from our passive neighbor  



    // This represented as a FIFO queue emulates by outbound bandwidth link, every second we clear off NODEDEGREE requests from the head of the queue
    public Vector outLink = new Vector();

    public Vector outAnycastLink = new Vector(); // this vector is a lower priority vector containing the anycast responses to be sent out

    public double normalizedLinkSize ; 

    public double normalizedAnycastLinkSize ; 

    public Hashtable pendingReceive = new Hashtable();

    public Hashtable pendingAnycastReceive = new Hashtable(); // this is a second priority queue for the anycast blocks

    
    
    public int maximumOutdegree = -1; 
    public static int M = 5; // Nov16, changed default to 5 neighbors this is the normalized outdegree of a node of nodedegree 1, it is also the desired indegree of a node


    public int MAXNEIGHBORREFRESHES; // After this many refreshes it means that the neighbors taken are random in join time instead of early-on members. This threshold is applied on a per join instance
    public int numNeighborRefreshes; // This is the number of neighbor refreshes done since it joined
    public static int ACQUIREDTHRESHOLD = 180000; // We periodically acquire fresh neighbors to make the graph construction random
    

    
    public Random rng = new Random();

    public Random serveblocksrng = new Random();

    public Vector servedBlocks = new Vector();

    
    public long lastServeBlocksInvokationTime = 0;

    public long lastRequestBlocksInvokationTime = 0;

    public long lastNeighborMaintenanceInvokationTime = 0;

    public long lastSendMulticastTopicTime = 0;

    public long lastVirtualSourceAcquiresBlocksTime = 0;

    //public long lastSwarmingInvokationTime = 0;
    public long lastBuffermapCalculationTime = 0;

    public long lastCanRequestBlocksTime = 0;

    public long lastMemoryOptimizeTime = 0;

    

    public Hashtable piggybackRespondblocksnotifymsg;

    public Hashtable toRequestBlocks; // We introduced piggybacking of fragments across fine-grained swarming periods and this stores what blocks should have been but were not requested in the recent past to keep control overhead low


    public long lastSENDBMAPTime = 0;  


    public int lastVirtualBlockGenerated = -1;

    public Random linklossRng = new Random(); 

    public int numpktssent = 0; 


    public static int NUMSTRIPES = 1; 

    public static long ADVERTISINGPERIOD = 200;   // this is polling interval 


    public int minDistanceToSource ; // This is the minimum distance thru the random graph to the source, we compute it by looking at the depth of the blocks traversed thru the graph in the last 30 blocks received 


    public NodeHandle minDepthNeighbor ; // this is the neighbor via which we are getting the shortest path blocks, we dont remove this node 

    public Vector lastFewDepths; // This contains the depths of the last 30 blocks received
    public int MAXSIZE_LASTFEWDEPTHS = 30; 

    public static boolean DOFINEGRAINEDADVERTISING = false; 

    public static boolean RIINDEPENDENTOUTDEGREE = false; // By default the outdegree is dependent on the RI, i.e M * RI. however if we fix it, then it will be (M + 2) this 2 is to enable graph reconstruction  


    // We have two strategies of implementing the right edge of the moving window - synchronized or unsynchronized, in the unsynchronized approach we set it a) most recent sequence number received when SETRIGHTEDGEUSINGNEIGHBORMAXBLOCK is true b) right-edge of source - mindepth of pkts in the last 1 minute when SETRIGHTEDGEUSINGMINDISTANCE is true
    public static boolean UNSYNCHRONIZEDWINDOW = false;

    //public static boolean SETRIGHTEDGEUSINGMINDISTANCE = false;

    public static boolean SETRIGHTEDGEUSINGNEIGHBORMAXBLOCK = true;


    //public static int HYBRIDBLOCKBASEDLAG = 0; // This is to ennsure that the blockbased is lagging the treebased in the hybrid protocol


    public int maxSeqRecv = 0; // this is the maximum sequence number received at local node including out-of-band blocks in hybrid protocol

    public int firstBackgroundPktToRecoverAfterJoin = -1; // This will be set in join()

    public static boolean DAMPENREQUESTBLOCKSMSG = false; // When enabled it reduced the polling overhead in the meshbased

    public static long STALEBMAPTHRESHOLD = 1000; // stale bmaps can occur due to dampening of RequestBlocksMsg

    public static int DATAMSGSIZEINBYTES;

    //public static int SWARMINGRETRYFACTOR ; // Will be set in the constructor  After this factor*PUBLISHPERIOD, the node rerequests

    public static long SWARMINGRETRYTHRESHOLD = 4000; 

    public static long MINSWARMINGINTERVAL = 1000; 

    public double dataplaneriusedbycontrol; 

    public double BACKGROUNDFRACTION = 1.0; // This is how much fraction of the spare bandwidth can be used by background traffic. It is set to 0.75 in hybrid systems to not affect foreground traffic by any chance

    public static boolean ONLYOVERLOADINFO = false; // When tru it disables fine-grained information of bandwidth advertisements in the swarming protocol. A node only use the fact if the neighbor is 'overloaded' or not. 


    public static boolean DYNAMICMININDEGREE = true;

    public static boolean MQDBUDYNAMIC = false; // This is when the 'mqdb' (min-queue-delay-background) threshold which determines if we shall entertain a request from a neighbor or not, is used for udyanmic computation

    public long expbackoffInterval = 500;
    public long maxExpbackoffInterval = 500;

    public long lastAnycastForRepairTime = 0;

    public static boolean BGUNAWAREOFLASTFGPKTRECVTIME = false;

    public static boolean DEGREEBASEDONMESHSUPPLY = true;

    public class PendingUploadState {
	public NodeHandle requestor;
	public RespondBlocksMsg msg;

	public PendingUploadState(NodeHandle requestor, RespondBlocksMsg msg) {
	    this.requestor = requestor;
	    this.msg = msg;
	}

    }

    
    public class NeighborDepths {
	public NodeHandle neighbor;
	public int depth;

	public NeighborDepths(NodeHandle neighbor, int depth) {
	    this.neighbor = neighbor;
	    this.depth = depth;
	}

    }



    public BlockbasedClient(SaarClient saarClient, int tNumber, SaarTopic saartopic, String topicName, int dataplaneType, double nodedegreeControlAndData, boolean amMulticastSource, boolean amVirtualSource) {
	super(saarClient, tNumber, saartopic, topicName, dataplaneType,nodedegreeControlAndData, amMulticastSource, amVirtualSource);


	PUBLISHPERIOD  = BLOCKPERIOD; 

	this.nodedegree = nodedegreeControlAndData;

	// If it is hybrid 
	if(saarClient.DATAPLANETYPE != 3) {

	    BACKGROUNDFRACTION = 0.9; // Added on Nov05-2007. This is less than 1.0 so that due to probabilistic serving of blocks in fraction backgroundbandwidths 

	    
	    if(saarClient.DATAPLANETYPE == 4) {
		PUBLISHPERIOD = rice.p2p.saar.singletree.SingletreeClient.PUBLISHPERIOD;
		//if(!SaarClient.HYBRIDDEBUG) {
		//  HYBRIDBLOCKBASEDLAG = 2 * ((int)(1000/PUBLISHPERIOD)); // Prev value was 2,  Updated on Sep21-2007
		//} else {
		//  HYBRIDBLOCKBASEDLAG = 0;
		//}
		

	    } else if(saarClient.DATAPLANETYPE == 5) {
		PUBLISHPERIOD = ((double)rice.p2p.saar.multitree.MultitreeClient.PUBLISHPERIOD) / ((double)rice.p2p.saar.multitree.MultitreeClient.NUMSTRIPES); // Updated on Nov20 because we used 7 stripes, so changing to use double
	       
		//if(!SaarClient.HYBRIDDEBUG) {
		//  HYBRIDBLOCKBASEDLAG =  5 * ((int)(1000/PUBLISHPERIOD)); // Prev value was 2 * rice.p2p.saar.multitree.MultitreeClient.NUMSTRIPES, Updated on Sep21-2007;
		//} else {
		//  HYBRIDBLOCKBASEDLAG = 0;
		//}
	       
	    }

	}

	// Commented out on Oct02-2007 when we make abrupt leaves the default
	//if(DAMPENREQUESTBLOCKSMSG) {
	//  NEIGHBORDEADTHRESHOLD = 3000000;
	//}




	//if(SaarTest.logLevel <= 880) myPrint("blockbased: " +"Hybrid newly set PUBLISHPERIOD: " + PUBLISHPERIOD + " , HYBRIDBLOCKBASEDLAG: " + HYBRIDBLOCKBASEDLAG + " , NEIGHBORDEADTHRESHOLD: " + SaarClient.NEIGHBORDEADTHRESHOLD, 880);
	if(SaarTest.logLevel <= 880) myPrint("blockbased: " +"Hybrid newly set PUBLISHPERIOD: " + PUBLISHPERIOD, 880);
	


	DATAMSGSIZEINBYTES = (int)(SaarClient.STREAMBANDWIDTHINBYTES / ((1000/PUBLISHPERIOD) * NUMSTRIPES));


	if(SaarTest.logLevel <= 880) myPrint("blockbased: " + "BLOCKPERIOD: " + BLOCKPERIOD + " PUBLISHPERIOD: " + PUBLISHPERIOD + " DATAMSGSIZEINBYTES: " + DATAMSGSIZEINBYTES, 880);


	if(SaarClient.DATAPLANETYPE == 3) {
	    BWCONSUMPTIONPERIOD = (long) (M* PUBLISHPERIOD); // 3 * M * PUBLISHPERIOD;  // Updated on Sep22-2007 to make the dtype4-hybriddebug == dtype3  
	    MESHDEPTHESTIMATIONPERIOD = (long)(M * PUBLISHPERIOD); // 3 * M * PUBLISHPERIOD; // Updated on Sep22-2007 to make the dtype4-hybriddebug == dtype3  
	} else {
	    // in the hybrid we set very fine grained calculation of dynamic utilization
	    BWCONSUMPTIONPERIOD = (long) (M * PUBLISHPERIOD);  
	    MESHDEPTHESTIMATIONPERIOD = (long) (M * PUBLISHPERIOD);
	}

	MAXNEIGHBORREFRESHES = M + 100; // That we refresh 4 extra times after the initial procurement of neighbors


	recomputeMaximumOutdegree();

	if(SaarTest.logLevel <= 880) myPrint("blockbased: INITIAL nodedegree: " + nodedegree + " MAXIMUMOUTDEGREE: " + maximumOutdegree, 880);
	

	blocks = new Hashtable();
	//numSequence = new Hashtable();
	numSequence = new MyBitSet();




	numUploaded = new Hashtable();
	manager = new TopicManager(saartopic);
	piggybackRespondblocksnotifymsg = new Hashtable();
	toRequestBlocks = new Hashtable();
	lastFewDepths = new Vector();
	

	if(this.amMulticastSource) {
	    if(SaarTest.logLevel <= 880) myPrint("blockbased: " +"I AM BLOCKBASED MULTICAST SOURCE", 880);
	}

	if(this.amVirtualSource) {
	    if(SaarTest.logLevel <= 880) myPrint("blockbased: " +"I AM BLOCKBASED VIRTUAL SOURCE", 880);
	}


	saarClient.reqRegister(saartopic, this);
    }



    public long getCurrentTimeMillis() {
	return saarClient.getCurrentTimeMillis();
    }
  


    public void recomputeMaximumOutdegree() {
	if(RIINDEPENDENTOUTDEGREE) {
	    maximumOutdegree = (int)(M + 2); // +2 in order to have the static utilization < 100 and be able to do graph reconstruction
	} else {
	    if(DEGREEBASEDONMESHSUPPLY) {
		maximumOutdegree = (int) ((this.nodedegree - saarClient.viewer.foregroundReservedRI) * M);
	    } else {
		maximumOutdegree = (int) (this.nodedegree * M);
	    }
	    if(maximumOutdegree < M) {
		maximumOutdegree = M;
	    }
	}
	if(SaarTest.logLevel <= 880) myPrint("blockbased: " + "recomputeMaximumOutdegree() " + "nodedegree: " + this.nodedegree + " foregroundRI: " + saarClient.viewer.foregroundReservedRI + " maximumOutdegree: " + maximumOutdegree, 880);

    }


    /************ Abstract Methods in DataplaneClient ************/
 
    public void join() {
	if(SaarTest.logLevel <= 880) myPrint("blockbased: " +"blockbasedclient.join()", 880);
	long currtime = getCurrentTimeMillis();
	rootSeqNumRecv = -1;
	firstPktTime = 0;
	firstPktSequence = -1;

	if(!amMulticastSource) {
	    // This is a temporary hack to know the global sequence number, in reality it will be using the grpSummary code to set the sequence number based on downward propagates in the group
	    if(firstPktSequence == -1) {
		//firstPktSequence = rice.p2p.saar.simulation.SaarSimTest.simulatorbroadcastseqnum - HYBRIDBLOCKBASEDLAG; // We initially assume a large diameter to make sure we get atleast some block
		//firstPktSequence = saarClient.viewer.getBackgroundBroadcastSeqnum() - HYBRIDBLOCKBASEDLAG; 
		firstPktSequence = saarClient.viewer.getBackgroundBroadcastSeqnum() ; // We now resorting to a new way to calculate the timeout to recover missings blocks in hybrid 


		firstPktTime = currtime;
	    }
	}

	lastAnycastForRepairTime = 0;
	expbackoffInterval = 500;
	maxExpbackoffInterval = 500;
	numGoodNeighbors = 0;
	lastPermitUnsubscribeTime = 0;
	outLink.clear();
	pendingReceive.clear();
	lastFewDepths.clear();
	numpktssent = 0;
	numNeighborRefreshes = 0;
	minDistanceToSource = -1; 
	minDepthNeighbor = null; 
	saarClient.reqSubscribe(tNumber);


	firstBackgroundPktToRecoverAfterJoin = saarClient.viewer.getBackgroundBroadcastSeqnum();
	if(SaarTest.logLevel <= 880) myPrint("blockbased: " + "firstBackgroundPktToRecoverAfterJoin(" + firstBackgroundPktToRecoverAfterJoin + ")", 880);
	
    }




   

    public void controlplaneUpdate(boolean forceUpdate) {
	if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"blockbasedclient.controlplaneUpdate()", 875);
	sendbmap = computeSendBMAP();
	streamingQuality = getStreamingQualityToAdvertise(sendbmap);
	uStatic = manager.getUStatic();
	uDynamic = manager.getUDynamic();
	avgMeshDepth = manager.getAvgMeshDepth();
	if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"SENDBMAP " + " BMAP: " + sendbmap + " uStatic: " + uStatic + " uDynamic: " + uDynamic + " StreamingQuality: " + streamingQuality + " AvgMeshDepth: " + avgMeshDepth, 875);
	int selfBroadcastSeq = saarClient.allTopics[tNumber].pSeqNum -1; // this value will be -1 for nonsources always and incremented every timeperiod for the source in the sendMulticastTopic()
	BlockbasedContent saarContent = new BlockbasedContent(SaarContent.UPWARDAGGREGATION, topicName, tNumber, false, 1, sendbmap, uStatic, uDynamic, streamingQuality, avgMeshDepth, selfBroadcastSeq);

	saarClient.reqUpdate(tNumber,saarContent,forceUpdate);


    }


    // This is executed at the granularity of publishperiod
    public void dataplaneMaintenance() {
	if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"dataplaneMaintenance()", 875);
	long currtime = getCurrentTimeMillis();	
	
	if((currtime - lastMemoryOptimizeTime) > 10000) {
	    lastMemoryOptimizeTime = currtime;
	    numSequence.optimizeMemory();
	}



	if(DOFINEGRAINEDADVERTISING) {
	    if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"finegrainedadvertising()", 875);
	    sendBMAP(); // Here it advertises its bmap to its neighbors. for the non-source nodes, we now pigyback this information on RequestBlocksMsg which is issued from requestBlocks()
	}
	
	// This is there to avaoid fine-grained calculation of the datastructures/values below 
	//if((currtime - lastSwarmingInvokationTime) < PUBLISHPERIOD) { // this is to enable finegrained requestblocks Updated on Sep22-2007
	    //  if(SaarTest.logLevel <= 850) myPrint("blockbased: " +" Skipping further swarming()", 850);
	    //return;
	//}
	//lastSwarmingInvokationTime = currtime;

	if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"swarming()", 875);

	if((currtime - lastBuffermapCalculationTime) >= 100) { // this is to enable finegrained requestblocks Updated on Sep22-2007
	    
	    lastBuffermapCalculationTime = currtime; 
	    sendbmap = computeSendBMAP();
	    setRightEdgeOfMovingWindow(); // This will set right edge based of moving window in the pure-mesh protocols, for the hybrids this function will bypass main execution logic since the hybrid protocols's right edge is set via the treebone
	
	    streamingQuality = getStreamingQualityToAdvertise(sendbmap);
	    uStatic = manager.getUStatic();
	    uDynamic = manager.getUDynamic();
	    avgMeshDepth = manager.getAvgMeshDepth();

	}
	


	// We incorporated calculating the sendbmap ... avgMeshDepth again in addiiton to the controlplaneUpdate() code because the latest bmap will be needed to do fine grained publishing


	if(amMulticastSource) {
	    sendMulticastTopic();
	    sendBMAP(); // this is explicitly invoked by the source to advertise its bmap to its neighbors since it will not isse any RequestBlocksMsg() on which the others piggyback this inofrmation
	    neighborMaintenance(); // This is explicitly done by the source
	}

	if(amVirtualSource) {
	    virtualSourceAcquiresBlocks();

	}

	serveBlocks(); // Here it serves the pending blocks requested by our neighbors
	if(!amMulticastSource) {
	    requestBlocks(); // Here it requests neighbors blocks based on their last advertisement
	    neighborMaintenance(); // Here it attempts to repair the mesh structure by invoking 'anycast' for neighbor acquisition. Note the multicast source need not do anything since it is only the 'passive' end of a neighbor connection. The 'active' end refreshes the neighbor connection 
	}


    }




    public void leave() {
	if(SaarTest.logLevel <= 880) myPrint("blockbased: " +"blockbasedclient.leave()", 880);
	if(!amMulticastSource) {
	    saarClient.reqUnsubscribe(tNumber);

	    if(SaarClient.LEAVENOTIFY) {
		// We will also send out CoolstreamingUnsubscribe messages to our parent/children by calling removeParent/removeChild (these mehtods internalls send out these messages)
		
		NodeHandle[] myneighbors = manager.getNeighbors();
		for(int i=0; i< myneighbors.length; i++) {
		    NodeHandle nh = myneighbors[i];
		    removeNeighbor(nh);   // This implicitly send out an UnsubscribeMsg
		}
	    
	    }

	    blocks.clear();

	    //numSequence.clear();
	    numSequence.initialize();

	    numUploaded.clear();
	    pendingReceive.clear();
	    pendingAnycastReceive.clear();
	    piggybackRespondblocksnotifymsg.clear();
	    toRequestBlocks.clear();
	    servedBlocks.clear();
	    numNeighborRefreshes = 0; 
	    firstBackgroundPktToRecoverAfterJoin = -1;
	    manager.initialize();
	    saarClient.viewer.initialize();
	}

    }



    // When an anycast message is delivered at the local node
    public boolean recvAnycast(SaarTopic saartopic, Topic topic, SaarContent content) {
	if(SaarTest.logLevel <= 850) myPrint("blockbased: " +"blockbasedclient.recvAnycast(" + "gId:" + content.anycastGlobalId + ", saartopic:" + saartopic + ", topic:" + topic + ")", 850);

	BlockbasedContent myContent = (BlockbasedContent)content;

	if(saarClient.endpoint.getLocalNodeHandle().equals(myContent.anycastRequestor)) {
	    // We do not accept the anycast made by ourself
	    if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"anycast(self=requestor, ret=false)", 875);
	    return false;
	    
	}

	if(isExistingNeighbor(myContent.anycastRequestor)) {
	    if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"anycast(self=existingneighborofrequestor, ret=false) ", 875);
	    return false;
	    
	}

	if(numNeighbors() >= maximumOutdegree) {
	    if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"anycast(uStaticLimitReached, ret=false) ", 875);
	    return false;
	}
	
	// We check to see if we are getting good performance 
	if((streamingQuality != -1) && (streamingQuality < PARENTSTREAMINGQUALITYTHRESHOLD)) {
	    if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"anycast(streamingQualityPoor(" + streamingQuality + ", ret=false) ", 875);
	    return false;
	}

	// We accept the anycast, add hte new neighbor connection, and notify the anycast requestor
	addNeighbor(myContent.anycastRequestor, false, false);
	if(amMulticastSource) {
	    myContent.amMulticastSource = true; // Setting this field prevents timing out this neighbor connection, the other non-source connections are timed out in order to update the mesh structure with time
	}
	
	saarClient.endpoint.route(null, new MyAnycastAckMsg(myContent, saarClient.endpoint.getLocalNodeHandle(), saartopic.baseTopic), myContent.anycastRequestor);
    

	// Since our state has change we proactively invoke controlplaneUpdate to update local state on the local node. Note that this does not mean that the update will be sent to the parent immediately
	controlplaneUpdate(true); 



	return true;
    }



    // When a SAAR Publish message (used for downward propagation) is delivered at the local node
    public void grpSummary(SaarTopic saartopic, Topic topic, SaarContent content) {
	if(SaarTest.logLevel <= 850) myPrint("blockbased: " +"blockbasedclient.grpSummary(" + "saartopic:" + saartopic + ", topic:" + topic + ")", 850);
	BlockbasedContent myContent = (BlockbasedContent) content;
	int topicNumber = myContent.tNumber; 
	if(tNumber != topicNumber) {
	    System.out.println("tNumber:" + tNumber + ", topicNumber:" + topicNumber);
	    System.out.println("ERROR: The tNumbers should match, else indicates some error in aggregation");
	    System.exit(1);
	}
	int saardepth = myContent.numScribeIntermediates;
	long currtime = getCurrentTimeMillis();
	if(SaarTest.logLevel <= 850) myPrint("blockbased: " +"sourceIsBroadcasting(" + "tNumber: " + topicNumber+ "," + currtime + ", saardepth: " + saardepth + ", sourceBroadcastSeq: " + myContent.sourceBroadcastSeq+ ", grpSize: " + myContent.descendants + ", grpQuality: " + myContent.streamingQuality + ", grpAvgMeshDepth: " + myContent.avgMeshDepth + ", grpUStatic: " + myContent.uStatic + ", grpUDynamic: " + myContent.uDynamic + " )", 850);
	if(myContent.streamingQuality > 0) {
	    //PARENTSTREAMINGQUALITYTHRESHOLD = myContent.streamingQuality - 25; // Commented on Aug 16-2007
	    //if(PARENTSTREAMINGQUALITYTHRESHOLD < 50) { // Commented on Aug 16-2007
	    //PARENTSTREAMINGQUALITYTHRESHOLD = 50; // Commented on Aug 16-2007
	    //}
	}

	rootSeqNumRecv = myContent.sourceBroadcastSeq;	    
	if(firstPktSequence == -1) {
	    firstPktSequence = myContent.sourceBroadcastSeq;
	    firstPktTime = currtime;
	}

	//int selfestimatedRWindow = firstPktSequence + (int)((currtime - firstPktTime)/PUBLISHPERIOD);
	//int controlPktRWindow = rootSeqNumRecv;
	//if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"RWINDOW-ESTIMATION:" + "controlPkt:" + controlPktRWindow + ", selfestimate: " + selfestimatedRWindow, 875);

    }




    // This notifies us when we receive a failure for a anycast
    public void recvAnycastFail(SaarTopic saartopic, Topic topic, NodeHandle failedAtNode, SaarContent content) {
	if(SaarTest.logLevel <= 880) myPrint("blockbased: " +"blockbasedclient.recvAnycastFail()", 880);

    }




    public void recvDataplaneMessage(SaarTopic saartopic, Topic topic, SaarDataplaneMessage message) {
	if(message instanceof MyAnycastAckMsg) {

	    expbackoffInterval = 500;
	    maxExpbackoffInterval = 500;

	    MyAnycastAckMsg  ackMsg = (MyAnycastAckMsg)message;
	    BlockbasedContent content = (BlockbasedContent) ackMsg.content;

	    if(SaarTest.logLevel <= 880) myPrint("blockbased: " +" Received " + ackMsg, 880);

	    if(!isExistingNeighbor(ackMsg.getSource())) {
		addNeighbor(ackMsg.getSource(),true,content.amMulticastSource);
	    } else {
		if(SaarTest.logLevel <= 875) myPrint("blockbased: " +saarClient.endpoint.getId() + ": Received AnycastAck from existing neighbor " + ackMsg.getSource(), 875);
	    }
	    
	}


	
	if(message instanceof CoolstreamingRequestUnsubscribeMessage) {
	    CoolstreamingRequestUnsubscribeMessage reqUnsubMsg = (CoolstreamingRequestUnsubscribeMessage) message;
	    long currtime = getCurrentTimeMillis();
	    if((numGoodNeighbors > getDesiredIndegree()) &&  (!reqUnsubMsg.getSource().equals(minDepthNeighbor)) && ((currtime - lastRequestUnsubscribeTime) > 2000) && ((currtime - lastPermitUnsubscribeTime) > 2000) && (isExistingNeighbor(reqUnsubMsg.getSource()))) {
		if(SaarTest.logLevel <= 875) myPrint("blockbased: " +" Permitting RequestUnsubscribeMessage from neighbor " + reqUnsubMsg.getSource() , 875);
		saarClient.endpoint.route(null, new CoolstreamingReplyUnsubscribeMessage(saarClient.endpoint.getLocalNodeHandle(), reqUnsubMsg.getTopic(), true), reqUnsubMsg.getSource());
		lastPermitUnsubscribeTime = currtime; 


	    } else {
		if(SaarTest.logLevel <= 875) myPrint("blockbased: " +" Denying RequestUnsubscribeMwessage from neighbor " + reqUnsubMsg.getSource() , 875);
		saarClient.endpoint.route(null, new CoolstreamingReplyUnsubscribeMessage(saarClient.endpoint.getLocalNodeHandle(), reqUnsubMsg.getTopic(), false), reqUnsubMsg.getSource());
	    }
	}


	if(message instanceof CoolstreamingReplyUnsubscribeMessage) {
	    CoolstreamingReplyUnsubscribeMessage replyUnsubMsg = (CoolstreamingReplyUnsubscribeMessage) message;
	    if(SaarTest.logLevel <= 875) myPrint("blockbased: " +" Received ReplyUnsubscribeMessage from neighbor " + replyUnsubMsg.getSource() + " permitted: " + replyUnsubMsg.permitted , 875);
	    if(replyUnsubMsg.permitted && (numGoodNeighbors > getDesiredIndegree())) {
		removeNeighbor(replyUnsubMsg.getSource());
	    }
	    
	    
	}
	
	// We can get an CSMUnsubscribe message either from our child or from our parent
	if(message instanceof CoolstreamingUnsubscribeMessage) {
	    CoolstreamingUnsubscribeMessage csmUnsubscribeMessage = (CoolstreamingUnsubscribeMessage) message;
	    if(SaarTest.logLevel <= 875) myPrint("blockbased: " +saarClient.endpoint.getId() + ": Received CoolstreamingUnsubscribe message from " + csmUnsubscribeMessage.getSource(), 875);
	    NodeHandle requestor = csmUnsubscribeMessage.getSource();

	    if(manager.containsNeighbor(requestor)) {
		manager.removeNeighbor(requestor);
	    } else {
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +" WARNING: Received CoolstreamingUnsubscribeMessage from " + requestor + " who is not a neighbor", Logger.WARNING);
	    } 
	}
	



	if(message instanceof BMAPAvailabilityMsg) {
	    BMAPAvailabilityMsg bmapMsg = (BMAPAvailabilityMsg) message;
	    //if(isExistingParent(bmapMsg.getTopic(),bmapMsg.getSource())) {
	    if(isExistingNeighbor(bmapMsg.getSource())) {
		CoolstreamingBufferMap pBMAP = bmapMsg.getBMAP();
		int pStatic = bmapMsg.getUStatic();
		int pDynamic = bmapMsg.getUDynamic();
		int pStreamingQuality = bmapMsg.getStreamingQuality();
		int pAvgMeshDepth = bmapMsg.getAvgMeshDepth();
		int pNumGoodNeighbors = bmapMsg.getNumGoodNeighbors();
		String pendingNotifySeqNumsString = bmapMsg.getPendingNotifySeqNumsString();
		
		if(SaarTest.logLevel <= 850) myPrint("blockbased: " +" Received BMAPAvailabilityMsg  from neighbor " + bmapMsg.getSource() + " BMAP: " + pBMAP + " pStatic: " + pStatic + " pDynamic: " + pDynamic + " pStreamingQuality: " + pStreamingQuality + " pAvgMeshDepth: " + pAvgMeshDepth + " pNumGoodNeighbors: " + pNumGoodNeighbors + " pN: " + pendingNotifySeqNumsString, 850);

		// We will also update this bitmap/uStatic/uDynamic corresponding to the parent
		//Topic myTopic = bmapMsg.getTopic();
		//TopicManager manager = (TopicManager) topics.get(myTopic);
		manager.setBMAPNeighbor(bmapMsg.getSource(),pBMAP);
		manager.setUStaticNeighbor(bmapMsg.getSource(),pStatic);
		manager.setUDynamicNeighbor(bmapMsg.getSource(),pDynamic);
		manager.setStreamingQualityNeighbor(bmapMsg.getSource(), pStreamingQuality);
		manager.setAvgMeshDepthNeighbor(bmapMsg.getSource(), pAvgMeshDepth);
		manager.setLastForwardHeartbeatTimeNeighbor(bmapMsg.getSource(),getCurrentTimeMillis());
		manager.setNumGoodNeighborsNeighbor(bmapMsg.getSource(),pNumGoodNeighbors);
		
		// We will also retrieve the piggy backed information of pendingNotify (i.e the blocks that will be uploaded by this neighbor shortly. The logic here is similar to that used in RespondBlocksNotifyMsg, except that when this information is piggybacked instead of relying explicity on a separate message to do so

		Vector neighborsPendingNotify = bmapMsg.getPendingNotifySeqNums();
		for(int i=0; i< neighborsPendingNotify.size();i++) {
		   int seqNum = ((Integer) neighborsPendingNotify.elementAt(i)).intValue();
		   if(SaarTest.logLevel <= 875) myPrint("blockbased: " +" PendingReceive: " + seqNum, 875);
		   pendingReceive.put(new Integer(seqNum),new Long(getCurrentTimeMillis()));
		   if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"pendingReceive.put(" + seqNum + ") because of BMAPAvailabilityMsg", 875);
		}
	    
	    
	    } else {
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +saarClient.endpoint.getId() + ": Received BMAPAvailabilityMsg from unknown neighbor " + bmapMsg.getSource(), Logger.WARNING);
	    }
	    
	}

	if(message instanceof RequestBlocksMsg) {

	    if(!saarClient.allTopics[tNumber].isSubscribed) {
		// We have leaft the grouyp by calling leave()
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +"Already invoked leave(), thus not entertaining RequestBlocksMsg", Logger.WARNING);
		return;

	    }

	    
	    RequestBlocksMsg reqBlocksMsg = (RequestBlocksMsg) message;
	    NodeHandle requestor = reqBlocksMsg.getSource();
	    // We make sure the requestor is one of our children
	    //TopicManager manager = (TopicManager) topics.get(reqBlocksMsg.getTopic());
	    if(!manager.isExistingNeighbor(requestor)) {
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +" WARNING: The blocks requestor " + requestor + " is not my neighbor/child", Logger.WARNING);
		return;
	    }

	    long currQueueDelay = saarClient.computeQueueDelay();

	    // We assume here that people only request the blocks that we had advertised earlier, thus we do not explicitly check to see if the block is in our BMAP
	    String requestBlocksString =  reqBlocksMsg.requestBlocksAsString();
	    CoolstreamingBufferMap pBMAP = reqBlocksMsg.getBMAP();
	    int pStatic = reqBlocksMsg.getUStatic();
	    int pDynamic = reqBlocksMsg.getUDynamic();
	    int pStreamingQuality = reqBlocksMsg.getStreamingQuality();
	    int pAvgMeshDepth = reqBlocksMsg.getAvgMeshDepth();
	    int pNumGoodNeighbors = reqBlocksMsg.getNumGoodNeighbors();
	    boolean pNeedFreshBMAP = reqBlocksMsg.getNeedFreshBMAP();
	    String pendingNotifySeqNumsString = reqBlocksMsg.getPendingNotifySeqNumsString();


	    manager.setBMAPNeighbor(reqBlocksMsg.getSource(),pBMAP);
	    manager.setUStaticNeighbor(reqBlocksMsg.getSource(),pStatic);
	    manager.setUDynamicNeighbor(reqBlocksMsg.getSource(),pDynamic);
	    manager.setStreamingQualityNeighbor(reqBlocksMsg.getSource(), pStreamingQuality);
	    manager.setAvgMeshDepthNeighbor(reqBlocksMsg.getSource(), pAvgMeshDepth);
	    manager.setLastForwardHeartbeatTimeNeighbor(reqBlocksMsg.getSource(),getCurrentTimeMillis());
	    manager.setNumGoodNeighborsNeighbor(reqBlocksMsg.getSource(),pNumGoodNeighbors);
	    //if(SaarTest.logLevel <= 875) myPrint("blockbased: " +" should print Sep12a ", 875);
	    manager.setNeedFreshBMAPNeighbor(reqBlocksMsg.getSource(), pNeedFreshBMAP);
	    //if(SaarTest.logLevel <= 875) myPrint("blockbased: " +" should print Sep12b ", 875);


	    if(pNeedFreshBMAP) {
		if(SaarTest.logLevel <= 875) myPrint("blockbased: " +" Received request for fresh BMAP from " + reqBlocksMsg.getSource(), 875);
	    }



	    // We will also retrieve the piggy backed information of pendingNotify (i.e the blocks that will be uploaded by this neighbor shortly. The logic here is similar to that used in RespondBlocksNotifyMsg, except that when this information is piggybacked instead of relying explicity on a separate message to do so
	    
	    Vector neighborsPendingNotify = reqBlocksMsg.getPendingNotifySeqNums();
	    for(int i=0; i< neighborsPendingNotify.size();i++) {
		int seqNum = ((Integer) neighborsPendingNotify.elementAt(i)).intValue();
		if(SaarTest.logLevel <= 850) myPrint("blockbased: " +" PendingReceive: " + seqNum, 850);
		pendingReceive.put(new Integer(seqNum),new Long(getCurrentTimeMillis()));
		if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"pendingReceive.put(" + seqNum + ") because of piggybacked info in RequestBlocksMsg", 875);
	    }
	    

	    //if(SaarTest.logLevel <= 875) myPrint("blockbased: " +" shouldprint Received " + reqBlocksMsg, 875);
	    if(reqBlocksMsg.requestBlocks.size() > 0) {
		if(SaarTest.logLevel <= 875) myPrint("blockbased: " +" Received RequestBlocksMsg  from neighbor " + reqBlocksMsg.getSource() + " RequestBlocks: " + requestBlocksString + " BMAP: " + pBMAP + " pStatic: " + pStatic + " pDynamic: " + pDynamic + " pStreamingQuality: " + pStreamingQuality + " pAvgMeshDepth: " + pAvgMeshDepth + " pNumGoodNeighbors: " + pNumGoodNeighbors + " pN: " + pendingNotifySeqNumsString + " pNeedFreshBMAP: " + pNeedFreshBMAP, 875);
	    }

	    Vector criticalBlocks = new Vector();
	    if(amMulticastSource || amVirtualSource) {
		// We first compute which are the critical blocks that need to go out using REQUEST-OVERRIDING
		criticalBlocks = getCriticalBlocksToUpload();
		if(criticalBlocks.size() > 0) {
		    String criticalBlocksAsString = blocksAsString(criticalBlocks);
		    if(SaarTest.logLevel <= 850) myPrint("blockbased: " +"CriticalBlocksToUpload: " + criticalBlocksAsString, 850);
		}
	    } 



	    CoolstreamingBufferMap requestorBMAP = reqBlocksMsg.getBMAP();
	    // Check to see if we have a critical block that is not in the BMAP of requestor
	    boolean doRequestOverriding = false;
	    int requestOverridingSeqNum = -1;
	    if(reqBlocksMsg.requestBlocks.size() > 0) {
		for(int i=0; i< criticalBlocks.size(); i++) {
		    int seqNum = ((Integer)criticalBlocks.elementAt(i)).intValue();
		    //if(SaarTest.logLevel <=850) myPrint("blockbased: " +"CriticalBlockSeqnum: " + seqNum + ", " + requestorBMAP, 850);
		    //if(!requestorBMAP.containsSeqNum(seqNum)) {
		    if(!mymapContainsSeqNum(requestorBMAP,seqNum)) {
			doRequestOverriding = true;
			requestOverridingSeqNum = seqNum;
			break; // We did not have this break before
		    }
		}
	    }


	    manager.setLastForwardHeartbeatTimeNeighbor(requestor,getCurrentTimeMillis());
	    Topic myTopic = reqBlocksMsg.getTopic();
	    // We will satisfy the requests marked PRIMARY, for the SECONDARY ones we will satisfy atmost NodeDegree requests. We assume for now we satisfy only the primary
	    Vector respondBlocks = new Vector();
	    Vector deniedBlocks = new Vector(); // Added on Dec27-2007
	    if(doRequestOverriding) {
		RequestTuple rTuple = (RequestTuple) reqBlocksMsg.requestBlocks.elementAt(0);
		if(SaarTest.logLevel <= 875) myPrint("blockbased: " +" RequestOverriding: " + "reqSeqNum: " + rTuple.seqNum + ", overridedSeqNum: " + requestOverridingSeqNum, 875);
		rTuple.seqNum = requestOverridingSeqNum;
		Block cachedBlock = (Block)blocks.get(new Integer(rTuple.seqNum));
		if(cachedBlock == null) {
		    if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +" ERROR: CachedBlock#: " + rTuple.seqNum + " is null, at REQUESTOVERRIDING request", Logger.WARNING);
		    System.out.println("ERROR: Setting the block to be null, this implies that the block was deleted from the temporary cache to early by mistake, REQUESTOVERRIDING, at localnode " + saarClient.endpoint.getLocalNodeHandle());
		    System.exit(1);
		}
		
		rTuple.setResponderBlock(cachedBlock);
		respondBlocks.add(rTuple);
		// incr bandwidth consumption
		willUploadBlock(rTuple.seqNum);
	    } else {
		for(int i=0; i< reqBlocksMsg.requestBlocks.size(); i++) {
		    RequestTuple rTuple = (RequestTuple) reqBlocksMsg.requestBlocks.elementAt(i);
		    boolean disallow = false;

		    Block cachedBlock = (Block)blocks.get(new Integer(rTuple.seqNum));
		    if(cachedBlock == null) {
			if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +" WARNING: CachedBlock#: " + rTuple.seqNum + " is null", Logger.WARNING);

			disallow = true;
		    }


		    if(currQueueDelay > SaarClient.MAXQUEUEDELAYBACKGROUND) {
			if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +" WARNING: Uplink fully utilized, denying request#: " + rTuple.seqNum , Logger.WARNING);

			disallow = true;
		    }


		    // If we are the source in addition to implementing strict request overriding, we do not allow some sequence number to be uploaded more than others, thus giving all sequence numbers a fair share
		    if(amMulticastSource) {
			Object obj = numUploaded.get(new Integer(rTuple.seqNum));
			if(obj == null) {
			    numUploaded.put(new Integer(rTuple.seqNum), new Integer(0));
			}
			int currVal = ((Integer)numUploaded.get(new Integer(rTuple.seqNum))).intValue();

			// Commented out on Oct28-2007
			//if(nodedegree != ((int)nodedegree)) { // previously was maxoutdegree/M when maxooutdegree was M*nodedegree
			    // We may be running in a homogeneous setting, but here this code executes for the multicast source only
			//  System.out.println("ERROR: nodedegree is fractional");
			//  System.exit(1);
			//}
			if(currVal >= ((int)nodedegree)) {  
			    if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"Source disallowing request for strict fair sharing of bandwidth across sequence numbers, child: " + requestor, 875);
			    disallow = true;
			}
			
		    }
		    if(!disallow) {
			if(rTuple.type == RequestTuple.PRIMARY) {
			    rTuple.setResponderBlock(cachedBlock);
			    respondBlocks.add(rTuple);
			    // incr bandwidth consumption
			    willUploadBlock(rTuple.seqNum);
			    //manager.incrBWConsumption(new BWTuple(1,getCurrentTimeMillis()));
			}
			// We respond to the SECONDARY requests based on bandwidth availability
			if(rTuple.type == RequestTuple.SECONDARY) {
			    //int uDynamic = manager.getUDynamic();
			    //if(uDynamic < 100) {    // we rely on the pusvi ouhback from the uDynamic to allow requests from the node

			    rTuple.setResponderBlock(cachedBlock);
			    respondBlocks.add(rTuple);
			    // incr bandwidth consumption
			    willUploadBlock(rTuple.seqNum);
			    //manager.incrBWConsumption(new BWTuple(1,getCurrentTimeMillis()));
			}
		    } else {
			// We will send an explicit NAK for the block that was disallowed
			deniedBlocks.add(rTuple);


		    }
		}
	    }
	    
	    
	    //if(respondBlocks.size() > 1) {
	    //if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +"ERROR: We assume responding one block at maximum per RequestBlocksMsg", Logger.WARNING);
	    //System.out.println("ERROR: Requesting multiple fragments in a single swarming period is not allowed");
	    //System.exit(1);
	    //}


	    

	    RespondBlocksNotifyMsg resBlocksNotifyMsg = new RespondBlocksNotifyMsg(saarClient.endpoint.getLocalNodeHandle(), myTopic, respondBlocks);
	    if(respondBlocks.size() > 0) {

		for(int j=0; j< respondBlocks.size(); j++) {
		    Vector msgrespondblocks = new Vector();
		    msgrespondblocks.add(respondBlocks.elementAt(j));
		    RespondBlocksMsg resBlocksMsg = new RespondBlocksMsg(saarClient.endpoint.getLocalNodeHandle(), myTopic, msgrespondblocks);
		    // Note that although the requests are piggybacked, the RespondBlocksMsg separates out the requests because the outlink estimates its queuesize to check amount of pending requests etc. Therefore grouping multiple requests as a single queue item is misleading
		    outLink.add(new PendingUploadState(requestor,resBlocksMsg));
		}

		if(!PIGGYBACKRESPONDBLOCKSNOTIFYMSG) {
		    if(SaarTest.logLevel <= 875) myPrint("blockbased: " +" Sending RespondBlocksNotifyMsg " + resBlocksNotifyMsg + " to requestor " + requestor, 875);
		    saarClient.endpoint.route(null, resBlocksNotifyMsg, requestor);	    
		} else {
		    // We will store this information and will piggyback it on the BMAPAvailabilityMsg
		    if(piggybackRespondblocksnotifymsg.containsKey(requestor)) {
			Vector pendingNotify = (Vector) piggybackRespondblocksnotifymsg.get(requestor);
			for(int i=0; i< respondBlocks.size(); i++) {
			    RequestTuple rTuple = (RequestTuple) respondBlocks.elementAt(i);
			    pendingNotify.add(rTuple);
			}
			if(pendingNotify.size() > 500) { // We increased this from 5 to 50 on Nov01-2007 . This is because when using the same queue for control and data, the control msgs to a neighbor requesting different blocks could get stacked up behing a datamsg. Then a blocks of 5 control RequestBlocksMsg when received at the other node, would cause the Exception to be fired. So we increased from 5 to 50
			    if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +"ERROR: We assume that the respondblocksnotifymsg will be piggybacked in the next BMAPAvailability msg, so the size of this vector should not grow", Logger.WARNING);
			    System.out.println("blockbased: " +"ERROR: In " + saarClient.endpoint.getLocalNodeHandle() + " We assume that the respondblocksnotifymsg will be piggybacked in the next BMAPAvailability msg, so the size of this vector should not grow");
			    //System.exit(1);  
			}
		    } else {
			piggybackRespondblocksnotifymsg.put(requestor,respondBlocks);
		    }
		}

	    }


	    if(deniedBlocks.size() > 0 ) {
		DeniedBlocksNotifyMsg deniedBlocksNotifyMsg = new DeniedBlocksNotifyMsg(saarClient.endpoint.getLocalNodeHandle(), myTopic, deniedBlocks);

		saarClient.endpoint.route(null, deniedBlocksNotifyMsg, requestor);	    

	    }



	}
	

	if(message instanceof RespondBlocksMsg) {
	    RespondBlocksMsg resBlocksMsg = (RespondBlocksMsg) message;
	    if(amVirtualSource) {
		if(SaarTest.logLevel <= 875) myPrint("blockbased: " +" Received " + resBlocksMsg, 875);
	    }
	    if(SaarTest.logLevel <= 850) myPrint("blockbased: " +" Received " + resBlocksMsg, 850);

	    NodeHandle responder = resBlocksMsg.getSource();
	    // We make sure the requestor is one of our children
	    for(int i=0; i< resBlocksMsg.respondBlocks.size();i++) {
		RequestTuple rTuple = (RequestTuple) resBlocksMsg.respondBlocks.elementAt(i);
		Block responderBlockClone = new Block(rTuple.responderBlock);
		responderBlockClone.addToPath(saarClient.bindIndex, saarClient.jvmIndex, saarClient.vIndex);
		int seqNum = rTuple.seqNum;
		//String blockId = "Stripe:" + "0" + "Seq:" + seqNum;
		blocks.put(new Integer(seqNum), responderBlockClone);

		// We will cancel off the corresponding entry in 'pendingReceive'

		pendingReceive.remove(new Integer(seqNum));  // We retained the use of seqnum instead of stripeid/seqnum since the request protocl does not understand stripeIds yet

		if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"pendingReceive.remove(" + seqNum + ") because of RespondBlocksMsg", 875);
		int numPkts = 0; 

		
		//if(numSequence.containsKey(new Integer(seqNum))) {
		//  numPkts = ((Integer)numSequence.get(new Integer(seqNum))).intValue();
		//} 
		//numSequence.put(new Integer(seqNum), new Integer(numPkts + 1));
		numSequence.set(seqNum);
	       



		// We will keep track of the distribution tree of this block , in case of duplicates we retain only the last
		coolstreamingDeliver(0, seqNum, -1, responder, numPkts +1, responderBlockClone); 
	    }
	}


	if(message instanceof RespondBlocksNotifyMsg) {
	    RespondBlocksNotifyMsg resBlocksNotifyMsg = (RespondBlocksNotifyMsg) message;
	    if(SaarTest.logLevel <= 850) myPrint("blockbased: " +" Received " + resBlocksNotifyMsg, 850);
	    NodeHandle responder = resBlocksNotifyMsg.getSource();
	    // We make sure the requestor is one of our children
	    //TopicManager manager = (TopicManager) topics.get(resBlocksNotifyMsg.getTopic());
	    for(int i=0; i< resBlocksNotifyMsg.respondBlocks.size();i++) {
		RequestTuple rTuple = (RequestTuple) resBlocksNotifyMsg.respondBlocks.elementAt(i);
		int seqNum = rTuple.seqNum;
		if(SaarTest.logLevel <= 875) myPrint("blockbased: " +" PendingReceive: " + seqNum, 875);
		pendingReceive.put(new Integer(seqNum),new Long(getCurrentTimeMillis()));
		if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"pendingReceive.put(" + seqNum + ") because of RespondBlocksNotifyMsg", 875);
	    }
	}



	if(message instanceof DeniedBlocksNotifyMsg) {
	    DeniedBlocksNotifyMsg deniedBlocksNotifyMsg = (DeniedBlocksNotifyMsg) message;
	    if(SaarTest.logLevel <= 850) myPrint("blockbased: " +" Received " + deniedBlocksNotifyMsg, 850);
	    NodeHandle responder = deniedBlocksNotifyMsg.getSource();
	    // We make sure the requestor is one of our children
	    //TopicManager manager = (TopicManager) topics.get(resBlocksNotifyMsg.getTopic());
	    for(int i=0; i< deniedBlocksNotifyMsg.deniedBlocks.size();i++) {
		RequestTuple rTuple = (RequestTuple) deniedBlocksNotifyMsg.deniedBlocks.elementAt(i);
		int seqNum = rTuple.seqNum;
		if(SaarTest.logLevel <= 880) myPrint("blockbased: " +" DeniedBlock: " + seqNum, 880);
		pendingReceive.remove(new Integer(seqNum));
		if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"pendingReceive.remove(" + seqNum + ") because of DeniedBlocksNotifyMsg", 875);
	    }
	}





    }



    /****** Abstract methods in DataplaneClient end here ************/


    /****** Helper methods specific to BlockbasedClient begin here ************/



  
    // It is possible that the node has already received this block and that this notification from the channelviewer was because of the initial singletreedeliver at this dataplane itself. So here we increment the count to 1 only if it was zero and do not increase the number of duplicates
    public void alreadyReceivedSequenceNumber(int seqNum) {
	long currtime = getCurrentTimeMillis();
	// We keep track of the maximum sequence number to enable setting the right edge in the hybrid protocol
	if(seqNum > maxSeqRecv) {
	    maxSeqRecv = seqNum;
	}
	//String blockId = "Stripe:" + stripeId + "Seq:" + seqNum;
	//if(!numSequence.containsKey(new Integer(seqNum))) {
	if(!numSequence.get(seqNum)) {
	    //numSequence.put(new Integer(seqNum), new Integer(1));
	    numSequence.set(seqNum);
	    Block block = new Block(0, seqNum);
	    block.setRecoveredOutOfBand();
	    block.addToPath(saarClient.bindIndex, saarClient.jvmIndex, saarClient.vIndex);
	    //blocks.put(new Integer(seqNum), block);
	    blocks.put(allocInteger(seqNum), block);
	}

	// In the hybrid protocols we also use this to set the right edge via the latest pkt received on the treebone
	if((saarClient.DATAPLANETYPE == 4) || (saarClient.DATAPLANETYPE == 5)) {
	    if(UNSYNCHRONIZEDWINDOW) {
		//firstPktSequence = saarClient.viewer.getLastForegroundPktRecvSeqnum() - HYBRIDBLOCKBASEDLAG;

		if((currtime - saarClient.viewer.getLastForegroundPktRecvTimeAcrossAllStripes()) < 5000) {  // Received a pkt in the last 5 sec
		    firstPktSequence = saarClient.viewer.getLastForegroundPktRecvSeqnumAcrossAllStripes(); // Updated on Dec13-2007, we use a new way to calculate timeout to recover missing blocks in the hybrid
		    firstPktTime = saarClient.viewer.getLastForegroundPktRecvTimeAcrossAllStripes();
		    if((firstPktTime == 0) && amMulticastSource) {
		    if(SaarTest.logLevel <= 880) myPrint("blockbased: " +"ERROR: getLastForegroundPktRecvTime at multicastsource returned zero", 880);
		    }
		    
		    if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"READJUSTING RIGHTEDGE to " + firstPktSequence + " REFTIME:" + firstPktTime, 875);		
		}
	    }
	}

    }



    public Integer allocInteger(int seqNum) {
	return new Integer(seqNum);
    }

	
	







    // parent - the neighbor who forwarded me this packet
    // numPkts - the total number of pkts of this sequence number received
    // depth - this is NOT depth of the block path, if the pkts are being flooded on the mesh using the PublishMessage, then this is that depth
    // 

    public void coolstreamingDeliver(int stripeId, int seqNum, int depth, NodeHandle parent, int numPkts, Block block) {
	long currtime = getCurrentTimeMillis();
	if(SaarTest.logLevel <= 880) myPrint("blockbased: " +"coolstreamingdeliver("+ stripeId + "," + currtime + "," + depth + "," + parent + "," + seqNum + "," + numPkts + "," + block + ")" , 880);

	saarClient.viewer.receivedSequenceNumber(seqNum, dataplaneType);

	lastFewDepths.add(new NeighborDepths(parent,block.getDepth()));
	if(lastFewDepths.size() > MAXSIZE_LASTFEWDEPTHS) {
	    lastFewDepths.remove(0);
	}

	if(!block.anycastShortCircuit) {
	    MeshDepthTuple meshdepthTuple = new MeshDepthTuple(block.getDepth(),currtime);
	    manager.updateMeshDepthEstimation(meshdepthTuple);
	}

	//if(UNSYNCHRONIZEDWINDOW && !amMulticastSource) {
	//  firstPktSequence = seqNum;
	//  firstPktTime = currtime;
	//}

    }

    
    public int getDesiredIndegree() {
	if(!DYNAMICMININDEGREE) {
	    return M;
	} else {
	    int val = (maximumOutdegree - 2);
	    if(val < M) {
		val = M;
	    }
	    return val;
	}

    }




    // Returns 1 if we actually did multicast (i.e if we were root)
    public int sendMulticastTopic() {

	
	long currtime = getCurrentTimeMillis();
	//if((currtime - lastSendMulticastTopicTime) < PUBLISHPERIOD) { // this is to enable finegrained requestblocks
	//  if(SaarTest.logLevel <= 850) myPrint("blockbased: " +" Skipping sendMulticastTopic()", 850);
	//  return 0;
	//}



	if(SaarTest.logLevel <= 875) myPrint("blockbased: " +" sendMulticastTopic()", 875);
	
	Topic myTopic = saartopic.baseTopic;
	long currTime = getCurrentTimeMillis();
	int currSeq = saarClient.allTopics[tNumber].pSeqNum;
	String myTopicName = topicName;
	int seqNumDue = (int) (((double)(currTime - firstBroadcastTime))/((double)PUBLISHPERIOD));
	if((currSeq ==0) || (currSeq <= seqNumDue)) {
	    lastSendMulticastTopicTime = currtime;
		


	    //if(SaarTest.logLevel <= 880) myPrint("blockbased: " +"SysTime: " + getCurrentTimeMillis() + " Node "+saarClient.endpoint.getLocalNodeHandle()+" BROADCASTING for Topic[ "+ myTopicName + " ] " + myTopic + " Seq= " + currSeq + " PUBLISHPERIOD: " + PUBLISHPERIOD + " firstBroadcastTime: " + firstBroadcastTime + " seqNumDue: " + seqNumDue, 880);
	    
	    if(SaarTest.logLevel <= 880) myPrint("blockbased: " +"SysTime: " + getCurrentTimeMillis() + " Node "+saarClient.endpoint.getLocalNodeHandle()+" BROADCASTING for Topic[ "+ myTopicName + " ] " + myTopic + " Seq= " + currSeq, 880);	    
	    if(firstBroadcastTime == 0) {
		firstBroadcastTime = currTime;
	    }
	    
	    // We normally do this in grpSummary to track the right end of the buffer, but the source can calculate this exactly
	    if(firstPktSequence == -1) {
		firstPktSequence = currSeq;
		firstPktTime = currTime;
	    }
	    
	    saarClient.allTopics[tNumber].setPSeqNum(currSeq + 1);
	    saarClient.allTopics[tNumber].setPTime(getCurrentTimeMillis());

	    //rice.p2p.saar.simulation.SaarSimTest.simulatorbroadcastseqnum = currSeq;
	    saarClient.viewer.setBackgroundBroadcastSeqnum(currSeq);

	    if((saarClient.DATAPLANETYPE == 3) || ((saarClient.DATAPLANETYPE!=3) && SaarClient.HYBRIDDEBUG)) {
		// We deliver the packet locally
		int seqNum = currSeq;
		int numPkts = 0; 
		
		//String blockId = "Stripe:" + stripeId + "Seq:" + seqNum;
		//if(numSequence.containsKey(new Integer(seqNum))) {
		//  numPkts = ((Integer)numSequence.get(new Integer(seqNum))).intValue();
		//} 
		//numSequence.put(new Integer(seqNum), new Integer(numPkts + 1));
		numSequence.set(seqNum);

		Block block = new Block(0, seqNum);
		block.addToPath(saarClient.bindIndex, saarClient.jvmIndex, saarClient.vIndex);
		blocks.put(new Integer(seqNum), block);
		//int depth = 0;
		sendbmap = computeSendBMAP(); // Only for the source we compute this again so that we have up do date information when doing the requestoverriding 
		
		
		
		// This is because in the hybrid the coolstreamingdeliver are for packets that are recovered only
		
		coolstreamingDeliver(0, block.seqNum, 0, saarClient.endpoint.getLocalNodeHandle(), numPkts +1, block);
	    }
		
	    return 1;
	} else {
	    // We will dampen rate to only send at PUBLISHPERIOD
	    return 0;
	}
    
	
    }
    



    public void virtualSourceAcquiresBlocks() {
	long currtime = getCurrentTimeMillis();
	if((currtime - lastVirtualSourceAcquiresBlocksTime) < PUBLISHPERIOD) { // this is to enable finegrained requestblocks
	    if(SaarTest.logLevel <= 850) myPrint("blockbased: " +" Skipping virtualSourceAcquiresBlocks()", 850);
	    return;
	}
	lastVirtualSourceAcquiresBlocksTime = currtime;
	if(SaarTest.logLevel <= 875) myPrint("blockbased: " +" virtualSourceAcquiresBlocks()", 875);



	Topic myTopic = saartopic.baseTopic;
	long currTime = getCurrentTimeMillis();
	String myTopicName = topicName;

       




	if(firstPktSequence == -1) {
	    return;
	}
	int virtualBlockId = firstPktSequence + (int)((currtime - firstPktTime)/PUBLISHPERIOD);
	
	    
	if((lastVirtualBlockGenerated ==-1) || (lastVirtualBlockGenerated < virtualBlockId)) {
	    
	    lastVirtualBlockGenerated  = virtualBlockId;

	    if(SaarTest.logLevel <= 880) myPrint("blockbased: " +"SysTime: " + getCurrentTimeMillis() + " Node "+saarClient.endpoint.getLocalNodeHandle()+" VIRTUALACQUIRING for Topic[ "+ myTopicName + " ] " + myTopic + " Seq= " + lastVirtualBlockGenerated, 880);
	    

	    int seqNum = lastVirtualBlockGenerated;
	    int numPkts = 0; 

	    
	    //String blockId = "Stripe:" + stripeId + "Seq:" + seqNum;
	    //if(numSequence.containsKey(new Integer(seqNum))) {
	    //numPkts = ((Integer)numSequence.get(new Integer(seqNum))).intValue();
	    //} 
	    //numSequence.put(new Integer(seqNum), new Integer(numPkts + 1));
	    numSequence.set(seqNum); // We no longer trak the number of duplicate packets received to reduce emmory consumption by using a bitvector now
	    Block block = new Block(0, seqNum);
	    block.addToPath(saarClient.bindIndex, saarClient.jvmIndex, saarClient.vIndex);
	    blocks.put(new Integer(seqNum), block);
	    sendbmap = computeSendBMAP();
	    coolstreamingDeliver(0, block.seqNum, 0, saarClient.endpoint.getLocalNodeHandle(), numPkts +1, block);
	

	    
	}

    }



    // We advertise our bmap to our neighbors, This function is invoked for the multicast source only, the others biggy back this information on RequestBlocks
    public void sendBMAP() {
	long currtime = getCurrentTimeMillis();
	if((currtime - lastSENDBMAPTime) < ADVERTISINGPERIOD) {
	    return;
	}

	lastSENDBMAPTime = currtime;

	sendbmap = computeSendBMAP();

	Topic myTopic = saartopic.baseTopic;	
	// We also send out advertised BMAP to our children
	NodeHandle[] handles = manager.getNeighbors();
	if(SaarTest.logLevel <= 875) myPrint("blockbased: " +" sendBMAP():#children: " + handles.length, 875);
	for (int i = 0; i < handles.length; i++) {
	    if(SaarTest.logLevel <= 875) myPrint("blockbased: " +" Sending BMAPAvailabilityMsg to child " + handles[i], 875);
	    // The variables here are calculted in the method controlplaneUpdate()

	    Vector pendingNotifySeqNums = new Vector();
	    Vector pendingNotify = (Vector) piggybackRespondblocksnotifymsg.get(handles[i]);
	    if(pendingNotify != null) {
		for(int k=0; k< pendingNotify.size(); k++) {
		    RequestTuple val = (RequestTuple) pendingNotify.elementAt(k);
		    pendingNotifySeqNums.add(new Integer(val.seqNum));
		}

	    }
	    piggybackRespondblocksnotifymsg.remove(handles[i]);

	    manager.setLastBMAPAdvertisementTimeNeighbor(handles[i], currtime);

	    saarClient.endpoint.route(null, new BMAPAvailabilityMsg(saarClient.endpoint.getLocalNodeHandle(), myTopic, sendbmap, uStatic, uDynamic, streamingQuality, avgMeshDepth, numGoodNeighbors, pendingNotifySeqNums, minDistanceToSource), handles[i]);
	}


    }
    



    // We serve out the pending blocks requested by our neighbors. This hsould be invoked only once every publishperiod
    public void serveBlocks() {
	Topic myTopic = saartopic.baseTopic;	
	long currtime = getCurrentTimeMillis();
	
	if((currtime - lastServeBlocksInvokationTime) < PUBLISHPERIOD) {
	    if(SaarTest.logLevel <= 850) myPrint("blockbased: " +" Skipping serveBlocks()", 850);
	    return;
	}

	if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"serveBlocks() executed", 875);
	lastServeBlocksInvokationTime = currtime;


	//boolean adjustmentInLastSegment = false;
	//int totalSent = 0;

	/*
	// This is hardcoded below for PUBLISPERIOD = 1000, outdegree=5, M=4
	if((maximumOutdegree % M) != 0 ) {

	    if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"ERROR: When the nodedegree is not integral, the previous code is not generic enough to handle all configurations of different publish rates etc", 875);
	    System.out.println("ERROR: When the nodedegree is not inegral, the previous code is not generic enough to handle all configurations of different publish rates etc");
	    System.exit(1);


	    // Basically in every 4 seconds it should be able to serve 5 blocks

	    while(servedBlocks.size() > 0) {
		ServedBlockTuple tuple = (ServedBlockTuple) servedBlocks.elementAt(0);
		if((currtime - tuple.servedTime) > 4000) { // hardcoded value for outdegree = 5, M= 4, note that actually we want 3 past instants, to get 3 instants we have to do 4000 ms
		    ServedBlockTuple  toRemove = (ServedBlockTuple)servedBlocks.remove(0);
		    if(SaarTest.logLevel <= 850) myPrint("blockbased: " +"removingServedBlock(" + toRemove.servedTime + ", " + toRemove.numBlocks + ")" , 850);
		} else {
		    break;
		}
	    }
	    // We will now go over the vector and count the total pkts sent
	    for(int i=0; i< servedBlocks.size(); i++) {
		ServedBlockTuple tuple = (ServedBlockTuple) servedBlocks.elementAt(i);
		 
		if(tuple.numBlocks > 1) {
		    adjustmentInLastSegment = true;
		}
		totalSent = totalSent + tuple.numBlocks;
		if(SaarTest.logLevel <= 850) myPrint("blockbased: " +"includingServedBlock(" + tuple.servedTime + ", " + tuple.numBlocks + "), totalSent: " + totalSent, 850);
	    }
	    if(!adjustmentInLastSegment && (totalSent < 4)) {
		adjustment = 1;
	    }
	    if(totalSent > 5) {
		adjustment = -(int)nodedegree; // implying we cannot send in this time
	    }
	}
	*/

	double backgroundBandwidth;
	// We will first check if the foreground is using up ny bandwidth
	//if((currtime - saarClient.viewer.getLastForegroundPktRecvTime()) > (1.1 * saarClient.viewer.getForegroundPublishPeriod())) {    // Corrected on Nov05-2007 previously the source was usinging up the entire background bandwidth for recovery in the hybrids. Also increased the 1.1 to 1.2 since with simulating transmission delays we may wanna give slightly more cushion


	if(BGUNAWAREOFLASTFGPKTRECVTIME) {
	    if(!amMulticastSource) {
		backgroundBandwidth = nodedegree;  
	    } else {
		backgroundBandwidth = BACKGROUNDFRACTION * (nodedegree - saarClient.viewer.foregroundReservedRI);
	    }
	    
	} else {
	    
	    if(!amMulticastSource &&  ((currtime - saarClient.viewer.getLastForegroundPktPushedTime()) > (1.2 * saarClient.viewer.getForegroundPublishPeriod()))) {
		backgroundBandwidth = BACKGROUNDFRACTION * nodedegree;  // Note that this nodedegree is initialnodedegree - controlRI
	    } else {
		backgroundBandwidth = BACKGROUNDFRACTION * (nodedegree - saarClient.viewer.foregroundReservedRI);
	    }
	}





	// We will determine the adjustment due to fractional R.I
	int adjustment = 0; // this will be set probabilistically to handle fractional R.I




	long currQueueDelay;
	currQueueDelay = saarClient.computeQueueDelay();
	if(SaarClient.SIMULATETRANSMISSIONDELAYS) {
	    // We calculate based on the pending bytes in the neighbor queues
	    if(currQueueDelay < 1000) {
		adjustment = 1;
	    }


	} else {
	    double fractionalRI = backgroundBandwidth - ((int)backgroundBandwidth);
	    if(fractionalRI > 0) {
		int desiredrange = (int)(fractionalRI*1000);
		int val = serveblocksrng.nextInt(1000);
		if(val < desiredrange) {
		    adjustment = 1;
		    
		}
	    }
	}
	if(SaarTest.logLevel <= 875) myPrint("blockbased: " + "backgroundBandwidth: " + backgroundBandwidth + " ADJUSTMENT: " + adjustment + " outlinksize: " + outLink.size(), 875);

	BWSupplyTuple bwSupplyTuple = new BWSupplyTuple(backgroundBandwidth,getCurrentTimeMillis());
	manager.incrBWSupply(bwSupplyTuple); 

    

	
	//if(SaarTest.logLevel <= 877) myPrint("blockbased: " +"OUTLINKSIZEBEFORE: " + outLink.size(), 877);
	normalizedLinkSize = ((double)outLink.size())/(backgroundBandwidth);   
	if(SaarTest.logLevel <= 875) myPrint("blockbased: " + "OUTLINKSIZEBEFORE: " + outLink.size()  +" NORMALIZEDLINKSIZEBEFORE: " + normalizedLinkSize, 875);




	// We will first clear off NODEDEGREE requests from the outbound link
	int numTransmitted = 0;

       

	while((numTransmitted < ( ((int)backgroundBandwidth) + adjustment)) && (outLink.size() > 0)) {
	    currQueueDelay = saarClient.computeQueueDelay();
	    // This code below makes sure that the background traffic does not interfere with the foreground traffic by increasing delays

	    if(((saarClient.DATAPLANETYPE == 4) ||  (saarClient.DATAPLANETYPE == 5)) && SaarClient.SIMULATETRANSMISSIONDELAYS) {
		if(currQueueDelay > SaarClient.MAXQUEUEDELAYBACKGROUND) {
		    if(SaarTest.logLevel <= 875) myPrint("blockbased: " +" Prempting serveblocks() inspite of backgroundbandwidth calculation", 875);
		    return;
		}
	    }
	



	    PendingUploadState pendingState = (PendingUploadState)outLink.remove(0);

	    if(shouldForwardConsideringLinkLoss()) {
		numTransmitted ++; 
		numpktssent++;
		if(SaarTest.logLevel <= 875) myPrint("blockbased: " +" Sending RespondBlocksMsg " + pendingState.msg + " to requestor " + pendingState.requestor, 875);
		saarClient.endpoint.route(null, pendingState.msg, pendingState.requestor);
		
	    } else {
		numTransmitted ++; // Copied from the if () part above to here on Sep26 
		if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"Not Publishing (because of link loss) RespondBlocksMsg " +  pendingState.msg + " to requestor " + pendingState.requestor , 875);
	    }
	    


	}




	
	//if(SaarTest.logLevel <= 850) myPrint("blockbased: " +"OUTLINKSIZEAFTER: " + outLink.size(), 850);
	normalizedLinkSize = ((double)outLink.size())/(backgroundBandwidth);   
	if(SaarTest.logLevel <= 875) myPrint("blockbased: " + "OUTLINKSIZEAFTER: " + outLink.size() +" NORMALIZEDLINKSIZEAFTER: " + normalizedLinkSize, 875);


	// This logic is no longer needed since we reverted to probabilistically handle fractional R.I
	// We need special accounting only when the number of blocks served per second is not an integer
	//if((maximumOutdegree % M) != 0 ) {
	//  servedBlocks.add(new ServedBlockTuple(numTransmitted, currtime));
	//}




	// We will also send out anycast packets at low priority. The idea is that we always give priority to the outLink and then to outAnycastLink
	//while((numTransmitted < nodedegree) && (outAnycastLink.size() > 0)) {
	//  PendingUploadState pendingState = (PendingUploadState)outAnycastLink.remove(0);
	//  if(SaarTest.logLevel <= 850) myPrint("blockbased: " +" Sending RespondBlocksMsg " + pendingState.msg + " to requestor " + pendingState.requestor, 850);
	//  saarClient.endpoint.route(null, pendingState.msg, pendingState.requestor);
	//  numTransmitted ++;
	//}
	//if(SaarTest.logLevel <= 850) myPrint("blockbased: " +"OUTANYCASTLINKSIZE: " + outAnycastLink.size(), 850);
	//normalizedAnycastLinkSize = ((double)outAnycastLink.size())/((double)nodedegree);
	//if(SaarTest.logLevel <= 850) myPrint("blockbased: " +"NORMALIZEDANYCASTLINKSIZE: " + normalizedAnycastLinkSize, 850);

    }
	


    // here it requests the blocks from its parents. We examine our current bmap, the bmap advertised by our parents and also try to incorporate some previoushistory of how much we requested from a parent(not including the shameless requests) in the last BANDWIDTHWINDOW periods 
    public void requestBlocks() {

	long currtime = getCurrentTimeMillis();
	

	long dampingperiod;
	if(PUBLISHPERIOD < MINSWARMINGINTERVAL) {
	    dampingperiod = (long)PUBLISHPERIOD; 
	} else {
	    dampingperiod = MINSWARMINGINTERVAL; 
	}

	if((currtime - lastRequestBlocksInvokationTime) < dampingperiod) { // Use PUBLISHPERIOD/2 do to more fine grained asking to demonstrate that with unsynchronized window edge and buffer of 1, with high R.I you can get the blocks
	    if(SaarTest.logLevel <= 850) myPrint("blockbased: " +" Skipping requestBlocks()", 850);
	    return;
	}
	lastRequestBlocksInvokationTime = currtime;
	if(SaarTest.logLevel <= 875) myPrint("blockbased: " +" requestBlocks()", 875);



	Topic myTopic = saartopic.baseTopic;	
	if(currAnycastPeriod == 0) {
	    currAnycastPeriod = rng.nextInt((int)MAXANYCASTPERIOD);
	    if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"CURRANYCASTPERIOD: " + currAnycastPeriod, 875);
	}

	//CoolstreamingBufferMap myBMAP = computeRequestBMAP();

	if(sendbmap == null) {
	    if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +"WARNING: Do not know what sourceIsBroadcasting", Logger.WARNING);
	    return;
	}

	NodeHandle[] myparents = manager.getNeighbors();
	//Vector missingBlocks = myBMAP.missingBlocks(0); // We
	Vector missingBlocks = sendbmap.missingBlocks(CoolstreamingBufferMap.ADVERTISEDWINDOWSIZE - CoolstreamingBufferMap.FETCHWINDOWSIZE); // We

	// If it is a hybrid, then only the blocks missing since the first tree-pkt tafter it has joined should be fetched
	//if((saarClient.DATAPLANETYPE !=3) || (saarClient.DATAPLANETYPE == 3)) {  // Updated on Sep22-2007, since it seems to be something which we should always do
	Vector toRemove = new Vector();
	for(int i=0; i < missingBlocks.size();i++) {
	    int val = ((Integer)missingBlocks.elementAt(i)).intValue();
	    if(val <= firstBackgroundPktToRecoverAfterJoin) {
		toRemove.add(new Integer(val));
	    }
	    
	}
	for(int i=0; i < toRemove.size();i++) {
	    missingBlocks.remove(toRemove.elementAt(i));
	    
	}
	toRemove.clear();
	//}


	// For the hybrid protocols, we will also make sure that packets that are about to arrive via the treebone shold not be fetched via the mesh recovery protocol
	if((saarClient.DATAPLANETYPE ==4) || (saarClient.DATAPLANETYPE == 5)) {  // Updated on Sep22-2007, since it seems to be something which we should always do
	    toRemove.clear();
	    for(int i=0; i < missingBlocks.size();i++) {
		int val = ((Integer)missingBlocks.elementAt(i)).intValue();
		if(!saarClient.viewer.shouldRecoverViaBackground(val)) {
		    if(SaarTest.logLevel <= 875) myPrint("blockbased: shouldRecoverViaBackground(false, " + val + ")" , 875);
		    toRemove.add(new Integer(val));
		}
		
	    }
	    for(int i=0; i < toRemove.size();i++) {
		missingBlocks.remove(toRemove.elementAt(i));
		
	    }
	    toRemove.clear();
	}

	





	// While emulating link-loss probability, there is a chance that our neighbor sent us a blocknotify and then while sending the actual block it got lost. If the 'seqnum' for this block continues to remain in the pendingReceive then we will keep waiting for this lost block. Thus, we remove the 'old' ( > 2 sec) old entries from the pendingReceive

	Enumeration pendingBlocks;
	Vector retryseqnums = new Vector();
	pendingBlocks = pendingReceive.keys();	
	while(pendingBlocks.hasMoreElements()) {
	    int seqNum = ((Integer)pendingBlocks.nextElement()).intValue();
	    long notifytime = ((Long)pendingReceive.get(new Integer(seqNum))).longValue();
	    //if( (currtime - notifytime) > (SWARMINGRETRYFACTOR * PUBLISHPERIOD)) { // We use a timeout of 2 sec - > 2*PUBLISHPERIOD on Sep26
	    if((currtime - notifytime) > (SWARMINGRETRYTHRESHOLD)) { // We use a timeout of 2 sec , Updated on Oct09-2007

		// We will remove this seqnum from pending receive so that we can retry
		retryseqnums.add(new Integer(seqNum));
	    }
	}
	for(int i=0; i< retryseqnums.size(); i++) {
	    pendingReceive.remove(retryseqnums.elementAt(i));
	    if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"pendingReceive.remove(" + ((Integer)retryseqnums.elementAt(i)).intValue() + ") because of expiry of retry threshold", 875);
	}
	



	if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"NUMPENDING: " + pendingReceive.size(), 875);

	// From the missing blocks we will remove the blocks about which our neighbor has sent a notification saying that the request will be granted in the very near future

	pendingBlocks = pendingReceive.keys();	
	String discountedPendingBlocksString = "( ";
	while(pendingBlocks.hasMoreElements()) {
	    int seqNum = ((Integer)pendingBlocks.nextElement()).intValue();
	    missingBlocks.remove(new Integer(seqNum));
	    discountedPendingBlocksString = discountedPendingBlocksString + seqNum + ", ";
	}
	discountedPendingBlocksString = discountedPendingBlocksString + ")";
	if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"DiscountedPendingBlocksString: " + discountedPendingBlocksString , 875);
	    



	Vector scheduledBlocks = new Vector(); // contains a tuple (seqNum,parentToRequestFrom)





	// We randomize the order of blocks that we would like to request
	while(missingBlocks.size() > 0) {
	    int pos = rng.nextInt(missingBlocks.size());
	    int seqNum = ((Integer)missingBlocks.elementAt(pos)).intValue();
	    if(seqNum >= 0) {
		scheduledBlocks.add(new RequestTuple(seqNum, -1, RequestTuple.MISSING)); // -1 denotes that this block has not been scheduled yet
	    }
	    missingBlocks.remove(pos);
	}



	if(ENABLECOOLSTREAMINGSWARMING && (myparents.length > 0)) {
	    int numPScheduled[] = new int[myparents.length]; // keeps track of the number of pkts requested scheduled to be requested for a particular parent
	    for(int j=0; j < myparents.length; j++) {
		numPScheduled[j]=0;
	    }


	    // For the hybrid dataplanes PASS 1 is omitted since blocks are asked only in pass 2 which checks for bandwidth
	    if((saarClient.DATAPLANETYPE == 3) || ((saarClient.DATAPLANETYPE !=3) && SaarClient.HYBRIDDEBUG)) {

		// PASS 1 - In the first pass, we ensure that in this time period we have not scheduled to ask a parent for more than one packet and also strictly respect the relatively corasegrained BWLIMITReached for each parent as well
		for(int i=0; i< scheduledBlocks.size();i++) {
		    RequestTuple tuple = (RequestTuple)scheduledBlocks.elementAt(i);
		    int pIndex = tuple.pIndex;
		    int seqNum = tuple.seqNum;
		    int randPos = rng.nextInt(myparents.length); // this helps us randomize 
		    if(pIndex == -1) {
			for(int j=0; j< myparents.length;j++) {
			    // We will check to see if the parent has it available in its BMap and its limit is not reached
			    int actualJ = (j+randPos)% myparents.length;
			    //CoolstreamingBufferMap pBMAP = manager.getBMAPParent(myparents[actualJ]);
			    CoolstreamingBufferMap pBMAP = manager.getBMAPNeighbor(myparents[actualJ]);
			    //boolean pBWLimitReached = manager.getBWLimitReachedParent(myparents[actualJ]);
			    boolean pBWLimitReached = manager.getBWLimitReachedNeighbor(myparents[actualJ]);
			    //int pUDynamic = manager.getUDynamicParent(myparents[actualJ]);
			    int pUDynamic = manager.getUDynamicNeighbor(myparents[actualJ]);
			    // Note : For the primary blocks we do not check for uDynamic for parent because, the secondary blocks being requested from another sibling can affect the local node's primary reservation with the parent
			    //if((numPScheduled[actualJ] == 0) && ((pBMAP!=null) && pBMAP.containsSeqNum(seqNum)) && !pBWLimitReached && (pUDynamic < 100)) {
			    if((numPScheduled[actualJ] == 0) && ((pBMAP!=null) && pBMAP.containsSeqNum(seqNum)) && !pBWLimitReached) {
				numPScheduled[actualJ]++;
				tuple.pIndex = actualJ;
				tuple.type = RequestTuple.PRIMARY;
				break;
			    }
			}
		    }
		    
		}
	    }



	    // We will now set the numRequested based on the PRIMARY blocks only. Note that the strict regulation of not asking much from a particular parent (i.e asking once every M sec) is done only for the primary
	    for(int j=0; j < myparents.length; j++) {
		manager.incrNumRequested(myparents[j],numPScheduled[j]);
	    }
	    

	    
	    // PASS 2 - We will again check for missing pkt that have not been set for asking in the first pass, we will, we will go ahead and ask these pkts to a parent that has it in its BMAP and choose a parent weighted randomly inversely proportional to its static/dynamic utilization(staticU:utilization is his numChildren/normalizedChildrenHeCanSupport, dynamicU - actual utilization). Warning - ideally this should work and the dynamic utilization should not exceed 100, else incorporate dynamic utilization 
	    
	    boolean haveUnscheduledBlock = false; 	    
	    for(int i=0; i< scheduledBlocks.size();i++) {
		RequestTuple tuple = (RequestTuple)scheduledBlocks.elementAt(i);
		int pIndex = tuple.pIndex;
		int seqNum = tuple.seqNum;
		int weights[] = new int[myparents.length]; // weighted round robbin wts for a particular missing seqNum
		for(int j=0; j < myparents.length; j++) {
		    weights[j]=0;
		}
		if(pIndex == -1) {
		    String wtString = "";
		    for(int j=0; j< myparents.length;j++) {
			// We will check to see if the parent has it available in its BMap and its limit is not reached
			//CoolstreamingBufferMap pBMAP = manager.getBMAPParent(myparents[j]);
			CoolstreamingBufferMap pBMAP = manager.getBMAPNeighbor(myparents[j]);
			//int pUDynamic = manager.getUDynamicParent(myparents[j]);
			int pUDynamic = manager.getUDynamicNeighbor(myparents[j]);
			//boolean pBWLimitReached = manager.getBWLimitReachedParent(myparents[j]);
			// We do not schedule more than a single SECONDARY block from the same parent in the same request, this is done to prevent the parent from allocating bandwidth to a child in a large burst for the secondary pkts
			if((numPScheduled[j] == 0) && ((pBMAP !=null) && pBMAP.containsSeqNum(seqNum)) && (pUDynamic < 100)) {
			    //weights[j] = (100 - pUStatic); // not that someone with no static spare slots, or with no dynamic spare utilization will not be used because the weight will be zero
			    
			    weights[j] = (100 - pUDynamic); // we will use dynamic utilization only to simplify things
			    
			} else {
			    weights[j] = 0;
			}
			wtString = wtString + "(" + myparents[j] + "," + pUDynamic + "," + weights[j] +  "), "; 
			if(SaarTest.logLevel <= 850) myPrint("blockbased: " +"weight[" + seqNum + ", " + myparents[j] + "]:" + weights[j], 850);
		    }
		    int sumWts = 0;
		    for(int j=0; j < myparents.length; j++) {
			sumWts = sumWts + weights[j];
		    }
		    if(sumWts <= 0) {
			if(sumWts < 0) {
			    if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +"WARNING: Pkt of sequence# " + seqNum + " is not present in any buffer OR they do not have bandwidth, wtString: " + wtString, Logger.WARNING);
			    //anycastRequestBlocks.add(new Integer(seqNum));
			}
			
			haveUnscheduledBlock = true;
			
		    } else {
			// We do a round robbin based on the weights
			int randSum = 1 + rng.nextInt(sumWts);
			int partialSum = 0;
			for(int j=0; j< myparents.length; j++) {
			    partialSum = partialSum + weights[j];
			    if(partialSum >= randSum) {
				numPScheduled[j]++;
				tuple.pIndex = j;
				tuple.type = RequestTuple.SECONDARY;
				break;
			    }
			}
			
			
		    }
		    
		    
		}
		
	    }

	    // We will iterate thru the scheduledblocks and if we find that there is atleast one block that has not been scheduled and we observe that the bmaps of neighbors are stale then we request for fresh bmaps
	    if(haveUnscheduledBlock) {
		if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"Have an unscheduled block", 875);
	    }
	    

	    boolean canRequestBlocks = true; 
	    if((currtime - lastCanRequestBlocksTime) < MINSWARMINGINTERVAL) {
		canRequestBlocks = false;
	    } else {
		canRequestBlocks = true;
		
		lastCanRequestBlocksTime = currtime;
		if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"lastCanRequestBlocksTime = " + lastCanRequestBlocksTime, 875);
	    }
	    
	    
	    // We will now created the BlockRequestMsg for each parent
	    for(int i=0; i< myparents.length; i++) {
		NodeHandle requestTo = myparents[i];
		Vector requestBlocks;
		if(toRequestBlocks.containsKey(requestTo)) {
		    requestBlocks = (Vector) toRequestBlocks.get(requestTo);
		} else {
		    requestBlocks = new Vector();
		    toRequestBlocks.put(requestTo, requestBlocks);
		}
		int numPrimary = 0;
		int numSecondary = 0;
		for(int j=0; j<scheduledBlocks.size();j++) {
		    RequestTuple tuple = (RequestTuple)scheduledBlocks.elementAt(j);
		    if(tuple.pIndex == i) {
			// For every scheduled block, we will self-trigger a pending notify. We switched to doing this instead of actually having on RespondBlocksNotifyMsg, Updated on Oct31-2007

			if(SaarTest.logLevel <= 875) myPrint("blockbased: " +" Scheduling request for SeqNum:" + tuple.seqNum + " to " + requestTo, 875);

			pendingReceive.put(new Integer(tuple.seqNum),new Long(getCurrentTimeMillis()));
			if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"pendingReceive.put(" + tuple.seqNum  + ") because of scheduling in requestBlocks", 875);
			
			requestBlocks.add(tuple);

			if(tuple.type == RequestTuple.PRIMARY) {
			    numPrimary ++;
			} else if(tuple.type == RequestTuple.SECONDARY) {
			    numSecondary++;
			}
		    }
		}


		if(!canRequestBlocks) {
		    continue;
		}



		// We will also remove the blocks which we got now
		Vector tuplesToRemove = new Vector();
		for(int j=0; j < requestBlocks.size(); j++) {
		    RequestTuple tupletocheck = (RequestTuple)requestBlocks.elementAt(j);
		    if(numSequence.get(tupletocheck.seqNum)) {
			tuplesToRemove.add(tupletocheck);
			if(SaarTest.logLevel <= 875) myPrint("blockbased: " +" DeScheduling request for SeqNum:" + tupletocheck.seqNum + " to " + requestTo, 875);

		    }
		}
		for(int j=0; j< tuplesToRemove.size(); j++) {
		    RequestTuple tupletocheck = (RequestTuple)tuplesToRemove.elementAt(j);
		    boolean didremove = requestBlocks.remove(tupletocheck);
		    if(!didremove) {
			System.out.println("ERROR: The vector requestBlocks should have contained this tuple which we tried removing");
			System.exit(1);
		    }
		}




		// We will also compute the necessary information notifying the blocks which will be uploaded shortly, which had been requested by 'requestTo' and honored by this localnode, but not yet served because of bandwidth constaints  
		Vector pendingNotifySeqNums = new Vector();
		Vector pendingNotify = (Vector) piggybackRespondblocksnotifymsg.get(requestTo);
		if(pendingNotify != null) {
		    for(int k=0; k< pendingNotify.size(); k++) {
			RequestTuple val = (RequestTuple) pendingNotify.elementAt(k);
			pendingNotifySeqNums.add(new Integer(val.seqNum));
		    }

		}
		piggybackRespondblocksnotifymsg.remove(requestTo);


		boolean canDampen = false;
		if((pendingNotifySeqNums.size() == 0) && (requestBlocks.size() == 0) && !manager.getNeedFreshBMAPNeighbor(requestTo) && ((currtime - manager.getLastBMAPAdvertisementTimeNeighbor(requestTo)) < SaarClient.NEIGHBORHEARTBEATPERIOD) ) {
		    canDampen = true;
		}

		


		if(!(DAMPENREQUESTBLOCKSMSG && canDampen)) {
		    RequestBlocksMsg rBlocksMsg = new RequestBlocksMsg(saarClient.endpoint.getLocalNodeHandle(), myTopic, requestBlocks, numPrimary, numSecondary, sendbmap, uStatic, uDynamic, streamingQuality, avgMeshDepth, numGoodNeighbors, pendingNotifySeqNums, false);


		    toRequestBlocks.put(requestTo, new Vector()); // Since we are requesting, we need to clear off the piggybacked fragments that were stored for future requesting

		    manager.setLastBMAPAdvertisementTimeNeighbor(requestTo, currtime);
		    manager.setNeedFreshBMAPNeighbor(requestTo, false); // We reset it since in response to the blocks we asked the neighbor will send a RequestBlocksMsg to piggyback the pendingNotifySeqnum evenif it did not have any blocks to request

		    if(SaarTest.logLevel <= 875) myPrint("blockbased: " +" Sending RequestBlocksMsg " + rBlocksMsg + " to neighbor " + requestTo , 875);
		    
		    saarClient.endpoint.route(null, rBlocksMsg, requestTo);

		} else {

		    // If we had a unscheduled block and requestBlocks.size() to this neighbor = 0, then we make sure that this was not because of the stale bmaps in the ENABLEDAMPEN optimization

		    if(haveUnscheduledBlock && ( (currtime - manager.getLastForwardHeartbeatTimeNeighbor(requestTo)) > STALEBMAPTHRESHOLD)) {
			// We will still send a null RequestBlocksMsg with the 'needFreshBMAP' bit set to 1
			
			RequestBlocksMsg rBlocksMsg = new RequestBlocksMsg(saarClient.endpoint.getLocalNodeHandle(), myTopic, requestBlocks, numPrimary, numSecondary, sendbmap, uStatic, uDynamic, streamingQuality, avgMeshDepth, numGoodNeighbors, pendingNotifySeqNums, true);

			toRequestBlocks.put(requestTo, new Vector()); // Since we are requesting, we need to clear off the piggybacked fragments that were stored for future requesting

			if(SaarTest.logLevel <= 875) myPrint("blockbased: " +" Sending NULL RequestBlocksMsg " + rBlocksMsg + " to neighbor " + requestTo + " #Blocks(Primary) " + numPrimary + " #Blocks(Secondary) " + numSecondary + " to trigger fresh BMAP advertisement", 875);
			manager.setLastBMAPAdvertisementTimeNeighbor(requestTo, currtime);
			saarClient.endpoint.route(null, rBlocksMsg, requestTo);
			
		    } else {

			if(SaarTest.logLevel <= 875) myPrint("blockbased: " + "Dampening RequestBlocksMsg", 875);
		    }

		}



	    }
	}
	
	/*
	// At this point all the unscheduled pkt anycastRequestBlocks() vector are either because they are not in anybody BMAP or that in the SECONDARY phase all nodes by virture of their UDynamic values were overloaded. We will fetch these blocks using an explicit anycast request as in Chunkcast
	if(ENABLECOOLSTREAMINGBLOCKRECOVERY) {
	    long currtime = getCurrentTimeMillis();
	    if((currtime - lastAnycastBlockRecoveryTime) < currAnycastPeriod) {
		if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"Disallowing anycast request due to rate-limiting" , 875);
		
	    } else {
		Vector anycastRequestBlocks = new Vector(); // These blocks will be requested explicitly using anycast because these blocks were not found at the direct neighbors
		// Note  - The order in which we request packets for the anycast block recovery is completely from the lower end. However, note that if we are experimenting with only anycastblockrecovery=true and with swarming=false, you would need to have the randomization enabled
		
		Vector scheduledBlocksReordered = new Vector();
		if(ENABLECOOLSTREAMINGSWARMING) {

		    // Recovery algorithm - operated on the left-to-right on the (ADVERTISEDWINDOW - FETCHWINDOW)
		    

		    // We will reorder the entries in the scheduled blocks with increasing order of sequence numbers
		    // We will now do a removal MINIMUM sort on this
		    //while(!scheduledBlocks.isEmpty()) {
		    //int val;
		    //int minVal;
		    //RequestTuple state;
		    //RequestTuple chosenState;
		    //state = (RequestTuple)scheduledBlocks.elementAt(0);
		    //minVal = state.seqNum;
		    //chosenState = state;
		    //for(int index = 1; index < scheduledBlocks.size(); index++) {
		    //    state = (RequestTuple)scheduledBlocks.elementAt(index);
		    //    val = state.seqNum;
		    //
		    //    if(val < minVal) {
		    //	minVal = val;
		    //	chosenState = state;
		    //    }
		    //}
		    //scheduledBlocksReordered.add(chosenState);
		    //scheduledBlocks.remove(chosenState);
		    //}
		    


		    //New Recovery Algorithm - Operates on the (ADVERTISEDWINDOW - FETCHWINDOW)
		    CoolstreamingBufferMap  recoveryBMAP = computeAnycastRecoveryBMAP();
		    Vector recoveryBlocks = recoveryBMAP.missingBlocks();
		    while(recoveryBlocks.size() > 0) {
			int pos = 0;
			int seqNum = ((Integer)recoveryBlocks.elementAt(pos)).intValue();
			if(seqNum >= 0) {
			    scheduledBlocksReordered.add(new RequestTuple(seqNum, -1, RequestTuple.MISSING)); // -1 denotes that this block has not been scheduled yet
			}
			recoveryBlocks.remove(pos);
		    }		    
		    



		} else {
		    // If we are only using anycasting we still want to randomize, so we preserve the already randomized order in scheduledBlocks
		    for(int i=0; i< scheduledBlocks.size();i++) {
			RequestTuple tuple = (RequestTuple)scheduledBlocks.elementAt(i);
			scheduledBlocksReordered.add(tuple);
		    }
		}

		if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"NUMBLOCKSTORECOVER: " + scheduledBlocksReordered.size(), 875);		
		
		
		for(int i=0; i< scheduledBlocksReordered.size();i++) {
		    RequestTuple tuple = (RequestTuple)scheduledBlocksReordered.elementAt(i);
		    int pIndex = tuple.pIndex;
		    if(pIndex == -1) { // it is important to check if it is '-1' since this block recovery could be used in combination of swarming
			anycastRequestBlocks.add(new Integer(tuple.seqNum));
		    }
		}

		// From the missing blocks to recover we will remove the blocks about which our neighbor has sent a notification saying that the request will be granted in the very near future
		Enumeration pendingAnycastBlocks = pendingAnycastReceive.keys();
		String discountedPendingAnycastBlocksString = "( ";
		while(pendingAnycastBlocks.hasMoreElements()) {
		    int seqNum = ((Integer)pendingAnycastBlocks.nextElement()).intValue();
		    anycastRequestBlocks.remove(new Integer(seqNum));
		    discountedPendingAnycastBlocksString = discountedPendingAnycastBlocksString + seqNum + ", ";
		}
		discountedPendingAnycastBlocksString = discountedPendingAnycastBlocksString + ")";
		if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"DiscountedPendingAnycastBlocksString: " + discountedPendingAnycastBlocksString , 875);


		if(anycastRequestBlocks.size() > 0) {
		    int satisfyThreshold = 3;
		    int traversalThreshold = 8;
		    int currSeq = allTopics[tNumber].aSeqNum;
		    String gId = "A" + tNumber + "_H" + hostName + "_J" + jvmIndex + "_V" + vIndex + "_S"  + currSeq;  // AnycastCode_StreamId_Hostname_Seq
		    // We will now send out an anycast to locate a neighbor who can satisfy a maximum of the blocks
		    allTopics[tNumber].setASeqNum(currSeq + 1);
		    CoolstreamingESMContent anycastContent = new CoolstreamingESMContent(CoolstreamingESMContent.BLOCKANYCAST, topicName, saarClient.endpoint.getLocalNodeHandle(), cachedGNPCoord, gId, currSeq, anycastRequestBlocks, satisfyThreshold, traversalThreshold);
		    if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"SysTime: " + currtime + " Node BLOCKANYCASTING for Topic[ " + topicName + " ] " + myTopic + " GID=" + gId + " anycastContent: " + anycastContent, 875);
		   
		    myScribe.anycast(myTopic, anycastContent, null);
		} else {
		    if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"SysTime: " + currtime + " Node disallowing NULL ANYCASTING", 875);
		}
		lastAnycastBlockRecoveryTime = currtime;
		currAnycastPeriod = rng.nextInt((int)MAXANYCASTPERIOD);
		if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"CURRANYCASTPERIOD: " + currAnycastPeriod, 875);		
	    }
	}
	*/
	
    }



    public boolean shouldForwardConsideringLinkLoss() {
	int valchosen =  linklossRng.nextInt(100); 
	if(valchosen >= (100* SaarClient.FIXEDAPPLICATIONLOSSPROBABILITY)) {
	    return true;
	} else {
	    return false;
	}

    }   
    


    // We can passively learn of this from the depths in the blocks received
    public void setRightEdgeOfMovingWindow() {
	long currtime = getCurrentTimeMillis();

	// Only the pure-mesh based and hybriddebug or a long outage in treebone set the right edge based on neighbor buffermaps
	if(SETRIGHTEDGEUSINGNEIGHBORMAXBLOCK) {
	    if(!amMulticastSource) {
		
		// If we received a packet via the tree backbone in the last 2 seconds we set it based only on the tree, else we set it just like pure mesh
		int maxBlockAtNeighbors = maxSeqRecv; // this is the maximum that the local node recived itself including out-of-band pkts received in the hybrid protocol. Initial value however is max pkt from treebone
		if((saarClient.DATAPLANETYPE == 3) || ((currtime - saarClient.viewer.getLastForegroundPktRecvTimeAcrossAllStripes()) > 5000)) {
		    
		    NodeHandle maxNeighbor = null;
		    NodeHandle[] myparents = manager.getNeighbors();
		    for(int i=0; i< myparents.length;i++) {
			CoolstreamingBufferMap pBMAP = manager.getBMAPNeighbor(myparents[i]);
			if(pBMAP != null) {
			    int neighborMax = pBMAP.maxBlockPresent();
			    if(neighborMax > maxBlockAtNeighbors) {
				maxBlockAtNeighbors = neighborMax;
				maxNeighbor = myparents[i];
			    }
			}
		    }
		}
		
		if(maxBlockAtNeighbors != -1) {
		    if(UNSYNCHRONIZEDWINDOW) {
			//firstPktSequence = maxBlockAtNeighbors - HYBRIDBLOCKBASEDLAG; 
			firstPktSequence = maxBlockAtNeighbors ;
			firstPktTime = currtime;
			if(SaarTest.logLevel <= 880) myPrint("blockbased: " +"READJUSTING RIGHTEDGE to " + firstPktSequence , 880);		
			
			
		    }
		    
		    //if(SaarTest.logLevel <= 875) myPrint("blockbased: " +" MAXBLOCKATNEIGHBOR: " + maxBlockAtNeighbors + ", MAXNEIGHBOR: " + maxNeighbor, 875);
		    
		} 
	    }
	    
	}
	
	
    }
    




    public void recomputeNodedegree() {
	int prevMaximumOutdegree = maximumOutdegree;
	dataplaneriusedbycontrol = saarClient.getDataplaneRIUsedByControl() ;
	nodedegree = nodedegreeControlAndData - dataplaneriusedbycontrol;   // Note that for the hybrids the control is only discounted. You dont discount foreground tree traffic. 
	if(nodedegree < 0) {
	    nodedegree = 0;
	}

	recomputeMaximumOutdegree();

	if(SaarTest.logLevel <= 875) myPrint("blockbased:  recomputeNodedegree() " + " nodedegree: " + nodedegree + " dataplaneriusedbycontrol: " + dataplaneriusedbycontrol + " maximumOutdegree: " + maximumOutdegree, 875);
	if(maximumOutdegree != prevMaximumOutdegree) {
	    if(SaarTest.logLevel <= 880) myPrint("blockbased:  Resetting maximumOutdegree: " + maximumOutdegree + " prevval: " + prevMaximumOutdegree + " nodedegree: " + nodedegree + " dataplaneriusedbycontrol: " + dataplaneriusedbycontrol, 880);
	}
    }



    // here it repairs the mesh
    public void neighborMaintenance() {

    
	long currtime = getCurrentTimeMillis();
	
	// This interval is 1 sec and independent of the publish period
	if((currtime - lastNeighborMaintenanceInvokationTime) < 1000) {
	    if(SaarTest.logLevel <= 850) myPrint("blockbased: " +" Skipping neighborMaintenance()", 850);
	    return;
	}

	lastNeighborMaintenanceInvokationTime = currtime;

	if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"neighborMaintenance()", 875);
	recomputeNodedegree();

	NodeHandle[] myneighbors = manager.getNeighbors();

	Topic myTopic = saartopic.baseTopic;

       
	if(SaarTest.logLevel <= 880) myPrint("blockbased: " +"BWUtilization: Nodedegree: " + nodedegree +  " dataplaneriusedbycontrol: " + dataplaneriusedbycontrol + "  numneighbors: " + myneighbors.length + " published: " + numpktssent, 880);	
	numpktssent = 0;

	

	
	int CURRACQUIREDTHRESHOLD; 
	if(!amMulticastSource) {

	    if(numNeighborRefreshes >= MAXNEIGHBORREFRESHES) {
		CURRACQUIREDTHRESHOLD = 6000000;
	    } else {
		CURRACQUIREDTHRESHOLD = ACQUIREDTHRESHOLD;
	    }

	    Vector currNeighbors = new Vector();
	    Vector passiveGoodNeighbors = new Vector();
	    Vector activeGoodNeighbors = new Vector();
	    Vector activeGoodOldNeighbors = new Vector();
	    Vector activeGoodRecentNeighbors = new Vector();
	    Vector activeRedundantNeighbors = new Vector();
	    

	    // STEP 1 - Remove dead neighbors (active or passive) and actively acquired neighbors that have bad quality. Note that for the repair algorithm to work you should give the passive neighbors chance to pull up their own quality based on actively acquired neighbors 

	    for(int i=0; i< myneighbors.length; i++) {
		NodeHandle nh = myneighbors[i];
		int neighborQuality = manager.getStreamingQualityNeighbor(nh);
		long lasttime = manager.getLastForwardHeartbeatTimeNeighbor(nh);
		long acquiredtime = manager.getAcquiredTimeNeighbor(nh);
		boolean isActive = manager.isActiveNeighbor(nh);
		int pNumGoodNeighbors = manager.getNumGoodNeighborsNeighbor(nh);	       
		boolean pIsMulticastSource = manager.isMulticastSourceNeighbor(nh);
		CoolstreamingBufferMap pBMAP = manager.getBMAPNeighbor(nh);
		int pUDynamic = manager.getUDynamicNeighbor(nh);



		if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"neighborMaintenance( " + nh + ", " + (currtime - acquiredtime) + ", " + isActive + ", " + (currtime - lasttime)  + ", " + neighborQuality + ", " + pNumGoodNeighbors + ", " + pIsMulticastSource + ", " + PARENTSTREAMINGQUALITYTHRESHOLD + "," + pBMAP + "," + pUDynamic + ")", 875);

		if(((currtime - lasttime) >= SaarClient.NEIGHBORDEADTHRESHOLD) || (isActive && (neighborQuality != -1) && (neighborQuality < PARENTSTREAMINGQUALITYTHRESHOLD))) {
		    
		    
		    if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"neighborMaintenance: removeNeighbor: Removing dead/active neighbor with bad quality, neighbor " + nh, 875);
		    removeNeighbor(nh);



		  
		    //if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"Doublecheck: currtime: " + currtime + " , lasttime: " + lasttime + ", NEIGHBORDEADTHRESHOLD: " + NEIGHBORDEADTHRESHOLD + ", isActive: " + isActive + ", neighborQuality: " + neighborQuality + ", PARENTSTREAMINGQUALITYTHRESHOLD: " + PARENTSTREAMINGQUALITYTHRESHOLD, 875);		  
		    
		    
		} else {
		    currNeighbors.add(nh);
		   

		    if((!isActive) && ((neighborQuality == -1) || (neighborQuality > PARENTSTREAMINGQUALITYTHRESHOLD))) {
			passiveGoodNeighbors.add(nh);

		    }

		    if((isActive) && ((neighborQuality == -1) || (neighborQuality > PARENTSTREAMINGQUALITYTHRESHOLD))) {
			activeGoodNeighbors.add(nh);

		    }

		    if((isActive) && ((neighborQuality == -1) || (neighborQuality > PARENTSTREAMINGQUALITYTHRESHOLD)) && (((currtime - acquiredtime) < CURRACQUIREDTHRESHOLD) || pIsMulticastSource) ) {
			activeGoodRecentNeighbors.add(nh);
		    }

		    if((isActive) && ((neighborQuality == -1) || (neighborQuality > PARENTSTREAMINGQUALITYTHRESHOLD)) && ((currtime - acquiredtime) >= CURRACQUIREDTHRESHOLD) && !pIsMulticastSource) {
			activeGoodOldNeighbors.add(nh);
		    }



		    if((pNumGoodNeighbors > getDesiredIndegree()) && isActive) {
			activeRedundantNeighbors.add(nh);
		    }




		}
		    
	    }

	    

	    if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"neighborMaintenance: #activeGoodNeighbors: " + activeGoodNeighbors.size() + ", #passiveGoodNeighbors: " + passiveGoodNeighbors.size() + ", #activeGoodRecentNeighbors: " + activeGoodRecentNeighbors.size() + ", #activeGoodOldNeighbors: " + activeGoodOldNeighbors.size() + ", #activeRedundantNeighbors: " + activeRedundantNeighbors.size() , 875);
	    


	    // STEP 2 - If we have greater than M good neighbors we remove the oldest active neighbor older than ACQUIREDTHRESHOLD 
	    if((passiveGoodNeighbors.size() + activeGoodNeighbors.size()) > getDesiredIndegree()) {
		if(activeGoodOldNeighbors.size() > 0) {
		    long val;
		    long maxVal;
		    NodeHandle state;
		    NodeHandle chosenState;
		    boolean isActive;
		    state = (NodeHandle)activeGoodOldNeighbors.elementAt(0);
		    maxVal =  currtime - manager.getAcquiredTimeNeighbor(state);
		    chosenState = state;
		    for(int index = 1; index < activeGoodOldNeighbors.size(); index++) {
			state = (NodeHandle)activeGoodOldNeighbors.elementAt(index);
			val =  currtime - manager.getAcquiredTimeNeighbor(state);
			if(val > maxVal) { // It is important to retain non-shuffling during the equal operation to ensure it is order preserving when we have equal values
			    maxVal = val;
			    chosenState = state;
			}
		    }

		    if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"neighborMaintenance: removeNeighbor: Removing the active OLD neighbor (but not multicast source) " + chosenState, 875);
		    removeNeighbor(chosenState);

		    
		    currNeighbors.remove(chosenState);
		    passiveGoodNeighbors.remove(chosenState);
		    activeGoodNeighbors.remove(chosenState);
		    activeGoodRecentNeighbors.remove(chosenState);
		    activeGoodOldNeighbors.remove(chosenState);
		    activeRedundantNeighbors.remove(chosenState);
		    
		}
	    }



	        
	    //  Updated on Oct03-2007, This step was causing nodes with RI < 1 to have less than M neighbors
	    //  Update again on Oct27-2007 to include this code since we recomputeNodedegree, but we ensure that this is not invoked when numneighbors < M
 	    // STEP 3 - If we have greater than outdegree neighbors we remove the oldest active neighbor 
	    myneighbors = manager.getNeighbors();
	    if( (myneighbors.length > getDesiredIndegree()) && (myneighbors.length > maximumOutdegree)) {

		int numToRemove = myneighbors.length - maximumOutdegree;
		int numRemoved = 0;
		while(numRemoved < numToRemove) {
		    long val;
		    long maxVal;
		    NodeHandle state;
		    NodeHandle chosenState;
		    boolean isActive;
		    boolean pIsMulticastSource;
		    state = (NodeHandle)currNeighbors.elementAt(0);
		    isActive = manager.isActiveNeighbor(state);
		    pIsMulticastSource = manager.isMulticastSourceNeighbor(state);
		    val =  currtime - manager.getAcquiredTimeNeighbor(state);
		    if(pIsMulticastSource || !isActive) {
			//val = 0;
		    }
		    maxVal = val;
		    chosenState = state;
		    for(int index = 1; index < currNeighbors.size(); index++) {
			state = (NodeHandle)currNeighbors.elementAt(index);
			val =  currtime - manager.getAcquiredTimeNeighbor(state);
			pIsMulticastSource = manager.isMulticastSourceNeighbor(state);
			if(pIsMulticastSource || !isActive) {
			    val = 0;
			}
			if(val > maxVal) { // It is important to retain non-shuffling during the equal operation to ensure it is order preserving when we have equal values
			    maxVal = val;
			    chosenState = state;
			}
		    }

		    if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"neighborMaintenance: removeNeighbor: Removing the oldest active neighbor " + chosenState + " till #neighbors does not exceed maximumoutdegree", 875);
		    removeNeighbor(chosenState);

		    
		    
		    currNeighbors.remove(chosenState);
		    passiveGoodNeighbors.remove(chosenState);
		    activeGoodNeighbors.remove(chosenState);
		    activeGoodRecentNeighbors.remove(chosenState);
		    activeGoodOldNeighbors.remove(chosenState);
		    activeRedundantNeighbors.remove(chosenState);

		    numRemoved ++;
		    
		}
	    }
	    



	    numGoodNeighbors = passiveGoodNeighbors.size() + activeGoodNeighbors.size();
	    if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"NUMGOODNEIGHBORS: " + numGoodNeighbors, 875);


	    // The multicast source does not need to establish new neighbors, but this routine was invoked to be able to remove dead neighbors 
	    //if(amMulticastSource) {
	    //return;
	    //}



	    // STEP 4 - Issue anycast if (#activeGoodRecentNeighbors + #passiveGoodNeighbors) < M
	    if((passiveGoodNeighbors.size() + activeGoodRecentNeighbors.size()) < getDesiredIndegree()) {



		// We do exponential backoff 
		if((currtime - lastAnycastForRepairTime) > expbackoffInterval) {
		    maxExpbackoffInterval = maxExpbackoffInterval * 2;
		    expbackoffInterval = rng.nextInt((int)maxExpbackoffInterval);
		    lastAnycastForRepairTime = currtime;
		    if(SaarTest.logLevel <= 875) myPrint("blockbased: Establishing neighbor because desired MINDEGREE of " + getDesiredIndegree() + " not satisfied," + " EXPBACKOFF(nextmax:" + maxExpbackoffInterval + ", nextchosen: " + expbackoffInterval + ")", 875);
		    SaarContent reqContent = new BlockbasedContent(BlockbasedContent.ANYCASTNEIGHBOR, topicName,tNumber); // this should contain the currSeq;

		    NodeHandle anycastHint = getRandomLeafsetMember(); // We start the anycast from random points in the spanning tree. We do this to enable more random mesh construction
		    saarClient.reqAnycast(tNumber, reqContent, anycastHint, 2, 1, 10) ;
		    
		    numNeighborRefreshes ++;

		} else {
		    if(SaarTest.logLevel <= 875) myPrint("singletree: Dampening ANYCASTNEIGHBOR EXPBACKOFF( max:" + maxExpbackoffInterval + ", chosen: " + expbackoffInterval + ", expired: " + (currtime - lastAnycastForRepairTime) + ")" , 875);
		}
	
	    }


	    // STEP 5 - Remove a redundant active neighbor
	    if(numGoodNeighbors > getDesiredIndegree()) {
		if(activeRedundantNeighbors.size() > 0) {
		    // We will sort the redundant neighbors and choose the oldest to ask
		    
		    long val;
		    long maxVal;
		    NodeHandle state;
		    NodeHandle chosenState;
		    boolean isActive;
		    state = (NodeHandle)activeRedundantNeighbors.elementAt(0);
		    maxVal =  currtime - manager.getAcquiredTimeNeighbor(state);
		    chosenState = state;
		    for(int index = 1; index < activeRedundantNeighbors.size(); index++) {
			state = (NodeHandle)activeRedundantNeighbors.elementAt(index);
			//val =  manager.getStreamingQualityParent(state);
			val =  currtime - manager.getAcquiredTimeNeighbor(state);
			if(val > maxVal) { // It is important to retain non-shuffling during the equal operation to ensure it is order preserving when we have equal values
			    maxVal = val;
			    chosenState = state;
			}
		    }
		    if(!chosenState.equals(minDepthNeighbor)) {
			// We only send one request every second 
			if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"neighborMaintenance: removeNeighbor: Dropping redundant active neighbor: Sending RequestUnsubscribeMessage to neighbor " + chosenState, 875);
			lastRequestUnsubscribeTime = currtime;
			saarClient.endpoint.route(null, new CoolstreamingRequestUnsubscribeMessage(saarClient.endpoint.getLocalNodeHandle(), myTopic), chosenState);		
		    }
		}

	    }	  
	    

	} else {
	    for(int i=0; i< myneighbors.length; i++) {
		NodeHandle nh = myneighbors[i];
		long lasttime = manager.getLastForwardHeartbeatTimeNeighbor(nh);
		long acquiredtime = manager.getAcquiredTimeNeighbor(nh);
		boolean isActive = manager.isActiveNeighbor(nh);
		int neighborQuality = manager.getStreamingQualityNeighbor(nh);
		if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"neighborMaintenance( " + nh + ", " + (currtime - acquiredtime) + ", " + isActive + ", " + (currtime - lasttime)  + ", " + neighborQuality + ")", 875);
		if((currtime - lasttime) >= SaarClient.NEIGHBORDEADTHRESHOLD) {
		    if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"neighborMaintenance: removeNeighbor: Removing a dead neighbor of the multicast source", 875);
		    removeNeighbor(nh);
		}
	    }

	}

    }




    // This is the BMAP which is being computed based on the last WINDOWSIZE window to the left of the last pkt I received. This BMAP will be advertised to my neighbors
    public CoolstreamingBufferMap computeSendBMAP() {

	// We will print the size of the hashtable to identfy memory leaks

	if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"Hashtables: " + " numSequence.cardinality: " + numSequence.cardinality() + " numSequence.length: " + numSequence.length() + ", blocks: " + blocks.size() + ", numUploaded: " + numUploaded.size() + ", pendingReceive: " + pendingReceive.size() + ", pendingAnycastReceive: " + pendingAnycastReceive.size() + ", piggybackRespondblocksnotifymsg: " + piggybackRespondblocksnotifymsg.size() + ", servedBlocks: " + servedBlocks.size(), 875);



	CoolstreamingBufferMap bmap = null; 
	long currtime = getCurrentTimeMillis();
	if(rootSeqNumRecv !=-1) {
	    int selfestimatedRWindow = firstPktSequence + (int)((currtime - firstPktTime)/PUBLISHPERIOD);
	    int controlPktRWindow = rootSeqNumRecv;
	    int rWindow = selfestimatedRWindow;

	    if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"RWINDOW-ESTIMATION:" + "controlPkt:" + controlPktRWindow + ", selfestimate: " + selfestimatedRWindow + ", firstPktSequence: " + firstPktSequence + ", firstPktTime: " + firstPktTime, 875);


	    if(!numUploaded.containsKey(new Integer(rWindow))) {
		numUploaded.put(new Integer(rWindow), new Integer(0));
	    }
	    int lWindow = rWindow - CoolstreamingBufferMap.ADVERTISEDWINDOWSIZE + 1;


	    // We will remove 
	    // The safemagin is because if we miss a particular lWindow then that block will remain
	    int SAFEMARGIN = 200;
	    for(int k =0; k < SAFEMARGIN; k++) {

		int targetSeqNumToRemove = lWindow - REMOVECACHEDBLOCKSTHRESHOLD - k;
		
		if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"Memory cleaning rWindow: " + rWindow + " lWindow: " + lWindow + " targetSeqNumToRemove: " + targetSeqNumToRemove, 875);		
		Block deletedblock = (Block) blocks.remove(new Integer(targetSeqNumToRemove)); // we keep a further safe window of 30 blocks because a node may advertise and other nodes when responding to the advertisement, may actually request a block that has past the window
		if(deletedblock != null) {
		    if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"Successfully Deleted CachedBlock# : " + deletedblock.seqNum + " rWindow: " + " lWindow: " + lWindow + " targetSeqNumToRemove: " + targetSeqNumToRemove, 875);		
		}


		//numSequence.remove(new Integer(targetSeqNumToRemove));
		numSequence.clear(targetSeqNumToRemove);

		Object finalVal = numUploaded.remove(new Integer(targetSeqNumToRemove));
		if(finalVal != null) {
		    if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"SeqNum: " + targetSeqNumToRemove + ", #Uploaded: "+ ((Integer)finalVal).intValue(), 875);		
		}

	    }
	    
	    
	    // We will advertise the pkts in this window
	    bmap = new CoolstreamingBufferMap(lWindow, numSequence, CoolstreamingBufferMap.ADVERTISEDWINDOWSIZE);
	    //bmap = new CoolstreamingBufferMap(lWindow, saarClient.viewer.numSequence, CoolstreamingBufferMap.ADVERTISEDWINDOWSIZE);  // We have switched to using the alreadyReceivedSequenceNumber() call to update the local numSequence with blocks arriving out-of-band (cross dataplanes)

	} else {
	    if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +"WARNING: rootSeqNumRecv is -1", Logger.WARNING);
	}
	return bmap;
	
    }
    



    // This is the BMAP that I compute locally to determine which blocks I should request, here we try to stay within the WINDOWSIZE of what is being published by the root
    //public CoolstreamingBufferMap computeRequestBMAP() {
    //CoolstreamingBufferMap bmap = null; 
    //long currtime = getCurrentTimeMillis();
    //if(rootSeqNumRecv != -1) {
    //    int selfestimatedRWindow = firstPktSequence + (int)((currtime - firstPktTime)/PUBLISHPERIOD);
    //    int controlPktRWindow = rootSeqNumRecv;
    //    //if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"RWINDOW-ESTIMATION:" + "controlPkt:" + controlPktRWindow + ", selfestimate: " + selfestimatedRWindow, 875);
    //
    //    int rWindow = selfestimatedRWindow;
    //    int lWindow = rWindow - CoolstreamingBufferMap.FETCHWINDOWSIZE + 1;
    //    // We will advertise the pkts in this window
    //    bmap = new CoolstreamingBufferMap(lWindow, numSequence, CoolstreamingBufferMap.FETCHWINDOWSIZE);
    //} else {
    //    if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +"WARNING: rootSeqNumRecv is -1", Logger.WARNING);
    //}
    //return bmap;
    //
    //}
    



    // This bmap is ADVERTISEDWINDOW - FETCHWINDOW
    public CoolstreamingBufferMap computeAnycastRecoveryBMAP() {
	CoolstreamingBufferMap bmap = null; 
	long currtime = getCurrentTimeMillis();
	if(rootSeqNumRecv != -1) {
	    int selfestimatedRWindow = firstPktSequence + (int)((currtime - firstPktTime)/PUBLISHPERIOD);
	    int controlPktRWindow = rootSeqNumRecv;
	    //if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"RWINDOW-ESTIMATION:" + "controlPkt:" + controlPktRWindow + ", selfestimate: " + selfestimatedRWindow, 875);

	    int rWindow = selfestimatedRWindow - CoolstreamingBufferMap.FETCHWINDOWSIZE;
	    int lWindow = rWindow - (CoolstreamingBufferMap.ADVERTISEDWINDOWSIZE - CoolstreamingBufferMap.FETCHWINDOWSIZE) + 1;
	    // We will advertise the pkts in this window
	    bmap = new CoolstreamingBufferMap(lWindow, numSequence, (CoolstreamingBufferMap.ADVERTISEDWINDOWSIZE - CoolstreamingBufferMap.FETCHWINDOWSIZE));
	} else {
	    if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +"WARNING: rootSeqNumRecv is -1", Logger.WARNING);
	}
	return bmap;

    }





    // We advertise based on the sendbmap provided we have already bootstrapped properly and made an attempt on fetching the blocks in the bitmap. We consider only the left end 
    public int getStreamingQualityToAdvertise(CoolstreamingBufferMap bmap) {
	int val;
	long currtime = getCurrentTimeMillis();
	if((rootSeqNumRecv == -1) || (bmap == null) || (((currtime - firstPktTime)/PUBLISHPERIOD) < CoolstreamingBufferMap.ADVERTISEDWINDOWSIZE)) {
	    val = 99;
	} else {
	    if(SaarTest.logLevel <= 875) myPrint("blockbased: " +" Computing quality based on fractionFilled ", 875);
	    val = bmap.fractionFilled(CoolstreamingBufferMap.ADVERTISEDWINDOWSIZE - CoolstreamingBufferMap.FETCHWINDOWSIZE);
	}
	return val;
    }




    // Note: Operation done only at the multicast source. The critical blocks are the blocks that have not been sent out esmScribePolicy.maximumChildren times
    public Vector getCriticalBlocksToUpload() {
	Vector criticalBlocks = new Vector();

	// If it is part of a hybrid dataplane then we assume that there are no critical blocks
	//if(saarClient.DATAPLANETYPE != 3) {
	if((saarClient.DATAPLANETYPE != 3) || (saarClient.DATAPLANETYPE == 3)) {
	    return criticalBlocks;

	}
	long currtime = getCurrentTimeMillis();
	if(rootSeqNumRecv !=-1) {
	    int selfestimatedRWindow = firstPktSequence + (int)((currtime - firstPktTime)/PUBLISHPERIOD);
	    int controlPktRWindow = rootSeqNumRecv;
	    int rWindow = selfestimatedRWindow -  2 ; // We atleast wait for 2 seconds till when the block has not been requested by neighbors
	    // In order to reduce join delays, in particular the forwarding delay at the source, while doing Request-Overriding we push out the most recent sequence numbers
	    int lWindow = selfestimatedRWindow - (CoolstreamingBufferMap.FETCHWINDOWSIZE/2); // Unless we give some fetchwindowsize/2 time  seconds the block will not have enough time to be propagated
	    //int lWindow = selfestimatedRWindow ;
	    if(nodedegree != ((int)nodedegree)) {     // previously was maximumoutdegree/M when maximumoutdegree was M*nodedegree
		System.out.println("ERROR: nodedegree is fractional");
		System.exit(1);
	    }
	    for(int i= lWindow; i<= rWindow; i++) {

		Object obj = numUploaded.get(new Integer(i));
		if(obj == null) {
		    numUploaded.put(new Integer(i), new Integer(0));
		}
		int currVal = ((Integer)numUploaded.get(new Integer(i))).intValue();
		if(SaarTest.logLevel <= 850) myPrint("blockbased: " +" getCriticalBlocksToUpload1( i=" + i + ", val=" + currVal + ", sendbmap.contains(i)= " + sendbmap.containsSeqNum(i), 850);
		if(SaarTest.logLevel <= 850) myPrint("blockbased: " +" getCriticalBlocksToUpload2( i=" + i + ", val=" + currVal + ", sendbmap.contains(i)= " + mymapContainsSeqNum(sendbmap,i), 850);
		
		if((currVal < nodedegree)  && ((sendbmap!=null) && sendbmap.containsSeqNum(i))) {
		    criticalBlocks.add(new Integer(i));
		}
	    }
	}
	return criticalBlocks;
    }






  
    public void willUploadBlock(int seqNum) {
	if(!numUploaded.containsKey(new Integer(seqNum))) {
	    numUploaded.put(new Integer(seqNum), new Integer(0));
	}
	int currVal = ((Integer)numUploaded.get(new Integer(seqNum))).intValue();
	numUploaded.put(new Integer(seqNum), new Integer(currVal +1));
	BWTuple bwTuple = new BWTuple(1,getCurrentTimeMillis());
	manager.incrBWConsumption(bwTuple);
	if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"WillUploadBlock: " + seqNum, 875);
	return;
    } 	




    public String blocksAsString(Vector blocks) {
	String s = "(";
	for(int i=0; i<blocks.size();i++) {
	    int seqNum = ((Integer) blocks.elementAt(i)).intValue();
	    s = s + ", " + seqNum ;
	}
	s = s + ")";
	return s;
	    
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






    
    protected void addNeighbor(NodeHandle neighbor, boolean active, boolean pIsMulticastSource) {
	Topic topic = saartopic.baseTopic;
	if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"addNeighbor() " + neighbor + " to topic " + topic + " active: " + active + " pIsMulticastSource: " + pIsMulticastSource, 875);
       
	manager.addNeighbor(neighbor, active, pIsMulticastSource);
    }



    protected void removeNeighbor(NodeHandle neighbor) {
	Topic topic = saartopic.baseTopic;
	if(SaarTest.logLevel <= 875) myPrint("blockbased: " +"removeNeighbor() " + neighbor + " from topic " + topic, 875);
	manager.removeNeighbor(neighbor);
	piggybackRespondblocksnotifymsg.remove(neighbor);
	toRequestBlocks.remove(neighbor);

	// We will send a Unsubscribe notification message to this node
	if(SaarTest.logLevel <= 875) myPrint("blockbased: " +saarClient.endpoint.getId() + ": Sending CoolstreamingUnsubscribe message to neighbor " + neighbor, 875);
	saarClient.endpoint.route(null, new CoolstreamingUnsubscribeMessage(saarClient.endpoint.getLocalNodeHandle(),topic), neighbor);


    }


    public NodeHandle[] getNeighbors() {
	return manager.getNeighbors();
    }


    public int numNeighbors() {
	return manager.numNeighbors();
    }



    public boolean isExistingNeighbor(NodeHandle neighbor) {
	return manager.isExistingNeighbor(neighbor);
    }



    
    public boolean mymapContainsSeqNum(CoolstreamingBufferMap mymap, int val) {
	
	int offset = val - mymap.lWindow;
	if(SaarTest.logLevel <= 850) myPrint("blockbased: " +"mymapContainsSeqNum" + " offset:" + offset + " lWindow:" + mymap.lWindow + " val:" + val, 850);
	if((offset < 0) || (offset >= mymap.windowsize)) {
	    if(SaarTest.logLevel <= 850) myPrint("blockbased: " +"mymapContainsSeqNum" + " returning false(1)", 850);
	    return false;
	} else {
	    //if(mymap.present[offset] == 0) {
	    if(!mymap.containsSeqNum(val)) {
		if(SaarTest.logLevel <= 850) myPrint("blockbased: " +"mymapContainsSeqNum" + " returning false(2)", 850);
		return false;
		
	    } else {
		return true;
	    }
	}
    }





    // This tuple helps us record our bandwidth utilization , it tells us the time instant t and the number of blocks sent in that time. Also the time is used to remove information beyond the BANDWIDTHWINDOW
    	

    public class BWSupplyTuple {
	double numBlocks;
	long time;

	public BWSupplyTuple(double numBlocks, long time) {
	    this.numBlocks = numBlocks;
	    this.time = time;
	}
    }


    public class BWTuple {
	int numBlocks;
	long sentTime;

	public BWTuple(int numBlocks, long sentTime) {
	    this.numBlocks = numBlocks;
	    this.sentTime = sentTime;
	}
    }

    public class ServedBlockTuple {
	int numBlocks;
	long servedTime;

	public ServedBlockTuple(int numBlocks, long servedTime) {
	    this.numBlocks = numBlocks;
	    this.servedTime = servedTime;
	}
    }


 
    public class MeshDepthTuple {
	int depth;
	long recvTime;

	public MeshDepthTuple(int depth, long recvTime) {
	    this.depth = depth;
	    this.recvTime = recvTime;
	}
    }




    public class NeighborState {
	NodeHandle neighbor;
	CoolstreamingBufferMap bmap; // this is the last bitmap received from the parent
	//Vector numRequested; // Got rid of this is a moving window of the number of pkts requested to parent in the last BANDWIDTHWINDOW timeperiods. Note that we preferably try not to violate this limit for each parentby asking other parents for this pkt, however we do ask shamelessly and it is upto the parent to actually deny the pkt when its bandwidth is consumed totally. Note that this policy is done since there might be spare bandwidth in the system (total outdegree > indegree) and a parnt might be willing to stream more down a particular child. 
	int uStatic; // this is the latest static utilization advertised by parent
	int uDynamic; // this is the latest dynamic utilization advertised by parent
	long lastRequestedTime; // this is the last time we requeted a block from our parent in the primary mode. This time will be used to space out the primary blocks that will be requested from a parent
	int streamingQuality; // This is the streaming quality of the parent as advertised by the parent himself
	int avgMeshDepth;
	int numGoodNeighbors;
	long lastForwardHeartbeatTime ; // this is the time this (local node RECVFROM this neighbor), explicitly or via when the last bitmap was received

	long lastBMAPAdvertisementTime; // This is the last time the (local node SENTTO this neighbor) a buffermap-advertisment. Also note that this value may be different for different neighbors, since especially in the hybrid protocols I might just piggyback a bmap on one neighbor. This also serves as a heartbeat message Updated on Oct02-2007 and is in the opposite direction of the 'lastForwardHeartbeatTime'


	Vector numSent ; // we keep track of how much we sent to each child, when the overal banfwidth limits are reached we stop entertaining requests for these children
	long acquiredTime; // This is the time when the neighbor was acquired, this time is used to keep an up-to-date set of neighbors to preserve the invariant that our neighbors are a random subset of the entire group, thus neighbors acquired long time back when say the group was small should be replaced with a newer set
	boolean isActive; // This means the neighbor was acquired actively
	
	int pNumGoodNeighbors; 

	boolean pIsMulticastSource; 

	boolean needFreshBMAP; 



	public NeighborState(NodeHandle neighbor, boolean isActive, boolean pIsMulticastSource) {
	    this.neighbor = neighbor;
	    this.isActive = isActive;
	    this.pIsMulticastSource = pIsMulticastSource;
	    acquiredTime = getCurrentTimeMillis();
	    this.lastForwardHeartbeatTime = getCurrentTimeMillis();
	    this.bmap = null;
	    this.uStatic = -1;
	    this.uDynamic = -1;
	    //this.numRequested = new Vector();
	    this.lastRequestedTime = 0;
	    this.streamingQuality = -1;
	    this.numSent = new Vector();
	    this.pNumGoodNeighbors = 0;
	    this.needFreshBMAP = false;
	    this.lastBMAPAdvertisementTime = 0;

	}

	public void setNeedFreshBMAP(boolean val) {
	    needFreshBMAP = val;
	}


	public boolean getNeedFreshBMAP() {
	    return needFreshBMAP ;
	}


	public long getLastBMAPAdvertisementTime() {
	    return lastBMAPAdvertisementTime;
	}

	
	public void setLastBMAPAdvertisementTime(long val) {
	    lastBMAPAdvertisementTime = val;
	}


	// This is time only for requesting a PRIMARY block
	public void setLastRequestedTime(long val) {
	    lastRequestedTime = val;
	}
	


	//public void setNumSent(int val) {
	//  if(numSent.size() > BANDWIDTHWINDOW) {
	//numSent.remove(0);
	//  }
	//  numSent.add(new Integer(val));
	//}


	// Since we do not record timestamps, we explicitly assume this function is called every time pperiod whether pkts are requested or not from this parent, also we count only the primary 
	//public void incrNumRequested(int val) {
	//  if(numRequested.size() > BANDWIDTHWINDOW) {
	//numRequested.remove(0);
	//  }
	//  numRequested.add(new Integer(val));
	//
	//}



	// this returns true, if I have requested this parent for pkts in the recent past more than what a single parent-child reservation allows, i.e more than an average of 1 block every M time periods(PUBLISHPERIOD)
	public boolean getBWLimitReached() {
	    long currtime = getCurrentTimeMillis();
	    if((currtime - lastRequestedTime) < (PUBLISHPERIOD * M)) {
		return true;
	    } else {
		return false;
	    }


	}

	public void setBMAP(CoolstreamingBufferMap val) {
	    bmap = val;
	}

	public void setUStatic(int val) {
	    uStatic = val;
	}

	public void setUDynamic(int val) {
	    uDynamic = val;
	}

	public void setStreamingQuality(int val) {
	    streamingQuality = val;
	}       

	public void setAvgMeshDepth(int val) {
	    avgMeshDepth = val;
	}       


	public void setNumGoodNeighbors(int val) {
	    pNumGoodNeighbors = val;
	}       


	public void setLastForwardHeartbeatTime(long val) {
	    lastForwardHeartbeatTime = val;
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
	    return pNumGoodNeighbors;
	}

	public long getLastForwardHeartbeatTime() {
	    return lastForwardHeartbeatTime;
	}

	public long getAcquiredTime() {
	    return acquiredTime;
	}

	public boolean isActive() {
	    return isActive;
	}

	public boolean isPMulticastSource() {
	    return pIsMulticastSource;
	}

    }



    public class TopicManager implements Observer {
	
	protected SaarTopic saartopic;
	
	// bidirectional neighbor
	protected Vector neighbors;

	protected  Hashtable neighborsState;

	// this keeps track of our bandwidth consumption (a vector of BWTuples)
	protected Vector bwConsumption ;


	// this keeps track of our bandwidth supply (a vector of BWSupplyTuples)
	protected Vector bwSupply ;




	// this keeps track of the estimate mesh depth (a vector of MeshDepthTuple)
	protected Vector meshDepthEstimation ;

	protected TopicManager(SaarTopic saartopic) {
	    this.saartopic = saartopic;
	    this.neighbors = new Vector();
	    this.neighborsState = new Hashtable();
	    this.bwConsumption = new Vector();
	    this.bwSupply = new Vector();
	    this.meshDepthEstimation = new Vector();
	    if(SaarTest.logLevel <= Logger.INFO) myPrint("blockbased: " +"Creating CESM.TopicManager for topic: " + saartopic.baseTopic, Logger.INFO);
	    
	}


	public void initialize() {
	    neighbors.clear();
	    neighborsState.clear();
	    bwConsumption.clear();
	    bwSupply.clear();
	    meshDepthEstimation.clear();
	}


	/**** Setters for neighbor start here ****/
	

	
	// This is the BMAP of the parent as advertised in the BMAPAvailabilityMsg
	public void setBMAPNeighbor(NodeHandle neighbor, CoolstreamingBufferMap val) {
	    NeighborState nState = (NeighborState) neighborsState.get(neighbor);
	    if(nState != null) {
		nState.setBMAP(val);
	    } else {
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +" Unknown neighbor " + neighbor, Logger.WARNING); 
	    }
	    
	}
	
	public void setUStaticNeighbor(NodeHandle neighbor, int val) {
	    NeighborState nState = (NeighborState) neighborsState.get(neighbor);
	    if(nState != null) {
		nState.setUStatic(val);
	    } else {
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +" Unknown neighbor " + neighbor, Logger.WARNING); 
	    }
	    
	}
	
	
	public void setUDynamicNeighbor(NodeHandle neighbor, int val) {
	    NeighborState nState = (NeighborState) neighborsState.get(neighbor);
	    if(nState != null) {
		nState.setUDynamic(val);
	    } else {
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +" Unknown neighbor " + neighbor, Logger.WARNING); 
	    }
	    
	}
	
	public void setStreamingQualityNeighbor(NodeHandle neighbor, int val) {
	    NeighborState nState = (NeighborState) neighborsState.get(neighbor);
	    if(nState != null) {
		nState.setStreamingQuality(val);
	    } else {
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +" Unknown neighbor " + neighbor, Logger.WARNING); 
	    }
	    
	}
	
	public void setAvgMeshDepthNeighbor(NodeHandle neighbor, int val) {
	    NeighborState nState = (NeighborState) neighborsState.get(neighbor);
	    if(nState != null) {
		nState.setAvgMeshDepth(val);
	    } else {
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +" Unknown neighbor " + neighbor, Logger.WARNING); 
	    }
	    
	}
	
	public void setNumGoodNeighborsNeighbor(NodeHandle neighbor, int val) {
	    NeighborState nState = (NeighborState) neighborsState.get(neighbor);
	    if(nState != null) {
		nState.setNumGoodNeighbors(val);
	    } else {
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +" Unknown neighbor " + neighbor, Logger.WARNING); 
	    }
	    
	}
	


	public void setLastForwardHeartbeatTimeNeighbor(NodeHandle neighbor, long val) {
	    NeighborState nState = (NeighborState) neighborsState.get(neighbor);
	    if(nState != null) {
		nState.setLastForwardHeartbeatTime(val);
	    } else {
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +saarClient.endpoint.getId() + ": Received unexpected forwardheartbeat from neighbor " + neighbor, Logger.WARNING); 
	    }
	    
	}


	public void setLastBMAPAdvertisementTimeNeighbor(NodeHandle neighbor, long val) {
	    NeighborState nState = (NeighborState) neighborsState.get(neighbor);
	    if(nState != null) {
		nState.setLastBMAPAdvertisementTime(val);
	    } else {
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +saarClient.endpoint.getId() + ": Received unexpected forwardheartbeat from neighbor " + neighbor, Logger.WARNING); 
	    }
	    
	}



	public void setNeedFreshBMAPNeighbor(NodeHandle neighbor, boolean val) {
	    NeighborState nState = (NeighborState) neighborsState.get(neighbor);
	    if(nState != null) {
		nState.setNeedFreshBMAP(val);
	    } else {
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +saarClient.endpoint.getId() + ": Received unexpected needfreshbmapneighbor from neighbor " + neighbor, Logger.WARNING); 
	    }
	    
	}

	
	/**  Setters for neighbor end here ****/
	

	/***  Getters for neighbor ************/ 



	
	public CoolstreamingBufferMap getBMAPNeighbor(NodeHandle neighbor) {
	    NeighborState nState = (NeighborState) neighborsState.get(neighbor);
	    if(nState != null) {
		return nState.getBMAP();
	    } else {
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +saarClient.endpoint.getId() + ": Retrieving BMAP on a neighbor with missing entry in neighborsState, neighbor is " + neighbor, Logger.WARNING); 
		return null;
	    }
	}
	
	
	public int getUStaticNeighbor(NodeHandle neighbor) {
	    NeighborState nState = (NeighborState) neighborsState.get(neighbor);
	    if(nState != null) {
		return nState.getUStatic();
	    } else {
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +saarClient.endpoint.getId() + ": Retrieving uStatic on a neighbor with missing entry in neighborsState, parent is " + neighbor, Logger.WARNING); 
		return 100;
	    }
	}
	
	
	public int getUDynamicNeighbor(NodeHandle neighbor) {
	    NeighborState nState = (NeighborState) neighborsState.get(neighbor);
	    if(nState != null) {
		return nState.getUDynamic();
	    } else {
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +saarClient.endpoint.getId() + ": Retrieving uDynamic on a neighbor with missing entry in neighborsState, neighbor is " + neighbor, Logger.WARNING); 
		return 100;
	    }
	}
	
	public int getStreamingQualityNeighbor(NodeHandle neighbor) {
	    NeighborState nState = (NeighborState) neighborsState.get(neighbor);
	    if(nState != null) {
		return nState.getStreamingQuality();
	    } else {
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +saarClient.endpoint.getId() + ": Retrieving streamingQuality on a neighbor with missing entry in neighborsState, neighbor is " + neighbor, Logger.WARNING); 
		return 100;
	    }
	}
	
	public int getAvgMeshDepthNeighbor(NodeHandle neighbor) {
	    NeighborState nState = (NeighborState) neighborsState.get(neighbor);
	    if(nState != null) {
		return nState.getAvgMeshDepth();
	    } else {
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +saarClient.endpoint.getId() + ": Retrieving streamingQuality on a neighbor with missing entry in neighborsState, neighbor is " + neighbor, Logger.WARNING); 
		return 100;
	    }
	}
	
	
	public int getNumGoodNeighborsNeighbor(NodeHandle neighbor) {
	    NeighborState nState = (NeighborState) neighborsState.get(neighbor);
	    if(nState != null) {
		return nState.getNumGoodNeighbors();
	    } else {
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +saarClient.endpoint.getId() + ": Retrieving numGoodNeighbors on a neighbor with missing entry in neighborsState, neighbor is " + neighbor, Logger.WARNING); 
		return 100;
	    }
	}
	
	

	public long getLastForwardHeartbeatTimeNeighbor(NodeHandle neighbor) {
	    NeighborState nState = (NeighborState) neighborsState.get(neighbor);
	    if(nState != null) {
		return nState.getLastForwardHeartbeatTime();
	    } else {
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +saarClient.endpoint.getId() + ": Retrieving forwardHeartbeatTime on a neighbor with missing entry in neighborsState, neighbor is " + neighbor, Logger.WARNING); 
		return 0;
	    }
	}



	public long getLastBMAPAdvertisementTimeNeighbor(NodeHandle neighbor) {
	    NeighborState nState = (NeighborState) neighborsState.get(neighbor);
	    if(nState != null) {
		return nState.getLastBMAPAdvertisementTime();
	    } else {
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +saarClient.endpoint.getId() + ": Retrieving lastBMAPAdvertismentTime on a neighbor with missing entry in neighborsState, neighbor is " + neighbor, Logger.WARNING); 
		return 0;
	    }
	}




	public boolean getNeedFreshBMAPNeighbor(NodeHandle neighbor) {
	    NeighborState nState = (NeighborState) neighborsState.get(neighbor);
	    if(nState != null) {
		return nState.getNeedFreshBMAP();
	    } else {
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +saarClient.endpoint.getId() + ": Retrieving needFreshBMAP on a neighbor with missing entry in neighborsState, neighbor is " + neighbor, Logger.WARNING); 
		return false;
	    }
	}


	
	// This is the time when the neighbor was discovered/acquired/established
	public long getAcquiredTimeNeighbor(NodeHandle neighbor) {
	    NeighborState nState = (NeighborState) neighborsState.get(neighbor);
	    if(nState != null) {
		return nState.getAcquiredTime();
	    } else {
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +saarClient.endpoint.getId() + ": Retrieving acquiredTime on a neighbor with missing entry in neighborsState, neighbor is " + neighbor, Logger.WARNING); 
		return 0;
	    }
	}
	
	// This is the time when the neighbor was discovered/acquired/established
	public boolean isActiveNeighbor(NodeHandle neighbor) {
	    NeighborState nState = (NeighborState) neighborsState.get(neighbor);
	    if(nState != null) {
		return nState.isActive();
	    } else {
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +saarClient.endpoint.getId() + ": Retrieving isActive (active/passive) on a neighbor with missing entry in neighborsState, neighbor is " + neighbor, Logger.WARNING); 
		return false;
	    }
	}
	
	// This is the time when the neighbor was discovered/acquired/established
	public boolean isMulticastSourceNeighbor(NodeHandle neighbor) {
	    NeighborState nState = (NeighborState) neighborsState.get(neighbor);
	    if(nState != null) {
		return nState.pIsMulticastSource;
	    } else {
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +saarClient.endpoint.getId() + ": Retrieving pIsMulticastSource (active/passive) on a neighbor with missing entry in neighborsState, neighbor is " + neighbor, Logger.WARNING); 
		return false;
	    }
	}
	
	/**** Getters for neighbor end here */
	

	// we only count the PRIMARY and assume we count it in each time period whether we actually request or not. This is because we do not have timestamps
	public void incrNumRequested(NodeHandle neighbor, int val) {
	    if(val > 1) {
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +"ERROR: We should not request more than one PRIMARY block in one requestBlocks() period", Logger.WARNING);
		System.exit(1);
	    }
	    NeighborState nState = (NeighborState) neighborsState.get(neighbor);
	    if(nState != null) {
		//nState.incrNumRequested(val); // Commented out on May 8, 2007 - It was dead code, we only used the setLastRequestTime to calculate getBWLimitReachedNeighbor
		if(val > 0) {
		    nState.setLastRequestedTime(getCurrentTimeMillis());
		}
	    } else {
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +"Unknown neighbor " + neighbor, Logger.WARNING); 
	    }	  
	    
	}
	
	
	public boolean getBWLimitReachedNeighbor(NodeHandle neighbor) {
	    NeighborState nState = (NeighborState) neighborsState.get(neighbor);
	    if(nState != null) {
		return nState.getBWLimitReached();
	    } else {
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +saarClient.endpoint.getId() + ": Retrieving forwardHeartbeatTime on a neighbor with missing entry in neighborsState, neighbor is " + neighbor, Logger.WARNING); 
		return true;
	    }
	}
	
	
	
	// called whenever serveBlocks() is invoked
	public void incrBWSupply(BWSupplyTuple supplytuple) {
	    // we simply add the entry, removing stale entries are done when the getUDynamic() is invoked
	    bwSupply.add(supplytuple);
	    if(SaarTest.logLevel <= 850) myPrint("blockbased: " + " #BWBlocksCanSent: " + supplytuple.numBlocks, 850); 
	}
       
	
	
	// called whenever some number of pkts are sent to a child
	public void incrBWConsumption(BWTuple tuple) {
	    // we simply add the entry, removing stale entries are done when the getUDynamic() is invoked
	    bwConsumption.add(tuple);
	    if(SaarTest.logLevel <= 850) myPrint("blockbased: " + " #BWBlocksSent: " + tuple.numBlocks, 850); 
	}
	
	public void updateMeshDepthEstimation(MeshDepthTuple tuple) {
	    // we simply add the entry, removing stale entries are done when the getUDynamic() is invoked
	    meshDepthEstimation.add(tuple);
	    if(SaarTest.logLevel <= 850) myPrint("blockbased: " + " MeshDepth: " + tuple.depth, 850); 
	}
	
	
	
	public Topic getTopic() {
	    return saartopic.baseTopic;
	}
	

	// static utilization is the ration of numChildren/MAXOutdegree
	public int getUStatic() {
	    double val;
	    val = ((double)numNeighbors())/((double)maximumOutdegree);
	    int uStatic = (int) (100*val);
	    return uStatic;
	}

	// dynamic utilization is the node's actual bandwidth. Whenever the outLink size is greater than 1 sec worth of packets we advertise as overloaded, else we report the actual moving window calculated value  
	public int getUDynamic() {
	    double val;

	    
	    // we firstly remove stale entries
	    long currtime = getCurrentTimeMillis();
	    while(bwConsumption.size() > 0) {
		BWTuple tuple = (BWTuple) bwConsumption.elementAt(0);
		if((currtime - tuple.sentTime) > BWCONSUMPTIONPERIOD) {
		    bwConsumption.remove(0);
		} else {
		    break;
		}
	    }

	    while(bwSupply.size() > 0) {
		BWSupplyTuple tuple = (BWSupplyTuple) bwSupply.elementAt(0);
		if((currtime - tuple.time) > BWCONSUMPTIONPERIOD) { // The supply uses the same period as consumption
		    bwSupply.remove(0);
		} else {
		    break;
		}
	    }





	    // We will now go over the vector and count the total pkts sent
	    int totalSent = 0;
	    for(int i=0; i< bwConsumption.size(); i++) {
		BWTuple tuple = (BWTuple) bwConsumption.elementAt(i);
		totalSent = totalSent + tuple.numBlocks;
	    }


	    // We will now go over the vector and count the total pkts we could have sent
	    double totalCanSent = 0;
	    for(int i=0; i< bwSupply.size(); i++) {
		BWSupplyTuple supplytuple = (BWSupplyTuple) bwSupply.elementAt(i);
		totalCanSent = totalCanSent + supplytuple.numBlocks;
	    }

	    

	    // Since one block is published every second
	    // We replaced nodedegree = maximumOutdegre/M to support homogeneous
	    //int totalCanSent = (int)((BWCONSUMPTIONPERIOD/PUBLISHPERIOD)*nodedegree);  
	    // We replaced the computation based on nodedegree to use backgroundbandwidth to support hybrid protocol
	    
	    int uDynamic;
	    if(totalCanSent == 0) {
		uDynamic = 100;
	    } else {
		val = ((double)totalSent)/totalCanSent;
		uDynamic = (int) (100*val);
	    }
	    
	    // We however ensure that our outlink in never more than PUBLISHPERIOD worth of packets
	    //if(outLink.size() > nodedegree) {
	    if((normalizedLinkSize > 1) || (uDynamic > 100)) {
		uDynamic = 100;
	    }

	    long currqueuedelay = saarClient.computeQueueDelay();
	    if(MQDBUDYNAMIC) {
		if(currqueuedelay >= SaarClient.MAXQUEUEDELAYBACKGROUND) {
		    uDynamic = 100;
		} else {
		    if(ONLYOVERLOADINFO) {
			uDynamic = 50; // fixed value independent of utilization
		    } else {
			uDynamic = (int) ((100 * currqueuedelay) / SaarClient.MAXQUEUEDELAYBACKGROUND); 
		    }
		}
	    }

	    
	    if(SaarTest.logLevel <= 875) myPrint("blockbased: getudynamic: " + uDynamic + ", totalSent: " + totalSent + ", totalCanSent: " + totalCanSent + " currqueuedelay: " + currqueuedelay + " normalizedLinkSize: " + normalizedLinkSize , 875);
	    
	    return uDynamic;	  
	}
	

	// dynamic utilization is the node's actual bandwidth  
	public int getAvgMeshDepth() {
	    double val;
	    // we firstly remove stale entries
	    long currtime = getCurrentTimeMillis();
	    while(meshDepthEstimation.size() > 0) {
		MeshDepthTuple tuple = (MeshDepthTuple) meshDepthEstimation.elementAt(0);
		if((currtime - tuple.recvTime) > MESHDEPTHESTIMATIONPERIOD) {
		    meshDepthEstimation.remove(0);
		} else {
		    break;
		}
	    }
	    // We will now go over the vector and count the total pkts sent
	    int totalEntries = 0;
	    int sumDepth = 0;
	    for(int i=0; i< meshDepthEstimation.size(); i++) {
		MeshDepthTuple tuple = (MeshDepthTuple) meshDepthEstimation.elementAt(i);
		totalEntries ++;
		sumDepth = sumDepth + tuple.depth;
	    }
	    int avgDepth = -1;
	    if(totalEntries > 0) {
		avgDepth = (int)(sumDepth/totalEntries);
	    }
	    return avgDepth;	  
	}
	
	
	public NodeHandle[] getNeighbors() {
	    return (NodeHandle[]) neighbors.toArray(new NodeHandle[0]);
	}
	
	
	public int numNeighbors() {
	    return neighbors.size();
	}
	
	
	public boolean isExistingNeighbor(NodeHandle responder) {
	    for(int i=0; i<neighbors.size();i++) {
		NodeHandle nh = (NodeHandle)neighbors.elementAt(i);
		if(responder.equals(nh)) {
		    return true;
		}
	    }
	    return false;
	    
	}
	

	public void update(Observable o, Object arg) {
	    if (arg.equals(NodeHandle.DECLARED_DEAD)) {
		if (neighbors.contains(o)) {
		    if(SaarTest.logLevel <= Logger.FINE) myPrint("blockbased: " +saarClient.endpoint.getId() + ": CESM.Neighbor " + o + " for topic " + getTopic() + " has died - removing.", Logger.FINE);
		    
		    removeNeighbor((NodeHandle) o);
		}
		
	    }
	}
	
      public boolean containsNeighbor(NodeHandle neighbor) {
	  if(neighbors.contains(neighbor)) {
	      return true;
	  } else {
	      return false;
	  }
      }

      
	// active- denotes that we initiated the discovery of the neighbor 
	// passive means that we accept the neighbor request
	public void addNeighbor(NodeHandle neighbor, boolean active, boolean pIsMulticastSource) {
	    if(SaarTest.logLevel <= 880) myPrint("blockbased: " +"PRM.addNeighbor( " + getTopic() + ", " + neighbor + ", " + active + ", " + pIsMulticastSource + ")", 880);
	    
	    if (!neighbors.contains(neighbor)) {
		if(neighbor.isAlive()) {
		    // We update the childrenState data structure
		    if(!neighborsState.containsKey(neighbor)) {
			neighborsState.put(neighbor, new NeighborState(neighbor,active, pIsMulticastSource));
		    }
		    neighbors.add(neighbor);
		    neighbor.addObserver(this);
		    // We update this information to the global data structure of children -> topics
		} else {
		    if(SaarTest.logLevel <= Logger.WARNING) myPrint("blockbased: " +"WARNING: addNeighbor( " + getTopic() + ", " + neighbor + ", " + active + ") did not add neighbor since the neighbor.isaLive() failed", Logger.WARNING);
		    
		}
		
	    }
	    
	}
	

	public boolean removeNeighbor(NodeHandle neighbor) {
	    if(SaarTest.logLevel <= 880) myPrint("blockbased: " +"PRM.removeNeighbor( " + getTopic() + ", " + neighbor + ")", 880);
	    
	    
	    // We update the childrenState data structure
	    if(neighborsState.containsKey(neighbor)) {
		neighborsState.remove(neighbor);
	    }
	    
	    neighbors.remove(neighbor);
	    neighbor.deleteObserver(this);
	    
	    return true;
	}
	
	

    }


    
}






