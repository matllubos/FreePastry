/*
 * Created on May 4, 2005
 */
package rice.p2p.libra;

import rice.replay.*;
import java.util.Random;
import java.util.Vector;
import java.util.Hashtable;
import java.lang.*;
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
import rice.p2p.scribe.Scribe;
import rice.p2p.scribe.ScribeClient;
import rice.p2p.scribe.ScribeContent;
import rice.p2p.scribe.ScribeImpl;
import rice.p2p.scribe.Topic;
import rice.pastry.PastryNode;
import rice.pastry.commonapi.PastryIdFactory;
import rice.selector.TimerTask;
import rice.pastry.ScheduledMessage;
import rice.pastry.PastryNodeFactory;
import rice.pastry.socket.SocketPastryNodeFactory;
import rice.pastry.socket.SocketNodeHandle;
import rice.environment.Environment;



/**
 * We implement Application to receive regular timed messages (see lesson5).
 * We implement ScribeClient to receive scribe messages (called ScribeContent).
 * 
 */
public class MyPastrytestClient implements ESMClient, Application {

    public int bindIndex;

    // This is an index for Pastry JVM running on this node (there might be multiple running for instance in the modelnet setting)
    public int jvmIndex;
   
    // Virtual node index
    public int vIndex;

    public Random rng = new Random();
       
    public CancellableTask queryTask;

    public CancellableTask udpQueryTask;

    public CancellableTask aliveTask;

    public CancellableTask leafsetTask;

    public CancellableTask pingAliveTask; // This pins the host machine telling it that it is alive
    
    /**
     * The Endpoint represents the underlieing node.  By making calls on the 
     * Endpoint, it assures that the message will be delivered to a MyApp on whichever
     * node the message is intended for.
     */
    protected Endpoint endpoint;
    
    public PastryNode node;

    public DatagramSocket udpSocket = null;
    
    // This Ip together with the esm port will be contacted when the anycast succeeds 
    public byte[] hostIp = new byte[4];

    public String hostName; // This will be   appended with the seq numbers for debugging


    public static long ALIVETHREADPERIOD  = 15000;

    public static long PINGALIVETHREADPERIOD  = 100000;


    public static long LEAFSETTHREADPERIOD = 15000;

    

    public static int NUMDUPLICATES = 5;

    public static int DUPLICATEINTERVAL = 100; // 100 ms 

    // This is used for tracking the path of messages
    public int seqNum = 0;
    public static int NUMTESTGROUPS = 25; // This will simulate sending to roots of k Scribe trees
    


    public int seqNum_UDP = -1;
    public int nodeChance = 0; // This is the node to which it will sent
    public int numUDPNodes = 0; 
    public int MAXUDPNODES = 1000;
    public NodeState[] udpNodeStates;
    public PastryIdFactory idFactory;
  
    public UDPLibraServer udpLibraServer;

    //public static String inFile = "MonitorNodes.nds";

    public int ownPLIndex = -1;

    // This models a centralized node to which every other overlay node sends msgs
    public NodeHandle centralizedNh;
    public InetAddress centralizedIpAddr;

    public PastryNodeFactory factory;

    //public SocketAddress lServerAddress;

    public SocketAddress udplibraserverSentToAddress;

    private Environment env;

    String hostAddress = "NONE";
    private InetAddress localAddress; // .getHostAddress() will tell us the modelnet ip
    //private int ipLSD = 0; // x.x.x.ipLSD is the bindAddress


    public class NodeState{
	public long lastSent;
	public long lastAck;
	public String nodeName;
	//public SocketAddress sentToAddress;
	public InetAddress ipAddr;

	public NodeState(String nodeName) {
	    this.nodeName = new String(nodeName);
	    try {
		this.ipAddr = InetAddress.getByName(nodeName);
		//this.sentToAddress = new InetSocketAddress(nodeName,LibraTest.SCRIBESERVERPORT);
	    } catch(UnknownHostException e) {
		System.out.println("ERROR: Trying to get ipaddress for : " + nodeName);
		System.exit(1);
	    }
	    this.lastSent = 0;
	    this.lastAck = 0;

	}

