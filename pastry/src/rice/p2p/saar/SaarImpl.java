
/*
 * Created on May 4, 2005
 */
package rice.p2p.saar;

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
 * @author Animesh Nandi
 *
 * SaarImpl is an extension of Scribe and basically adds the features below :
 *
 *      a) Adds aggregation feature to Scribe. Basically local/aggregated metadata over all topics for which this node is a member of a Scribe tree are updated to its parents in the respective Scribe trees. 
 *
 *      b) Adds redundant Scribe trees per Saar group. We do this by modifying the LSB bits of the base topic and creating additional Scribe trees. All operations - subscribes/unsubscribes/updates/anycasts are performed redundantly over these redundant Scribe groups.
 * 
 *      c) Its also implements abrupt/informed departures from the SAAR control overlay. Notifications enable explicit recovery of Scribe trees before the node departs and also informs leafset members for quicker leafsetrepairs 
 * 
 *      d) There is also support for integration of the Scribe with GNP and updated GNP coordinates are reflected in the metadata 
 *
 *
 */
public class SaarImpl implements ScribeClient, Application, SaarAPI {

    public Logger logger;
    
    public Environment environment;

    public SelectorManager selectorManager;
    
    
    // This denotes if we have explicit leave notifications to data plane neighbors
    public static boolean CONTROLOVERLAYLEAVENOTIFY = true;

    // These fours denote the different states of a nodes in a Saar control tree, Depending on the node's state 
    // the appropriate update task is performed 
    public static final int  NONTREEMEMBER = 1;
    public static final int  INTERMEDIATESUBSCRIBER = 2;
    public static final int  LEAFSUBSCRIBER = 3;
    public static final int ONLYINTERMEDIATE = 4;
    

    public Random rng = new Random(); // created for randomization services
    
    // These are created for the periodic tasks
    CancellableTask aliveTask; 

    CancellableTask updateTask;
    
    CancellableTask downwardUpdateTask;

    CancellableTask gnpTask;

    CancellableTask leafsetTask;

    CancellableTask checkTask;

    public Scribe myScribe; // instance of underlying Scribe layer
    
    
    public Endpoint endpoint;
    
    public PastryNode node;

    public SaarPolicy saarPolicy; // this implements the policy for anycast traversal. Here it basically delegates the responsibility to the appropriate DataplneClient based on the topic 


    // GNP Maintenance Policy - 
    // This is the last time we updated the GNP coordinate. The updation policy is
    //  1. Update if we get  a stable coordinate
    //  2. If unstable {
    //      a.  If the cached GNP is unstable update it
    //      b. If the cached GNP is stable and was got less than GNPCACHETIME, then do not update
    //      c. Actively remove GNP coordinates beyond GNPCACHETIME
    public long lastGNPSetTime = 0;
    // This is the maximum time we cache a GNP coordinate (stable or unstable). Also the updation 
    public long GNPCACHETIME = 600000; // 10 minutes
    public GNPCoordinate cachedGNPCoord = null;
    private InetAddress localAddress; // will be used to contact the GNP service


    public PastryNodeFactory factory;

    public long BOOTTIME = 0; // 
    public int SESSIONTIME = 0; //  in sec . 0- infinite (This is the default option), nonzero - After this many seconds the jvm will exit 
    public boolean overlayLeaveStarted = false;
    public long overlayLeaveOperationsStartTime;


    public static final long UPWARDUPDATEPERIOD  = 1000;
    public long lastUpwardUpdateTime;
    public static final long MAXDAMPENUPWARDUPDATEPERIOD = 500;    // On Ict09-2007 changed from 60000 -> 500 to disable dampening
    public static final long MAXDAMPENDOWNWARDPROPAGATEPERIOD = 500; // On Sep01-2007 We changed from 60000 -> 500 to disallow dampening
    

    public static final long DOWNWARDPROPAGATEPERIOD  = 1000;
    public long lastDownwardPropagateTime;

    public static final long GNPPERIOD  = 60000;
    public static final long CHECKPERIOD  = 60000;
    //public static final long SWITCHINGPERIOD  = 1000;


    public Hashtable topic2saartopic;

    public Hashtable registeredDataplanes; // Currently all types of dataplanes being supported in the SAAR overlay should register their DataplaneClient class which contains code on how to handle the anycast requests


    public Hashtable subscribedTopics; // This is used to check if any of the local node is subscribed to a particular topic. 

    public Hashtable saarTopicManagers; // this is a hashtable of the TopicManagers for which this node is a member of the SAAR Scribe control tree
 

    public static int MAXANYCASTWILLTRAVERSE  = 15; // This is absolute hard bound ( soft bound enforced by traversal threshold) on the number of nodes the anycast will be allowed to traverse before we declare a failure, detected using visited.size() in directAnycast(). This variable is udes in the directAnycast() mehtod in ESMScribePolicy to decide when to remove endnodes from toVisit list before adding fresh nodes in toVisit list. This is crucial to prevent the message size from growing too big becuase it stores prospective handles.   

    /**** (bindIndex,jvmIndex,vIndex) These parameters will be used to include sufficient information in the anycast traversal to trace the path of the anycast. ****/

    // This is the index corresponding to the Planetlabnode or the Modelnet node based on the Ip address it is bound to
    public int bindIndex;

