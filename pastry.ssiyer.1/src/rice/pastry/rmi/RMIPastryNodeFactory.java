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
    private RMINodeHandle rmilocalhandle;

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
	rmilocalhandle = new RMINodeHandle(rmilocalnode, nodeId /* local! */);
	rmilocalhandle.setLocalHandle(rmilocalhandle); // itself!

	handlepool = new RMINodeHandlePool();
	handlepool.coalesce(rmilocalhandle); // add ourselves to pool

	secureMan = new RMIPastrySecurityManager(rmilocalhandle, handlepool);
	msgDisp = new MessageDispatch();

	routeTable = new RoutingTable(rmilocalhandle, rtMax);
	leafSet = new LeafSet(nodeId, lSetSize);

	router = new StandardRouter(rmilocalhandle, routeTable, leafSet);

	lsProtocol = new StandardLeafSetProtocol(rmilocalhandle, secureMan, leafSet, routeTable);
	rsProtocol = new StandardRouteSetProtocol(rmilocalhandle, secureMan, routeTable);
	jProtocol = new StandardJoinProtocol(rmilocalhandle, secureMan, routeTable, leafSet);

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
	secureMan.setLocalPastryNode(pnode);
	rmilocalnode.setLocalPastryNode(pnode);

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
	rmilocalhandle = null;
    }
}

