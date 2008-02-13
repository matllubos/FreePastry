/*
 * Created on May 4, 2005
 */
package rice.p2p.saar.singletree;


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
public class SingletreeClient extends DataplaneClient {

    public static final long ANYCASTTIMEOUT = 1500; // After issuing the previous anycast the node must wait for atleast thsi time




    public static boolean ENABLETREEIMPROVEMENT = true;

    public static boolean ENABLE_PREMPT_DEGREE_PUSHDOWN = true;


    public Hashtable numSequence; // this is a hashtbale of the sequence numbers I got and the number of times I got.

    // these 2 variables will be used determine the expected packet number 
    public long firstPktTime = 0;
    public int firstPktSequence = -1;
    public long firstBroadcastTime = 0;

    // This is a control pkt flowing down the Scribe tree telling me what the root is currently publishing
    public int rootSeqNumRecv = -1; 

    public Hashtable blocks; // This hashtable stores the distribution tree of the blocks we receive

    /***  These variables track the current state of the node ****/
    public TemporalBufferMap sendbmap; // This bmap will be used only for debugging/tracking purposes, to help us track the quality the node is receiving
    
    public int streamingQuality = 99; // This is based on the left end bits (advertisedwindow - fetchwindow) of the temporalBMAP . We DO NOT initialize with '-1' because this might indicate very poor streaming Quality when the system is not bootstrapped (it has not received control pkt on Scribe tree)

    public int treeDepth = -1; 

    public int minAvailableDepth = -1;
    
    public boolean parentIsAlive; // This is true when we received a periodic heartbeat from the parent
    
    public boolean isConnected; // This is true if we are getting packets from the source via are pathToRoot in the recent past, note that having parentIsAlive does not guarantee isConnected


    public int[] grpAvailableSlotsAtDepth; 

    public int[] grpDepthAtNodeHavingChildOfDegree; 



    public long lastPktRecvTime; // This is the last time we received a pkt on the tree data plane
    public int lastPktRecvSeqNum; // This is the last pkt received on this sequence number
    

    public static long PUBLISHPERIOD  = 200; // exprimenting with faster rate to check if the tree repair times less than packet rate leads to wrong conclusions . 1000, this is the rate at which the multicast source publishes

    public static long BLOCKPERIOD = 200; 

    public TopicManager manager;

    public int maximumOutdegree = -1; 
    
    public Random rng = new Random();


    public static int MAXADVERTISEDTREEDEPTH = 100; // We advertise this value when we are not connected to the source (i.e we do not have a valid pathToRoot)  


    //public static final long PARENTHEARTBEATPERIOD = 1000; // 250 (in ms) This is the period in which the parents sends a heartbeat to its children. We need this even in simulation, because a parent needs to send the updated pathToRoot to his children when it updates is path. However it dampens the updates when the pathToRoot does not change, Switched to using SAARClient.NEIGHBORHEARTBEATPERIOD

    //public static final long CHILDHEARTBEATPERIOD = 1000; // 1000 or 100000000(in ms) This is the period in which the children are excepted to send a heartbeat to their parents, this enables us to remove children who have left abruptly. For simulation purposes we can assume that we rely on the unsubscribe messages to remove children and dont need timeouts to expire dead children, Switched to using SAARClient.NEIGHBORHEARTBEATPERIOD

    //public static int PARENTDEADTHRESHOLD = 2000; // We declare a neighbor dead if we have not heard from him for 2 sec
    //public static int CHILDDEADTHRESHOLD = 2000; // We declare a neighbor dead if we have not heard from him for 2 sec , Switched to using NEIGHBORDEADTHRESHOLD



    public long lastChildHeartbeatSentTime = 0; // this is the last time the local node sent a heartbeat to its parent
    public long lastParentHeartbeatSentTime = 0; // this is the last time the local node sent a heartbeat to its children

    public static final long TREEIMPROVEMENTPERIOD = 15000; // 15000 // This is the period at which the node issues anycast to improve the uality of the tree (i.e say DEPTH,RDP etc)

    public static final long PREMPTDEGREEPUSHDOWNPERIOD = 5000;


    public long lastTreeImprovementTime = 0; // this is the last time we sent an anycast to do tree optimization


    public long lastNeighborMaintenanceInvokationTime = 0;

    public long	lastPremptDegreePushdownTime = 0; // this is the last time we send out an anycast to do prempt-degree pushdown

    public long lastBackpointerNotifyTime = 0;

    public long CHILDRESERVATIONTIME = 2000; // we hold a reservation for 2 sec


    public Id zeroId;

  
    public boolean amSubscribed = false; // This variable will be set/unset in the join/leave methods


    public static int NUMSTRIPES = 1; // these number of stripes will be used for redundant coding in the time domain

    public static long STRIPEINTERVALPERIOD = 1000; // this is the spacing such that the consecutive stripes of a particular sequence are not correlated w.r.t loss due to a tree disrupture. The assumption is that the tree will become repaired within this time 'STRIPEINTERVALPERIOD'


    public static boolean ENABLEPRM = false;

    public static int ACQUIREDTHRESHOLD = 300000; // 120000 We periodically acquire fresh neighbors to make the PRM random graph construction random


    public static int NUMPRMNEIGHBORS = 4;  // these are the number of neighbors in PRM

    public static double PRMBETA = 0.1;  // this is the 'Beta' paprameter of PRM, the probability of forwarding an unseen ( to avoid duplicates) packet to a neighbor

    public Random[] prmRng = new Random[10]; // we assume that NUMPRMNEIGHBORS is less than 10 

    public Random linklossRng = new Random(); 

    public int numpktssent = 0;

    public boolean TIMEDOMAINCODING = false;

    public static int DATAMSGSIZEINBYTES;

    public double grpMinAvailableDepth; // We made this global on Oct26 to dampen issuing of anycast where there are no group resources


    public long lastAnycastForRepairTime = 0; // only corresponds to detecting failure and issuing anycast. does not include cases for periodic tree repairs or sending anycast when receiving unsubscribe message
    public long expbackoffInterval = 500;
    public long maxExpbackoffInterval = 500;

    public double dataplaneriusedbycontrol = 0; 

    public static double SPARERITOTAKECHILD = 1.05;

    public static double SPARERITODROPCHILD = 0.01; 

    public int NUMFRAGMENTSPERSECPERCHILD; // This is the number of pkts sent per child per sec. This is computed based on the publish period

    public SingletreeClient(SaarClient saarClient, int tNumber, SaarTopic saartopic, String topicName, int dataplaneType, double nodedegreeControlAndData, boolean amMulticastSource, boolean amVirtualSource) {
	super(saarClient, tNumber, saartopic, topicName, dataplaneType,nodedegreeControlAndData, amMulticastSource, amVirtualSource);

	PUBLISHPERIOD  = BLOCKPERIOD; 

	this.nodedegree = nodedegreeControlAndData; // This is the initial value that can be used by the dataplane of which it will be later recomputed after subtracting the controlRI

	maximumOutdegree = (int)this.nodedegree ; // This would not be able to use the fractional bandwidths
	if(SaarTest.logLevel <= 880) myPrint("singletree: INITIAL nodedegree: " + nodedegree + " MAXIMUMOUTDEGREE: " + maximumOutdegree, 880);
	double contributableRI = maximumOutdegree;
	if(SaarTest.logLevel <= 880) myPrint("singletree: CONTRIBUTABLERI: " + contributableRI, 880);

	numSequence = new Hashtable();
	blocks = new Hashtable();
	zeroId = rice.pastry.Id.build();
	grpAvailableSlotsAtDepth = new int[SingletreeContent.MAXSATURATEDDEPTH];

	grpDepthAtNodeHavingChildOfDegree = new int[SingletreeContent.MAXCHILDDEGREE + 1];
	for(int i=0; i< (SingletreeContent.MAXCHILDDEGREE + 1); i++) {
	    grpDepthAtNodeHavingChildOfDegree[i] = MAXADVERTISEDTREEDEPTH;
	}

	for(int i=0; i< 10; i++) {
	    prmRng[i] = new Random();
	}

	linklossRng = new Random();





	manager = new TopicManager(saartopic);

	if(amMulticastSource) {
	    if(SaarTest.logLevel <= 880) myPrint("singletree: I AM SINGLETREE MULTICAST SOURCE", 880);
	}
	saarClient.reqRegister(saartopic, this);

	saarClient.viewer.setNumForegroundStripes(NUMSTRIPES);

	saarClient.viewer.setForegroundPublishPeriod(PUBLISHPERIOD);  // Added on Sep21-2007 after noticing that when experimenting with different publish periods in the singletree/mesh, the FOREGROUNFPUBLISHPERIOD value used in serveBlocks() in the BlockbasedClient was set always to a default value of 1000

	
	NUMFRAGMENTSPERSECPERCHILD = (int)(1000/PUBLISHPERIOD);

	DATAMSGSIZEINBYTES = (int)(SaarClient.STREAMBANDWIDTHINBYTES / ((1000/PUBLISHPERIOD) * NUMSTRIPES));

	if(SaarTest.logLevel <= 880) myPrint("singletree: " + "BLOCKPERIOD: " + BLOCKPERIOD + " PUBLISHPERIOD: " + PUBLISHPERIOD + " NUMFRAGMENTSPERSECPERCHILD: " + NUMFRAGMENTSPERSECPERCHILD + " DATAMSGSIZEINBYTES: " + DATAMSGSIZEINBYTES, 880);

    }


    public long getCurrentTimeMillis() {
	return saarClient.getCurrentTimeMillis();
    }

    public void join() {
	if(SaarTest.logLevel <= 880) myPrint("singletree: singletreeclient.join()", 880);
	rootSeqNumRecv = -1;
	long currtime = getCurrentTimeMillis();
	//firstPktSequence = -1;
	//firstPktTime = 0;
	if(firstPktSequence == -1) {
	    //firstPktSequence = rice.p2p.saar.simulation.SaarSimTest.simulatorbroadcastseqnum;
	    firstPktSequence = saarClient.viewer.getForegroundBroadcastSeqnum();
	    firstPktTime = currtime;
	}
	
	lastAnycastForRepairTime = 0;
	expbackoffInterval = 500;
	maxExpbackoffInterval = 500;


	lastPktRecvTime = 0;
	parentIsAlive = false;
	numpktssent = 0;
	
	
	saarClient.reqSubscribe(tNumber);
	amSubscribed = true;

	//saarClient.viewer.setFirstForegroundPktToExpectAfterJoin(saarClient.viewer.getForegroundBroadcastSeqnum());
	saarClient.viewer.setFirstForegroundPktAfterJoin(-1);
	

    }


    public void dataplaneMaintenance() {
	if(SaarTest.logLevel <= 875) myPrint("singletree: singletreeclient.dataplaneMaintenance()", 875);
	if(amMulticastSource) {
	    sendMulticastTopic();
	}

	sendHeartbeats();
	
	neighborMaintenance(); // Here it attempts to remove dead children / maintain a 'parent' for the singletree data plane. Note that having to remove dead children implies even the multicast source would invoke this
	
	
    }


    // Create a content using public SingletreeContent(int mode, String topicName, int tNumber, boolean aggregateFlag, int descendants, TemporalBufferMap bmap, int usedSlots, int totalSlots, int streamingQuality, boolean isConnected, int treeDepth, int minAvailableDepth, int sourceBroadcastSeq, Id[] pathToRoot) {
    public void controlplaneUpdate(boolean forceUpdate) {
	if(SaarTest.logLevel <= 875) myPrint("singletree: singletreeclient.controlplaneUpdate()", 875);
	sendbmap = computeSendBMAP();
	streamingQuality = getStreamingQualityToAdvertise(sendbmap);
	int usedSlots = manager.numChildren();
	int totalSlots = this.maximumOutdegree;
	isConnected = evaluateIsConnected();
	treeDepth = manager.getTreeDepth();
	int minAvailableDepth;
	if(usedSlots < totalSlots) {
	    minAvailableDepth = treeDepth;
	} else {
	    minAvailableDepth = Integer.MAX_VALUE;
	}
	Id[] mypathToRoot = manager.getPathToRoot();
	int[] availableSlotsAtDepth = new int[SingletreeContent.MAXSATURATEDDEPTH];
	int[] depthAtNodeHavingChildOfDegree ;
	String depthAtNodeHavingChildOfDegreeString ;


	for(int i=0; i< SingletreeContent.MAXSATURATEDDEPTH; i++) {
	    availableSlotsAtDepth[i] = 0;
	    if(treeDepth == i) {
		availableSlotsAtDepth[i] = totalSlots - usedSlots;
	    }
	}




	depthAtNodeHavingChildOfDegree = manager.getDepthAtNodeHavingChildOfDegree();
	depthAtNodeHavingChildOfDegreeString = "[";
	for(int i=0; i< (SingletreeContent.MAXCHILDDEGREE + 1); i++) {
	    depthAtNodeHavingChildOfDegreeString = depthAtNodeHavingChildOfDegreeString + depthAtNodeHavingChildOfDegree[i] + ", ";
	    }
	depthAtNodeHavingChildOfDegreeString = depthAtNodeHavingChildOfDegreeString + "] ";


	//String pathToRootAsString = getPathToRootAsString(mypathToRoot);
	String pathToRootAsString = manager.getPathToRootAsString();


	if(SaarTest.logLevel <= 875) myPrint("singletree: SENDBMAP " + " BMAP: " + sendbmap + " usedSlots: " + usedSlots + " totalSlots: " + totalSlots + " StreamingQuality: " + streamingQuality + " isConnected: " + isConnected + " treeDepth: " + treeDepth + " minAvailableDepth: " + minAvailableDepth + " rootSeqNumRecv: " + rootSeqNumRecv + " pathToRoot: " + pathToRootAsString + " depthAtNodeHavingChildOfDegreeString: " + depthAtNodeHavingChildOfDegreeString, 875);
	int selfBroadcastSeq = saarClient.allTopics[tNumber].pSeqNum -1; // this value will be -1 for nonsources always and incremented every timeperiod for the source in the sendMulticastTopic()
	SingletreeContent saarContent = new SingletreeContent(SaarContent.UPWARDAGGREGATION, topicName, tNumber, false, 1, sendbmap, usedSlots, totalSlots, streamingQuality, isConnected, treeDepth, minAvailableDepth, selfBroadcastSeq,mypathToRoot, availableSlotsAtDepth, depthAtNodeHavingChildOfDegree);

	saarClient.reqUpdate(tNumber,saarContent,forceUpdate);

    }

