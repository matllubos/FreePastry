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
import rice.pastry.dist.*;

import java.util.*;
import java.rmi.RemoteException;
import java.rmi.RMISecurityManager;
import java.rmi.Naming;
import java.rmi.registry.*;
import java.net.*;

/**
 * Pastry node factory for RMI-linked nodes.
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 * @author Sitaram Iyer
 */

public class RMIPastryNodeFactory extends DistPastryNodeFactory {
  public static int NUM_ATTEMPTS = 2;

  private NodeIdFactory nidFactory;
  private int port;

  private static final int rtMax = 8;
  private static final int lSetSize = 24;

  /**
   * Large period (in seconds) means infrequent, 0 means never.
   */
  private static final int leafSetMaintFreq = 60;
  private static final int routeSetMaintFreq = 15*60;


    /**
     * Instance of RMI registry ever-created when using this
     * factory.
     */

    public static Registry rmiRegistry = null;


  /**
   * Constructor.
   *
   * @param p RMI registry port.
   */
  public RMIPastryNodeFactory(NodeIdFactory nf, int p) {
    nidFactory = nf;
    port = p;

    if( rmiRegistry == null){
	// set RMI security manager
	if (System.getSecurityManager() == null)
	    System.setSecurityManager(new RMISecurityManager());
	
	// start RMI registry
	try {
	    rmiRegistry = java.rmi.registry.LocateRegistry.createRegistry(port);
	} catch (Exception e) {
	    System.out.println("Error starting RMI registry: " + e);
	}
    }

  }


  /**
   * Specified by the DistPastryNodeFactory class.  The first looks on the
   * local machine (at the port p) to determine if there is an node bound
   * there.  If so, it retrieves the rmiNodeHanlde from that node.  If not,
   * this method then looks at the address address to see if there is a
   * pastry node bound there.
   *
   * @param address The address to look for the node.
   * @return A NodeHandle cooresponding to the remote node at address, or null
   *         if none is found.
   */
  public NodeHandle generateNodeHandle(InetSocketAddress address) {
    RMIRemoteNodeI bsnode = null;

    System.out.println("RMI: Attempting to locate bootstrap node " + address);

    for (int i = 1; bsnode == null && i <= NUM_ATTEMPTS; i++) {
      try {
        bsnode = (RMIRemoteNodeI) Naming.lookup("//" + address.getHostName()
						+ ":" + address.getPort() + "/Pastry");
      } catch (Exception e) {
        System.out.println("Unable to find bootstrap node on "
			   + address
			   + " (attempt " + i + "/" + NUM_ATTEMPTS + ")");
      }

      if ((bsnode == null) && (i != NUM_ATTEMPTS))
        pause(1000);
    }

    // grab node id
    NodeId bsid = null;
    if (bsnode != null) {
      try {
        bsid = bsnode.getNodeId();
      } catch (RemoteException e) {
        System.out.println("[rmi] Unable to get remote node id: " + e.toString());
        bsnode = null;
      }
    }

    // build node handle
    RMINodeHandle bshandle = null;
    if (bsid != null)
      bshandle = new RMINodeHandle(bsnode, bsid);
    else
      System.out.println("Couldn't find a bootstrap node, starting a new ring...");

    return bshandle;
  }

  /**
   * Pauses the current thread for ms milliseconds.
   *
   * @param ms The number of milliseconds to pause.
   */
  public synchronized void pause(int ms) {
    System.out.println("waiting for " + (ms/1000) + " sec");
    try { wait(ms); } catch (InterruptedException e) {}
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
  public PastryNode newNode(final NodeHandle bootstrap) {
    NodeId nodeId = nidFactory.generateNodeId();

    final RMIPastryNode pn = new RMIPastryNode(nodeId);

    RMINodeHandle localhandle = new RMINodeHandle(null, nodeId);

    RMINodeHandlePool handlepool = new RMINodeHandlePool();
    localhandle = (RMINodeHandle) handlepool.coalesce(localhandle); // add ourselves to pool

    RMIPastrySecurityManager secureMan =
      new RMIPastrySecurityManager(localhandle, handlepool);
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

    msgDisp.registerReceiver(router.getAddress(), router);
    msgDisp.registerReceiver(lsProtocol.getAddress(), lsProtocol);
    msgDisp.registerReceiver(rsProtocol.getAddress(), rsProtocol);
    msgDisp.registerReceiver(jProtocol.getAddress(), jProtocol);

    pn.setElements(localhandle, secureMan, msgDisp, leafSet, routeTable);
    pn.setRMIElements(handlepool, port, leafSetMaintFreq, routeSetMaintFreq);
    secureMan.setLocalPastryNode(pn);

    if (bootstrap != null)
      bootstrap.setLocalNode(pn);

    localhandle.setLocalNode(pn);

    // launch thread to handle the sockets
    Thread t = new Thread("Thread for node " + nodeId) {
      public void run() {
        pn.doneNode(bootstrap);
      }
    };

    t.start();

    return pn;
  }
}
