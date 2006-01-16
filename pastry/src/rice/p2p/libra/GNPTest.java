package rice.p2p.libra;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.util.*;

public class GNPTest {

    public static String BINDADDRESS = "DEFAULT";
    public static InetAddress localAddress;

    public GNPTest() {
	
    }

    public static void main(String[] args) throws Exception {
	
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
	String hostName = "NONE";
	GNPCoordinate gnpCoord = null;

	try {
	    if(!BINDADDRESS.equals("DEFAULT")) {
		localAddress = InetAddress.getByName(BINDADDRESS);
	    } else {
		localAddress = InetAddress.getLocalHost();
	    }
	    // Get the Ip
	    String hostAddress = localAddress.getHostAddress();
	    //System.out.println("HostAddress= " + hostAddress);
	    //hostIp = addr.getAddress();
	    hostName = localAddress.getHostName();
	    //System.out.println("hostIp: " + hostIp[0] + "." + hostIp[1] + "." + hostIp[2] + "." + hostIp[3]);
	} 
	catch (UnknownHostException e) {
	    System.out.println("ERROR: Trying to get localhost ipaddress");
	    System.exit(1);
	}
	


	try {
	    //String localhostname = "127.0.0.1";
	    QueryResult result = GNPUtilities.queryGNP(localAddress);
	    // Result is null if there was a timeout trying to conatct the GNP service

	    if(result != null) {
		double[] coord = result.getCoordinates();
		gnpCoord = new GNPCoordinate(result.isStable(),coord.length,coord);
	    }
		
	} catch(Exception e) {
	    System.out.println("Warning: Exception while contacting GNP server");
	}
	try {
	    BufferedWriter fout;
            fout = new BufferedWriter(new FileWriter("GNPCOORD." + BINDADDRESS));
	    String toDump = "SysTime: " + System.currentTimeMillis() + " Node: " + hostName + " " + gnpCoord;
	    System.out.println(toDump);
	    fout.write(toDump, 0, toDump.length());
	    fout.newLine();
	    fout.flush();
	    fout.close();

        }
        catch(IOException e) {
            System.out.println("ERROR: In opening input/output files");
        }

	

	
    }

}
