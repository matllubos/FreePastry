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
import rice.pastry.routing.*;
import rice.pastry.messaging.*;

import java.util.*;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.*;

/**
 * An RMI-exported proxy object associated with each Pastry node. Its remote
 * interface is exported over RMI (and not the PastryNode itself, which is
 * RMI-unaware), and acts as a proxy, explicitly calling PastryNode methods.
 *
 * @version $Id$
 *
 * @author Sitaram Iyer
 */

class RMIPastryNodeImpl extends UnicastRemoteObject
	implements RMIPastryNode, Observer
{
    private PastryNode node;
    private RMINodeHandlePool handlepool;
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
		System.out.println("[rmi] received " +
				   (msg instanceof RouteMessage ? "route" : "direct")
				   + " msg from " + sender + ": " + msg);
		if (sender != null) handlepool.activate(sender);
		node.receiveMessage(msg);
	    }
	}
    }

    /**
     * Constructor
     */
    public RMIPastryNodeImpl() throws RemoteException {
	node = null;
	queue = new LinkedList();
	count = 0;
	MsgHandler handler = new MsgHandler();
	new Thread(handler).start();
    }

    /**
     * sets the local Pastry node (local method)
     * @param n the local pastry node that this helper is associated with.
     */
    public void setLocalPastryNode(PastryNode n) { node = n; }

    /**
     * sets the local handle pool (local method)
     * @param hp the handle pool maintained by the local pastry node
     */
    public void setHandlePool(RMINodeHandlePool hp) { handlepool = hp; }

    /**
     * Proxies to the local node to get the local NodeId.
     */
    public NodeId getNodeId() { return node.getNodeId(); }

    /**
     * Proxies to the local node to accept a message.
     */
    public void receiveMessage(Message msg) {
	synchronized (queue) {
	    queue.add(msg);
	    count++;
	    queue.notify();
	}
    }

    /**
     * Synchronously rebinds node into rmi registry.
     */
    public void rmibind() {
	try {
	    Naming.rebind("//:" + 5009 + "/Pastry", this);
	} catch (Exception e) {
	    System.out.println("Unable to bind Pastry node in rmiregistry: " + e.toString());
	}
    }

    /**
     * Observer on leafset. Binds node into rmi registry on any leafset
     * activity. Replaces earlier binding; turns out, this is faster than
     * all nodes using the first virtual node to bootstrap.
     */
    public void update(Observable o, Object arg) {
	// this bind happens after the registry lookup, so the node never
	// ends up discovering itself

	try {
	    Naming.rebind("//:" + 5009 + "/Pastry", this);
	// } catch (AlreadyBoundException e) { // enable for "bind"
	} catch (Exception e) {
	    System.out.println("Unable to bind Pastry node in rmiregistry: " + e.toString());
	}

	node.deleteLeafSetObserver(this);
    }
}
