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

package rice.pastry.client;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;

import java.util.*;

/**
 * A Pastry client is an abstract class that every client application
 * extends.  This is the Pastry API.
 *
 * @author Andrew Ladd
 */

public abstract class PastryClient implements MessageReceiver 
{
    // private block

    private PastryNode thePastryNode;

    private class LeafSetObserver implements Observer {
	public void update(Observable o, Object arg) {
	    NodeSetUpdate nsu = (NodeSetUpdate) arg;

	    NodeHandle handle = nsu.handle();
	    boolean wa = nsu.wasAdded();

	    leafSetChange(handle.getNodeId(), wa);
	}
    }

    private class RouteSetObserver implements Observer {
	public void update(Observable o, Object arg) {
	    NodeSetUpdate nsu = (NodeSetUpdate) arg;

	    NodeHandle handle = nsu.handle();
	    boolean wa = nsu.wasAdded();

	    routeSetChange(handle.getNodeId(), wa);
	}
    }

    // constructor
    
    /**
     * Constructor.
     *
     * @param pn the pastry node that client will attach to.
     */
    
    public PastryClient(PastryNode pn) {
	thePastryNode = pn;
	
	thePastryNode.registerClient(this);

	thePastryNode.addLeafSetObserver(new LeafSetObserver());
	thePastryNode.addRouteSetObserver(new RouteSetObserver());
    }

    // internal methods


    /**
     * Gets the node id associated with this client.
     *
     * @return the node id.
     */

    public final NodeId getNodeId() { return thePastryNode.getNodeId(); }

    /**
     * Registers a message receiver with the pastry node.  This binds the given address 
     * to a message receiver.  This binding is certified by the given credentials.  Messages
     * that are delivered to this node with the given address as a destination are forwarded
     * to the supplied receiver.
     *
     * @param cred credentials which verify the binding
     * @param addr an address
     * @param mr a message receiver which will be bound the address.
     */

    public final void registerReceiver(Credentials cred, Address addr, MessageReceiver mr) { 
	thePastryNode.registerReceiver(cred, addr, mr);
    }

    /**
     * Sends a message directly to the local pastry node.
     *
     * @param msg a message.
     */

    public final void sendMessage(Message msg) { thePastryNode.receiveMessage(msg); }

    /**
     * Called by pastry to deliver a message to this client.  Not to be overridden.
     *
     * @param msg the message that is arriving.
     */

    public final void receiveMessage(Message msg) {
	if (msg instanceof RouteMessage) {
	    RouteMessage rm = (RouteMessage) msg;

	    if (enrouteMessage(rm.unwrap(), rm.getTarget(), rm.nextHop.getNodeId(), rm.getOptions()))
		rm.routeMessage(thePastryNode.getNodeId());
	}
	else messageForClient(msg);
    }

    // useful methods

    /**
     * Routes a message to a given node id.
     *
     * @param target the node id to route to.
     * @param msg the message to deliver.
     * @param cred credentials which verify the authenticity of the message.
     */

    public void routeMessage(NodeId target, Message msg, Credentials cred) {
	RouteMessage rm = new RouteMessage(target, msg, cred);

	thePastryNode.receiveMessage(rm);
    }

    /**
     * Routes a message to a given node id.
     *
     * @param target the node id to route to.
     * @param msg the message to deliver.
     * @param cred credentials which verify the authenticity of the message.
     * @param opt send options which describe how the message is to be routed.
     */

    public void routeMessage(NodeId target, Message msg, Credentials cred, SendOptions opt) {
	RouteMessage rm = new RouteMessage(target, msg, cred, opt);

	thePastryNode.receiveMessage(rm);
    }

    /**
     * Routes a message to a given node id delivering the message to a client with
     * same address as this client at each hop along the way.
     *
     * @param target the node id to route to.
     * @param msg the message to deliver.
     * @param cred credentials which verify the authenticity of the message.
     */

    public void sendEnrouteMessage(NodeId target, Message msg, Credentials cred) {
	RouteMessage rm = new RouteMessage(target, msg, cred, getAddress());

	thePastryNode.receiveMessage(rm);
    }

    /**
     * Routes a message to a given node id delivering the message to a client with
     * same address as this client at each hop along the way.
     *
     * @param target the node id to route to.
     * @param msg the message to deliver.
     * @param cred credentials which verify the authenticity of the message.
     * @param opt send options which describe how the message is to be routed.
     */

    public void sendEnrouteMessage(NodeId target, Message msg, Credentials cred, SendOptions opt) {
	RouteMessage rm = new RouteMessage(target, msg, cred, opt, getAddress());

	thePastryNode.receiveMessage(rm);
    }
    
    // abstract interface
    
    /**
     * Returns the address of this client.
     *
     * @return the address.
     */
    
    public abstract Address getAddress();

    /**
     * Returns the credentials of this client.
     *
     * @return the credentials.
     */

    public abstract Credentials getCredentials();

    /**
     * Called by pastry when a message arrives for this client.
     *
     * @param msg the message that is arriving.
     */

    public abstract void messageForClient(Message msg);

    /**
     * Called by pastry when a message is enroute and is passing through this node.  If this
     * method is not overridden, the default behaviour is to let message pass through.
     *
     * @param msg the message that is passing through.
     * @param target the final destination node id.
     * @param nextHop the next hop for the message.
     * @param opt the send options the message was sent with.
     *
     * @return true if the message should be routed, false if the message should be cancelled.
     */
     
    public boolean enrouteMessage(Message msg, NodeId target, NodeId nextHop, SendOptions opt) {
	return true;
    }
    
    /**
     * Called by pastry when the leaf set changes.
     *
     * @param nid the node id.
     * @param wasAdded true if the node was added, false if the node was removed.
     */

    public void leafSetChange(NodeId nid, boolean wasAdded) {}

    /**
     * Called by pastry when the route set changes.
     *
     * @param nid the node id.
     * @param wasAdded true if the node was added, false if the node was removed.
     */

    public void routeSetChange(NodeId nid, boolean wasAdded) {}
}
