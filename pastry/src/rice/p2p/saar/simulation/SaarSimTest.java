package rice.p2p.saar.simulation;

import rice.p2p.saar.*;
import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.Vector;
import java.util.Hashtable;
import java.util.Random;

import rice.environment.Environment;
import rice.p2p.commonapi.Id;
import rice.pastry.NodeHandle;
import rice.pastry.NodeIdFactory;
import rice.pastry.PastryNode;
import rice.pastry.PastryNodeFactory;
import rice.pastry.direct.*;
import rice.pastry.leafset.LeafSet;
import rice.pastry.standard.RandomNodeIdFactory;

public class SaarSimTest {


    public static boolean CENTRALIZEDSCRIBE = false;  //p2p.saar.SaarImpl/p2p.saar.SaarPolicy uses this flag

    public static String INPUTFILESDIR = "/DS/usr/animesh/anycast/SAARSVN/saar/trunk/FreePastry-saar/pastry/inputfiles/";

    //public static String MATRIXFILE = "anycast/modelnet/deployednetwork/King_Modelnet.500";
    public static String MATRIXFILE = "King.dataplanes.500"; // This is a new matrix that we produced in which we have a more well defined way of extracting a subMatrix from the original King matrix, by simply taking random positions and filling '-1' with the average delay of 86 ms

    public static String BITTYRANTDIST = "BittyrantCDF";

    public static String MONARCHDIST = "MonarchCDF";

    public Vector cembandwidthcdf = new Vector();
    

    // this will keep track of our applications
    Vector apps = new Vector();

    Vector noderecords = new Vector(); 


    Vector nodes = new Vector(); 
    
    protected Environment env;

    public static int NUMNODES = 1;

    //public static int simulatorbroadcastseqnum = -1; // This is a temporrary hack for the simulator version to know the global broadcast sequnce number, in a decentralized environment it will use the grpSummary() code to set this value based on downward propagates in the saar tree

    public static int foregroundBroadcastSeqnum = -1; 

    public static int backgroundBroadcastSeqnum = -1; 



    public Random rngnodedegree = new Random(1);  // Updated to using a fixed seed on Oct 15
    public Random rnglossrate = new Random(1);  // Updated to using a fixed seed on Oct 15


    public class CdfTuple {
	public double bw; // kbps
	public double cdf;

	public CdfTuple(double bw, double cdf) {
	    this.bw = bw;
	    this.cdf = cdf;
	}
    }



    //public Hashtable grpSummaryMetadata = new Hashtable(); // this is a temporary hack to see the best possible scenario of the multitree with respect to reducing the join delay problem because of waiting for the grpSummary to arrive via the control plane

