
package rice.pastry.leafset;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;

import java.io.*;
import java.util.*;

/**
 * Broadcast a leaf set to another node.
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 */

public class BroadcastLeafSet extends Message implements Serializable 
{
    public static final int Update = 0;
    public static final int JoinInitial = 1;
    public static final int JoinAdvertise = 2;
    public static final int Correction = 3;

    private NodeHandle fromNode;
    private LeafSet theLeafSet;
    private int theType;

    /**
     * Constructor.
     */
    
    public BroadcastLeafSet(NodeHandle from, LeafSet leafSet, int type) { 
	super(new LeafSetProtocolAddress()); 
	fromNode = from;
	theLeafSet = leafSet;
	theType = type;
	setPriority(0);
    }
    
    /**
     * Constructor.
     *
     * @param cred the credentials.
     */

    public BroadcastLeafSet(Credentials cred, NodeHandle from, LeafSet leafSet, int type) { 
	super(new LeafSetProtocolAddress(), cred); 

	fromNode = from;
	theLeafSet = leafSet;
	theType = type;
	setPriority(0);
    }
    
    /**
     * Constructor.
     *
     * @param stamp the timestamp
     */

    public BroadcastLeafSet(Date stamp, NodeHandle from, LeafSet leafSet, int type) { 
	super(new LeafSetProtocolAddress(), stamp); 

	fromNode = from;
	theLeafSet = leafSet;
	theType = type;
	setPriority(0);
    }

    /**
     * Constructor.
     *
     * @param cred the credentials.
     * @param stamp the timestamp
     */    

    public BroadcastLeafSet(Credentials cred, Date stamp, NodeHandle from, LeafSet leafSet, int type) { 
	super(new LeafSetProtocolAddress(), cred, stamp); 

	fromNode = from;
	theLeafSet = leafSet;
	theType = type;
	setPriority(0);
    }

    /**
     * Returns the node id of the node that broadcast its leaf set.
     *
     * @return the node id.
     */

    public NodeHandle from() { return fromNode; }

    /**
     * Returns the leaf set that was broadcast.
     *
     * @return the leaf set.
     */

    public LeafSet leafSet() { return theLeafSet; }

    /**
     * Returns the type of leaf set.
     *
     * @return the type.
     */

    public int type() { return theType; }

    public String toString() {
	String s = "";

	if (Log.ifp(7))
	    s+="BroadcastLeafSet(of " + fromNode.getNodeId() + ":" + theLeafSet + ")";
	else if (Log.ifp(5))
	    s+="BroadcastLeafSet(of " + fromNode.getNodeId() + ")";

	return s;
    }
}
