package rice.splitstream.testing;


import rice.*;
import rice.splitstream.*;

import rice.past.*;
import rice.past.messaging.*;

import rice.pastry.*;
import rice.pastry.dist.*;
import rice.pastry.direct.*;
import rice.pastry.standard.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.post.*;
import rice.post.messaging.*;
import rice.post.storage.*;

import rice.scribe.*;
import rice.scribe.testing.*;
import rice.storage.*;

import java.util.*;
import java.io.*;
import java.net.*;
import java.io.Serializable;
import java.security.*;


public class DirectSplitStreamTest{

    private EuclideanNetwork simulator; 
    private DirectPastryNodeFactory factory;
    private Vector pastrynodes;
    private Vector scribeNodes;
    private Vector splitStreamNodes;
    private Vector splitStreamApps;
    private Vector channelIds;
    private Random rng;
    private RandomNodeIdFactory idFactory;
    private static int numNodes = 500;
    private static int port = 5009;
    private static String bshost;
    private static int bsport = 5009;
    private Credentials credentials = new PermissiveCredentials();
    private int numResponses = 0;
    private Hashtable nodeIdToApp;

    static {
	try {
	    bshost = InetAddress.getLocalHost().getHostName();
	} catch (UnknownHostException e) {
	    System.out.println("Error determining local host: " + e);
	}
    }


    public static void main(String argv[]){
	boolean passed = true;
	boolean result = true;

	System.out.println("SplitStream Test Program v0.4");
	PastrySeed.setSeed((int)System.currentTimeMillis());
	//PastrySeed.setSeed( -687899135 );
	System.out.println(PastrySeed.getSeed() );
	DirectSplitStreamTest test = new DirectSplitStreamTest();
	test.init();
	test.createNodes();


	/** --CREATE -- **/
	ChannelId channelId = test.createChannel();
	//ChannelId channelId = content.getChannelId();
	test.channelIds.add(channelId);
	System.out.println(channelId);
	while(test.simulate());


	/** -- ATTACH -- **/
	for(int i = 0; i < test.splitStreamNodes.size(); i ++){
	    DirectSplitStreamTestApp app = (DirectSplitStreamTestApp) test.splitStreamApps.elementAt(i);
	    app.attachChannel(channelId);
	    while(test.simulate());
	    
	    if(!app.channelReady(channelId)){
		System.out.println("Application "+i+" could not attach. PROBLEM!! ");
	    }
	}

	while(test.simulate());

	/** 
	 * TEST 1.
	 * Checking if all node are part of ChannelId tree.
	 */

	passed = test.checkTree(channelId);
	
	if(passed)
	    System.out.println("\n\nCHANNEL-TREE MEMBERSHIP TEST : Passed \n\n");
	else
	    System.out.println("\n\nCHANNEL-TREE MEMBERSHIP TEST : Failed \n\n");



	System.out.println("All nodes are joining all stripes");
	test.join();
	while(test.simulate());
	
	/**
	 * TEST 2
	 * Checking if all nodes are part of all the stripes give channelId.
	 */
	result = passed;
	passed =  test.checkAllStripeTrees(channelId);

	if(passed)
	    System.out.println("\n\nSTRIPE-TREE MEMBERSHIP TEST : PASSED \n\n");
	else
	    System.out.println("\n\nSTRIPE-TREE MEMBERSHIP TEST : FAILED \n\n");


	/**
	 * TEST 3 
	 * Checking if everybody receives the data when its being sent
	 *
	 *
	 * First send data over the channel, and then check if everybody received
	 * it or not? 
	 */
	test.send(channelId, 0);
	while(test.simulate());

	result = passed;
	passed =  test.checkSend(channelId);

	if(passed)
	    System.out.println("\n\nSEND-DATA TEST : PASSED \n\n");
	else
	    System.out.println("\n\nSEND-DATA TEST : FAILED \n\n");


	/*	
	test.send(content);
	while(test.simulate());
	test.send(content);
	while(test.simulate());
	test.showBandwidth();
	*/
	System.out.println(PastrySeed.getSeed() );
    }

    public DirectSplitStreamTest(){
	System.out.println("Creating a SplitStream");
    }
 