    // Pastry JVM index
    public int jvmIndex; 

    // Pastry Virtual node index
    public int vIndex;
    
    // routingbase/msbdigitpos will be used to derive an index for a Scribe tree for genrating a unique anycastGlobalId
    public int routingBase;
    public int msbdigitpos;


    public NodeHandle centralizedHandle;

    public SaarClient saarClient;



    // This manages the metadata (state variables) of different topics for which this local node is part of the Scribe control tree. Note that there is a different SaarTopicManager for each redundant topic
    public static class SaarTopicManager {
	public Scribe scribe;
	public Topic topic;
	public Hashtable childData; // This hashtable contains the SaarContent associated with the children
	public SaarContent leafContent; // this contains the SaarContent associated with the local node for this topic

	public SaarContent aggContent; 	// This esmContent will contain the aggregate values of the childData and localValue, its fields will be updated lazily only when propagation is required. At that instant the rebuildESMContent() methos is innvoked 

	public SaarContent lastUpdateContent; // This is the previous aggregate content that was updated to the Scribe parent

	public SaarContent lastDownwardPropagateContent; // This is the previous aggregate content that was downward propagated down the Scribe tree provided this node is the root for the tree


	public NodeHandle lastUpdateParent; // This is set to the node (i.e Scribe parent) to which the aggregate content was last updated. Might use this to employ damping of updates if updates and corresponding parent has remain unchanged, but in such cases you might still need to be careful of lost pkts. We current dont use it though
	

       
	public long lastUpdateTime; // This is the last time the node updated its aggregate data to the parent 

	public long lastDownwardPropagateTime; // This is the last time the node propagates the aggregate metadata down in the Scribe tree


	public SaarContent lastGrpSummaryContent; // this is the latest grpSummary information propagated my the root downwards via Scribe publish

	public int controlTreeDepth; // this is the depth of the node in the control tree


	public SaarTopicManager(Scribe scribe, Topic topic) {
	    this.scribe = scribe;
	    this.topic = topic;
	    childData = new Hashtable();
	    leafContent = null;
	    aggContent = null;
	    lastUpdateContent = null;
	    lastUpdateTime = 0;
	    lastUpdateParent = null;
	    lastDownwardPropagateContent = null;
	    lastDownwardPropagateTime = 0;
	    controlTreeDepth = -1;
	}
	


	
	// builds the content as an aggregation of childData and localLeafData
	public void rebuildAggContent() {	    
	    //System.out.println("rebuildAggContent() called");
	    // We will first remove stale child from here
	    Set keySet = childData.keySet();
	    Iterator it = keySet.iterator();
	    Vector toRemove = new Vector();
	    while(it.hasNext()) {
		NodeHandle child = (NodeHandle)it.next();
		if(!scribe.containsChild(topic,child)) {
		    toRemove.add(child);
		}
	    } 
	    for(int i=0; i<toRemove.size(); i++) {
		NodeHandle handle = (NodeHandle) toRemove.elementAt(i);
		childData.remove(handle);
	    }


	    // At this point the metadata contains all current children
	    keySet = childData.keySet();
	    it = keySet.iterator();
	    aggContent = null;
	    while(it.hasNext()) {
		NodeHandle child = (NodeHandle)it.next();
		SaarContent content = (SaarContent)childData.get(child);
		aggContent = SaarContent.aggregate(aggContent,content);
	    }
	    aggContent = SaarContent.aggregate(aggContent,leafContent);
	}

	public void updateChildData(NodeHandle child, SaarContent content) {
	    childData.put(child, content);
	    
	}

	public SaarContent getSaarContent(NodeHandle child) {
	    return (SaarContent) childData.get(child);

	    //if(childData.containsKey(child)) {
	    //return ((SaarContent) childData.get(child));
	    //} else {
	    //return null;
	    //}
	}

	public void removeChildData(NodeHandle child) {
	    childData.remove(child);
	}

	public void updateLeafContent(SaarContent content){
	    leafContent = content;
	}


	public void setLastUpdateContent(SaarContent content) {
	    lastUpdateContent = content;
	}

	public SaarContent getLastUpdateContent() {
	    return lastUpdateContent;
	}

	public void setLastUpdateParent(NodeHandle handle) {
	    lastUpdateParent = handle;
	}


	public NodeHandle getLastUpdateParent() {
	    return lastUpdateParent;
	}


	public void setLastUpdateTime(long time) {
	    lastUpdateTime = time;
	}


	public void setLastDownwardPropagateContent(SaarContent content) {
	    lastDownwardPropagateContent = content;
	}

	public SaarContent getLastDownwardPropagateContent() {
	    return lastDownwardPropagateContent;
	}



	public void setLastDownwardPropagateTime(long time) {
	    lastDownwardPropagateTime = time;
	}


	public void setLastGrpSummaryContent(SaarContent content) {
	    lastGrpSummaryContent = content;
	}

	public SaarContent getLastGrpSummaryContent() {
	    return lastGrpSummaryContent;
	}

	public void setControlTreeDepth(int val) {
	    controlTreeDepth = val;
	}

	public int getControlTreeDepth() {
	    return controlTreeDepth;
	}
	

    } 

    

    





