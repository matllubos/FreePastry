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

package rice.pastry.leafset;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;

import java.io.*;
import java.util.*;

/**
 * Request a leaf set from another node.
 *
 * @author Andrew Ladd
 */

public class RequestLeafSet extends Message implements Serializable
{
    private NodeHandle handle;

    /**
     * Constructor.
     *
     * @param nh the return handle.
     */
    
    public RequestLeafSet(NodeHandle nh) { 
	super(new LeafSetProtocolAddress()); 
	handle = nh;
    }
    
    /**
     * Constructor.
     *
     * @param cred the credentials.
     * @param nh the return handle.
     */

    public RequestLeafSet(Credentials cred, NodeHandle nh) { 
	super(new LeafSetProtocolAddress(), cred); 
	handle = nh;
    }
    
    /**
     * Constructor.
     *
     * @param stamp the timestamp
     * @param nh the return handle
     */

    public RequestLeafSet(Date stamp, NodeHandle nh) { 
	super(new LeafSetProtocolAddress(), stamp); 
	handle = nh;
    }

    /**
     * Constructor.
     *
     * @param cred the credentials.
     * @param stamp the timestamp
     * @param nh the return handle.
     */    

    public RequestLeafSet(Credentials cred, Date stamp, NodeHandle nh) { 
	super(new LeafSetProtocolAddress(), cred, stamp); 
	handle = nh;
    }

    /**
     * The return handle for the message
     *
     * @return the node handle
     */

    public NodeHandle returnHandle() { return handle; }
}
