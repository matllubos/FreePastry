package rice.pastry.rmi;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.standard.*;

import java.util.*;

/**
 * Security manager for RMI connections between nodes.
 *
 * @author Andrew Ladd
 * @author Sitaram Iyer
 */

public class RMIPastrySecurityManager implements PastrySecurityManager 
{
    private RMINodeHandle localhandle;
    private RMINodeHandlePool handlepool;

    /**
     * Constructor.
     */
    public RMIPastrySecurityManager(RMINodeHandle rlh, RMINodeHandlePool hp) { 
	localhandle = rlh;
	handlepool = hp; // contains only localhandle by now
    }

    /**
     * This method takes a message and returns true
     * if the message is safe and false otherwise.
     *
     * @param msg a message.
     * @return if the message is safe, false otherwise.
     */
    
    public boolean verifyMessage(Message msg) { return true; }

    /**
     * Checks to see if these credentials can be associated with the address.
     *
     * @param cred some credentials.
     * @param addr an address.
     *
     * @return true if the credentials match the address, false otherwise.
     */
    
    public boolean verifyAddressBinding(Credentials cred, Address addr) { return true; }

    /**
     * Verify node handle safety.
     *
     * @param handle the handle to check.
     *
     * @return the verified node handle
     */

    public NodeHandle verifyNodeHandle(NodeHandle handle) {
	NodeId mynid = localhandle.getNodeId();
	NodeId nid = handle.getNodeId();

	if (mynid.equals(nid)) {
	    return localhandle;
	    //return handlepool.coalesce(localhandle);
	}
	else if (handle instanceof PastryNode) {
	    //DirectNodeHandle rnh = new RMINodeHandle((PastryNode) handle);
	    //return rnh;
	    System.out.println("[rmi] panic: Handle instanceof PastryNode. Tentatively returning NULL.");
	    return null; // xxx
	}
	else if (handle instanceof ProxyNodeHandle) {
	    System.out.println("[rmi] panic: Handle instanceof ProxyNodeHandle. Returning NULL.");
	    return null;
	}
	else if (handle instanceof RMINodeHandle) {
	    RMINodeHandle rnh = (RMINodeHandle) handle;

	    // set local handle, for bouncing failed RouteMessages to myself
	    rnh.setLocalHandle(localhandle);

	    return handlepool.coalesce(rnh);
	}
	else throw new Error("node handle of unknown type");	
    }

    /**
     * Gets the current time for a timestamp.
     *
     * @return the timestamp.
     */
    
    public Date getTimestamp() { return new Date(); }
}
