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
    
    private NodeId nodeId;
    private RMIPastrySecurityManager secureMan;
    private MessageDispatch msgDisp;

    private RoutingTable routeTable;
    private LeafSet leafSet;
  
    private StandardRouter router;

    private StandardLeafSetProtocol lsProtocol;
    private StandardRouteSetProtocol rsProtocol;
    private StandardJoinProtocol jProtocol;

    private RMIPastryNodeImpl rmilocalnode;
    private RMINodeHandle localhandle;

    private RMINodeHandlePool handlepool;

    private static final int rtMax = 8;
    private static final int lSetSize = 12;
  
    public RMIPastryNodeFactory() {
	nidFactory = new RandomNodeIdFactory();
    }
    
    public void constructNode() {
	nodeId = nidFactory.generateNodeId();
	
	try {
	    rmilocalnode = new RMIPastryNodeImpl();
	} catch (RemoteException e) {
	    System.out.println("Unable to create RMI Pastry node: " + e.toString());
	}
	localhandle = new RMINodeHandle(rmilocalnode, nodeId);

	handlepool = new RMINodeHandlePool();
	localhandle = handlepool.coalesce(localhandle); // add ourselves to pool
	rmilocalnode.setHandlePool(handlepool);

	secureMan = new RMIPastrySecurityManager(localhandle, handlepool);
	msgDisp = new MessageDispatch();

	routeTable = new RoutingTable(localhandle, rtMax);
	leafSet = new LeafSet(localhandle, lSetSize);

	router = new StandardRouter(localhandle, routeTable, leafSet);

	lsProtocol = new StandardLeafSetProtocol(localhandle, secureMan, leafSet, routeTable);
	rsProtocol = new StandardRouteSetProtocol(localhandle, secureMan, routeTable);
	jProtocol = new StandardJoinProtocol(localhandle, secureMan, routeTable, leafSet);

	msgDisp.registerReceiver(router.getAddress(), router);
	msgDisp.registerReceiver(lsProtocol.getAddress(), lsProtocol);
	msgDisp.registerReceiver(rsProtocol.getAddress(), rsProtocol);
	msgDisp.registerReceiver(jProtocol.getAddress(), jProtocol);
    }
        
    public NodeId getNodeId() { return nodeId; }

    public PastrySecurityManager getSecurityManager() { return secureMan; }
    
    public MessageDispatch getMessageDispatch() { return msgDisp; }

    public LeafSet getLeafSet() { return leafSet; }
    public RoutingTable getRouteSet() { return routeTable; }

    public void doneWithNode(PastryNode pnode) {
	localhandle.setLocalHandle(pnode); // itself!
	rmilocalnode.setLocalPastryNode(pnode);
	secureMan.setLocalPastryNode(pnode);
	pnode.setLocalHandle(localhandle);

	// this bind happens after the registry lookup, so the node never
	// ends up discovering itself
	try {
	    Naming.rebind("//:" + 5009 + "/Pastry", rmilocalnode);
	} catch (Exception e) {
	    System.out.println("Unable to bind Pastry node in rmiregistry: " + e.toString());
	}

	nodeId = null;
	secureMan = null;
	msgDisp = null;	
	rmilocalnode = null;
	localhandle = null;
	handlepool = null;
    }
}

