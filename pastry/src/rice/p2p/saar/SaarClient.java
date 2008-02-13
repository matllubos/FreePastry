
/*
 * Created on May 4, 2005
 */
package rice.p2p.saar;


import rice.p2p.saar.singletree.*;
import rice.p2p.saar.multitree.*;
import rice.p2p.saar.blockbased.*;
import rice.p2p.saar.simulation.*;

import rice.pastry.direct.DirectPastryNode;
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
 * SaarClient is a dummy client whose role is to register the different dataplaneClients and drive the experiment
 * @author Animesh
 */
public class SaarClient implements Application {

    public static int DATAPLANETYPE = -1; // will be set in the main()

    public static final int TESTANYCAST = 0;  // building block
    public static final int SINGLETREE = 1;  // building block
    public static final int MULTITREE = 2;   // building block
    public static final int BLOCKBASED = 3;  // building block
    public static final int SINGLETREEMESH = 4; // composed of 1 & 3
    public static final int MULTITREEMESH = 5; // composed of 1 & 3
    
    public int[] dataplanesEnabled = new int[4];  // this will tell me which are the dataplanes that have been enabled, it is set according to the DATAPLANETYPE

    


    public static int CENTRALSERVERBINDINDEX = 0;

    public static int MULTICASTSOURCEBINDINDEX = 1;

    public static int VIRTUALSOURCEMAXBINDINDEX = 0; // [2, ....MAX] since 1 is reserved for the actual source and 0 is reserved for the central server

    public Logger logger;
    
    public Environment environment;
    
    public SelectorManager selectorManager;
    
    // This is the index corresponding to the Planetlabnode or the Modelnet node based on the Ip address it is bound to
    public int bindIndex;

    // Pastry JVM index
    public int jvmIndex; 

    // Pastry Virtual node index
    public int vIndex;
    
    // These are thte total number of Anycast groups, however the total number of Scribe trees in the system is this multiplied by NUMTREES
    // WARNING : Since we use a single byte for streamId, we cannot use more than 128 groups ( 1 bit for sign). To test scalability we can test with NUMTREES of a higher value. 
    public static int NUMGROUPS = 4; // this is the number of dataplane building blocks  Eg of types grp 0: test anycast, grp 1:single-tree , grp 2:multi-tree , grp 3:block-based . Note that depending on the dataplanetype one or more of these will be enabled

    public static boolean EXPONENTIALOFFLINE = false;


    public static final long SWITCHINGPERIOD  = 1000;
    public static long DATAPLANEMAINTENANCEPERIOD  = 10; // We reduce this from 1000 to 100 to enable more fine grained block fetching in blockbased 
    public static long CONTROLPLANEUPDATEPERIOD  = 1000;
    public long lastControlPlaneUpdateTime = 0;

    public static final long ANYCASTRATELIMITPERIOD = 0;

    
    public Random rng = new Random(); // created for randomization services
    
    // These are created for the periodic tasks
    CancellableTask switchingTask; 

    CancellableTask dataplanemaintenanceTask;

    CancellableTask controlplaneupdateTask;
    
    
    public Endpoint endpoint;
    
    public PastryNode node;

    public TopicTaskState[] allTopics; // this is used to implement the abstraction of a dataplane client being simulatenously a member of different 

    public Hashtable topicTaskStates; // this is used as a Hashtbale for TopicTaskState

    public SaarAPI saarAPI;

    public SaarPolicy saarPolicy; // this implements the policy for anycast traversal

    public Hashtable topic2TopicName;

    public boolean topicsInitialized = false;

    public String hostName; // This will be appended with the seq numbers for debugging
    // This Ip together with the esm port will be contacted when the anycast succeeds 
    public byte[] hostIp = new byte[4];



    // saar proxy will be used for someone implementing his own dataplane and accessing the Saar service through the API
    public UDPSaarProxy saarProxy = null; 
    public SocketAddress saarProxySentToAddress = null;
    public InetAddress saarProxyInetAddr = null;
    public DatagramSocket udpSocket = null;




    // This is set using what is read in using the NAMETOIPCODEDFILE in SocketPastryNode and is used for efficient serialization. 
    public int ownPLIndex = -1;

    public boolean isBootstrapHost = false;

    public PastryNodeFactory factory;

    private InetAddress localAddress = null; // .getHostAddress() will tell us the modelnet ip
    String hostAddress = "NONE";

    public long BOOTTIME = 0; 
    public int SESSIONTIME = 0; //  in sec . 0- infinite (This is the default option), nonzero - After this many seconds the jvm will exit 
    public long STARTTIME = 0; // this is the start time of this local node, it uses this time to emulate session time


    public static long CONTROLOVERLAYBOOTTIME = 300000; // 600000 // When we emulate only data overlay churn, we start the churn after the contro loverlay has booted
    public static long MAXINITIALWAITTIME = 300000; // 300000 // After joining the control overlay the nodes start after a random time
    public long INITIALRANDOMWAITTIME  = 0; // A node waits for this period vefore joining ( this period is the sum of the controloverlayboottime + a random waiting time) 


