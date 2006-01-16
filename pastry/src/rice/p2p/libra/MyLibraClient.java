/*
 * Created on May 4, 2005
 */
package rice.p2p.libra;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.selector.SelectorManager;
import rice.replay.*;
import java.util.Random;
import java.util.Vector;
import java.util.Hashtable;
import java.lang.String;
import java.io.*;
import java.net.*;

import rice.p2p.util.MathUtils;
import java.text.*;

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



/**
 * We implement Application to receive regular timed messages (see lesson5).
 * We implement ScribeClient to receive scribe messages (called ScribeContent).
 * 
 * @author Animesh
 */
public class MyLibraClient implements ESMClient, ScribeClient, Application {

    Logger logger;

    Environment environment;

    SelectorManager selectorManager;

    // This is the index corresponding to the Planetlabnode or the Modelnet node based on the Ip address it is bound to
    public int bindIndex;

    // Pastry JVM index
    public int jvmIndex; 

    // Pastry Virtual node index
    public int vIndex;

    // These are thte total number of Anycast groups, however the total number of Scribe trees in the system is this multiplied by NUMTREES


    // WARNING : Since we use a single byte for streamId, we cannot use more than 128 groups ( 1 bit for sign). To test scalability we can test with NUMTREES of a higher value. 
    public static int NUMGROUPS = 6; // 50
    // The top half of the groups are used for regression tests, the rest are used by ESM
    public static int NUMTESTGROUPS = 0; // 50



    
    
    /****  These varibles are used for interaction between ScribeServer and ESM Client***/
    
    
    // This is a dummy ESM server port with which we experiment for the frist half of the groups. the esmServerPort is the actual port used when notified by the actual ESMclient communicating to the ScribeServer   
    


    // These fours denote the different states of a nodes in a Anycast tree, Depending on the node's state 
    // the appropriate refreshUpdate task is performed 
    public static final int  NONTREEMEMBER = 1;
    public static final int  INTERMEDIATESUBSCRIBER = 2;
    public static final int  LEAFSUBSCRIBER = 3;
    public static final int ONLYINTERMEDIATE = 4;





    // The subscribes take place in a periodic thread
    // In each subscribe we subscribe atmost SUBSCRIBEBURST
    public static int SUBSCRIBEBURST = 1;
    public static boolean SUBSCRIBEDONE = false;



    // The publishing takes place in a periodic thread, where each node publishes at most PUBLISHBURST times for the groups for which he is the root
    // WARNING: This value should be atleast 2, so that in each period we can publish to the virtual clock group and an additional group
    public static int PUBLISHBURST = 1;
    public static long PUBLISHTHREADPERIOD = 5000;
    public static long PUBLISHPERIOD  = 60000;


    public static long ALIVETHREADPERIOD  = 1000;

    public static long PINGALIVETHREADPERIOD  = 5000;




    // The updating takes place in a periodic thread, where each node publishes at most UPDATEBURST times for the groups for which he is subscribed
    public static int UPDATEBURST = 15; // The UPDATEBURST * (1000/UPDATETHREADPERIOD) should be greater than NUMGROUPS to test if fine grained update propagation is working
    public static long UPDATETHREADPERIOD = 100;
    public static long UPDATEPERIOD  = 5000; // updates each topic once in 1 sec with increasing values, this will help us check the accuracy of the update propagation in reflecting the correct value at the root


    public static long GNPTHREADPERIOD  = 60000; // every minute we update our GNP coordinates


    // This means that it will handle 25 * (1000/10) Scribe trees at max refreshupdates per second 
    public static int REFRESHUPDATEBURST = 25; // Changed from 5 on Oct 13
    public static long REFRESHUPDATETHREADPERIOD = 100;
    public static long REFRESHUPDATEPERIOD  = 1000; // 1000 ms


    public static long GRPMETADATAREQUESTTHREADPERIOD = 30000; 




    public static int CACHEROOTBURST = 5;
    public static long CACHEROOTTHREADPERIOD = 10000;
    public static long CACHEROOTPERIOD  = 60000;

    public static long LEAFSETTHREADPERIOD = 60000;


    // Every 2 minutes, the node randomly unsubscribes from a group and subscribes to another group 
    public static long SWITCHINGPERIOD = 300000; // 5 minutes


    // This period should be greater than the PUBLISHPERIOD, since we monitor things at the granularity of the virtual clock which is incremented at the PUBLISHPERIOD
    public static long CHECKPERIOD = 60000;

    // The sequence number is stored/updated on 5 replicas
    public static int NUMSEQREPLICAS = 5; 



    // These denote the thresholds of the fraction of the total planetlab nodes that will
    // be members of the largest and smallest group respectively
    public static double UPPERTHRESH = 0.5;
    public static double LOWERTHRESH = 0.5;

    // We associate each group with a probability, thereby more popular groups will have more members
    
    


    /**
     * Used to randomly accept an anycast.
     */
    Random rng = new Random();
    /**
       
    
    /**
    * This task kicks off publishing only, only one node publishes.
    * We hold it around in case we ever want to cancel the publishTask.
    */
    CancellableTask publishTask;

    CancellableTask aliveTask;

    CancellableTask pingAliveTask;
    
    
    // All nodes anycast
    CancellableTask anycastTask;
    
    CancellableTask subscribeTask;
    
    CancellableTask checkTask;
    
    CancellableTask switchingTask;
    
    // This is the periodic updation of metadata of child to its parent
    CancellableTask updateTask;
    
    CancellableTask refreshUpdateTask;

    CancellableTask downwardUpdateTask;

    CancellableTask grpMetadataRequestTask;

    CancellableTask gnpTask;

    CancellableTask leafsetTask;

    // This task caches/updates the current root handles, which helps in centralized algorithms
    CancellableTask cacheRootTask;



  
    /** 
     * My handle to a scribe impl.
     */
    Scribe myScribe;
    

    /**
     * The Endpoint represents the underlieing node.  By making calls on the 
     * Endpoint, it assures that the message will be delivered to a MyApp on whichever
     * node the message is intended for.
     */
    protected Endpoint endpoint;
    
    public PastryNode node;

    // These two counters wrap around the number of groups that this node is subscribed to
    // and do the anycast/publish task accordingly
    public int aTurn = -1; 
    public int pTurn = -1;
    public int sTurn = -1; // We subscribe 10 groups at a time to avoid sudden burst of messages
    public int uTurn = -1; // We update 2 groups at a time to avoid sudden burst of messages
    public int rTurn = -1; // We refresh update 2 groups at a time to avoid sudden burst of messages
    public int cTurn = -1; // We cache the current roots for the different topics
    public int gTurn = -1; // This requests the aggregate group metadata
    
    // We will create an array of topics, this is all the topics in the system 
    // Note : The node will be subscribe to a subset of them
    public TopicTaskState[] allTopics;

    // This is the virtual clock which is updated via the periodic broadcast on a group (TopicName_0) that every node belongs to
    public int virtualClock;

    // This is a server accepting messages to make calls to the Scribe layer, the ESM client will make calls to this server via the socket protocol on the local node. It calls the following methods on this MyScribeClient
    // invokeAnycast(streamId);
    // invokeSubscribe(streamId);
    // invokeUnsubscribe(streamId);
    // invokeUpdate(streamId,byte[] paramsLoad, byte[] paramsLoss, byte[] paramsPath);

    public DatagramSocket udpSocket = null;


   

    public int dummyesmServerPort = -1;
    public int dummyesmDatapathPort = -1;
    public byte[] dummyesmOverlayId = new byte[4];
    public byte dummyesmRunId = 0;



    // This Ip together with the esm port will be contacted when the anycast succeeds 
    public byte[] hostIp = new byte[4];

    public ESMScribePolicy esmScribePolicy;

    // This is set to true when the first periodic publish thread message is received. We wait for this
    // time to let the network stabilize
    public boolean initializePSeqNumDone = false;
    public long initializePSeqNumTime;

    // GNP Maintenance Policy - 
    // This is the last time we updated the GNP coordinate. The updation policy is
    //  1. Update if we get  a stable coordinate
    //  2. If unstable {
    //      a.  If the cached GNP is unstable update it
    //      b. If the cached GNP is stable and was got less than GNPCACHETIME, then do not update
    //      c. Actively remove GNP coordinates beyond GNPCACHETIME
    public long lastGNPSetTime = System.currentTimeMillis();
    // This is the maximum time we cache a GNP coordinate (stable or unstable). Also the updation 
    public long GNPCACHETIME = 600000; // 10 minutes
    public GNPCoordinate cachedGNPCoord = null;

    // This contains a mapping of the topic to the TopicName
    public Hashtable topic2TopicName;

    public boolean topicsInitialized = false;

    public String hostName; // This will be appended with the seq numbers for debugging

    // This keeps a registry of the esmRegistration information on a group basis
    public Hashtable esmRegistry ;

    // This updates the latest information it knows about the group
    public Hashtable grpMetadata ;


    // This is set using what is read in using the NAMETOIPCODEDFILE in SocketPastryNode and is used for efficient serialization. 
    public int ownPLIndex = -1;

    
    public boolean isBootstrapHost = false;

    public int fluctuateTotalSlots = 0; // This will be used to check the granularity of the update propagation algorithm, with the bootstrapnode fluctuating the totalslots between [0,50]. The actual totalSlots is (1 + fluctuateTotalSlots)



    // We have a datastructure that keeps a mapping from topicNumber -> centralizedNode, we read this file LibraTest.CENTRALIZEDNODEFILE. This list contains 5 centralized nodes ( thereby simulating a maximum of 5 different channels, since all topics for a channel should be located in the same centralized node, Also we can have a maximum of 10 stripes per channel) 
    
    public static final int MAXCENTRALIZEDNODES = 5;

    public String[] centralizedNodes = new String[MAXCENTRALIZEDNODES];
    public NodeHandle[] centralizedNh = new NodeHandle[MAXCENTRALIZEDNODES];
    public InetAddress[] centralizedIpAddr = new InetAddress[MAXCENTRALIZEDNODES];
    public PastryNodeFactory factory;

    public SocketAddress udplibraserverSentToAddress;
    public InetAddress udplibraserverInetAddr;

    private InetAddress localAddress; // .getHostAddress() will tell us the modelnet ip
    String hostAddress = "NONE";


    public long STARTTIME = 0; // this is the start time of this local node, it uses this time to emulate session time

    // This records the number of anycast requests it served in the timeslot currtime in seconds
    public static Hashtable anycastRecv = new Hashtable();



    public class GroupMetadata {
	public long updateTime;
	public ESMContent metadata;

	public GroupMetadata(long updateTime, ESMContent metadata) {
	    this.updateTime = updateTime;
	    this.metadata = metadata;
	}

    }


    public class ESMRegistryState {
	public int esmServerPort = -1;
	public int esmDatapathPort = -1;
	public byte[] esmOverlayId = new byte[4];
	public int esmStreamId;
	public byte esmRunId;
	

	public ESMRegistryState(int esmStreamId, byte[] esmOverlayId, int esmServerPort, int esmDatapathPort, byte esmRunId) {
	    this.esmStreamId = esmStreamId;
	    this.esmServerPort = esmServerPort;
	    this.esmDatapathPort = esmDatapathPort;
	    for(int i=0; i<4; i++) {
		this.esmOverlayId[i] = esmOverlayId[i];
	    }
	    this.esmRunId = esmRunId;
	}

	public String toString() {
	    String s = "ESMRegistryState: ";
	    s = s + "esmStreamId: " + esmStreamId + ", esmServerPort: " + esmServerPort + ", esmDataPathPort: " + esmDatapathPort + ", esmRunId: " + esmRunId + ", esmOverlayId: ";
	    for(int i=0; i< 4; i++) {
		s = s + esmOverlayId[i] + ".";
	    }
	    return s;
	}
    }

    
    public class TopicTaskState {
	public String topicName;
	public Topic topic;
	// This probability will determine the relative size of this group, for instance if it is 0.5 then this group will consist of half of the planetlab nodes, if it is 0.1, it will consists of approx 1/10th of the planetlab nodes
	public double prob; 
	// This is incremented each time it broadcasts
	// a message on this group, only the replica set around the root for the topic keep this value updated and the current publishes and increments this value
	public int pSeqNum ; 


	// This is the last time (local clock) that this node published to this group
	public long pTime;

	// This is the last time a dummy update was invoked
	public long dUTime;

	// This is the last time in ms (local clock) that this node was given a chance to update ( whether he actually updated or not is a different issue altogether) to this group
	public long uTime;

	// This was the node that was root for the topic, this information is updated using a periodic thread
	public NodeHandle cachedRoot;

	// This is the last time a message to fetch the current root was sent
	public long cTime;

	// This number is completely local and helps the local node to detect if its anycast failed or succeeded
	public int aSeqNum ; 


	// This number is completely local and helps the local node to debug details of anycast message for group metadata
	public int gSeqNum ; 


	// This is true if the node is subscribed to this topic
	public boolean isSubscribed;

	// This is the last sequence number that this node received on the periodic broadcast to this group, the sequence number helps to track the current group size when we later parse the logs
	public int lastRecvSeq;

	// This is the last time the node received a broadcast on a group, this helps in detecting tree
	// disconnections
	public long lastRecvTime;



	public TopicTaskState(Topic t, String name){
	    topic = t;
	    topicName = name;
	    pSeqNum = 0;
	    aSeqNum = 0;
	    gSeqNum = 0;
	    isSubscribed = false;
	    prob = 0.0;
	    lastRecvSeq = 0;
	    pTime = System.currentTimeMillis();
	    dUTime = System.currentTimeMillis();
	    uTime = System.currentTimeMillis();
	    lastRecvTime = System.currentTimeMillis();
	    cachedRoot = null;
	    cTime = System.currentTimeMillis();
	    myPrint(name + " " + t, 850);	    
	}



	public void setLastRecvSeq(int val) {
	    lastRecvSeq = val;
	}

	public void setLastRecvTime(long val) {
	    lastRecvTime = val;
	}

	public void setProb(double val) {
	    prob = val;
	}


	public void setPTime(long val) {
	    pTime = val;
	}

	public void setDUTime(long val) {
	    dUTime = val;
	}

	public void setUTime(long val) {
	    uTime = val;
	}


	public void setPSeqNum(int val) {
	    pSeqNum = val;
	}


	public void setASeqNum(int val) {
	    aSeqNum = val;
	}


	public void setGSeqNum(int val) {
	    gSeqNum = val;
	}



	public void setSubscribed(boolean val) {
	    isSubscribed = val;
	}


	public void setCachedRoot(NodeHandle handle) {
	    cachedRoot = handle;
	}

	public void setCTime(long val) {
	    cTime = val;
	}


    }
    
    
    /**
     * @param node the PastryNode
     */
    public MyLibraClient(int bindIndex, int jvmIndex, int vIndex, PastryNode node, DatagramSocket udpSocket, UDPLibraServer udpLibraServer, PastryNodeFactory factory ) {

	this.STARTTIME = System.currentTimeMillis();
	try {
	    this.factory = factory;
	    this.environment = node.getEnvironment();
	    localAddress = environment.getParameters().getInetAddress("socket_bindAddress");
	    this.selectorManager = environment.getSelectorManager();
	    logger = environment.getLogManager().getLogger(MyLibraClient.class,null);
	    this.node = node;
	    if(LibraTest.logLevel <= 875) myPrint("LocalAddress= " + localAddress, 875);
	    if(LibraTest.logLevel <= 875) myPrint("Passed (bindIndex,jvmIndex,vIndex) from LibraTest= (" + bindIndex + "," + jvmIndex + "," + vIndex + ")" , 875);
	    this.bindIndex = bindIndex;
	    this.jvmIndex = jvmIndex;
	    this.vIndex = vIndex;
	    this.udpSocket  = udpSocket;
	    udpLibraServer.registerApp(vIndex,this);
	    // you should recognize this from lesson 3
	    this.endpoint = node.registerApplication(this, "myinstance");

	    InetAddress addr = localAddress;
	    // Get the Ip
	    hostAddress = addr.getHostAddress();	
	    if(LibraTest.logLevel <= 875) myPrint("HostAddress= " + hostAddress, 875);
	    hostIp = addr.getAddress();
	    hostName = addr.getHostName();
	    if(LibraTest.logLevel <= 875) myPrint("HostName= " + hostName, 875);
	    if(LibraTest.logLevel <= 875) myPrint("hostIp: " + hostIp[0] + "." + hostIp[1] + "." + hostIp[2] + "." + hostIp[3], 875);


	} catch(Exception e) {
	    System.out.println("ERROR: Trying to get localhost ipaddress " + e);
	    System.exit(1);
	}
	// construct Scribe
	myScribe = new ScribeImpl(node, "lesson6instance");
	esmScribePolicy = new ESMScribePolicy(myScribe,this);
	myScribe.setPolicy(esmScribePolicy);
	topic2TopicName = new Hashtable();
	esmRegistry = new Hashtable();
	grpMetadata = new Hashtable();
	allTopics = new TopicTaskState[NUMGROUPS*LibraTest.NUMTREES];

	if((vIndex==0) && LibraTest.BOOTSTRAP_HOST.startsWith(hostName)) {
	    isBootstrapHost = true;
	}	
	
	ownPLIndex = ((rice.pastry.socket.SocketPastryNode)node).getPLIndexByIp(hostAddress);
	if(ownPLIndex > 0) {
	    if(LibraTest.logLevel <= 875) myPrint("OwnPLIndex= " + ownPLIndex, 875);
	    // It overrides an uninitialized bindIndex
	    if (this.bindIndex == -1) {
		this.bindIndex = ownPLIndex;
		if(LibraTest.logLevel <= 875) myPrint("Note: Resetting bindIndex using ownPLIndex to " + this.bindIndex, Logger.WARNING);
	    }
	} else {
	    if(LibraTest.logLevel <= Logger.WARNING) myPrint("WARNING: ownPLIndex=-1 while determining ownPLIndex with ownIpString:" + hostAddress, Logger.WARNING);
	}
    
	// Setting the bindAddress
	
	//if(!LibraTest.BINDADDRESS.equals("DEFAULT")) {
	//udplibraserverInetAddr = InetAddress.getByName(LibraTest.BINDADDRESS);
	//} else {
	//udplibraserverInetAddr = InetAddress.getByName("localhost");
	//}
	udplibraserverInetAddr = LibraTest.localAddress;
	udplibraserverSentToAddress = new InetSocketAddress(udplibraserverInetAddr,LibraTest.SCRIBESERVERPORT);
	
	if(LibraTest.logLevel <= 875) myPrint("udplibraserverSentToAddress: " + udplibraserverSentToAddress, 875);
	



	/*  Centralized node we have and the corresponding topic Range they serve
	    0-9   ricepl-3.cs.rice.edu
	    10-19 planetlab4.millennium.berkeley.edu
	    20-29 planet1.scs.cs.nyu.edu
	    30-39 planetlab-02.ipv6.lip6.fr
	    40-49 planetlab01.mpi-sws.mpg.de
	*/
	centralizedNodes[0] = "ricepl-3.cs.rice.edu";
	centralizedNodes[1] = "planetlab4.millennium.berkeley.edu";
	centralizedNodes[2] = "planet1.scs.cs.nyu.edu";
	centralizedNodes[3] = "planetlab-02.ipv6.lip6.fr";
	centralizedNodes[4] = "planetlab01.mpi-sws.mpg.de";

	for(int i=0; i<MAXCENTRALIZEDNODES; i++) {
	    InetSocketAddress centralizedAddress = new InetSocketAddress(centralizedNodes[i], LibraTest.centralizedNodePort);
	    centralizedNh[i] = ((SocketPastryNodeFactory)factory).generateNodeHandle(centralizedAddress);
	    if(LibraTest.logLevel <= 875) myPrint("centralizedAddress: " + centralizedAddress + ", centralizedNh: " + centralizedNh[i], 875);

	    if(centralizedNh[i] != null) {
		// This step makes sure we have a single instance of a NodeHandle per remote node	    
		centralizedNh[i] = (SocketNodeHandle) node.getLocalNodeI((rice.pastry.LocalNodeI)centralizedNh[i]);
		((SocketNodeHandle)centralizedNh[i]).setLocalNode(node);
	    }
	    try {
		centralizedIpAddr[i] = InetAddress.getByName(centralizedNodes[i]);
		//this.sentToAddress = new InetSocketAddress(nodeName,LibraTest.SCRIBESERVERPORT);
	    } catch(UnknownHostException e) {
		if(LibraTest.logLevel <= Logger.WARNING) myPrint("ERROR: Trying to get ipaddress for : " + centralizedNodes[i], Logger.WARNING);
		System.exit(1);
	    }
	}

	
	
	setGroupParams();
	topicsInitialized = true;
	queryGNP();
	
	 if(LibraTest.logLevel <= 875) myPrint("App[" + vIndex + "] is ready " +  endpoint.getLocalNodeHandle(), 875);
	 
	boolean dummyRegisterSuccess = false;
	while(!dummyRegisterSuccess) {
	    dummyRegisterSuccess = reqDummyRegister();
	}
	startGNPTask();
	//startPublishTask(); 
	//startCheckTask();
	startSubscribeTask();
	//startDownwardUpdateTask();
	startAnycastTask();
	
	
	//if(isBootstrapHost) {
	//  startGrpMetadataRequestTask(); // This periodically queries the aggregate group metadata
	//}
	


	//startSwitchingTask();
	// In this case we rely on the load monitoring to do the updates
	if(!LibraTest.VARYINGANYCASTRATE) {
	    startDummyUpdateTask();
	}
	startRefreshUpdateTask();
	//startCacheRootTask();
	startAliveTask(); // This just prints something to the log to ensure that the scheduling delays in Planetlab do not affect the anycast delays
	//startPingAliveTask();
	startLeafsetTask();
    }


