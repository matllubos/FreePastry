package rice.pastry.rmi;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.standard.*;

import java.util.*;

/**
 * Security manager for RMI connections between nodes.
 *
 * @author Sitaram Iyer
 */

public class RMIPastrySecurityManager implements PastrySecurityManager 
{
    private PastryNode pnode;

    /**
     * Constructor.
     */

    public RMIPastrySecurityManager() { 
	pnode = null;
    }

    /**
     * Sets the local pastry node.
     *
     * @param pn pastry node.
     */

    public void setLocalPastryNode(PastryNode local) { pnode = local; }

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
	NodeId local = pnode.getNodeId();
	NodeId nid = handle.getNodeId();

	if (local.equals(nid)) {
	    ProxyNodeHandle lnh = new ProxyNodeHandle(local);
	    lnh.setProxy(pnode);
	    return lnh;
	}
	else if (handle instanceof PastryNode) {
	    //DirectNodeHandle rnh = new RMINodeHandle((PastryNode) handle);
	    //return rnh;
	    System.out.println("Handle instanceof PastryNode. Tentatively returning NULL.");
	    return null; // xxx
	}
	else if (handle instanceof ProxyNodeHandle) {
	    //ProxyNodeHandle lnh = (ProxyNodeHandle) handle;
	    //RMINodeHandle rnh = new RMINodeHandle(lnh.getNode());
	    //return rnh;
	    System.out.println("Handle instanceof ProxyNodeHandle. Tentatively returning NULL.");
	    return null; // xxx
	}
	else if (handle instanceof RMINodeHandle) {
	    RMINodeHandle rnh = (RMINodeHandle) handle;
	    RMINodeHandle retRnh = new RMINodeHandle(rnh.getRemote());
	    return retRnh;
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