	public String toString() {
	    String s= "";
	    s = s + nodeName;
	    return s;
	}
    }


    
    public MyPastrytestClient(int bindIndex, int jvmIndex, int vIndex, PastryNode node, DatagramSocket udpSocket, UDPLibraServer udpLibraServer, PastryNodeFactory factory) {
	this.env = node.getEnvironment();
	try {
	    localAddress = env.getParameters().getInetAddress("socket_bindAddress");
	    this.factory = factory;
	    this.udpLibraServer = udpLibraServer;
	    udpLibraServer.registerApp(vIndex,this);
	    this.node = node;
	    this.bindIndex = bindIndex;
	    this.jvmIndex = jvmIndex; // this corresponds to a pastry JVM 
	    this.vIndex = vIndex; // this corresponds to virtual Pastry nodes within the same JVM
	    this.udpSocket  = udpSocket;
	    this.endpoint = node.registerApplication(this, "myinstance");
	    this.idFactory = new PastryIdFactory(this.env);
	    
	    
	    InetAddress addr = localAddress; // This is the bind address
	    // Get the Ip
	    hostAddress = addr.getHostAddress();
	    System.out.println("HostAddress= " + hostAddress);
	    //String[] args = hostAddress.split(".");
	    //System.out.println("args.length= " + args.length);
	    //System.out.println("args[0]/args[1]/args[2]: " + args[0] + "/" + args[1]+ "/" + args[2]);
	    //ipLSD = Integer.parseInt(args[3]);
	    //System.out.println("ipLSD= " + ipLSD);
	    hostIp = addr.getAddress();
	    System.out.println("hostIp: " + hostIp[0] + "." + hostIp[1] + "." + hostIp[2] + "." + hostIp[3]);
	    hostName = addr.getHostName();
	    System.out.println("HostName= " + hostName);
	    
	} 
	catch (Exception e) {
	    System.out.println("ERROR: Trying to get localhost ipaddress " + e);
	    System.exit(1);
	}

	
	ownPLIndex = ((rice.pastry.socket.SocketPastryNode)node).getPLIndexByIp(hostAddress);
	if(ownPLIndex > 0) {
	    // It overrides an uninitialized bindIndex
	    if (bindIndex == -1) {
		bindIndex = ownPLIndex;
	    }
	    System.out.println("OwnPLIndex= " + ownPLIndex);
	} else {
	    System.out.println("WARNING: ownPLIndex=-1 while determining ownPLIndex with ownIpString:" + hostAddress);	    
	}
	
	//try {
	//  InetAddress inetAddr = InetAddress.getByName(LibraTest.LSERVER_HOST);
	//  lServerAddress = new InetSocketAddress(inetAddr, LibraTest.LSERVER_PORT);
	//  //lServerAddress = new InetSocketAddress(LibraTest.LSERVER_HOST, LibraTest.LSERVER_PORT);
	//  System.out.println("lserverAddress: " + lServerAddress);
	//} catch(java.net.UnknownHostException e) {
	//  System.out.println("lserveradress exception: " + e);
	//}
    
	// Setting the bindAddress
	try {
	    InetAddress udplibraserverInetAddr;
	    if(!LibraTest.BINDADDRESS.equals("DEFAULT")) {
		udplibraserverInetAddr = InetAddress.getByName(LibraTest.BINDADDRESS);
	    } else {
		udplibraserverInetAddr = InetAddress.getByName("localhost");
	    }
	    udplibraserverSentToAddress = new InetSocketAddress(udplibraserverInetAddr,LibraTest.SCRIBESERVERPORT);
	    
	} catch(java.net.UnknownHostException e) {
	    System.out.println("ERROR: Error binding to specified BINDADDRESS: " + LibraTest.BINDADDRESS);
	}


	myPrint("App[" + vIndex + "] is ready " +  endpoint.getLocalNodeHandle(), 850);

	//startUDPQueryTask();
	startQueryTask();
	startAliveTask(); 
	startLeafsetTask();
	//startPingAliveTask();






	/*
	udpNodeStates = new NodeState[MAXUDPNODES];
	// We will first try to read it off the RUN file
	FileReader fr = null;
	boolean fileEnded = false;

	try{
	    fr = new FileReader(inFile);
	    BufferedReader  in = new BufferedReader( fr );
	    
	    while(!fileEnded) {
		String line = null;
		line = in.readLine();
		if(line.equals("Done")) {
		    fileEnded = true;
		} else {
		    // Extract the node name from there
		    String nodeName = new String(line);
		    udpNodeStates[numUDPNodes] = new NodeState(nodeName);
		    numUDPNodes ++;
		}
	    }
	}catch( Exception e ){
	    System.out.println("ERROR : While reading the MONITORNODES file " + e);
	}
	*/
	
	InetSocketAddress centralizedAddress = new InetSocketAddress(LibraTest.centralizedNodeName, LibraTest.centralizedNodePort);
	System.out.println("centralizedAddress: " + centralizedAddress);
	centralizedNh = ((SocketPastryNodeFactory)factory).generateNodeHandle(centralizedAddress);
	System.out.println("centralizedNh: " + centralizedNh);
	if(centralizedNh != null) {
	    // This step makes sure we have a single instance of a NodeHandle per remote node	    
	    centralizedNh = (SocketNodeHandle) node.getLocalNodeI((rice.pastry.LocalNodeI)centralizedNh);
	    ((SocketNodeHandle)centralizedNh).setLocalNode(node);
	}
	try {
	    centralizedIpAddr = InetAddress.getByName(LibraTest.centralizedNodeName);
	    //this.sentToAddress = new InetSocketAddress(nodeName,LibraTest.SCRIBESERVERPORT);
	} catch(UnknownHostException e) {
	    System.out.println("ERROR: Trying to get ipaddress for : " + LibraTest.centralizedNodeName);
	    System.exit(1);
	}

    }

