
package rice.pastry.standard;

import rice.pastry.*;

import java.util.Random;
import java.net.*;
import java.security.*;

/**
 * Constructs NodeIds for virtual nodes derived from the IP address and port number of this Java VM.
 *
 * @version $Id$
 *
 * @author Peter Druschel
 */

public class IPNodeIdFactory implements NodeIdFactory
{
    private static int nextInstance = 0;
    private InetAddress localIP;
    private int port;
    //private Random rng;

    /**
     * Constructor.
     *
     * @param port the port number on which this Java VM listens
     */
   
    public IPNodeIdFactory(int port) {
	this.port = port;

	try {
	    localIP = InetAddress.getLocalHost();
	    if (localIP.isLoopbackAddress()) throw new Exception("got loopback address: nodeIds will not be unique across computers!");
	} catch (Exception e) {
	    System.out.println("ALERT: IPNodeIdFactory cannot determine local IP address: " + e);
	}

//	rng = new Random(PastrySeed.getSeed());
    }

    /**
     * generate a nodeId
     * multiple invocations result in a deterministic series of randomized NodeIds, seeded by the 
     * IP address of the local host.
     *
     * @return the new nodeId
     */

    public NodeId generateNodeId() {
	byte rawIP[] = localIP.getAddress();

	byte rawPort[] = new byte[2];
	int tmp = port;
	for (int i=0; i<2; i++) {
	    rawPort[i] = (byte)(tmp & 0xff);
	    tmp >>= 8;
	}

	byte raw[] = new byte[4];
	tmp = ++nextInstance;
	for (int i=0; i<4; i++) {
	    raw[i] = (byte)(tmp & 0xff);
	    tmp >>= 8;
	}

	MessageDigest md = null;
	try {
	    md = MessageDigest.getInstance("SHA");
	} catch ( NoSuchAlgorithmException e ) {
	    System.err.println( "No SHA support!" );
	}

	md.update(rawIP);
	md.update(rawPort);
	md.update(raw);
	byte[] digest = md.digest();

	// now, we randomize the least significant 32 bits to ensure
	// that stale node handles are detected reliably.
//	byte rand[] = new byte[4];
//	rng.nextBytes(rand);
//	for (int i=0; i<4; i++)
//	    digest[i] = rand[i];

	NodeId nodeId = NodeId.buildNodeId(digest);

	return nodeId;
    }

}

