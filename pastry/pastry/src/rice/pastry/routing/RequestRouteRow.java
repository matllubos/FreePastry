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

import java.io.*;
import java.util.*;

/**
 * Request a row from the routing table from another node.
 *
 * @version $Id$
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
	setPriority(true);
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
	setPriority(true);
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
	setPriority(true);
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
	setPriority(true);
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

    public String toString() {
	String s = "";

	s+="RequestRouteRow(row " + row + " by " + handle.getNodeId() + ")";

	return s;
    }
}
