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

package rice.pastry.direct;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.standard.*;
import rice.pastry.routing.*;
import rice.pastry.leafset.*;

import java.util.*;

/**
 * Pastry node factory for direct connections between nodes (local instances).
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 * @author Sitaram Iyer
 * @author Rongmei Zhang/Y. Charlie Hu
 */

public class DirectPastryNodeFactory implements PastryNodeFactory
{
    private RandomNodeIdFactory nidFactory;
    private NetworkSimulator simulator;

    private static final int rtMax = 8;
    private static final int lSetSize = 24;
  
    public DirectPastryNodeFactory() {
	nidFactory = new RandomNodeIdFactory();
//	simulator = new EuclideanNetwork();
	simulator = new SphereNetwork();
    }

    public NetworkSimulator getNetworkSimulator() { return simulator; }
    
    /**
     * Get the closest node from a RouteSet
     */

    private NodeHandle getClosest( NodeHandle local, RouteSet rs ){
	NodeHandle nh = null;
	NodeHandle nearHandle = null;
	double dist = Double.MAX_VALUE;
	double newdist;

	for ( int i=0; i<rs.size(); i++ ) {
	    nh = rs.get(i);
	    newdist = simulator.proximity( local.getNodeId(), nh.getNodeId() );
	    if( dist >= newdist  ){
		nearHandle = nh;
		dist = newdist;
	    }
	}
	return nearHandle;
    }

    /**
     * The discovery algorithm to find a nearby node
     */

    private NodeHandle discover( NodeHandle localhandle, NodeHandle bootstrap ){
	if( bootstrap == null )
	    return null;

	PastryNode	nearNode = ((DirectNodeHandle)bootstrap).getLocal();
	LeafSet 	ls = nearNode.getLeafSet();
	NodeHandle 	nearHandle = bootstrap;
	double		dist = simulator.proximity( localhandle.getNodeId(), bootstrap.getNodeId() );
	double		newdist;
	int		i;
	NodeHandle	nh, currentClosest;

	for( i=0; i<ls.size(); i++ ){
	    nh = ls.get( i );
	    if( nh == null )
		continue;
	    newdist = simulator.proximity( localhandle.getNodeId(), nh.getNodeId() );
	    if( dist >= newdist  ){
		nearHandle = nh;
		dist = newdist;
	    }
	}

	nearNode = ((DirectNodeHandle)nearHandle).getLocal();
	RoutingTable rt = nearNode.getRoutingTable();
	int depth = rt.numRows();

	while( depth -- > 0 ){
	    for( i=0; i<rt.numColumns(); i++ ){
		nh = getClosest( localhandle, rt.getRow(depth)[i] );
		if( nh == null )
		    continue;
		newdist = simulator.proximity( localhandle.getNodeId(), nh.getNodeId() );
		if( dist >= newdist  ){
		    nearHandle = nh;
		    dist = newdist;
		}
	    }
	    nearNode = ((DirectNodeHandle)nearHandle).getLocal();
	    rt = nearNode.getRoutingTable();
	}
	
	do{
	    currentClosest = nearHandle;
	    for( i=0; i<rt.numColumns(); i++ ){
		nh = getClosest( localhandle, rt.getRow(0)[i] );
		if( nh == null )
		    continue;
		newdist = simulator.proximity( localhandle.getNodeId(), nh.getNodeId() );
		if( dist >= newdist  ){
		    nearHandle = nh;
		    dist = newdist;
		}
	    }
	    nearNode = ((DirectNodeHandle)nearHandle).getLocal();
	    rt = nearNode.getRoutingTable();
	}while( currentClosest != nearHandle );
	return nearHandle;
    }

    /**
     * Manufacture a new Pastry node.
     *
     * @return a new PastryNode
     */
    public PastryNode newNode(NodeHandle bootstrap) {

	NodeId nodeId = nidFactory.generateNodeId();
	DirectPastryNode pn = new DirectPastryNode(nodeId);
	
	DirectNodeHandle localhandle = new DirectNodeHandle(pn, pn, simulator);

	DirectSecurityManager secureMan = new DirectSecurityManager(simulator);
	MessageDispatch msgDisp = new MessageDispatch();

	RoutingTable routeTable = new RoutingTable(localhandle, rtMax);
	LeafSet leafSet = new LeafSet(localhandle, lSetSize);
		
	StandardRouter router =
	    new StandardRouter(localhandle, routeTable, leafSet, secureMan);
	StandardLeafSetProtocol lsProtocol =
	    new StandardLeafSetProtocol(localhandle, secureMan, leafSet, routeTable);
	StandardRouteSetProtocol rsProtocol =
	    new StandardRouteSetProtocol(localhandle, secureMan, routeTable);
	StandardJoinProtocol jProtocol =
	    new StandardJoinProtocol(pn, localhandle, secureMan, routeTable, leafSet);

//	simulator.registerNodeId(nodeId);
	simulator.registerNodeId( localhandle );

	msgDisp.registerReceiver(router.getAddress(), router);
	msgDisp.registerReceiver(lsProtocol.getAddress(), lsProtocol);
	msgDisp.registerReceiver(rsProtocol.getAddress(), rsProtocol);
	msgDisp.registerReceiver(jProtocol.getAddress(), jProtocol);

	pn.setElements(localhandle, secureMan, msgDisp, leafSet, routeTable);
	pn.setDirectElements(/* simulator */);
	secureMan.setLocalPastryNode(pn);

	// pn.doneNode(bootstrap);
	//	pn.doneNode( discover(localhandle,bootstrap) );
	pn.doneNode( simulator.getClosest(nodeId) );

	return pn;
    }
}
