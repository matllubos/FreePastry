package rice.p2p.libra;


import rice.replay.*;
import java.util.*;
import java.lang.String;
import java.io.*;
import java.net.*;

import rice.p2p.util.MathUtils;
import java.text.*;

import rice.environment.logging.*;
import rice.p2p.commonapi.Application;
import rice.p2p.commonapi.CancellableTask;
import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.Id;
import rice.p2p.commonapi.Message;
import rice.p2p.commonapi.NodeHandle;
import rice.p2p.commonapi.NodeHandleSet;
import rice.p2p.commonapi.RouteMessage;
import rice.p2p.splitstream.SplitStream;
import rice.p2p.splitstream.SplitStreamImpl;
import rice.pastry.PastryNode;
import rice.pastry.commonapi.PastryIdFactory;

import rice.p2p.splitstream.*;
import rice.p2p.scribe.*;



/**
 * We implement Application to receive regular timed messages (see lesson5).
 * We implement ScribeClient to receive scribe messages (called ScribeContent).
 * 
 * @author Jeff Hoye
 */
public class MySplitstreamClient implements ESMClient, SplitStreamClient, Application {

    public String nodeName = "";

    public int bindIndex;

    // instance id for the Pastry JVM
    public int jvmIndex;


    // Virtual node index
    public int vIndex;

    // Every node subscribes to Topic_0, this serves as the technique fo nodes
    // to have a virtual clock 'time' that is broadcast on this group
    // For the rest of the groups the approximtae group size (fraction of all Planet lab nodes is decided using a probability value)
    // WARNING : NUMGROUPS should atleast be 2, because the first group is for the virtual clock

    // We have atmost 5 channels in Splitstream for the hardcoded publishers
    public static int NUMGROUPS = 5;
    // The top half of the groups are used for regression tests, the rest are used by ESM
    public static int NUMTESTGROUPS = 5;


    /****  These varibles are used for interaction between ScribeServer and ESM Client***/


    public static final byte REGISTEROPCODE = 0;
    public static final byte ANYCASTOPCODE = 1;
    public static final byte SUBSCRIBEOPCODE = 2;
    public static final byte UNSUBSCRIBEOPCODE = 3;
    public static final byte UPDATEOPCODE = 4;
    public static final byte ANYCASTACKOPCODE = 5;
    public static final byte PROSPECTIVECHILDOPCODE = 6;
    public static final byte ANYCASTFAILUREOPCODE = 7;
    
    // This is registering the dummy ESM server
    public static final byte DUMMYREGISTEROPCODE = 8;
    public static final byte NOP = 9; //no operation
    public static final byte ISALIVE = 10; // The centralized monitor can send a ping to Scribe server with this opcode
                                          // to check that it is alive


    public static final byte LOADMETRIC = 1;
    public static final byte LOSSMETRIC = 2;
    public static final byte PATHMETRIC = 3;
    // This will be echoed back to the client on success
    public static final byte SUCCESSCODE = 100;


    // The subscribes take place in a periodic thread
    // In each subscribe we subscribe atmost SUBSCRIBEBURST
    public static int SUBSCRIBEBURST = 2;
    public static boolean SUBSCRIBEDONE = false;



    // The publishing takes place in a periodic thread, where each node publishes at most PUBLISHBURST times for the groups for which he is the root
    // WARNING: This value should be atleast 2, so that in each period we can publish to the virtual clock group and an additional group
    public static int PUBLISHBURST = 5;
    public static long PUBLISHTHREADPERIOD = 500;
    public static long PUBLISHPERIOD  = 1000;


    // The node switches channels based on this mean period
    public static long SWITCHINGPERIOD = 60000000; // 1000 minutes
    public static long MINSWITCHINGPERIOD = 60000000;
    public static long SWITCHINGTHREADPERIOD = 5000;


    // This period should be greater than the PUBLISHPERIOD, since we monitor things at the granularity of the virtual clock which is incremented at the PUBLISHPERIOD
    public static long CHECKPERIOD = 60000;

    // The sequence number is stored/updated on 5 replicas
    public static int NUMSEQREPLICAS = 5; 
    public static long SEQNUMPROPPERIOD = 5000;


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

  CancellableTask subscribeTask;

  CancellableTask checkTask;

  CancellableTask switchingTask;

  
  /** 
   * My handle to a scribe impl.
   */
  SplitStream mySplitstream;

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


