
package rice.pastry.routing;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;

import java.io.*;
import java.util.*;

/**
 * Request a row from the routing table from another node.
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 */

public class RequestRouteRow extends Message implements Serializable
{
    private NodeHandle handle;
    private int row;

    /**
     * Constructor.
     *
     * @param nh the return handle.
     * @param r which row
     */
    
    public RequestRouteRow(NodeHandle nh, int r) { 
	super(new RouteProtocolAddress()); 
	handle = nh;
	row = r;
	setPriority(0);
    }
    
    /**
     * Constructor.
     *
     * @param cred the credentials.
     * @param nh the return handle.
     * @param r which row
     */

    public RequestRouteRow(Credentials cred, NodeHandle nh, int r) { 
	super(new RouteProtocolAddress(), cred); 
	handle = nh;
	row = r;
	setPriority(0);
    }
    
    /**
     * Constructor.
     *
     * @param stamp the timestamp
     * @param nh the return handle
     * @param r which row
     */

    public RequestRouteRow(Date stamp, NodeHandle nh, int r) { 
	super(new RouteProtocolAddress(), stamp); 
	handle = nh;
	row = r;
	setPriority(0);
    }

    /**
     * Constructor.
     *
     * @param cred the credentials.
     * @param stamp the timestamp
     * @param nh the return handle.
     * @param r which row
     */    

    public RequestRouteRow(Credentials cred, Date stamp, NodeHandle nh, int r) { 
	super(new RouteProtocolAddress(), cred, stamp); 
	handle = nh;
	row = r;
	setPriority(0);
    }

    /**
     * The return handle for the message
     *
     * @return the node handle
     */

    public NodeHandle returnHandle() { return handle; }

    /**
     * Gets the row that made the request.
     *
     * @return the row.
     */
    
    public int getRow() { return row; }

    public String toString() {
	String s = "";

	s+="RequestRouteRow(row " + row + " by " + handle.getNodeId() + ")";

	return s;
    }
}
