//////////////////////////////////////////////////////////////////////////////
// Rice Open Source Pastry Implementation                  //               //
//                                                         //  R I C E      //
// Copyright (c)                                           //               //
// Romer Gil                   rgil@cs.rice.edu            //   UNIVERSITY  //
// Andrew Ladd                 aladd@cs.rice.edu           //               //
// Tsuen Wan Ngan              twngan@cs.rice.edu          ///////////////////
//                                                                          //
// This program is free software; you can redistribute it and/or            //
// modify it under the terms of the GNU General Public License              //
// as published by the Free Software Foundation; either version 2           //
// of the License, or (at your option) any later version.                   //
//                                                                          //
// This program is distributed in the hope that it will be useful,          //
// but WITHOUT ANY WARRANTY; without even the implied warranty of           //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the            //
// GNU General Public License for more details.                             //
//                                                                          //
// You should have received a copy of the GNU General Public License        //
// along with this program; if not, write to the Free Software              //
// Foundation, Inc., 59 Temple Place - Suite 330,                           //
// Boston, MA  02111-1307, USA.                                             //
//                                                                          //
// This license has been added in concordance with the developer rights     //
// for non-commercial and research distribution granted by Rice University  //
// software and patent policy 333-99.  This notice may not be removed.      //
//////////////////////////////////////////////////////////////////////////////

package rice.pastry.routing;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;

import java.util.*;
import java.io.*;

/**
 * Broadcast message for a row from a routing table.
 *
 * @author Andrew Ladd
 */

public class BroadcastRouteRow extends Message implements Serializable {
    private NodeId fromNode;
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

    public BroadcastRouteRow(Credentials cred, Date stamp, NodeId from, RouteSet[] r) {
	super(addr, cred, stamp);

	fromNode = from;
	row = r;
    }


    /**
     * Constructor.
     *
     * @param stamp the timestamp
     * @param from the node id
     * @param r the row
     */

    public BroadcastRouteRow(Date stamp, NodeId from, RouteSet[] r) {
	super(addr, stamp);

	fromNode = from;
	row = r;
    }


    /**
     * Constructor.
     *
     * @param cred the credentials
     * @param from the node id
     * @param r the row
     */

    public BroadcastRouteRow(Credentials cred, NodeId from, RouteSet[] r) {
	super(addr, cred);

	fromNode = from;
	row = r;
    }


    /**
     * Constructor.
     *
     * @param from the node id
     * @param r the row
     */

    public BroadcastRouteRow(NodeId from, RouteSet[] r) {
	super(addr);

	fromNode = from;
	row = r;
    }

    /**
     * Gets the from node.
     *
     * @return the from node.
     */

    public NodeId from() { return fromNode; }

    /**
     * Gets the row that was sent in the message.
     *
     * @return the row.
     */

    public RouteSet[] getRow() { return row; }
}
