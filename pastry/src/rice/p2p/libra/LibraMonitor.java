package rice.p2p.libra;

import java.util.*;
import java.io.*;
import java.net.*;
import rice.p2p.util.MathUtils;


// This monitors the planetlab nodes to see which nodes are currently running Libra
public class LibraMonitor {

    public static int SCRIBESERVERPORT = 11110; // This will be updated using the portShift value that is read in at command line
    public static int RUNID = 0;
    public static int portShift = 0;
    public static final byte ISALIVE = 12; // The centralized monitor can send a ping to Scribe server with this opcode to check that it is alive
    public static final byte KILLIFOPCODE  = 16; // This is to kill the remote node using a udp packet if it is operating at an older jar version


    public static long INTERVAL = 60000;  // 1 minutes, pings a node every 1 minutes
    public static long DECLAREDDEADINTERVAL = 600000; // 10 minutes

    public Hashtable nodes = new Hashtable();

    //public static String outFile = "MONITORSTATUS";
    public static String inFile = "NONE";
    

     // These will be used for the udp client portion communicating with the UDPScribeServer
    public DatagramSocket udpSocket = null;
    public static int UDPSOCKETPORT = 10000; // will be altered by the portShift




    // This will be echoed back to the client on success
    public static final byte SUCCESSCODE = 100;
    public static final byte FAILURECODE = 101;


    public class NodeState{
	public long lastSent; // This is the systiem time
	public long lastAck;  // this is the system time
	public String nodeName;
	public SocketAddress sentToAddress;



	public NodeState(String nodeName) {
	    this.nodeName = new String(nodeName);
	    this.sentToAddress = new InetSocketAddress(nodeName,SCRIBESERVERPORT);
	    this.lastSent = 0;
	    this.lastAck = 0;

	}


	//  This kills the remote node if it is operating on the older jar version than RUNID
	public void killif() {
	    try {
		System.out.print("KILLIF: " + nodeName + " ");
		byte[] msg = new byte[2+4]; // VINDEX + OPCODE + int (RUNID)
		msg[0] = (byte)0;
		msg[1] = KILLIFOPCODE;
		byte[] seqNumArray = new byte[4];
		MathUtils.intToByteArray(RUNID, seqNumArray,0);
		for(int i=0;i<4; i++) {
		    msg[2+i] = seqNumArray[i];
		}
		DatagramPacket sendPacket = new DatagramPacket(msg,msg.length, sentToAddress);
		udpSocket.send(sendPacket);
		// We will receive the ack
		byte[] reply= new byte[3];    
		DatagramPacket from_server = new DatagramPacket(reply,reply.length);
		
		udpSocket.setSoTimeout(500);
		udpSocket.receive(from_server);

		//System.out.println("Read " + from_server.getLength() + " bytes "); 
		byte[] real_reply = new byte[from_server.getLength()];
		for(int i=0;i<from_server.getLength();i++) real_reply[i]=reply[i];
		if(real_reply[1] == SUCCESSCODE) {
		    System.out.print(" STATUS: Older jar (Hence killing)");
		} else if(real_reply[1] == FAILURECODE) {
		    System.out.print(" STATUS: Up-to-date jar");
		} else {
		    System.out.print(" ERROR: Unknown protocol opcode");
		}
	    }
	    catch (Exception e) {
		//System.out.println(e); 
	    } 
	    System.out.println("");
		
	}
	

	
	public void ping() {
	    try {
		System.out.println("Pinging " + nodeName);
		long currTime = System.currentTimeMillis();
		if((currTime - lastSent) < INTERVAL) {
		    return;
		} 
		lastSent = currTime;
		
		byte[] msg = new byte[1]; // OPCODE 
		msg[0] = ISALIVE;
		DatagramPacket sendPacket = new DatagramPacket(msg,msg.length, sentToAddress);
		udpSocket.send(sendPacket);
		// We will receive the ack
		byte[] reply= new byte[2];    
		DatagramPacket from_server = new DatagramPacket(reply,reply.length);
		
		udpSocket.setSoTimeout(2000);
		
		udpSocket.receive(from_server);
		byte[] real_reply = new byte[from_server.getLength()];
		for(int i=0;i<from_server.getLength();i++) real_reply[i]=reply[i];
		if(real_reply[0] == SUCCESSCODE) {
		    lastAck = System.currentTimeMillis();
		} else {
			//System.out.println("reqUnsubscribe: GOT FAILURE ACK from server(UDPSERVER)");
		}
	    }
	    catch (Exception e) { System.out.println(e); } 
	    
	}
	    


	public boolean isAlive() {
	    long currTime = System.currentTimeMillis();
	    if((currTime - lastAck) > DECLAREDDEADINTERVAL) {
		return false;
	    } else {
		return true;
	    }
	}

	// This is the difference between the last time it got the ack and the current time
	public long diffTime() {
	    long diffTime = System.currentTimeMillis() - lastAck;
	    return diffTime;

	}

    }

    


    public LibraMonitor() {

	udpSocket = null;
	try {
	    udpSocket = new DatagramSocket(UDPSOCKETPORT);
	}catch(Exception e) {
	    System.out.println("ERROR: Could not udp socket on UDPSOCKETPORT ");
	    System.exit(1);
	}

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
		    if(!nodes.containsKey(nodeName)) {
			nodes.put(nodeName, new NodeState(nodeName));
		    }
		}
	    }
	}catch( Exception e ){
	    System.out.println("ERROR : While reading the LibraMonitor nodefile");
	}
    
	
	killifNodes();
	//pingNodes();
	//displayStatus();
	
    }

    public void killifNodes() {
	Iterator it = (nodes.keySet()).iterator();
	while(it.hasNext()) {
	    String nodeName = (String) it.next();
	    NodeState state = (NodeState)nodes.get(nodeName);
	    state.killif();
	}

    }


    public void pingNodes() {
	Iterator it = (nodes.keySet()).iterator();
	while(it.hasNext()) {
	    String nodeName = (String) it.next();
	    NodeState state = (NodeState)nodes.get(nodeName);
	    state.ping();
	}

    }
    


    /*
    // We show the nodes that responded to Ack in the last 5 minutes
    public void displayStatus() {
	// We write these values to a file
	BufferedWriter fout = null;
	try {
            fout = new BufferedWriter(new FileWriter(outFile));
        }
        catch(IOException e) {
            System.out.println("ERROR: In opening input/output files");
        }



	try {

 	    Iterator it = (nodes.keySet()).iterator();
	    while(it.hasNext()) {
		String nodeName = (String) it.next();
		NodeState state = (NodeState)nodes.get(nodeName);
		String toDump = nodeName + " " + state.isAlive() + " " + state.diffTime();
	 	fout.write(toDump, 0, toDump.length());
		fout.newLine();
		fout.flush();
	    }

	    fout.close();
	}catch(IOException ioe){
	    System.out.println("ERROR: While writing Statistics to file");
	}


	
    }
    */




    public static void main(String[] args) throws Exception {

	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-help")) {
		System.out.println("Usage: LibraMonitor [-nodefile filename] [-portShift s] [-run r] [-help]");
		System.exit(1);
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
	  if (args[i].equals("-run") && i + 1 < args.length) {
	      int n = Integer.parseInt(args[i + 1]);
	      if(n>=0) {
		  RUNID = n;
	      } 
	      break;
	  }
      }

      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-nodefile") && i + 1 < args.length) {
	      inFile = args[i + 1];
	      break;
	  }
      }
      


      SCRIBESERVERPORT = 11110 + portShift;  	
      UDPSOCKETPORT = 10000 + portShift;
      new LibraMonitor();


    }


}