    public static long OFFLINETIME = 15000; // to emulate as big a group as possible the node stays offline just long enough to expire the neighbor timeouts 
    public static long NOCHURNTIME = 300000; // 600000 No churn
    public static long MEANDATAOVERLAYCHURNPERIOD = 120000; // 2 minutes i.e 120000
    public static long MINIMUMDATAOVERLAYSTAYTIME =  60000; // in msec, 15 sec 
    public static long EXPERIMENTTIME = 2000000; // 3600000 maximum experiment duration 
    public static long GRADUALFINISHTIME = 300000; // maximum experiment duration 


    
    public static int DATAOVERLAYCHURNTYPE = 2; // 0 - constant , 1- uniformrandom , 2 - Poisson

    
    // These variables will be used to set the Nodedegree
    public static double MUL = 2; // used by HETEROGENOUSTYPE = 0 & 1

    public static final int BITTYRANT = 3;
    public static final int MONARCH = 4; 
    public static int HETEROGENOUSTYPE = 2; // 0 - homogeneous=MUL , 1 - unioform random[0,MUL), 2 - streaming workload ,  3 - Bittyrant , 4 - Monarch



    public static double MINDEGREE = 1.15; // We added 0.15 on Oct29 since we use the hysteresis etc enforced only in HETEROGENOUSTYPE=2
    public static int DEGREECAP = 50;
    public static double SOURCEDEGREE = 5; // degree of the multicast source
    public static double CENTRALSERVERDEGREE = 2; // degree of the central server


    public double nodedegree=  0; // This is the Resource.Index and can be fractional. Based on this value the nodes will set their integral outdegrees. It will be set by getNodedegree()

    public boolean amMulticastSource; // When a node is chosen as a multicast source we currently assume it is the multicast source for all the dataplanes

    public boolean amVirtualSource; 

    public NodeHandle centralizedHandle; 

    public ChannelViewer viewer; // This is the end node that will recive all packets and notify bandwidth usage 


    public static boolean HYBRIDDEBUG = false; // this should be enabled only while debugging why the hybrid protocol is not giving performance = pure meshbased when the tree protocols break down


    public SaarSimTest simulator; // The code in general does not rely on state kept in the simulator, but this is used only for a temporary hack for the multitree to get the grpSummary quickly without relying on the centralized server to tell it. 
    
    public static int STREAMBANDWIDTHINBYTES = 50000; // bytes, assuming 400 kbps

    public static int SYNTHETICSTREAMBANDWIDTHINBYTES =  43750; // bytes, assuming 350 kbps

    public static boolean LEAVENOTIFY = false; // by default we do abrupt leaves

    public static long NEIGHBORDEADTHRESHOLD = 4000;

    public static long NEIGHBORHEARTBEATPERIOD = 1000;

    public static double FIXEDAPPLICATIONLOSSPROBABILITY = 0.0; // This was the prevous implementation of loss at the dataplane layer 
    public static double MEANLINKLOSSPROBABILITY = 0; // Exponential distribution
    public double linklossprobability; // Will be set according to passed value in the constructor


    public static double FRACTIONCONTROLBANDWIDTH = 100; // This is estimate fraction of the upstream bandwidth that will be used up by control traffic 

    public static int MAXCONTROLTREEFANOUT = 16; // default is unlimited
    
    public static boolean SIMULATETRANSMISSIONDELAYS = true;


    public static boolean SEPARATECONTROLANDDATAQUEUE = true;

    public static boolean CONTROLTRAFFICISFREE = false; 

    public static boolean NICEBACKGROUNDTRAFFIC = false; 

    public static boolean SIMULATEDOWNLINK = false;

    //public static boolean IPPACKETMULTIPLEXING = true; We only support the true option now, Jan-2008. previous is obsolete

    public static long MAXQUEUEDELAYBACKGROUND = 1000; 


    public int pingId = 0; // This is used to just test the delays in simulator/Modelnet
    
    public static boolean PINGTEST = false;


    public class TopicTaskState {
	public int tNumber;
	public SaarTopic saartopic;
	public String topicName;
	public int dataplaneType; // 1 - single-tree, 2 - multi-tree, 3 - block-based
	public DataplaneClient dataplaneClient; // instance of the appropriate type of dataplane type
	// This is incremented each time it broadcasts a message to teh group
	public int pSeqNum ; 

	public int publishOpportunityCount;
	// This is the last time (local clock) that this node published to this group
	public long pTime;

	// This number is completely local and helps the local node to detect if its anycast failed or succeeded
	public int aSeqNum ;
	public long lastAnycastTime; // This is the last time the data plane's anycast was allowed, we use this to implement ratelimiting
	public boolean isSubscribed; 	// This is true if the node is subscribed to this topic
	public long lastSwitchedTime = 0; // this helps in emulating switching 
	public long currStayTime = 0;  // this helps in emulating switching
	public boolean firstJoinPending = true; // this helps in emulating switching