    public void myPrint(String s, int priority) {
	logger.log(priority, s);
    }
    
    
    
    // This sets the prob values so that the approximate group sizes is set
    public void setGroupParams() {
	int routingBase = node.getRoutingTable().baseBitLength();
	for(int i=0; i<NUMGROUPS; i++) {
	    String baseTopicName = "" + i;
	    Topic baseTopic = new Topic(new PastryIdFactory(node.getEnvironment()), baseTopicName);
	    rice.pastry.Id baseTopicId = (rice.pastry.Id) baseTopic.getId();
	    // The redundant trees are scattered over the Id space
	    for(int k=0; k < LibraTest.NUMTREES; k++) {
		int tNumber = i + k*NUMGROUPS;
		String topicName = "" + tNumber;
		//int numDigits = Id.numDigits(routingBase);
		//int gap = Math.pow(2,routingBase)/LibraTest.NUMTREES;
		//int shiftFactor = gap * k;
		// This is constructed from base topic and ( MSBDigit = (MSB digit + shiftFactor) % 16)
		Id myTopicId = baseTopicId.getAlternateId(LibraTest.NUMTREES, routingBase, k);
		Topic myTopic = new Topic(myTopicId);
		topic2TopicName.put(myTopic, new String(topicName));
		TopicTaskState tState= new TopicTaskState(myTopic, topicName);
		double val;

		//if((tNumber % NUMGROUPS)== 0) {
		    // Every node subscribes to this group
		//  val = 1.0;
		//} else 
		if((tNumber % NUMGROUPS)>=NUMTESTGROUPS) {
		    val = 0;
		} else {
		    //val = 1.0; // This is fine since now we make even a subscribe make an anycast and see if it is resolved by another node not himself
		       
		    if(isBootstrapHost) {
			val = 1.0; //1.0
		    } else {
			val = 1.0; 
		    }
			
		    //val = UPPERTHRESH - ((UPPERTHRESH - LOWERTHRESH)/ ((double) NUMTESTGROUPS))*i;
		}
		allTopics[tNumber] = tState;
		tState.setProb(val);
	    }
	}

    }

  
    /**
     * Starts the alive task.
     */
    public void startAliveTask() {
	aliveTask = endpoint.scheduleMessage(new AliveContent(), 60000, ALIVETHREADPERIOD);    
    }






    public void startLeafsetTask() {
	leafsetTask = endpoint.scheduleMessage(new LeafsetContent(), 60000, LEAFSETTHREADPERIOD);    
    }
 
    public void startPingAliveTask() {
	pingAliveTask = endpoint.scheduleMessage(new PingAliveContent(), 1000, PINGALIVETHREADPERIOD);    
    }
    

  
    /**
     * Starts the publish task.
     */
    public void startPublishTask() {
	publishTask = endpoint.scheduleMessage(new PublishContent(), 500000, PUBLISHTHREADPERIOD);    
    }
    

    // The starting time of the naycast task is startTime of PublishTask + 0.5 Period (period is same for publish/anycast)
    public void startAnycastTask() {
	anycastTask = endpoint.scheduleMessage(new AnycastContent(), 600000, (int) (LibraTest.ANYCASTPERIOD * 1000));    
    }

    public void startGrpMetadataRequestTask() {
	grpMetadataRequestTask = endpoint.scheduleMessage(new GrpMetadataRequestThreadContent(), 600000, GRPMETADATAREQUESTTHREADPERIOD);    
    }

    
    public void startSubscribeTask() {
	subscribeTask = endpoint.scheduleMessage(new SubscribeContent(), 300000, 1000); // 300 * 1000   
    }

    public void startCheckTask() {
	checkTask = endpoint.scheduleMessage(new CheckContent(), 180000, CHECKPERIOD);    
    }
  

    public void startSwitchingTask() {
	switchingTask = endpoint.scheduleMessage(new SwitchingContent(), 120000, SWITCHINGPERIOD);    
    }
  

     public void startDummyUpdateTask() {
	updateTask = endpoint.scheduleMessage(new UpdateContent(), 30000, UPDATETHREADPERIOD);    
    }
    
     public void startRefreshUpdateTask() {
	 refreshUpdateTask = endpoint.scheduleMessage(new RefreshUpdateContent(), 30000, REFRESHUPDATETHREADPERIOD);    
    }


    public void startDownwardUpdateTask() {
	downwardUpdateTask = endpoint.scheduleMessage(new DownwardUpdateContent(), 120000, LibraTest.DOWNWARDUPDATETHREADPERIOD * 1000);
	if(LibraTest.logLevel <= 875) myPrint("Scheduling downwardUpdateTask with INTERVAL: " + (LibraTest.DOWNWARDUPDATETHREADPERIOD * 1000), 875);
    }

    


    public void startCacheRootTask() {
	cacheRootTask = endpoint.scheduleMessage(new CacheRootContent(), 60000, CACHEROOTTHREADPERIOD);    
    }
    
    


    public void startGNPTask() {
	gnpTask = endpoint.scheduleMessage(new GNPContent(), 120000, GNPTHREADPERIOD);    
    }
    


    // Returns
    //   LEAFSUBSCRIBER - is leaf subscriber ( issubscribed= true and the getChildren() returns empty array)
    //       In this case we update from the metadata in MyLibraClient
    //   ONLYINTERMEDIATE - is intermediate node only ( isSubscribed= false and getChildren() retruns non empty array)
    //       We update from the metadata at the ESMScribePolicy
    //   INTERMEDIATESUBSCRIBER - is both intermediate and subscriber ( we have to perform the averaging using the data in
    //       ESMScribePolicy as well as the MyLibraClient
    //   NONTREEMEMBER - not a member of tree
    public int getTreeStatus(int tNumber) {
	Topic myTopic = allTopics[tNumber].topic;
	boolean isSubscribed = allTopics[tNumber].isSubscribed;
	NodeHandle[] children = myScribe.getChildren(myTopic);
	if((!isSubscribed) && (children.length == 0)) {
	    return NONTREEMEMBER;
	} else if(isSubscribed && (children.length > 0)) {
	    return INTERMEDIATESUBSCRIBER;
	} else if(isSubscribed && (children.length == 0)) {
	    return LEAFSUBSCRIBER;
	} else if(!isSubscribed && (children.length > 0)) {
	    return ONLYINTERMEDIATE;
	}
	return -1;

    }





    /***** Methods in interface Application ******/

    public boolean forward(RouteMessage message) {
	return true;
    }
    
    
    public void update(NodeHandle handle, boolean joined) {
    
    }
    

    public void deliver(Id id, Message message) {
	long currTime = System.currentTimeMillis(); 
	
	/*
	if (message instanceof PublishContent) {
	    if(!topicsInitialized) {
		return;
	    }
	    if(!initializePSeqNumDone) {
		initializePSeqNum();
		initializePSeqNumTime = currTime;
	    } else if((currTime - initializePSeqNumTime) < 60000){
		// We wait for some time to get updates on the PSeqNums
	    } else {
		sendMulticast();
	    }
	}
	*/

	if (message instanceof LeafsetContent){
	    // Print the leafset
	     if(LibraTest.logLevel <= 880) myPrint("SysTime: " + System.currentTimeMillis() + " LEAFSET: " + node.getLeafSet(), 880);
	    
	}


	if (message instanceof AliveContent){
	    long currtime = System.currentTimeMillis();
	    if(LibraTest.SESSIONTIME > 0) {
		if((currtime - STARTTIME) >= (LibraTest.SESSIONTIME * 1000)) {
		    if(LibraTest.logLevel <= 875) myPrint("SysTime: " + currtime + " Node killing itself after expiry of SESSIONTIME of " + LibraTest.SESSIONTIME + " seconds", 875);
		    System.exit(1);
		    
		}
	    }

	    if(LibraTest.logLevel <= 880) myPrint("SysTime: " + currtime + " ALIVE ", 880);
	    // Every second we also update the fluctuateTotalSlots
	    fluctuateTotalSlots = (fluctuateTotalSlots + 1) % 50;
	    long currtimeSlot = currTime/(LibraTest.MONITORLOADINTERVAL*1000);
	    Long mykey = new Long(currtimeSlot);
	    int val;
	    if(LibraTest.VARYINGANYCASTRATE) {
		if((currtime - STARTTIME) >= (LibraTest.MAXSESSIONTIME * 1000)) {
		    System.out.println("EXITING: Because of VARYINGANYCASTRATE and MAXSESSIONTIME");
		    System.exit(1);
		}
		if(!anycastRecv.containsKey(mykey)) {
		    underloadAction();
		}
	    }
		
	    

	    


	     /* The code below was just to check that the update propagation works in a fine granularity
	     // If we are the root we print the GrpTotalSlots every second
	     for(int i=0; i<(NUMGROUPS * LibraTest.NUMTREES); i++) {
		 //boolean isSubscribed = allTopics[i].isSubscribed;
		 //String topicName = allTopics[i].topicName;
		 Topic myTopic = allTopics[i].topic;
		 //long idleTime = currTime - allTopics[i].lastRecvTime;
		 NodeHandle[] childrenList = myScribe.getChildren(myTopic);
		 //NodeHandle scribeParent = myScribe.getParent(myTopic);
		 
		 if(isRoot(myTopic) && (childrenList.length > 0) && printTreeStats(i)) {
		     ESMContent esmContentRoot = null;
		     ESMContent esmContentLeaf = null;
		     ESMContent esmContentIntermediate = null;
		     if(esmScribePolicy.leafMetadata.containsKey(myTopic)) {
			 esmContentLeaf= (ESMContent) esmScribePolicy.leafMetadata.get(myTopic);		
		     }
		     ESMScribePolicy.ESMTopicManager manager = null;
		     if(esmScribePolicy.intermediateMetadata.containsKey(myTopic)) {
			 manager = (ESMScribePolicy.ESMTopicManager)esmScribePolicy.intermediateMetadata.get(myTopic);
			 manager.rebuildESMContent();
			 esmContentIntermediate = manager.esmContent;
		     }
		     esmContentRoot = esmScribePolicy.aggregate(esmContentLeaf,esmContentIntermediate);
		     int grpTotalSlots = -1;
		     if(esmContentRoot != null) {
			 grpTotalSlots = esmContentRoot.paramsLoad[1];
		     }
		     if(LibraTest.logLevel <= 875) myPrint(" GRPTOTALSLOTSATROOT for Topic[ " + i + " ]: " + myTopic + " TOTALSLOTS: " + grpTotalSlots , 875);		    
		     
		 }
	     }
	     */
	     
	}
	

	if (message instanceof PingAliveContent){
	     if(LibraTest.logLevel <= 875) myPrint("SysTime: " + System.currentTimeMillis() + " PINGALIVE ", 875);
	    // We will ask the UDPLibraServer to send a ping to the LSERVER ( amushk.cs.rice.edu)
	    
	    reqPingAlive();

	}

	
	if (message instanceof AnycastContent){
	    if((!topicsInitialized) || (NUMTESTGROUPS == 0)) {
		return;
	    }
	    sendAnycast();
	}
	
	if (message instanceof SubscribeContent){
	    if((!topicsInitialized) || (NUMTESTGROUPS ==0)) {
		return;
	    }
	    sendSubscribe();
	}
	
	if (message instanceof CheckContent){
	    if((!topicsInitialized) || (NUMTESTGROUPS == 0)) {
		return;
	    }
	    invariantChecking();
	}

	/*
	if (message instanceof SwitchingContent){
	    if(!topicsInitialized) {
		return;
	    }
	    switchStreams();
	}
	*/
	
	if (message instanceof UpdateContent){
	    if((!topicsInitialized) || (NUMTESTGROUPS == 0)) {
		return;
	    }
	    dummyUpdate();
	}
	
	if (message instanceof RefreshUpdateContent){
	    if(!topicsInitialized) {
		return;
	    }
	    refreshUpdate();
	}
	
	
	if (message instanceof DownwardUpdateContent){
	    if((!topicsInitialized) || (NUMTESTGROUPS == 0)) {
		return;
	    }
	    downwardUpdate();
	}

	if (message instanceof GrpMetadataRequestThreadContent){
	    if((!topicsInitialized) || (NUMTESTGROUPS == 0)) {
		return;
	    }
	    sendGrpMetadataRequest();
	}


	/*
	if (message instanceof CacheRootContent){
	    if(!topicsInitialized) {
		return;
	    }
	    cacheRoot();
	}
	*/
	


	if (message instanceof GNPContent){
	    queryGNP();
	}
	



	if(message instanceof MyAnycastAckMsg) {
	    if(!topicsInitialized) {
		return;
	    }
	    MyAnycastAckMsg  myMsg = (MyAnycastAckMsg)message;
	    myMsg.content.addToMsgPath(endpoint.getLocalNodeHandle(), bindIndex, jvmIndex, vIndex);
	    String topicName = myMsg.content.topicName;
	    int topicNumber = topicName2Number(topicName);
	    int seqNum = myMsg.content.seq;
	    Topic myTopic = myMsg.getTopic();
	    byte[] responderIp = myMsg.getResponderIp();
	    byte[] responderESMId = myMsg.getResponderESMId();
	    int responderESMPort = myMsg.getResponderESMPort(); // This is the datapth port
	    GNPCoordinate responderGNPCoord = myMsg.getResponderGNPCoord();
	    double gnpDist = Double.MAX_VALUE;
	    
	    if(cachedGNPCoord != null) {
	    gnpDist = cachedGNPCoord.distance(responderGNPCoord);
	    }
	    
	    MyScribeContent.NodeIndex[] traversedPath = myMsg.content.getMsgPath();
	    String pathString = pathAsString(traversedPath);
	    //if(printEnable(topicNumber)) {
	    //if(LibraTest.logLevel <= 875) myPrint("SysTime: " + System.currentTimeMillis() +  " ESMRunId: " + myMsg.content.getESMRunId() + " Node "+endpoint.getLocalNodeHandle()+" received ANYCASTACK for Topic[ " + topicName + " ] " + myTopic + " Seq= " + seqNum + " DETAILS( " + " Src= " + myMsg.getSource() + " ResponderIp= " + asString(responderIp) + " responderESMId= " + asString(responderESMId) + " responderESMDataPathPort= " + responderESMPort + " GNPDist= " + gnpDist + " ContentDetails: " + myMsg.content + " TraversedPathHops: " + (traversedPath.length -1) + " TraversedPathString: " + pathString +  " )", 875);
	    if(LibraTest.logLevel <= 875) myPrint("SysTime: " + System.currentTimeMillis() +  " Node received ANYCASTACK for Topic[ " + topicName + " ] " + myTopic + " DETAILS( " + " Src= " + myMsg.getSource() + " ContentDetails: " + myMsg.content + " TraversedPathHops: " + (traversedPath.length -1) + " TraversedPathString: " + pathString +  " )", 875);
	    

	    //}
	    
	    // Based on the topicNumber the request will be sent to the actual esm server or the dummy esm server
	    reqESMServer(topicNumber, seqNum, responderIp,responderESMId, responderESMPort, traversedPath.length, traversedPath);
	    
	}
	

	if(message instanceof MyGrpMetadataAckMsg) {
	    if(!topicsInitialized) {
		return;
	    }
	    MyGrpMetadataAckMsg  myMsg = (MyGrpMetadataAckMsg)message;
	    String topicName = myMsg.content.topicName;
	    int topicNumber = topicName2Number(topicName);
	    Topic myTopic = myMsg.getTopic();
	    ESMContent content = myMsg.content;
	    int seq = myMsg.seq;

	    
	    //if(printEnable(topicNumber)) {
	     if(LibraTest.logLevel <= 875) myPrint("SysTime: " + System.currentTimeMillis() + " Node "+endpoint.getLocalNodeHandle()+" received GRPMETADATAACK for Topic[ " + topicName + " ] " + myTopic + " Seq= " + seq + " DETAILS( " + " Src= " + myMsg.getSource() + " ContentDetails: " + myMsg.content + " )", 875);
	    //}
	    
	    // Based on the topicNumber the request will be sent to the actual esm server or the dummy esm server
	    reqESMServerGrpMetadataAck(topicNumber, seq, content);
	    
	}

	/*
	if(message instanceof RootHandleAckMsg) {
	    if(!topicsInitialized) {
		return;
	    }
	    RootHandleAckMsg  myMsg = (RootHandleAckMsg)message;
	    String topicName = myMsg.topicName;
	    int topicNumber = topicName2Number(topicName);
	    //System.out.println("Received RootHandleAck message from " + myMsg.getSource() + " for Topic[ " + topicName + " ]= " + myMsg.getTopic());
	    allTopics[topicNumber].setCachedRoot(myMsg.getSource());
	}
	
	

	if(message instanceof RequestRootHandleMsg) {
	    if(!topicsInitialized) {
		return;
	    }
	    RequestRootHandleMsg  myMsg = (RequestRootHandleMsg)message;
	    String topicName = myMsg.topicName;
	    NodeHandle requestor = myMsg.getSource();
	    Topic myTopic = myMsg.getTopic();
	//System.out.println("Received RequestRootHandle message from " + myMsg.getSource() + " for Topic[ " + topicName + " ]= " + myMsg.getTopic());
	    RootHandleAckMsg rootHandleAckMsg = new RootHandleAckMsg(topicName, myTopic, endpoint.getLocalNodeHandle());
	    endpoint.route(null, rootHandleAckMsg , requestor);
	    
	    
	}
	*/
	
	
	
	/*
	if(message instanceof PublishStateMsg) {
	    if(!topicsInitialized) {
		return;
	    }
	    PublishStateMsg  myMsg = (PublishStateMsg)message;
	    int topicNumber = myMsg.topicNumber;
	    int seqNum = myMsg.seqNum;
	    String topicName = myMsg.topicName;
	    //System.out.println("Received PSTATE message from " + myMsg.getSource() + " for Topic[ " + topicName + "] " + " Seq= " + seqNum + " topicNumber= " + topicNumber);
	    allTopics[topicNumber].setPSeqNum(seqNum);
	}
	
	if(message instanceof RequestPublishStateMsg) {
	    if(!topicsInitialized) {
		return;
	    }
	    RequestPublishStateMsg  myMsg = (RequestPublishStateMsg)message;
	    int topicNumber = myMsg.topicNumber;
	    String topicName = myMsg.topicName;
	    Topic myTopic = allTopics[topicNumber].topic;
	    int requestorSeqNum = myMsg.seqNum;
	    NodeHandle requestor = myMsg.getSource();
	    
	    //System.out.println("Received PSTATE message from " + myMsg.getSource() + " for Topic[ " + topicName + " ]= " + myMsg.getTopic() + " Seq= " + seqNum);
	    int mySeqNum  = allTopics[topicNumber].pSeqNum;
	    // If the requestor needs to be updated send him the updated PSTATE msg
	    // Note : This mechanism is used to update the newly joined node of the sequence number, so there is no chance that the requestorSeqNum is greater than mySeqNum
	    if(mySeqNum > requestorSeqNum) {
		PublishStateMsg pStateMsg = new PublishStateMsg(topicNumber, allTopics[topicNumber].topicName, mySeqNum, endpoint.getLocalNodeHandle());
		endpoint.route(null, pStateMsg , requestor);
	    }
	}
	*/
	
    }
    
