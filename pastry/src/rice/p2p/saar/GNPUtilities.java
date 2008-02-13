package rice.p2p.saar;

import java.math.*;
import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;

/** 
 * A Utilities class for the GNP java classes. Contains several of the constants found in the 
 * Global.h file, and contains useful methods for communicating with a GNP system for the GNP
 * java programs.
 */
public class GNPUtilities {
  /** C_BUF_SIZE. Found in Global.h */
  public static final int C_BUF_SIZE = 5000;
  /** C_UDP_PORT. Found in Global.h */
    public static final int C_UDP_PORT = 44200;  // This is the port on which service is running
  /** E_GNP_QUERY Found in Global.h */
  public static final byte E_GNP_QUERY = 4;
  /** REPLY_WANTED. Found in Global.h */
  public static final byte REPLY_WANTED = 1;
  /** C_PRECISION. Found in Global.h */
  public static final double C_PRECISION = 1000.0;
  /** An instance of a random number generator */
  public static final Random random = new Random();
  /** space_string array. Found in Global.h */
  public static final String [] space_string = {"Euclidean","S_MAX"};
  
  /**
   * Sends a UDP packet containing a GNP_QUERY message to the node specified by the given hostname.
   * @param hostname the name or id of the host to query, as a String
   * @return a QueryResult object, which is a structure containing all of the useful information obtained from a GNP Query
   */
  public static QueryResult queryGNP(String hostname) throws UnknownHostException {
    return queryGNP(InetAddress.getByName(hostname));
  }
  
  /**
   * Sends a UDP packet containing a GNP_QUERY message to the node specified by the given address.
   * @param ip the address of the host to query, as an InetAddress
   * @return a QueryResult object, which is a structure containing all of the useful information obtained from a GNP Query
   */
  public static QueryResult queryGNP(InetAddress ip) {
    try {
      DatagramSocket socket = new DatagramSocket();
      
      int auth_id = random.nextInt();
      byte[] qm = new byte[16];
      qm[0] = E_GNP_QUERY;
      
      qm[1] = REPLY_WANTED;
      
      qm[2] = (byte)(C_UDP_PORT>>8);
      qm[3] = (byte)(C_UDP_PORT%256);
      
      qm[4] = 127;
      qm[5] = 0;
      qm[6] = 0;
      qm[7] = 1;
      
      qm[8] = (byte)(auth_id>>24);
      qm[9] = (byte)(auth_id>>16%256);
      qm[10]= (byte)(auth_id>>8%256);
      qm[11]= (byte)(auth_id%256);
      
      socket.send(new DatagramPacket(qm,16,ip,C_UDP_PORT));
      
      
      byte[] buf = new byte[C_BUF_SIZE];
      DatagramPacket received = new DatagramPacket(buf,C_BUF_SIZE);

      // We will set a timeout on this incase the GNP service is not up
      socket.setSoTimeout(1000);
      
      socket.receive(received);
      byte [] data = received.getData();
      byte dim = data[12];
      
      // check auth_id
      BigInteger recv_id = new BigInteger(new byte[] { data[8],data[9],data[10],data[11]});
      if(auth_id != recv_id.intValue()) {
	  throw new GNPException("auth_id did not match, could be a malicious packet, ignoring.\n");
      }
      
      double [] coords = new double[dim];
      for(byte i = 0; i < dim; i++) {
	  BigInteger temp = new BigInteger(new byte[] {data[16+i*4], data[17+i*4], data[18+i*4], data[19+i*4]});
	  coords[i] = temp.intValue()/C_PRECISION;
      }
      return new QueryResult(data[14]!=0, ip, coords, space_string[data[13]]); // FIX SPACE
    }
    
    catch(SocketTimeoutException e) {
	//System.out.println("Warning: queryGNP: Timeout while contacting GNP service");
	return null;
    }
    catch(SocketException se) {
	throw new GNPException("SocketException: " + se.getMessage());
    }
    catch(IOException ioe) {
	throw new GNPException("IOExcpetion: " + ioe.getMessage());
    }
  }
  
}