	public TopicTaskState(int tNumber, SaarTopic saartopic, String topicName, int dataplaneType, SaarClient saarClient, SaarAPI saarAPI, double nodedegree){
	    this.tNumber = tNumber;
	    this.saartopic = saartopic;
	    this.topicName = topicName;
	    this.dataplaneType = dataplaneType;
	    if(dataplaneType == TESTANYCAST) {
		//dataplaneClient = new TestAnycastClient(saarClient, tNumber, saartopic, topicName, dataplaneType,nodedegree, amMulticastSource);
		if(SaarTest.logLevel <= 875) myPrint("TestAnycastClient deprecated", 875);
		System.out.println("TestAnycastClient deprecated");
		System.exit(1);

	    } else if(dataplaneType == SINGLETREE) {
		dataplaneClient = new SingletreeClient(saarClient, tNumber, saartopic, topicName, dataplaneType,nodedegree, amMulticastSource, amVirtualSource);
	    } else if(dataplaneType == MULTITREE) {
		dataplaneClient = new MultitreeClient(saarClient, tNumber, saartopic, topicName, dataplaneType, nodedegree, amMulticastSource, amVirtualSource);
	    } else if(dataplaneType == BLOCKBASED) {
		dataplaneClient = new BlockbasedClient(saarClient, tNumber, saartopic, topicName, dataplaneType,nodedegree, amMulticastSource, amVirtualSource);
	    }

	    isSubscribed = false;
	    pSeqNum = 0;
	    publishOpportunityCount = 0;
	    pTime = saarClient.getCurrentTimeMillis();
	    aSeqNum = 0;
	    this.lastAnycastTime = 0;
	    if(SaarTest.logLevel <= 875) myPrint("Creating dataplaneType: " + dataplaneType + " , topicName: " + topicName + ", saartopic: " + saartopic, 875);
	}

	public void setSubscribed(boolean val) {
	    isSubscribed = val;
	}

	public void setPTime(long val) {
	    pTime = val;
	}

	public void setPSeqNum(int val) {
	    pSeqNum = val;
	}

	public void setPublishOpportunityCount(int val) {
	    publishOpportunityCount = val;
	}



	public void setASeqNum(int val) {
	    aSeqNum = val;
	}

	public void setLastAnycastTime(long val) {
	    lastAnycastTime = val;
	}


    }

    