    /**
     * This constructor launches numNodes PastryNodes.  They will bootstrap 
     * to an existing ring if one exists at the specified location, otherwise
     * it will start a new ring.
     * 
     * @param bindport the local port to bind to 
     * @param bootaddress the IP:port of the node to boot from
     * @param numNodes the number of nodes to create in this JVM
     * @param env the environment for these nodes
     */
    public SaarSimTest(int numNodes, Environment env) throws Exception {

	this.env = env;

	readBandwidthDistCdf();

	rice.p2p.saar.SaarClient.MULTICASTSOURCEBINDINDEX = 1;
	

	// We will print out the importtant configurations
	System.out.println("");
	System.out.println("SAAR-CONFIGS");
	System.out.println("************");
	System.out.println("NUMNODES: " + numNodes);
	System.out.println("CENTRALIZEDSCRIBE: " + CENTRALIZEDSCRIBE);
  if (true) throw new RuntimeException("TODO: implement.");
//	System.out.println("pastry.direct.GenericNetwork.DELAYTOCENTRALIZEDSERVER: " + rice.pastry.direct.GenericNetwork.DELAYTOCENTRALIZEDSERVER);
	System.out.println("p2p.saar.SaarClient.DATAPLANETYPE: " + SaarClient.DATAPLANETYPE);
	System.out.println("p2p.saar.SaarTopic.NUMTREES: " + SaarTopic.NUMTREES);
	System.out.println("p2p.saar.SaarClient.CONTROLOVERLAYBOOTTIME: " + SaarClient.CONTROLOVERLAYBOOTTIME);
	System.out.println("p2p.saar.SaarClient.MAXINITIALWAITTIME: " + SaarClient.MAXINITIALWAITTIME);
	System.out.println("p2p.saar.SaarClient.EXPONENTIALOFFLINE: " + SaarClient.EXPONENTIALOFFLINE);
	System.out.println("p2p.saar.SaarClient.OFFLINETIME: " + SaarClient.OFFLINETIME);
	System.out.println("p2p.saar.SaarClient.NOCHURNTIME: " + SaarClient.NOCHURNTIME);
	System.out.println("p2p.saar.SaarClient.MEANDATAOVERLAYCHURNPERIOD: " + SaarClient.MEANDATAOVERLAYCHURNPERIOD);
	System.out.println("p2p.saar.SaarClient.MINIMUMDATAOVERLAYSTAYTIME: " + SaarClient.MINIMUMDATAOVERLAYSTAYTIME);
	System.out.println("p2p.saar.SaarClient.EXPERIMENTTIME: " + SaarClient.EXPERIMENTTIME);
	System.out.println("p2p.saar.SaarClient.DATAOVERLAYCHURNTYPE: " + SaarClient.DATAOVERLAYCHURNTYPE);
	System.out.println("p2p.saar.SaarClient.MUL: " + SaarClient.MUL);	  
	System.out.println("p2p.saar.SaarClient.HETEROGENOUSTYPE: " + SaarClient.HETEROGENOUSTYPE);
	System.out.println("p2p.saar.SaarClient.MINDEGREE: " + SaarClient.MINDEGREE);
	System.out.println("p2p.saar.SaarClient.DEGREECAP: " + SaarClient.DEGREECAP);
	System.out.println("p2p.saar.SaarClient.SOURCEDEGREE: " + SaarClient.SOURCEDEGREE);
	System.out.println("p2p.saar.blockbased.CoolstreamingBufferMap.ADVERTISEDWINDOWSIZE: " + rice.p2p.saar.blockbased.CoolstreamingBufferMap.ADVERTISEDWINDOWSIZE);
	System.out.println("p2p.saar.blockbased.CoolstreamingBufferMap.FETCHWINDOWSIZE: " + rice.p2p.saar.blockbased.CoolstreamingBufferMap.FETCHWINDOWSIZE);
	//System.out.println("p2p.saar.blockbased.BlockbasedClient.COOLSTREAMINGCONFIG: " + rice.p2p.saar.blockbased.BlockbasedClient.COOLSTREAMINGCONFIG);
	//System.out.println("p2p.saar.multitree.MultitreeClient.SPLITSTREAMCONFIG: " + rice.p2p.saar.multitree.MultitreeClient.SPLITSTREAMCONFIG);
	System.out.println("p2p.saar.SaarClient.MULTICASTSOURCEBINDINDEX: " + rice.p2p.saar.SaarClient.MULTICASTSOURCEBINDINDEX);
	System.out.println("p2p.saar.singletree.SingletreeClient.BLOCKPERIOD: " + rice.p2p.saar.singletree.SingletreeClient.BLOCKPERIOD);
	System.out.println("p2p.saar.multitree.MultitreeClient.BLOCKPERIOD: " + rice.p2p.saar.multitree.MultitreeClient.BLOCKPERIOD);
	System.out.println("p2p.saar.blockbased.BlockbasedClient.BLOCKPERIOD: " + rice.p2p.saar.blockbased.BlockbasedClient.BLOCKPERIOD);

	System.out.println("p2p.saar.multitree.SingletreeClient.NUMSTRIPES: " + rice.p2p.saar.singletree.SingletreeClient.NUMSTRIPES);
	System.out.println("p2p.saar.multitree.MultitreeClient.NUMSTRIPES: " + rice.p2p.saar.multitree.MultitreeClient.NUMSTRIPES);
	System.out.println("p2p.saar.blockbased.BlockbasedClient.ACQUIREDTHRESHOLD: " + rice.p2p.saar.blockbased.BlockbasedClient.ACQUIREDTHRESHOLD);
	System.out.println("p2p.saar.singletree.SingletreeClient.STRIPEINTERVALPERIOD: " + rice.p2p.saar.singletree.SingletreeClient.STRIPEINTERVALPERIOD);
	if (true) throw new RuntimeException("TODO: implement.");
//	System.out.println("pastry.direct.GenericNetwork.delayFactor: " + rice.pastry.direct.GenericNetwork.delayFactor);
	//System.out.println("p2p.saar.SaarClient.DATAPLANEMAINTENANCEPERIOD: " + rice.p2p.saar.SaarClient.DATAPLANEMAINTENANCEPERIOD);
	System.out.println("p2p.saar.SaarClient.CONTROLPLANEUPDATEPERIOD: " + rice.p2p.saar.SaarClient.CONTROLPLANEUPDATEPERIOD);
	System.out.println("p2p.saar.SaarClient.VIRTUALSOURCEMAXBINDINDEX: " + rice.p2p.saar.SaarClient.VIRTUALSOURCEMAXBINDINDEX);
	System.out.println("p2p.saar.singletree.SingletreeClient.NUMPRMNEIGHBORS: " + rice.p2p.saar.singletree.SingletreeClient.NUMPRMNEIGHBORS);
	System.out.println("p2p.saar.singletree.SingletreeClient.PRMBETA: " + rice.p2p.saar.singletree.SingletreeClient.PRMBETA);
	System.out.println("p2p.saar.singletree.SingletreeClient.ENABLEPRM: " + rice.p2p.saar.singletree.SingletreeClient.ENABLEPRM);

	System.out.println("p2p.saar.multitree.MultitreeClient.NUMREDUNDANTSTRIPES: " + rice.p2p.saar.multitree.MultitreeClient.NUMREDUNDANTSTRIPES);
	System.out.println("p2p.saar.blockbased.BlockbasedClient.ADVERTISINGPERIOD: " + rice.p2p.saar.blockbased.BlockbasedClient.ADVERTISINGPERIOD);
	System.out.println("p2p.saar.blockbased.BlockbasedClient.M: " + rice.p2p.saar.blockbased.BlockbasedClient.M);
	System.out.println("p2p.saar.blockbased.BlockbasedClient.RIINDEPENDENTOUTDEGREE: " + rice.p2p.saar.blockbased.BlockbasedClient.RIINDEPENDENTOUTDEGREE);
	System.out.println("p2p.saar.blockbased.BlockbasedClient.UNSYNCHRONIZEDWINDOW: " + rice.p2p.saar.blockbased.BlockbasedClient.UNSYNCHRONIZEDWINDOW);	
	System.out.println("p2p.saar.multitree.MultitreeClient.ENABLESIMULATORGRPSUMMARY: " + rice.p2p.saar.multitree.MultitreeClient.ENABLESIMULATORGRPSUMMARY);
	System.out.println("p2p.saar.blockbased.BlockbasedClient.DOFINEGRAINEDADVERTISING: " + rice.p2p.saar.blockbased.BlockbasedClient.DOFINEGRAINEDADVERTISING);	
	System.out.println("p2p.saar.blockbased.BlockbasedClient.DAMPENREQUESTBLOCKSMSG: " + rice.p2p.saar.blockbased.BlockbasedClient.DAMPENREQUESTBLOCKSMSG);	
	System.out.println("p2p.saar.blockbased.BlockbasedClient.STALEBMAPTHRESHOLD: " + rice.p2p.saar.blockbased.BlockbasedClient.STALEBMAPTHRESHOLD);	
	System.out.println("p2p.saar.SaarClient.HYBRIDDEBUG: " + rice.p2p.saar.SaarClient.HYBRIDDEBUG);
	System.out.println("p2p.saar.SaarClient.STREAMBANWIDTH(kbps): " + (rice.p2p.saar.SaarClient.STREAMBANDWIDTHINBYTES*8/1000));
	System.out.println("p2p.saar.multitree.MultitreeClient.AVOIDWAITFORGRPUPDATE: " + rice.p2p.saar.multitree.MultitreeClient.AVOIDWAITFORGRPUPDATE);
	System.out.println("p2p.saar.SaarClient.LEAVENOTIFY: " + rice.p2p.saar.SaarClient.LEAVENOTIFY);
	System.out.println("p2p.saar.SaarClient.NEIGHBORDEADTHRESHOLD: " + rice.p2p.saar.SaarClient.NEIGHBORDEADTHRESHOLD);
	System.out.println("p2p.saar.SaarClient.NEIGHBORHEARTBEATPERIOD: " + rice.p2p.saar.SaarClient.NEIGHBORHEARTBEATPERIOD);
	//System.out.println("p2p.saar.blockbased.BlockbasedClient.SWARMINGRETRYFACTOR: " + rice.p2p.saar.blockbased.BlockbasedClient.SWARMINGRETRYFACTOR);	
	System.out.println("p2p.saar.blockbased.BlockbasedClient.SWARMINGRETRYTHRESHOLD: " + rice.p2p.saar.blockbased.BlockbasedClient.SWARMINGRETRYTHRESHOLD);	
	System.out.println("p2p.saar.singletree.SaarClient.FIXEDAPPLICATIONLOSSPROBABILITY: " + rice.p2p.saar.SaarClient.FIXEDAPPLICATIONLOSSPROBABILITY);
	System.out.println("p2p.saar.saar.SaarClient.MEANLINKLOSSPROBABILITY: " + rice.p2p.saar.SaarClient.MEANLINKLOSSPROBABILITY);
	System.out.println("p2p.saar.saar.SaarClient.FRACTIONCONTROLBANDWIDTH: " + rice.p2p.saar.SaarClient.FRACTIONCONTROLBANDWIDTH);	
	System.out.println("p2p.saar.saar.SaarClient.MAXCONTROLTREEFANOUT: " + rice.p2p.saar.SaarClient.MAXCONTROLTREEFANOUT);	
	System.out.println("p2p.saar.saar.SaarClient.SIMULATETRANSMISSIONDELAYS: " + rice.p2p.saar.SaarClient.SIMULATETRANSMISSIONDELAYS);	
	System.out.println("p2p.saar.saar.SaarClient.SEPARATECONTROLANDDATAQUEUE: " + rice.p2p.saar.SaarClient.SEPARATECONTROLANDDATAQUEUE);	
	if (true) throw new RuntimeException("TODO: implement.");
//	System.out.println("pastry.direct.DirectPastryNode.BWPERIOD: " + rice.pastry.direct.DirectPastryNode.BWPERIOD);	
	System.out.println("p2p.saar.SaarClient.CENTRALSERVERDEGREE: " + rice.p2p.saar.SaarClient.CENTRALSERVERDEGREE);	
	System.out.println("p2p.saar.SaarClient.CONTROLTRAFFICISFREE: " + rice.p2p.saar.SaarClient.CONTROLTRAFFICISFREE);	
	System.out.println("p2p.saar.SaarClient.NICEBACKGROUNDTRAFFIC: " + rice.p2p.saar.SaarClient.NICEBACKGROUNDTRAFFIC);	
	System.out.println("p2p.saar.blockbased.BlockbasedClient.MINSWARMININTERVAL: " + rice.p2p.saar.blockbased.BlockbasedClient.MINSWARMINGINTERVAL);	
	System.out.println("p2p.saar.SaarClient.SIMULATEDOWNLINK: " + rice.p2p.saar.SaarClient.SIMULATEDOWNLINK);	
	//System.out.println("p2p.saar.SaarClient.IPPACKETMULTIPLEXING: " + rice.p2p.saar.SaarClient.IPPACKETMULTIPLEXING);	
	System.out.println("p2p.saar.SaarClient.MAXQUEUEDELAYBACKGROUND: " + rice.p2p.saar.SaarClient.MAXQUEUEDELAYBACKGROUND);	
	System.out.println("p2p.saar.blockbased.BlockbasedClient.ONLYOVERLOADINFO: " + rice.p2p.saar.blockbased.BlockbasedClient.ONLYOVERLOADINFO);	
	if (true) throw new RuntimeException("TODO: implement.");
//	System.out.println("pastry.direct.DirectPastryNode.SIZEMSGHEADER: " + rice.pastry.direct.DirectPastryNode.SIZEMSGHEADER);
//	System.out.println("pastry.direct.DirectPastryNode.MAXQUEUESTALL: " + rice.pastry.direct.DirectPastryNode.MAXQUEUESTALL);
	System.out.println("p2p.saar.singletree.SingletreeClient.SPARERITOTAKECHILD: " + rice.p2p.saar.singletree.SingletreeClient.SPARERITOTAKECHILD);
	System.out.println("p2p.saar.singletree.SingletreeClient.SPARERITODROPCHILD: " + rice.p2p.saar.singletree.SingletreeClient.SPARERITODROPCHILD);
	System.out.println("p2p.saar.blockbased.BlockbasedClient.DYNAMICMININDEGREE: " + rice.p2p.saar.blockbased.BlockbasedClient.DYNAMICMININDEGREE);	
	System.out.println("p2p.saar.blockbased.BlockbasedClient.MQDBUDYNAMIC: " + rice.p2p.saar.blockbased.BlockbasedClient.MQDBUDYNAMIC);	
	System.out.println("p2p.saar.blockbased.BlockbasedClient.BGUNAWAREOFLASTFGPKTRECVTIME: " + rice.p2p.saar.blockbased.BlockbasedClient.BGUNAWAREOFLASTFGPKTRECVTIME);	
	System.out.println("p2p.saar.blockbased.BlockbasedClient.DEGREEBASEDONMESHSUPPLY: " + rice.p2p.saar.blockbased.BlockbasedClient.DEGREEBASEDONMESHSUPPLY);	
	System.out.println("INPUTFILESDIR: " + INPUTFILESDIR);


	// Generate the NodeIds Randomly
	NodeIdFactory nidFactory = new RandomNodeIdFactory(env);
    
	// construct the PastryNodeFactory, this is how we use rice.pastry.direct, with a Euclidean Network
	//NetworkSimulator ns = new SphereNetwork(env);
	//NetworkSimulator ns = new EuclideanNetwork(env);
	
	//NetworkSimulator ns = new GenericNetwork(env, new File("/DS/usr/animesh/anycast/King_Modelnet.500"));

	
	String ABSOLUTEMATRIXFILE = INPUTFILESDIR + MATRIXFILE;
	NetworkSimulator ns = new GenericNetwork(env, new File(ABSOLUTEMATRIXFILE));
	
	SimulatorListener simlistener = new SaarSimulatorListener(env);
	ns.addSimulatorListener(simlistener);

	ns.setMaxSpeed((float)1.0);
	PastryNodeFactory factory = new DirectPastryNodeFactory(nidFactory, ns, env);
	
	// create the handle to boot off of
	NodeHandle bootHandle = null;
	NodeHandle centralizedHandle = null;





	// We assumed above that the {centralserver had index = 0, multicast source had index = 1}
	Vector nodedegrees = new Vector();
	// We will first get all the nodedegrees
	for (int curNode = 0; curNode < numNodes; curNode++) {
	    double nodedegree = SaarClient.getNodedegree(rngnodedegree, this);
	    nodedegrees.add(new Double(nodedegree));
	    //System.out.println("Adding bw to list: " + nodedegree); 
	}
	// We will sort the node degrees
	Vector sortedNodedegrees = new Vector();
	// We will first add the centralized server degree
	sortedNodedegrees.add(new Double(SaarClient.CENTRALSERVERDEGREE + 0.5));
	// We will first add the source degree
	sortedNodedegrees.add(new Double(SaarClient.SOURCEDEGREE + 0.5));  // Updated on Oct27-2007, We add the 0.5 to provide so RI for control

	double chosenNodedegree = 0;
	while(!nodedegrees.isEmpty()) {
	    double nodedegree;
	    nodedegree = ((Double)nodedegrees.elementAt(0)).doubleValue();
	    chosenNodedegree = nodedegree;
	    
	    for(int index = 1; index < nodedegrees.size(); index++) {
		nodedegree = ((Double)nodedegrees.elementAt(index)).doubleValue();
		if(nodedegree > chosenNodedegree) {
		    chosenNodedegree = nodedegree;
		}
	    }
	    //System.out.println("Adding max to sortedlist: " + chosenNodedegree); 
	    sortedNodedegrees.add(new Double(chosenNodedegree + SaarClient.FRACTIONCONTROLBANDWIDTH));
	    nodedegrees.remove(new Double(chosenNodedegree));
	}
	// Since we initially added the CENTRALSERVERDEGREE/SOURCEDEGREE, we will remove the last member from the sorted list
	sortedNodedegrees.remove(sortedNodedegrees.size()-1);

	sortedNodedegrees.remove(sortedNodedegrees.size()-1);

	if(sortedNodedegrees.size() != numNodes) {
	    System.out.println("The number of entries in the sorted nodedegrees is not equal to the overlay size");
	    System.exit(1);
	}



	
	// We will now get all loss rates
	Vector lossrates = new Vector();
	for (int curNode = 0; curNode < numNodes; curNode++) {
	    double lossrate = SaarClient.getLossRate(rnglossrate);
	    lossrates.add(new Double(lossrate));
	    //System.out.println("Adding bw to list: " + nodedegree); 
	}








	// loop to construct the nodes
	for (int curNode = 0; curNode < numNodes; curNode++) {

	    ns.setMaxSpeed(curNode+1);

	    System.out.println("curNode: " + curNode);
	    // construct a node, passing the null boothandle on the first loop will cause the node to start its own ring
	    PastryNode node = factory.newNode(bootHandle);

	    // this way we can boot off the previous node
	    bootHandle = node.getLocalHandle();

	    // The centralizedHandle remains null when CENTRALIZEDSCRIBE= false
	    if(CENTRALIZEDSCRIBE && (curNode == 0)) {
		centralizedHandle = bootHandle;
		System.out.println("CentralizedHandle: " + centralizedHandle);

	    }
	    
	    System.out.println("after bootHandle");
	    // the node may require sending several messages to fully boot into the ring
	    synchronized(node) {
		while(!node.isReady() && !node.joinFailed()) {
		    System.out.println("in loop");
		    // delay so we don't busy-wait
		    node.wait(500);
		    
		    // abort if can't join
		    if (node.joinFailed()) {
			throw new IOException("Could not join the FreePastry ring.  Reason:"+node.joinFailedReason()); 
		    }
		}       
	    }
	    nodes.add(node);
	    System.out.println("Finished creating new node[" + curNode + "] " + node);
	}

	ns.setMaxSpeed(1.0f);






	// loop to attach apps to nodes
	for (int curNode = 0; curNode < numNodes; curNode++) {
	    PastryNode node = (PastryNode)nodes.elementAt(curNode);
	    int BINDINDEX = curNode;
	    int JVMINDEX = 0;
	    int VINDEX = 0;
	    int SESSIONTIME = 3000;
	    long BOOTTIME = env.getTimeSource().currentTimeMillis();
	    boolean amMulticastSource = false;
	    boolean amVirtualSource = false;


	    if(BINDINDEX == SaarClient.MULTICASTSOURCEBINDINDEX) {
		amMulticastSource = true;

	    } else if((BINDINDEX > SaarClient.MULTICASTSOURCEBINDINDEX) && (BINDINDEX <= SaarClient.VIRTUALSOURCEMAXBINDINDEX)) {
	    
		amVirtualSource = true;
		
	    } else {
		// Centralized node (index=0) or other normal nodes
	    }
	    

	    // We now set the upstreambandwidth/upstreamlossrate in the noderecord
      throw new RuntimeException("TODO: implement.");      
//	    rice.pastry.direct.NodeRecord nr = ((DirectPastryNode)node).getDirectPastryNodeRecord();
//	    double nodedegree = ((Double)sortedNodedegrees.remove(0)).doubleValue();
//	    double lossprobability = ((Double)lossrates.remove(0)).doubleValue();
//	    nr.setUpstreamLossprobability(lossprobability);
//
//	    //nr.setUpstreamBandwidthInBytes( (nodedegree * SaarClient.STREAMBANDWIDTHINBYTES) + (SaarClient.FRACTIONCONTROLBANDWIDTH * SaarClient.STREAMBANDWIDTHINBYTES)); // We updated this to now factor in the extra FRACCONTROLBANDWIDTH directly into the sortedNodedegree list above
//
//	    if(SaarClient.PINGTEST) {
//		nr.setUpstreamBandwidthInBytes(12500);  // i.e 100 Kbps
//	    } else {
//		nr.setUpstreamBandwidthInBytes(nodedegree * SaarClient.STREAMBANDWIDTHINBYTES);
//	    }
//
//	    // Default is 1 Mbps, for Bittyrant workload where the streaming rate is high use 4 Mbps
//	    if(SaarClient.HETEROGENOUSTYPE == SaarClient.BITTYRANT) {
//		nr.setDownstreamBandwidthInBytes(125000*4); // i.e 4 Mbps
//	    } else {
//		nr.setDownstreamBandwidthInBytes(125000);  // i.e 1 Mbps
//	    }
//	    //Set downstream based on upstream nr.setDownstreamBandwidthInBytes(nodedegree * SaarClient.STREAMBANDWIDTHINBYTES * downstreamToUpstreamRatio);
//	    
//	    
//	    noderecords.add(nr);
//
//	    SaarClient app = new SaarClient(BINDINDEX, JVMINDEX,  VINDEX, node, null, null, factory, "saarClient", SESSIONTIME, BOOTTIME, centralizedHandle, nodedegree, amMulticastSource, amVirtualSource, lossprobability, this);
//	    apps.add(app);
//	    System.out.println("AssignedNodedegree: " + nodedegree + " AssignedLossrate: " + lossprobability + " Finished attaching application[" + curNode + "] " + node);
	}

	ns.setFullSpeed();	


	
	// wait 10 seconds
	env.getTimeSource().sleep(10000);

	if(SaarClient.PINGTEST) {
    throw new RuntimeException("TODO: implement.");
//	    int nrsrcIndex = -1;
//	    int nrdestIndex = -1;
//	    for(int i =0; i< apps.size(); i ++) {
//		SaarClient clientsrc = (SaarClient) apps.elementAt(i);
//		rice.pastry.direct.NodeRecord nrsrc = (rice.pastry.direct.NodeRecord) noderecords.elementAt(i);	
//    throw new RuntimeException("TODO: implement.");
//		rice.pastry.direct.GenericNetwork.GNNodeRecord gnnrsrc = (rice.pastry.direct.GenericNetwork.GNNodeRecord)nrsrc;
//		nrsrcIndex = gnnrsrc.getIndex();
//		for(int j=0; j< apps.size(); j++) {
//		    if(j!=i) {
//			SaarClient clientdest = (SaarClient) apps.elementAt(j);
//			rice.pastry.direct.NodeRecord nrdest = (rice.pastry.direct.NodeRecord) noderecords.elementAt(j);
//			rice.pastry.direct.GenericNetwork.GNNodeRecord gnnrdest = (rice.pastry.direct.GenericNetwork.GNNodeRecord)nrdest;
//			nrdestIndex = gnnrdest.getIndex();
//
//			float forwarddelay = nrsrc.networkDelay(nrdest);
//			float reversedelay = nrdest.networkDelay(nrsrc);
//			
//			if(forwarddelay != reversedelay) {
//			    System.out.println("WARNING: Delays dont match in forward/reverse direction, fwd: " + forwarddelay + " rev: " + reversedelay + " srcindex: " + nrsrcIndex + " destindex: " + nrdestIndex);
//			    //System.exit(1);
//			}
//			System.out.println("forwarddelay: " + forwarddelay + " reversedelay: " + reversedelay);
//			for(int payloadinbytes= 0; payloadinbytes <= 5000; payloadinbytes = payloadinbytes + 1000) {
//			    clientsrc.issuePing(clientdest.endpoint.getLocalNodeHandle(), (forwarddelay + reversedelay), payloadinbytes);
//			    // wait 1 seconds
//			    env.getTimeSource().sleep(5000); 
//
//			}
//		    }
//
//		    
//		}
//	    }
//	    
//	
//	    // wait 10 seconds
//	    env.getTimeSource().sleep(10000);
//	    
//	    System.exit(1);
	}
	
    }

    
    public void readBandwidthDistCdf() {

	if(!( (SaarClient.HETEROGENOUSTYPE == SaarClient.BITTYRANT) || (SaarClient.HETEROGENOUSTYPE == SaarClient.MONARCH))) {

	    // In this case we dont need to read in any distribution
	    return; 
	}
	File inFile; 
	if(SaarClient.HETEROGENOUSTYPE == SaarClient.MONARCH) {
	    String ABSOLUTEMONARCHDIST = INPUTFILESDIR + MONARCHDIST; 
	    inFile = new File(ABSOLUTEMONARCHDIST);
	    //inFile = new File("/DS/usr/animesh/anycast/MonarchCDF");
	} else {
	    String ABSOLUTEBITTYRANTDIST = INPUTFILESDIR + BITTYRANTDIST; 
	    inFile = new File(ABSOLUTEBITTYRANTDIST);
	    //inFile = new File("/DS/usr/animesh/anycast/BittyrantCDF");
	}

	FileReader fr = null;
	try {
	    fr = new FileReader(inFile);
	    
	    BufferedReader in = new BufferedReader(fr);
	    
	    int lineCount = 0;
	    String line = null;
	   
	    while ((line = in.readLine()) != null) {
		String[] words;
		words = line.split("[ \t]+");
		double bw = Float.parseFloat(words[0]);         // bw is in kbps
		double cdf = Float.parseFloat(words[1]);
		// The pair (words[0] and words[1] ) denote the cem-upstream-bandwidth distritbuion
		//System.out.println("FILETYPE: " + SaarClient.HETEROGENOUSTYPE + " , bw: " + bw + ", cdf: " + cdf);
		cembandwidthcdf.add(new CdfTuple(bw,cdf));
	    }
	    	    
	    fr.close();	    
	} catch (Exception e) {
	    System.out.println("Exception while reading bandwidth distribution file " + e);
	   
	}
	


    }