    // In this the root of the anycast group propgates the aggregate group information down the tree
    public void downwardUpdate() {
	//System.out.println("downwardUpdate() called");
	for(int i=0; i<(LibraTest.NUMTREES*NUMGROUPS); i++) {
	    String topicName = allTopics[i].topicName;
	    Topic myTopic = allTopics[i].topic;
	    NodeHandle[] childrenList = myScribe.getChildren(myTopic);
	    if(isRoot(myTopic) && (getTreeStatus(i) != NONTREEMEMBER)) {
		// Publish the aggregate group metadata down the tree
		ESMContent esmContentRoot = null;
		ESMContent esmContentLeaf = null;
		ESMContent esmContentIntermediate = null;
		if(esmScribePolicy.leafMetadata.containsKey(myTopic)) {
		    esmContentLeaf= (ESMContent) esmScribePolicy.leafMetadata.get(myTopic);		
		}
		ESMScribePolicy.ESMTopicManager manager = null;
		if(esmScribePolicy.intermediateMetadata.containsKey(myTopic)) {
		    manager = (ESMScribePolicy.ESMTopicManager)esmScribePolicy.intermediateMetadata.get(myTopic);
		    manager.rebuildESMContent();
		    esmContentIntermediate = manager.esmContent;
		}
		esmContentRoot = esmScribePolicy.aggregate(esmContentLeaf,esmContentIntermediate);
		 if(LibraTest.logLevel <= 875) myPrint("SysTime: " + System.currentTimeMillis() + " Node "+endpoint.getLocalNodeHandle()+" initiating DOWNWARDUPDATE for Topic[ " + topicName + " ] " + myTopic + " esmContent: " + esmContentRoot, 875);		
		myScribe.publish(myTopic, esmContentRoot); 		
	    }

	}
	

    }


    // This checks to see if it is receiving broadcast messages on its subscribed trees 
    public void invariantChecking() {
	

	//System.out.println("invariantChecking()");
	long currTime = System.currentTimeMillis();

	

	/*
	for(int i=0; i<NUMGROUPS; i++) {
	    int policy = esmScribePolicy.getPolicy(i);
	    if((policy == ESMScribePolicy.CENTRALRANDOM) || (policy == ESMScribePolicy.CENTRALOPTIMAL) || (policy == ESMScribePolicy.ESMCENTRALRANDOM) || (policy == ESMScribePolicy.ESMCENTRALOPTIMAL)) {
		String topicName = allTopics[i].topicName;
		Topic myTopic = allTopics[i].topic;
		NodeHandle cachedRoot = allTopics[i].cachedRoot;
		//myPrint("SysTime: " + System.currentTimeMillis() + " CachedRoot: " + " Topic[ " + allTopics[i].topicName + " ] " + " cachedRoot= " + cachedRoot, 850);
	    }
	}
	System.out.println("");
	*/


	    
	// We will output data for tree visualizer
	for(int i=0; i<(NUMGROUPS * LibraTest.NUMTREES); i++) {
	    boolean isSubscribed = allTopics[i].isSubscribed;
	    String topicName = allTopics[i].topicName;
	    Topic myTopic = allTopics[i].topic;
	    long idleTime = currTime - allTopics[i].lastRecvTime;
	    NodeHandle[] childrenList = myScribe.getChildren(myTopic);
	    NodeHandle scribeParent = myScribe.getParent(myTopic);
	    boolean amRoot = false;
	    if(isRoot(myTopic)) {
		amRoot = true;
	    }
	    if((amRoot || (childrenList.length > 0) || (scribeParent != null) || isSubscribed) && printTreeStats(i)) {
		if(LibraTest.logLevel <= 875) myPrint(" TREESTATS: Topic[ " + i + " ] " + myTopic, 875);
		if(amRoot) {
		    ESMContent esmContentRoot = null;
		    ESMContent esmContentLeaf = null;
		    ESMContent esmContentIntermediate = null;
		    if(esmScribePolicy.leafMetadata.containsKey(myTopic)) {
			esmContentLeaf= (ESMContent) esmScribePolicy.leafMetadata.get(myTopic);		
		    }
		    ESMScribePolicy.ESMTopicManager manager = null;
		    if(esmScribePolicy.intermediateMetadata.containsKey(myTopic)) {
			manager = (ESMScribePolicy.ESMTopicManager)esmScribePolicy.intermediateMetadata.get(myTopic);
			manager.rebuildESMContent();
			esmContentIntermediate = manager.esmContent;
		    }
		    esmContentRoot = esmScribePolicy.aggregate(esmContentLeaf,esmContentIntermediate);
		    int descendants = -1;
		    if(esmContentRoot != null) {
			descendants = esmContentRoot.descendants;
		    }
		     if(LibraTest.logLevel <= 875) myPrint(" ROOT for Topic[ " + i + " ] " + myTopic + " : " + endpoint.getLocalNodeHandle() + " GRPSIZE: " + descendants , 875);		    
		    
		}
		if(isSubscribed) {
		     if(LibraTest.logLevel <= 875) myPrint(" Topic[ " + i + " ].SUBSCRIBER " + " IDLETIME: " + idleTime, 875);
		} 
		if(scribeParent != null) {
		     if(LibraTest.logLevel <= 875) myPrint(" Topic[ " + i + " ].PARENT: " + scribeParent, 875);
		}
		for(int j=0; j<childrenList.length; j++) {
		     if(LibraTest.logLevel <= 875) myPrint(" Topic[ " + i + " ].CHILD[ " + j + " ]: " + childrenList[j], 875);
		}
		 if(LibraTest.logLevel <= 875) myPrint("", 875);
	    }
	}


	
	/*
	for(int i=0; i<(NUMGROUPS*LibraTest.NUMTREES); i++) {
	    boolean isSubscribed = allTopics[i].isSubscribed;
	    if(isSubscribed) {
		String topicName = allTopics[i].topicName;
		Topic myTopic = allTopics[i].topic;
		NodeHandle scribeParent = myScribe.getParent(myTopic);
		NodeHandle cachedRoot = allTopics[i].cachedRoot;
		int policy = esmScribePolicy.getPolicy(i);
		if((policy == ESMScribePolicy.CENTRALRANDOM) || (policy == ESMScribePolicy.CENTRALOPTIMAL) || (policy == ESMScribePolicy.ESMCENTRALRANDOM) || (policy == ESMScribePolicy.ESMCENTRALOPTIMAL)) {
		    // In this case the parent should be the current root for the topic
		    if((scribeParent!= null) && (!scribeParent.equals(cachedRoot))) {
			 if(LibraTest.logLevel <= 875) myPrint("WARNING: In the centralized policy the subscriber's parent is not the current root", 875);
			 if(LibraTest.logLevel <= 875) myPrint("SysTime: " + System.currentTimeMillis() + " Node " + endpoint.getLocalNodeHandle() + " RESUBSCRIBING for Topic[ " + topicName + " ]= "+ myTopic, 875);
	 		myScribe.unsubscribe(myTopic, this);
			myScribe.subscribe(myTopic, this);

		    }
		    
		}
		
		
	    }
	}
	*/
	
	
    }



    // This operates over the dummy groups only
    public void dummyUpdate() {
	if(NUMTESTGROUPS == 0) {
	    return;
	}
	//myPrint("dummyUpdate() called", 850);
	int count = 0;
	// In each period we update to the subscribed groups
	int numLoops = 0;

	while((count < UPDATEBURST) && (numLoops <= NUMTESTGROUPS)) {
	    numLoops ++;
	    uTurn = (uTurn + 1) % NUMTESTGROUPS;
	    count = count + sendDummyUpdateForTopic(uTurn);
	}

    }

    public int sendDummyUpdateForTopic(int tNumber) {
	//myPrint("sendDummyUpdateForTopic(" + tNumber + ")", 850);
	long currTime = System.currentTimeMillis();

	//myPrint("timediff(" + (currTime - allTopics[tNumber].dUTime) + ")", 850);
	//if(allTopics[tNumber].isSubscribed) {
	if(allTopics[tNumber].isSubscribed && ((currTime - allTopics[tNumber].dUTime) > UPDATEPERIOD)) {
	    allTopics[tNumber].setDUTime(currTime);
	    reqUpdate(tNumber);
	    return 1;
	} else {
	    return 0;
	}
	
    }

    /*
    // This operates over all groups
    public void cacheRoot() {
      int count = 0;
      // In each period we update to the subscribed groups
      int numLoops = 0;
      while((count < CACHEROOTBURST) && (numLoops <= NUMGROUPS)) {
	  numLoops ++;
	  cTurn = (cTurn + 1) % NUMGROUPS;
	  count = count + cacheRootForTopic(cTurn);
      }
      
    }
    


    public int cacheRootForTopic(int tNumber) {
	int policy = esmScribePolicy.getPolicy(tNumber);
	if((policy == ESMScribePolicy.CENTRALRANDOM) || (policy == ESMScribePolicy.CENTRALOPTIMAL) || (policy == ESMScribePolicy.ESMCENTRALRANDOM) || (policy == ESMScribePolicy.ESMCENTRALOPTIMAL)) {
	    long currTime = System.currentTimeMillis();
	    if((currTime - allTopics[tNumber].cTime) > CACHEROOTPERIOD) {
		allTopics[tNumber].setCTime(currTime);
		String topicName = allTopics[tNumber].topicName;
		Topic myTopic = allTopics[tNumber].topic;
		RequestRootHandleMsg reqRootHandleMsg = new RequestRootHandleMsg(topicName, myTopic, endpoint.getLocalNodeHandle());
		//myPrint("SysTime: " + System.currentTimeMillis() +  " Node "+endpoint.getLocalNodeHandle()+" sending CACHEROOT for Topic[ "+ topicName + " ] " + myTopic, 850);
		// We provide the curent cached root as a hint

		if(endpoint.getLocalNodeHandle().equals(allTopics[tNumber].cachedRoot)) {
		    // There is probably a bug in Freepastry where if the message is given a hint as itself, the message is delivered inappropriately
		    endpoint.route(myTopic.getId(), reqRootHandleMsg , null);

		} else {
		    endpoint.route(myTopic.getId(), reqRootHandleMsg , allTopics[tNumber].cachedRoot);
		}
		return 1;
	    } else {
		return 0;
	    }
	} else {
	    return 0;
	}
	
    }
    */



    // This operates over all groups
    public void refreshUpdate() {
      int count = 0;
      // In each period we update to the subscribed groups
      int numLoops = 0;
      long currTime = System.currentTimeMillis();
      while((count < REFRESHUPDATEBURST) && (numLoops <= (NUMGROUPS*LibraTest.NUMTREES))) {
	    numLoops ++;
	    rTurn = (rTurn + 1) % (NUMGROUPS*LibraTest.NUMTREES);
	    // When using the periodic updates we use the @param forceUpdate field as false
	    count = count + sendRefreshUpdateForTopic(rTurn,false);
      }
      
    }


