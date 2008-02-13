/*
 * Created on Apr 6, 2005
 */
package rice.p2p.saar;

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
 * @author Animesh Nandi
 */
public class SaarTest  {
    public static final int JARVERSION = 140;
    public static boolean MODELNET = true;
    public static final int PRINTLIBRA_LEVEL = 850; // This is all logging in Libra
    public static final int PRINTLIBRA_ESSENTIAL = 877; // Use 878 instead of 877 until we debug the sM-choking problem completely. This is the essential logging in Libra
    public static final int PRINTESMSERVER_ESSENTIAL = 880; // This is the essential logging in Libra

    public static int testType = SaarClient.SINGLETREE; 
    

    //public static final int logLevel = Logger.ALL;
    //public static final int logLevel = Logger.FINE;
    //public static final int logLevel = Logger.INFO;

    //public static final int logLevel = PRINTLIBRA_LEVEL;  
    //public static final int logLevel = PRINTLIBRA_ESSENTIAL;  
    public static final int logLevel = PRINTESMSERVER_ESSENTIAL;          



    public static boolean OWNSERIALIZER = false; // This is for TCP
    public static boolean OWNUDPSERIALIZER = false; // This is for UDP
    public static String NAMETOIPCODEDFILE = "NameToIpCoded-Feb2007.nds"; // This contains the mapping of nodenames to codes for efficient serialization. When this localnode is not found in this file, the serialization will explicitly write the nodename 
    public static String MODELNETCONFIGFILE = "/DS/usr/animesh/anycast/modelnet/deployednetwork/example.config.sortvn"; // This contains the ipaddresses of the modelnet nodes
    public static String CENTRALIZEDNODEFILE = "CentralizedMapping.nds"; // This contains the mapping of topicNumber to centralized nodes responsible for the topic 

    public static int portShift = 0; // this tells us which port to run on


    public static int PORT = 11111;
    // The port for Pastry is the highest port so that when using virtual nodes, they can grab consecutive ports starting from this base port
    public static int BOOTSTRAP_PORT = 11111;
    public static int SCRIBESERVERPORT = 11110;  
    public static int UDPSOCKETPORT = 11109;
    public static int DUMMYESMSERVERPORT = 11108;
    public static int DUMMYESMDATAPATHPORT = 1; // We will not be using this in any way

    // The bootstrap machine:port
    public static String BOOTSTRAP_HOST = "ricepl-2.cs.rice.edu";
    public static boolean ISBOOTSTRAP = false;

    public static String BINDADDRESS = "DEFAULT";
    public static int WAITTIME = 300; //  in sec . This is the default option 
    public static int SESSIONTIME = 0; //  in sec . 0- infinite (This is the default option), nonzero - After this many seconds the jvm will exit 



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

    public DatagramSocket udpSocket = null;



    // the collection of nodes which have been created
    protected PastryNode[] nodes;
    protected Environment env;

  //the object is just to implement the destruction policy.
  //PastryNode localNode;
  //LeafSet leafSet;

    public static String centralizedNodeName = "10.0.0.177"; // for Modelnet
    public static int centralizedNodePort = 15111; // default port shift of 4000
    
    
    public static int numModelnet = 0;
    public static String[] modelnetIp = new String[5000]; // This is the maximum size of the modelnet setup
    public static InetAddress[] modelnetInetAddr = new InetAddress[5000]; // This is the maximum size of the modelnet setup

    public static InetAddress localAddress; // This is the localAddress using the given bindAddress

    // This is the start of the global state of the entire experiment over all nodes , this value will be used to change an experiment parameter with system time
    public static long BOOTTIME = 0; 
    // In seconds, after every such interval the changing paramter will be increased linearly
    public static int CONSTANTINTERVAL = 30; // secs


    public static int MAXSESSIONTIME = 5000; // in seconds, applicable only if VARYINGANYCASTRATE is set to true


    public static boolean VARYINGANYCASTRATE = false;

