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

package rice.pastry.routing;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;

import java.util.*;
import java.io.*;

/**
 * Broadcast message for a row from a routing table.
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 */

public class BroadcastRouteRow extends Message implements Serializable {
    private NodeHandle fromNode;
    private RouteSet[] row;

    private static final Address addr = new RouteProtocolAddress();

    /**
     * Constructor.
     *
     * @param cred the credentials
     * @param stamp the timestamp
     * @param from the node id
     * @param r the row
     */

    public BroadcastRouteRow(Credentials cred, Date stamp, NodeHandle from, RouteSet[] r) {
	super(addr, cred, stamp);

	fromNode = from;
	row = r;
	setPriority(0);
    }


    /**
     * Constructor.
     *
     * @param stamp the timestamp
     * @param from the node id
     * @param r the row
     */

    public BroadcastRouteRow(Date stamp, NodeHandle from, RouteSet[] r) {
	super(addr, stamp);

	fromNode = from;
	row = r;
	setPriority(0);
    }


    /**
     * Constructor.
     *
     * @param cred the credentials
     * @param from the node id
     * @param r the row
     */

    public BroadcastRouteRow(Credentials cred, NodeHandle from, RouteSet[] r) {
	super(addr, cred);

	fromNode = from;
	row = r;
	setPriority(0);
    }


    /**
     * Constructor.
     *
     * @param from the node id
     * @param r the row
     */

    public BroadcastRouteRow(NodeHandle from, RouteSet[] r) {
	super(addr);

	fromNode = from;
	row = r;
	setPriority(0);
    }

    /**
     * Gets the from node.
     *
     * @return the from node.
     */

    public NodeHandle from() { return fromNode; }

    /**
     * Gets the row that was sent in the message.
     *
     * @return the row.
     */

    public RouteSet[] getRow() { return row; }

    public String toString() {
	String s = "";

	s+="BroadcastRouteRow(of " + fromNode.getNodeId() + ")";

	return s;
    }
}