    public void myPrint(String s, int priority) {
	node.getEnvironment().getLogManager().getLogger(MyLibraClient.class,null).log(priority,s);
	
    }
    

    /**
     * Starts the alive task.
     */
    public void startAliveTask() {
	aliveTask = endpoint.scheduleMessage(new AliveContent(), 60000, ALIVETHREADPERIOD);    
    }



    public void startPingAliveTask() {
	pingAliveTask = endpoint.scheduleMessage(new PingAliveContent(), 5000, PINGALIVETHREADPERIOD);    
    }




    public void startLeafsetTask() {
	leafsetTask = endpoint.scheduleMessage(new LeafsetContent(), 60000, LEAFSETTHREADPERIOD);    
    }
 
    /**
     * Starts the alive task.
     */
    public void startQueryTask() {
	System.out.println("PASTRYQUERYINTERVAL: " + (LibraTest.PASTRYQUERYPERIOD * 1000));
	queryTask = endpoint.scheduleMessage(new QueryContent(), 30000, (int) (LibraTest.PASTRYQUERYPERIOD * 1000));    

    }

    // The traffic will be sent to LibraTest.SCRIBESERVERPORT
    public void startUDPQueryTask() {
	if(LibraTest.numModelnet > 0) {
	    udpQueryTask = endpoint.scheduleMessage(new UDPQueryContent(), 60000, (int) (LibraTest.UDPQUERYPERIOD * 1000));   
	} 
    }

     /***** Methods in interface Application ******/

    public boolean forward(RouteMessage message) {
	if(message.getMessage() instanceof RequestRootHandleMsg) {
	    RequestRootHandleMsg mymsg = (RequestRootHandleMsg) message.getMessage();
	    mymsg.content.addToMsgPath(endpoint.getLocalNodeHandle(), bindIndex, jvmIndex, vIndex);
	}
	return true;
    }
    
    
    public void update(NodeHandle handle, boolean joined) {
    
    }
    