    //public int esmMulticastPort = -1; 
    public int esmServerPort = -1;
    public byte[] esmOverlayId = new byte[4];

    //public int dummyesmMulticastPort = -1; 
    public int dummyesmServerPort = -1;
    public byte[] dummyesmOverlayId = new byte[4];

    // This Ip together with the esm port will be contacted when the anycast succeeds 
    public byte[] hostIp = new byte[4];


    // This is set to true when the first periodic publish thread message is received. We wait for this
    // time to let the network stabilize
    //public boolean initializePSeqNumDone = false;
    //public long initializePSeqNumTime;


    // This contains a mapping of the topic to the TopicName
    public Hashtable topic2TopicName;

    public boolean topicsInitialized = false;

    public String hostName; // This will be appended with the seq numbers for debugging

    // This helps in debugging
    public byte esmRunId = 0;

    public Hashtable stripe2Channel;

    // DATA_SIZE/PUBLISHPERIOD KBps aggregate, [(DATA_SIZE/PUBLISHPERIOD)*(8/base)] Kbps/stripe/sec 
    public int DATA_SIZE = 2;
    private int base = 16;
    public byte[] content = new byte[DATA_SIZE * 1024 / base];


    //public static long LOSSTRACKERPERIOD = 10*PUBLISHPERIOD; // 10 seconds


    public ESMSplitstreamPolicy esmSplitstreamPolicy;


    // This is the last time the node swiched channels
    public long lastSwitchedTime = System.currentTimeMillis();
    // This is the stay time in this channel
    public long currStayTime = 0;

    public int publishOnChannelId = -1;

    /*
    public class LossTracker {
	public String topicName;
	public Vector frameRecvTimes;

	public LossTracker(String topicName) {
	    this.topicName = topicName;
	    this.frameRecvTimes = new Vector();
	    
	}

	public void append(long val) {
	    Long obj = new Long(val);
	    frameRecvTimes.add(obj);
	}

	// This removes stale entries 
	public void removeStale() {
	    long currTime = System.currentTimeMillis();
	    while(!frameRecvTimes.isEmpty()) {
		long recvTime = ((Long)frameRecvTimes.elementAt(0)).longValue();
		if((currTime - recvTime) > LOSSTRACKERPERIOD) {
		    frameRecvTimes.remove(0);
		} else {
		    break;
		}
	    }
	}

	public double getRecvRate() {
	    int numRecv = frameRecvTimes.size();
	    int shouldRecv = (int) ((LOSSTRACKERPERIOD/PUBLISHPERIOD)*base);
	    double recvRate = numRecv * 100/ shouldRecv;
	    return recvRate;
	    
	}


    }
    */

    public class TopicTaskState {
	public String topicName;
	public Channel channel;
	
	// This probability will determine the relative size of this group, for instance if it is 0.5 then this group will consist of half of the planetlab nodes, if it is 0.1, it will consists of approx 1/10th of the planetlab nodes
	public double prob; 
	// This is incremented each time it broadcasts
	// a message on this group, only the replica set around the root for the topic keep this value updated and the current publishes and increments this value
	public int pSeqNum ; 

	// This is the last time this node updated its neighbours of the current sequence number
	public long seqNumPropTime;

	// This is the last time (local clock) that this node published to this group
	public long pTime;


	// This is the last time in ms (local clock) that this node updated to this group
	public long uTime;

	// This was the node that was root for the topic, this information is updated using a periodic thread
	public NodeHandle cachedRoot;

	// This is the last time a message to fetch the current root was sent
	public long cTime;

	// This number is completely local and helps the local node to detect if its anycast failed or succeeded
	public int aSeqNum ; 

	// This is true if the node is subscribed to this topic
	public boolean isSubscribed;

	// This is the last sequence number that this node received on the periodic broadcast to this group, the sequence number helps to track the current group size when we later parse the logs
	public int[] stripeLastRecvSeq = new int[base]; // base = 16

	// This is the last time the node received a broadcast on a group, this helps in detecting tree
	// disconnections
	public long[] stripeLastRecvTime = new long[base]; // base= 16
	

	//public LossTracker lossTracker;

