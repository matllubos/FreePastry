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

import java.io.*;
import java.util.*;

/**
 * Request a row from the routing table from another node.
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
}