    public static int MAXANYCASTRECV = -1; // -1 implies infinite. imposing a bound means that a node accepts max that #anycasts per second

    /* LOADMONITORING ALGO 

    1. Begin of new Timeslot : local Metadata on all groups set to denote underload
    2. Within the timeslot as soon as the #anycasts received reaches LibraTest.MAXANYCASTRECV , the local metadata on all groups is set to denote overload
    */
    public static int MONITORLOADINTERVAL = 1; // in seconds

    public static int sessionExpiryWarningCount = 0;


  public SaarTest(int num, int bindport) {
      System.out.println("SAARTest starting");
      this.bindport = bindport;
      nodes = new PastryNode[num];

      env = new Environment();
      env.getParameters().setBoolean("pastry_factory_selectorPerNode", true);
      
      System.out.println("BINDADDRESS: " + localAddress);
      env.getParameters().setInetAddress("socket_bindAddress",localAddress);

      env.getParameters().setInt("loglevel", logLevel);

      // We will read in the Modelnet-Configfile
      FileReader fr = null;
      boolean fileEnded = false;
      
      try{
	  fr = new FileReader(MODELNETCONFIGFILE);
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
	  System.out.println("ERROR : SAARTest() While reading the " + MODELNETCONFIGFILE + " " + err);
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
	      int waitTime;
	      if(WAITTIME == 0) {
		  waitTime = 1; // Waits a minimum of 1 second
	      } else {
		  waitTime = 1 + env.getRandomSource().nextInt(WAITTIME); // Currently made 1 minute
	      }
	      System.out.println("Waiting for "+waitTime+" sec before continuing..."+env.getTimeSource().currentTimeMillis());
	      Thread.sleep(waitTime * 1000);
	      System.out.println("Starting connection process "+env.getTimeSource().currentTimeMillis());
	  } else {
	      // We will set the boottime
	  }
	  // If the boottime is not set using -boottime it is set to the starttime of this node
	  if(BOOTTIME == 0) {
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
	      udpSocket = new DatagramSocket(UDPSOCKETPORT, localAddress);
	  }catch(Exception e) {
	      System.out.println("ERROR: Could not open UDPSOCKET for (" + UDPSOCKETPORT + "," + localAddress + ")");
	      System.exit(1);
	  }
	  System.out.println("Opened pastry UDPSOCKET socket at (" + UDPSOCKETPORT + "," + localAddress + ")");
	  
	  
	  new Thread(new Runnable() {
		  public void run() {
		      while(true) {
			  long currtime = System.currentTimeMillis();
			  int awakeTime = (int)((System.currentTimeMillis() - BOOTTIME)/1000);
			  if((SESSIONTIME>0) && (awakeTime > SESSIONTIME)) {
			      System.out.println("WARNING: Systime: " + System.currentTimeMillis() + " SESSIONTIME of " + SESSIONTIME + " seconds expired");
			      sessionExpiryWarningCount ++;
			      //System.exit(1);
			  } else {
			  System.out.println("Systime: " + System.currentTimeMillis() + " AWAKE");
		      }
		      if(sessionExpiryWarningCount > 30) {
			  System.out.println("Systime: " + System.currentTimeMillis() + " sessionExpiryWarning count reached limit, killing abruptly");
			  System.exit(1);
		      }
		      try {
			  Thread.sleep(1000);
		      } catch (Exception e) {}
		      }
		  }
	      },"AWAKE").start();
	  
      

	  

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
	      System.out.println("SysTime: " + System.currentTimeMillis() + " SESSIONTIME: " + SESSIONTIME + " seconds");

	      
	  }


	  //rice.p2p.libra.LibraTest.OWNSERIALIZER = false;

	  rice.p2p.saar.SaarClient.MULTICASTSOURCEBINDINDEX = 107;

