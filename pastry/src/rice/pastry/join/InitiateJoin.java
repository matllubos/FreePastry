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

import java.io.*;
import java.util.*;

/**
 * Request to join this network.
 *
 * @author Andrew Ladd
 */

public class InitiateJoin extends Message implements Serializable
{    
    private NodeHandle handle;

    /**
     * Constructor.
     *
     * @param nh the node handle that the join will begin from.
     */

    public InitiateJoin(NodeHandle nh) {
	super(new JoinAddress());
	
	handle = nh;
    }

    /**
     * Constructor.
     *
     * @param jh a handle of the node trying to join the network.
     * @param stamp the timestamp
     *
     * @param nh the node handle that the join will begin from.
     */

    public InitiateJoin(Date stamp, NodeHandle nh) {
	super(new JoinAddress(), stamp);

	handle = nh;
    }

    /**
     * Constructor.
     *
     * @param jh a handle of the node trying to join the network.
     * @param cred the credentials 
     *
     * @param nh the node handle that the join will begin from.
     */

    public InitiateJoin(Credentials cred, NodeHandle nh) {
	super(new JoinAddress(), cred);

	handle = nh;
    }

    /**
     * Constructor.
     *
     * @param jh a handle of the node trying to join the network.
     * @param cred the credentials 
     * @param stamp the timestamp
     *
     * @param nh the node handle that the join will begin from.
     */

    public InitiateJoin(Credentials cred, Date stamp, NodeHandle nh) {
	super(new JoinAddress(), cred, stamp);

	handle = nh;
    }

    /**
     * Gets the handle for the join.
     *
     * @return the handle.
     */

    public NodeHandle getHandle() { return handle; }
}

