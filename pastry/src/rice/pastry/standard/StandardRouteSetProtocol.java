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

package rice.pastry.standard;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.pastry.security.*;

import java.util.*;

/**
 * An implementation of a simple route set protocol.
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 */

public class StandardRouteSetProtocol implements Observer, MessageReceiver
{
    private NodeHandle localHandle;    
    private PastrySecurityManager security;
    private RoutingTable routeTable;
    private Address address;
        
    /**
     * Constructor.
     *
     * @param lh the local handle
     * @param sm the security manager
     * @param rt the routing table
     */

    public StandardRouteSetProtocol(NodeHandle lh, PastrySecurityManager sm, RoutingTable rt) {
	localHandle = lh;
	security = sm;
	routeTable = rt;

	address = new RouteProtocolAddress();

	rt.addObserver(this);
    }

    /**
     * Gets the address.
     *
     * @return the address.
     */

    public Address getAddress() { return address; }

    /**
     * Observer update.
     *
     * @param obs the observable.
     * @param arg the argument.
     */

    public void update(Observable obs, Object arg) {}

    /**
     * Receives a message.
     *
     * @param msg the message.
     */

    public void receiveMessage(Message msg) {
	if (msg instanceof BroadcastRouteRow) {
	    BroadcastRouteRow brr = (BroadcastRouteRow) msg;

	    RouteSet[] row = brr.getRow();

	    for (int i=0; i<row.length; i++) {
		RouteSet rs = row[i];

		int n = rs.size();

		//System.out.print(n + " ");
		
		for (int j=0; j<n; j++) {
		    NodeHandle nh = rs.get(j);

		    nh = security.verifyNodeHandle(nh);

		    if (nh.isAlive() == false) continue;

		    routeTable.put(nh);
		}
	    }

	    //System.out.println("done");
	}
	else if (msg instanceof RequestRouteRow) {
	    RequestRouteRow rrr = (RequestRouteRow) msg;
	    
	    // not implemented - oops.
	}
    }
}