    /**
     * @param node the PastryNode
     */
    public SaarImpl(PastryNode node, String instance, int SESSIONTIME, long BOOTTIME, int bindIndex, int jvmIndex, int vIndex, NodeHandle centralizedHandle, SaarClient saarClient) {
	this.saarClient = saarClient;
	this.SESSIONTIME = SESSIONTIME;
	this.BOOTTIME = BOOTTIME;
	this.centralizedHandle = centralizedHandle;
	this.bindIndex = bindIndex;
	this.jvmIndex = jvmIndex;
	this.vIndex = vIndex;
	this.routingBase = node.getRoutingTable().baseBitLength();
	this.msbdigitpos = rice.pastry.Id.numDigits(routingBase) -1;
	System.out.println("routingBase:" + this.routingBase + ", msbdigitpos:" + this.msbdigitpos);
	try {
	    this.factory = factory;
	    this.environment = node.getEnvironment();


	    if(environment.getParameters().contains("socket_bindAddress")) {
		localAddress = environment.getParameters().getInetAddress("socket_bindAddress");
	    }
	    this.selectorManager = environment.getSelectorManager();
	    logger = environment.getLogManager().getLogger(SaarImpl.class,null);
	    this.node = node;
	    if(SaarTest.logLevel <= 880) myPrint("LocalAddress= " + localAddress, 880);
	    //this.endpoint = node.registerApplication(this, instance);
	    this.endpoint = node.buildEndpoint(this, instance);
	    

	} catch(Exception e) {
	    System.out.println("ERROR: Trying to get localhost ipaddress " + e);
	    e.printStackTrace();
	    System.exit(1);
	}
	// construct Scribe
	myScribe = new ScribeImpl(node, "scribeinstance");
	saarPolicy = new SaarPolicy(this);
	myScribe.setPolicy(saarPolicy);
	subscribedTopics = new Hashtable();
	startUpwardUpdateTask(); // Upward metadata propagation in the Saar Scribe trees
	startDownwardPropagateTask(); // Downward propagation of aggregate metadata
	//startGNPTask(); // GNP related tasks
	startCheckTask(); // Check invariants and implement departures from control overlay
 
	registeredDataplanes = new Hashtable(); 
	subscribedTopics = new Hashtable(); 
	saarTopicManagers = new Hashtable(); 
	topic2saartopic = new Hashtable();
	
	endpoint.register();


    }


    public long getCurrentTimeMillis() {
	return environment.getTimeSource().currentTimeMillis();
    }

    public void myPrint(String s, int priority) {
      if (logger.level <= priority) logger.log(s);
    }



    public DataplaneClient getDataplaneClient(SaarTopic saartopic) {
	return (DataplaneClient)registeredDataplanes.get(saartopic);

    }

    public SaarTopic getTopic2Saartopic(Topic topic) {
	return (SaarTopic) topic2saartopic.get(topic);
    }

    public void startUpwardUpdateTask() {
	checkTask = endpoint.scheduleMessage(new UpwardUpdateContent(), 1200, 500);    
    }

    public void startDownwardPropagateTask() {
	checkTask = endpoint.scheduleMessage(new DownwardPropagateContent(), 1400, 500);    
    }
    
    public void startGNPTask() {
	checkTask = endpoint.scheduleMessage(new GNPContent(), 1600, GNPPERIOD);    
    }


    public void startCheckTask() {
	checkTask = endpoint.scheduleMessage(new CheckContent(), 1800, CHECKPERIOD);    
    }




    public boolean isRoot(Topic myTopic) {
	return myScribe.isRoot(myTopic);
    }
    