    /**
     * Usage: 
     * java [-cp FreePastry-<version>.jar] rice.tutorial.direct.DirectTutorial numNodes
     * example java rice.tutorial.direct.DirectTutorial 100
     */
    public static void main(String[] args) throws Exception {
	// Loads pastry settings, and sets up the Environment for simulation
	Environment env = Environment.directEnvironment();

	env.getParameters().setBoolean("environment_logToFile", true);


	// We do the parsing of the options
	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-help")) {
		System.out.println("Usage: SaarSimTest -nodes -dataplanetype -iscentralized -delaytocentralserver -matrixfile");
		System.exit(1);
	    }
	}


	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-nodes") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>=0) {
		    NUMNODES = n;
	      } 
		break;
	    }
	}



	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-virtualsourcemaxbindindex") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>=0) {
		    System.out.println("Setting virtualsourcemaxbindindex= " + n);
		    SaarClient.VIRTUALSOURCEMAXBINDINDEX = n;
		} 
		break;
	    }
	}


	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-dataplanetype") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>=0) {
		    SaarClient.DATAPLANETYPE= n;
		    
	      } 
		break;
	    }
	}



	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-neighbordeadthreshold") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>=0) {
		    SaarClient.NEIGHBORDEADTHRESHOLD = n;
		    
	      } 
		break;
	    }
	}


	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-meanstaytime") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>=0) {
		    SaarClient.MEANDATAOVERLAYCHURNPERIOD = n;
		    
	      } 
		break;
	    }
	}

	
	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-minimumstaytime") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>=0) {
		    SaarClient.MINIMUMDATAOVERLAYSTAYTIME = n;
		    
	      } 
		break;
	    }
	}


	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-degreedist") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>=0) {
		    SaarClient.HETEROGENOUSTYPE = n;
		    
	      } 
		break;
	    }
	}

	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-mul") && i + 1 < args.length) {
		float n = Float.parseFloat(args[i + 1]);
		if(n>=0) {
		    SaarClient.MUL = (double)n;
		    
	      } 
		break;
	    }
	}


	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-mindegree") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>=0) {
		    SaarClient.MINDEGREE = n;
		    
	      } 
		break;
	    }
	}



	// streambandwidth is in kbps , default is 400 kbps
	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-streambandwidth") && i + 1 < args.length) {
		float n = Float.parseFloat(args[i + 1]);
		//int n = Integer.parseInt(args[i + 1]);
		if(n>=0) {
		    SaarClient.STREAMBANDWIDTHINBYTES = (int)(n * 1000 / 8);
		    
	      } 
		break;
	    }
	}
	


	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-maxcontroltreefanout") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>=0) {
		    SaarClient.MAXCONTROLTREEFANOUT = n;
		    
	      } 
		break;
	    }
	}


	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-degreecap") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>=0) {
		    SaarClient.DEGREECAP = n;
		    
	      } 
		break;
	    }
	}


	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-sourcedegree") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>=0) {
		    SaarClient.SOURCEDEGREE = n;
		    
	      } 
		break;
	    }
	}


	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-sizemsgheader") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>=0) {
      throw new RuntimeException("TODO: implement.");