    public void deliver(Id id, Message message) {
	long currTime = System.currentTimeMillis(); 

	
	if (message instanceof UDPQueryContent) {
	    for(int k=0; k< LibraTest.PACKETSININTERVAL; k++) {
		try {
		    seqNum_UDP ++;
		    //int dupCount =0;
		    //String destHostAddress = LibraTest.modelnetIp[seqNum_UDP % LibraTest.numModelnet];
		    int destVNum = seqNum_UDP % LibraTest.numModelnet;
		    //int destVNum = 0;

		    String destHostAddress = LibraTest.modelnetIp[destVNum];

		    String debugString = "H" + hostAddress + "_BIND" + bindIndex + "_JVM" + jvmIndex + "_V" + vIndex + "_S"  + seqNum_UDP + "_D" + destHostAddress; 
		    
		    
		    byte[] sBytes = debugString.getBytes();
		    byte[] msg = new byte[1 + 1 + 4 + sBytes.length]; // vIndex + OPCODE + (4 bytes: for string length in int) + string_Bytes 
		    msg[0] = (byte)vIndex;
		    msg[1] = UDPLibraServer.UDPQUERY;
		    byte[] lengthArray = new byte[4];
		    MathUtils.intToByteArray(sBytes.length, lengthArray,0);
		    for(int i=0;i<4; i++) {
			msg[2+i] = lengthArray[i];
		    }
		    for(int i=0; i < sBytes.length; i++) {
			msg[6 + i] = sBytes[i];
		    }
		    
		    InetAddress destIpAddr = LibraTest.modelnetInetAddr[destVNum];
		    DatagramPacket sendPacket = new DatagramPacket(msg,msg.length, destIpAddr, LibraTest.SCRIBESERVERPORT);
		    
		    //System.out.println("To: " + nState.sentToAddress);
		    udpLibraServer.ds.send(sendPacket); // We send the packet using the datagram socket of the libraserver, so that the ack will come to the udplibraserver
		    
		    //udpSocket.send(sendPacket);
		    
		    
		    myPrint("SysTime: " + System.currentTimeMillis() +  " Node "+endpoint.getLocalNodeHandle()+ " UDPQUERYING for [ " + debugString + " ] of size " + msg.length, 875);
		    
		} catch (Exception e) {
		    myPrint("Delivering " + this + " caused exception " + e, Logger.WARNING);
		}
	    }
	    
	    
	    /*
	    try {
	    seqNum_UDP ++;
	    nodeChance = (nodeChance + 1)% numUDPNodes;
	    NodeState nState = udpNodeStates[nodeChance];
	    
	    // At this point we will schedule to send NUMDUPLICATES duplicates for this msg
	    
	    
	    for(int dupCount=1; dupCount<=NUMDUPLICATES; dupCount++) {
	    ScheduledUDPDuplicateMsg msg = new ScheduledUDPDuplicateMsg(nState, seqNum_UDP, dupCount);
	    node.getEnvironment().getSelectorManager().getTimer().schedule(msg,DUPLICATEINTERVAL *dupCount); 
	    }
	    
	    }
	    catch(Exception e) { System.out.println(e); }
	    */
	}
	
	
	if (message instanceof QueryContent) {

	    /*
	    try {
		int val = 2/0;
	    }
	    catch(Exception e) {
		e.printStackTrace();
		System.exit(1);
	    }
	    */


	    //myPrint("Systime: " + System.currentTimeMillis() + " ScheduledMsg.QueryContent", 850);
	    
	    for(int k=0; k< LibraTest.PACKETSININTERVAL; k++) {
		// We send out a RequestRootHandle message
		seqNum ++;
		String debugString = "H" + hostName + "_BIND" + bindIndex + "_JVM" + jvmIndex + "_V" + vIndex + "_S"  + seqNum;


		
		
		int topicNum;
		if(NUMTESTGROUPS == 1) {
		    topicNum = 0;
		} else {
		    topicNum = (seqNum % NUMTESTGROUPS);
		}

		//String topicString = "_N" + modelnetVNode + "_V" + vIndex + "_S"  + topicNum; // You cannot include the hostname here because the idea is to simulate centralized Scribe roots. Also the NUMTESTGROUPS allows you to simulated the effect of balancing the load on this many roots 
		
		
		//Id msgKey = idFactory.buildId(topicString); // Use this to simulated centralized Scribe roots
		Id msgKey = idFactory.buildId(debugString); // Use this to simulated centralized Scribe roots


		RequestRootHandleMsg reqRootHandleMsg = new RequestRootHandleMsg(debugString, new Topic(msgKey), endpoint.getLocalNodeHandle());
		myPrint("SysTime: " + System.currentTimeMillis() +  " Node "+endpoint.getLocalNodeHandle()+ " ANYCASTING for " + debugString + " ] " + msgKey + " TopicName[ " + topicNum + " ]", 875);
		
		endpoint.route(msgKey, reqRootHandleMsg , null);
		//endpoint.route(null, reqRootHandleMsg, centralizedNh);
		
	    }
	}
	

	if (message instanceof AliveContent){
	    myPrint("SysTime: " + System.currentTimeMillis() + " ALIVE ", 875);
	}

	if (message instanceof PingAliveContent){
	    myPrint("SysTime: " + System.currentTimeMillis() + " PINGALIVE ", 875);
	    // We will ask the UDPLibraServer to send a ping to the LSERVER ( amushk.cs.rice.edu)
	    //sendPingDirect();
	    reqPingAlive();

	}



	if (message instanceof LeafsetContent){
	    // Print the leafset
	    myPrint("SysTime: " + System.currentTimeMillis() + " LEAFSET: " + node.getLeafSet(), 875);
	    
	}

	/*
	if(message instanceof RootHandleAckMsg) {
	    
	    RootHandleAckMsg  myMsg = (RootHandleAckMsg)message;
	    String topicName = myMsg.topicName;
	    myPrint("SysTime: " + System.currentTimeMillis() +  " Node "+endpoint.getLocalNodeHandle()+ " received ANYCASTACK from " + myMsg.getSource() + " for Topic[ " + topicName + " ]= " + myMsg.getTopic(), 850);
	    

	}
	*/
	
	

	if(message instanceof RequestRootHandleMsg) {

	    /*
	    try {
		int val = 2/0;
	    }
	    catch(Exception e) {
		e.printStackTrace();
		System.exit(1);
	    }
	    */



	    RequestRootHandleMsg  myMsg = (RequestRootHandleMsg)message;
	    String topicName = myMsg.topicName;
	    NodeHandle requestor = myMsg.getInitialRequestor();

	    if(myMsg.acceptNow && endpoint.getLocalNodeHandle().equals(requestor)) {
		MyScribeContent.NodeIndex[] traversedPath = myMsg.content.getMsgPath();
		String pathString = pathAsString(traversedPath);
		//myPrint("SysTime: " + System.currentTimeMillis() +  " Node "+endpoint.getLocalNodeHandle()+ " received ANYCASTACK from " + myMsg.getSource() + " for Topic[ " + topicName + " ]= " + myMsg.getTopic() + " TraversedPathString: " + pathString + " " + myMsg.debugInfoAsString(), 850);		
		myPrint("SysTime: " + System.currentTimeMillis() +  " Node "+endpoint.getLocalNodeHandle()+ " received ANYCASTACK from " + myMsg.getSource() + " for Topic[ " + topicName + " ]= " + myMsg.getTopic() + " TraversedPathString: " + pathString + " " + myMsg.debugInfoAsString(), 875);		


	    } else {

		Topic myTopic = myMsg.getTopic();
		//myPrint("Node received RequestRootHandle message from " + myMsg.getSource() + " for Topic[ " + topicName + " ]= " + myTopic, 850);
		//System.out.println("Systime: " + System.currentTimeMillis() + " Node received RequestRootHandle message from " + myMsg.getSource() + " for Topic[ " + topicName + " ]= " + myTopic);

		// We set acceptnow to true now
		myMsg.acceptNow = true;
		// We also set the source to ourself so that the initialRequestor knows the root when he gets the ack
		myMsg.setSource(endpoint.getLocalNodeHandle());
		

		/* We do not use RootHandleAckMsg anymore
		RootHandleAckMsg rootHandleAckMsg = new RootHandleAckMsg(topicName, myTopic, endpoint.getLocalNodeHandle());
		endpoint.route(null, rootHandleAckMsg , requestor);
		*/

		// We disable the acks now
		endpoint.route(null, myMsg , requestor);
	    }

	    
	    
	}
	

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

  
    /*
    public String pathAsString(int[] plIndices) {
	String s = "";
	int val = -1;
	
	s = s + "[ ";
	for(int i=0; i< plIndices.length; i++) {
	    val = plIndices[i];
	    //s = s + plNodes[val].nodeName + " "; 
	    String name = ((rice.pastry.socket.SocketPastryNode)node).getPLName(val);
	    //if(name!= "NONE") {
	    s =s + name + " ";
	    //} else {
	    //s =s + val + " ";
	    //}
	}
	s = s + "]";
	return s;
    }
    */

    
    // This bypasses the UDPLIBRASERVER
    //public void sendPingDirect() {
    //try {
    //    // (2) sending an pingAlive to LServer ( amushk.cs.rice.edu)
    ///    byte[] msg = new byte[2];
    ///    msg[0] = (byte)vIndex;
    //    msg[1] = UDPLibraServer.PINGALIVEOPCODE;
    //    DatagramPacket sendPacket = new DatagramPacket(msg,msg.length, lServerAddress);
    //    System.out.println("Sending to " + lServerAddress);
    //    udpSocket.send(sendPacket);
    //} catch(Exception e) {
    //    System.out.println("sendPingDirect: " + e);
    //}
    //}

     
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
	catch (UnknownHostException e) { myPrint("reqPingAlive() " + e, 850); } 
	catch (IOException e) { myPrint("reqPingAlive() " + e, 850); }
	
	
	
    }	
    



