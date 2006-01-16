package rice.p2p.libra;


// We implement a ScribeServer that is modelled on the client-server paradigm acepting calls to the Scribe layer. Also this server is a UDP server

import java.net.*;
import java.io.*;
import java.util.*;
import java.lang.*;
import rice.p2p.util.MathUtils;


public class UDPLibraServer extends Thread
{

    // UDPLibraServer.

    private int NUMVIRTUALNODES = 1;
    private ESMClient[] esmClients;

    public static final byte REGISTEROPCODE = 0;
    public static final byte ANYCASTOPCODE = 1;
    public static final byte SUBSCRIBEOPCODE = 2;
    public static final byte UNSUBSCRIBEOPCODE = 3;
    public static final byte UPDATEOPCODE = 4;
    public static final byte ANYCASTACKOPCODE = 5;
    public static final byte PROSPECTIVECHILDOPCODE = 6;
    public static final byte ANYCASTFAILUREOPCODE = 7;
    public static final byte GRPMETADATAREQUESTOPCODE = 8;
    public static final byte GRPMETADATAACKOPCODE = 9;
    
    
    // This is registering the dummy ESM server
    public static final byte DUMMYREGISTEROPCODE = 10;
    public static final byte NOP = 11; //no operation
    public static final byte ISALIVE = 12; // The centralized monitor can send a ping to Scribe server with this opcode to check that it is alive


    public static final byte UDPQUERY  = 13; // These two messages will be used to test if UDP traffic is slowing properly on planetlab
    public static final byte UDPACK  = 14;
    public static final byte PINGALIVEOPCODE  = 15;
    public static final byte KILLIFOPCODE  = 16; // This is to kill the remote node using a udp packet
    



    public static final byte LOADMETRIC = 1;
    public static final byte LOSSMETRIC = 2;
    public static final byte PATHMETRIC = 3;
    public static final byte TIMEMETRIC = 4;
    

    // This will be echoed back to the client on success
    public static final byte SUCCESSCODE = 100;
    public static final byte FAILURECODE = 101;



    public DatagramSocket ds = null;

    public SocketAddress lServerAddress;
    
    public SharedStack sharedStack;



    /**
     * A shared bounded stack class of size MAXSTACKSIZE 
     */
    public class SharedStack {
	private List stack = new ArrayList();
	private int MAXSTACKSIZE = 1000; 
	
	/**
	 * Construct a new shared stack.
	 */
	public SharedStack() {}
	
	/**
	 * Push a new object onto a shared stack.
	 * @param object The object to be pushed onto the stack.
	 */
	public synchronized void produce(Object object) {
	    if(stack.size() < MAXSTACKSIZE) {
		stack.add(object);
	    } else {
		System.out.println("ERROR: UDPLibraServer.Request being dropped at SharedStack");
	    }
	    notify();
	}
	/**
	 * Pop the top object off a stack or wait, if needed, until there is one.
	 * @return The top object on the stack.
	 */
	public synchronized Object consume() {
	    while (stack.isEmpty()) {
		try {
		    wait();
		} catch (InterruptedException e) {
		}
	    }
	    Object object = stack.remove(0);
	    return object;
	}
	/**
	 * Construct a string that represents a shared stack.
	 * @return The string representation of a shared stack.
	 */
	public synchronized String contains() {
	    String s = "The shared stack currently contains the following:\n";
	    for (Iterator i = stack.iterator(); i.hasNext(); ) {
	    s += i.next() + "\n";
	    }
	    return s;
	}
    }
    
    
    
    


    public  class RequestState 
    {
	byte[] msg;
	ESMClient esmClient;
	SocketAddress requestorAddress;

	// Constructor
	public RequestState(byte[] request, ESMClient esmClient, SocketAddress requestorAddress)  
	{
	    this.msg = request;
	    this.esmClient = esmClient;
	    this.requestorAddress = requestorAddress;
	}
    