//		    rice.pastry.direct.DirectPastryNode.SIZEMSGHEADER = n;
		    
	      } 
		break;
	    }
	}

	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-maxqueuestall") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>=0) {
      throw new RuntimeException("TODO: implement.");
//		    rice.pastry.direct.DirectPastryNode.MAXQUEUESTALL = n;
		    
	      } 
		break;
	    }
	}



	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-numneighbors") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>=0) {
		    rice.p2p.saar.blockbased.BlockbasedClient.M = n;
		    
	      } 
		break;
	    }
	}


	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-advertisedwindowsize") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>=0) {
		    rice.p2p.saar.blockbased.CoolstreamingBufferMap.ADVERTISEDWINDOWSIZE = n;
		    
	      } 
		break;
	    }
	}



	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-fetchwindowsize") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>=0) {
		    rice.p2p.saar.blockbased.CoolstreamingBufferMap.FETCHWINDOWSIZE = n;
		    
	      } 
		break;
	    }
	}




	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-linkloss") && i + 1 < args.length) {
		float n = Float.parseFloat(args[i + 1]);
		if(n>=0) {
		    rice.p2p.saar.SaarClient.MEANLINKLOSSPROBABILITY = n;
		    
		} 
		break;
	    }
	}

	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-fractioncontrolbw") && i + 1 < args.length) {
		float n = Float.parseFloat(args[i + 1]);
		if(n>=0) {
		    rice.p2p.saar.SaarClient.FRACTIONCONTROLBANDWIDTH = n;
		    
		} 
		break;
	    }
	}


 	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-spareritotakechild") && i + 1 < args.length) {
		float n = Float.parseFloat(args[i + 1]);
		if(n>=0) {
		    rice.p2p.saar.singletree.SingletreeClient.SPARERITOTAKECHILD = n;
		    
		} 
		break;
	    }
	}


	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-spareritodropchild") && i + 1 < args.length) {
		float n = Float.parseFloat(args[i + 1]);
		if(n>=0) {
		    rice.p2p.saar.singletree.SingletreeClient.SPARERITODROPCHILD = n;
		    
		} 
		break;
	    }
	}





	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-networkdelayfactor") && i + 1 < args.length) {
		float n = Float.parseFloat(args[i + 1]);
		if(n>=0) {
      throw new RuntimeException("TODO: implement.");
//		    rice.pastry.direct.GenericNetwork.delayFactor = n;
		    
		} 
		break;
	    }
	}


	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-swarmingretrythreshold") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>=0) {
		    rice.p2p.saar.blockbased.BlockbasedClient.SWARMINGRETRYTHRESHOLD = n;
		    
		} 
		break;
	    }
	}


	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-maxqueuedelaybackground") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>=0) {
		    rice.p2p.saar.SaarClient.MAXQUEUEDELAYBACKGROUND = n;
		    
		} 
		break;
	    }
	}




	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-prmbeta") && i + 1 < args.length) {
		float n = Float.parseFloat(args[i + 1]);
		if(n>=0) {
		    rice.p2p.saar.singletree.SingletreeClient.PRMBETA  = (double) n;
		    
		} 
		break;
	    }
	}

	
	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-applicationloss") && i + 1 < args.length) {
		float n = Float.parseFloat(args[i + 1]);
		if(n>=0) {
		    rice.p2p.saar.SaarClient.FIXEDAPPLICATIONLOSSPROBABILITY  = (double) n;
		} 
		break;
	    }
	}



	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-inputfilesdir") && i + 1 < args.length) {
	        INPUTFILESDIR = args[i + 1];
		break;
	    }
	}

	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-numstripes") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>=0) {
		    rice.p2p.saar.multitree.MultitreeClient.NUMSTRIPES = n;
		    
		} 
		break;
	    }
	}

	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-stripeintervalperiod") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>=0) {
		    rice.p2p.saar.singletree.SingletreeClient.STRIPEINTERVALPERIOD = n;        // ms
		    
	      } 
		break;
	    }
	}



	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-publishperiod") && i + 1 < args.length) {
		System.out.println("ERROR: We no longer support -publishperiod, we only support -blocksize");
		System.exit(1);
		int n = Integer.parseInt(args[i + 1]);
		if(n>=0) {
		    rice.p2p.saar.singletree.SingletreeClient.PUBLISHPERIOD = n;        // ms
		    rice.p2p.saar.multitree.MultitreeClient.PUBLISHPERIOD = n;        // ms
		    rice.p2p.saar.blockbased.BlockbasedClient.PUBLISHPERIOD = n; // ms
		    
		} 
		break;
	    }
	}

	
	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-blockperiod") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>=0) {
		    rice.p2p.saar.singletree.SingletreeClient.BLOCKPERIOD = n;        // ms
		    rice.p2p.saar.multitree.MultitreeClient.BLOCKPERIOD = n;        // ms
		    rice.p2p.saar.blockbased.BlockbasedClient.BLOCKPERIOD = n; // ms
		    
		} 
		break;
	    }
	}



	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-advertisingperiod") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>=0) {
		    rice.p2p.saar.blockbased.BlockbasedClient.ADVERTISINGPERIOD = n; // ms
		    
	      } 
		break;
	    }
	}

	// We now use the SaarClient.PUBLISHPERIOD to also determine the frequency of scheduling of dataplaneMaintenance()
	//for (int i = 0; i < args.length; i++) {
	//  if (args[i].equals("-dataplanemaintenanceperiod") && i + 1 < args.length) {
	//int n = Integer.parseInt(args[i + 1]);
	//if(n>=0) {
	//    rice.p2p.saar.SaarClient.DATAPLANEMAINTENANCEPERIOD  = n;        // ms
	//    
	//} 
	//break;
	//  }
	//}

	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-controlplaneupdateperiod") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>=0) {
		    rice.p2p.saar.SaarClient.CONTROLPLANEUPDATEPERIOD  = n;        // ms
		    
		} 
		break;
	    }
	}


	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-stalebmapthreshold") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>=0) {
		    rice.p2p.saar.blockbased.BlockbasedClient.STALEBMAPTHRESHOLD  = n;        // ms
		    
		} 
		break;
	    }
	}


	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-minswarminginterval") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>=0) {
		    rice.p2p.saar.blockbased.BlockbasedClient.MINSWARMINGINTERVAL  = n;        // ms
		    
		} 
		break;
	    }
	}


	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-blockbasedneighborrefresh") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>=0) {
		    rice.p2p.saar.blockbased.BlockbasedClient.ACQUIREDTHRESHOLD = n;        // ms
		    
		} 
		break;
	    }
	}


	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-nochurntime") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>=0) {
		    rice.p2p.saar.SaarClient.NOCHURNTIME = n;        // ms
		    
		} 
		break;
	    }
	}



	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-delaytocentralserver") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>=0) {
		  throw new RuntimeException("TODO: implement.");