    /**
     * @param node the PastryNode
     */
    public SaarClient(int bindIndex, int jvmIndex, int vIndex, PastryNode node, DatagramSocket udpSocket, UDPSaarProxy saarProxy, PastryNodeFactory factory, String instance, int SESSIONTIME, long BOOTTIME, NodeHandle centralizedHandle,double nodedegree, boolean amMulticastSource, boolean amVirtualSource, double linklossprobability, SaarSimTest simulator) {
	
	
	try {
	    System.out.println("Initializing SaarClient(" + "bindIndex:" + bindIndex + ", nodedegree:" + nodedegree + ", linklossprobability:" + linklossprobability + ", amMulticastSource:" + amMulticastSource + ", amVirtualSource:" + amVirtualSource + ", DATAPLANETYPE: " + DATAPLANETYPE);
	    

	    this.simulator = simulator;
	    this.linklossprobability = linklossprobability;
	    this.factory = factory;
	    this.environment = node.getEnvironment();

	    this.STARTTIME = getCurrentTimeMillis();
	    this.BOOTTIME = BOOTTIME;
	    this.centralizedHandle = centralizedHandle;

	    if(environment.getParameters().contains("socket_bindAddress")) {
		localAddress = environment.getParameters().getInetAddress("socket_bindAddress");
	    }
	    
	    this.selectorManager = environment.getSelectorManager();
	    logger = environment.getLogManager().getLogger(SaarClient.class,null);

	
	    if(SaarTest.logLevel <= 880) myPrint("STARTTIME= " + STARTTIME + ", BOOTTIME= " + BOOTTIME, 880);
	    this.node = node;
	    if(SaarTest.logLevel <= 880) myPrint("LocalAddress= " + localAddress, 880);
	    if(SaarTest.logLevel <= 880) myPrint("Passed (bindIndex,jvmIndex,vIndex) from SaarTest= (" + bindIndex + "," + jvmIndex + "," + vIndex + ")" , 880);
	    this.bindIndex = bindIndex;
	    this.jvmIndex = jvmIndex;
	    this.vIndex = vIndex;
	    this.udpSocket  = udpSocket;
	    this.saarProxy = saarProxy;
	    // you should recognize this from lesson 3
	    //this.endpoint = node.registerApplication(this, instance);
	    this.endpoint = node.buildEndpoint(this, instance);

	    if(localAddress != null) {
		InetAddress addr = localAddress;
		// Get the Ip
		hostAddress = addr.getHostAddress();	
		if(SaarTest.logLevel <= 880) myPrint("HostAddress= " + hostAddress, 880);
		hostIp = addr.getAddress();
		hostName = addr.getHostName();
		if(SaarTest.logLevel <= 880) myPrint("HostName= " + hostName, 880);
		if(SaarTest.logLevel <= 880) myPrint("hostIp: " + hostIp[0] + "." + hostIp[1] + "." + hostIp[2] + "." + hostIp[3], 880);
	    }


	} catch(Exception e) {
	    System.out.println("ERROR: Trying to get localhost ipaddress " + e);
	    e.printStackTrace();
	    System.exit(1);
	}
	// construct Scribe
	saarAPI = new SaarImpl(node, "saarImpl", SESSIONTIME, BOOTTIME, bindIndex, jvmIndex, vIndex, centralizedHandle, this);
	allTopics = new TopicTaskState[NUMGROUPS];
	topicTaskStates = new Hashtable();
 
	
	if((bindIndex==0) && (jvmIndex==0) && (vIndex==0)) {
	    isBootstrapHost = true;
	    if(SaarTest.logLevel <= 875) myPrint("ISBOOTSTRAPHOST: TRUE", 875);
	} else {
	    if(SaarTest.logLevel <= 875) myPrint("ISBOOTSTRAPHOST: FALSE", 875);
	}	

	//    ownPLIndex = ((rice.pastry.socket.SocketPastryNode)node).getPLIndexByIp(hostAddress);
	ownPLIndex = PLDictionary.getPLIndexByIp(hostAddress);
	if(ownPLIndex > 0) {
	    if(SaarTest.logLevel <= 875) myPrint("OwnPLIndex= " + ownPLIndex, 875);
	    // It overrides an uninitialized bindIndex
	    if (this.bindIndex == -1) {
		this.bindIndex = ownPLIndex;
		if(SaarTest.logLevel <= 875) myPrint("Note: Resetting bindIndex using ownPLIndex to " + this.bindIndex, Logger.WARNING);
	    }
	} else {
	    if(SaarTest.logLevel <= Logger.WARNING) myPrint("WARNING: ownPLIndex=-1 while determining ownPLIndex with ownIpString:" + hostAddress, Logger.WARNING);
	    if(!SaarTest.MODELNET) {
		System.exit(1);
	    }
	}
    
	if(SaarTest.localAddress != null) {
	    saarProxyInetAddr = SaarTest.localAddress;
	    saarProxySentToAddress = new InetSocketAddress(saarProxyInetAddr,SaarTest.SCRIBESERVERPORT);
	}
	
	if(SaarTest.logLevel <= 875) myPrint("saarProxySentToAddress: " + saarProxySentToAddress, 875);



	/* This part of code is done in the Simulation driver and values passed in the SaarClient constructor
	// We use the binIndex=0 for the centralized node
 	if(this.bindIndex == MULTICASTSOURCEBINDINDEX) {
	    amMulticastSource = true;
	    if(SaarTest.logLevel <= 875) myPrint("I AM MULTICAST SOURCE", 875);
	} else if((this.bindIndex > MULTICASTSOURCEBINDINDEX) && (this.bindIndex <= VIRTUALSOURCEMAXBINDINDEX)) {
	    
	    amVirtualSource = true;

	} else {
	    // Centralized node (index=0) or other normal nodes
	}



	nodedegree = getNodedegree(); // nodedegree should be called before setting the amMulticastSource
	*/

	this.amMulticastSource = amMulticastSource;
	this.amVirtualSource = amVirtualSource;
	this.nodedegree = nodedegree;




	if(SaarTest.logLevel <= 880) myPrint("NodeStats: " + "Nodedegree: " + nodedegree + " , amMulticastSource: " + amMulticastSource + " , amVirtualSource: " + amVirtualSource, 880);

	if(PINGTEST) {
	    endpoint.register();
	    return;
       
	}

	this.viewer = new ChannelViewer(this);
	
	setDataplanesEnabledVector();
	
	setGroupParams();

	topicsInitialized = true;

	if(SaarTest.logLevel <= 875) myPrint("App[" + vIndex + "] is ready " +  endpoint.getLocalNodeHandle(), 875);
	 
	// The centralized membership server should not join any data overlay
	if(!endpoint.getLocalNodeHandle().equals(centralizedHandle)) {
	    startDataplanemaintenanceTask();
	    
	    startControlplaneupdateTask();
	    
	    startSwitchingTask(); // This will be used to emulate data overlay dynamics
	}

	endpoint.register();

    }


    public long getCurrentTimeMillis() {
	return environment.getTimeSource().currentTimeMillis();
    }
    

    public void myPrint(String s, int priority) {
      if (logger.level <= priority) logger.log(s);
    }



    public DataplaneClient getDataplaneClient(Topic basetopic) {
	return ((TopicTaskState)topicTaskStates.get(basetopic)).dataplaneClient;

    }