	private void sendAck(byte vIndex, byte OPCODE) {
	    try {
		// (2) sending an ack to the requestor
		byte[] ack = new byte[3];
		ack[0] = vIndex;
		ack[1] = SUCCESSCODE;
		ack[2] = OPCODE;
		DatagramPacket sendPacket = new DatagramPacket(ack,ack.length, requestorAddress);
		ds.send(sendPacket);
	    } catch(Exception e) {
		System.out.println("sendAck: " + e);
	    }
	}

	private void sendFailure(byte vIndex, byte OPCODE) {
	    try {
		// (2) sending an ack to the requestor
		byte[] failure = new byte[3];
		failure[0] = vIndex;
		failure[1] = FAILURECODE;
		failure[2] = OPCODE;
		DatagramPacket sendPacket = new DatagramPacket(failure,failure.length, requestorAddress);
		ds.send(sendPacket);
	    }  catch(Exception e) {
		System.out.println("sendFailure: " + e);
	    }
	}

	private void sendPingAlive(byte vIndex, byte OPCODE) {
	    try {
		// (2) sending an pingAlive to LServer ( amushk.cs.rice.edu)
		byte[] msg = new byte[2];
		msg[0] = vIndex;
		msg[1] = PINGALIVEOPCODE;
		DatagramPacket sendPacket = new DatagramPacket(msg,msg.length, lServerAddress);
		System.out.println("Sending to " + lServerAddress);
		ds.send(sendPacket);
	    } catch(Exception e) {
		System.out.println("sendPingAlive: " + e);
	    }
	    
	}