    public void queryGNP() {
	// GNP Maintenance Policy - 
	// This is the last time we updated the GNP coordinate. The updation policy is
	//  1. Update if we get  a stable coordinate
	//  2. If unstable {
	//      a.  If the cached GNP is unstable update it
	//      b. If the cached GNP is stable and was got less than GNPCACHETIME, then do not update
	//      c. Actively remove GNP coordinates beyond GNPCACHETIME
	
	 if(SaarTest.logLevel <= 850) myPrint("queryGNP() called", 850);
	 
	 long currTime = getCurrentTimeMillis();
	 if((cachedGNPCoord != null) && ( (currTime - lastGNPSetTime) > GNPCACHETIME)) {
	     // remove stale coordinates
	     cachedGNPCoord = null;
	     lastGNPSetTime = currTime;
	     updateGNPCoord();
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
		     updateGNPCoord();
		     
		 } else {
		     // We retain the old coordinates
		 }
	     }
	     
	 } catch(Exception e) {
	     if(SaarTest.logLevel <= Logger.WARNING) myPrint("Warning: Exception while contacting GNP server", Logger.WARNING);
	 }
	 if(SaarTest.logLevel <= 875) myPrint("SysTime: " + getCurrentTimeMillis() + " Current GNP COORDINATES are " + cachedGNPCoord, 875);	
	 
    }


    // This is called by the queryGNP() routing in MyScribeClient whenever it sees a change in the GNP coordinates
    public void updateGNPCoord() {
	Set keySet = saarTopicManagers.keySet();
	Iterator it = keySet.iterator();
	while(it.hasNext()) {
	    Topic topic = (Topic)it.next();
	    SaarTopicManager sManager = (SaarTopicManager) saarTopicManagers.get(topic);
	    SaarContent saarContent = (SaarContent)sManager.leafContent;
	    if(saarContent != null) {
		saarContent.setGNPCoordAggregator(cachedGNPCoord);
	    }
	} 
    }
    



    // Returns
    //   LEAFSUBSCRIBER - is leaf subscriber ( issubscribed= true and the getChildren() returns empty array)
    //       In this case we update from the metadata in MyCoolstreamingClient
    //   ONLYINTERMEDIATE - is intermediate node only ( isSubscribed= false and getChildren() retruns non empty array)
    //       We update from the metadata at the ESMScribePolicy
    //   INTERMEDIATESUBSCRIBER - is both intermediate and subscriber ( we have to perform the averaging using the data in
    //       ESMScribePolicy as well as the MyCoolstreamingClient
    //   NONTREEMEMBER - not a member of tree
    public int getTreeStatus(Topic myTopic) {
	boolean isSubscribed = isSubscribed(myTopic);
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

    
    public boolean isSubscribed(Topic topic) {
	return subscribedTopics.containsKey(topic);
	
    }


    public void issuePing(NodeHandle remote, int pingId, float networkdelay, int payloadinbytes) {
       
	PingMessage pingMsg = new PingMessage(endpoint.getLocalNodeHandle(), null, pingId, payloadinbytes);
	if(SaarTest.logLevel <= 880) myPrint("issuePing( pingsrc:" + endpoint.getLocalNodeHandle().getId() + " pingId:" + pingId + " pingdest:" + remote.getId()+ " networkdelay: " + networkdelay + " payloadwithoutheaderinbytes: " + payloadinbytes + " )", 880);	    
	endpoint.route(null,  pingMsg, remote);


    }


    
    /***** Methods in interface Application ******/
    
    public boolean forward(RouteMessage message) {
	return true;
    }
    
    
    public void update(NodeHandle handle, boolean joined) {
    
    }



    

    public void deliver(Id id, Message message) {
      
	long currtime = getCurrentTimeMillis(); 



	if(message instanceof PingMessage) {
	    PingMessage pingMsg = (PingMessage)message;
	    NodeHandle sender = pingMsg.getSource();
	    PingAckMessage pingAckMsg = new PingAckMessage(endpoint.getLocalNodeHandle(), pingMsg.getTopic(), pingMsg.getPingId(), pingMsg.getSizeInBytes());
	    if(SaarTest.logLevel <= 880) myPrint("recvPing( pingsrc:" + sender.getId() + " pingId:" + pingMsg.getPingId() + " pingdest:" + endpoint.getLocalNodeHandle().getId()+ " )", 880);
	    endpoint.route(null,  pingAckMsg, sender);

	}


	if(message instanceof PingAckMessage) {
	    PingAckMessage pingAckMsg = (PingAckMessage)message;
	    NodeHandle remote = pingAckMsg.getSource();
	    if(SaarTest.logLevel <= 880) myPrint("recvPingAck( pingsrc:" + endpoint.getLocalNodeHandle().getId() + " pingId:" + pingAckMsg.getPingId() + " pingdest:" + remote.getId()+ " )", 880);

	}






	if(message instanceof DownwardPropagateMessage) {
	    
	    DownwardPropagateMessage dpmsg = (DownwardPropagateMessage)message;
	    Topic topic = dpmsg.getTopic();
	    SaarContent sContent = (SaarContent)dpmsg.getContent();
	    SaarTopicManager sManager = (SaarTopicManager) saarTopicManagers.get(topic);
	    sManager.setLastGrpSummaryContent(sContent);
	    sManager.setControlTreeDepth(sContent.numScribeIntermediates);
	    getDataplaneClient(getTopic2Saartopic(topic)).grpSummary(getTopic2Saartopic(topic), topic, sContent);
	    
	}

	
	if (message instanceof UpwardUpdateContent) {
	    // For all topics for which we are a Scribe tree member do upward propagation, we might also consider to piggyback traffic across groups later
	    	
	    if((currtime - lastUpwardUpdateTime) >= UPWARDUPDATEPERIOD) {
		lastUpwardUpdateTime = currtime;	    
	
		Enumeration keys = saarTopicManagers.keys();
		while(keys.hasMoreElements()) {
		    Topic topic = (Topic) keys.nextElement();
		    singleSendUpwardUpdateForTopic(topic, false);
		}
	    }
	}


	if (message instanceof DownwardPropagateContent) {

	    if((currtime - lastDownwardPropagateTime) >= DOWNWARDPROPAGATEPERIOD) {
		lastDownwardPropagateTime = currtime;	    

		// Check if we are the root for any topic, if yes update the aggregate content and publish it down
		Enumeration keys = saarTopicManagers.keys();
		while(keys.hasMoreElements()) {
		    Topic topic = (Topic) keys.nextElement();
		    if((rice.p2p.saar.simulation.SaarSimTest.CENTRALIZEDSCRIBE && endpoint.getLocalNodeHandle().equals(centralizedHandle)) || ((!rice.p2p.saar.simulation.SaarSimTest.CENTRALIZEDSCRIBE) && myScribe.isRoot(topic))) {
			
			singleSendDownwardPropagateForTopic(topic, false);
		    }
		}
	    }
	    
	}
	

	if(message instanceof OverlayLeaveMessage) {
	    OverlayLeaveMessage olMessage = (OverlayLeaveMessage) message;
	    if(SaarTest.logLevel <= 875) myPrint(endpoint.getId() + ": Received OverlayLeave message from " + olMessage.getSource(), 875);
//	    ((SocketNodeHandle)olMessage.getSource()).markDeadForever();
	    
	}




	if (message instanceof GNPContent) {
	    queryGNP();

	}

	if (message instanceof CheckContent) {

	    /*
	    // implement control overlay churn
	    if(SESSIONTIME > 0) {
		if((currtime - BOOTTIME) >= (SESSIONTIME * 1000)) {		   
		    if(CONTROLOVERLAYLEAVENOTIFY) {
			if(!overlayLeaveStarted) {
			    overlayLeaveStarted = true;
			    if(SaarTest.logLevel <= 875) myPrint("SysTime: " + currtime + " Node initiating overlayLeaveOperations() after expiry of SESSIONTIME of " + SESSIONTIME + " seconds", 875);
			    overlayLeaveOperationsStartTime = currtime;
			    overlayLeaveOperations();
			    
			} else {
			    if((currtime - overlayLeaveOperationsStartTime) > 5*1000) {
				if(SaarTest.logLevel <= 875) myPrint("SysTime: " + currtime + " Node smoothly departing overlay after expiry of SESSIONTIME of " + SESSIONTIME + " seconds", 875);
				System.exit(1);
				
			    }


			}
			
		    } else {
			if(SaarTest.logLevel <= 875) myPrint("SysTime: " + currtime + " Node abruptly killing itself after expiry of SESSIONTIME of " + SESSIONTIME + " seconds", 875);
			System.exit(1);
		    }
		    
		}
	   }
	    */
	    


	    // Print the leafset
	    if(SaarTest.logLevel <= 875) myPrint("SysTime: " + getCurrentTimeMillis() + " LEAFSET: " + node.getLeafSet(), 875);

	    /*
	    // Make sure that PNS is working by checking the average proximity values in the different rows of routing table 
	    RoutingTable rt = node.getRoutingTable();
	    int numrows = rt.numRows();
	    int[] rtProximity = new int[numrows];
	    for(int i=0; i< numrows; i++) {
		rtProximity[i] = -1;
	    }
	    for(int i=0; i < numrows; i++) {
		//System.out.println("Row[" + i + "]");
		RouteSet[] rowset = rt.getRow(i);
		int sumProximity = 0;
		int countProximity = 0;
		int avgRowProximity = -1;
		for(int j=0; j< rowset.length; j++) {
		    if(rowset[j] != null) {
			int proximity = rowset[j].avgProximity();
			if(proximity !=-1) {
			    sumProximity = sumProximity + proximity;
			    countProximity ++;
			}
		    }
		}
		if(countProximity>0) {
		    avgRowProximity = sumProximity/countProximity;
		}
		rtProximity[i] = avgRowProximity;
		
	     }
	    String rtProximityString = "";
	    for(int i=0; i< numrows; i++) {
		rtProximityString = rtProximityString + rtProximity[i] + " ";
		 
	    }
	    
	    if(SaarTest.logLevel <= 880) myPrint("SysTime: " + getCurrentTimeMillis() + " ROUTINGTABLEPROXIMITY: " + rtProximityString, 880);
	    */

	    
	}

	if (message instanceof UpdateMessage) {
	  UpdateMessage um = (UpdateMessage)message;	  
	  saarPolicy.updateChild(um.getTopic(), um.getSource(), um.getContent());
	  return;
	}

    }




    // This is code to do a different update per group. 'single' refers to the fact that this code currently does not piggyback across groups. When we have lots of groups in the control overlay, piggybacking across groups is critical
    public int singleSendUpwardUpdateForTopic(Topic myTopic, boolean forceUpdate) {
	long currTime = getCurrentTimeMillis();
	SaarTopicManager sManager = (SaarTopicManager) saarTopicManagers.get(myTopic);
	// We put the period rate check here because sendRefreshUpdateForTopic() has 2 callpaths : refreshUpdate() , invokeUpdate(). Additionally we have an option of forcing the update
	if((!((currTime - sManager.lastUpdateTime) > UPWARDUPDATEPERIOD)) && (!forceUpdate)) {
	    return 0;
	}

	NodeHandle parent = myScribe.getParent(myTopic);
	sManager.rebuildAggContent();

	boolean needsUpdate = false;
	// This could be null
	SaarContent sContentUpdate = null;	
	

	if((parent == null) || ((sManager.aggContent == null) && (sManager.getLastUpdateContent() == null))) {
	    return 0;
	} else if(parent.equals(sManager.getLastUpdateParent()) && (sManager.aggContent != null) && sManager.aggContent.negligibleChangeUpwardUpdate(sManager.getLastUpdateContent()) && ((currTime - sManager.lastUpdateTime) < MAXDAMPENUPWARDUPDATEPERIOD)) {
	    // We will dampen the update
	    if (SaarTest.logLevel <= 875) myPrint("Node DAMPENING UPDATING for Topic[ " + myTopic + " ]" + " to parent= " + myScribe.getParent(myTopic), 875);
	    return 0;

	} else {
	    sManager.setLastUpdateParent(parent);
	    sManager.setLastUpdateContent(sManager.aggContent);
	    sManager.setLastUpdateTime(currTime);
	    // We will send the update
	    sContentUpdate = SaarContent.duplicate(sManager.aggContent);
	    // We will set the aggregator/gnpcoordAggregator of the sContentUpdate here
	    if(sContentUpdate != null) {
		sContentUpdate.setGNPCoordAggregator(cachedGNPCoord);
		sContentUpdate.setAggregator(endpoint.getLocalNodeHandle());
	    }
	    if (SaarTest.logLevel <= 850) myPrint("Node UPDATING for Topic[ " + myTopic + " ]" + "saarcontent= " + sContentUpdate + " to parent= " + myScribe.getParent(myTopic), 850);
//      myScribe.sendUpdate(myTopic,sContentUpdate);
	    sendUpdate(myTopic,sContentUpdate);
	    return 1;
	} 
    }

    /**
     * Call updateChild on the policy of the parent
     * @param topic
     * @param update
     */
    public void sendUpdate(Topic topic, SaarContent update) {
      NodeHandle parent = myScribe.getParent(topic);
      endpoint.route(null, new UpdateMessage(endpoint.getLocalNodeHandle(), topic, update), parent);
    }
    
    
    // The root of the Scribe tree propagates the aggregate metadata downward in the Scribe tree. 'single' implies we do not piggyback across topics. In the case of the centralized version, the centralizedhandle does this job
    public int singleSendDownwardPropagateForTopic(Topic myTopic, boolean forceUpdate) {
	long currTime = getCurrentTimeMillis();
	SaarTopicManager sManager = (SaarTopicManager) saarTopicManagers.get(myTopic);
	// We put the period rate check here because sendRefreshUpdateForTopic() has 2 callpaths : refreshUpdate() , invokeUpdate(). Additionally we have an option of forcing the update
	if((sManager.aggContent == null) || ((!((currTime - sManager.lastDownwardPropagateTime) >= DOWNWARDPROPAGATEPERIOD)) && (!forceUpdate))) {
	    return 0;
	} else if(sManager.aggContent.negligibleChangeDownwardPropagate(sManager.getLastDownwardPropagateContent()) && ((currTime - sManager.lastDownwardPropagateTime) < MAXDAMPENDOWNWARDPROPAGATEPERIOD)) {
	    // We will dampen the update
	    if (SaarTest.logLevel <= 875) myPrint("Node DAMPENING DOWNWARDPROPAGATING for Topic[ " + myTopic + " ] to #numChildren: " + myScribe.numChildren(myTopic), 875);
	    return 0;

	} else {
	    sManager.setLastDownwardPropagateTime(currTime);
	    sManager.setLastDownwardPropagateContent(sManager.aggContent);
	    // For the simulator version we will also set the last RootMetadata in the simulator
	    //saarClient.simulator.grpSummaryMetadata.put(getTopic2Saartopic(myTopic), sManager.aggContent);
	    //if(SaarTest.logLevel <= 875) myPrint("SimulatorGrpSumary( "  + getTopic2Saartopic(myTopic) + " , " + sManager.aggContent + ")", 875);		
	    sManager.aggContent.mode = SaarContent.DOWNWARDPROPAGATE;

	    

	    if(rice.p2p.saar.simulation.SaarSimTest.CENTRALIZEDSCRIBE && endpoint.getLocalNodeHandle().equals(centralizedHandle)) {
			    
		// Here the Scribe's Publish message will behave in a awkward way, so we will directly send the desired content to the children
		
		NodeHandle[] mychildren = myScribe.getChildren(myTopic);
		for(int k= 0; k< mychildren.length; k++) {
		    if(SaarTest.logLevel <= 850) myPrint("Publishing downwardpropagatecontent to child " + mychildren[k] + " for topic " + myTopic, 850);		
		    endpoint.route(null,  new DownwardPropagateMessage(endpoint.getLocalNodeHandle(), myTopic, sManager.aggContent), mychildren[k]);
		}
		
		
	    } else {
		myScribe.publish(myTopic, sManager.aggContent);
	    }
	    
	    
	    if (SaarTest.logLevel <= 875) myPrint("Node DOWNWARDPROPAGATING for Topic[ " + myTopic + " ] to #numChildren: " + myScribe.numChildren(myTopic), 875);

	    
	    
	    return 1;
	}
	 

    }
    

    


     // Sends explicit notifications before leaving the overlay
    boolean firstTimeOLO = true;
    public void overlayLeaveOperations() {
      if (firstTimeOLO && logger.level <= Logger.WARNING) {
        logger.log("warning: SaarImpl.overlayLeaveOperations() is not implemented.");
        firstTimeOLO = false;
      }
      if (true) return;
	// We  will iterate on the SaarTopicManagers since this contains the entire list of topics (raw topics not basetopics for whichthis node belongs to the Scribe treee
	if(SaarTest.logLevel <= 875) myPrint("SysTime: " + getCurrentTimeMillis() + " Node "+endpoint.getLocalNodeHandle()+" initiating overlayLeaveOperations()", 875);
	Enumeration scribeTopics = saarTopicManagers.keys();
	while(scribeTopics.hasMoreElements()) {
	    Topic myTopic = (Topic)scribeTopics.nextElement();
	    if(getTreeStatus(myTopic) != NONTREEMEMBER) {
//		myScribe.depart(myTopic, SaarImpl.this);
	    }
	}

  // We should also disable the local node from responding to Pings and other messages so that this node is viewed as dead to other nodes 
//node.setDepart();

	// TODO: Make this use the replica set instead of the leafset
	// I will send out a message to my leafset members
	LeafSet ls = node.getLeafSet();
	int cwSize = ls.cwSize();
	int ccwSize = ls.ccwSize();
	for (int i=-ccwSize; i<0; i++) {
	    NodeHandle nh = ls.get(i);
	    if(nh!= null) {
		if(SaarTest.logLevel <= 875) myPrint(endpoint.getId() + ": Sending OverlayLeave message to leafset member " + nh, 875);
		endpoint.route(null, new OverlayLeaveMessage(endpoint.getLocalNodeHandle(),null), nh);
	    }
	}
	for (int i=1; i<=cwSize; i++) {
	    NodeHandle nh = ls.get(i);
	    if(nh!=null) {
		if(SaarTest.logLevel <= 875) myPrint(endpoint.getId() + ": Sending OverlayLeave message to leafset member " + nh, 875);
		endpoint.route(null, new OverlayLeaveMessage(endpoint.getLocalNodeHandle(),null), nh);
	    }
	}
	
    }





    /***  Methods in the interface ScribeClient ***/

    // We delegate the local node to decide for itself if it can accept the anycast. Note that here we do not resort to the predicateSatisfied() method of the saarContent at the local node since this metadata is updated every second. The accepting of an anycast is done using the absolute instantaneous state. Addiitionally, there might be other metadata which is not aggregatabel, so we do not include them in the propagated SaarContent but do the decisions locally.
    public boolean anycast(Topic topic, ScribeContent content) {
	SaarContent sContent = (SaarContent)content;
	return getDataplaneClient(getTopic2Saartopic(topic)).recvAnycast(getTopic2Saartopic(topic), topic, sContent);	
    }
    
    
    public void deliver(Topic topic, ScribeContent content) {
	SaarContent sContent = (SaarContent)content;
	SaarTopicManager sManager = (SaarTopicManager) saarTopicManagers.get(topic);
	sManager.setLastGrpSummaryContent(sContent);
	sManager.setControlTreeDepth(sContent.numScribeIntermediates);
	getDataplaneClient(getTopic2Saartopic(topic)).grpSummary(getTopic2Saartopic(topic), topic, sContent);	
    }
    
    
    public void childAdded(Topic topic, NodeHandle child) {
	if(SaarTest.logLevel <= 875) myPrint("SaarImpl.childAdded("+topic+","+child+")", 875);
	
    }
    
    
    public void childRemoved(Topic topic, NodeHandle child) {
	if(SaarTest.logLevel <= 875) myPrint("SaarImpl.childRemoved("+topic+","+child+")", 875);
    
    }
    
    
    public void subscribeFailed(Topic topic) {
	if(SaarTest.logLevel <= 850) myPrint("SaarImpl.subscribeFailed("+topic+")", 850);
	NodeHandle scribeParent = myScribe.getParent(topic);
	if(scribeParent == null) {
	    myScribe.subscribe(topic, this);
	}
    }




    /**** These are the methods that the dataplaneclients will be invoking via the SaarClient ****/

    public void register(SaarTopic saartopic, DataplaneClient dataplaneClient) {
	if(SaarTest.logLevel <= 850) myPrint("SaarImpl.register("+saartopic+")", 850);
	registeredDataplanes.put(saartopic, dataplaneClient);
	// We also insert an intry into topic to saarTopic
	for(int i=0; i< saartopic.redundantTopics.length; i++) {
	    Topic topic = saartopic.redundantTopics[i];
	    topic2saartopic.put(topic,saartopic);
	    SaarTopicManager sManager = new SaarTopicManager(myScribe,topic);
	    saarTopicManagers.put(topic, sManager);
	}

    }


    public void subscribe(SaarTopic saartopic) {
	if(SaarTest.logLevel <= 850) myPrint("SaarImpl.subscribe("+saartopic+")", 850);
	for(int i=0; i< saartopic.redundantTopics.length; i++) {
	    Topic topic = saartopic.redundantTopics[i];
	    subscribedTopics.put(saartopic.redundantTopics[i],new Integer(1));
	    if(rice.p2p.saar.simulation.SaarSimTest.CENTRALIZEDSCRIBE) {
		myScribe.subscribe(topic,this,null,centralizedHandle);
	    } else {
		myScribe.subscribe(topic,this);
	    }
	}
	

    }


    public void unsubscribe(SaarTopic saartopic) {
	if(SaarTest.logLevel <= 850) myPrint("SaarImpl.unsubscribe("+saartopic+")", 850);
	for(int i=0; i< saartopic.redundantTopics.length; i++) {
	    Topic topic = saartopic.redundantTopics[i];
	    subscribedTopics.remove(saartopic.redundantTopics[i]);
	    SaarTopicManager sManager = (SaarTopicManager) saarTopicManagers.get(topic);
	    sManager.updateLeafContent(null);
	    myScribe.unsubscribe(topic,this);
	}

    }



    // In the update method we just modify the leafmetadata, the periodic thread delegated for the aggregation algorithm will take care of the rest
    public void update(SaarTopic saartopic, SaarContent saarContent, boolean forceUpdate) {
	if(SaarTest.logLevel <= 850) myPrint("SaarImpl.update("+saartopic+ "," + forceUpdate + ")", 850);

	// We first update the local state
	saarContent.setGNPCoordAggregator(cachedGNPCoord);
	saarContent.setAggregator(endpoint.getLocalNodeHandle());
	for(int i=0; i< saartopic.redundantTopics.length; i++) {
	    Topic topic = saartopic.redundantTopics[i];
	    SaarTopicManager sManager = (SaarTopicManager) saarTopicManagers.get(topic);
	    sManager.updateLeafContent(saarContent);
	}	
	
	// If we want to relfect the change immediately to our parent in the Scribe tree, then we proactively send the update. Note that forceUpdate=true only when critical local state on the local node changes. If there is a change in the values of the children if this is an intermediate node in the Scribe tree, then no such proactive updates are sent
	if(forceUpdate) {
	    for(int i=0; i< saartopic.redundantTopics.length; i++) {
		Topic topic = saartopic.redundantTopics[i];
		singleSendUpwardUpdateForTopic(topic, true);
	    }
	}

    }


    public void anycast(SaarTopic saartopic, SaarContent reqContent, NodeHandle hint, int numTreesToUse, int satisfyThreshold, int traversalThreshold) {
	if(SaarTest.logLevel <= 850) myPrint("SaarImpl.anycast("+saartopic+ "," + hint + "," + numTreesToUse + "," + satisfyThreshold + "," + traversalThreshold + ", " + reqContent.anycastGlobalId + ")", 850);
	reqContent.setSatisfyThreshold(satisfyThreshold);
	reqContent.setTraversalThreshold(traversalThreshold);
	reqContent.setNumScribeIntermediates(0);
	reqContent.setAnycastRequestor(endpoint.getLocalNodeHandle());
	reqContent.setGNPCoordAnycastRequestor(cachedGNPCoord);
	

	if(numTreesToUse > SaarTopic.NUMTREES) {
	    numTreesToUse = SaarTopic.NUMTREES;
	}
	// We will randomize the topics in saartopic and pick the first 'numTreesToUse' topics to send the anycast thru
	Topic[] topicsInSaartopic = new Topic[SaarTopic.NUMTREES];
	for(int i=0; i< saartopic.redundantTopics.length; i++) {
	    topicsInSaartopic[i] = saartopic.redundantTopics[i];
	}
	for (int i=0; i<topicsInSaartopic.length; i++) {
	    int j = rng.nextInt(topicsInSaartopic.length);
	    int k = rng.nextInt(topicsInSaartopic.length);
	    Topic tmp = topicsInSaartopic[j];
	    topicsInSaartopic[j] = topicsInSaartopic[k];
	    topicsInSaartopic[k] = tmp;
	}
	
	String prevAnycastGlobalId = reqContent.anycastGlobalId;
	for(int i=0; i< numTreesToUse; i++) {
	    Topic topic = topicsInSaartopic[i];
	    rice.pastry.Id redundantTreeId = (rice.pastry.Id) topic.getId();
	    int redundantTreeIndex = redundantTreeId.getDigit(msbdigitpos,routingBase);
	    SaarContent reqContentCopy = SaarContent.duplicate(reqContent);
	    reqContentCopy.setAnycastGlobalId(prevAnycastGlobalId  + "_T" + redundantTreeIndex);
	    if(SaarTest.logLevel <= 880) myPrint("SaarImpl.issueanycast("+saartopic+ "," + hint + "," + numTreesToUse + "," + satisfyThreshold + "," + traversalThreshold + ", anycastGlobalId:" + reqContentCopy.anycastGlobalId + ", treeTopic:" + topic + ")", 880);
	    if(rice.p2p.saar.simulation.SaarSimTest.CENTRALIZEDSCRIBE) {
		myScribe.anycast(topic,reqContentCopy,centralizedHandle); // We override the hint
	    } else {
		myScribe.anycast(topic,reqContentCopy,hint);
	    }
	}

    }

    

    class UpwardUpdateContent implements Message {
//	public void dump(ReplayBuffer buffer, PastryNode pn) {
//		    
//	}

	public int getPriority() {
	    return 0;
	}
    }


    class DownwardPropagateContent implements Message {
//	public void dump(ReplayBuffer buffer, PastryNode pn) {
//
//	}

	public int getPriority() {
	    return 0;
	}
    }


    class GNPContent implements Message {
//	public void dump(ReplayBuffer buffer, PastryNode pn) {
//
//	}

	public int getPriority() {
	    return 0;
	}
    }



    class CheckContent implements Message {
//	public void dump(ReplayBuffer buffer, PastryNode pn) {
//
//	}

	public int getPriority() {
	    return 0;
	}
    }









    public Environment getEnvironment() {
      return environment;
    }

}







  
    
    
    