    /** Metos in the ESMClient API ****/

    public void invokeRegister(int esmStreamId, byte[] esmOverlayId, int esmServerPort, int esmDatapathPort, byte esmRunId) {

    }
    public void invokeDummyRegister(byte[] dummyesmOverlayId, int dummyesmServerPort, int dummyesmDatapathPort, byte dummyesmRunId) {

    }
    public void invokeAnycast(int index, int seqNum, int pathLength, byte[] paramsPath) {

    }

    public void invokeGrpMetadataRequest(int index, int seqNum) {

    }

    public void invokeSubscribe(int index) {

    }

    public void invokeUnsubscribe(int index) {

    }
    public void invokeUpdate(int index, int[] paramsLoad, int[] paramsLoss, int time, int pathLength, byte[] paramsPath) {

    }

    
    public void recvUDPQuery(byte[] msg) {
	byte[] lengthArray = new byte[4];
	for(int i=0; i<4; i++) {
	    lengthArray[i]= msg[2+i];
	}
	int length;
	length = MathUtils.byteArrayToInt(lengthArray);
	byte[] sBytes = new byte[length];
	for(int i=0; i<length; i++) {
	    sBytes[i] = msg[6+i];
	}
	String debugString = new String(sBytes);

	//myPrint("SysTime: " + System.currentTimeMillis() +  " Node "+endpoint.getLocalNodeHandle()+ " received UDPQUERY for [ " + debugString + " ] of size " + msg.length, 875);	
    }