    public void init(){
	simulator = new EuclideanNetwork();
	idFactory = new RandomNodeIdFactory();
	//PastrySeed.setSeed((int)System.currentTimeMillis());
	factory = new DirectPastryNodeFactory(idFactory, simulator);
	scribeNodes = new Vector();    
	pastrynodes = new Vector();
	channelIds = new Vector();
	splitStreamNodes = new Vector();
	splitStreamApps = new Vector();
	nodeIdToApp = new Hashtable();
	rng = new Random(5);
      
    }


    public ChannelId createChannel(){
	System.out.println("Attempting to create a Channel");
        int base = RoutingTable.baseBitLength();
	ChannelId cid = 
	    ((DirectSplitStreamTestApp) splitStreamApps.elementAt(rng.nextInt(numNodes))).createChannel(1<<base,"DirectSplitStreamTest");
	while(simulate());
	return cid;
    }


    public void  attachChannel(int index, ChannelId channelId){
	DirectSplitStreamTestApp app = (DirectSplitStreamTestApp)splitStreamApps.elementAt(index);
	app.attachChannel(channelId);
	return;
    }


    public void join(){
	System.out.println("JOIN :: channelIds size "+channelIds.size());
	for(int i = 0; i < channelIds.size(); i ++){
	    ChannelId channelId = (ChannelId) channelIds.elementAt(i);
	    for(int j = 0; j < splitStreamApps.size(); j++){
		DirectSplitStreamTestApp app = (DirectSplitStreamTestApp)splitStreamApps.elementAt(j);
		
		// Join all stripes for all channels an app has created or attached to.
		app.joinChannelStripes(channelId);
		while(simulate());
	    }
	    
	}	

    }



    public void send(ChannelId send, int index){
	DirectSplitStreamTestApp app = (DirectSplitStreamTestApp) splitStreamApps.elementAt(index);
	app.sendData(send);
    }
    
    /* ---------- Setup methods ---------- */

    /**
     * Gets a handle to a bootstrap node.
     *
     * @return handle to bootstrap node, or null.
     */
 
    private NodeHandle getBootstrap() {
	NodeHandle bootstrap = null;
	try {
	    PastryNode lastnode = (PastryNode) pastrynodes.lastElement();
	    bootstrap = lastnode.getLocalHandle();
	} catch (NoSuchElementException e) {
	}
	return bootstrap;
    }

    /**
     * Creates a pastryNode with a past, scribe, and post running on it.
     */
    protected void makeNode(int index) {
	PastryNode pn = factory.newNode(getBootstrap());
	pastrynodes.add(pn);
	Scribe scribe = new Scribe(pn, credentials);
	scribeNodes.add(scribe);  
	ISplitStream ss = new SplitStreamImpl(pn, scribe);
	DirectSplitStreamTestApp app = new DirectSplitStreamTestApp(ss, index);
	splitStreamNodes.add(ss);
	splitStreamApps.add(app);
	nodeIdToApp.put(pn.getNodeId(), app);
	//System.out.println("created " + pn);
    }

    /**
     * Creates the nodes used for testing.
     */
    protected void createNodes() {
	for (int i=0; i < numNodes; i++) {
	    makeNode(i);
	    System.out.print("<"+i+">, ");
	    while(simulate());
	}
	while(simulate());
	System.out.println("All Nodes Created Succesfully");
    }
    public boolean simulate() { 
	return simulator.simulate(); 
    }
    
    /**
     * Checks if all nodes are part of the tree. We do a tree traversal from
     * the root of the tree and see if number of ndoes in the tree is same
     * as the total number of nodes that called subscribe for this topicId.
     */
    public boolean checkTree(NodeId topicId){
	int num = BFS(topicId);

	//System.out.println("Number of nodes in channel tree "+num);
	if(num < splitStreamApps.size())
	    return false;
	return true;
    }


    /**
     * Checks if all nodes are part of all stripe multicast trees.
     */
    public boolean checkAllStripeTrees(ChannelId channelId){
	DirectSplitStreamTestApp app = ( DirectSplitStreamTestApp) splitStreamApps.elementAt(0);
	StripeId[] stripeIds = app.getStripeIds(channelId);

	boolean result = true;

	for(int i = 0; i < app.getNumStripes(channelId); i++)
	    result &= checkTree((NodeId)stripeIds[i]);
    
	return result;
    }