	public TopicTaskState(Channel channel, String name){
	    this.topicName = name;
	    //lossTracker = new LossTracker(name);
	    this.channel = channel; 
	    pSeqNum = 0;
	    aSeqNum = 0;
	    isSubscribed = false;
	    prob = 0.0;
	    for(int i=0; i< base; i++) {
		stripeLastRecvSeq[i] = 0;
	    }
	    pTime = System.currentTimeMillis();
	    uTime = System.currentTimeMillis();
	    for(int i=0; i< base; i++) {
		stripeLastRecvTime[i] = System.currentTimeMillis();
	    }
	    cachedRoot = null;
	    cTime = System.currentTimeMillis();
	    seqNumPropTime = System.currentTimeMillis();
	}
 

	public void setLastRecvSeq(int sNumber,int val) {
	    stripeLastRecvSeq[sNumber] = val;
	}
	
	public void setLastRecvTime(int sNumber, long val) {
	    stripeLastRecvTime[sNumber] = val;
	}

	public void setProb(double val) {
	    prob = val;
	}


	public void setPTime(long val) {
	    pTime = val;
	}

	public void setUTime(long val) {
	    uTime = val;
	}

	public void setSeqNumPropTime(long val) {
	    seqNumPropTime = val;
	}

	


	public void setPSeqNum(int val) {
	    pSeqNum = val;
	}


