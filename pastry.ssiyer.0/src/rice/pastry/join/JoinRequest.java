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

package rice.pastry.join;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;

import java.io.*;
import java.util.*;

/**
 * Request to join this network.
 *
 * @author Andrew Ladd
 */

public class JoinRequest extends Message implements Serializable
{
    private NodeHandle handle;
    private NodeHandle joinHandle;
    private int rowCount;
    private RouteSet rows[][];
    
    /**
     * Constructor.
     *
     * @param nh a handle of the node trying to join the network.
     */

    public JoinRequest(NodeHandle nh) {
	super(new JoinAddress());

	handle = nh;

	initialize();
    }

    /**
     * Constructor.
     *
     * @param nh a handle of the node trying to join the network.
     * @param stamp the timestamp
     */

    public JoinRequest(NodeHandle nh, Date stamp) {
	super(new JoinAddress(), stamp);

	handle = nh;

	initialize();
    }

    /**
     * Constructor.
     *
     * @param nh a handle of the node trying to join the network.
     * @param cred the credentials 
     */

    public JoinRequest(NodeHandle nh, Credentials cred) {
	super(new JoinAddress(), cred);

	handle = nh;

	initialize();
    }

    /**
     * Constructor.
     *
     * @param nh a handle of the node trying to join the network.
     * @param cred the credentials 
     * @param stamp the timestamp
     */

    public JoinRequest(NodeHandle nh, Credentials cred, Date stamp) {
	super(new JoinAddress(), cred, stamp);
	
	handle = nh;

	initialize();
    }


    /**
     * Gets the handle of the node trying to join.
     *
     * @return the handle.
     */
    
    public NodeHandle getHandle() { return handle; }


    /**
     * Gets the handle of the node that accepted the join request;
     *
     * @return the handle.
     */
    
    public NodeHandle getJoinHandle() { return joinHandle; }

    /**
     * Returns true if the request was accepted, false if it hasn't yet.
     */

    public boolean accepted() { return joinHandle != null; }

    /**
     * Accept join request.
     *
     * @param nh the node handle that accepts the join request.
     */

    public void acceptJoin(NodeHandle nh) {
	joinHandle = nh;
    }

    /**
     * Returns the number of rows left to determine (in order).
     *
     * @return the number of rows left.
     */

    public int lastRow() { return rowCount; }

    /**
     * Push row.
     *
     * @param row the row to push.
     */

    public void pushRow(RouteSet row[]) {
	rows[--rowCount] = row;
    }

    /**
     * Get row.
     *
     * @param i the row to get.
     *
     * @return the row.
     */

    public RouteSet[] getRow(int i) { return rows[i]; }
    
    /**
     * Get the number of rows.
     *
     * @return the number of rows.
     */

    public int numRows() { return rows.length; }
    
    private void initialize() {
	joinHandle = null;
	
	rowCount = NodeId.nodeIdBitLength / RoutingTable.idBaseBitLength;

	rows = new RouteSet[rowCount][];
    }
}

