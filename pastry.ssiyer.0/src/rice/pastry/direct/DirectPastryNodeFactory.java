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
 * @author Andrew Ladd
 */

public class DirectPastryNodeFactory implements PastryNodeFactory
{
    private RandomNodeIdFactory nidFactory;
    private ProxyNodeHandle lhandle;
    
    private NetworkSimulator simulator;

    private NodeId nodeId;
    private DirectSecurityManager secureMan;
    private MessageDispatch msgDisp;

    private RoutingTable routeTable;
    private LeafSet leafSet;
  
    private StandardRouter router;

    private StandardLeafSetProtocol lsProtocol;
    private StandardRouteSetProtocol rsProtocol;
    private StandardJoinProtocol jProtocol;

    private static final int rtMax = 8;
    private static final int lSetSize = 12;
  
    public DirectPastryNodeFactory() {
	nidFactory = new RandomNodeIdFactory();

	simulator = new EuclideanNetwork();
    }
    
    public void constructNode() {
	nodeId = nidFactory.generateNodeId();
	
	lhandle = new ProxyNodeHandle(nodeId);
		
	secureMan = new DirectSecurityManager(simulator);
	msgDisp = new MessageDispatch();

	routeTable = new RoutingTable(lhandle, rtMax);
	leafSet = new LeafSet(lhandle, lSetSize);
		
	router = new StandardRouter(lhandle, routeTable, leafSet);
		
	lsProtocol = new StandardLeafSetProtocol(lhandle, secureMan, leafSet, routeTable);
	rsProtocol = new StandardRouteSetProtocol(lhandle, secureMan, routeTable);
	jProtocol = new StandardJoinProtocol(lhandle, secureMan, routeTable, leafSet);

	simulator.registerNodeId(nodeId);

	msgDisp.registerReceiver(router.getAddress(), router);
	msgDisp.registerReceiver(lsProtocol.getAddress(), lsProtocol);
	msgDisp.registerReceiver(rsProtocol.getAddress(), rsProtocol);
	msgDisp.registerReceiver(jProtocol.getAddress(), jProtocol);
    }
        
    public NodeId getNodeId() { return nodeId; }

    public PastrySecurityManager getSecurityManager() { return secureMan; }
    
    public MessageDispatch getMessageDispatch() { return msgDisp; }

    public NetworkSimulator getNetworkSimulator() { return simulator; }

    public LeafSet getLeafSet() { return leafSet; }

    public RoutingTable getRouteSet() { return routeTable; }

    public void doneWithNode(PastryNode pnode) {
	lhandle.setProxy(pnode);
	secureMan.setLocalPastryNode(pnode);
	pnode.setLocalHandle(lhandle);

	nodeId = null;
	secureMan = null;
	msgDisp = null;	
	lhandle = null;
    }
}