	public void processRequest() 
	{
	    byte vIndex = 0;
	    int streamId = -1;
	    int usedSlots = -1;
	    int totalSlots = -1;
	    int lossRate =-1; 
	    int pathLength = -1;
	    //Vector ipPath = new Vector(); // This will contain the path to the root

	    // We read in the OPCODE and STREAMID

	   
	    try {
		if(msg.length < 1) {
		    System.out.println("Warning: msg.length is less than 1, no OPCODE can be read");
		    sendFailure(vIndex,NOP);
		    return;
		}
		
		vIndex = msg[0];

		if(msg[1] == KILLIFOPCODE) {
		    if(msg.length < 6) {
			System.out.println("Warning: KILLIFOPCODE msg.length at ScribeServer is less than required");
			//sendFailure(vIndex, msg[1]); We do not send anything back, so that it does not interfere with the assumed meaning of failure meaning that the jar is up-to-date
			return;
		    }
		    byte[] jarVersionArray = new byte[4];
		    for(int i=0; i<4; i++) {
			jarVersionArray[i]= msg[2+i];
		    }
		    int currJarVersion;
		    currJarVersion = MathUtils.byteArrayToInt(jarVersionArray);


		    // We conditionally kill ourselves if we are running an older jar version
		    if(LibraTest.JARVERSION < currJarVersion) {
			sendAck(vIndex, msg[1]);
			System.out.println("SHUTDOWN: On receiving KILLOPCODE");
			System.exit(1);
		    } else {
			sendFailure(vIndex, msg[1]);
		    }
		} else if(msg[1] == PINGALIVEOPCODE) {
		    // We send a pingalive to LServer(amushk)
		    sendPingAlive(vIndex, msg[1]);

		} else if(msg[1] == ISALIVE) {
		    sendAck(vIndex,msg[1]);
		    
		} else if(msg[1] == UDPQUERY) {
		    if(esmClient != null) {
			esmClient.recvUDPQuery(msg);
		    }
		    // We respond to the querier with the same payload except the header is changed to UDPACK
		    try {
			// We overwrite the msg.OPCODE to UDPACK
			msg[1] = UDPACK;
			DatagramPacket sendPacket = new DatagramPacket(msg,msg.length, requestorAddress);
			ds.send(sendPacket);
		    } catch(Exception e) {
		    System.out.println("sendAck: " + e);
		    }
		    
		} else if(msg[1] == UDPACK) {
		    // We just print the payload information
		    esmClient.recvUDPAck(msg);
		    
		} else if(msg[1] == REGISTEROPCODE) {
		    if(msg.length <16) {
		    System.out.println("Warning: REGISTER msg.length at ScribeServer is less than required");
		    sendFailure(vIndex, msg[1]);
		    return;
		    }
		    // param1(overlayId - 4 bytes),
		    byte[] esmOverlayId = new byte[4];
		    for(int i=0; i<4; i++) {
			esmOverlayId[i]= msg[2+i];
		    }
		    //param2(esmserverport - 4 bytes)
		    byte[] esmServerPortArray = new byte[4];
		    for(int i=0; i<4; i++) {
			esmServerPortArray[i]= msg[6+i];
		    }
		    int esmServerPort;
		    esmServerPort = MathUtils.byteArrayToInt(esmServerPortArray);
		    
		    
		    //param2(esmDatapathPort - 4 bytes)
		    byte[] esmDatapathPortArray = new byte[4];
		    for(int i=0; i<4; i++) {
			esmDatapathPortArray[i]= msg[10+i];
		    }
		    int esmDatapathPort;
		    esmDatapathPort = MathUtils.byteArrayToInt(esmDatapathPortArray);
		    
		    //System.out.println("Invoking Register()");
		    
		    byte esmRunId = msg[14];
		    
		    streamId = (int)msg[15];
		    
		    sendAck(vIndex, msg[1]);
		    for(int i=0; i< LibraTest.NUMTREES;i++) {
			int mystreamId = (i*MyLibraClient.NUMGROUPS) + streamId;
			esmClient.invokeRegister(mystreamId, esmOverlayId, esmServerPort, esmDatapathPort, esmRunId);
		    }
		    
		} else if(msg[1] == DUMMYREGISTEROPCODE) {
		    if(msg.length <15) {
			System.out.println("Warning: DUMMYREGISTER msg.length at ScribeServer is less than required");
			sendFailure(vIndex, msg[1]);
			return;
		    }
		    // param1(overlayId - 4 bytes),
		    byte[] dummyesmOverlayId = new byte[4];
		    for(int i=0; i<4; i++) {
			dummyesmOverlayId[i]= msg[2+i];
		    }
		    //param2(esmserverport - 4 bytes)
		    byte[] dummyesmServerPortArray = new byte[4];
		    for(int i=0; i<4; i++) {
			dummyesmServerPortArray[i]= msg[6+i];
		    }
		    int dummyesmServerPort;
		    dummyesmServerPort = MathUtils.byteArrayToInt(dummyesmServerPortArray);
		    
		    //param3(esmDatapathPort - 4 bytes)
		    byte[] dummyesmDatapathPortArray = new byte[4];
		    for(int i=0; i<4; i++) {
			dummyesmDatapathPortArray[i]= msg[10+i];
		    }
		    int dummyesmDatapathPort;
		    dummyesmDatapathPort = MathUtils.byteArrayToInt(dummyesmDatapathPortArray);
		    
		    byte dummyesmRunId = msg[14];
		    
		    
		    sendAck(vIndex, msg[1]);
		    esmClient.invokeDummyRegister(dummyesmOverlayId,dummyesmServerPort, dummyesmDatapathPort, dummyesmRunId);
		    
		    
		} else if(msg[1] == ANYCASTOPCODE) {
		    if(msg.length <8) {
			System.out.println("Warning: ANYCAST msg.length at ScribeServer is less than required");
			sendFailure(vIndex, msg[1]);
			return;
		    }
		    // param1(streamId - 1 byte)
		    streamId = (int) msg[2];
		    String streamName = "Stream_" + streamId;
		    //System.out.println("Invoking ANYCAST(" + streamName + ")");
		    byte[] seqNumArray = new byte[4];
		    for(int i=0; i<4; i++) {
			seqNumArray[i]= msg[3+i];
		    }
		    int seqNum;
		    seqNum = MathUtils.byteArrayToInt(seqNumArray);
		    
		    int pathLengthRequestor = msg[7];
		    int paramsPathPosRequestor = 0;
		    if(msg.length < (8+4*pathLengthRequestor)) {
			System.out.println("Warning: ANYCAST msg.length at ScribeServer is less than required");
			sendFailure(vIndex, msg[1]);
			return;
		    }			
		    
		    byte paramsPathRequestor[] = new byte[4*pathLengthRequestor];
		    for(int l=0; l<pathLengthRequestor; l++) {
			for(int i=0; i<4;i++) {
			    paramsPathRequestor[paramsPathPosRequestor] = msg[8 +paramsPathPosRequestor];
			    paramsPathPosRequestor ++;
			}
		    }
		    
		    sendAck(vIndex, msg[1]);
		    for(int i=0; i< LibraTest.NUMTREES;i++) {
			int mystreamId = (i*MyLibraClient.NUMGROUPS) + streamId;
			esmClient.invokeAnycast(mystreamId, seqNum, pathLengthRequestor, paramsPathRequestor);
		    }
		    
		    
		}else if(msg[1] == GRPMETADATAREQUESTOPCODE) {
		    if(msg.length <7) {
			System.out.println("Warning: GRPMETADATREQUEST msg.length at ScribeServer is less than required");
			sendFailure(vIndex, msg[1]);
			return;
		    }
		    // param1(streamId - 1 byte)
		    streamId = (int) msg[2];
		    String streamName = "Stream_" + streamId;
		    //System.out.println("Invoking GRPMETADATAREQUEST(" + streamName + ")");
		    byte[] seqNumArray = new byte[4];
		    for(int i=0; i<4; i++) {
			seqNumArray[i]= msg[3+i];
		    }
		    int seqNum;
		    seqNum = MathUtils.byteArrayToInt(seqNumArray);
		    
		    sendAck(vIndex, msg[1]);
		    for(int i=0; i< LibraTest.NUMTREES;i++) {
			int mystreamId = (i*MyLibraClient.NUMGROUPS) + streamId;
			esmClient.invokeGrpMetadataRequest(mystreamId, seqNum);	
		    }
		    
		    
		    

		}else if(msg[1] == SUBSCRIBEOPCODE) {
		    if(msg.length <3) {
			System.out.println("Warning: SUBSCRIBE msg.length at ScribeServer is less than required");
			sendFailure(vIndex, msg[1]);
			return;
		    }
		    // param1(streamId - 1 byte)
		    streamId = (int) msg[2];
		    String streamName = "Stream_" + streamId;
		    //System.out.println("Invoking SUBSCRIBE(" + streamName + ")");
		    sendAck(vIndex, msg[1]);
		    for(int i=0; i< LibraTest.NUMTREES;i++) {
			int mystreamId = (i*MyLibraClient.NUMGROUPS) + streamId;
			esmClient.invokeSubscribe(mystreamId);
		}
		    
		    
		} else if(msg[1] == UNSUBSCRIBEOPCODE) {
		    if(msg.length <3) {
			System.out.println("Warning: UNSUBSCRIBE msg.length at ScribeServer is less than required");
			sendFailure(vIndex, msg[1]);
			return;
		    }
		    // param1(streamId - 1 byte)
		    streamId = (int) msg[2];
		    String streamName = "Stream_" + streamId;
		    //System.out.println("Invoking UNSUBSCRIBE(" + streamName + ")");
		    sendAck(vIndex, msg[1]);
		    for(int i=0; i< LibraTest.NUMTREES;i++) {
			int mystreamId = (i*MyLibraClient.NUMGROUPS) + streamId;
			esmClient.invokeUnsubscribe(mystreamId);
		    }
		    
		} else if(msg[1] == UPDATEOPCODE) {
		    if(msg.length <15) {
			System.out.println("Warning: UPDATE msg.length at ScribeServer is less than required");
			sendFailure(vIndex, msg[1]);
			return;
		    }
		    //System.out.println("UPPSCRIBESERVER: UPDATEOPCODE msg.length= " + msg.length + "msg= " + ESMClient.asString(msg));
		    // param1(streamId - 1 byte)
		    streamId = (int) msg[2];
		    String streamName = "Stream_" + streamId;
		    //System.out.print("ScribeServer Invoking UPDATE(" + streamName + "): ");
		    
		    int paramsLoad[] = new int[2];
		    int paramsLoss[] = new int[1];
		    int time;
		    byte paramsPath[] = null;
		    int paramsPathPos;
		    
		    if(msg[3] != LOADMETRIC) {
			System.out.println("Warning: UPDATE msg does not have LOADMETRIC opcode at correct position");
			sendFailure(vIndex, msg[1]);
			return;
			
		    }
		    usedSlots =  (int)msg[4];
		    totalSlots = (int)msg[5];
		    paramsLoad[0] = usedSlots;
		    paramsLoad[1] = totalSlots;
		    //System.out.print(" LOADMETRIC(" + usedSlots + "," + totalSlots+")");
		    
		    if(msg[6] != LOSSMETRIC) {
			System.out.println("Warning: UPDATE msg does not have LOSSMETRIC opcode at correct position");
			sendFailure(vIndex, msg[1]);
			return;
		    }
		    lossRate = (int)msg[7];
		    paramsLoss[0] = lossRate;
		    //System.out.print(" LOSSMETRIC(" + lossRate +")");
		    
		    if(msg[8] != TIMEMETRIC) {
			System.out.println("Warning: UPDATE msg does not have TIMEMETRIC opcode at correct position");
			sendFailure(vIndex, msg[1]);
			return;
		    }
		    byte[] timeArray = new byte[4];
		    for(int i=0; i<4; i++) {
			timeArray[i]= msg[9+i];
		    }
		    
		    time = MathUtils.byteArrayToInt(timeArray);		
		    
		    if(msg[13] != PATHMETRIC) {
			System.out.println("Warning: UPDATE msg does not have PATHMETRIC opcode at correct position");
			sendFailure(vIndex, msg[1]);
			return;
			
		    }
		    pathLength = msg[14];
		    // After this we have 2 PADS
		    if(pathLength > 0) {
			if(msg.length < (15+4*pathLength)) {
			    System.out.println("Warning: SUBSCRIBE msg.length at ScribeServer is less than required");
			    sendFailure(vIndex, msg[1]);
			    return;
			}			
			
			paramsPath = new byte[4*pathLength];
			paramsPathPos = 0;
			//System.out.print(" PATHMETRIC(" + pathLength +")");
			InetAddress inetAddr;
			for(int l=0; l<pathLength; l++) {
			    byte ipBytes[] = new byte[4];
			    for(int i=0; i<4; i++) {
				ipBytes[i] = msg[15+ 4*l +i];
			    }
			    for(int i=0; i<4;i++) {
				paramsPath[paramsPathPos] = ipBytes[i];
				paramsPathPos ++;
			    }
			    inetAddr = InetAddress.getByAddress(ipBytes);
			}
		    }
		    
		    sendAck(vIndex, msg[1]);
		    for(int i=0; i< LibraTest.NUMTREES;i++) {
			int mystreamId = (i*MyLibraClient.NUMGROUPS) + streamId;
			esmClient.invokeUpdate(mystreamId,paramsLoad, paramsLoss, time, pathLength, paramsPath);
		    }
		    
		}
		
	    }

	    catch(Exception e) {
		System.out.println("ERROR: UDPLibraServer.RequestState.processRequest() " + e);
		e.printStackTrace();
		System.exit(1);
		    
	    }
	}
    }
    