    public void setDataplanesEnabledVector() {

	if(SaarTest.logLevel <= 875) myPrint("setDataplanesEnabledVector()", 875);
	if(DATAPLANETYPE == 0) {
	    dataplanesEnabled[0] = 1;
	    dataplanesEnabled[1] = 0;
	    dataplanesEnabled[2] = 0;
	    dataplanesEnabled[3] = 0;


	} else if(DATAPLANETYPE == 1) {
	    dataplanesEnabled[0] = 0;
	    dataplanesEnabled[1] = 1;
	    dataplanesEnabled[2] = 0;
	    dataplanesEnabled[3] = 0;
	   

	} else if(DATAPLANETYPE == 2) {
	    dataplanesEnabled[0] = 0;
	    dataplanesEnabled[1] = 0;
	    dataplanesEnabled[2] = 1;
	    dataplanesEnabled[3] = 0;


	} else if(DATAPLANETYPE == 3) {
	    dataplanesEnabled[0] = 0;
	    dataplanesEnabled[1] = 0;
	    dataplanesEnabled[2] = 0;
	    dataplanesEnabled[3] = 1;


	} else if(DATAPLANETYPE == 4) {
	    dataplanesEnabled[0] = 0;
	    dataplanesEnabled[1] = 1;
	    dataplanesEnabled[2] = 0;
	    dataplanesEnabled[3] = 1;



	} else if(DATAPLANETYPE == 5) {
	    dataplanesEnabled[0] = 0;
	    dataplanesEnabled[1] = 0;
	    dataplanesEnabled[2] = 1;
	    dataplanesEnabled[3] = 1;

	}

	if(SaarTest.logLevel <= 875) myPrint("DataplanesEnabledVector[" + dataplanesEnabled[0] + "," + dataplanesEnabled[1] + "," + dataplanesEnabled[2] + "," + dataplanesEnabled[3] + "]", 875);



    }


    /****  Schedule the periodic tasks at diferent offsets *******/

    public void startSwitchingTask() {
	if(SaarTest.logLevel <= 850) myPrint(" startSwitchingTask: ", 850);
	switchingTask  = endpoint.scheduleMessage(new SwitchingContent(), 300, SWITCHINGPERIOD);    
    }


    public void startDataplanemaintenanceTask() {
	if(amMulticastSource || amVirtualSource) {
	    dataplanemaintenanceTask = endpoint.scheduleMessage(new DataplanemaintenanceContent(), 600, DATAPLANEMAINTENANCEPERIOD); // We schedule the multicast source differently ( period < streaming rate) to make sure that the scheduling thread gives it enough opportunity to send out one packet per second (or at the desired streaming rate)     
	} else {
	    dataplanemaintenanceTask = endpoint.scheduleMessage(new DataplanemaintenanceContent(), 600, DATAPLANEMAINTENANCEPERIOD);    
	}

    }

    public void startControlplaneupdateTask() {
	if(amMulticastSource) {
	    controlplaneupdateTask = endpoint.scheduleMessage(new ControlplaneupdateContent(), 900, 100);    
	} else {
	    controlplaneupdateTask = endpoint.scheduleMessage(new ControlplaneupdateContent(), 900, CONTROLPLANEUPDATEPERIOD);    
	}

    }



    