    // Based on the strategy it will either send a single update or will emply piggybacking
    // @param forceUpdate : when true, it send an update without checking for the lastRefresh time
    public int sendRefreshUpdateForTopic(int tNumber, boolean forceUpdate) {
	int ret;
	if(LibraTest.UPDATEPIGGYBACKING) {
	    ret = piggybackSendRefreshUpdateForTopic(tNumber, forceUpdate);
	} else {
	    ret = singleSendRefreshUpdateForTopic(tNumber, forceUpdate);
	}
	return ret;
	
    }
    

    
    // We take the opportunity to piggy back information of other groups on this group as long as the total number of groups piggy backed is less than a threshold PIGGYBACKUPDATETHRESHOLD. Additionally, we do a round robbin on all groups since doing a round robin on parents is not effective since parents change. When we round robbin on groups, we update the last refresh period on all groups that were piggy backed with this group
    public int piggybackSendRefreshUpdateForTopic(int tNumber, boolean forceUpdate) {
	if(!topicsInitialized) {
	    return 0;
	}
	
	long currTime = System.currentTimeMillis();
	// We put the period rate check here because sendRefreshUpdateForTopic() has 2 callpaths : refreshUpdate() , invokeUpdate()
	if((!((currTime - allTopics[tNumber].uTime) > REFRESHUPDATEPERIOD)) && (!forceUpdate)) {
	    return 0;
	}



	Topic myTopic = allTopics[tNumber].topic;
	String topicName = allTopics[tNumber].topicName;
	NodeHandle parent = myScribe.getParent(myTopic);


	if(parent == null) {
	    int checkTreeStatus = getTreeStatus(tNumber);
	    if((checkTreeStatus != NONTREEMEMBER) && !isRoot(myTopic)) {
		if(LibraTest.logLevel <= 875) myPrint("WARNING: piggybackSendRefreshUpdateForTopic( " + myTopic + ", " + tNumber + " , " + forceUpdate + ") has null parent inspite of treestatus: " + checkTreeStatus , 875);
		
	    }
	    return 0;
	}

	if(LibraTest.logLevel <= 850) myPrint("piggybackSendRefreshUpdateForTopic( " + myTopic + ", " + tNumber + " , " + forceUpdate + ", " + parent + " )", 850);

	// We will now get the different topics for which this parent holds, and shrink this list to maintain the PIOGGYBACK THRESHOLD
	Topic[] aggregateTopicsArray = myScribe.topicsAsParent(parent);
	Vector aggregateTopics = new Vector();
	for(int i=0; i< aggregateTopicsArray.length; i++) {
	    aggregateTopics.add(aggregateTopicsArray[i]);
	}
	// We ensure that while shrinking, we maintain the invariant that this particular topic corresponding to the function parameter 'tNumber' is contained. Since we shrink from the end, we make sure that this topic is present at the head
	aggregateTopics.remove(myTopic);
	aggregateTopics.add(0, myTopic);

	// We will now build the AggregateESMContent, while adding only the updates that need to be propagated and also ensuring a max of PIGGYBACKUPDATETHRESHOLD
	AggregateESMContent aggregateContent = new AggregateESMContent();
	for(int i=0; i< aggregateTopics.size();i++) {
	    Topic aggregTopic = (Topic) aggregateTopics.elementAt(i);
	    if(!parent.equals(myScribe.getParent(aggregTopic))) {
		if(LibraTest.logLevel <= Logger.WARNING) myPrint("WARNING: Inconsistency in allParents data structure in Scribe", Logger.WARNING);
		if(LibraTest.logLevel <= Logger.WARNING) myPrint("topic: " + aggregTopic + ", parent: " + parent + ", aggregParent: " + myScribe.getParent(aggregTopic), Logger.WARNING);

		if(LibraTest.logLevel <= Logger.WARNING) myPrint("ERROR: We will print the allParents data structure", Logger.WARNING);
		((ScribeImpl)myScribe).printAllParentsDataStructure();
		
		System.exit(1);
	    }

	    String aggregTName = topic2TopicName(aggregTopic);
	    int aggregTNumber = topicName2Number(aggregTName);

	    // Note : We have added the actual desired topic in the front, However we do also piggyback the other topics on this message but only if they did not violate the refreshTimeCheck
	    if((!((currTime - allTopics[aggregTNumber].uTime) > REFRESHUPDATEPERIOD)) && !(forceUpdate && (tNumber == aggregTNumber))) {
		// We need not aggregate this topic
		continue;
	    }

	    

	    
	    // This means that a chance was given
	    allTopics[aggregTNumber].setUTime(System.currentTimeMillis());
	
	    int treeStatus = getTreeStatus(aggregTNumber);
	    
	    boolean needsUpdate = false;
	    // This could be null
	    ESMContent esmContentPrev = null;
	    ESMContent esmContentNew = null;
	    ESMContent esmContentUpdate = null;	
	    
	    if(esmScribePolicy.prevMetadata.containsKey(aggregTopic)) {
		esmContentPrev= (ESMContent) esmScribePolicy.prevMetadata.get(aggregTopic);
	    }
       
	    if(treeStatus == NONTREEMEMBER) {
		// We do nothing
	    } else if(treeStatus == LEAFSUBSCRIBER) {
		if(esmScribePolicy.leafMetadata.containsKey(aggregTopic)) {
		    esmContentNew= (ESMContent) esmScribePolicy.leafMetadata.get(aggregTopic);		
		}
	    } else if(treeStatus == INTERMEDIATESUBSCRIBER) {
		ESMContent esmContentLeaf = null;
		ESMContent esmContentIntermediate = null;
		if(esmScribePolicy.leafMetadata.containsKey(aggregTopic)) {
		    esmContentLeaf= (ESMContent) esmScribePolicy.leafMetadata.get(aggregTopic);		
		}
		ESMScribePolicy.ESMTopicManager manager = null;
		if(esmScribePolicy.intermediateMetadata.containsKey(aggregTopic)) {
		    manager = (ESMScribePolicy.ESMTopicManager)esmScribePolicy.intermediateMetadata.get(aggregTopic);
		    manager.rebuildESMContent();
		    esmContentIntermediate = manager.esmContent;
		}
		esmContentNew = esmScribePolicy.aggregate(esmContentLeaf,esmContentIntermediate);
	    } else if(treeStatus == ONLYINTERMEDIATE) {
		ESMContent esmContentIntermediate = null;
		ESMScribePolicy.ESMTopicManager manager = null;
		if(esmScribePolicy.intermediateMetadata.containsKey(aggregTopic)) {
		    manager = (ESMScribePolicy.ESMTopicManager)esmScribePolicy.intermediateMetadata.get(aggregTopic);
		    manager.rebuildESMContent();
		    esmContentIntermediate = manager.esmContent;
		}
		esmContentNew = esmScribePolicy.aggregate(null,esmContentIntermediate);
	    }

	    if(esmContentNew == null) {
		needsUpdate = false;
	    } else if(esmContentPrev == null) {
		// We have to propagate
		needsUpdate = true;
	    } else {
		// We we DISABLE UpdateAcks, we just check for changes in ESMContent 
		//if(parent.equals(esmContentPrev.lastRefreshParent) && esmContentNew.negligibleChange(esmContentPrev) && (esmContentPrev.lastRefreshTime < esmContentPrev.lastUpdateAckTime)) {
		if(parent.equals(esmContentPrev.lastRefreshParent) && esmContentNew.negligibleChange(esmContentPrev)) {
		    
		    // The parent acknowledged receiving the most recent info
		    needsUpdate = false ;
		    //System.out.println("Most updated data already reflected at parent");
		} else {
		    needsUpdate = true;
		}
	    }
	    if(needsUpdate) {
		// We will send the update
		esmContentUpdate = new ESMContent(esmContentNew);
		if(esmContentUpdate == null) {
		    if(LibraTest.logLevel <= Logger.WARNING) myPrint("WARNING: esmContentUpdate is null, esmContentNew= " + esmContentNew, Logger.WARNING);
		} else {
		    esmContentUpdate.setLastRefreshParent(parent);
		    esmContentUpdate.setLastRefreshTime(System.currentTimeMillis());
		    esmScribePolicy.prevMetadata.put(aggregTopic,esmContentUpdate);
		    if(printEnable(aggregTNumber)) {
			if (LibraTest.logLevel <= 850) myPrint("Node AGGREGATEUPDATING for Topic[ " + aggregTName + " ]= "+ aggregTopic + "esmcontent= " + esmContentUpdate + " to parent= " + myScribe.getParent(aggregTopic), 850);
		    }
		    aggregateContent.appendUpdate(aggregTopic, esmContentUpdate);

		    // We do the check after adding the content becuase we have already updated the lastRefresh time etc assuming that the updated would be aggregated
		    if(aggregateContent.getNumUpdates() >= LibraTest.PIGGYBACKUPDATETHRESHOLD) {
			break;
		    } 
		    
		}
	       
	    } else {
		// We not not do anything
	    }
	
	}
	if(aggregateContent.getNumUpdates() > 0) {
	    // We use myTopic here but could have used any of the topics in aggregateContent, since the Scribe layer uses this to decide whom to send the update to
	    if (LibraTest.logLevel <= 850) myPrint("SysTime: " + System.currentTimeMillis() + " piggybackSendRefreshUpdateForTopic( " + tNumber + ", " + aggregateContent + " )", 850);
	    myScribe.sendUpdate(myTopic,aggregateContent);
	    return 1;
	} else {
	    return 0;
	}

    }

    






    // This is code to do a different update per group without aggregation
    public int singleSendRefreshUpdateForTopic(int tNumber, boolean forceUpdate) {
	if(!topicsInitialized) {
	    return 0;
	}

	long currTime = System.currentTimeMillis();
	// We put the period rate check here because sendRefreshUpdateForTopic() has 2 callpaths : refreshUpdate() , invokeUpdate(). Additionally we have an option of forcing the update
	if((!((currTime - allTopics[tNumber].uTime) > REFRESHUPDATEPERIOD)) && (!forceUpdate)) {
	    return 0;
	}


	Topic myTopic = allTopics[tNumber].topic;
	String topicName = allTopics[tNumber].topicName;
	NodeHandle parent = myScribe.getParent(myTopic);
	// This means that a chance was given
	allTopics[tNumber].setUTime(System.currentTimeMillis());
	
	int treeStatus = getTreeStatus(tNumber);

	boolean needsUpdate = false;
	// This could be null
	ESMContent esmContentPrev = null;
	ESMContent esmContentNew = null;
	ESMContent esmContentUpdate = null;	

	if(esmScribePolicy.prevMetadata.containsKey(myTopic)) {
	    esmContentPrev= (ESMContent) esmScribePolicy.prevMetadata.get(myTopic);
	}
	

	if(parent == null) {
	    return 0;
	} else if(treeStatus == NONTREEMEMBER) {
	    return 0;
	} else if(treeStatus == LEAFSUBSCRIBER) {
	    if(esmScribePolicy.leafMetadata.containsKey(myTopic)) {
		esmContentNew= (ESMContent) esmScribePolicy.leafMetadata.get(myTopic);		
	    }
	} else if(treeStatus == INTERMEDIATESUBSCRIBER) {
	    ESMContent esmContentLeaf = null;
	    ESMContent esmContentIntermediate = null;
	    if(esmScribePolicy.leafMetadata.containsKey(myTopic)) {
		esmContentLeaf= (ESMContent) esmScribePolicy.leafMetadata.get(myTopic);		
	    }
	    ESMScribePolicy.ESMTopicManager manager = null;
	    if(esmScribePolicy.intermediateMetadata.containsKey(myTopic)) {
		manager = (ESMScribePolicy.ESMTopicManager)esmScribePolicy.intermediateMetadata.get(myTopic);
		manager.rebuildESMContent();
		esmContentIntermediate = manager.esmContent;
	    }
	    esmContentNew = esmScribePolicy.aggregate(esmContentLeaf,esmContentIntermediate);
	} else if(treeStatus == ONLYINTERMEDIATE) {
	    ESMContent esmContentIntermediate = null;
	    ESMScribePolicy.ESMTopicManager manager = null;
	    if(esmScribePolicy.intermediateMetadata.containsKey(myTopic)) {
		manager = (ESMScribePolicy.ESMTopicManager)esmScribePolicy.intermediateMetadata.get(myTopic);
		manager.rebuildESMContent();
		esmContentIntermediate = manager.esmContent;
	    }
	    esmContentNew = esmScribePolicy.aggregate(null,esmContentIntermediate);
	}

	if(esmContentNew == null) {
	    needsUpdate = false;
	} else if(esmContentPrev == null) {
	    // We have to propagate
	    needsUpdate = true;
	} else {
	    // We we DISABLE UpdateAcks, we just check for changes in ESMContent 
	    //if(parent.equals(esmContentPrev.lastRefreshParent) && esmContentNew.negligibleChange(esmContentPrev) && (esmContentPrev.lastRefreshTime < esmContentPrev.lastUpdateAckTime)) {
	    if(parent.equals(esmContentPrev.lastRefreshParent) && esmContentNew.negligibleChange(esmContentPrev)) {

		// The parent acknowledged receiving the most recent info
		needsUpdate = false ;
		//System.out.println("Most updated data already reflected at parent");
	    } else {
		needsUpdate = true;
	    }
	}
	if(needsUpdate) {
	    // We will send the update
	    esmContentUpdate = new ESMContent(esmContentNew);
	    if(esmContentUpdate == null) {
		 if(LibraTest.logLevel <= Logger.WARNING) myPrint("WARNING: esmContentUpdate is null, esmContentNew= " + esmContentNew, Logger.WARNING);
	    } else {
		esmContentUpdate.setLastRefreshParent(parent);
		esmContentUpdate.setLastRefreshTime(System.currentTimeMillis());
		esmScribePolicy.prevMetadata.put(myTopic,esmContentUpdate);
		if(printEnable(tNumber)) {
		    if (LibraTest.logLevel <= 875) myPrint("Node UPDATING for Topic[ " + topicName + " ]= "+ myTopic + "esmcontent= " + esmContentUpdate + " to parent= " + myScribe.getParent(myTopic), 875);
		}

		
		myScribe.sendUpdate(myTopic,esmContentUpdate);
	    }
	    return 1;
	} else {
	    return 0;
	}
	
    }
    








      /*
    // Note : We will not subcribe/unsubscribe from Stream 0, which is the virtual clock group
    public void switchStreams() {
	//System.out.println("switchStreams()");
	if(SUBSCRIBEDONE) { 
	    Vector subscribedGrps = new Vector();
	    Vector unsubscribedGrps = new Vector();

	    for(int i=1; i<NUMTESTGROUPS; i++) {
		boolean isSubscribed = allTopics[i].isSubscribed;
		if(isSubscribed) {
		    subscribedGrps.add(new Integer(i));
		} else {
		    unsubscribedGrps.add(new Integer(i));
		}
	    }
	    TopicTaskState tState;
	    Topic myTopic;
	    int rngIndex;
	    String myTopicName;

	    if(subscribedGrps.size() > 0) { 
		rngIndex = rng.nextInt(subscribedGrps.size());
		int toUnsubscribeFrom = ((Integer)subscribedGrps.elementAt(rngIndex)).intValue();
		reqUnsubscribe(toUnsubscribeFrom);
	    }

	    if(unsubscribedGrps.size() > 0) { 
		rngIndex = rng.nextInt(unsubscribedGrps.size());
		int toSubscribeTo = ((Integer)unsubscribedGrps.elementAt(rngIndex)).intValue();
		reqSubscribe(toSubscribeTo);
	    }
	}

    }
      */


    /**
   * Subscribes to a subset of NUMGROUPS using their probability values.
   */
    public void sendSubscribe() {
	//System.out.println("sendSubscribe(): sTurn: " + sTurn + " NUMTESTGROUPS: " + NUMTESTGROUPS);
	int count = 0; // In each turn we send SUBSCRIBE subscribes
	while((sTurn < (NUMTESTGROUPS-1)) && (count <SUBSCRIBEBURST)) {
	    sTurn = sTurn + 1;
	    System.out.println("sTurn: " + sTurn);
	    TopicTaskState tState = allTopics[sTurn];
	    String myTopicName = allTopics[sTurn].topicName;
	    Topic myTopic = tState.topic;
	    int randVal = rng.nextInt(100);
	    //System.out.println("randVal: " + randVal + " tstate.prob: " + tState.prob);
	    if(randVal < (100.0 * tState.prob)) {
		reqSubscribe(sTurn);
		count ++;
	    }
	}
	// If we have subscribed to all groups (sTurn == NUMGROUPS), we can cancel the periodic Subscribetask
	if(sTurn == (NUMTESTGROUPS -1)) {
	    subscribeTask.cancel();
	     if(LibraTest.logLevel <= 880) myPrint("SysTime: " + System.currentTimeMillis() + " SUBSCRIBING TO ALL DESIRED GROUPS IS OVER", 880);
	    SUBSCRIBEDONE = true;
	}
	
    }
    

    


  /**
   * Sends the multicast message.
   */
    /*
  public void sendMulticast() {
      int count = 0;
      // In each period we multicast to the virtual clock group if we are responsible for that

      // In each period we send to the virtual clock group if we are the root for that group. Thus note that the frequency of publishing to the virtual clock group is approximately NUMGROUPS times the frequency of publishing to the other groups
      int numLoops = 0;
      count = count + sendMulticastTopic(0);
      while((count < PUBLISHBURST) && (numLoops <= NUMTESTGROUPS)) {
	  numLoops ++;
	  pTurn = (pTurn + 1) % NUMTESTGROUPS;
	  if(pTurn == 0) {
	      // We have already published if required to the virtual clock group
	      pTurn++;
	  }
	  count = count + sendMulticastTopic(pTurn);
      }
    
  }

    // Returns 1 if we actually did multicast (i.e if we were root)
    public int sendMulticastTopic(int tNumber) {
	Topic myTopic = allTopics[tNumber].topic;
	long currTime = System.currentTimeMillis();
	if(isRoot(myTopic) && ((currTime - allTopics[tNumber].pTime) > PUBLISHPERIOD)) {
	    int currSeq = allTopics[tNumber].pSeqNum;
	    String myTopicName = allTopics[tNumber].topicName;

	    //System.out.println("MSC:"+System.currentTimeMillis()+" Node "+endpoint.getLocalNodeHandle()+" broadcasting for topic[ "+ tNumber + " ] " + myTopic + " Seq= " + currSeq);
	    //System.out.println("SysTime: " + System.currentTimeMillis() + " Node "+endpoint.getLocalNodeHandle()+" BROADCASTING for Topic[ "+ myTopicName + " ] " + myTopic + " Seq= " + currSeq);

	    MyScribeContent myMessage = new MyScribeContent(allTopics[tNumber].topicName, endpoint.getLocalNodeHandle(), currSeq, false, null, 0, null);
	    myScribe.publish(myTopic, myMessage); 
	    allTopics[tNumber].setPSeqNum(currSeq + 1);
	    allTopics[tNumber].setPTime(System.currentTimeMillis());
	    
	    
	    // We will also notify our replica set for this sequence number, this is to ensure that in the presence of churn the new root starts publishing with this sequence number
	    PublishStateMsg pStateMsg = new PublishStateMsg(tNumber, allTopics[tNumber].topicName, currSeq + 1, endpoint.getLocalNodeHandle());
	    NodeHandleSet set = endpoint.replicaSet(myTopic.getId(), NUMSEQREPLICAS);
	    for(int i=0; i < set.size(); i++) {
		NodeHandle replica = set.getHandle(i);
		endpoint.route(null, pStateMsg , replica);
	    }
	    return 1;
	} else {
	    return 0;
	}
    }
    

    // When the node boots up, it queries its replica set to fetch the latest PSeqNum for the different groups
    public void initializePSeqNum() {
	for(int i=0; i<NUMTESTGROUPS; i++) {
	    String topicName = allTopics[i].topicName;
	    Topic myTopic = allTopics[i].topic; 
	    if(isRoot(myTopic)) {
		int currSeq = allTopics[i].pSeqNum;
		NodeHandleSet set = endpoint.replicaSet(myTopic.getId(), NUMSEQREPLICAS);
		RequestPublishStateMsg reqPStateMsg = new RequestPublishStateMsg(i, allTopics[i].topicName, currSeq, endpoint.getLocalNodeHandle());
		myPrint("SysTime: " + System.currentTimeMillis() +  " Node "+endpoint.getLocalNodeHandle()+" REQUESTINGPSTATE for Topic[ "+ topicName + " ] " + myTopic, 850);
		for(int j=0; j < set.size(); j++) {
		    NodeHandle replica = set.getHandle(j);
		    endpoint.route(null, reqPStateMsg , replica);
		}
	    }
	}
	initializePSeqNumDone = true;
    }       
    */

    public String topic2TopicName(Topic topic) {
	return (String) topic2TopicName.get(topic);
	
    }


    public int topicName2Number(String name) {
	
	/*
	for(int i=0; i< NUMGROUPS; i++) {
	    String s = "_" + i;
	    if(name.endsWith(s)) {
		return i;
	    }
	}
	*/
	try {
	    int tNumber = Integer.parseInt(name);
	    return tNumber;

	} catch(Exception e) {
	    myPrint("ERROR: TopicNumber could not be extracted from " + name, 850);
	    return -1;
	}
    }

