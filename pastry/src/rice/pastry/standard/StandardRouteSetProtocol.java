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

package rice.pastry.standard;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.pastry.security.*;

import java.util.*;

/**
 * An implementation of a simple route set protocol.
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