    // This tells us the next stay time
    public long getStayTime() {
	// We currently return SWITCHINGPERIOD +/- [0, (SWITCHINGPERIOD - MINSWITCHINGPERIOD)]
	long val;
	
	if(DATAOVERLAYCHURNTYPE == 0) {
	    val = MEANDATAOVERLAYCHURNPERIOD;
	} else if(DATAOVERLAYCHURNTYPE == 1) {
	    if(MEANDATAOVERLAYCHURNPERIOD < 120000) {
		System.out.println("ERROR: We assume that the churn period is atleast 2 minutes");
		System.exit(1);
	    }
	    // random between [MEANDATAOVERLAYCHURNPERIOD - mean/2, MEANDATAOVERLAYCHURNPERIOD + mean/2]
	    int deviation =  rng.nextInt((int)MEANDATAOVERLAYCHURNPERIOD/2); // in seconds
	    int sgn = rng.nextInt(2);
	    if(sgn==0) {
		val = MEANDATAOVERLAYCHURNPERIOD - deviation;
	    } else {
		val = MEANDATAOVERLAYCHURNPERIOD + deviation;
	    }


	}else {
	    double randNum = rng.nextDouble();
	    double intermediate = Math.log(randNum);
	    val = (long) (-MEANDATAOVERLAYCHURNPERIOD * intermediate);
	    //if(SaarTest.logLevel <= 875) myPrint("randNum: " + randNum + ", intermediate: " + intermediate + ", val: " + val, 875);
	    // We enforce a minimum session time of 1 minute
	}

	if(val < MINIMUMDATAOVERLAYSTAYTIME) {
	    val = MINIMUMDATAOVERLAYSTAYTIME;
	}
	
	//val = 180000;   // Temporary static setting to check code
	if(SaarTest.logLevel <= 880) myPrint(" STAYTIME: " + val, 880);
	return val;
    }



    
    public static double getLossRate(Random rng) {
	double val;
	double randNum = rng.nextDouble();
	double intermediate = Math.log(randNum);
	val = (double) (-MEANLINKLOSSPROBABILITY * intermediate);
	//System.out.println("val:" + val + " intermediate:" + intermediate);
	if(val > 0.99) {
	    val = 0.99;
	}
	return val;
	
    }


    



    
    // Degree based on degree caps (MIN,MAX)
    public static double getNodedegree(Random rng, SaarSimTest simulator) {
	double degree = -1;

	//if(amMulticastSource) {
	//  degree = SOURCEDEGREE;
	//} else if(HETEROGENOUSTYPE == 0) {

	if(HETEROGENOUSTYPE == 0) { // homoegeneous with a fixed value (integer or non-integer)
	    degree = MUL;
	} else if(HETEROGENOUSTYPE == 1) { // uniformly random between (0,MUL)  with possibly fractional values
	    // We first multiply by 10 since MUL can be a non-integer like 1.25
	    degree = 0.1 * rng.nextInt((int)MUL*10);
	} else if(HETEROGENOUSTYPE == 2) { // heterogeneous with integral values
	    
	    int val = rng.nextInt(10000);

	    // BEGIN OUTDATED SYHTETIC DISTRIBUTION
	    // 55.70 % is degree 0          CDF = 55.70
	    // 21.15 % is degree 1          CDF = 76.85
	    // 9.50 % is degree 2           CDF = 86.35
	    // 5.88 % is degree [3-19]      CDF = 92.23
	    // 7.69 % is degree > 20        CDF = 100.00
	    //if(val < 5570) {
	    //degree = 0;
	    //} else if(val < 7685) {
	    //degree = 1;
	    //} else if(val < 8635) {
	    //degree = 2;
	    //} else if(val < 9223) {
	    // We generate a random number between [3-$DEGREECAP] uniformly randomly
	    //degree = rng.nextInt(19-3+1) + 3;
	    //} else {
	    //degree = 20;
	    //}
	    // END OUTDATED SYNTHETIC DISTRIBUTION



	    // 86 % is degree 1.15          
	    // 14 % is degree 2.30         
	    if(val < 8600) {
		degree = 1.15;
	    } else {
		degree = 2.30;
	    }
	    
	    if(degree > DEGREECAP) {
		degree = DEGREECAP;
	    }
	    if(degree < MINDEGREE) {
		degree = MINDEGREE;
	    }

	    double degreebeforescaling = degree;
	    double chosenbw = degree * (SYNTHETICSTREAMBANDWIDTHINBYTES*8/1000); // Added on Oct27-2007, this is because when the fraccontrolbw = 0, then the way to assign slightly higher ri is to scale down the streambandwidth
	    degree = chosenbw / (STREAMBANDWIDTHINBYTES*8/1000);
	    //System.out.println("degreebeforescaling: " + degreebeforescaling + " degreeafterscaling: " + degree);
	 
	} else if((HETEROGENOUSTYPE == BITTYRANT) || (HETEROGENOUSTYPE == MONARCH)) {
	    // We will determine the bandwidth based on the input cem-bandwidth distribution file
	    int val = rng.nextInt(10000);
	    double chosenbw = 0;
	    for(int i=0; i <simulator.cembandwidthcdf.size(); i++) {
		SaarSimTest.CdfTuple cdftuple = (SaarSimTest.CdfTuple) simulator.cembandwidthcdf.elementAt(i);
		if( val <= (cdftuple.cdf * 10000)) {
		    chosenbw = cdftuple.bw; // this is in kbps
		    break;
		}

	    }
	    degree = chosenbw / (STREAMBANDWIDTHINBYTES*8/1000);
	    //System.out.println("Chosen-up-bw: " + chosenbw + ", nodedegree: " + degree);
	    
	}
	
	return degree;
    }
    








    public void setGroupParams() {
	int routingBase = node.getRoutingTable().baseBitLength();
	for(int i=0; i<NUMGROUPS; i++) {
	    if(dataplanesEnabled[i] == 0) {
		continue;
	    }
	    String baseTopicName = "" + i;
	    Topic baseTopic = new Topic(new PastryIdFactory(node.getEnvironment()), baseTopicName);
	    SaarTopic saartopic = new SaarTopic(baseTopic, routingBase);
	    int dataplaneType;
	    //if(DATAPLANETYPE == -1) {
	    //   dataplaneType = (i % 3) + 1; // one-to-one mapping from baseTopicIndex to dataplaneType
	    //} else {
	    //  dataplaneType = (i % 3) + DATAPLANETYPE; // one-to-one mapping from baseTopicIndex to dataplaneType
	    //}
	    
	    dataplaneType = i;
	    TopicTaskState tState= new TopicTaskState(i,saartopic, baseTopicName, dataplaneType,this,saarAPI, nodedegree);
	    
	    allTopics[i] = tState;
	    topicTaskStates.put(baseTopic, tState);
	    
	}
    }