    public void recvUDPAck(byte[] msg) {
	byte[] lengthArray = new byte[4];
	for(int i=0; i<4; i++) {
	    lengthArray[i]= msg[2+i];
	}
	int length;
	length = MathUtils.byteArrayToInt(lengthArray);
	byte[] sBytes = new byte[length];
	for(int i=0; i<length; i++) {
	    sBytes[i] = msg[6+i];
	}
	String debugString = new String(sBytes);
	myPrint("SysTime: " + System.currentTimeMillis() +  " Node "+endpoint.getLocalNodeHandle()+ " received UDPACK for [ " + debugString + " ] of size " + msg.length, 875);
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

    class QueryContent implements Message {
	public void dump(ReplayBuffer buffer, PastryNode pn) {

	}

	public int getPriority() {
	    return 0;
	}
    }


    class UDPQueryContent implements Message {
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


    class ScheduledUDPDuplicateMsg extends TimerTask {
	public NodeState state;
	public int seqNum;
	public int dupCount;

	public ScheduledUDPDuplicateMsg(NodeState nState, int seqNum, int dupCount) {
	    this.state = nState;
	    this.seqNum = seqNum;
	    this.dupCount = dupCount;
	}


	public void run() {
	    try {
		String destNodeName = state.nodeName;
		String debugString = "H" + hostName + "_BIND" + bindIndex + "_JVM" + jvmIndex + "_V" + vIndex + "_S"  + seqNum + "_C" + dupCount + "_D" + destNodeName; 
		

		byte[] sBytes = debugString.getBytes();
		byte[] msg = new byte[1 + 1 + 4 + sBytes.length]; // vIndex + OPCODE + (4 bytes: for string length in int) + string_Bytes 
		msg[0] = (byte)vIndex;
		msg[1] = UDPLibraServer.UDPQUERY;
		byte[] lengthArray = new byte[4];
		MathUtils.intToByteArray(sBytes.length, lengthArray,0);
		for(int i=0;i<4; i++) {
		    msg[2+i] = lengthArray[i];
		}
		for(int i=0; i < sBytes.length; i++) {
		    msg[6 + i] = sBytes[i];
		}
		DatagramPacket sendPacket = new DatagramPacket(msg,msg.length, state.ipAddr, LibraTest.SCRIBESERVERPORT);

		//System.out.println("To: " + nState.sentToAddress);
		udpLibraServer.ds.send(sendPacket); // We send the packet using the datagram socket of the libraserver, so that the ack will come to the udplibraserver

		//udpSocket.send(sendPacket);
		
		
		myPrint("SysTime: " + System.currentTimeMillis() +  " Node "+endpoint.getLocalNodeHandle()+ " UDPQUERYING for [ " + debugString + " ] of size " + msg.length, 850);

	    } catch (Exception e) {
		myPrint("Delivering " + this + " caused exception " + e, Logger.WARNING);
	    }
	}

	public String toString() {
	    return "SchedUDPDuplicateMsg: " + state + " " + "DCount: " + dupCount;	
	}
    }
}