  /**
   * Called whenever we receive a published message.
   */
    public void deliver(Topic topic, ScribeContent content) {

	if(content instanceof MyScribeContent) {
	    MyScribeContent myContent = (MyScribeContent)content;
	    //System.out.println("MSC:"+System.currentTimeMillis()+" MyScribeClient.deliver("+topic+","+content+")");
	    //System.out.println("SysTime: " + System.currentTimeMillis() + " Received BROADCAST message for Topic[ " + myContent.topicName + " ]= " + topic + " Seq= " + myContent.seq);
	    
	    // We update the last seqNum we got for this group
	    int topicNumber = topicName2Number(myContent.topicName);
	    
	    allTopics[topicNumber].setLastRecvSeq(myContent.seq);
	    allTopics[topicNumber].setLastRecvTime(System.currentTimeMillis());
	    
	    // Since we already received the current sequence number on this group, we use this
	    // opportunity to increment the PSEQ, note that the other source of updating PSEQ is the 
	    // explicit PSTATE message
	    int newPSEQ = myContent.seq + 1;
	    if(newPSEQ > allTopics[topicNumber].pSeqNum) {
		allTopics[topicNumber].setPSeqNum(newPSEQ);
	    } 


	    // If the topic is TopicName_0, we will adjust our virtual clock
	    if(myContent.topicName.endsWith("_0")) {
		if(myContent.seq > virtualClock) {
		    // We will print the statistics for this time period near this virtual clock
		    
		    virtualClock = myContent.seq;
		     if(LibraTest.logLevel <= 850) myPrint("SysTime: " + System.currentTimeMillis() + " Virtual Clock: " + virtualClock, 850);
		}
	    }
	} else if(content instanceof ESMContent) {
	    // This is the downward propagation of the aggregate group metadata
	    if(content != null) {
		ESMContent myContent = (ESMContent)content;
		String topicName = myContent.topicName;
		int grpSize = myContent.descendants;
		int usedSlots = myContent.paramsLoad[0];
		int totalSlots = myContent.paramsLoad[1];
		grpMetadata.put(topic, new GroupMetadata(System.currentTimeMillis(),myContent));
		 if(LibraTest.logLevel <= Logger.INFO) myPrint("SysTime: " + System.currentTimeMillis() +  " Node "+endpoint.getLocalNodeHandle()+" received DOWNWARDUPDATE for Topic[ "+ topicName + " ] " + topic +  " ESMContent: " + "GrpSize: " + grpSize + ", UsedSlots: " + usedSlots + ", TotalSlots: " + totalSlots, Logger.INFO);
	    } else {
		 if(LibraTest.logLevel <= Logger.INFO) myPrint("SysTime: " + System.currentTimeMillis() +  " Node "+endpoint.getLocalNodeHandle()+" received DOWNWARDUPDATE for Topic[ "+ topic2TopicName(topic) + " ] " + topic + " ESMContent: " + "NULL", Logger.INFO);
	    }
	    // We also take this opportunity to update the last time any data was received from the parent in the tree to update our idletime
	    String topicName = topic2TopicName(topic);
	    int topicNumber = topicName2Number(topicName);
	    allTopics[topicNumber].setLastRecvTime(System.currentTimeMillis());
	}
	
    }
    
    /**
     * Sends an anycast message.
     */
    public void sendGrpMetadataRequest() {
	boolean done = false;
	// In order to prevent busy waiting when sufficient work is not present
	int numLoops = 0;
	while(!done && (numLoops <= NUMTESTGROUPS)) {
	    numLoops ++;
	    //gTurn = (gTurn + 1) % NUMTESTGROUPS;
	    gTurn = 0; // We currently fix this to check the accuracy of the update propagation
	    int currSeq = allTopics[gTurn].gSeqNum;
	    reqGrpMetadataRequest(gTurn,currSeq);
	    allTopics[gTurn].setGSeqNum(currSeq + 1); // Note that here only the gSeqNum is updated for the primary sScribe tree
	    done = true;
	}
    
    }
    

    public int numRequests(long currtime) {
	int currInterval  = (int)((currtime - (LibraTest.BOOTTIME + 600000))/(LibraTest.CONSTANTINTERVAL*1000));
	int val = currInterval;
	if(val < 0) {
	    val = 0;
	}
	if(val > 10) {
	    val = 10;
	}
	//System.out.println("NUMREQUESTS: " + val);
	return val;
    }


    /**
     * Sends an anycast message.
     */
    public void sendAnycast() {
	if(LibraTest.VARYINGANYCASTRATE) {
	    // We model increasing the rate of anycasts
	    long currtime = System.currentTimeMillis();
	    int reqToMake = numRequests(currtime);
	    for(int i=0; i< reqToMake; i++) {
		// We choose a random anycast group
		int grpId = rng.nextInt(NUMTESTGROUPS);
		int currSeq = allTopics[grpId].aSeqNum;
		reqAnycast(grpId, currSeq);
		allTopics[grpId].setASeqNum(currSeq + 1); // Note that here only the aSeqNum is updated for the primary Scribe tree
	    }

	} else {
	    boolean done = false;
	    // In order to prevent busy waiting when sufficient work is not present
	    int numLoops = 0;
	    while(!done && (numLoops <= NUMTESTGROUPS)) {
		numLoops ++;
		aTurn = (aTurn + 1) % NUMTESTGROUPS;
		// Note : We will anycast only if we are not member of this group
		
		//if(!allTopics[aTurn].isSubscribed) {
		int currSeq = allTopics[aTurn].aSeqNum;
		reqAnycast(aTurn, currSeq);
		allTopics[aTurn].setASeqNum(currSeq + 1); // Note that here only the aSeqNum is updated for the primary Scribe tree
		done = true;
	    //}
	    }
	}
    }


    /**
     * Called when we receive an anycast.  If we return
     * false, it will be delivered elsewhere.  Returning true
     * stops the message here.
     */
    public boolean anycast(Topic topic, ScribeContent content) {
	/* Currently we let only the root handle the Group resource request in the directAnycast() method
	if(content instanceof GrpMetadataRequestContent) {
	    GrpMetadataRequestContent myContent = (GrpMetadataRequestContent)content;
	    String myTopicName = myContent.topicName;
	    int topicNumber = topicName2Number(myTopicName);
	    NodeHandle anycastRequestor = myContent.from;
	    int seq = myContent.seq;

	    if(grpMetadata.containsKey(topic)) {
		GroupMetadata metadata = (GroupMetadata)grpMetadata.get(topic);
		long currTime = System.currentTimeMillis();
		if((currTime - metadata.updateTime) > (LibraTest.GRPMETADATASTALEPERIOD * 1000)) {
		    // Remove the stale entry
		    grpMetadata.remove(topic);
		    return false;
		} else {
		    // We return the metadata to the requestor
		    if(printEnable(topicNumber)) {
			 if(LibraTest.logLevel <= 850) myPrint("SysTime: " + System.currentTimeMillis() + " Node "+endpoint.getLocalNodeHandle()+" PROVIDINGGRPMETADATA to " + anycastRequestor + "for Topic[ "+ myTopicName + " ] " + topic + " content: " + myContent , 850);
			
			
		    }
		    if (anycastRequestor != null) {
			endpoint.route(null, new MyGrpMetadataAckMsg(metadata.metadata, endpoint.getLocalNodeHandle(), topic, seq), anycastRequestor);
		    }
		    return true;

		}
	    } else {
		return false;
	    }

	} 
	*/
	if(content instanceof MyScribeContent) {
	

	    // Here we implement the logic to make sure that we are eligible for a good parent just on the basis of local metadat information. Note : The propagation of metadata information to the parents make sure that in directAnycast() we are not chosen in the first place
	    
	    
	    boolean returnValue;
	    MyScribeContent myContent = (MyScribeContent)content;
	    //System.out.println("anycastMessageContent at anycast(): " + myContent);
	    String myTopicName = myContent.topicName;
	    int topicNumber = topicName2Number(myTopicName);
	    NodeHandle anycastRequestor = myContent.from;
	    byte[] requestorIdArray = myContent.getESMIdArray();
	    
	    
	    if(endpoint.getLocalNodeHandle().equals(anycastRequestor)) {
		// We do not accept the anycast made by ourself
		return false;
	    
	    }
	    
	    
	    // We prevent ESM tree loop formation 
	    ESMContent esmContent = (ESMContent)esmScribePolicy.leafMetadata.get(topic);
	    
	    // We set it to true and make it false if any of the conditions is violated
	    boolean canAccept = true;
	    
	    if(esmContent == null) {
		if(printEnable(topicNumber)) {
		    if(LibraTest.logLevel <= 850) myPrint("anycast(esmContent=null, ret=false " + myContent, 850);
		}
		canAccept = false;
	    }


	    // This is a hack to force some number of traversals in the NUMTESTGROUPS
	    //myPrint(" topicNumber= " + topicNumber + " , " + "#refused= " + myContent.numRefused + " , " + " thresh= " + LibraTest.ANYCASTTRAVERSALTHRESHOLD, 850);
	    if(((topicNumber % NUMGROUPS) < NUMTESTGROUPS) && (myContent.numRefused < LibraTest.ANYCASTTRAVERSALTHRESHOLD)) {
		myContent.numRefused ++;
		canAccept = false;
	    }
 
	    if(esmContent != null) {
		if(!esmContent.hasSpareBandwidth()) {
		    if(printEnable(topicNumber)) {
			 if(LibraTest.logLevel <= 850) myPrint("anycast(esmContent=noBandwidth, ret=false " + myContent, 850);
		    }
		    canAccept = false;
		}

		if(!esmContent.hasGoodPerformance()) {
		    if(!LibraTest.ENABLEFASTCONVERGENCE) {
			if(printEnable(topicNumber)) {
			     if(LibraTest.logLevel <= 850) myPrint("anycast(esmContent=highLoss_FastConvergenceFlagDisabled, ret=false " + myContent, 850);
			}
			canAccept = false;
		    } else {
			if(!esmContent.allowFastConvergence(myContent.pathLength, myContent.paramsPath, requestorIdArray)) {
			    if(printEnable(topicNumber)) {
				 if(LibraTest.logLevel <= 850) myPrint("anycast(esmContent=highLoss_FastConvergenceFlagEnabled_IMPROPERORDERING, ret=false " + myContent, 850);
			    }
			    canAccept = false;			    
			}
		    }
		} 

		if(!esmContent.hasNoLoops(requestorIdArray)) {
		    if(printEnable(topicNumber)) {
			 if(LibraTest.logLevel <= 850) myPrint("anycast(esmContent=hasLoops, ret=false " + myContent, 850);
		    }
		    canAccept = false;
		} 
		

	    }
	    
	    
	    if(canAccept) {
		if(printEnable(topicNumber)) {
		     if(LibraTest.logLevel <= 850) myPrint("anycast(esmContent=SpareB_LowLossORFastConvergenceProperOrdering_NoLoops, ret=true " + myContent, 850);
		}
		int policy = esmScribePolicy.getPolicy(topicNumber);
		if((policy == ESMScribePolicy.CENTRALRANDOM) || (policy == ESMScribePolicy.CENTRALOPTIMAL) || (policy == ESMScribePolicy.ESMCENTRALRANDOM) || (policy == ESMScribePolicy.ESMCENTRALOPTIMAL)) {
		    // We check to see if we have other children present
		    NodeHandle[] children = myScribe.getChildren(topic);
		    if(children.length > 0) {
			 if(LibraTest.logLevel <= 850) myPrint("WARNING: In the Centralized policies, the intermediate node being a subscriber refused the anycast although it could have accepted it", 850);
			returnValue = false;
		    } else {
			returnValue = true;
		    }
		} else {
		    returnValue = true; // We accept the anycast
		}
	    
	    } else {
		if(printEnable(topicNumber)) {
		     if(LibraTest.logLevel <= 850) myPrint("SysTime: " + System.currentTimeMillis() +  " Node REFUSED anycast from " + anycastRequestor + "for Topic[ "+ myTopicName + " ] " + topic + " for /NULLESMCONTENT/ESMloopformation/Bandwidth/Performance/FORECEDTRAVERSALS", 850);
		}
		returnValue = false;
	    }
	    
	    
	    

	    if(returnValue) {
		
		// We will send an ack back to the anycast requestor
		// We also tell it the hostIp:esmServerPort/dummyesmServerPort depending on the topicNumber
		
		
		int portToReport; // This is the datapath port
		byte[] idToReport;
		int tNumber = topicName2Number(myTopicName); 
		if((tNumber % NUMGROUPS) < NUMTESTGROUPS) {
		    portToReport = dummyesmDatapathPort;
		    idToReport = dummyesmOverlayId;
		} else {
		    portToReport =  ((ESMRegistryState)esmRegistry.get(new Integer(tNumber))).esmDatapathPort;
		idToReport =  ((ESMRegistryState)esmRegistry.get(new Integer(tNumber))).esmOverlayId;
		}
		if(printEnable(topicNumber)) {
		     if(LibraTest.logLevel <= 850) myPrint("SysTime: " + System.currentTimeMillis() + " Node ACCEPTED anycast from " + anycastRequestor + "for Topic[ "+ myTopicName + " ] " + topic, 850);
		    
		    
		}
		if (anycastRequestor != null) {
		    endpoint.route(null, new MyAnycastAckMsg(myContent, endpoint.getLocalNodeHandle(), topic, hostIp, idToReport, portToReport, cachedGNPCoord), anycastRequestor);
		}
		reqESMServerProspectiveChild(tNumber,requestorIdArray);
		
	    }
	    return returnValue;
	}
	return false;
    }
    
   


  public void childAdded(Topic topic, NodeHandle child) {
//    System.out.println("MyScribeClient.childAdded("+topic+","+child+")");
  }

  public void childRemoved(Topic topic, NodeHandle child) {
//    System.out.println("MyScribeClient.childRemoved("+topic+","+child+")");
  }

  public void subscribeFailed(Topic topic) {
       if(LibraTest.logLevel <= 850) myPrint("MyScribeClient.subscribeFailed("+topic+")", 850);
      NodeHandle scribeParent = myScribe.getParent(topic);
      if(scribeParent == null) {
	  myScribe.subscribe(topic, this);
      }
  }

    /*********** Methods called by  the Scribe Server *****************/

    // invokeRegister();
    // invokeAnycast(int streamId);
    // invokeSubscribe(int streamId);
    // invokeUnsubscribe(int streamId);
    // invokeUpdate(int streamId,byte[] paramsLoad, byte[] paramsLoss, byte[] paramsPath);

    public void invokeRegister(final int esmStreamId, final byte[] esmOverlayId, final int esmServerPort, final int esmDatapathPort, final byte esmRunId) {

	Runnable runnable = new Runnable() {
		public void run() {
	

		    if((esmOverlayId.length != 4) || (esmServerPort <=0) || (esmDatapathPort <=0) || ((esmStreamId < 0) || (esmStreamId >= NUMGROUPS*LibraTest.NUMTREES))) {
			if(LibraTest.logLevel <= Logger.WARNING) myPrint("Warning: invokeRegister() FAILED, esmOverlayId is not 4 bytes, or esmPort leq 0: esmServerPort= " + esmServerPort + ", esmDatapathPort= " + esmDatapathPort + ", esmOverlayId.length= " + esmOverlayId.length + ", esmStreamId= " + esmStreamId, Logger.WARNING);
		    } else {
			ESMRegistryState rState = new ESMRegistryState(esmStreamId, esmOverlayId, esmServerPort,esmDatapathPort, esmRunId);
			esmRegistry.put(new Integer(esmStreamId), rState);
			if(LibraTest.logLevel <= 875) myPrint("Registering ESM with (StreamId: " + esmStreamId + ", OVERLAYID:" + asString(esmOverlayId) + ", PORT:" + esmServerPort + ", DATAPATHPORT: " + esmDatapathPort + " ESMRUNID:" + esmRunId + ")", 875);
		    }
		    
		}
	    };

	
	if (selectorManager.isSelectorThread()) {
	    runnable.run();
	} else {
	    selectorManager.invoke(runnable);
	}





    }

    
    public void invokeDummyRegister(final byte[] dummyesmOverlayId, final int dummyesmServerPort, final int dummyesmDatapathPort, final byte dummyesmRunId) {
	
	Runnable runnable = new Runnable() {
		public void run() {
	

		    if((dummyesmOverlayId.length != 4) || (dummyesmServerPort <=0) || (dummyesmDatapathPort <=0)) {
			if(LibraTest.logLevel <= Logger.WARNING) myPrint("Warning: invokeDummyRegister() FAILED, esmOverlayId is not 4 bytes, or esmPort leq 0", Logger.WARNING);
		    } else {
			for(int i=0; i<4; i++) {
			    MyLibraClient.this.dummyesmOverlayId[i]  = dummyesmOverlayId[i];
			} 
			MyLibraClient.this.dummyesmServerPort = dummyesmServerPort;
			MyLibraClient.this.dummyesmDatapathPort = dummyesmDatapathPort;
			MyLibraClient.this.dummyesmRunId = dummyesmRunId;
			if(LibraTest.logLevel <= 875) myPrint("DummyRegistering ESM with (ID:" + asString(dummyesmOverlayId) + ", PORT:" + dummyesmServerPort + ", DATAPATHPORT: " + dummyesmDatapathPort + " DUMMYESMRUNID: " + dummyesmRunId + ")", 875);
		    }

		}
	    };
	
	
	if (selectorManager.isSelectorThread()) {
	    runnable.run();
	} else {
	    selectorManager.invoke(runnable);
	}

    }
    
    // This invokes the sending of a reuqest to find aggregate group metadata
    public void invokeGrpMetadataRequest(final int index, final int currSeq) {
	
	Runnable runnable = new Runnable() {
		public void run() {

		    
		    if((index < 0) || (index >= NUMGROUPS* LibraTest.NUMTREES)) {
			if(LibraTest.logLevel <= 850) myPrint("Warning: invokeGrpMetadataRequest() FAILED, streamId= " + index, 850);
			
		    } else {
			Topic myTopic = allTopics[index].topic;
			String topicName = allTopics[index].topicName;
			
			GrpMetadataRequestContent myMessage = new GrpMetadataRequestContent(allTopics[index].topicName, endpoint.getLocalNodeHandle(), currSeq);
			String gId = "GRPMETADATA" + "_G" + index + "_H" + hostName + "_J" + jvmIndex + "_V" + vIndex + "_S"  + currSeq;  // AnycastCode_StreamId_Hostname_Seq
			myMessage.setGlobalId(gId);
			if(LibraTest.logLevel <= 875) myPrint("SysTime: " + System.currentTimeMillis() + " Node "+endpoint.getLocalNodeHandle()+" GRPMETADATAREQUESTING for Topic[ "+ topicName + " ] " + myTopic + " Seq=" + currSeq + " GID=" + gId, 875);
			myScribe.anycast(myTopic, myMessage);
		    }

		}
	    };
	
	
	if (selectorManager.isSelectorThread()) {
	    runnable.run();
	} else {
	    selectorManager.invoke(runnable);
	}
		    
    }