    /**
     * This returns the index of the application that is currently the 
     * root for the topic's multicast tree.
     *
     * @param topicId
     * the topic id of the multicast tree.
     *
     * @return index of application that is the root of topic's multicast tree.
     */
     public int rootApp(NodeId topicId) {

	int i;
	DirectSplitStreamTestApp app;
	int rootApp = -1;
	
	for(i=0; i< splitStreamApps.size() ; i++) {
	    app = ( DirectSplitStreamTestApp) splitStreamApps.elementAt(i);
	    if(app.isRoot(topicId)) {
	 	if(rootApp != -1)
		    System.out.println("Warning::more than ONE application is root for the same topic's multicast tree");
		else
		    rootApp = i; 
	    }
	}
	if(rootApp == -1)
	    System.out.println("Warning::No application is root for the topic's multicast tree");
	
	return rootApp;
     }


    /**
     * This does the Breadth First Traversal of the multicast tree for a topic.
     *
     * @param topicId
     * the topic id of the multicast tree to be traversed
     *
     * @return the total number of nodes in the tree OR returns -1 if the
     *         graph is not a TREE (it could be a DAG or maybe a cycle was
     *         detected.
     */
    public int BFS(NodeId topicId) {
	int appIndex;
	Vector toTraverse = new Vector();
	DirectSplitStreamTestApp app;
	Topic topic;
	Vector children;
	NodeId childId;
	int rootAppIndex;
	NodeHandle child;
	int i;
	int depth=0;
	Vector traversedList = new Vector();
	rootAppIndex = rootApp(topicId);
	
	//System.out.println("TREE Traversal for tree" + topicId);
	traversedList.add(new Integer(rootAppIndex));
	toTraverse.add(new Integer(rootAppIndex));
	toTraverse.add(new Integer(-1));
	// This -1 acts as a marker for a level of children and thus helps to compute the depth
	while(toTraverse.size() > 0 ) {
	    appIndex = ((Integer)toTraverse.remove(0)).intValue();
	    if(appIndex == -1) {
		depth++;
		if(toTraverse.size() != 0)
		    toTraverse.add(new Integer(-1));
		continue;
	    }
	    app = (DirectSplitStreamTestApp) splitStreamApps.elementAt(appIndex);
	    //System.out.println(" *** Children of " + app.getNodeId() + " ***");
	    topic = app.getScribe().getTopic(topicId);

	    children = topic.getChildren();
	    if( children.size() > 0){
		for(i=0; i < children.size(); i++) {
		    childId = ((NodeHandle)children.elementAt(i)).getNodeId();
		    //System.out.print(childId + " ");
		    app = (DirectSplitStreamTestApp)nodeIdToApp.get(childId);
		    appIndex = ((DirectSplitStreamTestApp)nodeIdToApp.get(childId)).m_appIndex;
		    if(!traversedList.contains(new Integer(appIndex)))
			{
			    traversedList.add(new Integer(appIndex));
			    toTraverse.add(new Integer(appIndex));
			}
		    else 
			{
			    // This node has been previously visited. So we
			    // either have a cycle or it results in DAG, but
			    // it is no longer a TREE.
			    System.out.println("Warning:: The graph being traversed is NOT a TREE"+ childId);
			    return -1;
			}
		}
		//System.out.println("");
	    }
	}
	//System.out.println("TREE TRAVERSAL COMPLETE:: DEPTH = " + depth + "Total Nodes = " + traversedList.size());
	return traversedList.size();

    }
    
    public boolean checkSend(ChannelId channelId){
	
	boolean result = true;
	DirectSplitStreamTestApp app;

	for(int i = 0; i < splitStreamApps.size(); i++){
	    app = (DirectSplitStreamTestApp) splitStreamApps.elementAt(i);
	    if(app.m_numRecv != app.getNumStripes(channelId)){
		result &= false;
		System.out.println("App "+i+" recv "+app.m_numRecv+" pkts, actually sent"+app.getNumStripes(channelId));
	    }
	}
	
	return result;
    }
}


