package rice.p2p.libra;
import java.util.*;
import java.io.*;
import java.net.*;

public class LServer {


    public static int LSERVER_PORT = -1;
    public static final byte PINGALIVEOPCODE  = 15;

    public DatagramSocket ds = null;
    
    // This will determine which set of nodes libra/coordgen we are monitoring
    public static int portShift = 0;

    private  class RequestHandler implements Runnable
    {
	byte[] msg;
	SocketAddress requestorAddress;
	Object obj; // This is a replacement for ESMClient in UDPLibraServer

	// Constructor
	public RequestHandler(byte[] request, Object obj, SocketAddress requestorAddress) throws Exception 
	{
	    this.msg = request;
	    this.requestorAddress = requestorAddress;
	}
    
	// Implement the run() method of the Runnable interface.
	public void run()
	{
	    try {
		processRequest();
	    }
	    catch(Exception e) {
		System.out.println(e);
		e.printStackTrace();
	    }
	}
	
	private void processRequest() throws Exception
	{
	    byte vIndex = 0;
	    
	    if(msg.length < 1) {
		System.out.println("Warning: msg.length is less than 1, no OPCODE can be read");
		return;
	    }

	    vIndex = msg[0];
	    
	    if(msg[1] == PINGALIVEOPCODE) {
		System.out.println("RecvPing from " + requestorAddress);
	    }
	    
	}

    }

    public LServer() {
	try {
	    ds = new DatagramSocket(LSERVER_PORT);
	    System.out.println("LSERVER waiting for client connection ..on port: " + LSERVER_PORT);
	    
	}catch(Exception e) {
	    System.out.println("ERROR: Could not open LSERVER on port:" +  LSERVER_PORT );
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
		//System.out.println("New connection accepted by UDPScribeServer" + requestorAddress);
		
		
		
		
		// We extract the first byte which is the vIndex
		int vIndex = (int) real_request[0];
		// (2) Processing the request
		// Construct a handler to process the request
		
		
		
		RequestHandler requestHandler = new RequestHandler(real_request, null, requestorAddress);
		// Create a new thread to process the request.
		Thread thread = new Thread(requestHandler);
		
		// Start the thread.
		thread.start();
		
	    }
	    catch(Exception e) {
		e.printStackTrace();
	    }
	    
	}//end-while
    }
    
    


    public static void main(String[] args) throws Exception {
	// We do the parsing of the options
      for (int i = 0; i < args.length; i++) {
	  if (args[i].equals("-help")) {
	      System.out.println("Usage: LServer [-portShift s]");
	      System.exit(1);
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

      new LServer();

    }
      

}
