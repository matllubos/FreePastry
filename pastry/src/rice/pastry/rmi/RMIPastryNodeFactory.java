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

package rice.pastry.rmi;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.standard.*;
import rice.pastry.routing.*;
import rice.pastry.leafset.*;

import java.util.*;
import java.rmi.RemoteException;
import java.rmi.Naming;

/**
 * Pastry node factory for RMI-linked nodes.
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 * @author Sitaram Iyer
 */

public class RMIPastryNodeFactory implements PastryNodeFactory
{
    private RandomNodeIdFactory nidFactory;

    private static final int rtMax = 8;
    private static final int lSetSize = 24;

    /**
     * Large value means infrequent, 0 means never
     */
    private static final int leafSetMaintFreq = 100;
    private static final int routeSetMaintFreq = 300;

    int port;
  
    /**
     * Constructor.
     *
     * @param p RMI registry port.
     */
    public RMIPastryNodeFactory(int p) {
	nidFactory = new RandomNodeIdFactory();
	port = p;
    }

    /**
     * Makes many policy choices and manufactures a new RMIPastryNode.
     * Creates a series of artifacts to adorn the node, like a security
     * manager, a leafset, etc. with hand-picked parameters like the leaf
     * set size. Finally calls the respective setElements to pass these on
     * to the {,RMI,Direct}PastryNode as appropriate, and then calls
     * node.doneNode() (which internally performs mechanisms like exporting
     * the node and notifying applications).
     *
     * @param bootstrap Node handle to bootstrap from.
     */
    public PastryNode newNode(NodeHandle bootstrap) {

	NodeId nodeId = nidFactory.generateNodeId();
	RMIPastryNode pn = new RMIPastryNode(nodeId);
	
	RMINodeHandle localhandle = new RMINodeHandle(null, nodeId);
	localhandle.setLocalNode(pn);

	RMINodeHandlePool handlepool = new RMINodeHandlePool();
	localhandle = handlepool.coalesce(localhandle); // add ourselves to pool

	RMIPastrySecurityManager secureMan =
	    new RMIPastrySecurityManager(localhandle, handlepool);
	MessageDispatch msgDisp = new MessageDispatch();

	RoutingTable routeTable = new RoutingTable(localhandle, rtMax);
	LeafSet leafSet = new LeafSet(localhandle, lSetSize);

	StandardRouter router =
	    new StandardRouter(localhandle, routeTable, leafSet);
	StandardLeafSetProtocol lsProtocol =
	    new StandardLeafSetProtocol(localhandle, secureMan, leafSet, routeTable);
	StandardRouteSetProtocol rsProtocol =
	    new StandardRouteSetProtocol(localhandle, secureMan, routeTable);
	StandardJoinProtocol jProtocol =
	    new StandardJoinProtocol(localhandle, secureMan, routeTable, leafSet);

	msgDisp.registerReceiver(router.getAddress(), router);
	msgDisp.registerReceiver(lsProtocol.getAddress(), lsProtocol);
	msgDisp.registerReceiver(rsProtocol.getAddress(), rsProtocol);
	msgDisp.registerReceiver(jProtocol.getAddress(), jProtocol);

	pn.setElements(localhandle, secureMan, msgDisp, leafSet, routeTable,
		       leafSetMaintFreq, routeSetMaintFreq);
	pn.setRMIElements(handlepool, port);
	secureMan.setLocalPastryNode(pn);

	pn.doneNode(bootstrap);

	return pn;
    }
}

