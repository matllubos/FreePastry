
package rice.pastry.standard;

import rice.pastry.*;

import java.util.Random;
import java.security.*;

/**
 * Constructs random node ids by SHA'ing consecutive numbers, with random starting value.
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 * @author Peter Druschel
 */

public class RandomNodeIdFactory implements NodeIdFactory
{
    private Random rng;
    private long next;


    /**
     * Constructor.
     */
    
    public RandomNodeIdFactory() {
	rng = new Random(PastrySeed.getSeed());
	next = rng.nextLong();
    }

    /**
     * generate a nodeId
     *
     * @return the new nodeId
     */

    public NodeId generateNodeId() {

	//byte raw[] = new byte[NodeId.nodeIdBitLength >> 3];
	//rng.nextBytes(raw);

	byte raw[] = new byte[8];
	long tmp = ++next;
	for (int i=0; i<8; i++) {
	    raw[i] = (byte)(tmp & 0xff);
	    tmp >>= 8;
	}

	MessageDigest md = null;
	try {
	    md = MessageDigest.getInstance("SHA");
	} catch ( NoSuchAlgorithmException e ) {
	    System.err.println( "No SHA support!" );
	}

	md.update(raw);
	byte[] digest = md.digest();
	
	NodeId nodeId = NodeId.buildNodeId(digest);

	return nodeId;
    }

}