	  // We will print out the importtant configurations
	  System.out.println("");
	  System.out.println("SAAR-CONFIGS");
    	  //System.out.println("p2p.libra.LibraTest.OWNSERIALIZER: " + rice.p2p.libra.LibraTest.OWNSERIALIZER);
    	  System.out.println("p2p.saar.SaarTest.testType: " + testType);
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
	  //System.out.println("p2p.saar.blockbased.MultitreeClient.SPLITSTREAMCONFIG: " + rice.p2p.saar.multitree.MultitreeClient.SPLITSTREAMCONFIG);
	  //System.out.println("p2p.saar.blockbased.MultitreeClient.SPLITSTREAMOUTDEGREE: " + rice.p2p.saar.multitree.MultitreeClient.SPLITSTREAMOUTDEGREE);
	  System.out.println("p2p.saar.SaarClient.MULTICASTSOURCEBINDINDEX: " + rice.p2p.saar.SaarClient.MULTICASTSOURCEBINDINDEX);




	  for (int i=0; i<num; i++) {
	      PastryNode node = nodes[i];
	      SaarClient app = new SaarClient(BINDINDEX, JVMINDEX, i,node, udpSocket, null, factory, "saarClient", SESSIONTIME, BOOTTIME,null, 1, false, false, 0, null);


	      System.out.println(" ERROR: We modified Saarclient to pass it the nodedegree/amMulticastsource etc from the driver");
	      System.exit(1);
	  }
	  
      } catch(Exception e) {
	  System.out.println("ERROR: Exception in SaarTest() " + e );
	  e.printStackTrace();
	  System.exit(1);
      }


    

      
  }
    
  
  public static void main(String[] args) throws Exception {
      
      // We do the parsing of the options
      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-help")) {
	      System.out.println("Usage: SaarTest -tcpOwnSerialized -modelnet -udpOwnSerialized -boottime - jvmIndex -bindIndex -isBootstrap -useUDP -nodes -sessiontime");
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
	  if (args[i].equals("-boottime") && i + 1 < args.length) {
	      long n = Long.parseLong(args[i + 1]);
	      if(n>0) {
		  BOOTTIME = n;
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
	  if (args[i].equals("-portShift") && i + 1 < args.length) {
	      int n = Integer.parseInt(args[i + 1]);
	      if(n>=0) {
		  portShift = n;
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


      // The '-test' accomplishes the same thing as '-dataplanetype' but we do this for reverse compatibility with earlier versions of code
      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-test") && i + 1 < args.length) {
	      int n = Integer.parseInt(args[i + 1]);
	      if(n>=0) {
		  SaarClient.DATAPLANETYPE= n;
		  
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
	  if (args[i].equals("-bootstrapport") && i + 1 < args.length) {
	      int n = Integer.parseInt(args[i + 1]);
	      if(n>=0) {
		  BOOTSTRAP_PORT = n;
	      } 
	      break;
	  }
      }


      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-waittime") && i + 1 < args.length) {
	      int n = Integer.parseInt(args[i + 1]);
	      if(n>=0) {
		  WAITTIME = n ; // waittime is in sec
	      } 
	      break;
	  }
      }

      

      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-sessiontime") && i + 1 < args.length) {
	      int n = Integer.parseInt(args[i + 1]);
	      if(n>0) {
		  SESSIONTIME = n ; // sessiontime is in sec
	      } 
	      break;
	  }
      }



      // These ports will be incremented when we run multiple JVMs in the same machine. On the same machine therefore we can run with portShifts spaced by 4
      DUMMYESMSERVERPORT = 11108 + portShift;	  
      UDPSOCKETPORT = 11109 + portShift;
      SCRIBESERVERPORT = 11110 + portShift;  
      PORT = 11111 + portShift;




      // These ports will be incremented when we run multiple JVMs in the same machine. On the same machine therefore we can run with portShifts spaced by 4
      PORT = 11111 + portShift;

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
	      System.out.println("Couldn't bind on port "+bindport+" exiting: " + e);
	      System.exit(1);
	  }
      }
      
      new SaarTest(NUMVIRTUALNODES, bindport);
    
  }

}
