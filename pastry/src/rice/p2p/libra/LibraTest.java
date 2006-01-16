/*
 * Created on Apr 6, 2005
 */
package rice.p2p.libra;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.util.*;
import rice.environment.Environment;
import rice.environment.logging.*;
import rice.pastry.*;
import rice.pastry.leafset.LeafSet;
import rice.pastry.socket.SocketPastryNodeFactory;
import rice.pastry.standard.*;
import rice.pastry.standard.RandomNodeIdFactory;

/**
 * @author Jeff Hoye
 */
public class LibraTest  {
    // When this node starts it checks if the RUNID corresponds to this jarversion. By this way if a node has a previous version of jar then it will not boot
    public static final int JARVERSION = 111;

    public static boolean MODELNET = false;

    public static final int PRINTLIBRA_LEVEL = 850; // This is all logging in Libra
    public static final int PRINTLIBRA_ESSENTIAL = 875; // This is the essential logging in Libra
    public static final int PRINTESMSERVER_ESSENTIAL = 880; // This is the essential logging in Libra
    


    //public static final int logLevel = Logger.ALL;         
    //public static final int logLevel = Logger.INFO;
    //public static final int logLevel = PRINTLIBRA_LEVEL;  
    //public static final int logLevel = PRINTLIBRA_ESSENTIAL;  
    public static final int logLevel = PRINTESMSERVER_ESSENTIAL;          


    public static int PIGGYBACKUPDATETHRESHOLD = 25;
    public static boolean UPDATEPIGGYBACKING = true;
    public static boolean OWNSERIALIZER = true; // This is for TCP
    public static boolean OWNUDPSERIALIZER = false; // This is for UDP
    public static boolean USEUDPFORREALTIME = false;
    public static int NUMDUPLICATES = 0; // This is the number of duplicates sent in the underlying UDP messaging
    public static String NAMETOIPCODEDFILE = "NameToIpCoded.nds"; // This contains the mapping of nodenames to codes for efficient serialization. When this localnode is not found in this file, the serialization will explicitly write the nodename 


    public static String MODELNETCONFIGFILE = "/DS/usr/animesh/anycast/modelnet/examplenetwork/example.config"; // This contains the ipaddresses of the modelnet nodes



    public static String CENTRALIZEDNODEFILE = "CentralizedMapping.nds"; // This contains the mapping of topicNumber to centralized nodes responsible for the topic 



    public static boolean ENABLEFASTCONVERGENCE = false;



    public static final int LIBRAESM = 1;
    public static final int SPLITSTREAM = 2;
    public static final int PASTRYTEST = 3;



    // This includes variables that need to be changed depending on whether we are running Splitstream/Libra or on planetlab

    // This is the number of trees we have corresponding to each anycast group
    public static int NUMTREES = 2;  // default is a single tree
    public static int testType = LIBRAESM; 

    public static boolean DEBUG = false; // This puts timestamp information in the Anycast messages
    public static int portShift = 0; // This tells us on which port group to run

    public static double PASTRYQUERYPERIOD  = 1; // in sec
    public static double UDPQUERYPERIOD  = 1; // in sec
    public static int PACKETSININTERVAL = 1; // 

    // These help in determing the anyucast rate/traversal
    public static double ANYCASTPERIOD = 0.5; // defauut , can be set using -anycastPeriod
    public static int ANYCASTTRAVERSALTHRESHOLD = 2; // Traverse minimum # of nodes to analyze performance
    public static int MAXSATISFIEDCHILDREN = 5; // This value should be higher than ANYCASTTRAVERSALTHRESHOLD, this helps limiting the size of the naycast message, but is done on a per intermediate node basis

    public static int MAXANYCASTWILLTRAVERSE = 20; // This is the total number of nodes the anycast will be allowed to traverse before we declare a failure, detected using visited.size() in directAnycast(). This variable is udes in the directAnycast() mehtod in ESMScribePolicy to decide when to remove endnodes from toVisit list before adding fresh nodes in toVisit list. 