    boolean firstTimeRI = true;
    public double getDataplaneRIUsedByControl() {
      if (firstTimeRI && logger.level <= Logger.WARNING) {
        logger.log("SaarClient.getDataplaneRIUsedByControl() not implemented.");
        firstTimeRI = false;
      }
      return 1.0;
//	return ((DirectPastryNode)node).getDataplaneRIUsedByControl();
    }

    boolean firstTimeQD = true;
    public long computeQueueDelay() {
      if (firstTimeRI && logger.level <= Logger.WARNING) {
        logger.log("SaarClient.computeQueueDelay() not implemented.");
        firstTimeRI = false;
      }
      return 1;
//	return ((DirectPastryNode)node).computeQueueDelay();
    }



    public void reqRegister(SaarTopic saartopic, DataplaneClient dataplaneClient) {
	saarAPI.register(saartopic, dataplaneClient);

    }



    public void reqSubscribe(int tNumber) {
	saarAPI.subscribe(allTopics[tNumber].saartopic);

    }


    public void reqUnsubscribe(int tNumber) {
	saarAPI.unsubscribe(allTopics[tNumber].saartopic);

    }


    public void reqUpdate(int tNumber, SaarContent saarContent, boolean forceUpdate) {
	saarAPI.update(allTopics[tNumber].saartopic, saarContent, forceUpdate);

    }
    
    // the dataplaneclient can choose to specify the number of trees to use upto the maximum number of trees supported by SAAR
    public void reqAnycast(int tNumber, SaarContent reqContent, NodeHandle hint, int numTreesToUse, int satisfyThreshold, int traversalThreshold) {

	long prevAnycastTime = allTopics[tNumber].lastAnycastTime;
	//long currtime = getCurrentTimeMillis();
	long currtime = environment.getTimeSource().currentTimeMillis();
	if((currtime - prevAnycastTime) >= ANYCASTRATELIMITPERIOD) {
	    int currSeq = allTopics[tNumber].aSeqNum;
	    allTopics[tNumber].setASeqNum(currSeq + 1); 
	    allTopics[tNumber].setLastAnycastTime(currtime);
	    reqContent.setAnycastGlobalId("Anycast" + "_B" + bindIndex + "_J" + jvmIndex + "_V" + vIndex + "_G" + tNumber + "_S" + currSeq);

	    if(SaarTest.logLevel <= 875) myPrint("reqAnycast() issued", 875);	    
	    saarAPI.anycast(allTopics[tNumber].saartopic, reqContent, hint, numTreesToUse,  satisfyThreshold, traversalThreshold);  
	} else {
	    if(SaarTest.logLevel <= 875) myPrint("reqAnycast() DENIED because of rate limiting", 875);	    
	}
    }
    

    // networkdelay is RTT
    public void issuePing(NodeHandle remote, float networkdelay, int payloadinbytes) {
	pingId ++;
	saarAPI.issuePing(remote, pingId, networkdelay, payloadinbytes);

    }

    
    /***** Methods in interface Application ******/
    
    public boolean forward(RouteMessage message) {
	return true;
    }
    
    
    public void update(NodeHandle handle, boolean joined) {
    
    }


    
    