	public void setASeqNum(int val) {
	    aSeqNum = val;
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
  public MySplitstreamClient(int bindIndex, int jvmIndex, int vIndex, PastryNode node, DatagramSocket udpSocket, UDPLibraServer udpLibraServer ) {
    this.node = node;
    this.bindIndex = bindIndex;
    this.jvmIndex = jvmIndex;
    this.vIndex = vIndex;
    this.udpSocket  = udpSocket;
    udpLibraServer.registerApp(vIndex,this);
    this.endpoint = node.registerApplication(this, "myinstance");
    // construct Scribe
    mySplitstream = new SplitStreamImpl(node, "lesson6instance");
    myScribe = mySplitstream.getScribe();
    try {
	// we will set our own Splitstream policy
	esmSplitstreamPolicy = new ESMSplitstreamPolicy(myScribe,mySplitstream, this);
	mySplitstream.setPolicy(esmSplitstreamPolicy);
    } catch (Exception e) {
	System.out.println("exception= " + e);
	e.printStackTrace();
    }

    try {
	InetAddress localAddress = InetAddress.getLocalHost();
	nodeName = localAddress.getHostName();
    } catch(Exception e) {
	System.out.println("ERROR: While getting nodeName");
    }

    stripe2Channel = new Hashtable();
    topic2TopicName = new Hashtable();
    allTopics = new TopicTaskState[NUMGROUPS];
    setGroupParams();
    topicsInitialized = true;
    myPrint("App[" + vIndex + "] isready " + endpoint.getLocalNodeHandle(), 850);
    
    //boolean dummyRegisterSuccess = false;
    //while(!dummyRegisterSuccess) {
    //dummyRegisterSuccess = reqDummyRegister();
    //}
    //startGNPTask();
    startPublishTask(); 
    startCheckTask();
    //startSubscribeTask();
    //startAnycastTask(); 
    startSwitchingTask();
    //startDummyUpdateTask();
    //startRefreshUpdateTask();
    //startCacheRootTask();

  }

    /************ Some passthrough accessors for the myScribe *************/
    public boolean isRoot(Id id) {
	NodeHandleSet set = endpoint.replicaSet(id, 1);
	if (set.size() == 0)
	    return false;
	else
	    return set.getHandle(0).getId().equals(endpoint.getId());
    }


    // When the node boots up, it queries its replica set to fetch the latest PSeqNum for the different groups
    /*
    public void initializePSeqNum() {
	for(int i=0; i<NUMGROUPS; i++) {
	    String topicName = allTopics[i].topicName;
	    Channel myChannel = allTopics[i].channel; 
	    if(isRoot(myChannel.getChannelId().getId())) {
	 	int currSeq = allTopics[i].pSeqNum;
		NodeHandleSet set = endpoint.replicaSet(myChannel.getChannelId().getId(), NUMSEQREPLICAS);
		RequestPublishStateMsg reqPStateMsg = new RequestPublishStateMsg(i, allTopics[i].topicName, currSeq, endpoint.getLocalNodeHandle());
		System.out.println("SysTime: " + System.currentTimeMillis() +  " Node "+endpoint.getLocalNodeHandle()+" REQUESTINGPSTATE for Topic[ "+ topicName + " ] " + myChannel);
		for(int j=0; j < set.size(); j++) {
		    NodeHandle replica = set.getHandle(j);
		    endpoint.route(null, reqPStateMsg , replica);
		}
	    }
	}
	initializePSeqNumDone = true;
    } 
    */




    public void myPrint(String s, int priority) {
	node.getEnvironment().getLogManager().getLogger(MyLibraClient.class,null).log(priority,s);

    }


    public String stripe2TopicName(Stripe s) {
	Channel channel = (Channel)stripe2Channel.get(s);
	String topicName = (String)topic2TopicName.get(channel);
	return topicName;

    }


    public int topicName2Number(String name) {
	for(int i=0; i< NUMGROUPS; i++) {
	    String s = "_" + i;
	    if(name.endsWith(s)) {
		return i;
	    }
	}
	System.out.println("ERROR: TopicNumber could not be extracted from " + name);
	return -1;
    }


    // This sets the prob values so that the approximate group sizes is set
    public void setGroupParams() {
	// The prob values is chosen in a linear function [0.25,0.75]
	for(int i=0; i<NUMGROUPS; i++) {
	    String topicName = "C_" + i;
	    ChannelId cid = new ChannelId((new PastryIdFactory(node.getEnvironment())).buildId(topicName));
	    Channel channel = mySplitstream.attachChannel(cid);
	    
	    Stripe[] stripes = channel.getStripes();
	    for (int j = 0; j < stripes.length; j++) {
		stripe2Channel.put(stripes[j],channel);
	    }

	    topic2TopicName.put(channel, new String(topicName));
	    myPrint(topicName + " " + channel, 850);
	    
	    TopicTaskState tState= new TopicTaskState(channel, topicName);
	    double val;

	    if(i== 0) {
		// Every node subscribes to this group
		val = 1.0;
	    } else if(i >=NUMTESTGROUPS) {
		val = 0;
	    } else {
		val = UPPERTHRESH - ((UPPERTHRESH - LOWERTHRESH)/ ((double) NUMTESTGROUPS))*i;
		
	    }
	    allTopics[i] = tState;
	    tState.setProb(val);
	}
	// We also set the publishers for the different channels
	setPublishOnChannelId();

    }    

    // This sets the publishers for the different channels
    public void setPublishOnChannelId() {

	// We only allow the zeroth modelnetVNode/zeroth Pastry virtual node to publish
	if(!((vIndex ==0) && (jvmIndex==0))) {
	    return;
	}
	if(nodeName.startsWith("sys")) {
	    // We are testing our code on sys machines
	    if(nodeName.startsWith("sys01")) {
		publishOnChannelId = 0;
	    } else if(nodeName.startsWith("sys02")) {
		publishOnChannelId = 1;
	    } else if(nodeName.startsWith("sys03")) {
		publishOnChannelId = 2;
	    } else if(nodeName.startsWith("sys04")) {
		publishOnChannelId = 3;
	    } else if(nodeName.startsWith("sys05")) {
		publishOnChannelId = 4;
	    }
	}
    }


    public void startPublishTask() {
	publishTask = endpoint.scheduleMessage(new PublishContent(), 15000, PUBLISHTHREADPERIOD);    
    }

    public void startSwitchingTask() {
	switchingTask = endpoint.scheduleMessage(new SwitchingContent(), 60000, SWITCHINGTHREADPERIOD);    
    }

    /*
    public void startSubscribeTask() {
	subscribeTask = endpoint.scheduleMessage(new SubscribeContent(), 1*60000, 5000);    
    }
    */

    
    public void startCheckTask() {
	checkTask = endpoint.scheduleMessage(new CheckContent(), 180000, CHECKPERIOD);    
    }
  
    

    
    /***** Methods in interface Application ******/

    public boolean forward(RouteMessage message) {
	return true;
    }
    
    
    public void update(NodeHandle handle, boolean joined) {
    
    }
    

    public void deliver(Id id, Message message) {
	long currTime = System.currentTimeMillis(); 
	
	
	if (message instanceof PublishContent) {
	    if(!topicsInitialized) {
	      return;
	    }
	    /*
	    if(!initializePSeqNumDone) {
		initializePSeqNum();
		initializePSeqNumTime = currTime;
	    } else if((currTime - initializePSeqNumTime) < 15000){
		// We wait for some time to get updates on the PSeqNums
	    } else {
	    */

	    sendMulticast();
	    
	}
    
	if (message instanceof SubscribeContent){
	    if(!topicsInitialized) {
		return;
	    }
	    sendSubscribe();
	}
	
	if (message instanceof CheckContent){
	    if(!topicsInitialized) {
		return;
	    }
	    invariantChecking();
	}


	if (message instanceof SwitchingContent){
	    if(!topicsInitialized) {
		return;
	    }
	    switchStreams();
	}


	 if(message instanceof PublishStateMsg) {
	     if(!topicsInitialized) {
		 return;
	     }
	     PublishStateMsg  myMsg = (PublishStateMsg)message;
	     int topicNumber = myMsg.topicNumber;
	     int seqNum = myMsg.seqNum;
	     String topicName = myMsg.topicName;
	     //System.out.println("Received PSTATE message from " + myMsg.getSource() + " for Topic[ " + topicName + " ]= " + myMsg.getTopic() + " Seq= " + seqNum);
	     allTopics[topicNumber].setPSeqNum(seqNum);
	 }

	 if(message instanceof RequestPublishStateMsg) {
	     if(!topicsInitialized) {
		 return;
	     }
	     RequestPublishStateMsg  myMsg = (RequestPublishStateMsg)message;
	     int topicNumber = myMsg.topicNumber;
	     String topicName = myMsg.topicName;
	     int requestorSeqNum = myMsg.seqNum;
	     NodeHandle requestor = myMsg.getSource();
	     
	     //System.out.println("Received PSTATE message from " + myMsg.getSource() + " for Topic[ " + topicName + " ]= " + myMsg.getTopic() + " Seq= " + seqNum);
	     int mySeqNum  = allTopics[topicNumber].pSeqNum;
	     // If the requestor needs to be updated send him the updated PSTATE msg
	     // Note : This mechanism is used to update the newly joined node of the sequence number, so there is no chance that the requestorSeqNum is greater than mySeqNum
	     if(mySeqNum > requestorSeqNum) {
		 PublishStateMsg pStateMsg = new PublishStateMsg(topicNumber, allTopics[topicNumber].topicName,  mySeqNum, endpoint.getLocalNodeHandle());
		 endpoint.route(null, pStateMsg , requestor);
	     }
	 }
	 

    }

    // This should be triggered every second because it ensures that the root for the channel is always subscribed
    public void invariantChecking() {
	long currTime = System.currentTimeMillis();
	for(int i=0; i<NUMGROUPS; i++) {
	    String topicName = allTopics[i].topicName;
	    boolean isSubscribed = allTopics[i].isSubscribed;
	    Channel channel = allTopics[i].channel;
	    
	    if(isSubscribed) {
		//allTopics[i].lossTracker.removeStale();
		//double recvRate = allTopics[i].lossTracker.getRecvRate();
		//myPrint("ChannelName [ " + topicName + " ] " + " RecvRate= " + recvRate + "%", 850);
		Stripe[] stripes = channel.getStripes();
		for(int j=0; j<stripes.length; j++) {
		    Topic tp = new Topic(stripes[j].getStripeId().getId()); 
		    NodeHandle parent = myScribe.getParent(tp);
		    Id stripeId = stripes[j].getStripeId().getId();
		    int sNumber = stripe2Number(stripeId);
		    long idleTime = currTime - allTopics[i].stripeLastRecvTime[sNumber];
		    boolean isRoot = isRoot(tp.getId());
		    myPrint("ChannelName [ " + topicName + " ] " + " Stripe[ " + sNumber + "] " + " IdleTime: " + idleTime + " Parent: " + parent + " isRoot: " + isRoot, 850);
		}

	    } else {
		// We check to see if we have children on the channels we are not subscribed to, only case should be if we are the root of a topic
		Stripe[] stripes = allTopics[i].channel.getStripes(); 
		for(int j=0; j< stripes.length; j++) {
		    Topic tp = new Topic(stripes[j].getStripeId().getId());
		    NodeHandle[] children = myScribe.getChildren(tp);
		    if(children.length !=0) {
			// It should be a root for this topic
			if(!isRoot(tp.getId())) {
			    //myPrint("WARNING: Non root node is not subscribed to " + allTopics[i].channel + " but has " + children.length + " children for stripe " + stripes[i], 850);
			}
			   
		    }
		}

	    }
	    
	}
    }
    

    // Note : We will not subcribe/unsubscribe from Stream 0, which is the virtual clock group
    public void switchStreams() {
	//System.out.println("switchStreams()");
	long currTime = System.currentTimeMillis();
	if((currTime - lastSwitchedTime) > currStayTime) { 
	    lastSwitchedTime = currTime;
	    currStayTime = getStayTime();

	    Vector subscribedGrps = new Vector();
	    Vector unsubscribedGrps = new Vector();

	    for(int i=0; i<NUMTESTGROUPS; i++) {
		boolean isSubscribed = allTopics[i].isSubscribed;
		if(isSubscribed) {
		    subscribedGrps.add(new Integer(i));
		} else {
		    unsubscribedGrps.add(new Integer(i));
		}
	    }
	    TopicTaskState tState;
	    Channel myChannel;
	    int rngIndex;
	    String myTopicName;

	    if(subscribedGrps.size() > 0) {
		rngIndex = rng.nextInt(subscribedGrps.size());
		int toUnsubscribeFrom = ((Integer)subscribedGrps.elementAt(rngIndex)).intValue();
		reqUnsubscribe(toUnsubscribeFrom);
	    } 

	    if(unsubscribedGrps.size() > 0) {
		if(subscribedGrps.size() == 0) {
		    // Initially we make everyone subscribe to channel 0, this is for experiments for a single channel
		    reqSubscribe(0);
		    
		} else {
		    rngIndex = rng.nextInt(unsubscribedGrps.size());
		    int toSubscribeTo = ((Integer)unsubscribedGrps.elementAt(rngIndex)).intValue();
		    reqSubscribe(toSubscribeTo);
		}
	    }
	}
	
    }
    


    
    /**
     * Subscribes to a subset of NUMGROUPS using their probability values.
     */
    public void sendSubscribe() {
	// In splitstream we subscribe to a single channel
	// We choose a random channel to subscribe to
	int randIndex = rng.nextInt(NUMTESTGROUPS);
	lastSwitchedTime = System.currentTimeMillis();
	currStayTime = getStayTime();
	reqSubscribe(randIndex);
	subscribeTask.cancel();
	System.out.println("SysTime: " + System.currentTimeMillis() + " SUBSCRIBING TO ALL DESIRED GROUPS IS OVER");
	SUBSCRIBEDONE = true;
	
    }
    

    


    /**
     * Sends the multicast message.
     */
    public void sendMulticast() {
      int count = 0;

      if(publishOnChannelId >= 0) {
	  sendMulticastTopic(publishOnChannelId);
      }

      /*
      int numLoops = 0;
      while((count < PUBLISHBURST) && (numLoops <= NUMTESTGROUPS)) {
	  numLoops ++;
	  pTurn = (pTurn + 1) % NUMTESTGROUPS;
	  count = count + sendMulticastTopic(pTurn);
      }
      */
    
  }


  // Returns 1 if we actually did multicast (i.e if we were root)
    public int sendMulticastTopic(int tNumber) {
	Channel myChannel = allTopics[tNumber].channel;
	long currTime = System.currentTimeMillis();
	//if(isRoot(myChannel.getChannelId().getId()) && ((currTime - allTopics[tNumber].pTime) > PUBLISHPERIOD)) {
	if((currTime - allTopics[tNumber].pTime) >= PUBLISHPERIOD) {
	    int currSeq = allTopics[tNumber].pSeqNum;
	    String myTopicName = allTopics[tNumber].topicName;

	    myPrint("BROADCASTING(" + myTopicName + "," + currSeq + ")", 850);

	    
	    publishOnChannel(myChannel,currSeq);
	    

	    allTopics[tNumber].setPSeqNum(currSeq + 1);
	    allTopics[tNumber].setPTime(System.currentTimeMillis());

	    /*
	    if((currTime - allTopics[tNumber].seqNumPropTime) > SEQNUMPROPPERIOD) {
		allTopics[tNumber].setSeqNumPropTime(System.currentTimeMillis());
		// We will also notify our replica set for this sequence number, this is to ensure that in the presence of churn the new root starts publishing with this sequence number
		PublishStateMsg pStateMsg = new PublishStateMsg(tNumber, allTopics[tNumber].topicName, currSeq + 1, endpoint.getLocalNodeHandle());
		NodeHandleSet set = endpoint.replicaSet(myChannel.getChannelId().getId(), NUMSEQREPLICAS);
		for(int i=0; i < set.size(); i++) {
		    NodeHandle replica = set.getHandle(i);
		    endpoint.route(null, pStateMsg , replica);
		}
	    }
	    */


	    return 1;
	    
	} else {
	    return 0;
	}
    }


    public void publishOnChannel(Channel myChannel, int sequenceNum) {
	
	String str = (new Integer(sequenceNum)).toString();
	str += "\t" + (new Long(node.getEnvironment().getTimeSource().currentTimeMillis()).toString());
	str += "\t";
	byte[] toSend = new byte[DATA_SIZE * 1024 / base];
	System.arraycopy(str.getBytes(), 0, toSend, 0, str.getBytes().length);
	System.arraycopy(content, 0, toSend, str.getBytes().length + 1,toSend.length - (str.getBytes().length + 1));
	Stripe[] stripes;
	stripes = myChannel.getStripes();
	for (int i = 0; i < stripes.length; i++) {
	    stripes[i].publish(toSend);
	}
	
	
	/*
	String str = (new Integer(sequenceNum)).toString();
	str += "\t" + (new Long(node.getEnvironment().getTimeSource().currentTimeMillis()).toString());
	str += "\t";
	byte[] toSend = new byte[str.getBytes().length];
	System.arraycopy(str.getBytes(), 0, toSend, 0, str.getBytes().length);
	Stripe[] stripes;
	stripes = myChannel.getStripes();
	for (int i = 0; i < stripes.length; i++) {
	    stripes[i].publish(toSend);
	}
	*/

    } 


    /****  Methods of SplitStreamClient *****/

    public void joinFailed(Stripe s) {


    }
    
    public int stripe2Number(Id stripeId) {
	String str = stripeId.toString().substring(3, 4);
	char[] c = str.toString().toCharArray();
	int stripe_int = c[0] - '0';
	if (stripe_int > 9)
	    stripe_int = 10 + c[0] - 'A';
	else
	    stripe_int = c[0] - '0';
	return stripe_int; 
    }
    

    public void deliver(Stripe s, byte[] data) {
	String topicName = stripe2TopicName(s);
	String ds = new String(data);
	StringTokenizer tk = new StringTokenizer(ds);
	//String seqNumber = tk.nextToken();
	int seqNumber = Integer.parseInt(tk.nextToken());
	String sentTime = tk.nextToken();
	Id stripeId = (rice.pastry.Id) (s.getStripeId().getId());
	String str = stripeId.toString().substring(3, 4);
	long recv_time = System.currentTimeMillis();
	int diff;
	char[] c = str.toString().toCharArray();
	int stripe_int = c[0] - '0';
	if (stripe_int > 9)
	    stripe_int = 10 + c[0] - 'A';
	else
	    stripe_int = c[0] - '0';

	
	//myPrint("Channel[ " + topicName + " ]" + " Stripe[ " + stripe_int + " ]" + " SeqNum: " + seqNumber + " SentTime: " + sentTime +  " RecvTime: " + recv_time, 850);

	myPrint("Deliver(" + topicName + "," + stripe_int + "," + seqNumber+ ")", 850);
	
	int tNumber = topicName2Number(topicName);
	allTopics[tNumber].setLastRecvTime(stripe_int,recv_time);
	allTopics[tNumber].setLastRecvSeq(stripe_int,seqNumber);
	
	//allTopics[tNumber].lossTracker.append(recv_time);
    }




    /***** Methods of ESMClient *******/

    public void invokeRegister(int esmStreamId, byte[] esmOverlayId, int esmServerPort, int esmDatapathPort,  byte esmRunId) {

    }


    public void invokeDummyRegister(byte[] dummyesmOverlayId, int dummyesmServerPort, int dummyesmDatapathPort, byte dummyesmRunId) {
	
    }


    // This should not be invoked in the case of Splitstream
    public void invokeAnycast(int index, int seqNum, int pathLength, byte[] paramsPath) {

    }

    public void invokeGrpMetadataRequest(int index, int seqNum) {


    }



    public void invokeSubscribe(int index) {
	if((index < 0) || (index >= NUMGROUPS)) {
	    System.out.println("Warning: invokeSubscribe() FAILED, streamId= " + index);
	    
	} else {
	    if(allTopics[index].isSubscribed) {
		System.out.println("Warning: Node SUBSCRIBING inspite of being a subscriber");
		return;
	    }
	    TopicTaskState tState = allTopics[index];
	    String myTopicName = tState.topicName;
	    Channel channel = tState.channel;
	    myPrint("SysTime: " + System.currentTimeMillis() + " ESMRunId: " + esmRunId +" Node SUBSCRIBING for Topic[ " + myTopicName + " ]= "+ channel, 850);
	    tState.setSubscribed(true);

	    Stripe[] stripes = tState.channel.getStripes();
	    for (int i = 0; i < stripes.length; i++) {
		stripes[i].subscribe(this);
	    }

	}

    }

    public void invokeUnsubscribe(int index) {
	if((index < 0) || (index >= NUMGROUPS)) {
	    System.out.println("Warning: invokeUnSubscribe() FAILED, streamId= " + index);
	    
	} else {
	    if(!allTopics[index].isSubscribed) {
		System.out.println("Warning: Node UNSUBSCRIBING inspite of not being subscribed");
		return;
	    }
	    TopicTaskState tState = allTopics[index];
	    String myTopicName = tState.topicName;
	    Channel channel = tState.channel;
	    myPrint("SysTime: " + System.currentTimeMillis() + " ESMRunId: " + esmRunId +" Node " + endpoint.getLocalNodeHandle() + " UNSUBSCRIBING for Topic[ " + myTopicName + " ]= "+ channel, 850);
	    tState.setSubscribed(false);

	    Stripe[] stripes = tState.channel.getStripes();
	    // At this point in ESM-Splistream we might still have more children
	    for (int i = 0; i < stripes.length; i++) {
		/*
		Note : This piece 
		Topic tp = new Topic(stripes[i].getStripeId().getId());
		if(!isRoot(tp.getId())) {
		    // If we are not the root for this stripe we will drop all our children
		    NodeHandle[] children = myScribe.getChildren(tp);
		    for(int j=0; j<children.length; j++) {
			myScribe.removeChild(tp,children[j]);
		    }
		}
		*/
		stripes[i].unsubscribe(this);
	    }
	}	

    }

    public void invokeUpdate(int index, int[] paramsLoad, int[] paramsLoss, int time,  int pathLength, byte[] paramsPath) {

    }



    public void recvUDPQuery(byte[] payload) {

    }

    public void recvUDPAck(byte[] payload) {

    }




    /*** Methods to invoke the UDPServer *****/

     // index - is the stream id 
    public void reqSubscribe(int index) {
    	String host = "localhost";
	try {
	    byte[] msg = new byte[3]; // vIndex + OPCODE + streamId (1 byte)
	    byte streamId = (byte) index;
	    msg[0] = (byte)vIndex;
	    msg[1] = SUBSCRIBEOPCODE;
	    msg[2] = streamId;
	    SocketAddress sentToAddress = new InetSocketAddress(host,LibraTest.SCRIBESERVERPORT);
	    
	    DatagramPacket sendPacket = new DatagramPacket(msg,msg.length, sentToAddress);
	    udpSocket.send(sendPacket);

	}
	catch (UnknownHostException e) { System.err.println(host + ": unknown host."); } 
	catch (IOException e) { System.err.println("I/O error with " + host); }
    }



    // index - is the stream id 
    public void reqUnsubscribe(int index) {
	String host = "localhost";
	try {
	    byte[] msg = new byte[3]; // vIndex + OPCODE + streamId (1 byte)
	    byte streamId = (byte) index;
	    msg[0] = (byte)vIndex;
	    msg[1] = UNSUBSCRIBEOPCODE;
	    msg[2] = streamId;
	    SocketAddress sentToAddress = new InetSocketAddress(host,LibraTest.SCRIBESERVERPORT);
	    
	    DatagramPacket sendPacket = new DatagramPacket(msg,msg.length, sentToAddress);
	    udpSocket.send(sendPacket);
	    
	}
	catch (UnknownHostException e) { System.err.println(host + ": unknown host."); } 
	catch (IOException e) { System.err.println("I/O error with " + host); }
    }


    // This tells us the next stay time
    public long getStayTime() {
	// We currently return SWITCHINGPERIOD +/- [0, (SWITCHINGPERIOD - MINSWITCHINGPERIOD)]
	long val;
	int sgn = rng.nextInt(2);
	int dev = rng.nextInt((int) (1 + SWITCHINGPERIOD - MINSWITCHINGPERIOD));
	if(sgn == 0) {
	    val = SWITCHINGPERIOD - dev;
	} else {
	    val = SWITCHINGPERIOD + dev;
	}
	myPrint(" STAYTIME= " + val, 850);
	return val;
    }


    class PublishContent implements Message {
	public void dump(ReplayBuffer buffer, PastryNode pn) {

	}

	public int getPriority() {
	    return 0;
	}
    }
    
    class SubscribeContent implements Message {
	public void dump(ReplayBuffer buffe, PastryNode pnr) {

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

}