  // index - is the stream id 
    public void invokeAnycast(final int index, final int currSeq, final int pathLength, final byte[] paramsPath) {
	Runnable runnable = new Runnable() {
		public void run() {

		    if((index < 0) || (index >= NUMGROUPS*LibraTest.NUMTREES)) {
			if(LibraTest.logLevel <= Logger.WARNING) myPrint("Warning: invokeAnycast() FAILED, streamId= " + index, Logger.WARNING);
			
		    } else {
			//if(allTopics[index].isSubscribed) {
			//System.out.println("Warning: Node ANYCASTING inspite of being subscribed, probably is an ESM client looking for a new parent");
			//}
			Topic myTopic = allTopics[index].topic;
			//int currSeq = allTopics[index].aSeqNum;
			String topicName = allTopics[index].topicName;
			byte myesmRunId;
			if((index % NUMGROUPS) < NUMTESTGROUPS) {
			    myesmRunId = dummyesmRunId;
			} else {
			    myesmRunId = ((ESMRegistryState)esmRegistry.get(new Integer(index))).esmRunId;
			}
			
			//allTopics[index].setASeqNum(currSeq + 1);
			MyScribeContent myMessage = new MyScribeContent(allTopics[index].topicName, endpoint.getLocalNodeHandle(), currSeq, true, cachedGNPCoord, pathLength, paramsPath);
			String gId = "R" + myesmRunId + "_A" + index + "_H" + hostName + "_J" + jvmIndex + "_V" + vIndex + "_S"  + currSeq;  // AnycastCode_StreamId_Hostname_Seq
			myMessage.setGlobalId(gId);
			myMessage.setESMRunId(myesmRunId);
			int topicNumber = index;
			if((topicNumber % NUMGROUPS) < NUMTESTGROUPS) {
			    myMessage.setESMIdArray(dummyesmOverlayId);
			} else {
			    myMessage.setESMIdArray(((ESMRegistryState)esmRegistry.get(new Integer(index))).esmOverlayId);
			}
			
			int policy = esmScribePolicy.getPolicy(index);
			if((policy == ESMScribePolicy.CENTRALRANDOM) || (policy == ESMScribePolicy.CENTRALOPTIMAL) || (policy == ESMScribePolicy.ESMCENTRALRANDOM) || (policy == ESMScribePolicy.ESMCENTRALOPTIMAL)) {
			    // We will send the anycast directly to the root
			    NodeHandle cachedRoot = allTopics[index].cachedRoot;
		//if(printEnable(index)) {
			    if(LibraTest.logLevel <= 875) myPrint("SysTime: " + System.currentTimeMillis() + " Node ANYCASTING for Topic[ "+ topicName + " ] " + myTopic + " Seq= " + currSeq + " thru CachedRoot=  " + cachedRoot + " GID=" + gId, 875);
			    //}
			    myScribe.anycast(myTopic, myMessage, cachedRoot); 
			} else {
			    if(LibraTest.logLevel <= 875) myPrint("SysTime: " + System.currentTimeMillis() + " Node ANYCASTING for Topic[ " + topicName + " ] " + myTopic + " GID=" + gId, 875);
			    String requestor;
			    //if(LibraTest.BINDADDRESS.equals("DEFAULT")) {
			    //requestor = "BIND" + bindIndex + "_JVM" + jvmIndex;
			    //} else {
			    //requestor = LibraTest.BINDADDRESS;
			    //}
			    System.out.println("Systime: " + System.currentTimeMillis() + " ESMSERVER_ANYCASTING for VIndex= " + vIndex + " StreamId= " + index + " SeqNum= " + currSeq + " Requestor= " + LibraTest.BINDADDRESS);
			    //}
			    myScribe.anycast(myTopic, myMessage);
			}
		    }

		}

	    };
	
	
	if (selectorManager.isSelectorThread()) {
	    runnable.run();
	} else {
	    selectorManager.invoke(runnable);
	}
	
    }
    
    
	// index - is the stream id 
    public void invokeSubscribe(final int index) {
	Runnable runnable = new Runnable() {
		public void run() {

		    
		    if((index < 0) || (index >= NUMGROUPS*LibraTest.NUMTREES)) {
			if(LibraTest.logLevel <= Logger.WARNING) myPrint("Warning: invokeSubscribe() FAILED, streamId= " + index, Logger.WARNING);
			
		    } else {
			if(allTopics[index].isSubscribed) {
			    if(LibraTest.logLevel <= Logger.WARNING) myPrint("Warning: Node SUBSCRIBING inspite of being a subscriber", Logger.WARNING);
			    return;
			}
			TopicTaskState tState = allTopics[index];
			String myTopicName = tState.topicName;
			Topic myTopic = tState.topic;
			if(LibraTest.logLevel <= 880) myPrint("SysTime: " + System.currentTimeMillis() + " Node " + endpoint.getLocalNodeHandle() + " SUBSCRIBING for Topic[ " + myTopicName + " ]= "+ myTopic, 880);
			tState.setSubscribed(true);
			myScribe.subscribe(myTopic, MyLibraClient.this);
		    }	
		}
		
	    };
	
	
	if (selectorManager.isSelectorThread()) {
	    runnable.run();
	} else {
	    selectorManager.invoke(runnable);
	}


    }
    

  // index - is the stream id 
    public void invokeUnsubscribe(final int index) {
	Runnable runnable = new Runnable() {
		public void run() {

		    if((index < 0) || (index >= NUMGROUPS*LibraTest.NUMTREES)) {
			if(LibraTest.logLevel <= Logger.WARNING) myPrint("Warning: invokeUnsubscribe() FAILED, streamId= " + index, Logger.WARNING);
		    } else {
			if(!allTopics[index].isSubscribed) {
			    if(LibraTest.logLevel <= Logger.WARNING) myPrint("Warning: Node UNSUBSCRIBING inspite of not being subscribed", Logger.WARNING);
			    return;
			}
			TopicTaskState tState = allTopics[index];
			String myTopicName = tState.topicName;
			Topic myTopic = tState.topic;
			if(LibraTest.logLevel <= 880) myPrint("SysTime: " + System.currentTimeMillis() + " Node " + endpoint.getLocalNodeHandle() + " UNSUBSCRIBING for Topic[ " + myTopicName + " ]= "+ myTopic, 880);
			tState.setSubscribed(false);
			esmScribePolicy.leafMetadata.remove(myTopic);
			myScribe.unsubscribe(myTopic, MyLibraClient.this);
		    }
		}
		
	    };
	
	
	if (selectorManager.isSelectorThread()) {
	    runnable.run();
	} else {
	    selectorManager.invoke(runnable);
	}
	
    }


    // This is similar to sendRefreshUpdateForTopic except that it is either LEAFSUBSCRIBER or INTERMEDIATESUBSCRIBER
    public void invokeUpdate(final int index, final int[] paramsLoad, final int[] paramsLoss, final int time, final int pathLength, final byte[] paramsPath) {

	
	Runnable runnable = new Runnable() {
		public void run() {
		    // This variable is set to true if the group is outside NUMTESTGROUPS. This will send the update without checking for the lastRefresh time
		    boolean forceUpdate = false;
		    //myPrint("invokeUpdate(" + index + ")", 850);
		    if((index < 0) || (index >= NUMGROUPS*LibraTest.NUMTREES)) {
			if(LibraTest.logLevel <= Logger.WARNING) myPrint("Warning: invokeUpdate() FAILED, streamId= " + index, Logger.WARNING);
		    } else {
			if(!allTopics[index].isSubscribed) {
			    if(LibraTest.logLevel <= Logger.WARNING) myPrint("Warning: Node UPDATING inspite of not being subscribed", Logger.WARNING);
			    return;
			}
			TopicTaskState tState = allTopics[index];
			String myTopicName = tState.topicName;
			int topicNumber = topicName2Number(myTopicName);
			Topic myTopic = tState.topic;
			NodeHandle parent = myScribe.getParent(myTopic);
			
			ESMContent esmContent;
			if((topicNumber % NUMGROUPS) < NUMTESTGROUPS) {
			    esmContent = new ESMContent(myTopicName,false, hostIp,dummyesmServerPort, dummyesmOverlayId, paramsLoad,paramsLoss, time, pathLength,paramsPath,1,cachedGNPCoord);
			} else {
			    esmContent = new ESMContent(myTopicName,false, hostIp, ((ESMRegistryState)esmRegistry.get(new Integer(index))).esmServerPort,  ((ESMRegistryState)esmRegistry.get(new Integer(index))).esmOverlayId, paramsLoad,paramsLoss, time, pathLength,paramsPath,1, cachedGNPCoord);
			}
			byte myesmRunId;
			if((index % NUMGROUPS)< NUMTESTGROUPS) {
			    myesmRunId = dummyesmRunId;
			    forceUpdate = false;
			} else {
			    myesmRunId =  ((ESMRegistryState)esmRegistry.get(new Integer(index))).esmRunId;
			    // If the load/loss changes we consider it as a forced update
			    ESMContent esmContentPrev = (ESMContent)esmScribePolicy.prevMetadata.get(myTopic);
			    if(esmContent.sameExceptStayTime(esmContentPrev)) {
				forceUpdate = false;
			    } else {
				forceUpdate = true;
			    }
			}
			
			esmContent.setESMRunId(myesmRunId);
			if(!((index % NUMGROUPS)< NUMTESTGROUPS)) {
			    if(LibraTest.logLevel <= 875) myPrint("SysTime: " + System.currentTimeMillis() + " Node invokeUpdate() called for Topic[ " + myTopicName + " ]" + esmContent, 875);
			} else {
			    if(LibraTest.logLevel <= 850) myPrint("SysTime: " + System.currentTimeMillis() + " Node invokeUpdate() called for Topic[ " + myTopicName + " ]" + esmContent, 850);

			}
			
			
			esmScribePolicy.leafMetadata.put(myTopic, esmContent);
			if(!((topicNumber % NUMGROUPS) < NUMTESTGROUPS)) {
			    // We explicityly call the refresh routine to be proactive in sending the update
			    sendRefreshUpdateForTopic(topicNumber,forceUpdate);
			} else {
				// We rely on the periodic refresh update to take care of updating the metadata periodically
			    
			
			}
			
		    }
		}
		
	    };
	
	
	if (selectorManager.isSelectorThread()) {
	    runnable.run();
	} else {
	    selectorManager.invoke(runnable);
	}
    }


    public void recvUDPQuery(byte[] payload) {
	

    }

    public void recvUDPAck(byte[] payload) {

    }


    /*********** Methods called by ourselves acting as a dummy ESM Client ****************/
    // reqRegister(); 
    // reqAnycast(int streamId);
    // reqSubscribe(int streamId);
    // reqUnsubscribe(int streamId);
    // reqUpdate(int streamId,byte[] paramsLoad, byte[] paramsLoss, byte[] paramsPath);


    // This acts as a dummy esm client registering itself to the Scribe server with
    // its (overlayId,port)
    // Returns true if Success
    public boolean reqDummyRegister() {
	 if(LibraTest.logLevel <= 850) myPrint("Attempting reqDummyRegister()", 850);
	String host = "localhost";
	try {
	    byte[] msg = new byte[15]; // VINDEX + OPCODE + esmoverlayId(4 bytes) + esmserverport (4 bytes) + esmdatapathport ( 4 bytes) + esmRunId ( 1 byte)
	    msg[0] = (byte)vIndex;
	    msg[1] = UDPLibraServer.DUMMYREGISTEROPCODE;
	    for(int i=0; i<4; i++) {
		msg[2+i] = hostIp[i];
	    }
	    byte[] portArray = new byte[4];
	    MathUtils.intToByteArray(LibraTest.DUMMYESMSERVERPORT,portArray,0);
	    for(int i=0; i<4; i++) {
		msg[6+i] = portArray[i];
	    }
	    byte[] datapathPortArray = new byte[4];
	    MathUtils.intToByteArray(LibraTest.DUMMYESMDATAPATHPORT,datapathPortArray,0);
	    for(int i=0; i<4; i++) {
		msg[10+i] = datapathPortArray[i];
	    }
	    msg[14] = 0; // We set our own dummy experimentation with ESMRunId:0
	    
	    //SocketAddress sentToAddress = new InetSocketAddress(host,LibraTest.SCRIBESERVERPORT);
	    
	    DatagramPacket sendPacket = new DatagramPacket(msg,msg.length, udplibraserverSentToAddress);
	    udpSocket.send(sendPacket);
	    
	    return true;
	    
	    
	}
	catch (UnknownHostException e) {  if(LibraTest.logLevel <= 850) myPrint(host + ": unknown host.", 850); } 
	catch (IOException e) { if(LibraTest.logLevel <= 850) myPrint("I/O error with " + host, 850); }

	return false;
    }


    // index - is the stream id 
    public void reqGrpMetadataRequest(int index, int seqNum) {
	String host = "localhost";
	try {
	    byte[] msg = new byte[7]; // VINDEX + OPCODE + streamId (1 byte) + seqNum ( 4 bytes)
	    byte streamId = (byte) index;
	    msg[0] = (byte)vIndex;
	    msg[1] = UDPLibraServer.GRPMETADATAREQUESTOPCODE;
	    msg[2] = streamId;
	    byte[] seqNumArray = new byte[4];
	    MathUtils.intToByteArray(seqNum,seqNumArray,0);
	    for(int i=0; i<4; i++) {
		msg[3+i] = seqNumArray[i];
	    }
	    //SocketAddress sentToAddress = new InetSocketAddress(host,LibraTest.SCRIBESERVERPORT);
	    
	    DatagramPacket sendPacket = new DatagramPacket(msg,msg.length, udplibraserverSentToAddress);
	    udpSocket.send(sendPacket);

	    
	}
	catch (UnknownHostException e) { if(LibraTest.logLevel <= 850) myPrint(host + ": unknown host.", 850); } 
	catch (IOException e) { if(LibraTest.logLevel <= 850) myPrint("I/O error with " + host, 850); }
	
	
	
    }	
    



    // index - is the stream id 
    public void reqAnycast(int index, int seqNum) {
	//System.out.println("reqAnycast(" + index + ", " + seqNum + ")");
	//String host = "localhost";
	try {
	    int pathLength = 1;
	    byte[] msg = new byte[8 + 4*pathLength]; // VINDEX + OPCODE + streamId (1 byte) + seqNum ( 4 bytes) + pathLength (1 byte) + path ( 4* pathlength bytes )
	    byte streamId = (byte) index;
	    msg[0] = (byte)vIndex;
	    msg[1] = UDPLibraServer.ANYCASTOPCODE;
	    msg[2] = streamId;
	    byte[] seqNumArray = new byte[4];
	    MathUtils.intToByteArray(seqNum,seqNumArray,0);
	    for(int i=0; i<4; i++) {
		msg[3+i] = seqNumArray[i];
	    }
	    // We give a dummy pathlength = 1; and path {1.1.1.1}
	    msg[7] = (byte)pathLength;
	    for(int i=0; i<4; i++) {
		msg[8+i] = 1;
	    }
	    //SocketAddress sentToAddress = new InetSocketAddress(host,LibraTest.SCRIBESERVERPORT);
	    
	    DatagramPacket sendPacket = new DatagramPacket(msg,msg.length, udplibraserverSentToAddress);
	    udpSocket.send(sendPacket);

	    
	}
	catch (UnknownHostException e) { if(LibraTest.logLevel <= 850) myPrint("unknown host", 850); } 
	catch (IOException e) { if(LibraTest.logLevel <= 850) myPrint("I/O error with host", 850); }
	
	
	
    }	
    

    public void reqPingAlive() {
	//String host = "localhost";
	try {
	    byte[] msg = new byte[2]; // VINDEX + OPCODE 
	    msg[0] = (byte)vIndex;
	    msg[1] = UDPLibraServer.PINGALIVEOPCODE;
	    //SocketAddress sentToAddress = new InetSocketAddress(host,LibraTest.SCRIBESERVERPORT);
	    DatagramPacket sendPacket = new DatagramPacket(msg,msg.length, udplibraserverSentToAddress);
	    
	    udpSocket.send(sendPacket);

	    
	}
	catch (UnknownHostException e) { if(LibraTest.logLevel <= 850) myPrint("unknown host.", 850); } 
	catch (IOException e) { if(LibraTest.logLevel <= 850) myPrint("I/O error with host", 850); }
	
	
	
    }	
    
	
    



    // index - is the stream id 
    public void reqSubscribe(int index) {
	/*
	if(SERVERTYPE == TCPSERVER) {
	    String host = "localhost";
	    try {
		Socket           client    = new Socket(host, LibraTest.SCRIBESERVERPORT);
		DataOutputStream socketOut = new DataOutputStream(client.getOutputStream());
		DataInputStream  socketIn  = new DataInputStream(client.getInputStream());
		
		byte streamId = (byte) index;
		byte[] msg = new byte[2];
		msg[0] = SUBSCRIBEOPCODE;
		msg[1] = streamId;
		//System.out.println("reqSubscribe: Sending req to ScribeServer");
		
		socketOut.write(msg, 0, 2);
		
		byte msgIn[] = new byte[2];
		socketIn.read(msgIn,0,2);
		if(msgIn[0] == SUCCESSCODE) {
		    //System.out.println("reqSubscribe: GOT SUCCESS ACK from server");
		}
		socketOut.close(); socketIn.close(); client.close();
	    } 
	    catch (UnknownHostException e) { System.err.println(host + ": unknown host."); } 
	    catch (IOException e) { System.err.println("I/O error with " + host); }
	} else  {
	*/

	//System.out.println("reqSubscribe( " + index + ")");
	//String host = "localhost";
	try {
	    byte[] msg = new byte[3]; // vIndex + OPCODE + streamId (1 byte)
	    byte streamId = (byte) index;
	    msg[0] = (byte)vIndex;
	    msg[1] = UDPLibraServer.SUBSCRIBEOPCODE;
	    msg[2] = streamId;
	    //SocketAddress sentToAddress = new InetSocketAddress(host,LibraTest.SCRIBESERVERPORT);
	    
	    DatagramPacket sendPacket = new DatagramPacket(msg,msg.length, udplibraserverSentToAddress);
	   
	    udpSocket.send(sendPacket);

	    

	    /*
	    // We will receive the ack
	    byte[] reply= new byte[2];    
	    DatagramPacket from_server = new DatagramPacket(reply,reply.length);
	    
	    udpSocket.setSoTimeout(1000);
	    try {
		udpSocket.receive(from_server);
		byte[] real_reply = new byte[from_server.getLength()];
		for(int i=0;i<from_server.getLength();i++) real_reply[i]=reply[i];
		if(real_reply[0] == SUCCESSCODE) {
		    //System.out.println("reqSubscribe: GOT SUCCESS ACK from server(UDPSERVER)");
		} else {
		    //System.out.println("reqSubscribe: GOT FAILURE ACK from server(UDPSERVER)");
		}
	    }catch(SocketTimeoutException e) {
		//System.out.println("Warning: reqSubscribe: Timeout before receiving an ack from UDPSERVER");
	    }
	    */

	}
	catch (UnknownHostException e) { if(LibraTest.logLevel <= 850) myPrint("unknown host.", 850); } 
	catch (IOException e) { if(LibraTest.logLevel <= 850) myPrint("I/O error with host", 850); }
    }



