/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/

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

    // the nodeId of this node handle's remote node
    protected NodeId nodeId;

    // the address (ip + port) of this node
    protected InetSocketAddress address;

    /**
     * Constructor
     *
     * @param nodeId This node handle's node Id.
     */
    public DistNodeHandle(NodeId nodeId, InetSocketAddress address) {
      this.nodeId = nodeId;
      this.address = address;
    }

    /**
     * Gets the nodeId of this Pastry node.
     *
     * @return the node id.
     */
    public final NodeId getNodeId() {
      return nodeId;
    }

    /**
     * Returns the IP address and port of the remote node.
     *
     * @return The InetSocketAddress of the remote node.
     */
    public final InetSocketAddress getAddress() {
      return address;
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
}