    public  class RequestHandler implements Runnable
    {

	private SharedStack sharedStack;

	// Constructor
	public RequestHandler(SharedStack sharedStack)  
	{
	    this.sharedStack = sharedStack;
	}
    
	// Implement the run() method of the Runnable interface.
	public void run()
	{

	    System.out.println("Consumer Thread (UDPLibraServer.RequestHandler) starting");
	    while(true) {
		RequestState rState = (RequestState)sharedStack.consume();
		rState.processRequest();
	    }

	}

    }


    public UDPLibraServer(int num) {
	this.NUMVIRTUALNODES = num;
	esmClients = new ESMClient[num];
	try {
	    InetAddress inetAddr = InetAddress.getByName(LibraTest.LSERVER_HOST);
	    lServerAddress = new InetSocketAddress(inetAddr, LibraTest.LSERVER_PORT);
	    //lServerAddress = new InetSocketAddress(LibraTest.LSERVER_HOST, LibraTest.LSERVER_PORT);
	    System.out.println("lserverAddress: " + lServerAddress);
	} catch(java.net.UnknownHostException e) {
	    System.out.println("lserveradress exception: " + e);
	}
	sharedStack = new SharedStack();

	RequestHandler requestHandler = new RequestHandler(sharedStack);
	// Create a new thread to process the request.
	Thread consumerThread = new Thread(requestHandler);
	consumerThread.start();
	start(); // it itself is the Producer thread
    }