//		    GenericNetwork.DELAYTOCENTRALIZEDSERVER = n;
		} 
		break;
	    }
	}
	


	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-iscentralized") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>0) {
		    CENTRALIZEDSCRIBE = true;
		    rice.p2p.saar.SaarClient.CENTRALSERVERDEGREE = 300000;
		} else {
		    CENTRALIZEDSCRIBE = false;
		    
		}
		break;
	    }
	}


	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-pingtest") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>0) {
		    rice.p2p.saar.SaarClient.PINGTEST = true;
		} else {
		    rice.p2p.saar.SaarClient.PINGTEST = false;
		    
		}
		break;
	    }
	}


	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-separatequeues") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>0) {
		    rice.p2p.saar.SaarClient.SEPARATECONTROLANDDATAQUEUE = true;
		} else {
		    rice.p2p.saar.SaarClient.SEPARATECONTROLANDDATAQUEUE = false;
		}
		break;
	    }
	}

	
	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-leavenotify") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>0) {
		    rice.p2p.saar.SaarClient.LEAVENOTIFY = true;
		} else {
		    rice.p2p.saar.SaarClient.LEAVENOTIFY = false;
		}
		break;
	    }
	}



	
	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-dynamicminindegree") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>0) {
		    rice.p2p.saar.blockbased.BlockbasedClient.DYNAMICMININDEGREE = true;
		} else {
		    rice.p2p.saar.blockbased.BlockbasedClient.DYNAMICMININDEGREE = false;
		}
		break;
	    }
	}


	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-degreebasedonmeshsupply") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>0) {
		    rice.p2p.saar.blockbased.BlockbasedClient.DEGREEBASEDONMESHSUPPLY = true;
		} else {
		    rice.p2p.saar.blockbased.BlockbasedClient.DEGREEBASEDONMESHSUPPLY = false;
		}
		break;
	    }
	}



	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-bgunawareoffg") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>0) {
		    rice.p2p.saar.blockbased.BlockbasedClient.BGUNAWAREOFLASTFGPKTRECVTIME = true;
		} else {
		    rice.p2p.saar.blockbased.BlockbasedClient.BGUNAWAREOFLASTFGPKTRECVTIME = false;
		}
		break;
	    }
	}


	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-mqdbudynamic") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>0) {
		    rice.p2p.saar.blockbased.BlockbasedClient.MQDBUDYNAMIC = true;
		} else {
		    rice.p2p.saar.blockbased.BlockbasedClient.MQDBUDYNAMIC = false;
		}
		break;
	    }
	}




	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-freecontrol") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>0) {
		    rice.p2p.saar.SaarClient.CONTROLTRAFFICISFREE = true;
		} else {
		    rice.p2p.saar.SaarClient.CONTROLTRAFFICISFREE = false;
		}
		break;
	    }
	}



	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-nicebgtraffic") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>0) {
		    rice.p2p.saar.SaarClient.NICEBACKGROUNDTRAFFIC = true;
		} else {
		    rice.p2p.saar.SaarClient.NICEBACKGROUNDTRAFFIC = false;
		}
		break;
	    }
	}

	//for (int i = 0; i < args.length; i++) {
	//  if (args[i].equals("-ippacketmultiplexing") && i + 1 < args.length) {
	//int n = Integer.parseInt(args[i + 1]);
	//if(n>0) {
	//    rice.p2p.saar.SaarClient.IPPACKETMULTIPLEXING = true;
	//} else {
	//    rice.p2p.saar.SaarClient.IPPACKETMULTIPLEXING = false;
	//}
	//break;
	//  }
	//}


	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-onlyoverloadinfo") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>0) {
		    rice.p2p.saar.blockbased.BlockbasedClient.ONLYOVERLOADINFO = true;
		} else {
		    rice.p2p.saar.blockbased.BlockbasedClient.ONLYOVERLOADINFO = false;
		}
		break;
	    }
	}




	//for (int i = 0; i < args.length; i++) {
	//  if (args[i].equals("-simulatorgrpsummary") && i + 1 < args.length) {
	//int n = Integer.parseInt(args[i + 1]);
	//if(n>0) {
	//    rice.p2p.saar.multitree.MultitreeClient.ENABLESIMULATORGRPSUMMARY = true;
	//} 
	//break;
	//  }
	//}

	//for (int i = 0; i < args.length; i++) {
	//  if (args[i].equals("-splitstreamconfig") && i + 1 < args.length) {
	//int n = Integer.parseInt(args[i + 1]);
	//if(n>0) {
	//    rice.p2p.saar.multitree.MultitreeClient.SPLITSTREAMCONFIG = true;
	//} 
	//break;
	//  }
	//}



	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-hybriddebug") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>0) {
		    rice.p2p.saar.SaarClient.HYBRIDDEBUG = true;
		} else {
		    rice.p2p.saar.SaarClient.HYBRIDDEBUG = false;
		}
		break;
	    }
	}


	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-simulatetransmissiondelays") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>0) {
		    rice.p2p.saar.SaarClient.SIMULATETRANSMISSIONDELAYS = true;
		} else {
		    rice.p2p.saar.SaarClient.SIMULATETRANSMISSIONDELAYS = false;
		}
		break;
	    }
	}

	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-simulatedownlink") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>0) {
		    rice.p2p.saar.SaarClient.SIMULATEDOWNLINK = true;
		} else {
		    rice.p2p.saar.SaarClient.SIMULATEDOWNLINK = false;
		}
		break;
	    }
	}



	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-avoidwait") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>0) {
		    rice.p2p.saar.multitree.MultitreeClient.AVOIDWAITFORGRPUPDATE = true;
		} else {
		    rice.p2p.saar.multitree.MultitreeClient.AVOIDWAITFORGRPUPDATE = false;
		}
		break;
	    }
	}




	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-dampenrequestblocksmsg") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>0) {
		    rice.p2p.saar.blockbased.BlockbasedClient.DAMPENREQUESTBLOCKSMSG = true;
		} else {
		    rice.p2p.saar.blockbased.BlockbasedClient.DAMPENREQUESTBLOCKSMSG = false;
		}
		break;
	    }
	}


	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-unsynchronizedwindow") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>0) {
		    rice.p2p.saar.blockbased.BlockbasedClient.UNSYNCHRONIZEDWINDOW = true;
		} else {
		    rice.p2p.saar.blockbased.BlockbasedClient.UNSYNCHRONIZEDWINDOW = false;
		}
		break;
	    }
	}





	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-primaryreconstruction") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>0) {
		    rice.p2p.saar.multitree.MultitreeClient.ENABLEPRIMARYFRAGMENTRECONSTRUCTION = true;
		} else {
		    rice.p2p.saar.multitree.MultitreeClient.ENABLEPRIMARYFRAGMENTRECONSTRUCTION = false;
		}
		break;
	    }
	}


	
	//for (int i = 0; i < args.length; i++) {
	//  if (args[i].equals("-bandwidthstaging") && i + 1 < args.length) {
	//int n = Integer.parseInt(args[i + 1]);
	//if(n>0) {
	//    rice.p2p.saar.singletree.SingletreeClient.BANDWIDTHSTAGING = true;
	//} 
	//break;
	//  }
	//}


	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-riindependentoutdegree") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>0) {
		    rice.p2p.saar.blockbased.BlockbasedClient.RIINDEPENDENTOUTDEGREE = true;
		} else {
		    rice.p2p.saar.blockbased.BlockbasedClient.RIINDEPENDENTOUTDEGREE = false;
		}
		break;
	    }
	}


	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-dofinegrainedadvertising") && i + 1 < args.length) {
		int n = Integer.parseInt(args[i + 1]);
		if(n>0) {
		    rice.p2p.saar.blockbased.BlockbasedClient.DOFINEGRAINEDADVERTISING = true;
		} else {
		    rice.p2p.saar.blockbased.BlockbasedClient.DOFINEGRAINEDADVERTISING = false;
		}
		break;
	    }
	}
	

	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-matrixfile") && i + 1 < args.length) {
		MATRIXFILE = args[i + 1];
		
	     
		break;
	    }
	}




    
	try {
	    SaarSimTest st = new SaarSimTest(NUMNODES, env);
	} catch (Exception e) {
	    // remind user how to use
	    System.out.println("Usage:"); 
	    System.out.println("java [-cp FreePastry-<version>.jar] rice.p2p.saar.simulation.SaarSimTest numNodes");
	    throw e; 
	}
    }
}
