package rice.p2p.libra;

import java.net.*;
import java.io.*;
import java.util.*;
import java.lang.*;
import rice.p2p.util.MathUtils;

public class ESMServer extends Thread
{    


    public static DatagramSocket ds = null;

    public static SocketAddress udplibraserverSentToAddress;

   

    private static class  RequestHandler implements Runnable
    {
	byte[] msg;
	
	// Constructor
	public RequestHandler(byte[] request) throws Exception 
	{
	    
	    this.msg = request;
	    //System.out.println("msg.length= " + msg.length + "msg= " + asString(msg));
	    
	}
    
	// Implement the run() method of the Runnable interface.
	public void run()
	{
	    processRequest();
	}
	
	private void processRequest()
	{
	    byte vIndex;
	    try {
		int streamId = -1;
		
		// We read in the OPCODE and STREAMID
		
		vIndex = msg[0];

		if(msg[1] == UDPLibraServer.ANYCASTACKOPCODE) {
		    streamId = (int) msg[2];
		    // param1(ip - 4 bytes),
		    byte[] ip = new byte[4];
		    
		    for(int i=0; i<4; i++) {
			ip[i]= msg[3+i];
		    }
		    String ipS = asString(ip);
		    
		    
		    // param2(esmId - 4 bytes),
		    byte[] esmId = new byte[4];
		    for(int i=0; i<4; i++) {
		    esmId[i]= msg[7+i];
		    }
		    String idS = asString(esmId);
		    
		    //param3(esmPort - 4 bytes)
		    int esmPort ;
		    byte[] esmPortArray = new byte[4];
		    for(int i=0; i<4; i++) {
		    esmPortArray[i]= msg[11+i];
		    }
		    String portS = asString(esmPortArray);
		    esmPort = MathUtils.byteArrayToInt(esmPortArray);


		    // param4(seqNum - 4 bytes)
		    int seqNum ;
		    byte[] seqNumArray = new byte[4];
		    for(int i=0; i<4; i++) {
			seqNumArray[i]= msg[15+i];
		    }
		    seqNum = MathUtils.byteArrayToInt(seqNumArray);



		    // param 5 - msgPathLength (int)
		    //           {int, int ...}


		    int msgPathLength ;
		    byte[] intBytes = new byte[4];
		    int val;
		    for(int i=0; i<4; i++) {
			intBytes[i]= msg[19+i];
		    }
		    msgPathLength = MathUtils.byteArrayToInt(intBytes);
		    int pos = 23;
		    String sPath ="[";
		    for(int p=0; p< msgPathLength; p++) {
			for(int i=0; i<4; i++) {
			    intBytes[i]= msg[pos];
			    pos ++;
			}
			val = MathUtils.byteArrayToInt(intBytes);
			sPath = sPath + val + " ";
		    }
		    sPath = sPath + "]";


		    System.out.println("Systime: " + System.currentTimeMillis() + " ESMSERVER_ANYCASTACK for VIndex= " + vIndex + " StreamId= " + streamId + " SeqNum= " + seqNum + " ProspectiveParent= ( " + "IP: " + ipS + " MsgHops: " + (msgPathLength -1) + " MsgPath: " + sPath + " )");
		}



		if(msg[1] == UDPLibraServer.GRPMETADATAACKOPCODE) {
		    streamId = (int) msg[2];
		    byte[] myarray = new byte[4];
		    
		    for(int i=0; i<4; i++) {
			myarray[i]= msg[3+i];
		    }
		    int seq = MathUtils.byteArrayToInt(myarray);
		    
		    for(int i=0; i<4; i++) {
			myarray[i]= msg[7+i];
		    }
		    int grpSize = MathUtils.byteArrayToInt(myarray);
		    

		    for(int i=0; i<4; i++) {
			myarray[i]= msg[11+i];
		    }
		    int totalSlots = MathUtils.byteArrayToInt(myarray);
		    

		    for(int i=0; i<4; i++) {
			myarray[i]= msg[15+i];
		    }
		    int usedSlots = MathUtils.byteArrayToInt(myarray);
		    
		    
		    System.out.println("At ESMServer: received GrpMetadataAck for VIndex= " + vIndex + " StreamId= " + streamId + " Seq=" + seq + " GrpSize=" + grpSize + " TotalSlots= " + totalSlots + " UsedSlots= " + usedSlots + ")");
		}



		
		if(msg[1] == UDPLibraServer.PROSPECTIVECHILDOPCODE) {
		    streamId = (int) msg[2];
		    // param1(esmId - 4 bytes),
		    byte[] esmId = new byte[4];
		    for(int i=0; i<4; i++) {
			esmId[i]= msg[3+i];
		    }
		    String idS = asString(esmId);
		    System.out.println("Systime: " + System.currentTimeMillis() + " ESMSERVER_PROSPECTIVECHILD for VIndex= " + vIndex + " StreamId= " + streamId + " ProspeciveChild= ( " + "IP: " + idS + " )");

		       
		}
		
		if(msg[1] == UDPLibraServer.ANYCASTFAILUREOPCODE) {
		    streamId = (int) msg[2];
		    int seqNum ;
		    byte[] seqNumArray = new byte[4];
		    for(int i=0; i<4; i++) {
			seqNumArray[i]= msg[3+i];
		    }
		    seqNum = MathUtils.byteArrayToInt(seqNumArray);

		    System.out.println("Systime: " + System.currentTimeMillis() + " ESMSERVER_ANYCASTFAILURE for VIndex= " + vIndex + " StreamId= " + streamId + " SeqNum= " + seqNum);
		}
		
		
		//System.out.println("Thread:RequestHandler ending");
	    }
	    
	    catch(Exception e) {
		System.out.println("ESMServer: processRequest() " + e);
	    }
	}
    }
    




