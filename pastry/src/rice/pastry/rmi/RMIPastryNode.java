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
import rice.pastry.leafset.*;
import rice.pastry.routing.*;
import rice.pastry.messaging.*;
import rice.pastry.join.*;
import rice.pastry.client.*;

import java.util.*;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.*;

/**
 * An RMI-exported Pastry node. Its remote interface is exported over RMI.
 *
 * @version $Id$
 *
 * @author Sitaram Iyer
 */

public class RMIPastryNode extends PastryNode
	implements RMIRemoteNodeI
{
    private RMIRemoteNodeI remotestub;
    private RMINodeHandlePool handlepool;
    private int port;

    /**
     * Large value (in seconds) means infrequent, 0 means never.
     */
    private int leafSetMaintFreq, routeSetMaintFreq;

    private LinkedList queue;
    private int count;

    private class MsgHandler implements Runnable {
	public void run() {
	    while (true) {
		Message msg = null;
		synchronized (queue) {
		    while (count == 0) {
			try {
			    queue.wait();
			} catch (InterruptedException e) {}
		    }

		    try {
			msg = (Message) queue.removeFirst();
			count--;
		    } catch (NoSuchElementException e) {
			System.out.println("no msg despite count = " + count);
			continue;
		    }
		}

		/*
		 * The sender of this message is alive. So if we have a
		 * handle in our pool with this Id, then it should be
		 * reactivated.
		 */
		NodeId sender = msg.getSenderId();
		if (Log.ifp(6))
		    System.out.println("received " +
				       (msg instanceof RouteMessage ? "route" : "direct")
				       + " msg from " + sender + ": " + msg);
		if (sender != null) handlepool.activate(sender);

		receiveMessage(msg);
	    }
	}
    }

    private class MaintThread implements Runnable {
	public void run() {

	    int leaftime = 0, routetime = 0, slptime;
	    
	    if (leafSetMaintFreq == 0)
		slptime = routeSetMaintFreq;
	    else if (routeSetMaintFreq == 0)
		slptime = leafSetMaintFreq;
	    else if (leafSetMaintFreq < routeSetMaintFreq)
		slptime = leafSetMaintFreq;
	    else
		slptime = routeSetMaintFreq;

	    // Assumes one of leafSetMaintFreq and routeSetMaintFreq is a
	    // multiple of the other. Generally true; else it approximates
	    // the larger one to the nearest upward multiple.

	    while (true) {
		try {
		    Thread.sleep(1000 * slptime);
		} catch (InterruptedException e) {}

		leaftime += slptime;
		routetime += slptime;

		if (leafSetMaintFreq != 0 && leaftime >= leafSetMaintFreq) {
		    leaftime = 0;
		    receiveMessage(new InitiateLeafSetMaintenance());
		}

		if (routeSetMaintFreq != 0 && routetime >= routeSetMaintFreq) {
		    routetime = 0;
		    receiveMessage(new InitiateRouteSetMaintenance());
		}
	    }
	}
    }

    /**
     * Constructor
     */
    public RMIPastryNode(NodeId id) {
	super(id);
	remotestub = null;
	handlepool = null;
	queue = new LinkedList();
	count = 0;
    }

    /**
     * accessor method for elements in RMIPastryNode, called by
     * RMIPastryNodeFactory.
     *
     * @param hp Node handle pool
     * @param p RMIregistry port
     * @param lsmf Leaf set maintenance frequency. 0 means never.
     * @param rsmf Route set maintenance frequency. 0 means never.
     */
    public void setRMIElements(RMINodeHandlePool hp, int p, int lsmf, int rsmf) {
	handlepool = hp;
	port = p;
	leafSetMaintFreq = lsmf;
	routeSetMaintFreq = rsmf;
    }

    /**
     * Called after the node is initialized.
     *
     * @param hp Node handle pool
     */
    public void doneNode(NodeHandle bootstrap) {

	new Thread(new MsgHandler()).start();
	if (leafSetMaintFreq > 0 || routeSetMaintFreq > 0)
	    new Thread(new MaintThread()).start();

	try {
	    remotestub = (RMIRemoteNodeI) UnicastRemoteObject.exportObject(this);
	} catch (RemoteException e) {
	    System.out.println("Unable to acquire stub for Pastry node: " + e.toString());
	}

	((RMINodeHandle)localhandle).setRemoteNode(remotestub);

	initiateJoin(bootstrap);
    }

    /**
     * Called from PastryNode when the join succeeds, whereupon it rebinds
     * the node into the RMI registry. Happens after the registry lookup, so
     * the node never ends up discovering itself.
     */
    protected final void nodeIsReady() {
	try {
	    Naming.rebind("//:" + port + "/Pastry", remotestub);
	} catch (Exception e) {
	    System.out.println("Unable to bind Pastry node in rmiregistry: " + e.toString());
	}
    }

    /**
     * Proxies to the local node to accept a message. For synchronization
     * purposes, it only adds the message to the queue and signals the
     * message handler thread.
     */
    public void remoteReceiveMessage(Message msg) {
	synchronized (queue) {
	    queue.add(msg);
	    count++;
	    queue.notify();
	}
    }

//    /**
//     * Testing purposes only!
//     */
//    public void KILLNODE() {
//	try {
//	    UnicastRemoteObject.unexportObject(this, true); // force
//	} catch (NoSuchObjectException e) {
//	    System.out.println("Unable to unbind Pastry node from rmiregistry: " + e.toString());
//	}
//    }
}