    public void leave() {
	if(SaarTest.logLevel <= 880) myPrint("singletree: singletreeclient.leave()", 880);
	Topic topic = saartopic.baseTopic;
	if(!amMulticastSource) {
	    saarClient.reqUnsubscribe(tNumber);

	    if(SaarClient.LEAVENOTIFY) {
		// We will also send out Unsubscribe messages to our parent/children
		NodeHandle myparent = manager.getParent();
		if(myparent!= null) {
		    if(SaarTest.logLevel <= 875) myPrint("singletree: " + saarClient.endpoint.getId() + ": Sending UnsubscribeMsg to parent " + myparent, 875);
		    saarClient.endpoint.route(null, new UnsubscribeMsg(saarClient.endpoint.getLocalNodeHandle(),topic), myparent);
		    manager.setParent(null);
		}
		NodeHandle[] mychildren = manager.getChildren();
		for(int i=0; i<mychildren.length; i++) {
		    if(SaarTest.logLevel <= 875) myPrint("singletree: " + saarClient.endpoint.getId() + ": Sending UnsubscribeMsg to child " + mychildren[i], 875);
		    saarClient.endpoint.route(null, new UnsubscribeMsg(saarClient.endpoint.getLocalNodeHandle(),topic), mychildren[i]);
		    manager.removeChild(mychildren[i]);
		}
		
		
		NodeHandle[] mybackpointers = manager.getBackpointers();
		for(int i=0; i< mybackpointers.length; i++) {
		    NodeHandle nh = mybackpointers[i];
		    // We will send a Unsubscribe notification message to this node
		    if(SaarTest.logLevel <= 875) myPrint("singletree: " + saarClient.endpoint.getId() + ": Sending PRMUnsubscribe message to backpointers " + nh, 875);
		    boolean toRemoveForwardpointer = true;
		    boolean toRemoveBackpointer = false;
		    saarClient.endpoint.route(null, new PRMUnsubscribeMessage(saarClient.endpoint.getLocalNodeHandle(),topic, toRemoveForwardpointer, toRemoveBackpointer), nh);
		    manager.removeBackpointer(nh);
		}
		
		
		NodeHandle[] myneighbors = manager.getNeighbors();
		for(int i=0; i< myneighbors.length; i++) {
		    NodeHandle nh = myneighbors[i];
		    // We will send a Unsubscribe notification message to this node
		    if(SaarTest.logLevel <= 875) myPrint("singletree: " + saarClient.endpoint.getId() + ": Sending PRMUnsubscribe message to forwardpointers " + nh, 875);
		    boolean toRemoveForwardpointer = false;
		    boolean toRemoveBackpointer = true;
		    saarClient.endpoint.route(null, new PRMUnsubscribeMessage(saarClient.endpoint.getLocalNodeHandle(),topic, toRemoveForwardpointer, toRemoveBackpointer), nh);
		    manager.removeNeighbor(nh);
		}
	    }
		


	    amSubscribed = false;
	    // We will also clear off the Hashtables blocks/numSequence
	    blocks.clear();
	    numSequence.clear();
	    manager.initialize();
	    saarClient.viewer.initialize();

	}


    }


    // This function should be a superset of the predicateSatisfied() method in SaarContent (i.e SingleTreeContent), when the aggregateFlag = false;
    public boolean recvAnycast(SaarTopic saartopic, Topic topic, SaarContent requestorcontent) {
	if(SaarTest.logLevel <= 875) myPrint("singletree: singletreeclient.recvanycast(" + "gId:" + requestorcontent.anycastGlobalId + ", saartopic:" + saartopic + ", topic:" + topic + ")", 875);

	SingletreeContent myContent = (SingletreeContent)requestorcontent;

	if(saarClient.endpoint.getLocalNodeHandle().equals(myContent.anycastRequestor)) {
	    // We do not accept the anycast made by ourself
	    if(SaarTest.logLevel <= 875) myPrint("singletree: anycast(self=requestor, ret=false)", 875);
	    return false;
	    
	}

	if(myContent.mode == SingletreeContent.ANYCASTFORPRMNEIGHBOR) {
	    if(!amMulticastSource) {
		if(SaarTest.logLevel <= 875) myPrint("singletree: ANYCASTFORPRMNEIGHBOR-CONDITION-PASSED", 875);
		// We will send the anycastackmsg
		saarClient.endpoint.route(null, new MyAnycastAckMsg(myContent, saarClient.endpoint.getLocalNodeHandle(), saartopic.baseTopic), myContent.anycastRequestor);
		
		return true;
	    } else {
		if(SaarTest.logLevel <= 875) myPrint("singletree: anycast(multicastsource-isnotvalid-as-prmneighbor, ret=false) ", 875);
		return false;
	    }
	}

	if(manager.containsChild(myContent.anycastRequestor)) {
	    if(SaarTest.logLevel <= 875) myPrint("singletree: anycast(self=requestorIsExistingChild, ret=false) ", 875);
	    return false;
	    
	}

	if((myContent.mode!= SingletreeContent.ANYCASTFORPREMPTDEGREEPUSHDOWN) && (manager.numChildren() >= this.maximumOutdegree)) {
	    if(SaarTest.logLevel <= 875) myPrint("singletree: anycast(uStaticLimitReached, ret=false) ", 875);
	    return false;
	}
	
	// We check to see if we are getting good performance 
	isConnected = evaluateIsConnected();
	if(!isConnected) {
	    if(SaarTest.logLevel <= 875) myPrint("singletree: anycast(poorInstantaneousQuality, ret=false) ", 875);
	    return false;
	}


	if(manager.hasLoops(myContent.anycastRequestor.getId())) {
	    if(SaarTest.logLevel <= 875) myPrint("singletree: anycast(hasLoops, ret=false) ", 875);
	    return false;

	}


	// We additionally check for depth when the the mode is ANYCASTFORTREEIMPROVEMENT
	treeDepth = manager.getTreeDepth();

	if((myContent.mode == SingletreeContent.ANYCASTFORTREEIMPROVEMENT) && ((treeDepth + 1) >= myContent.treeDepth)) {
	    if(SaarTest.logLevel <= 875) myPrint("singletree: anycast(mode=TREEIMPROVEMENT, hasGreaterOREqualDepth, ret=false) ", 875);
	    return false;

	}






	if(myContent.mode== SingletreeContent.ANYCASTFORPREMPTDEGREEPUSHDOWN) {
	    // We need to prempt somebody if the condition for premption satisfies

	    boolean willAcceptByDroping = true;
	    int[] depthAtNodeHavingChildOfDegree = manager.getDepthAtNodeHavingChildOfDegree();;
	    
	    
	    // We assume here the requestorsdegree >0 and requestors has available capacity (thse conditions are checked before the requestor can issue an anycast)
	    if(manager.numChildren() < maximumOutdegree) {
		if(SaarTest.logLevel <= 875) myPrint("singletree: PREMPTDEGREEPUSHDOWN-whypremptsincefreeslotsavailable-CONDITION-FAILED", 875);
		willAcceptByDroping = false; // Note that this requestor will be accepted as a child below without having to drop any existing child

		// } else if(!((myContent.requestorsDegree > 1) && (((depthAtNodeHavingChildOfDegree[0] + 1) < myContent.treeDepth) || ((depthAtNodeHavingChildOfDegree[1] + 1) < myContent.treeDepth))))  {  // Updated on Jan15-2008 to allow a node of outdegree=1 to prempt a node with outdegree=0;
	    } else if(!( ((myContent.requestorsDegree > 1) && (((depthAtNodeHavingChildOfDegree[0] + 1) < myContent.treeDepth) || ((depthAtNodeHavingChildOfDegree[1] + 1) < myContent.treeDepth)))    ||     ((myContent.requestorsDegree == 1) && ((depthAtNodeHavingChildOfDegree[0] + 1) < myContent.treeDepth))   ))  {
		

		if(SaarTest.logLevel <= 875) myPrint("singletree: PREMPTDEGREEPUSHDOWN-depth-CONDITION-FAILED", 875);
		willAcceptByDroping = false;
	    } else if(maximumOutdegree == 0) {
		if(SaarTest.logLevel <= 875) myPrint("singletree: PREMPTDEGREEPUSHDOWN-maximumOutdegree-CONDITION-FAILED", 875);
		willAcceptByDroping = false;
	    } else {
		if(SaarTest.logLevel <= 875) myPrint("singletree: PREMPTDEGREEPUSHDOWN-DEPTH-CONDITION-PASSED", 875);
		willAcceptByDroping = true;
		
	    }
	    if(!willAcceptByDroping) {
		if(SaarTest.logLevel <= 875) myPrint("singletree: anycast(mode=ANYCASTFORPREMPTDEGREEPUSHDOWN, anycastneighbormode=ACCEPTBYDROPING condition failed, ret=false) ", 875);
		return false;
	    } else {
		
		// We will choose a lowest-degree child and give it the address of the anycast requestor as the prospective parent
		if(SaarTest.logLevel <= 875) myPrint("singletree: COOL: Accepted PREMPTDEGREEPUSHDOWN request by ACCEPTBYDROPPING", 875);
		
		NodeHandle premptedChild; // we choose the lowest degree child
		premptedChild = manager.getLowestDegreeChild();


		if(premptedChild == null) {
		    System.out.println("ERROR: if no such child existed, the previous check of myContent.requestorsTreeDepth <= (depthAtNodeHavingChildOfDegree[0] + 1) should have resulted in willAcceptByDroping = false");
		    System.exit(1);
		}
		if(SaarTest.logLevel <= 875) myPrint("singletree:  Sending PreemptChildMsg to child " + premptedChild, 875);
		saarClient.endpoint.route(null, new PremptChildMsg(saarClient.endpoint.getLocalNodeHandle(),topic, myContent.anycastRequestor), premptedChild);
		manager.removeChild(premptedChild);
		
	    }
	    
	}
	

	
	// We accept the anycast, add the new neighbor connection, and notify the anycast requestor
	manager.addChild(myContent.anycastRequestor, myContent.requestorsDegree);

	myContent.setResponderDepth(treeDepth);
	saarClient.endpoint.route(null, new MyAnycastAckMsg(myContent, saarClient.endpoint.getLocalNodeHandle(), saartopic.baseTopic), myContent.anycastRequestor);
    

	// Instead of sending the pathToRootinfo to the child rightaway, we will send it when we receive the ChildIsAliveMsg. This is because of a race condition where if the PathToRootInfoMsg reaches earlier than the MyAnycastAckMsg then the path information is disrgarded since it came from an unknown parent


	// Since our state has change we proactively invoke controlplaneUpdate to update local state on the local node. Note that this does not mean that the update will be sent to the parent immediately
	controlplaneUpdate(true); 


	return true;

    }


    // When a SAAR Publish message (used for downward propagation) is delivered at the local node
    public void grpSummary(SaarTopic saartopic, Topic topic, SaarContent content) {
	if(SaarTest.logLevel <= 850) myPrint("singletree: singletreeclient.grpSummary(" + "saartopic:" + saartopic + ", topic:" + topic, 850);
	SingletreeContent myContent = (SingletreeContent) content;
	int topicNumber = myContent.tNumber; 

	if(tNumber != topicNumber) {
	    System.out.println("tNumber:" + tNumber + ", topicNumber:" + topicNumber);
	    System.out.println("ERROR: The tNumbers should match, else indicates some error in aggregation");
	    System.exit(1);
	}


	int saardepth = myContent.numScribeIntermediates;
	long currtime = getCurrentTimeMillis();
	double grpuStatic = 100*((double)myContent.usedSlots)/((double)myContent.totalSlots);  // Here the total slots also include those nodes which are not connected yet but subscribed
	grpMinAvailableDepth = myContent.minAvailableDepth;   // This is the desired  indicator for available resources on nodes that are connected to tree
	String slotsAtDepthString = myContent.getSlotsAtDepthString();
	// We will copy over the grpAvailableSlotsAtDepth information


	for(int i=0; i< SingletreeContent.MAXSATURATEDDEPTH; i++) {
	    grpAvailableSlotsAtDepth[i] = myContent.availableSlotsAtDepth[i];
	}

	// We will copy over the grpDepthAtNodeHavingChildOfDegree information
	String depthAtNodeHavingChildOfDegreeString = "";
	depthAtNodeHavingChildOfDegreeString = myContent.getDepthAtNodeHavingChildOfDegreeString();
	for(int i=0; i< (SingletreeContent.MAXCHILDDEGREE + 1); i++) {
	    grpDepthAtNodeHavingChildOfDegree[i] = myContent.depthAtNodeHavingChildOfDegree[i];
	}
	
	

	if(SaarTest.logLevel <= 850) myPrint("singletree: sourceIsBroadcasting(" + "tNumber: " + topicNumber+ "," + currtime + ", saardepth: " + saardepth + ", sourceBroadcastSeq: " + myContent.sourceBroadcastSeq+ ", grpSize: " + myContent.descendants + ", grpQuality: " + myContent.streamingQuality + ", grptreeDepth: " + myContent.treeDepth + ", grpUStatic: " + grpuStatic + ", grpSlotsAtDepth: " + slotsAtDepthString + ", grpDepthAtNodeHavingChildOfDegree: " + depthAtNodeHavingChildOfDegreeString + ", grpMinAvailableDepth: " + grpMinAvailableDepth + " )", 850);


	rootSeqNumRecv = myContent.sourceBroadcastSeq;	    
	if(firstPktSequence == -1) {
	    firstPktSequence = myContent.sourceBroadcastSeq;
	    firstPktTime = currtime;
	}

	int selfestimatedRWindow = firstPktSequence + (int)((currtime - firstPktTime)/PUBLISHPERIOD);
	int controlPktRWindow = rootSeqNumRecv;
	//if(SaarTest.logLevel <= 875) myPrint("singletree: RWINDOW-ESTIMATION:" + "controlPkt:" + controlPktRWindow + ", selfestimate: " + selfestimatedRWindow, 875);
	
    }
    
    
    public void recvAnycastFail(SaarTopic saartopic, Topic topic, NodeHandle failedAtNode, SaarContent content) {
	if(SaarTest.logLevel <= 880) myPrint("singletree: singletreeclient.recvAnycastFail(" + "saartopic:" + saartopic + ", topic:" + topic + ", anycastId:" + content.anycastGlobalId + ")", 880);

    }