    public ESMServer() {
	start();
    }
	
    
    public void run()
    {
	try {
	    ds = new DatagramSocket(LibraTest.DUMMYESMSERVERPORT, LibraTest.localAddress);
	    System.out.println("ESMSERVER waiting for client connection ..on UDPSOCKET: (" + LibraTest.DUMMYESMSERVERPORT + "," + LibraTest.localAddress + ")");

	    InetAddress udplibraserverInetAddr;

	    if(!LibraTest.BINDADDRESS.equals("DEFAULT")) {
		udplibraserverInetAddr = InetAddress.getByName(LibraTest.BINDADDRESS);
	    } else {
		udplibraserverInetAddr = InetAddress.getByName("localhost");
	    }
	    udplibraserverSentToAddress = new InetSocketAddress(udplibraserverInetAddr,LibraTest.SCRIBESERVERPORT);

		
	}catch(Exception e) {
	    System.out.println("ERROR: Could not open esmserver UDPSOCKET for (" + LibraTest.DUMMYESMSERVERPORT + "," + LibraTest.localAddress + ")");
	    System.exit(1);
	}
	
	// Buffers for recieve a request.
	// if we receive more then 1024 bytes from the client
	// they will be discard.
	byte[] request = new byte[1024];
	
	
	    
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
		//System.out.println("requestorAddress= " + requestorAddress);
		//System.out.println("New connection accepted by ESMServer" + requestorAddress);
                            				

		
		// (3) Processing the request
		// Construct a handler to process the request
		try {
		    RequestHandler requestHandler = new RequestHandler(real_request);
		    // Create a new thread to process the request.
		    Thread thread = new Thread(requestHandler);
		    
		    // Start the thread.
		    thread.start();
		}
		catch(Exception e) {
		    System.out.println("ESMServer: Creating thread " + e);
		}
		
	    }
	    catch(Exception e) {
		System.out.println("ESMServer: while loop " + e);
	    }
	    
	    }//end-while
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

}




