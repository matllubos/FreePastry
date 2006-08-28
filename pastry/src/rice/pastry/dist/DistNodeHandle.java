
package rice.pastry.dist;

import rice.pastry.*;
import rice.pastry.messaging.*;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Abstract class for handles to "real" remote nodes. This class abstracts out
 * the node handle verification which is necessary in the "real" pastry protocols,
 * since NodeHandles are sent across the wire.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public abstract class DistNodeHandle extends NodeHandle implements Observer {
    static final long serialVersionUID = 6030505652558872412L;
    // the nodeId of this node handle's remote node
    protected Id nodeId;

    /**
     * Constructor
     *
     * @param nodeId This node handle's node Id.
     */
    public DistNodeHandle(Id nodeId) {
      this.nodeId = nodeId;
    }

    /**
     * Gets the nodeId of this Pastry node.
     *
     * @return the node id.
     */
    public final Id getNodeId() {
      return nodeId;
    }

    /**
     * Returns a String representation of this DistNodeHandle. This method is
     * designed to be called by clients using the node handle, and is provided in order
     * to ensure that the right node handle is being talked to.
     *
     * @return A String representation of the node handle.
     */
    public abstract String toString(); 
    
    /**
     * Equivalence relation for nodehandles. They are equal if and
     * only if their corresponding NodeIds are equal.
     *
     * @param obj the other nodehandle .
     * @return true if they are equal, false otherwise.
     */
    public abstract boolean equals(Object obj);

    /**
     * Hash codes for node handles. It is the hashcode of
     * their corresponding NodeId's.
     *
     * @return a hash code.
     */
    public abstract int hashCode();

    public abstract InetSocketAddress getAddress();
}




