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

	s+="BroadcastLeafSet(of " + fromNode.getNodeId() + ": " + theLeafSet + ")";
	//s+="BroadcastLeafSet of " + fromNode.getNodeId() + " " + theLeafSet.cwSize() + " : " + theLeafSet.ccwSize();

	return s;
    }
}