    // index - is the stream id 
    public void reqUnsubscribe(int index) {
	//String host = "localhost";
	try {
	    byte[] msg = new byte[3]; // vIndex + OPCODE + streamId (1 byte)
	    byte streamId = (byte) index;
	    msg[0] = (byte)vIndex;
	    msg[1] = UDPLibraServer.UNSUBSCRIBEOPCODE;
	    msg[2] = streamId;
	    //SocketAddress sentToAddress = new InetSocketAddress(host,LibraTest.SCRIBESERVERPORT);
	    DatagramPacket sendPacket = new DatagramPacket(msg,msg.length, udplibraserverSentToAddress);
	    
	    udpSocket.send(sendPacket);

	}
	catch (UnknownHostException e) { if(LibraTest.logLevel <= 850) myPrint("unknown host.", 850); } 
	catch (IOException e) { if(LibraTest.logLevel <= 850) myPrint("I/O error with host", 850); }
    }
    


    // index - is the stream id
    // This is a dummy update
    public void reqUpdate(int index) {
	//myPrint("reqUpdate(" + index+ ")", 850); 
	//String host = "localhost";
	try {
	    byte[] msg = new byte[27]; // vIndex + OPCODE + streamId (1 byte) + LOADMETRIC(1 byte) + USEDSLOTS/TOTALSLOTS ( 2 bytes) + LOSSMETRIC (1 byte) + LOSSRATE (1 byte) + TIMEMETRIC + REMAININGTIME ( 4 bytes) + PATHMETRIC ( 1 byte) + PATHLENGTH ( 1 byte) + PATHVALUES ( 4 * pathlength bytes)
	    byte streamId = (byte) index;

	    byte usedSlots = 0;
	    byte totalSlots = 1;
	    byte lossRate = (byte) rng.nextInt(10); // We currently intentionally fluctuate the loss lower than LOSSTHRESHOLD that refuses the anycast but at the same time forcing the update propagation to actually happen
	    byte PAD = 0;
	    byte pathLength = 3;
	    String name_1 = "sys01.cs.rice.edu";
	    String name_2 = "sys02.cs.rice.edu";
	    String name_3 = "achtung.cs.rice.edu";
	    
	    InetAddress inetAddr_1 = InetAddress.getByName(name_1);
	    InetAddress inetAddr_2 = InetAddress.getByName(name_2);
	    InetAddress inetAddr_3 = InetAddress.getByName(name_3);
	    byte[] ipBytes_1 = inetAddr_1.getAddress();
	    byte[] ipBytes_2 = inetAddr_2.getAddress();
	    byte[] ipBytes_3 = inetAddr_3.getAddress();

	    msg[0] = (byte)vIndex;
	    msg[1] = UDPLibraServer.UPDATEOPCODE;
	    msg[2] = streamId;
	    msg[3] = UDPLibraServer.LOADMETRIC;
	    msg[4] = usedSlots;
	    // Only the bootstrap host fluctuates the totalslots field. If everyone fluctuates we will not be able to track the correct desired value
	    
	    if(isBootstrapHost) {
		msg[5] = (byte) (fluctuateTotalSlots + 1);
	    } else {
		msg[5] = totalSlots;
	    }
	    
	    //msg[5] = totalSlots;
	    
	    

	    msg[6] = UDPLibraServer.LOSSMETRIC;
	    msg[7] = lossRate;
	    msg[8] = UDPLibraServer.TIMEMETRIC;
	    byte[] timeArray = new byte[4];
	    MathUtils.intToByteArray(1000,timeArray,0);
	    for(int i=0; i<4; i++) {
		msg[9+i] = timeArray[i];
	    }	    
	    msg[13] = UDPLibraServer.PATHMETRIC;
	    msg[14] = pathLength;
	    byte[] ipBytes;
	    ipBytes = ipBytes_1;
	    for(int i=0;i<4; i++) {
		msg[15+i] = ipBytes[i];
	    }
	    
	    ipBytes = ipBytes_2;
	    for(int i=0;i<4; i++) {
		msg[19+i] = ipBytes[i];
	    }
	    
	    ipBytes = ipBytes_3;
	    for(int i=0;i<4; i++) {
		msg[23+i] = ipBytes[i];
	    }
	    
	    
	    
	    //System.out.println("reqUpdate: Connected to " + host + ": Calling update() on streamId=" + streamId + " with the following parameters LOADUPDATE(" + usedSlots + "," + totalSlots+") " + "LOSSUPDATE(" + lossRate +")" + " PATHUPDATE( " + pathLength + ",[" + name_1 + "," + name_2 + "," + name_3 + "]");
	    
	    //SocketAddress sentToAddress = new InetSocketAddress(host,LibraTest.SCRIBESERVERPORT);
	    DatagramPacket sendPacket = new DatagramPacket(msg,msg.length, udplibraserverSentToAddress);
	    
	    udpSocket.send(sendPacket);

	}
	catch (UnknownHostException e) { if(LibraTest.logLevel <= 850) myPrint("unknown host.", 850); } 
	catch (IOException e) { if(LibraTest.logLevel <= 850) myPrint("I/O error with host", 850); }	    
    }


    // We assume that the esmserver is in UDP
    // The parameters are all information about the responder or the prospective parent (Note the esmPort is the ESMDatapath port
    public void reqESMServer(int index, int seqNum, byte[] ip, byte[] esmId, int esmPort, int msgPathLength, MyScribeContent.NodeIndex[] msgPath) {
	//String host = "localhost";
	try {
	    byte[] msg = new byte[19 + 4 + 4*msgPathLength]; // vIndex + OPCODE + streamId (1 byte) + Ip (4 bytes) + ESMId (4 bytes) + ESMPort(4 bytes) + seqNum (4 bytes) + msgPathLength (int) + msgPath (4 * msgPathLength bytes)
	    byte streamId = (byte) index;
	    msg[0] = (byte)vIndex;
	    msg[1] = UDPLibraServer.ANYCASTACKOPCODE;
	    msg[2] = streamId;
	    String ipS = asString(ip);
	    for(int i=0;i<4; i++) {
		msg[3+i] = ip[i];
	    }
	    for(int i=0;i<4; i++) {
		msg[7+i] = esmId[i];
	    }
	    byte[] portArray = new byte[4];
	    MathUtils.intToByteArray(esmPort,portArray,0);
	    for(int i=0;i<4; i++) {
		msg[11+i] = portArray[i];
	    }
	    String portString = asString(portArray);

	    byte[] seqNumArray = new byte[4];
	    MathUtils.intToByteArray(seqNum, seqNumArray,0);
	    for(int i=0;i<4; i++) {
		msg[15+i] = seqNumArray[i];
	    }
	    byte[] intBytes = new byte[4];
	    MathUtils.intToByteArray(msgPathLength, intBytes,0);
	    for(int i=0;i<4; i++) {
		msg[19+i] = intBytes[i];
	    }
	    int pos = 23;
	    String sPath ="[";
	    for(int p=0; p <msgPathLength; p++) {
		int val = msgPath[p].bindIndex; // We tell the ESM only the plIndex so as to not change the API at the ESM layer. Moreover the ESM does not care about the vIndex anyway
		sPath = sPath + val + " ";
		MathUtils.intToByteArray(val, intBytes,0);
		for(int i=0;i<4; i++) {
		    msg[pos] = intBytes[i];
		    pos ++;
		}
	    }
	    sPath = sPath + "]";

	    SocketAddress sentToAddress;
	    if((index % NUMGROUPS) < NUMTESTGROUPS) {
		if(dummyesmServerPort < 0) {
		     if(LibraTest.logLevel <= 850) myPrint("ERROR : reqESMServer() dummyesmServerPort= " + dummyesmServerPort, 850);
		    return;
		} else {
		    sentToAddress = new InetSocketAddress(udplibraserverInetAddr,dummyesmServerPort);
		}
	    } else {
		int esmServerPort =  ((ESMRegistryState)esmRegistry.get(new Integer(index))).esmServerPort;
		if(esmServerPort < 0) {
		     if(LibraTest.logLevel <= 850) myPrint("ERROR : reqESMServer() esmServerPort= " + esmServerPort, 850);
		    return;
		} else {
		    sentToAddress = new InetSocketAddress(udplibraserverInetAddr,esmServerPort);
		}

		// Since the printing at the ESMSERVER cannot be seen in this case we explictly print here
		System.out.println("Systime: " + System.currentTimeMillis() + " ESMSERVER_ANYCASTACK for VIndex= " + vIndex + " StreamId= " + streamId + " SeqNum= " + seqNum + " ProspectiveParent= ( " + "IP: " + ipS + " MsgHops: " + (msgPathLength -1) + " MsgPath: " + sPath + " )");
		

	    }
	    
	    DatagramPacket sendPacket = new DatagramPacket(msg,msg.length, sentToAddress);
	    udpSocket.send(sendPacket);

	 
	}   
	catch (UnknownHostException e) {  if(LibraTest.logLevel <= 850) myPrint("unknown host.", 850); } 
	catch (IOException e) {  if(LibraTest.logLevel <= 850) myPrint("I/O error with host", 850); }	
	catch(IllegalArgumentException e) {  if(LibraTest.logLevel <= 850) myPrint("IllegalArgument Exception " + e, 850);}
	
    }

    public void  reqESMServerGrpMetadataAck(int index, int seq, ESMContent content) {

	//String host = "localhost";
	try {
	    byte[] msg = new byte[19]; 	// vIndex (1 byte) + OPCODE (1 byte) + streamId (1 byte) + seq ( 4 bytes) +  groupSize(int - 4 bytes ) + totalSlots(int - 4 bytes) + usedSlots(int - 4 bytes)
	    byte streamId = (byte) index;
	    msg[0] = (byte)vIndex;
	    msg[1] = UDPLibraServer.GRPMETADATAACKOPCODE;
	    msg[2] = streamId;
	    byte[] myarray = new byte[4];
	    MathUtils.intToByteArray(seq,myarray,0);
	    for(int i=0;i<4; i++) {
		msg[3+i] = myarray[i];
	    }	    

	    int grpSize = content.descendants;
	    int usedSlots = content.paramsLoad[0];
	    int totalSlots = content.paramsLoad[1];

	    MathUtils.intToByteArray(grpSize,myarray,0);
	    for(int i=0;i<4; i++) {
		msg[7+i] = myarray[i];
	    }	    


	    MathUtils.intToByteArray(totalSlots,myarray,0);
	    for(int i=0;i<4; i++) {
		msg[11+i] = myarray[i];
	    }	    
	    MathUtils.intToByteArray(usedSlots,myarray,0);
	    for(int i=0;i<4; i++) {
		msg[15+i] = myarray[i];
	    }	    


	    SocketAddress sentToAddress;
	    if((index % NUMGROUPS) < NUMTESTGROUPS) {
		if(dummyesmServerPort < 0) {
		     if(LibraTest.logLevel <= 850) myPrint("ERROR : reqESMServerGrpMetadataAck() dummyesmServerPort= " + dummyesmServerPort, 850);
		    return;
		} else {
		    sentToAddress = new InetSocketAddress(udplibraserverInetAddr,dummyesmServerPort);
		}
	    } else {
		int esmServerPort =  ((ESMRegistryState)esmRegistry.get(new Integer(index))).esmServerPort;
		if(esmServerPort < 0) {
		     if(LibraTest.logLevel <= 850) myPrint("ERROR : reqESMServerGrpMetadataAck() esmServerPort= " + esmServerPort, 850);
		    return;
		} else {
		    sentToAddress = new InetSocketAddress(udplibraserverInetAddr,esmServerPort);
		}
	    }
	    
	    DatagramPacket sendPacket = new DatagramPacket(msg,msg.length, sentToAddress);
	    udpSocket.send(sendPacket);

	 
	}   
	catch (UnknownHostException e) { if(LibraTest.logLevel <= 850) myPrint("unknown host.", 850); } 
	catch (IOException e) { if(LibraTest.logLevel <= 850) myPrint("I/O error with host", 850); }	
	catch(IllegalArgumentException e) { if(LibraTest.logLevel <= 850) myPrint("IllegalArgument Exception " + e, 850);}
	




    }

    
	// Based on the topicNumber the request will be sent to the actual esm server or the dummy esm server
    public void  reqESMServerProspectiveChild(int index, byte[] esmId) {
	// We first do the load protection task on behalf of the ESMServer
	
	long currtimeSlot = System.currentTimeMillis()/(LibraTest.MONITORLOADINTERVAL *1000);
	Long mykey = new Long(currtimeSlot);
	int val;
	if((index % NUMGROUPS) < NUMTESTGROUPS) {
	    if(anycastRecv.containsKey(mykey)) {
		val = ((Integer)anycastRecv.get(mykey)).intValue();
		anycastRecv.put(mykey,new Integer(val+1));
		if(LibraTest.MAXANYCASTRECV >0) {
		    if((val+1)>= LibraTest.MAXANYCASTRECV) {
			// We update overload status
			overloadAction();
			
		}
		}
		
	    } else {
		anycastRecv.put(mykey,new Integer(1));
	    }
	}
	



	//String host = "localhost";
	try {
	    byte[] msg = new byte[7]; // vIndex + OPCODE + streamId (1 byte) + ESMId (4 bytes)
	    byte streamId = (byte) index;
	    msg[0] = (byte)vIndex;
	    msg[1] = UDPLibraServer.PROSPECTIVECHILDOPCODE;
	    msg[2] = streamId;
	    for(int i=0;i<4; i++) {
		msg[3+i] = esmId[i];
	    }
	    SocketAddress sentToAddress;
	    if((index % NUMGROUPS) < NUMTESTGROUPS) {
		if(dummyesmServerPort < 0) {
		     if(LibraTest.logLevel <= 850) myPrint("ERROR : reqESMServer() dummyesmServerPort= " + dummyesmServerPort, 850);
		    return;
		} else {
		    sentToAddress = new InetSocketAddress(udplibraserverInetAddr,dummyesmServerPort);
		}
	    } else {
		int esmServerPort =  ((ESMRegistryState)esmRegistry.get(new Integer(index))).esmServerPort;
		if(esmServerPort < 0) {
		     if(LibraTest.logLevel <= 850) myPrint("ERROR : reqESMServer() esmServerPort= " + esmServerPort, 850);
		    return;
		} else {
		    sentToAddress = new InetSocketAddress(udplibraserverInetAddr,esmServerPort);
		}
	    }
	    
	    DatagramPacket sendPacket = new DatagramPacket(msg,msg.length, sentToAddress);
	    udpSocket.send(sendPacket);

	 
	}   
	catch (UnknownHostException e) { if(LibraTest.logLevel <= 850) myPrint("unknown host.", 850); } 
	catch (IOException e) { if(LibraTest.logLevel <= 850) myPrint("I/O error with host", 850); }	
	catch(IllegalArgumentException e) { if(LibraTest.logLevel <= 850) myPrint("IllegalArgument Exception " + e, 850);}

    }




	// Based on the topicNumber the request will be sent to the actual esm server or the dummy esm server
    public void  reqESMServerAnycastFailure(int index, int seqNum) {
	//String host = "localhost";
	try {
	    byte[] msg = new byte[7]; // vIndex + OPCODE + streamId (1 byte) + seqNum (4 bytes)
	    byte streamId = (byte) index;
	    msg[0] = (byte)vIndex;
	    msg[1] = UDPLibraServer.ANYCASTFAILUREOPCODE;
	    msg[2] = streamId;

	    byte[] seqNumArray = new byte[4];
	    MathUtils.intToByteArray(seqNum, seqNumArray,0);
	    for(int i=0;i<4; i++) {
		msg[3+i] = seqNumArray[i];
	    }


	    SocketAddress sentToAddress;
	    if((index % NUMGROUPS) < NUMTESTGROUPS) {
		if(dummyesmServerPort < 0) {
		     if(LibraTest.logLevel <= 850) myPrint("ERROR : reqESMServer() dummyesmServerPort= " + dummyesmServerPort, 850);
		    return;
		} else {
		    sentToAddress = new InetSocketAddress(udplibraserverInetAddr,dummyesmServerPort);
		}
	    } else {
		int esmServerPort =  ((ESMRegistryState)esmRegistry.get(new Integer(index))).esmServerPort;
		if(esmServerPort < 0) {
		     if(LibraTest.logLevel <= 850) myPrint("ERROR : reqESMServer() esmServerPort= " + esmServerPort, 850);
		    return;
		} else {
		    sentToAddress = new InetSocketAddress(udplibraserverInetAddr,esmServerPort);
		}
	    }
	    
	    DatagramPacket sendPacket = new DatagramPacket(msg,msg.length, sentToAddress);
	    udpSocket.send(sendPacket);

	}   
	catch (UnknownHostException e) { if(LibraTest.logLevel <= 850) myPrint("unknown host.", 850); } 
	catch (IOException e) { if(LibraTest.logLevel <= 850) myPrint("I/O error with host", 850); }	
	catch(IllegalArgumentException e) { if(LibraTest.logLevel <= 850) myPrint("IllegalArgument Exception " + e, 850);}

    }