    public void registerApp(int vIndex, ESMClient app) {
	if(vIndex >= NUMVIRTUALNODES) {
	    System.out.println("ERROR: Trying to register an app with UDPScribeServer with an index greater than NUMVIRTUALNODES");
	    System.exit(1);
	}
	System.out.println("App[" + vIndex + "] registering with UDPScribeServer"); 
	esmClients[vIndex] = app;
    }

    public void run()
    {
	try {
	    ds = new DatagramSocket(LibraTest.SCRIBESERVERPORT, LibraTest.localAddress);
	    System.out.println("UDPLIBRASERVER waiting for client connection ..on UDPSOCKET: (" + LibraTest.SCRIBESERVERPORT + "," + LibraTest.localAddress + ")");
	    
	}catch(Exception e) {
	    System.out.println("ERROR: Could not open updlibraserver UDPSOCKET for (" +  LibraTest.SCRIBESERVERPORT + "," + LibraTest.localAddress + ")");
	    System.exit(1);
	}
                    		
	// Buffers for recieve a request.
	// if we receive more then 1024 bytes from the client
	// they will be discard.
	byte[] request = new byte[1024];
                    		

	System.out.println("Producer Thread (UDPLibraServer.run() thread) starting");
	while(true) {
	    
	    try {
		
		// (1) receive data from client
		DatagramPacket from_client = new DatagramPacket(request,request.length);
		ds.receive(from_client);
		// Need to create another buffer with the size 
		// of bytes that we really recieved from client
		byte[] real_request = new byte[from_client.getLength()];
		for(int i=0;i<from_client.getLength();i++) real_request[i] = request[i];
		SocketAddress  requestorAddress = from_client.getSocketAddress();
		//System.out.println("New connection accepted by UDPScribeServer " + requestorAddress);
                            				



		// We extract the first byte which is the vIndex
		int vIndex = (int) real_request[0];
		ESMClient esmClient = null;
		if(vIndex < NUMVIRTUALNODES) {
		    esmClient = esmClients[vIndex]; 		    
		} else {
		    // This could be for the ISALIVE Opcode
		    esmClient = null;
		}
                            					
		// (2) Processing the request
		// Construct a handler to process the request


		
		RequestState requestState = new RequestState(real_request, esmClient, requestorAddress);

		sharedStack.produce(requestState);

	    }
	    catch(Exception e) {
		System.out.println("ERROR: Producer Thread ending , e: " + e);
		e.printStackTrace();
	    }

	}//end-while
    }
    
    

}