    public static int MAXSPLITSTREAMANYCASTWILLTRAVERSE = 20;

    // WARNING: This feature has been disabled becuase limiting in this fashion does not guarantee that we will reach a leaf 
    //public static int MAXANYCASTTOVISIT = 5; // This helps limiting the lookahead toVisit nodes in the anycast message to keep the size of the message low


    // Group metadata via downward proagation is updated every DOWNWARDUPDATETHREADPERIOD, but a stale data incase it misses fresh updates can be returned withing 5 sec)
    public static long GRPMETADATASTALEPERIOD = 60; // in sec

    // Variables for load propagation, this is the downward propagation rate
    public static long DOWNWARDUPDATETHREADPERIOD = 120; // in sec 
    
    

    public static boolean ENABLEUPDATEACK = false;
    // This variable tells us if the update is immediately propagated all the way to the root, updating metadata along path
    public static boolean IMMEDIATEUPDATEPROP = false; 
    public static int ESMLIBRA_POLICY = ESMScribePolicy.ESMLIBRA_TIME;
    public static int LOSSTHRESHOLD = 50;


    // These variables will be changed in main() depending on whether we are running Libra/Splitstream

    public static boolean useLibra = false;
    public static boolean useSplitstream = false; 
    public static boolean usePastrytest = true;
    public static int PORT = 11111;
    // The port for Pastry is the highest port so that when using virtual nodes, they can grab consecutive ports starting from this base port
    public static int BOOTSTRAP_PORT = 11111;
    public static int SCRIBESERVERPORT = 11110;  
    public static int UDPSOCKETPORT = 11109;
    public static int DUMMYESMSERVERPORT = 11108;
    public static int DUMMYESMDATAPATHPORT = 1; // We will not be using this in any way

    public static String LSERVER_HOST = "amushk.cs.rice.edu";
    public static int LSERVER_PORT = 10001; // This is the port on amushk.cs.rice.edu

    // The bootstrap machine:port
    public static String BOOTSTRAP_HOST = "ricepl-2.cs.rice.edu";
    public static boolean ISBOOTSTRAP = false;

    public static String BINDADDRESS = "DEFAULT";
    public static int WAITTIME = 300; //  in sec . This is the default option 

    public static final boolean useArtificialChurn = false;
    // This flag helps in debugging in distributed environments, by actually enabling dumps what what is read/writen to the sockets



    // This is set in the command line options
    // If the -run option is set it will actually run with that runId, 
    //   else it will try to read the runId from the RUN file, if such a file does not exist
    // it will run with a default value of 0
    public static int RUNID = 0;
    public static int NUMVIRTUALNODES = 1;
    // Port at which this aplication is running
    // This is the port where this application will be listening, it starts trying from PORT
    public int bindport;

    public static int BINDINDEX = -1; 
    public static int JVMINDEX = 0; 

    public UDPLibraServer udplibraServer = null;
    // These will be used for the udp client portion communicating with the ScribeServer/ESMServer
    public DatagramSocket udpSocket = null;

    // This will receive the anycast ack information 
    public ESMServer esmServer;



    // the collection of nodes which have been created
    protected PastryNode[] nodes;
    protected ESMClient[] apps;
    protected Environment env;

  //the object is just to implement the destruction policy.
  //PastryNode localNode;
  //LeafSet leafSet;

    public static String centralizedNodeName = "10.0.0.1"; // for Modelnet
    public static int centralizedNodePort = 15111; // default port shift of 4000
    
    public static int numModelnet = 0;
    public static String[] modelnetIp = new String[5000]; // This is the maximum size of the modelnet setup
    public static InetAddress[] modelnetInetAddr = new InetAddress[5000]; // This is the maximum size of the modelnet setup

    public static InetAddress localAddress; // This is the localAddress using the given bindAddress

    // This is the start of the global state of the entire experiment over all nodes , this value will be used to change an experiment parameter with system time
    public static long BOOTTIME = 0; 
    // In seconds, after every such interval the changing paramter will be increased linearly
    public static int CONSTANTINTERVAL = 30; // secs