    public void recvDataplaneMessage(SaarTopic saartopic, Topic topic, SaarDataplaneMessage message) {

	if(message instanceof MyAnycastAckMsg) {
	    // We might get an ack to a anycast request we had sent out before unsubscribing, we do not react upon such acks
	    if(amSubscribed) {
		expbackoffInterval = 500;
		maxExpbackoffInterval = 500;
		Topic myTopic = saartopic.baseTopic;	
		MyAnycastAckMsg  ackMsg = (MyAnycastAckMsg)message;
		if(SaarTest.logLevel <= 880) myPrint("singletree:  Received " + ackMsg, 880);
		long currtime = getCurrentTimeMillis();
		SingletreeContent content = (SingletreeContent) ackMsg.content;

		if(content.mode == SingletreeContent.ANYCASTFORPRMNEIGHBOR) {
		    // responses to establish the prm random graph
		    if(SaarTest.logLevel <= 875) myPrint("singletree: Accepted PRMNEIGHBOR " + ackMsg.getSource(), 875);
		    if(!manager.containsNeighbor(ackMsg.getSource())) {
			addNeighbor(ackMsg.getSource());
		    } else {
			if(SaarTest.logLevel <= 875) myPrint("singletree:  MyAnycastAckMsg returned existing neighbor " + ackMsg.getSource(), 875);
		    }

		} else {
		    // responses for parents in the tree structure
		    boolean acceptedNewParent = false;
		    long difftime = currtime - manager.getLastReverseHeartbeatTimeParent();
		    
		    if((manager.getParent() == null) || (difftime >= SaarClient.NEIGHBORDEADTHRESHOLD)) {
			// We do not have a parent, so we set the responder as parent
			acceptedNewParent = true;
		    } else {
			
			// We will set the responder if its depth is better than current depth. We ensure that the node is connected to ensure that the current depth is well defined 
			int newExpectedDepth = content.responderDepth + 1;		
			treeDepth = manager.getTreeDepth();
			isConnected = evaluateIsConnected();
			if(isConnected && (newExpectedDepth < treeDepth)) {
			    // We will switch to the newparent
			    if(SaarTest.logLevel <= 875) myPrint("singletree:  Switching to better parent, prevDepth: " + treeDepth + " newExpectedDepth: " + newExpectedDepth, 875);
			    acceptedNewParent = true;
			}
		    }
		    
		    // We will send an ChilsIsAliveMsg instantly to denote acceptance of the conenction
		    if(acceptedNewParent) {
			if(SaarTest.logLevel <= 875) myPrint("singletree: Accepted NEW PARENT", 875);
			NodeHandle myoldparent = manager.getParent();
			// We will send un UnsubscribeMsg to our old parent
			if((myoldparent != null) && !myoldparent.equals(ackMsg.getSource())) {
			    if(SaarTest.logLevel <= 875) myPrint("singletree: " + saarClient.endpoint.getId() + ": Sending UnsubscribeMsg to old parent " + myoldparent, 875);
			    saarClient.endpoint.route(null, new UnsubscribeMsg(saarClient.endpoint.getLocalNodeHandle(),topic), myoldparent);
			}
			
			manager.setParent(ackMsg.getSource());
			NodeHandle myparent = manager.getParent();
			if(SaarTest.logLevel <= 875) myPrint("singletree:  Sending ChildIsAliveMsg to newly established parent " + myparent, 875);
			saarClient.endpoint.route(null, new ChildIsAliveMsg(saarClient.endpoint.getLocalNodeHandle(), myTopic), myparent);	    
		    }
		}
		
		
	    }
	}

	if(message instanceof PathToRootInfoMsg) {
	    PathToRootInfoMsg pathMsg = (PathToRootInfoMsg) message;
	    if(SaarTest.logLevel <= 875) myPrint("singletree:  Received " + pathMsg, 875);
	    if(pathMsg.getSource().equals(manager.getParent())) {
		if(!loopDetected(pathMsg.getPathToRoot())) {
		    manager.appendPathToRoot(pathMsg.getPathToRoot());
		    manager.setLastReverseHeartbeatTimeParent(getCurrentTimeMillis());
		} else {
		    if(SaarTest.logLevel <= Logger.WARNING) myPrint("singletree: ERROR: A loop has been detected in the dataplane, pathToRoot: " + pathMsg.getPathToRoot(), Logger.WARNING);
		    // To break the loop We will unsubscribe from our parent, setParent as null. We do not issue anycast for repair here, the neighborMaintenance should trigger off to do this.
		    NodeHandle myparent = manager.getParent();
		    if(myparent!= null) {
			if(SaarTest.logLevel <= 875) myPrint("singletree: " + saarClient.endpoint.getId() + ": Sending UnsubscribeMsg to parent " + myparent, 875);
			saarClient.endpoint.route(null, new UnsubscribeMsg(saarClient.endpoint.getLocalNodeHandle(),topic), myparent);
			manager.setParent(null);
		    }
		}

	    } else {
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("singletree: " + saarClient.endpoint.getId() + ": Received PathToRootInfoMsg from unknown parent " + pathMsg.getSource(), Logger.WARNING);
	    }

	}

	if(message instanceof ChildIsAliveMsg) {
	    ChildIsAliveMsg hMsg = (ChildIsAliveMsg) message;
	    if(SaarTest.logLevel <= 875) myPrint("singletree:  Received " + hMsg, 875);
	    if(manager.containsChild(hMsg.getSource())) {
		
		// When the child acknowledges the establishment of the parent-child link by sending the ChildIsAliveMsg, we send the child the currpathtoRoot. Note that we do not send this rightaway when we send the MyAnycastAckMsg because of a race condition of the PathToRoot reaching the child before the AnycastAckMsg and the child disregarding the message thinking it came from an unknown parent
		if(!manager.gotChildAck(hMsg.getSource())) {
		    if(SaarTest.logLevel <= 875) myPrint("singletree:  Sending PathToRootInfoMsg to newly added child " + hMsg.getSource(), 875);
		    saarClient.endpoint.route(null, new PathToRootInfoMsg(saarClient.endpoint.getLocalNodeHandle(), topic, manager.getPathToRoot()), hMsg.getSource());	  
		    
		}

		
		manager.setLastForwardHeartbeatTimeChild(hMsg.getSource(),getCurrentTimeMillis());
		
		
	    } else {
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("singletree: " + saarClient.endpoint.getId() + ": Received ChildIsAliveMsg from unknown child " + hMsg.getSource(), Logger.WARNING);
	    }

	}

	
	if(message instanceof UnsubscribeMsg) {
	    // We have the same unsubscribe msg for a parent as well as a child unsubscribing
	    UnsubscribeMsg uMsg = (UnsubscribeMsg) message;
	    if(SaarTest.logLevel <= 875) myPrint("singletree:  Received " + uMsg, 875);
	    if(uMsg.getSource().equals(manager.getParent())) {
		manager.setParent(null);
		// We will issue an anycast to locate a parent immediately

		// Our parent probably left the group, we anycast for a new parent 
		NodeHandle anycastHint = null;
		SaarContent reqContent = new SingletreeContent(SingletreeContent.ANYCASTNEIGHBOR, topicName, tNumber, manager.getPathToRoot(), manager.getTreeDepth(), maximumOutdegree);
		saarClient.reqAnycast(tNumber,reqContent, anycastHint, 2, 1, 10);

	    }
	    if(manager.containsChild(uMsg.getSource())) {
		manager.removeChild(uMsg.getSource());
	    } 
	}




	if(message instanceof PremptChildMsg) { // sent from parent to the child that he prempted
	    Topic myTopic = saartopic.baseTopic;
	    // We have the same unsubscribe msg for a parent as well as a child unsubscribing
	    PremptChildMsg premptMsg = (PremptChildMsg) message;
	    NodeHandle newProspectiveParent = premptMsg.newProspectiveParent; 
	    NodeHandle prevparent = manager.getParent();
	    if(SaarTest.logLevel <= 875) myPrint("singletree:  Received " + premptMsg, 875);
	    if(premptMsg.getSource().equals(prevparent)) {
		// We will send him an UnsubscribeMsg
		if(SaarTest.logLevel <= 875) myPrint("singletree:  Sending UnsubscribeMsg to prevparent " + prevparent, 875);
		saarClient.endpoint.route(null, new UnsubscribeMsg(saarClient.endpoint.getLocalNodeHandle(),topic), prevparent);
		
		manager.setParent(null);
		// We will send prospective parent a message to accept us
		if(SaarTest.logLevel <= 875) myPrint("singletree:  Sending AcceptPremptedChildMsg to " + newProspectiveParent, 875);
		saarClient.endpoint.route(null, new AcceptPremptedChildMsg(saarClient.endpoint.getLocalNodeHandle(), myTopic, maximumOutdegree), newProspectiveParent);	

	    }
	}


	if(message instanceof AcceptPremptedChildMsg) { // request msg sent from prempted child to the node that took its slot 
	    Topic myTopic = saartopic.baseTopic;
	    // We have the same unsubscribe msg for a parent as well as a child unsubscribing
	    AcceptPremptedChildMsg acceptPremptedChildMsg = (AcceptPremptedChildMsg) message;
	    NodeHandle premptedChild = acceptPremptedChildMsg.getSource();
	    boolean acceptRequest = true;
	    // We will check the child addition constraints once
	    

	    if(manager.numChildren() >= maximumOutdegree) { // If we do reservation then we must check agaist the reservation
		
		if(SaarTest.logLevel <= 875) myPrint("singletree: acceptPremptedChild(uStaticLimitReachedBasedOnTotalSlots, ret=false) ", 875);
		
		acceptRequest = false;
	    }
	
	    


	    if(saarClient.endpoint.getLocalNodeHandle().equals(premptedChild)) {
		// We do not accept the anycast made by ourself
		if(SaarTest.logLevel <= 875) myPrint("singletree: acceptPremptedChild(self=requestor, ret=false)", 875);
		acceptRequest = false;
		
	    }
	    
	    if(manager.containsChild(premptedChild)) {
		if(SaarTest.logLevel <= 875) myPrint("singletree: acceptPremptedChild(self=requestorIsExistingChild, ret=false) ", 875);
		acceptRequest = false;
		
	    }


	
	    // We check to see if we are getting good performance. We got rid of this on Jan15-2008 because of a race condition of the AcceptPremptedChildMsg arriving before the AnycastAck msg 
	    //isConnected = evaluateIsConnected();
	    //if(!isConnected) {
	    //if(SaarTest.logLevel <= 875) myPrint("singletree: acceptPremptedChild(poorInstantaneousQuality, ret=false) ", 875);
	    //acceptRequest = false;
	    //}
	    

	    
	    if(manager.hasLoops(premptedChild.getId())) {
		if(SaarTest.logLevel <= 875) myPrint("singletree: acceptPremptedChild(hasLoops, ret=false) ", 875);
		acceptRequest = false;
		
	    }

	    if(acceptRequest) {
		// We accept the anycast, add the new neighbor connection, and notify the anycast requestor
		
		if(SaarTest.logLevel <= 875) myPrint("singletree:  Accepting prempted child " + premptedChild, 875);
		manager.addChild(premptedChild, acceptPremptedChildMsg.premptedChildDegree);
		saarClient.endpoint.route(null, new PremptedChildWasAcceptedMsg(saarClient.endpoint.getLocalNodeHandle(), myTopic), premptedChild);	

	    } else {
		if(SaarTest.logLevel <= 875) myPrint("singletree:  Denying prempted child", 875);
		// We will send him a message deny acceptance
		saarClient.endpoint.route(null, new PremptedChildWasDeniedMsg(saarClient.endpoint.getLocalNodeHandle(), myTopic), premptedChild);	


	    }


	}


	if(message instanceof PremptedChildWasAcceptedMsg) { // msg from node that took the slot ->  the prempted child
	    Topic myTopic = saartopic.baseTopic;
	    // We have the same unsubscribe msg for a parent as well as a child unsubscribing
	    PremptedChildWasAcceptedMsg premptedChildWasAcceptedMsg = (PremptedChildWasAcceptedMsg) message;

	    if(SaarTest.logLevel <= 875) myPrint("singletree:  Received PremptedChildWasAcceptedMsg from " + premptedChildWasAcceptedMsg.getSource(), 875);
	    NodeHandle prevparent = manager.getParent();

	    if(prevparent != null) {
		if(SaarTest.logLevel <= 875) myPrint("singletree:  WARNING: Acquired parent via anycast when prempted BEFORE receiving PremptedChildWasAcceptedMsg", 875);

		// We will send this previous parent an unsubscribe msg
		if(SaarTest.logLevel <= 875) myPrint("singletree:  Sending UnsubscribeMsg to prevparent " + prevparent, 875);
		saarClient.endpoint.route(null, new UnsubscribeMsg(saarClient.endpoint.getLocalNodeHandle(),myTopic), prevparent);

	    }
	    manager.setParent(premptedChildWasAcceptedMsg.getSource()); // the way of accepting prempted child ensures that there is no primary stripe violation

	    NodeHandle myparent = manager.getParent();

	    if(SaarTest.logLevel <= 850) myPrint("singletree:  Sending ChildIsAliveMsg to parent " + myparent, 850);
	    saarClient.endpoint.route(null, new ChildIsAliveMsg(saarClient.endpoint.getLocalNodeHandle(), myTopic), myparent);	    	    
	    

	}


	if(message instanceof PremptedChildWasDeniedMsg) { // msg from node that took the slot ->  the prempted child
	    Topic myTopic = saartopic.baseTopic;
	    // We have the same unsubscribe msg for a parent as well as a child unsubscribing
	    PremptedChildWasDeniedMsg premptedChildWasDeniedMsg = (PremptedChildWasDeniedMsg) message;
	    if(SaarTest.logLevel <= 875) myPrint("singletree:  Received PremptedChildWasDeniedMsg from " + premptedChildWasDeniedMsg.getSource(), 875);

	}





	
	if(message instanceof PublishMsg) {
	    long currtime = getCurrentTimeMillis();
	    Topic myTopic = saartopic.baseTopic;
	    PublishMsg publishMsg = (PublishMsg) message;
	    if(SaarTest.logLevel <= 875) myPrint("singletree:  Received " + publishMsg, 875);
	    //if(publishMsg.getSource().equals(manager.getParent())) { // We comment this line since we are now using PRM and the block can come from either parent or a backpointer

	    Block recvBlock = publishMsg.getBlock();
	    int selfestimatedRWindow = firstPktSequence + (int)((currtime - firstPktTime)/PUBLISHPERIOD);




	    if(publishMsg.getSource().equals(manager.getParent()) || manager.containsBackpointer(publishMsg.getSource())) {

		if(publishMsg.getSource().equals(manager.getParent())) {
		    manager.setLastReverseHeartbeatTimeParent(getCurrentTimeMillis());
		}

		
		int depth = recvBlock.getDepth();
		recvBlock.addToPath(saarClient.bindIndex, saarClient.jvmIndex, saarClient.vIndex);
		String blockId = "Stripe:" + recvBlock.stripeId + "Seq:" + recvBlock.seqNum;
		blocks.put(blockId, recvBlock);
		int numPkts = 0; 
		if(numSequence.containsKey(blockId)) {
		    numPkts = ((Integer)numSequence.get(blockId)).intValue();
		}
		boolean firstinstanceofpacket = false; 
		if(numPkts == 0) {
		    firstinstanceofpacket = true;
		}
		numSequence.put(blockId, new Integer(numPkts + 1));
		singletreeDeliver(recvBlock.stripeId, recvBlock.seqNum, depth, publishMsg.getSource(), numPkts + 1, recvBlock); 

		if(firstinstanceofpacket) {
		    boolean gotblockfromprmneighbor = false;
		    if(!publishMsg.getSource().equals(manager.getParent())) {
			gotblockfromprmneighbor = true;
			if(SaarTest.logLevel <= 875) myPrint("singletree:  Got firstinstanceblock from prmneighbor", 875);
		    }
		    

		    lastParentHeartbeatSentTime = currtime;
		    // We will recursively push out the block to our children
		    NodeHandle handles[] = manager.getChildren();
		    for(int i=0; i < handles.length; i++) {
			if(shouldForwardConsideringLinkLoss()) {
			    numpktssent++;
			    if(SaarTest.logLevel <= 850) myPrint("singletree: Acquiredviaprm: " + gotblockfromprmneighbor + " , Publishing Block to child " + handles[i] , 850);
			    saarClient.endpoint.route(null, new PublishMsg(saarClient.endpoint.getLocalNodeHandle(), myTopic, recvBlock), handles[i]);	    
			} else {
			    if(SaarTest.logLevel <= 875) myPrint("singletree: Acquiredviaprm: " + gotblockfromprmneighbor + " , Not Publishing Block (because of link loss) to child " + handles[i] , 875);
			}
		    }
		    
		    // We will also forward to a random neighnor with probability 'PRMBETA; 
		    if(ENABLEPRM) {
			NodeHandle myprmneighbors[] = manager.getNeighbors();
			for(int i=0; i < myprmneighbors.length; i++) {
			    if(shouldForwardPRM(i)) {
				if(SaarTest.logLevel <= 875) myPrint("singletree: Acquiredviaprm: " + gotblockfromprmneighbor + " , Publishing Block to PRMNEIGHBOR " + myprmneighbors[i] + " Stripe: " + recvBlock.stripeId + " Seqnum: " + recvBlock.seqNum, 875);
				saarClient.endpoint.route(null, new PublishMsg(saarClient.endpoint.getLocalNodeHandle(), myTopic, recvBlock), myprmneighbors[i]);	    
			    }
			}
			
			
		    }

		}


		
	    } else {
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("singletree: " + saarClient.endpoint.getId() + ": Received PublishMsg from unknown parent/backpointer " + publishMsg.getSource(), Logger.WARNING);


	    }
	    
	}

	
	if(message instanceof PRMNeighborEstablishMessage) {
	    Topic myTopic = saartopic.baseTopic;
	    PRMNeighborEstablishMessage prmMsg = (PRMNeighborEstablishMessage) message;
	    if(SaarTest.logLevel <= 875) myPrint("singletree:  Received " + prmMsg, 875);
	    addBackpointer(prmMsg.getSource()); // This establishes the backpointer neighbor ( passively formed neighbor). Note that the active neighbors are my forward pointers, the ones that I acquire explicitly
	}



	if(message instanceof PRMUnsubscribeMessage) {
	    Topic myTopic = saartopic.baseTopic;
	    PRMUnsubscribeMessage prmMsg = (PRMUnsubscribeMessage) message;
	    if(SaarTest.logLevel <= 875) myPrint("singletree:  Received " + prmMsg, 875);
	    if(prmMsg.toRemoveForwardpointer) {
		removeNeighbor(prmMsg.getSource());
	    } else if(prmMsg.toRemoveBackpointer) {
		removeBackpointer(prmMsg.getSource());
	    } else {
		// Should not happen
	    }
	}


	if(message instanceof PRMBackpointerNotifyMessage) {
	    Topic myTopic = saartopic.baseTopic;
	    PRMBackpointerNotifyMessage prmMsg = (PRMBackpointerNotifyMessage) message;
	    if(SaarTest.logLevel <= 875) myPrint("singletree:  Received " + prmMsg, 875);
	    
	    
	    manager.updateNeighborInfo(prmMsg.getSource(), prmMsg.pathToRoot); // This establishes the backpointer neighbor ( passively formed neighbor). Note that the active neighbors are my forward pointers, the ones that I acquire explicitly
	}



    }


    
    public boolean shouldForwardPRM(int rngIndex) {
	int valchosen =  prmRng[rngIndex].nextInt(100); 
	if(valchosen < (100*PRMBETA)) {
	    return true;
	} else {
	    return false;
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
	int prevMaximumOutdegree = maximumOutdegree;
	dataplaneriusedbycontrol = saarClient.getDataplaneRIUsedByControl() ;

	NodeHandle[] mystaticreservation = manager.getChildren();
	int mynumchildren = mystaticreservation.length;
	// This is the overhead that comes from the headers in the data pkts. We found this to be significant when the node has many children and is publishing at a finer rate (i.e many pkts)
  double pktheaderri = 1.0; 
  if (firstTimeRN && saarClient.logger.level <= Logger.WARNING) {
    saarClient.logger.log("recomputeNodedegree() is not implemented");
    firstTimeRN = false;
  }
	//pktheaderri = ((double)(mynumchildren * NUMFRAGMENTSPERSECPERCHILD * rice.pastry.direct.DirectPastryNode.SIZEMSGHEADER)) / ((double)SaarClient.STREAMBANDWIDTHINBYTES); 
	

	nodedegree = nodedegreeControlAndData - dataplaneriusedbycontrol - pktheaderri;
	if(nodedegree < 0) {
	    nodedegree = 0;
	}
	double spareri = nodedegree - prevMaximumOutdegree; // Note - This is not the spare based on actual number of children, instead it is spare based on the maximumOutdegree it has set 
	if(spareri > SPARERITOTAKECHILD) {  // Update from 1.05 to 1.1 on Jan03-3008 on observing increased queue delays in tree-based systems
	    maximumOutdegree = maximumOutdegree + 1; 
	}else if(spareri < SPARERITODROPCHILD) { // Updated from 0.01 to 0.05 on Jan03-2008 
	    maximumOutdegree = maximumOutdegree - 1;
	}
	

	if(SaarTest.logLevel <= 877) myPrint("singletree:  recomputeNodedegree() " + " nodedegreeControlAndData: " + nodedegreeControlAndData +  " dataplaneriusedbycontrol: " + dataplaneriusedbycontrol +  " pktheaderri: " + pktheaderri + " nodedegree: " + nodedegree +  " maximumOutdegree: " + maximumOutdegree + " spareri: " + spareri, 877);
	if(maximumOutdegree != prevMaximumOutdegree) {
	    if(SaarTest.logLevel <= 880) myPrint("singletree:  Resetting maximumOutdegree: " + maximumOutdegree + " prevval: " + prevMaximumOutdegree + " nodedegree: " + nodedegree + " dataplaneriusedbycontrol: " + dataplaneriusedbycontrol + " pktheaderri: " + pktheaderri, 880);
	}
    }
 

    // todo: remove this when it's implemented
    boolean firstTimeNM = true;
    public void neighborMaintenance() {

	// In the HybridDEBUG setting the trees will not be used, so why establish them
	if((!amMulticastSource) && SaarClient.HYBRIDDEBUG) {
	    return;
	}
	Topic topic = saartopic.baseTopic;

	long currtime = getCurrentTimeMillis();
	
	// This interval is 1 sec and independent of the publish period
	if((currtime - lastNeighborMaintenanceInvokationTime) < 250) {   // Update to 250 from 1000 on Oct02-2007 since we are using abrupt-leaves
	    if(SaarTest.logLevel <= 850) myPrint("singletree:  Skipping neighborMaintenance()", 850);
	    return;
	}
	lastNeighborMaintenanceInvokationTime = currtime;


	if(SaarTest.logLevel <= 875) myPrint("singletree: neighborMaintenance()", 875);
	recomputeNodedegree();

	NodeHandle[] mystaticreservation = manager.getChildren();
	int mynumchildren = mystaticreservation.length;
	
	long currqueuedelay = saarClient.computeQueueDelay();
	

	// This is the overhead that comes from the headers in the data pkts. We found this to be significant when the node has many children and is publishing at a finer rate (i.e many pkts)
  double pktheaderri = 1.0; 
  if (firstTimeNM && saarClient.logger.level <= Logger.WARNING) {
    saarClient.logger.log("neighborMaintenance() is not implemented");
    firstTimeNM = false;
  }
  //pktheaderri = ((double)(mynumchildren * NUMFRAGMENTSPERSECPERCHILD * rice.pastry.direct.DirectPastryNode.SIZEMSGHEADER)) / ((double)SaarClient.STREAMBANDWIDTHINBYTES); 

	double foregroundreservedri = mystaticreservation.length + pktheaderri; 
	// We will report the foreground reservation to the channelviewer
	saarClient.viewer.setForegroundReservedRI(foregroundreservedri);


	if(SaarTest.logLevel <= 880) myPrint("singletree: BWUtilization: Nodedegree: " + nodedegree +  " dataplaneriusedbycontrol: " + dataplaneriusedbycontrol + " pktheaderri: " + pktheaderri + "  numchildren: " + mystaticreservation.length + " foregroundreservedri: " + foregroundreservedri + " published: " + numpktssent + " currqueuedelay: " + currqueuedelay , 880);	
	numpktssent = 0;

	if(ENABLEPRM) {

	
	    // STEP   - list my PRM backpointers
	    NodeHandle[] mybackpointers = manager.getBackpointers();
	    for(int i=0; i< mybackpointers.length; i++) {
		NodeHandle nh = mybackpointers[i];
		if(SaarTest.logLevel <= 850) myPrint("singletree: prmBackpointers( " + nh  + ")", 850);
		
	    }
	    
	    // We will notify our backpointers of our current path to root
	    if(!((currtime - lastBackpointerNotifyTime) < 5000)) {
		lastBackpointerNotifyTime = currtime;
		for(int i=0; i< mybackpointers.length; i++) {
		    NodeHandle nh = mybackpointers[i];
		    if(SaarTest.logLevel <= 875) myPrint("singletree: Sending PRMBackpointerNotifyMessage to " + nh, 875);
		    saarClient.endpoint.route(null, new PRMBackpointerNotifyMessage(saarClient.endpoint.getLocalNodeHandle(), topic, manager.getPathToRoot()), nh);
		}
		
		

	    } 
	    
	

	    
	    // To implement PRM, we need to maintain k random neighbors
	    NodeHandle[] myneighbors = manager.getNeighbors();
	    Vector currNeighbors = new Vector();
	    // STEP 1 - Remove actively  neighbors acquired before a time threshold
	    Id[] mypathToRoot = manager.getPathToRoot();	
	    //String mypath = getPathToRootAsString(mypathToRoot);
	    String mypath = manager.getPathToRootAsString();

	    if(SaarTest.logLevel <= 880) myPrint("singletree: NUMPRMNEIGHBORS: " + myneighbors.length, 880);
	    for(int i=0; i< myneighbors.length; i++) {
		NodeHandle nh = myneighbors[i];
		long acquiredtime = manager.getAcquiredTimeNeighbor(nh);
		Id[] nhPathToRoot = manager.getPathToRootNeighbor(nh);
		String nhpath = getPathAsString(nhPathToRoot);
		int hca = computehca(mypathToRoot, nhPathToRoot);
		
		if(SaarTest.logLevel <= 850) myPrint("singletree: prmNeighborMaintenance( " + nh + ", " + (currtime - acquiredtime) + ")", 850);
		if(SaarTest.logLevel <= 850) myPrint("singletree: prmnhpath( " + nh + ", mypath:" + mypath + ", nhpath:" + nhpath + ", hca:" + hca + ")", 850);
		
		if((currtime - acquiredtime) > ACQUIREDTHRESHOLD) {
		    removeNeighbor(nh);
		    // We will send a PRMUnsubscribe message
		    if(SaarTest.logLevel <= 875) myPrint("singletree: " + saarClient.endpoint.getId() + ": Sending PRMUnsubscribeMessage to neighbor(forward pointer) " + nh, 875);
		    boolean toRemoveForwardpointer = false;
		    boolean toRemoveBackpointer = true;
		    saarClient.endpoint.route(null, new PRMUnsubscribeMessage(saarClient.endpoint.getLocalNodeHandle(),topic, toRemoveForwardpointer, toRemoveBackpointer), nh);
		    
		    
		    
		    if(SaarTest.logLevel <= 875) myPrint("singletree: pmNeighborMaintenance: Removing active old neighbor, neighbor " + nh, 875);		    
		    
		} else {
		    currNeighbors.add(nh);
		}
		
	    }		
	    
	    // STEP 2 - Issue anycast if (#currNeighbors < NUMPRMNEIGHBORS)
	    if(currNeighbors.size() < NUMPRMNEIGHBORS) {
		if(SaarTest.logLevel <= 875) myPrint("singletree: Establishing neighbor because #neighbors < NUMPRMNEIGHBORS", 875);
		//SaarContent reqContent = new SingletreeContent(SingletreeContent.ANYCASTFORPRMNEIGHBOR, topicName,tNumber); // this should contain the currSeq, this was the previous naive prm random neighbor selection without the loss correlation extension
		
		SaarContent reqContent = new SingletreeContent(SingletreeContent.ANYCASTFORPRMNEIGHBOR, topicName, tNumber, manager.getPathToRoot(), manager.getTreeDepth(), maximumOutdegree);
		
		NodeHandle anycastHint = getRandomLeafsetMember(); // We start the anycast from random points in the spanning tree. We do this to enable more random mesh construction
		saarClient.reqAnycast(tNumber, reqContent, anycastHint, 2, 1, 10) ;
	    
	    }

	}
	



	// Step 1: We will first remove dead parent and dead children (i.e children from whom I have not received periodic heartbeats)


	long difftime;
	difftime = currtime - manager.getLastReverseHeartbeatTimeParent();
	// NOTE: setParent(null) if called on the multicastsource will not add the srcheader in the pathToRoot, so its important that we do this only for non source nodes
	if(!amMulticastSource) {
	    if(difftime >= SaarClient.NEIGHBORDEADTHRESHOLD) {  
		// We will set our parent to null
		manager.setParent(null);
	    }
	}
	if(SaarTest.logLevel <= 875) myPrint("singletree: dataplaneparent (" + manager.getParent() + "," + difftime + ", lastReverseHeartbeat:" + manager.getLastReverseHeartbeatTimeParent() + ")", 875);	


	

	NodeHandle[] mychildren = manager.getChildren();
	for(int i=0; i<mychildren.length; i++) {
	    difftime = currtime - manager.getLastForwardHeartbeatTimeChild(mychildren[i]);
	    if(SaarTest.logLevel <= 850) myPrint("singletree: dataplanechild[" + i + "] (" + mychildren[i] + "," + difftime + ")", 850);
	    // Child Removal Policy:
	    //     a) Until the child has acknowledge the connection by sending a ChildIsAliveMsg to the anycastresponder, the child reservation is held only for 'CHILDRESERVATIONTIME' 
	    //     b) If the child acknowledge the connection, then the difftime is 4*CHILDHEARTBEATPERIOD
	    if( ((!manager.gotChildAck(mychildren[i])) && (difftime > CHILDRESERVATIONTIME)) || (difftime >= SaarClient.NEIGHBORDEADTHRESHOLD)) {
		if(SaarTest.logLevel <= 875) myPrint("singletree: WARNING: Removing child: " + mychildren[i] + " from neighborMaintenance(), difftime: " + difftime, 875);
		manager.removeChild(mychildren[i]);

		// WARNING: Send an unsubscribe message explicitly

	    }
	}



	// If total children exceeds the maximumOutdegree drop children
	mychildren = manager.getChildren();
	while(mychildren.length > maximumOutdegree) {
	    if(SaarTest.logLevel <= 880) myPrint("singletree: WARNING: MaximumOutdegree violated , Removing child: " + mychildren[0], 880);
	    manager.removeChild(mychildren[0]);
	    mychildren = manager.getChildren();
	}



	if(SaarTest.logLevel <= 875) myPrint("singletree: premptdegreestatus: grpDepthAtNodeHavingChildOfDegree0: " + grpDepthAtNodeHavingChildOfDegree[0] + " grpDepthAtNodeHavingChildOfDegree1: " + grpDepthAtNodeHavingChildOfDegree[1] , 875);


	if(!amMulticastSource) {
	    // Step 2: We will now establish a parent via 'anycast' if required

	    


	    
	    isConnected = evaluateIsConnected();
	    if((manager.getParent()!= null) && isConnected && manager.hasValidPathToRoot()) {
		// Since we have a parent and are getting packets dont worry, we will however invoke an anycast to improve the tree quality (e.g tree depth)
		
		if(SaarTest.logLevel <= 850) myPrint("singletree: parent is not null, isconnected = true, hasvalidpath", 850);

		if(ENABLETREEIMPROVEMENT && ((currtime - lastTreeImprovementTime) > TREEIMPROVEMENTPERIOD)) {
		    if(SaarTest.logLevel <= 850) myPrint("singletree: parent is notnull, isconnected = true, hasvalidpath, do tree improvement", 850);
		    lastTreeImprovementTime = currtime;

		    // We will issue the tree improvment anycast request only if we know the group has available slots at levels lower than the local node's treeDepth
		    treeDepth = manager.getTreeDepth();
		    int sumAvailableSlotsAtLowerDepth = 0;
		    int probeDepth = -1;
		    if((treeDepth-1) >= SingletreeContent.MAXSATURATEDDEPTH) {
			probeDepth = SingletreeContent.MAXSATURATEDDEPTH;
		    } else {
			probeDepth = treeDepth - 1;
		    }
		    for(int i=0; i< probeDepth; i++) {
			sumAvailableSlotsAtLowerDepth = sumAvailableSlotsAtLowerDepth + grpAvailableSlotsAtDepth[i];
		    }
		    if(sumAvailableSlotsAtLowerDepth > 0) {
			NodeHandle anycastHint = getRandomLeafsetMember();
			SaarContent reqContent = new SingletreeContent(SingletreeContent.ANYCASTFORTREEIMPROVEMENT, topicName, tNumber, manager.getPathToRoot(), manager.getTreeDepth(), maximumOutdegree);
			if(SaarTest.logLevel <= 875) myPrint("singletree: Issuing anycast for tree IMPROVEMENT, currDepth= " + manager.getTreeDepth(), 875);
			saarClient.reqAnycast(tNumber,reqContent, anycastHint, 2, 25, 10);
		    } else {
			if(SaarTest.logLevel <= 875) myPrint("singletree: Damping anycast for tree IMPROVEMENT because of saturation at lower depths", 875);
		    }
		}




		
		// Currently we enable only nodes with degree > 1 to prempt nodes of degree 0 or 1. 
		//if(ENABLE_PREMPT_DEGREE_PUSHDOWN && (maximumOutdegree > 1) && (((grpDepthAtNodeHavingChildOfDegree[0] + 1) < manager.getTreeDepth()) || ((grpDepthAtNodeHavingChildOfDegree[1] + 1) < manager.getTreeDepth())) && (manager.numChildren() < maximumOutdegree) && ((currtime - lastPremptDegreePushdownTime) > PREMPTDEGREEPUSHDOWNPERIOD)) { // For now we preempt only zero degree nodes, Replaced with allowing nodes of degree = 1 to prempt nodes of degree = 0 also
		if(ENABLE_PREMPT_DEGREE_PUSHDOWN     &&    (((maximumOutdegree > 1) && (((grpDepthAtNodeHavingChildOfDegree[0] + 1) < manager.getTreeDepth()) || ((grpDepthAtNodeHavingChildOfDegree[1] + 1) < manager.getTreeDepth())))  ||   ((maximumOutdegree == 1) && ((grpDepthAtNodeHavingChildOfDegree[0] + 1) < manager.getTreeDepth())))   &&    (manager.numChildren() < maximumOutdegree)   &&   ((currtime - lastPremptDegreePushdownTime) > PREMPTDEGREEPUSHDOWNPERIOD)  ) { // Updated on Jan15-2008 to allow enable nodes of maximumOutdegree = 1 to prempt nodes of degree 0

		    if(SaarTest.logLevel <= 850) myPrint("singletree: parent is notnull, isconnected = true, do premption", 850);

		    lastPremptDegreePushdownTime = currtime;
		    
		    NodeHandle anycastHint = null;
		    SaarContent reqContent = new SingletreeContent(SingletreeContent.ANYCASTFORPREMPTDEGREEPUSHDOWN, topicName, tNumber, manager.getPathToRoot(), manager.getTreeDepth(), maximumOutdegree);
		    if(SaarTest.logLevel <= 875) myPrint("singletree: Issuing anycast for PREMPTDEGREEPUSHDOWN, currDepth= " + manager.getTreeDepth() + " maximumoutdegree: " + maximumOutdegree, 875);
		    saarClient.reqAnycast(tNumber,reqContent, anycastHint, 2, 25, 10);
		    
		    
		} else {

		    if(SaarTest.logLevel <= 875) myPrint("singletree: Damping anycast for PREMPTDEGREEPUSHDOWN, currDepth= " + manager.getTreeDepth() + " maximumoutdegree: " + maximumOutdegree + " numChildren: " + manager.numChildren() + " grpDepthAtNodeHavingChildOfDegree0: " + grpDepthAtNodeHavingChildOfDegree[0] + " grpDepthAtNodeHavingChildOfDegree1: " + grpDepthAtNodeHavingChildOfDegree[1] + " elapsedtime: " + (currtime - lastPremptDegreePushdownTime) , 875);		    


		}

		return;

	    } else if((manager.getParent() != null) && (difftime < SaarClient.NEIGHBORDEADTHRESHOLD)) {
		//} else if(manager.getParent() != null) { // We removed the condition of having received a heartbeat in PARENTHEARTBEATPERIOD because heartbeats are now sent only when the pathtoroot changes, We reverted back again to rely on heartbeats for abrupt-leave (Updated on Oct02-200) 
		
		// We do have an intact parent, we are not getting packets because the tree disconnect is higher up. Since only subtree roots repair the tree, we dont do anything here
		return;
	    } else {
		// If we do not have a parent we might also consider issuing the prempt degree pushdown if it respects the period or else the normal anycast.
		

		if(SaarTest.logLevel <= 850) myPrint("singletree: parent is perhaps null", 850);


		boolean clause1 = ENABLE_PREMPT_DEGREE_PUSHDOWN ;
		boolean clause2 =  (((maximumOutdegree > 1) && (((grpDepthAtNodeHavingChildOfDegree[0] + 1) < manager.getTreeDepth()) || ((grpDepthAtNodeHavingChildOfDegree[1] + 1) < manager.getTreeDepth())))  ||   ((maximumOutdegree == 1) && ((grpDepthAtNodeHavingChildOfDegree[0] + 1) < manager.getTreeDepth())));
		boolean clause3 =  (manager.numChildren() < maximumOutdegree) ;
		boolean clause4 = ((currtime - lastPremptDegreePushdownTime) > PREMPTDEGREEPUSHDOWNPERIOD);
		boolean clause2a = ((maximumOutdegree > 1) && (((grpDepthAtNodeHavingChildOfDegree[0] + 1) < manager.getTreeDepth()) || ((grpDepthAtNodeHavingChildOfDegree[1] + 1) < manager.getTreeDepth()))) ;
		boolean clause2b =  ((maximumOutdegree == 1) && ((grpDepthAtNodeHavingChildOfDegree[0] + 1) < manager.getTreeDepth()));
							       
		
		if(SaarTest.logLevel <= 850) myPrint("singletree: clause1:" + clause1 + " clause2:" + clause2 + " clause3:" + clause3 + " clause4:" + clause4 + " manager.getTreeDepth():" + manager.getTreeDepth() + " maximumOutdegree:" + maximumOutdegree +  " clause2a:" + clause2a + " clause2b:" + clause2b + " lastPremptTime:" + lastPremptDegreePushdownTime, 850);

		// Previously we enable only nodes with degree > 1 to prempt nodes of degree 0 or 1. 		//if(ENABLE_PREMPT_DEGREE_PUSHDOWN && (maximumOutdegree > 1) && (((grpDepthAtNodeHavingChildOfDegree[0] + 1) < manager.getTreeDepth()) || ((grpDepthAtNodeHavingChildOfDegree[1] + 1) < manager.getTreeDepth())) && (manager.numChildren() < maximumOutdegree) && ((currtime - lastPremptDegreePushdownTime) > PREMPTDEGREEPUSHDOWNPERIOD)) { // For now we preempt only zero degree nodes
		if(ENABLE_PREMPT_DEGREE_PUSHDOWN     &&    (((maximumOutdegree > 1) && (((grpDepthAtNodeHavingChildOfDegree[0] + 1) < manager.getTreeDepth()) || ((grpDepthAtNodeHavingChildOfDegree[1] + 1) < manager.getTreeDepth())))  ||   ((maximumOutdegree == 1) && ((grpDepthAtNodeHavingChildOfDegree[0] + 1) < manager.getTreeDepth())))   &&    (manager.numChildren() < maximumOutdegree)   &&   ((currtime - lastPremptDegreePushdownTime) > PREMPTDEGREEPUSHDOWNPERIOD)  ) {  // Updated on Jan15-2008 to allow enable nodes of maximumOutdegree = 1 to prempt nodes of degree 0
		    
		    lastPremptDegreePushdownTime = currtime;
		    
		    NodeHandle anycastHint = null;
		    SaarContent reqContent = new SingletreeContent(SingletreeContent.ANYCASTFORPREMPTDEGREEPUSHDOWN, topicName, tNumber, manager.getPathToRoot(), manager.getTreeDepth(), maximumOutdegree);
		    if(SaarTest.logLevel <= 875) myPrint("singletree: Issuing anycast for PREMPTDEGREEPUSHDOWN, currDepth= " + manager.getTreeDepth() + " maximumoutdegree: " + maximumOutdegree, 875);
		    saarClient.reqAnycast(tNumber,reqContent, anycastHint, 2, 25, 10);
		    
		    
		} else {


		    if(SaarTest.logLevel <= 850) myPrint("singletree: parent is perhaps null, else  before anycasttimeout check", 850);

		    // Our parent probably left the group, we anycast for a new parent 
		    NodeHandle anycastHint = null;
		    SaarContent reqContent = new SingletreeContent(SingletreeContent.ANYCASTNEIGHBOR, topicName, tNumber, manager.getPathToRoot(), manager.getTreeDepth(), maximumOutdegree);
		    
		    if((currtime - lastAnycastForRepairTime) > ANYCASTTIMEOUT) {
			
			//if(grpMinAvailableDepth < MAXADVERTISEDTREEDEPTH) {
			//  expbackoffInterval = 500;
			//  maxExpbackoffInterval = 500;
			//  lastAnycastForRepairTime = currtime;
			//  if(SaarTest.logLevel <= 875) myPrint("singletree: Issuing baseline anycast for ANYCASTNEIGHBOR, currDepth= " + manager.getTreeDepth() + " maximumoutdegree: " + maximumOutdegree + " grpMAD:" + grpMinAvailableDepth, 875);
			//  saarClient.reqAnycast(tNumber,reqContent, anycastHint, 2, 1, 10);
			
			if(grpMinAvailableDepth >= MAXADVERTISEDTREEDEPTH) {
			    // We dampen the request since there are no resources
			    if(SaarTest.logLevel <= 875) myPrint("singletree: Damping ANYCASTNEIGHBOR because there are no group resources, grpMAD:" + grpMinAvailableDepth, 875);

			} else {
			    // We do exponential backoff 
			    if((currtime - lastAnycastForRepairTime) > expbackoffInterval) {
				maxExpbackoffInterval = maxExpbackoffInterval * 2;
				expbackoffInterval = rng.nextInt((int)maxExpbackoffInterval);
				lastAnycastForRepairTime = currtime;
				 if(SaarTest.logLevel <= 875) myPrint("singletree: Issuing ANYCASTNEIGHBOR  EXPBACKOFF(grpMAD:" + grpMinAvailableDepth + ", nextmax:" + maxExpbackoffInterval + ", nextchosen: " + expbackoffInterval + ") , currDepth= " + manager.getTreeDepth() + " maximumoutdegree: " + maximumOutdegree, 875);
				 saarClient.reqAnycast(tNumber,reqContent, anycastHint, 2, 1, 10);

			    } else {
				if(SaarTest.logLevel <= 875) myPrint("singletree: Dampening ANYCASTNEIGHBOR EXPBACKOFF(grpMAD:" + grpMinAvailableDepth + ", max:" + maxExpbackoffInterval + ", chosen: " + expbackoffInterval + ", expired: " + (currtime - lastAnycastForRepairTime) + ")" , 875);
			    }
			    
			    
			}
		    } 
		    
		    
		}
		
	    }



	}

    



    }
    
    // this is only for active neighbors. The reverse is called backpointers
    protected void addNeighbor(NodeHandle neighbor) {
	Topic topic = saartopic.baseTopic;
	if(SaarTest.logLevel <= 880) myPrint("singletree: addNeighbor() " + neighbor + " to topic " + topic, 880);
       
	manager.addNeighbor(neighbor);


       
	// We will send a PRMNeighborEstablishMessage to the neighbor. The prupose of this message is that when the neighbor departs from the group, the local node will be notified and can acquire a new neighbor
	if(SaarTest.logLevel <= 875) myPrint("singletree: " + saarClient.endpoint.getId() + ": Sending PRMNeighborEstablishMessage to neighbor " + neighbor, 875);
	saarClient.endpoint.route(null, new PRMNeighborEstablishMessage(saarClient.endpoint.getLocalNodeHandle(),topic), neighbor);
    

    }

    
    protected void removeNeighbor(NodeHandle neighbor) {
	Topic topic = saartopic.baseTopic;
	if(SaarTest.logLevel <= 880) myPrint("singletree: removeNeighbor() " + neighbor + " from topic " + topic, 880);
	manager.removeNeighbor(neighbor);

	
    }



    protected void addBackpointer(NodeHandle neighbor) {
	Topic topic = saartopic.baseTopic;
	if(SaarTest.logLevel <= 880) myPrint("singletree: addBackpointer() " + neighbor + " to topic " + topic, 880);
       
	manager.addBackpointer(neighbor);


    }


    protected void removeBackpointer(NodeHandle neighbor) {
	Topic topic = saartopic.baseTopic;
	if(SaarTest.logLevel <= 880) myPrint("singletree: removeBackpointer() " + neighbor + " to topic " + topic, 880);
       
	manager.removeBackpointer(neighbor);


    }




    // Here it advertises its pathToRoot info to its children, Additonally this heartbeat also serves the purpose of enabling only parentless nodes to issue repairs. Thus for a disconnected subtree other than the root, all other nodes will be receiving the heartbeats and thus will not issue anycasts for repair
    public void sendHeartbeats() {
	long currtime = getCurrentTimeMillis();

  
	boolean requiresPathToRootPush = manager.requiresPathToRootPush();
	if(requiresPathToRootPush || ((currtime - lastParentHeartbeatSentTime) >= (SaarClient.NEIGHBORHEARTBEATPERIOD + 500))) {  // +500 since the publishperiod in simulations could be requal to the heartbeatperiod = 1000

	    Topic myTopic = saartopic.baseTopic;	
	    NodeHandle handles[] = manager.getChildren();	    
	    if(SaarTest.logLevel <= 880) myPrint("singletree:  Pushing CURRPATHTOROOT: " + manager.getPathToRootAsString() + " to " + handles.length + " children because lastheartbeatsenttime: " + lastParentHeartbeatSentTime + " OR requiresPathToRootPush: " + requiresPathToRootPush , 880);
	    manager.setLastPushedPathToRoot(manager.getPathToRoot());
	    lastParentHeartbeatSentTime = currtime;



	    for(int i=0; i < handles.length; i++) {
		if(SaarTest.logLevel <= 875) myPrint("singletree:  Sending PathToRootInfoMsg to child " + handles[i], 875);
		saarClient.endpoint.route(null, new PathToRootInfoMsg(saarClient.endpoint.getLocalNodeHandle(), myTopic, manager.getPathToRoot()), handles[i]);	    
		
	    }
	} else {
	    //if(SaarTest.logLevel <= 875) myPrint("singletree:  DAMPING PATHTOROOT,  CURRPATHTOROOT: " + manager.getPathToRootAsString() +  " LASTPUSHED: " + manager.getLastPushedPathToRootAsString(), 875);		
	    if(SaarTest.logLevel <= 875) myPrint("singletree:  DAMPING redundant PATHTOROOT msg", 875);

	}
	
	

	
	if((currtime - lastChildHeartbeatSentTime) >= SaarClient.NEIGHBORHEARTBEATPERIOD) {
	    lastChildHeartbeatSentTime = currtime;
	    Topic myTopic = saartopic.baseTopic;	
	    NodeHandle myparent = manager.getParent();
	    if(myparent!= null) {
		if(SaarTest.logLevel <= 875) myPrint("singletree:  Sending ChildIsAliveMsg to parent " + myparent, 875);
		saarClient.endpoint.route(null, new ChildIsAliveMsg(saarClient.endpoint.getLocalNodeHandle(), myTopic), myparent);	    
		
	    }
	}
	

    }







  // Returns 1 if we actually did multicast (i.e if we were root)
    public void sendMulticastTopic() {
	Topic myTopic = saartopic.baseTopic;
	long currTime = getCurrentTimeMillis();
	int currSeq = saarClient.allTopics[tNumber].pSeqNum;
	String myTopicName = topicName;

	int seqNumDue = (int) ((currTime - firstBroadcastTime)/PUBLISHPERIOD); // PUBLISHPERIOD is related to the rate at which the sequence numbers are being incremented
	//System.out.println("currSeq: " + currSeq +  ", seqNumDue: " + seqNumDue);
	if((currSeq ==0) || (currSeq <= seqNumDue)) {	
	    if(SaarTest.logLevel <= 880) myPrint("singletree: SysTime: " + getCurrentTimeMillis() + " Node "+saarClient.endpoint.getLocalNodeHandle()+" BROADCASTING for Topic[ "+ myTopicName + " ] " + myTopic + " Seq= " + currSeq, 880);
	    
	    if(firstBroadcastTime == 0) {
		firstBroadcastTime = currTime;
	    }
	
	    saarClient.allTopics[tNumber].setPSeqNum(currSeq + 1);
	    saarClient.allTopics[tNumber].setPTime(getCurrentTimeMillis());

	    //rice.p2p.saar.simulation.SaarSimTest.simulatorbroadcastseqnum = currSeq;
	    saarClient.viewer.setForegroundBroadcastSeqnum(currSeq);

	    // We deliver the packet locally
	    int seqNum = currSeq;
	    int numPkts = 0;
	    for(int stripeId=0; stripeId < NUMSTRIPES; stripeId++) {
		String blockId = "Stripe:" + stripeId + "Seq:" + seqNum;
		if(numSequence.containsKey(blockId)) {
		    numPkts = ((Integer)numSequence.get(blockId)).intValue();
		} 
		numSequence.put(blockId, new Integer(numPkts + 1));
		Block block = new Block(stripeId, seqNum);
		block.addToPath(saarClient.bindIndex, saarClient.jvmIndex, saarClient.vIndex);
		blocks.put(blockId, block);
		Id[] srcHeader = new Id[1];
		srcHeader[0] = rice.pastry.Id.build();
		manager.appendPathToRoot(srcHeader); // the local node will be appended in the pathToRoot
		singletreeDeliver(block.stripeId, block.seqNum, block.getDepth(), saarClient.endpoint.getLocalNodeHandle(), numPkts +1, block);
	    }
	}

	if(!TIMEDOMAINCODING) {
	    if(!SaarClient.HYBRIDDEBUG) {
		if(NUMSTRIPES != 1) {
		    System.out.println("TIMEDOMAINCODING=false but NUMSTRIPES neq 1");
		    System.exit(1);
		}
		String blockId = "Stripe:" + "0" + "Seq:" + currSeq;
		Block toPushBlock = (Block)blocks.get(blockId);
		if(toPushBlock!= null) {
		    NodeHandle handles[] = manager.getChildren();
		    if(SaarTest.logLevel <= 875) myPrint("singletree:  PUSHING block " + blockId + " to " + handles.length + " children", 875);
		    lastParentHeartbeatSentTime = currTime;

		    for(int i=0; i < handles.length; i++) {
			numpktssent ++;		
			if(SaarTest.logLevel <= 850) myPrint(" Publishing Block to child " + handles[i], 850);
			saarClient.endpoint.route(null, new PublishMsg(saarClient.endpoint.getLocalNodeHandle(), myTopic, toPushBlock), handles[i]);	    
			
		    }
		}
	    }

	} else {
	    // The pushing of blocks is now decoupled from the generation of blocks, we do this in order to be able to do the redundant encoding of packets in the time domain.
	    
	    long FINEPUBLISHPERIOD = PUBLISHPERIOD/NUMSTRIPES; // This is because you are trying to publish at a finer granularity of pushing out one (stripeId,seqNum) per publishopportunity 
	    int publishOpportunityDue = (int) ((currTime - firstBroadcastTime)/FINEPUBLISHPERIOD);
	    int currPublishOpportunityCount = saarClient.allTopics[tNumber].publishOpportunityCount;
	    
	    if((currPublishOpportunityCount ==0) || (currPublishOpportunityCount <= publishOpportunityDue)) {	
		
		saarClient.allTopics[tNumber].setPublishOpportunityCount(currPublishOpportunityCount + 1);
		// We will now determine the blocks (stripeId,seqNum) based on the currPublishOpportunityCount
		// STRIPEINTERVALPERIOD is slightly higher than tree repair time, essentially it ensures that the two stripes of a seqnum are not correlated, say its 2000
		int cyclingSeqnumPeriod = (int) ((STRIPEINTERVALPERIOD/PUBLISHPERIOD)*NUMSTRIPES);
		int bSeqnum =  cyclingSeqnumPeriod * ((int) (currPublishOpportunityCount / (NUMSTRIPES*cyclingSeqnumPeriod))) + (currPublishOpportunityCount % cyclingSeqnumPeriod) - cyclingSeqnumPeriod;
		int bStripeId = ((int)(currPublishOpportunityCount / cyclingSeqnumPeriod)) % NUMSTRIPES; 
		String blockId = "Stripe:" + bStripeId + "Seq:" + bSeqnum;
		Block toPushBlock = (Block)blocks.get(blockId);
		if(toPushBlock != null){
		    if(SaarTest.logLevel <= 875) myPrint("singletree:  PUSHING block " + blockId + " to children", 875);
		    lastParentHeartbeatSentTime = currTime;
		    NodeHandle handles[] = manager.getChildren();
		    for(int i=0; i < handles.length; i++) {
			numpktssent ++;
			if(SaarTest.logLevel <= 850) myPrint("singletree:  Publishing Block to child " + handles[i], 850);
			saarClient.endpoint.route(null, new PublishMsg(saarClient.endpoint.getLocalNodeHandle(), myTopic, toPushBlock), handles[i]);	    
			
		    }
		} else {
		    if(SaarTest.logLevel <= 875) myPrint("singletree:  The desired block to PUSH " + blockId + " , has not been generated yet ", 875);
		    
		}
	    }
	}
	
	return;
	
    }
    

// This is the BMAP which is being computed based on the last WINDOWSIZE window to the left of the last pkt I received. This BMAP will be advertised to my neighbors
    public TemporalBufferMap computeSendBMAP() {

	// We will print the size of the hashtable to identfy memory leaks
	if(SaarTest.logLevel <= 875) myPrint("singletree: Hashtables: " + " numSequence: " + numSequence.size() + ", blocks: " + blocks.size(), 875);


	TemporalBufferMap bmap = null; 
	long currtime = getCurrentTimeMillis();
	if(rootSeqNumRecv !=-1) {
	    // int selfestimatedRWindow = firstPktSequence + (int)((currtime - firstPktTime)/PUBLISHPERIOD); Updated on Dec23-2007 on detecting that it should not mark the rightwindow based on root's broadcast since this created memepory leaks
	    int selfestimatedRWindow = lastPktRecvSeqNum +  (int)((currtime - lastPktRecvTime)/PUBLISHPERIOD); 
	    int controlPktRWindow = rootSeqNumRecv;
	    if(SaarTest.logLevel <= 880) myPrint("singletree: RWINDOW-ESTIMATION:" + "controlPkt:" + controlPktRWindow + ", selfestimate: " + selfestimatedRWindow, 880);

	    int rWindow = selfestimatedRWindow;
	    int lWindow = rWindow - TemporalBufferMap.ADVERTISEDWINDOWSIZE + 1;
	    // We will remove the sequence number outside the window from the blocks hashtable
	    //if(SaarTest.logLevel <= 875) myPrint("singletree: Removing key: " + (lWindow - 30), 875);
	    
	    // The safemagin is because if we miss a particular lWindow then that block will remain
	    int SAFEMARGIN = (int)((SaarClient.CONTROLPLANEUPDATEPERIOD/PUBLISHPERIOD) + 2);
	    for(int k =0; k < SAFEMARGIN; k++) {
		for(int stripeId = 0 ; stripeId < NUMSTRIPES; stripeId++) {
		    String blockId = "Stripe:" + stripeId + "Seq:" + (lWindow - 30 - k);
		    blocks.remove(blockId); // we keep a further safe window of 30 blocks because a node may advertise and other nodes when responding to the advertisement, may actually request a block that has past the window
		    numSequence.remove(blockId);

		    if(SaarTest.logLevel <= 875) myPrint("singletree: " + "Memory cleaning: lWindow: " + lWindow + " SAFEMARGIN: " + SAFEMARGIN + " removingId: " + (lWindow - 30 -k) + ")", 875);
		}
	    }

	    if(SaarTest.logLevel <= 875) myPrint("singletree: BLOCKSHASHTABLESIZE: " + blocks.size() + " NUMSEQUENCESIZE: " + numSequence.size(), 875);
	    //Enumeration e = blocks.keys();
	    //while(e.hasMoreElements()) {
	    //int storedkey = ((Integer) e.nextElement()).intValue();
	    //if(SaarTest.logLevel <= 875) myPrint("singletree:  storedkey: " + storedkey, 875);
	    //}

	    
	    // We will advertise the pkts in this window
	    bmap = new TemporalBufferMap(lWindow, numSequence, TemporalBufferMap.ADVERTISEDWINDOWSIZE);
	} else {
	    if(SaarTest.logLevel <= Logger.WARNING) myPrint("singletree: WARNING: rootSeqNumRecv is -1", Logger.WARNING);
	}
	return bmap;

    }
    

    // We advertise based on the sendbmap provided we have already bootstrapped properly and made an attempt on fetching the blocks in the bitmap. We consider only the left end 
    public int getStreamingQualityToAdvertise(TemporalBufferMap bmap) {
	int val;
	long currtime = getCurrentTimeMillis();
	if((rootSeqNumRecv == -1) || (bmap == null) || (((currtime - firstPktTime)/PUBLISHPERIOD) < TemporalBufferMap.ADVERTISEDWINDOWSIZE)) {
	    val = 99;
	} else {
	    if(SaarTest.logLevel <= 875) myPrint("singletree:  Computing quality based on fractionFilled ", 875);
	    val = bmap.fractionFilled(TemporalBufferMap.ADVERTISEDWINDOWSIZE - TemporalBufferMap.FETCHWINDOWSIZE);
	}
	return val;
    }


    
    // It is possible that the node has already received this block and that this notification from the channelviewer was because of the singletreedeliver itself. Since right now we do not use the packets recovered via the mesh to re-push them down the tree, we need not do anything here
    public void alreadyReceivedSequenceNumber(int seqNum) {
	
	/* WARNING: In anycase use the right conversion fuction to translate the global blockbased seqnum to tree pkt #
	//for(int stripeId=0; stripeId < NUMSTRIPES; stripeId++) {
	  //  String blockId = "Stripe:" + stripeId + "Seq:" + seqNum;
	    //if(!numSequence.containsKey(blockId)) {
		//numSequence.put(blockId, new Integer(1));
		//Block block = new Block(stripeId, seqNum);
		//block.setRecoveredOutOfBand();
		//block.addToPath(saarClient.bindIndex, saarClient.jvmIndex, saarClient.vIndex);
		//blocks.put(blockId, block);
	    //}
	//}
	*/

    }



    public void singletreeDeliver(int stripeId, int seqNum, int depth, NodeHandle parent, int numPkts, Block block) {
	long currtime = getCurrentTimeMillis();
	if(SaarTest.logLevel <= 880) myPrint("singletree: singletreedeliver(" + stripeId + "," + currtime + "," + depth + "," + parent + "," + seqNum + "," + numPkts + "," + block + ")", 880);


	if(saarClient.viewer.getFirstForegroundPktAfterJoin() == -1) {
	    saarClient.viewer.setFirstForegroundPktAfterJoin(seqNum);
	    
	}

	lastPktRecvTime = currtime;
	lastPktRecvSeqNum = seqNum;

	saarClient.viewer.setLastForegroundPktRecvTime(currtime, stripeId);
	saarClient.viewer.setLastForegroundPktRecvSeqnum(seqNum, stripeId);
	saarClient.viewer.receivedSequenceNumber(seqNum, dataplaneType);

	if(SaarClient.HYBRIDDEBUG) {
	    // We are not using our foreground bandwidth, so we dont notify the viewer
	} else {
	    saarClient.viewer.setLastForegroundPktPushedTime(currtime);
	}


	
    }


    public boolean evaluateIsConnected() {
	long currtime = getCurrentTimeMillis();
	long minValue;
	if((2*PUBLISHPERIOD) < 500) {
	    minValue = 500;
	} else {
	    minValue = 2*PUBLISHPERIOD;
	}
	if((currtime - lastPktRecvTime) > minValue) {
	    return false;
	} else {
	    return true;
	}
	

    }



    // The loop detection checks to see if the local node's exists in the recvPathToRoot and also if the local node's predecessor is the same as the current parent. Note that due to stale information it is possible to receive the local node in the recvPathToRoot but this might not imply that a loop has actually been formed
    public boolean loopDetected(rice.p2p.commonapi.Id[] recvPathToRoot) {
	if(manager.getParent() == null) {
	    // There is no chance of a loop, since we do not have a parent
	    return false;
	}
	// We check from i=1 since we need to find the predecessor
	for(int i=1; i< recvPathToRoot.length; i++) {
	    if(recvPathToRoot[i].equals(saarClient.endpoint.getId())) {
		// Check to see if the predecessor is the local node's parent otherwise its fine
		rice.p2p.commonapi.Id predecessor = recvPathToRoot[i-1];
		if(predecessor.equals(manager.getParent().getId())) {
		    // This implies a loop has been formed
		    return true;
		} else {
		    if(SaarTest.logLevel <= 875) myPrint("singletree: WARNING: A stale pathToRoot could have MISLED into trigger a loop formation error", 875);
		}

	    }

	}
	return false;
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


    public String getPathAsString(Id[] arr) {
	String s = "(";
	if(arr!= null) {
	    for(int i=0; i< arr.length; i++) {
		s = s + arr[i];
	    }
	}
	s = s + ")";
	return s;
    }




    public int computehca(Id[] arr1, Id[] arr2) {
	int hca = 0; // this is the height of the common ancestor 
	Id commonancestor = null; 
	boolean foundhca = false;
	Id[] patharr1, patharr2;  // In this case these arrays should be filled in the reverse order as per the hca computation algorithm below
	if(arr1== null) {
	    patharr1 = null;
	} else {
	    patharr1 = new Id[arr1.length];
	    for(int i=0; i<patharr1.length; i++) {
		patharr1[i] = arr1[arr1.length - i - 1];
	    }

	}
	if(arr2== null) {
	    patharr2 = null;
	} else {
	    patharr2 = new Id[arr2.length];
	    for(int i=0; i<patharr2.length; i++) {
		patharr2[i] = arr2[arr2.length - i - 1];
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
	String path1 = getPathAsString(patharr1);
	String path2 = getPathAsString(patharr2);
	//System.out.println("path1: " + path1 + " , " + "path2: " + path2 + " , commonancestor: " + commonancestor + " , hca: " + hca);
	return hca;

    }




    // This class contains metadata for the child related to this topic
    public class ChildState {
	NodeHandle child;
	long lastForwardHeartbeatTime ; // this will also be used as the time when the connection was established
	boolean gotChildAck; // until we get the first ack, this varibale is true
	int childDegree; // the childdegree will be used to do prempt degree pushdown

	public ChildState(NodeHandle child, int childDegree) {
	    this.child = child;
	    this.lastForwardHeartbeatTime = getCurrentTimeMillis(); 
	    this.gotChildAck = false; // When a child connection is established we need the child to send a ChildIsAliveMsg instantly when it receives the anycastack to set this variable to true, otherwise all 
	    this.childDegree = childDegree;
	}

	public void setLastForwardHeartbeatTime(long val) {
	    gotChildAck = true;
	    lastForwardHeartbeatTime = val;
	}

	public long getLastForwardHeartbeatTime() {
	    return lastForwardHeartbeatTime;
	}

	//	public boolean getGotChildAck() {
	//  return gotChildAck;
	//}

	public int getChildDegree() {
	    return childDegree;
	}


    }





    // Below is all the fields used in the blockbased, only a few are retained that will be used in PRM
    public class NeighborState {
	NodeHandle neighbor;
	//CoolstreamingBufferMap bmap; // this is the last bitmap received from the parent
	//Vector numRequested; // Got rid of this is a moving window of the number of pkts requested to parent in the last BANDWIDTHWINDOW timeperiods. Note that we preferably try not to violate this limit for each parentby asking other parents for this pkt, however we do ask shamelessly and it is upto the parent to actually deny the pkt when its bandwidth is consumed totally. Note that this policy is done since there might be spare bandwidth in the system (total outdegree > indegree) and a parnt might be willing to stream more down a particular child. 
	//int uStatic; // this is the latest static utilization advertised by parent
	//int uDynamic; // this is the latest dynamic utilization advertised by parent
	//long lastRequestedTime; // this is the last time we requeted a block from our parent in the primary mode. This time will be used to space out the primary blocks that will be requested from a parent
	//int streamingQuality; // This is the streaming quality of the parent as advertised by the parent himself
	//int avgMeshDepth;
	//int numGoodNeighbors;
	//long lastForwardHeartbeatTime ; // this is the time we heard from this parent, explicitly or via when the last bitmap was received
	//Vector numSent ; // we keep track of how much we sent to each child, when the overal banfwidth limits are reached we stop entertaining requests for these children
	long acquiredTime; // This is the time when the neighbor was acquired, this time is used to keep an up-to-date set of neighbors to preserve the invariant that our neighbors are a random subset of the entire group, thus neighbors acquired long time back when say the group was small should be replaced with a newer set
	boolean isActive; // This means the neighbor was acquired actively
	
	//int pNumGoodNeighbors; 

	boolean pIsMulticastSource;  // This will be false for PRM 
	
	public Id[] pathToRoot; // this is the neighbors pathtoroot info whichis updated periodically on receving a message from this neighbor. This state is used to exploit anti-correlated cross edges in the prm


	public NeighborState(NodeHandle neighbor, boolean isActive, boolean pIsMulticastSource) {
	    this.neighbor = neighbor;
	    this.isActive = isActive;
	    this.pIsMulticastSource = pIsMulticastSource;
	    acquiredTime = getCurrentTimeMillis();

	    //this.lastForwardHeartbeatTime = getCurrentTimeMillis();
	    //this.bmap = null;
	    //this.uStatic = -1;
	    //this.uDynamic = -1;
	    //this.lastRequestedTime = 0;
	    //this.streamingQuality = -1;
	    //this.numSent = new Vector();
	    //this.pNumGoodNeighbors = 0;
	    

	}

	public boolean isActive() {
	    return isActive;
	}

	public long getAcquiredTime() {
	    return acquiredTime;
	}

	public void setPathToRoot(Id[] pathToRoot) {
	    if(pathToRoot == null) {
		this.pathToRoot = null;
	    } else {
		this.pathToRoot = new Id[pathToRoot.length];
		for(int i=0; i<this.pathToRoot.length; i++) {
		    this.pathToRoot[i] = pathToRoot[i];
		}
	    }
	    
	}

	public Id[] getPathToRoot() {
	    return pathToRoot;
	}


    }









    public class TopicManager implements Observer {

	protected SaarTopic saartopic;

	protected Id[] pathToRoot; // The staleness of this entry can be checked via the variable lastReverseHeartbeatTime, since it is the parent's message which updates this value

	protected Id[] lastPushedPathToRoot; // This is the last pathToRoot that was pushed to children. With the help of this field we can push updates on pathToRoot only when necessary. Also note, that a newly added child will be pushed the currPathToRoot. thus you need not keep track of what you pushed to each child individually

	protected Vector children;

	// This keeps track of the children metadata
	protected Hashtable childrenState;
	
	protected NodeHandle parent;

	// This is the last time when a reverse heartbeat has received from the parent 
	protected long lastReverseHeartbeatTimeParent; 


	// unidirectional neighbor
	protected Vector neighbors;  // These neighbors are the PRM neighbors (these are directional neighbors)

	protected  Hashtable neighborsState; // this is the state of the PRM neighbors

	protected Vector backpointers; // this is the nodes that point to me

	public TopicManager(SaarTopic saartopic) {
	    this.saartopic = saartopic;
	    this.children = new Vector();
	    this.childrenState = new Hashtable();
	    this.lastReverseHeartbeatTimeParent = getCurrentTimeMillis();
	    appendPathToRoot(new Id[0]);
	    this.lastPushedPathToRoot = new Id[0];
	    this.neighbors = new Vector();
	    this.neighborsState = new Hashtable();
	    this.backpointers = new Vector();
	    
	}

	// Updated on Oct02-2007, to be invoked on a leave
	public void initialize() {
	    parent = null;
	    appendPathToRoot(new Id[0]);
	    this.lastPushedPathToRoot = new Id[0];
	    children.clear();
	    childrenState.clear();
	    neighbors.clear();
	    neighborsState.clear();
	    backpointers.clear();
	}

	public Topic getTopic() {
	    return saartopic.baseTopic;
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
		if(SaarTest.logLevel <= 875) myPrint("singletree: setLastForwardHeartbeatTimeChild() called on unknown child", 875);
	    }
	    
	}
	
	// child sending to us, it is called 'forward' since the flow is from upwards in the tree
	public long getLastForwardHeartbeatTimeChild(NodeHandle child) {
	  ChildState cState = (ChildState) childrenState.get(child);
	  if(cState != null) {
	      return cState.getLastForwardHeartbeatTime();
	  } else {
	      if(SaarTest.logLevel <= 875) myPrint("singletree: getLastForwardHeartbeatTimeChild() called on unknown child", 875);
	      return 0;
	  }
	}


	// child sending to us, it is called 'forward' since the flow is from upwards in the tree
	public int getChildDegree(NodeHandle child) {
	  ChildState cState = (ChildState) childrenState.get(child);
	  if(cState != null) {
	      return cState.getChildDegree();
	  } else {
	      if(SaarTest.logLevel <= Logger.WARNING) myPrint("singletree: ERROR: getChildDegree() called on unknown child", Logger.WARNING);
	      System.exit(1);
	      return -1;
	  }
	}

	public NodeHandle getLowestDegreeChild() {
	    int mindegree = 100;
	    NodeHandle mindegreeChild = null;

	    for(int i=0; i< children.size(); i++) {
		NodeHandle child = (NodeHandle)children.elementAt(i);
		int childdegree= getChildDegree(child);
		if(childdegree < mindegree) {
		    mindegree = childdegree;
		    mindegreeChild = child;
		}
	    }
	    return mindegreeChild;
	}

	
	public NodeHandle getParent() {
	    return parent;
	}

    
	public NodeHandle[] getChildren() {
	    return (NodeHandle[]) children.toArray(new NodeHandle[0]);
	}


	public int numChildren() {
	    return children.size();
	}
	
	public Id[] getPathToRoot() {
	    return pathToRoot;
	}


	public String getPathToRootAsString() {
	    String s = "currPathToRoot:" + pathToRoot.length + ", [";
	    for(int i=0; i< pathToRoot.length; i++) {
		s = s + pathToRoot[i] + ","; 
	    }
	    s = s +"]";
	    return s;
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
	    boolean[] childOfDegreeExists = new boolean[SingletreeContent.MAXCHILDDEGREE + 1];
	    int ret[] = new int[SingletreeContent.MAXCHILDDEGREE + 1];
	    int currTreeDepth = getTreeDepth();

	    for(int i=0; i< (SingletreeContent.MAXCHILDDEGREE + 1); i++) {
		childOfDegreeExists[i] = false;

	    }

	    for(int i=0; i< children.size(); i++) {
		NodeHandle child = (NodeHandle)children.elementAt(i);
		childOfDegreeExists[getChildDegree(child)] = true;
	    }

	    for(int i=0; i< (SingletreeContent.MAXCHILDDEGREE + 1); i++) {
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
	    if(SaarTest.logLevel <= 850) myPrint("singletree: " + "pathToRootAsString: " + getPathToRootAsString(), 850); 
	}
	
       	// This appends the local node's Id to the parent's pathToRoot
	public void setLastPushedPathToRoot(Id[] currPathToRoot) {
	    // build the path to the root for the new node
	    this.lastPushedPathToRoot = new Id[currPathToRoot.length ];
	    for(int i=0; i< currPathToRoot.length; i++) {
		this.lastPushedPathToRoot[i] = currPathToRoot[i];
	    }
	    if(SaarTest.logLevel <= 850) myPrint("singletree: " + "lastPushedPathToRootAsString: " + getLastPushedPathToRootAsString(), 850);
	    
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


	public void setParent(NodeHandle handle) {
	    NodeHandle prevParent = parent;
	
	    if(SaarTest.logLevel <= 880) myPrint("singletree: " + "setParent(" + saartopic + ", " +  handle + " )", 880);
	    if ((prevParent != null) && (handle !=null)) {
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("singletree: Changing parent for saartopic " + saartopic, Logger.WARNING);
	    }
	    
	    parent = handle;
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
		if(SaarTest.logLevel <= 875) myPrint("singletree: gotChildAck() called on unknown child", 875);
		return false;
	    }
	}




	//public boolean getGotChildAckChild(NodeHandle child) {
	//ChildState cState = (ChildState) childrenState.get(child);
	//if(cState != null) {
	//    return cState.getGotChildAck();
	//} else {
	//    if(SaarTest.logLevel <= 875) myPrint("singletree: getGotChildAckChild() called on unknown child", 875);
	//    return false;
	//}
	//}

	
	

	public void addChild(NodeHandle child, int childDegree) {
	    if(SaarTest.logLevel <= 880) myPrint("singletree: addChild(" + child + ", " + childDegree + ")" , 880);	    
	    if (!children.contains(child)) {
		if(!childrenState.containsKey(child)) {
		    childrenState.put(child, new ChildState(child, childDegree));
		}
		children.add(child);
		
	    } 

	}
      

	public void removeChild(NodeHandle child) {
	    if(SaarTest.logLevel <= 880) myPrint("singletree: removeChild(" + child + ")" , 880);	    
	    if(childrenState.containsKey(child)) {
		childrenState.remove(child);
	    }

	    children.remove(child);
	}


	/*****************  Below are the state of neighbors added to implement PRM ************/
	

	public NodeHandle[] getNeighbors() {
	    return (NodeHandle[]) neighbors.toArray(new NodeHandle[0]);
	}


	public NodeHandle[] getBackpointers() {
	    return (NodeHandle[]) backpointers.toArray(new NodeHandle[0]);
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


	// This is the time when the neighbor was discovered/acquired/established
	//public boolean isActiveNeighbor(NodeHandle neighbor) {
	//  NeighborState nState = (NeighborState) neighborsState.get(neighbor);
	//  if(nState != null) {
	//return nState.isActive();
	//  } else {
	//if(SaarTest.logLevel <= Logger.WARNING) myPrint("singletree: " + saarClient.endpoint.getId() + ": Retrieving isActive (active/passive) on a neighbor with missing entry in neighborsState, neighbor is " + neighbor, Logger.WARNING); 
	//return false;
	//  }
	//}


	// This is the time when the neighbor was discovered/acquired/established
	public long getAcquiredTimeNeighbor(NodeHandle neighbor) {
	    NeighborState nState = (NeighborState) neighborsState.get(neighbor);
	    if(nState != null) {
		return nState.getAcquiredTime();
	    } else {
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("singletree: " + saarClient.endpoint.getId() + ": Retrieving acquiredTime on a neighbor with missing entry in neighborsState, neighbor is " + neighbor, Logger.WARNING); 
		return 0;
	    }
	}


	public void update(Observable o, Object arg) {
	    if (arg.equals(NodeHandle.DECLARED_DEAD)) {
		if (neighbors.contains(o)) {
		    if(SaarTest.logLevel <= Logger.FINE) myPrint("singletree: " + saarClient.endpoint.getId() + ": CESM.Neighbor " + o + " has died - removing.", Logger.FINE);
		    
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



      public boolean containsBackpointer(NodeHandle neighbor) {
	  if(backpointers.contains(neighbor)) {
	      return true;
	  } else {
	      return false;
	  }
      }



      
	// active- denotes that we initiated the discovery of the neighbor 
	// passive means that we accept the neighbor request
	public void addNeighbor(NodeHandle neighbor) {
	    if(SaarTest.logLevel <= 880) myPrint("singletree: PRM.addNeighbor( " + getTopic() + ", " + neighbor + ")", 880);
	    
	    if (!neighbors.contains(neighbor)) {
		if(neighbor.isAlive()) {
		    // We update the childrenState data structure
		    if(!neighborsState.containsKey(neighbor)) {
			neighborsState.put(neighbor, new NeighborState(neighbor,true,false));
		    }
		    neighbors.add(neighbor);
		    neighbor.addObserver(this);
		    // We update this information to the global data structure of children -> topics
		} else {
		    if(SaarTest.logLevel <= Logger.WARNING) myPrint("singletree: WARNING: addNeighbor( " + getTopic() + ", " + neighbor  + ") did not add neighbor since the neighbor.isaLive() failed", Logger.WARNING);
		    
		}
		
	    }
	    
	}
	
	public boolean removeNeighbor(NodeHandle neighbor) {
	    if(SaarTest.logLevel <= 880) myPrint("singletree: PRM.removeNeighbor( " + getTopic() + ", " + neighbor + ")", 880);
	    
	    
	    // We update the childrenState data structure
	    if(neighborsState.containsKey(neighbor)) {
		neighborsState.remove(neighbor);
	    }
	    
	    neighbors.remove(neighbor);
	    neighbor.deleteObserver(this);
	    
	    return true;
	}

	
	// This updates the neighbor's pathToRoot information
	public void updateNeighborInfo(NodeHandle neighbor, Id[] pathToRoot) {
	    if(SaarTest.logLevel <= 875) myPrint("singletree: PRM.updateNeighborInfo( " + getTopic() + ", " + neighbor + ")", 875);
	    
	    if (neighbors.contains(neighbor)) {
		if(neighborsState.containsKey(neighbor)) {
		    NeighborState nstate = (NeighborState) neighborsState.get(neighbor);
		    nstate.setPathToRoot(pathToRoot);
		}
	    }
	    
	}


	public Id[] getPathToRootNeighbor(NodeHandle neighbor) {
	    NeighborState nState = (NeighborState) neighborsState.get(neighbor);
	    if(nState != null) {
		return nState.getPathToRoot();
	    } else {
		if(SaarTest.logLevel <= Logger.WARNING) myPrint("singletree: " + saarClient.endpoint.getId() + ": Retrieving acquiredTime on a neighbor with missing entry in neighborsState, neighbor is " + neighbor, Logger.WARNING); 
		return null;
	    }
	}


	public void addBackpointer(NodeHandle bp) {
	    if(SaarTest.logLevel <= 880) myPrint("singletree: PRM.addBackpointer( " + getTopic() + ", " + bp + ")", 880);
	    if(!backpointers.contains(bp)) {
		backpointers.add(bp);
	    }

	}



	public boolean removeBackpointer(NodeHandle bp) {
	    if(SaarTest.logLevel <= 880) myPrint("singletree: PRM.removeBackpointer( " + getTopic() + ", " + bp + ")", 880);
	    
	    backpointers.remove(bp);
	    bp.deleteObserver(this);
	    
	    return true;
	}



    }



}