    public void queryGNP() {
	// GNP Maintenance Policy - 
	// This is the last time we updated the GNP coordinate. The updation policy is
	//  1. Update if we get  a stable coordinate
	//  2. If unstable {
	//      a.  If the cached GNP is unstable update it
	//      b. If the cached GNP is stable and was got less than GNPCACHETIME, then do not update
	//      c. Actively remove GNP coordinates beyond GNPCACHETIME
	
	 if(LibraTest.logLevel <= 850) myPrint("queryGNP() called", 850);
	
	long currTime = System.currentTimeMillis();
	if((cachedGNPCoord != null) && ( (currTime - lastGNPSetTime) > GNPCACHETIME)) {
	    // remove stale coordinates
	    cachedGNPCoord = null;
	    lastGNPSetTime = currTime;
	    esmScribePolicy.updateGNPCoord();
	}
	

	try {
	    //String hostname = "127.0.0.1";
	    QueryResult result = GNPUtilities.queryGNP(localAddress);
	    // Result is null if there was a timeout trying to conatct the GNP service
	    if(result != null) {
		double[] coord = result.getCoordinates();
		GNPCoordinate gnpCoord = new GNPCoordinate(result.isStable(),coord.length,coord);
		
		if((cachedGNPCoord == null) || (!cachedGNPCoord.isStable()) || gnpCoord.isStable()) {
		    cachedGNPCoord = gnpCoord;
		    lastGNPSetTime = currTime;
		    esmScribePolicy.updateGNPCoord();
		    
		} else {
		    // We retain the old coordinates
		}
	    }
		
	} catch(Exception e) {
	     if(LibraTest.logLevel <= Logger.WARNING) myPrint("Warning: Exception while contacting GNP server", Logger.WARNING);
	}
	 if(LibraTest.logLevel <= 880) myPrint("SysTime: " + System.currentTimeMillis() + " Current GNP COORDINATES are " + cachedGNPCoord, 880);	

    }


    
    public static String asString(byte[] array) {
	String s = "";
	for(int i=0; i<array.length; i++) {
	    int val = (int)array[i];
	    if(val < 0) {
		val = 256 + val;
	    }
	    s = s + val + ".";
	}
	return s;
    }


    // We need to update the local metadata and do a smart propagation of load updates for alll groups
    public void overloadAction() {
	if(LibraTest.logLevel <= 875) myPrint("OVERLOAD Action", 875);
	ESMContent esmContent;
	for(int index=0; index< (NUMGROUPS*LibraTest.NUMTREES); index++) {
	    if((index % NUMGROUPS) < NUMTESTGROUPS) {
		if(allTopics[index].isSubscribed) {
		    TopicTaskState tState = allTopics[index];
		    String myTopicName = tState.topicName;
		    int topicNumber = topicName2Number(myTopicName);
		    Topic myTopic = tState.topic;
		    NodeHandle parent = myScribe.getParent(myTopic);
		    int[] paramsLoad = new int[2];
		    int[] paramsLoss = new int[1];
		    int time = 1000;
		    int pathLength = 0;
		    byte[] paramsPath = null;
		    paramsLoad[0] = 1;
		    paramsLoad[1] = 1;
		    paramsLoss[0] = 5;
		    esmContent = new ESMContent(myTopicName,false, hostIp,dummyesmServerPort, dummyesmOverlayId, paramsLoad,paramsLoss, time, pathLength,paramsPath,1,cachedGNPCoord);
		    esmContent.setESMRunId(dummyesmRunId);
		    esmScribePolicy.leafMetadata.put(myTopic, esmContent);
		}
	    }
	}

	// We will also explicity invoke the sendRefreshUpdate on al topics. Note that the piggy backing will be taken care of automatically
	int numLoops = 0;
	int count = 0;
	while(numLoops < (NUMGROUPS*LibraTest.NUMTREES)) {
	    numLoops ++;
	    rTurn = (rTurn + 1) % (NUMGROUPS*LibraTest.NUMTREES);
	    count = count + fineLeafSendRefreshUpdateForTopic(rTurn,false);
	}
	

    }

    // We need to update the local metadata and do a smart propagation of load updates 
    public void underloadAction() {
	if(LibraTest.logLevel <= 875) myPrint("UNDERLOAD Action", 875);
	ESMContent esmContent;
	for(int index=0; index< (NUMGROUPS*LibraTest.NUMTREES); index++) {
	    if((index % NUMGROUPS) < NUMTESTGROUPS) {
		if(allTopics[index].isSubscribed) {
		    TopicTaskState tState = allTopics[index];
		    String myTopicName = tState.topicName;
		    int topicNumber = topicName2Number(myTopicName);
		    Topic myTopic = tState.topic;
		    NodeHandle parent = myScribe.getParent(myTopic);
		    int[] paramsLoad = new int[2];
		    int[] paramsLoss = new int[1];
		    int time = 1000;
		    int pathLength = 0;
		    byte[] paramsPath = null;
		    paramsLoad[0] = 0;
		    paramsLoad[1] = 1;
		    paramsLoss[0] = 5;
		    esmContent = new ESMContent(myTopicName,false, hostIp,dummyesmServerPort, dummyesmOverlayId, paramsLoad,paramsLoss, time, pathLength,paramsPath,1,cachedGNPCoord);
		    esmContent.setESMRunId(dummyesmRunId);
		    esmScribePolicy.leafMetadata.put(myTopic, esmContent);
		}
	    }
	}

	// We will also explicity invoke the sendRefreshUpdate on al topics. Note that the piggy backing will be taken care of automatically
	int numLoops = 0;
	int count = 0;
	while(numLoops < (NUMGROUPS*LibraTest.NUMTREES)) {
	    numLoops ++;
	    rTurn = (rTurn + 1) % (NUMGROUPS*LibraTest.NUMTREES);
	    count = count + fineLeafSendRefreshUpdateForTopic(rTurn,false);
	}
	
    }
    

    
    // This is called for fine grained overload protection from the leaf level only. Will be used when
    // running experiments with varying anycast rate near utilization
    public int fineLeafSendRefreshUpdateForTopic(int tNumber, boolean forceUpdate) {
	long FINELEAFREFRESHUPDATEPERIOD = 25;
	if(!topicsInitialized) {
	    return 0;
	}
	
	long currTime = System.currentTimeMillis();
	// We put the period rate check here because sendRefreshUpdateForTopic() has 2 callpaths : refreshUpdate() , invokeUpdate()
	if((!((currTime - allTopics[tNumber].uTime) > FINELEAFREFRESHUPDATEPERIOD)) && (!forceUpdate)) {
	    return 0;
	}


	int leafTreeStatus = getTreeStatus(tNumber);
	if(!((leafTreeStatus == LEAFSUBSCRIBER) || (leafTreeStatus == INTERMEDIATESUBSCRIBER))) {
	    return 0;
	}

	Topic myTopic = allTopics[tNumber].topic;
	String topicName = allTopics[tNumber].topicName;
	NodeHandle parent = myScribe.getParent(myTopic);


	if(parent == null) {
	    int checkTreeStatus = getTreeStatus(tNumber);
	    if((checkTreeStatus != NONTREEMEMBER) && !isRoot(myTopic)) {
		if(LibraTest.logLevel <= 875) myPrint("WARNING: fineLeafSendRefreshUpdateForTopic( " + myTopic + ", " + tNumber + " , " + forceUpdate + ") has null parent inspite of treestatus: " + checkTreeStatus , 875);
		
	    }
	    return 0;
	}

	if(LibraTest.logLevel <= 850) myPrint("fineLeafSendRefreshUpdateForTopic( " + myTopic + ", " + tNumber + " , " + forceUpdate + ", " + parent + " )", 850);

	// We will now get the different topics for which this parent holds, and shrink this list to maintain the PIOGGYBACK THRESHOLD
	Topic[] aggregateTopicsArray = myScribe.topicsAsParent(parent);
	Vector aggregateTopics = new Vector();
	for(int i=0; i< aggregateTopicsArray.length; i++) {
	    aggregateTopics.add(aggregateTopicsArray[i]);
	}
	// We ensure that while shrinking, we maintain the invariant that this particular topic corresponding to the function parameter 'tNumber' is contained. Since we shrink from the end, we make sure that this topic is present at the head
	aggregateTopics.remove(myTopic);
	aggregateTopics.add(0, myTopic);

	// We will now build the AggregateESMContent, while adding only the updates that need to be propagated and also ensuring a max of PIGGYBACKUPDATETHRESHOLD
	AggregateESMContent aggregateContent = new AggregateESMContent();
	for(int i=0; i< aggregateTopics.size();i++) {
	    Topic aggregTopic = (Topic) aggregateTopics.elementAt(i);
	    if(!parent.equals(myScribe.getParent(aggregTopic))) {
		if(LibraTest.logLevel <= Logger.WARNING) myPrint("WARNING: Inconsistency in allParents data structure in Scribe", Logger.WARNING);
		if(LibraTest.logLevel <= Logger.WARNING) myPrint("topic: " + aggregTopic + ", parent: " + parent + ", aggregParent: " + myScribe.getParent(aggregTopic), Logger.WARNING);

		if(LibraTest.logLevel <= Logger.WARNING) myPrint("ERROR: We will print the allParents data structure", Logger.WARNING);
		((ScribeImpl)myScribe).printAllParentsDataStructure();
		
		System.exit(1);
	    }

	    String aggregTName = topic2TopicName(aggregTopic);
	    int aggregTNumber = topicName2Number(aggregTName);

	    // Note : We have added the actual desired topic in the front, However we do also piggyback the other topics on this message but only if they did not violate the refreshTimeCheck
	    if((!((currTime - allTopics[aggregTNumber].uTime) > FINELEAFREFRESHUPDATEPERIOD)) && !(forceUpdate && (tNumber == aggregTNumber))) {
		// We need not aggregate this topic
		continue;
	    }

	    

	    
	    // This means that a chance was given
	    allTopics[aggregTNumber].setUTime(System.currentTimeMillis());
	
	    int treeStatus = getTreeStatus(aggregTNumber);
	    
	    boolean needsUpdate = false;
	    // This could be null
	    ESMContent esmContentPrev = null;
	    ESMContent esmContentNew = null;
	    ESMContent esmContentUpdate = null;	
	    
	    if(esmScribePolicy.prevMetadata.containsKey(aggregTopic)) {
		esmContentPrev= (ESMContent) esmScribePolicy.prevMetadata.get(aggregTopic);
	    }
       
	    if(treeStatus == NONTREEMEMBER) {
		// We do nothing
	    } else if(treeStatus == LEAFSUBSCRIBER) {
		if(esmScribePolicy.leafMetadata.containsKey(aggregTopic)) {
		    esmContentNew= (ESMContent) esmScribePolicy.leafMetadata.get(aggregTopic);		
		}
	    } else if(treeStatus == INTERMEDIATESUBSCRIBER) {
		ESMContent esmContentLeaf = null;
		ESMContent esmContentIntermediate = null;
		if(esmScribePolicy.leafMetadata.containsKey(aggregTopic)) {
		    esmContentLeaf= (ESMContent) esmScribePolicy.leafMetadata.get(aggregTopic);		
		}
		ESMScribePolicy.ESMTopicManager manager = null;
		if(esmScribePolicy.intermediateMetadata.containsKey(aggregTopic)) {
		    manager = (ESMScribePolicy.ESMTopicManager)esmScribePolicy.intermediateMetadata.get(aggregTopic);
		    manager.rebuildESMContent();
		    esmContentIntermediate = manager.esmContent;
		}
		esmContentNew = esmScribePolicy.aggregate(esmContentLeaf,esmContentIntermediate);
	    } else if(treeStatus == ONLYINTERMEDIATE) {
		ESMContent esmContentIntermediate = null;
		ESMScribePolicy.ESMTopicManager manager = null;
		if(esmScribePolicy.intermediateMetadata.containsKey(aggregTopic)) {
		    manager = (ESMScribePolicy.ESMTopicManager)esmScribePolicy.intermediateMetadata.get(aggregTopic);
		    manager.rebuildESMContent();
		    esmContentIntermediate = manager.esmContent;
		}
		esmContentNew = esmScribePolicy.aggregate(null,esmContentIntermediate);
	    }

	    if(esmContentNew == null) {
		needsUpdate = false;
	    } else if(esmContentPrev == null) {
		// We have to propagate
		needsUpdate = true;
	    } else {
		// We we DISABLE UpdateAcks, we just check for changes in ESMContent 
		//if(parent.equals(esmContentPrev.lastRefreshParent) && esmContentNew.negligibleChange(esmContentPrev) && (esmContentPrev.lastRefreshTime < esmContentPrev.lastUpdateAckTime)) {
		if(parent.equals(esmContentPrev.lastRefreshParent) && esmContentNew.negligibleChange(esmContentPrev)) {
		    
		    // The parent acknowledged receiving the most recent info
		    needsUpdate = false ;
		    //System.out.println("Most updated data already reflected at parent");
		} else {
		    needsUpdate = true;
		}
	    }
	    if(needsUpdate) {
		// We will send the update
		esmContentUpdate = new ESMContent(esmContentNew);
		if(esmContentUpdate == null) {
		    if(LibraTest.logLevel <= Logger.WARNING) myPrint("WARNING: esmContentUpdate is null, esmContentNew= " + esmContentNew, Logger.WARNING);
		} else {
		    esmContentUpdate.setLastRefreshParent(parent);
		    esmContentUpdate.setLastRefreshTime(System.currentTimeMillis());
		    esmScribePolicy.prevMetadata.put(aggregTopic,esmContentUpdate);
		    if(printEnable(aggregTNumber)) {
			if (LibraTest.logLevel <= 850) myPrint("Node AGGREGATEUPDATING for Topic[ " + aggregTName + " ]= "+ aggregTopic + "esmcontent= " + esmContentUpdate + " to parent= " + myScribe.getParent(aggregTopic), 850);
		    }
		    aggregateContent.appendUpdate(aggregTopic, esmContentUpdate);

		    // We do the check after adding the content becuase we have already updated the lastRefresh time etc assuming that the updated would be aggregated
		    if(aggregateContent.getNumUpdates() >= LibraTest.PIGGYBACKUPDATETHRESHOLD) {
			break;
		    } 
		    
		}
	       
	    } else {
		// We not not do anything
	    }
	
	}
	if(aggregateContent.getNumUpdates() > 0) {
	    // We use myTopic here but could have used any of the topics in aggregateContent, since the Scribe layer uses this to decide whom to send the update to
	    if (LibraTest.logLevel <= 850) myPrint("SysTime: " + System.currentTimeMillis() + " fineLeafSendRefreshUpdateForTopic( " + tNumber + ", " + aggregateContent + " )", 850);
	    myScribe.sendUpdate(myTopic,aggregateContent);
	    return 1;
	} else {
	    return 0;
	}

    }




    class PublishContent implements Message {
	public void dump(ReplayBuffer buffer, PastryNode pn) {

	}

	public int getPriority() {
	    return 0;
	}
    }


    class AliveContent implements Message {
	public void dump(ReplayBuffer buffer, PastryNode pn) {

	}

	public int getPriority() {
	    return 0;
	}
    }


    class PingAliveContent implements Message {
	public void dump(ReplayBuffer buffer, PastryNode pn) {

	}

	public int getPriority() {
	    return 0;
	}
    }


    class AnycastContent implements Message {
	public void dump(ReplayBuffer buffer, PastryNode pn) {

	}

	public int getPriority() {
	    return 0;
	}
    }

    class SubscribeContent implements Message {
	public void dump(ReplayBuffer buffer, PastryNode pn) {

	}

	public int getPriority() {
	    return 0;
	}
    }


    class CheckContent implements Message {
	public void dump(ReplayBuffer buffer, PastryNode pn) {

	}

	public int getPriority() {
	    return 0;
	}
    }


    class SwitchingContent implements Message {
	public void dump(ReplayBuffer buffer, PastryNode pn) {

	}

	public int getPriority() {
	    return 0;
	}
    }

    class UpdateContent implements Message {
	public void dump(ReplayBuffer buffer, PastryNode pn) {
		    
	}

	public int getPriority() {
	    return 0;
	}
    }


    class RefreshUpdateContent implements Message {
	public void dump(ReplayBuffer buffer, PastryNode pn) {

	}

	public int getPriority() {
	    return 0;
	}
    }


    class DownwardUpdateContent implements Message {
	public void dump(ReplayBuffer buffer, PastryNode pn) {

	}

	public int getPriority() {
	    return 0;
	}
    }

    class GrpMetadataRequestThreadContent implements Message {
	public void dump(ReplayBuffer buffer, PastryNode pn) {

	}

	public int getPriority() {
	    return 0;
	}
    }


    class CacheRootContent implements Message {
	public void dump(ReplayBuffer buffer, PastryNode pn) {

	}

	public int getPriority() {
	    return 0;
	}
    }


    class LeafsetContent implements Message {
	public void dump(ReplayBuffer buffer, PastryNode pn) {

	}

	public int getPriority() {
	    return 0;
	}
    }


    class GNPContent implements Message {
	public void dump(ReplayBuffer buffer, PastryNode pn) {

	}

	public int getPriority() {
	    return 0;
	}
    }




  
    /************ Some passthrough accessors for the myScribe *************/
    public boolean isRoot(Topic myTopic) {
	return myScribe.isRoot(myTopic);
    }
    
    public NodeHandle getParent(Topic myTopic) {
	// NOTE: Was just added to the Scribe interface.  May need to cast myScribe to a
	// ScribeImpl if using 1.4.1_01 or older.
	// return ((ScribeImpl)myScribe).getParent(myTopic); 
	return myScribe.getParent(myTopic); 
    }
    
    public NodeHandle[] getChildren(Topic myTopic) {
	return myScribe.getChildren(myTopic); 
    }
    
    public String pathAsString(MyScribeContent.NodeIndex[] plIndices) {
	String s = "";
	MyScribeContent.NodeIndex val = null;
	
	s = s + "[ ";
	for(int i=0; i< plIndices.length; i++) {
	    val = plIndices[i];
	    //s = s + plNodes[val].nodeName + " "; 
	    
	    if(LibraTest.MODELNET) {
		s =s + val.bindIndex + ":" + val.jvmIndex + ":" + val.vIndex + " ";
	    } else {
		s =s + ((rice.pastry.socket.SocketPastryNode)node).getPLName(val.bindIndex) + ":" + val.jvmIndex + ":" + val.vIndex + " ";
	    }
	}
	s = s + "]";
	return s;
    }

    
    public boolean printEnable(int streamId) {
	return true;
	/*
	if((streamId % LibraTest) >= NUMTESTGROUPS) {
	    return true;
	} else {
	    return false;
	}
	*/
    } 

    public boolean printTreeStats(int streamId) {
	return true;

	/*
	if((streamId % LibraTest) >= NUMTESTGROUPS) {
	    return true;
	} else {
	    return false;
	}
	*/
    } 

    
}