    public static int SESSIONTIME = -1; // in seconds, -1 implies infinite session time
    public static int MAXSESSIONTIME = 5000; // in seconds, applicable only if VARYINGANYCASTRATE is set to true


    public static boolean VARYINGANYCASTRATE = false;

    public static int MAXANYCASTRECV = -1; // -1 implies infinite. imposing a bound means that a node accepts max that #anycasts per second

    /* LOADMONITORING ALGO 

    1. Begin of new Timeslot : local Metadata on all groups set to denote underload
    2. Within the timeslot as soon as the #anycasts received reaches LibraTest.MAXANYCASTRECV , the local metadata on all groups is set to denote overload
    */
    public static int MONITORLOADINTERVAL = 1; // in seconds
    


  public LibraTest(int num, int bindport) {
      System.out.println("LibraTest starting");
      this.bindport = bindport;
      nodes = new PastryNode[num];
      apps = new ESMClient[num];

      env = new Environment();
      env.getParameters().setBoolean("pastry_factory_selectorPerNode", true);
      
      System.out.println("BINDADDRESS: " + localAddress);
      env.getParameters().setInetAddress("socket_bindAddress",localAddress);

      env.getParameters().setInt("loglevel", logLevel);

      //env.getParameters().setInt("loglevel", Logger.FINER);
      //env.getParameters().setInt("loglevel", Logger.ALL);
      //env.getParameters().setInt("loglevel", PRINTLIBRA_LEVEL); // 850 - Libra - Scribe treestats
      //env.getParameters().setInt("loglevel", PRINTTREES_LEVEL); // 825 - Scribe treestats
      //env.getParameters().setInt("loglevel", PRINTOVERHEAD_LEVEL);


      // We will read in the Modelnet-Configfile
      FileReader fr = null;
      boolean fileEnded = false;
      
      try{
	  fr = new FileReader(LibraTest.MODELNETCONFIGFILE);
	  BufferedReader  in = new BufferedReader( fr );
	  
	  while(!fileEnded) {
	      String line = null;
	      line = in.readLine();
	      if(line.equals("Done")) {
		  fileEnded = true;
	      } else {
		// Extract the node name from there
		  String[] args = line.split(" ");
		  int vnum = Integer.parseInt(args[3]);
		  String modelnetIpString = args[5];
		  modelnetIp[vnum] = modelnetIpString;
		  modelnetInetAddr[vnum] = InetAddress.getByName(modelnetIpString);
		  numModelnet ++;
	      }
	  }
      }catch( Exception err ){
	  System.out.println("ERROR : LibraTest() While reading the " + LibraTest.MODELNETCONFIGFILE + " " + err);
      }
      

      

      try {

	  boolean riceNode = false;
	  //InetAddress localAddress;
	  //localAddress = InetAddress.getLocalHost();
	  
	  System.out.println("localaddress.getHostname= " + localAddress.getHostName());
	  System.out.println("BOOTSTRAP_HOST= " + BOOTSTRAP_HOST);
	  System.out.println("ISBOOTSTRAP= " + ISBOOTSTRAP);
	  
	  // build the bootaddress from the command line args
	  InetAddress bootaddr;
	  
	  bootaddr = InetAddress.getByName(BOOTSTRAP_HOST); 
	  // Every non ricenode will wait a random period between [0,5 minutes]
	  // We do this to reduce the load on the bootstrap node and also reduce 
	  // the node join rate
	  
	  if(!ISBOOTSTRAP) {
	      int waitTime = env.getRandomSource().nextInt(WAITTIME); // Currently made 1 minute
	      System.out.println("Waiting for "+waitTime+" sec before continuing..."+env.getTimeSource().currentTimeMillis());
	      Thread.sleep(waitTime * 1000);
	      System.out.println("Starting connection process "+env.getTimeSource().currentTimeMillis());
	  } else {
	      // We will set the boottime
	      BOOTTIME  = System.currentTimeMillis();
	      
	  }
      
      
	  int bootport = BOOTSTRAP_PORT;
	  
	  
	  // Generate the NodeIds Randomly
	  NodeIdFactory nidFactory = new RandomNodeIdFactory(env);
	  
	  // construct the PastryNodeFactory, this is how we use rice.pastry.socket
	  PastryNodeFactory factory = new SocketPastryNodeFactory(nidFactory, bindport,env);
	  
	  
	  
	  InetSocketAddress bootaddress;
	  NodeHandle bootHandle;



	  // We will create an UDPSocket to listen to messages from other modules
	  // This socket is used to communicate to the UDP Scribe Server/ UDP ESM server and will also be used
	  // to communicate with other modules like GNP etc
	  udpSocket = null;
	  try {
	      udpSocket = new DatagramSocket(LibraTest.UDPSOCKETPORT, LibraTest.localAddress);
	  }catch(Exception e) {
	      System.out.println("ERROR: Could not open UDPSOCKET for (" + LibraTest.UDPSOCKETPORT + "," + LibraTest.localAddress + ")");
	      System.exit(1);
	  }
	  System.out.println("Opened pastry UDPSOCKET socket at (" + LibraTest.UDPSOCKETPORT + "," + LibraTest.localAddress + ")");
	  udplibraServer = new UDPLibraServer(num);

	  // These models the ESM server to which the anycast results are reported. This esmserver will be used as a dummy esm server for the first half of the groups
	  esmServer = new ESMServer();
	  
	  

	  for (int i=0; i<num; i++) {
	      PastryNode node;
	      final LeafSet ls;
	      if(i == 0) {
		  bootaddress = new InetSocketAddress(bootaddr,bootport);
		  // This will return null if we there is no node at that location
		  bootHandle = ((SocketPastryNodeFactory)factory).getNodeHandle(bootaddress);
		  
		  if (bootHandle == null) {
		      if (ISBOOTSTRAP) {
			  // go ahead and start a new ring
		      } else {
			  // don't boot your own ring unless you are ricepl-1
			  System.out.println("Couldn't find remote bootstrap... exiting.");        
			  System.exit(23); 
		      }
		  }
		  
	      } else {
		  // We make it boot of the local node
		  bootaddress = new InetSocketAddress(localAddress.getHostName(), bindport);
		  bootHandle = ((SocketPastryNodeFactory)factory).getNodeHandle(bootaddress);
	      } 
	      
	      node = factory.newNode(bootHandle);
	      System.out.println("SysTime: " + System.currentTimeMillis() + " STARTUP " + " " + node);    
	      nodes[i] = node;
	      // the node may require sending several messages to fully boot into the ring
	      ls = node.getLeafSet();	
	      
	      Observer preObserver = 
		  new Observer() {
		      public void update(Observable arg0, Object arg1) {
			  //System.out.println("SysTime: " + System.currentTimeMillis() + " LEAFSET4:" + ls);
		      }
	      };
	      ls.addObserver(preObserver);
	      
	      long lastTimePrinted = 0;  
	      while(!node.isReady()) {
		  // delay so we don't busy-wait
		  long now = System.currentTimeMillis();
		  if (now-lastTimePrinted > 3*60*1000) {
		      //System.out.println("SysTime: " + System.currentTimeMillis() + " LEAFSET5:" + ls);
		      lastTimePrinted = now;
		  }
		  Thread.sleep(100);
	      }
	      ls.deleteObserver(preObserver);
	      
	      System.out.println("SysTime: " + System.currentTimeMillis() + " SETREADY new node[" + i + "]: " +nodes[i] + " ls: " + ls);
	      System.out.println("");
	      
	  }


	  for (int i=0; i<num; i++) {
	      PastryNode node = nodes[i];
	      if (useLibra) {
		  // this is to do scribe stuff
		  MyLibraClient app = new MyLibraClient(BINDINDEX, JVMINDEX, i,node, udpSocket, udplibraServer, factory);
		  apps[i] = app;
	      } else if(useSplitstream) {
		  // this is to do scribe stuff
		  MySplitstreamClient app = new MySplitstreamClient(BINDINDEX, JVMINDEX,i,node, udpSocket, udplibraServer);
		  apps[i] = app;
	      } else if(usePastrytest) {
		  MyPastrytestClient app = new MyPastrytestClient(BINDINDEX, JVMINDEX, i, node, udpSocket, udplibraServer, factory);
		  apps[i] = app;

	      }
	  }
	  
      } catch(Exception e) {
	  System.out.println("ERROR: Exception in LibraTest() " + e );
	  e.printStackTrace();
	  System.exit(1);
      }
      
  }
    
  
  public static void main(String[] args) throws Exception {
      // We do the parsing of the options
      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-help")) {
	      System.out.println("Usage: LibraTest [-usePlanetlab p] [-nodes n] [-portShift s] [-run r]  [-test type] [-aTraversal a] [-esmPolicy p] [-bootstrap host[:port]] [-help]");
	      System.exit(1);
	  }
      }


      // We will first try to read it off the RUN file
      FileReader fr = null;
      try{
	  fr = new FileReader("RUN");
	  BufferedReader  in = new BufferedReader( fr );
	  String line = null;
	  line = in.readLine();
	  RUNID = Integer.parseInt(line);
	  //System.out.println("Setting RUNID= " + RUNID + " from file:RUN");
      }catch( Exception e ){
	  //System.out.println( e );
      }



      /*
	 The following polciies are defined in ESMScribePolicy (governing child selection at intermediate Scribe tree node) :
	 public static final int ESMLIBRA_RANDOM = 11;
	 public static final int ESMLIBRA_LOCALITY = 12;
	 public static final int ESMLIBRA_BANDWIDTH = 13;
	 public static final int ESMLIBRA_DEPTH = 14;
	 public static final int ESMLIBRA_TIME = 15;  // biasing towards members with higher remaining times 
	 public static final int ESMLIBRA_COMBINED = 16;
      */
      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-esmPolicy") && i + 1 < args.length) {
	      int n = Integer.parseInt(args[i + 1]);
	      if(n>0) {
		  ESMLIBRA_POLICY = n;
	      } 
	      break;
	  }
      }
      
      
      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-tcpOwnSerialized") && i + 1 < args.length) {
	      int n = Integer.parseInt(args[i + 1]);
	      if(n==0) {
		  OWNSERIALIZER = false;
	      } else {
		  OWNSERIALIZER = true;
	      }
	      break;
	  }
      }



      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-updatePiggybacking") && i + 1 < args.length) {
	      int n = Integer.parseInt(args[i + 1]);
	      if(n==0) {
		  UPDATEPIGGYBACKING = false;
	      } else {
		  UPDATEPIGGYBACKING = true;
	      }
	      break;
	  }
      }


      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-modelnet") && i + 1 < args.length) {
	      int n = Integer.parseInt(args[i + 1]);
	      if(n==0) {
		  MODELNET = false;
	      } else {
		  MODELNET = true;
	      }
	      break;
	  }
      }



      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-udpOwnSerialized") && i + 1 < args.length) {
	      int n = Integer.parseInt(args[i + 1]);
	      if(n==0) {
		  OWNUDPSERIALIZER = false;
	      } else {
		  OWNUDPSERIALIZER = true;
	      }
	      break;
	  }
      }


      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-fastConvergence") && i + 1 < args.length) {
	      int n = Integer.parseInt(args[i + 1]);
	      if(n==0) {
		  ENABLEFASTCONVERGENCE = false;
	      } else {
		  ENABLEFASTCONVERGENCE = true;
	      }
	      break;
	  }
      }


      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-maxSatisfiedChildren") && i + 1 < args.length) {
	      int n = Integer.parseInt(args[i + 1]);
	      if(n>=0) {
		  MAXSATISFIEDCHILDREN = n;
	      } 
	      break;
	  }
      }


      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-boottime") && i + 1 < args.length) {
	      long n = Long.parseLong(args[i + 1]);
	      if(n>=0) {
		  BOOTTIME = n;
	      } 
	      break;
	  }
      }


      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-sessiontime") && i + 1 < args.length) {
	      int n = Integer.parseInt(args[i + 1]);
	      if(n>=0) {
		  SESSIONTIME = n;
	      } 
	      break;
	  }
      }

      /*
      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-maxAnycastToVisit") && i + 1 < args.length) {
	      int n = Integer.parseInt(args[i + 1]);
	      if(n>=0) {
		  MAXANYCASTTOVISIT = n;
	      } 
	      break;
	  }
      }
      */
      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-packetsInInterval") && i + 1 < args.length) {
	      int n = Integer.parseInt(args[i + 1]);
	      if(n>=0) {
		  PACKETSININTERVAL = n;
	      } 
	      break;
	  }
      }

      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-aTraversal") && i + 1 < args.length) {
	      int n = Integer.parseInt(args[i + 1]);
	      if(n>=0) {
		  ANYCASTTRAVERSALTHRESHOLD = n;
	      } 
	      break;
	  }
      }


      /* We comment this out for now, so that the compiler can get rid of the conditional logging
      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-logLevel") && i + 1 < args.length) {
	      int n = Integer.parseInt(args[i + 1]);
	      if(n>=0) {
		  logLevel = n;
	      } 
	      break;
	  }
      }
      */


      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-anycastPeriod") && i + 1 < args.length) {
	      double n = Double.parseDouble(args[i + 1]);
	      if(n>=0) {
		  ANYCASTPERIOD = n;
	      } 
	      break;
	  }
      }

      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-downwardUpdatePeriod") && i + 1 < args.length) {
	      int n = Integer.parseInt(args[i + 1]);
	      if(n>=0) {
		  DOWNWARDUPDATETHREADPERIOD = n;
	      } 
	      break;
	  }
      }

      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-pastryQueryPeriod") && i + 1 < args.length) {
	      double n = Double.parseDouble(args[i + 1]);
	      if(n>=0) {
		  PASTRYQUERYPERIOD = n;
	      } 
	      break;
	  }
      }



      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-udpQueryPeriod") && i + 1 < args.length) {
	      double n = Double.parseDouble(args[i + 1]);
	      if(n>=0) {
		  UDPQUERYPERIOD = n;
	      } 
	      break;
	  }
      }


      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-numTrees") && i + 1 < args.length) {
	      int n = Integer.parseInt(args[i + 1]);
	      if(n>0) {
		  NUMTREES = n;
	      } 
	      break;
	  }
      }

      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-jvmIndex") && i + 1 < args.length) {
	      int n = Integer.parseInt(args[i + 1]);
	      if(n>=0) {
		  JVMINDEX = n;
	      } 
	      break;
	  }
      }


      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-bindIndex") && i + 1 < args.length) {
	      int n = Integer.parseInt(args[i + 1]);
	      if(n>=0) {
		  BINDINDEX = n;
	      } 
	      break;
	  }
      }

      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-numDuplicates") && i + 1 < args.length) {
	      int n = Integer.parseInt(args[i + 1]);
	      if(n>0) {
		  NUMDUPLICATES = n;
	      } 
	      break;
	  }
      }

      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-portShift") && i + 1 < args.length) {
	      int n = Integer.parseInt(args[i + 1]);
	      if(n>=0) {
		  portShift = n;
	      } 
	      break;
	  }
      }

      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-useUDP") && i + 1 < args.length) {
	      int n = Integer.parseInt(args[i + 1]);
	      if(n==0) {
		  USEUDPFORREALTIME = false;
	      } else {
		  USEUDPFORREALTIME = true;
	      }
	      break;
	  }
      }

      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-isBootstrap") && i + 1 < args.length) {
	      int n = Integer.parseInt(args[i + 1]);
	      if(n==0) {
		  ISBOOTSTRAP = false;
	      } else {
		  ISBOOTSTRAP = true;
	      }
	      break;
	  }
      }
      
      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-debug") && i + 1 < args.length) {
	      int n = Integer.parseInt(args[i + 1]);
	      if(n==0) {
		  DEBUG = false;
	      } else {
		  DEBUG = true;
	      } 
	      break;
	  }
      }


      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-varyinganycastrate") && i + 1 < args.length) {
	      int n = Integer.parseInt(args[i + 1]);
	      if(n==0) {
		  VARYINGANYCASTRATE = false;
	      } else {
		  VARYINGANYCASTRATE = true;
	      } 
	      break;
	  }
      }


      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-maxanycastrecv") && i + 1 < args.length) {
	      int n = Integer.parseInt(args[i + 1]);
	      if(n>0) {
		  MAXANYCASTRECV = n;
	      } 
	      break;
	  }
      }


      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-test") && i + 1 < args.length) {
	      int n = Integer.parseInt(args[i + 1]);
	      if (n >0) {
		  testType = n;
	      } 
	      break;
	  }
      }


      
      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-nodes") && i + 1 < args.length) {
	      int n = Integer.parseInt(args[i + 1]);
	      if (n >= 0) {
		  NUMVIRTUALNODES = n;
	      }
	      break;
	  }
      }


      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-run") && i + 1 < args.length) {
	      int r = Integer.parseInt(args[i + 1]);
	      if (r >= 0) {
		  RUNID = r;
		  if(RUNID != JARVERSION) {
		      System.out.println("ERROR: The JARVERSION and runID do not match, exiting");
		      System.exit(1);
		      
		  }
	      }
	      break;
	  }
      }


      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-bootstrap") && i + 1 < args.length) {
	      String str = args[i + 1];
	      int index = str.indexOf(':');
	      if (index == -1) {
		  BOOTSTRAP_HOST = str;
	      }
	      break;
	  }
      }


      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-nametoipcodedfile") && i + 1 < args.length) {
	      String str = args[i + 1];
	      int index = str.indexOf(':');
	      if (index == -1) {
		  NAMETOIPCODEDFILE = str;
	      }
	      break;
	  }
      }



      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-modelnetconfigfile") && i + 1 < args.length) {
	      String str = args[i + 1];
	      int index = str.indexOf(':');
	      if (index == -1) {
		  MODELNETCONFIGFILE = str;
	      }
	      break;
	  }
      }


      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-centralizednodefile") && i + 1 < args.length) {
	      String str = args[i + 1];
	      int index = str.indexOf(':');
	      if (index == -1) {
		  CENTRALIZEDNODEFILE = str;
	      }
	      break;
	  }
      }


      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-bindAddress") && i + 1 < args.length) {
	      String str = args[i + 1];
	      int index = str.indexOf(':');
	      if (index == -1) {
		  BINDADDRESS = str;
	      }
	      break;
	  }
      }



      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-lserver") && i + 1 < args.length) {
	      String str = args[i + 1];
	      int index = str.indexOf(':');
	      if (index == -1) {
		  LSERVER_HOST = str;
	      }
	      break;
	  }
      }
      
      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-centralized") && i + 1 < args.length) {
	      String str = args[i + 1];
	      int index = str.indexOf(':');
	      if (index == -1) {
		  centralizedNodeName = str;
	      }
	      break;
	  }
      }


      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-bootstrapport") && i + 1 < args.length) {
	      int n = Integer.parseInt(args[i + 1]);
	      if(n>=0) {
		  BOOTSTRAP_PORT = n;
	      } 
	      break;
	  }
      }


      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-centralizedport") && i + 1 < args.length) {
	      int n = Integer.parseInt(args[i + 1]);
	      if(n>=0) {
		  centralizedNodePort = n;
	      } 
	      break;
	  }
      }

      
      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-lserverport") && i + 1 < args.length) {
	      int n = Integer.parseInt(args[i + 1]);
	      if(n>=0) {
		  LSERVER_PORT = n;
	      } 
	      break;
	  }
      }

      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-waittime") && i + 1 < args.length) {
	      int n = Integer.parseInt(args[i + 1]);
	      if(n>0) {
		  WAITTIME = n ; // waittime is in sec
	      } 
	      break;
	  }
      }





      if(ANYCASTTRAVERSALTHRESHOLD >= MAXSATISFIEDCHILDREN) {
	  System.out.println("ERROR: ANYCASTTRAVERSALTHRESHOLD is greater than MAXSATISFIEDCHILDREN");
	  System.exit(1);
      }


      //System.out.println("UsePlanetlab: " + usePlanetlab);
      //System.out.println("TestType: " + testType);
      


      
      if(testType == SPLITSTREAM) {
	  useLibra = false;
	  useSplitstream = true;
	  usePastrytest = false;
	  
      } else if(testType == PASTRYTEST) {
	  useLibra = false;
	  useSplitstream = false;
	  usePastrytest = true;
	  
      } else if(testType == LIBRAESM) {
	  useLibra = true;
	  useSplitstream = false;
	  usePastrytest = false;
	 
      }

      // These ports will be incremented when we run multiple JVMs in the same machine. On the same machine therefore we can run with portShifts spaced by 4
      DUMMYESMSERVERPORT = 11108 + portShift;	  
      UDPSOCKETPORT = 11109 + portShift;
      SCRIBESERVERPORT = 11110 + portShift;  
      PORT = 11111 + portShift;
      //LSERVER_PORT = 10001/10002 on amushk.cs      

      // These ports correspond to ports that are fixed over different JAVA VMS on the same physical node
      //centralizedNodePort = 11111 + portShift;
      //BOOTSTRAP_PORT = 11111 + portShift;


      try {
	  if(!BINDADDRESS.equals("DEFAULT")) {
	      localAddress = InetAddress.getByName(BINDADDRESS);
	  } else {
	      localAddress = InetAddress.getLocalHost();
	  }
	      
      } catch(java.net.UnknownHostException e) {
	  System.out.println("ERROR: Error binding to specified BINDADDRESS: " + BINDADDRESS);
      }



      //InetAddress localAddress;
      //localAddress = InetAddress.getLocalHost();

      String logFileName = "Log_" + localAddress.getHostName() + "_" + BINDINDEX + "_" + JVMINDEX + "_" + RUNID + "_" + System.currentTimeMillis(); 
      PrintStream ps = new PrintStream(new FileOutputStream(logFileName, false));
      System.setErr(ps);
      System.setOut(ps);


      
      System.out.println("SysTime: " + System.currentTimeMillis()+ " BOOTUP: RUNID: " + RUNID);
    
    
      // the port to use locally    
      int bindport = PORT;
      

      // todo, test port bindings before proceeding
      boolean success = false;
      while(!success) {
	  try {
	      InetSocketAddress bindAddress = new InetSocketAddress(InetAddress.getLocalHost(),bindport);
	      
	      // udp test
	      DatagramChannel channel = DatagramChannel.open();
	      channel.configureBlocking(false);
	      channel.socket().bind(bindAddress);
	      channel.close();
	      
	      ServerSocketChannel channel1 = ServerSocketChannel.open();
	      channel1.configureBlocking(false);
	      channel1.socket().bind(bindAddress);
	      channel1.close();
	      
	      success = true;
	  } catch (Exception e) {
	      if(LibraTest.MODELNET) {
		  System.out.println("Couldn't bind on port "+bindport+" exiting");
	      } else {
		  System.out.println("Couldn't bind on port "+bindport+" trying "+(bindport+1));
		  bindport++; 
	      }
	      
	  }
      }
      
      new LibraTest(NUMVIRTUALNODES, bindport);
    
   
    
    /*
    Runtime.getRuntime().addShutdownHook(new Thread() {
      public void run() { System.out.println("SysTime: " + System.currentTimeMillis() + " SHUTDOWN " + " " + node); }
    });
    */
  }

}