    public void deliver(Id id, Message message) {
	if(!topicsInitialized) {
	    return;
	}
	long currtime = getCurrentTimeMillis(); 





	// These messages are messages that were sent by the registered dataplaneclients. The topic in ScribeMessage is used to demultiplex the message to the appropriate dataplaneclient
	if(message instanceof SaarDataplaneMessage) {
	    SaarDataplaneMessage sMsg = (SaarDataplaneMessage)message;
	    Topic basetopic = sMsg.getTopic();
	    SaarTopic saartopic = ((TopicTaskState)topicTaskStates.get(basetopic)).saartopic;

	    if(sMsg instanceof MyAnycastAckMsg) {
		MyAnycastAckMsg ackMsg = (MyAnycastAckMsg) sMsg;
		SaarContent mycontent = (SaarContent) ackMsg.getContent();
		// We will add the code of this node to track the path traversed
		mycontent.addToMsgPath(endpoint.getLocalNodeHandle(), bindIndex, jvmIndex, vIndex);
	    }


	    getDataplaneClient(sMsg.getTopic()).recvDataplaneMessage(saartopic,basetopic, sMsg);	
	}

	if((message instanceof ScribeMessage) && !(message instanceof SaarDataplaneMessage)) {
	    System.out.println("ERROR: All dataplane specific messages should extend SaarDataplaneMessage");
	    System.exit(1);

	}
	

	
	if (message instanceof SwitchingContent) {
	    if(SaarTest.logLevel <= 850) myPrint("SwitchingContent handler", 850);
	    if((currtime - STARTTIME) > (EXPERIMENTTIME + GRADUALFINISHTIME)) {
		System.out.println("Smoothly finishing experiment duration of " + EXPERIMENTTIME + " after an extra time of " + GRADUALFINISHTIME); 
		System.exit(1);
	    }
	    if(INITIALRANDOMWAITTIME == 0) {
		if(amMulticastSource) {
		    INITIALRANDOMWAITTIME = CONTROLOVERLAYBOOTTIME - 30;
		} else {
		    INITIALRANDOMWAITTIME = CONTROLOVERLAYBOOTTIME + rng.nextInt((int)MAXINITIALWAITTIME);
		}
		if(SaarTest.logLevel <= 880) myPrint("INITIALRANDOMWAITTIME: " + INITIALRANDOMWAITTIME, 880);
	    }

	    // We do this for all topics
	    

	    
	    
	    long getStayTimeVal = 0;
	    for(int tNumber=0; tNumber<NUMGROUPS; tNumber++) {	    

		if(dataplanesEnabled[tNumber] == 0) {
		    continue;
		}


		if((currtime - BOOTTIME) > INITIALRANDOMWAITTIME) {
		    //System.out.println("currtime:" + currtime + ", boottime: " + BOOTTIME + ", initialrandomwaittime: " + INITIALRANDOMWAITTIME + " lastSwitchedTime["+tNumber+"]:" + allTopics[tNumber].lastSwitchedTime + " currStayTime:[" + tNumber + "]:" + allTopics[tNumber].currStayTime);

		    if((currtime - allTopics[tNumber].lastSwitchedTime) > allTopics[tNumber].currStayTime) {

			if(getStayTimeVal == 0) { // This is a temporary hack to get the hybrid dataplane working so that all the data planes operate in join()/leave() in lock step, thereby requiring that they see the same staytime
			    getStayTimeVal = getStayTime();
			}
			allTopics[tNumber].lastSwitchedTime = currtime;
			if(allTopics[tNumber].firstJoinPending) {
			    allTopics[tNumber].currStayTime = NOCHURNTIME + getStayTimeVal;
			    allTopics[tNumber].firstJoinPending = false;
			} else if(!allTopics[tNumber].isSubscribed){
			    allTopics[tNumber].currStayTime = getStayTimeVal;
			} else {
			    if(EXPONENTIALOFFLINE) {
				allTopics[tNumber].currStayTime = getStayTimeVal;
			    } else {
				// When it leaves we keep the offtime a fixed duration that is pretty small, we do this so that we have as large a group as possible. The offtime should just be sufficient enough that all neighbor timeouts occur
				allTopics[tNumber].currStayTime = OFFLINETIME;
			    }
			}
			
			
			if(allTopics[tNumber].isSubscribed) {
			    if(!(allTopics[tNumber].dataplaneClient.amMulticastSource || allTopics[tNumber].dataplaneClient.amVirtualSource)) {
				allTopics[tNumber].isSubscribed = false;
				allTopics[tNumber].dataplaneClient.leave();
			    }
				
			} else {
			    if((currtime - STARTTIME) > EXPERIMENTTIME) {
				System.out.println("Bypassing join() since the experiment duration finished"); 
			    } else {
			    
				allTopics[tNumber].isSubscribed = true;
				allTopics[tNumber].dataplaneClient.join();
			    }
			}
		    
		    
		    
		    } else {
			// Node continues to stay in the same state
		    }
		

		} else {
		    if(SaarTest.logLevel <= 875) myPrint("Node skipping switching code, initialrandomwaittime: " + INITIALRANDOMWAITTIME + " (currtime - boottime): " + (currtime - BOOTTIME), 875);
		}
	    }
	    
	}


	if(message instanceof DataplanemaintenanceContent) {
	    

	    // We do this for all topics
	    for(int tNumber=0; tNumber<NUMGROUPS; tNumber++) {
		if(dataplanesEnabled[tNumber] == 0) {
		    continue;
		}
	    
		if(allTopics[tNumber].isSubscribed) {
		    allTopics[tNumber].dataplaneClient.dataplaneMaintenance();
		    
		}

	    }
	}


	if(message instanceof ControlplaneupdateContent) {

	    lastControlPlaneUpdateTime = currtime;	    
	    
	    boolean atleastOneModuleSubscribed = false;
	    // We do this for all topics
	    for(int tNumber=0; tNumber<NUMGROUPS; tNumber++) {
		if(dataplanesEnabled[tNumber] == 0) {
		    continue;
		}	    
		if(allTopics[tNumber].isSubscribed) {
		    atleastOneModuleSubscribed = true;
		    allTopics[tNumber].dataplaneClient.controlplaneUpdate(false);
		    
		}
		
	    }
	    if(atleastOneModuleSubscribed) {
		viewer.printHybridStats();	   
	    } 
	    
	}



    }



    class SwitchingContent implements Message {
//	public void dump(ReplayBuffer buffer, PastryNode pn) {
//
//	}

	public int getPriority() {
	    return 0;
	}
    }

    class DataplanemaintenanceContent implements Message {
//	public void dump(ReplayBuffer buffer, PastryNode pn) {
//
//	}

	public int getPriority() {
	    return 0;
	}
    }

    class ControlplaneupdateContent implements Message {
//	public void dump(ReplayBuffer buffer, PastryNode pn) {
//
//	}

	public int getPriority() {
	    return 0;
	}
    }



}







  
    
    
    
