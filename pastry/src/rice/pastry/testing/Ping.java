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

package rice.pastry.testing;

import rice.pastry.*;
import rice.pastry.client.*;
import rice.pastry.routing.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.direct.*;

import java.util.*;

/**
 * Ping
 *
 * A performance test suite for pastry. This is the per-node app object.
 *
 * @version $Id$
 *
 * @author Rongmei Zhang
 */

public class Ping extends PastryAppl {
    private static Address pingAddress = new PingAddress();
    private Credentials pingCred = new PermissiveCredentials();

    public Ping(PastryNode pn) {
	super(pn);
    }

    public Address getAddress() { return pingAddress; }

    public Credentials getCredentials() { return pingCred; }

    public void sendPing(NodeId nid) {
//	System.out.println("sending a trace from " + getNodeId() + " to " + nid);
	routeMsg(nid, new PingMessageNew(pingAddress, getNodeId(), nid),
		 pingCred, new SendOptions());
    }

    public void messageForAppl(Message msg) {
	/*
	System.out.print( msg );
	System.out.println(" received");
	*/
		    
	PingMessageNew pMsg = (PingMessageNew)msg;
        int nHops = pMsg.getHops() - 1;
	double fDistance = pMsg.getDistance();
	double rDistance;

	NetworkSimulator sim = ((DirectNodeHandle)((DirectPastryNode)thePastryNode).getLocalHandle()).getSimulator();
	PingTestRecord tr = (PingTestRecord)(sim.getTestRecord());

	double dDistance = sim.proximity(thePastryNode.getNodeId(),pMsg.getSource());
	if ( dDistance == 0 ) {
	    rDistance = 0;
	}
	else{
	    rDistance = fDistance/dDistance;
	}
	/*
	System.out.println( "------------------" );
	System.out.println( "number of hops " + nHops );
	System.out.println( "relative distance " + rDistance );
	System.out.println( "------------------" );
	*/
	
	tr.addHops( nHops );
	tr.addDistance( rDistance );

    }
    
    public boolean enrouteMessage(Message msg, NodeId from, NodeId nextHop, SendOptions opt) {
	/*
	System.out.print(msg);
	System.out.println(" at " + getNodeId());
	*/

	PingMessageNew pMsg = (PingMessageNew)msg;
	pMsg.incrHops();
	pMsg.incrDistance( ((DirectNodeHandle)((DirectPastryNode)thePastryNode).getLocalHandle()).getSimulator().proximity(thePastryNode.getNodeId(), nextHop) );

	return true;
    }

    public void leafSetChange(NodeHandle nh, boolean wasAdded) {
    }

    public void routeSetChange(NodeHandle nh, boolean wasAdded) {
    }
}

