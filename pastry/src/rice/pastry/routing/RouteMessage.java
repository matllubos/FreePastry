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

package rice.pastry.routing;

import rice.pastry.commonapi.*;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;

import java.io.*;

/**
 * A route message contains a pastry message that has been wrapped to
 * be sent to another pastry node.
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 */

public class RouteMessage extends Message implements Serializable, rice.p2p.commonapi.RouteMessage {
    private Id target;
    private Message internalMsg;

    private NodeHandle prevNode;
    private transient SendOptions opts;
    private Address auxAddress;
    public transient NodeHandle nextHop;

    /**
     * Constructor.
     *
     * @param target this is id of the node the message will be routed to.
     * @param msg the wrapped message.
     * @param cred the credentials for the message.
     */

    public RouteMessage(Id target, Message msg, Credentials cred) 
    {
	super(new RouterAddress());
	this.target = target;
	internalMsg = msg;
	this.opts = new SendOptions();

	nextHop = null;
    }

    /**
     * Constructor.
     *
     * @param target this is id of the node the message will be routed to.
     * @param msg the wrapped message.
     * @param cred the credentials for the message.
     * @param opts the send options for the message.
     */

    public RouteMessage(Id target, Message msg, Credentials cred, SendOptions opts) 
    {
	super(new RouterAddress());
	this.target = target;
	internalMsg = msg;
	this.opts = opts;
	
	nextHop = null;
    }

    /**
     * Constructor.
     *
     * @param dest the node this message will be routed to
     * @param msg the wrapped message.
     * @param cred the credentials for the message.
     * @param opts the send options for the message.
     * @param aux an auxilary address which the message after each hop.
     */

    public RouteMessage(NodeHandle dest, Message msg, Credentials cred, SendOptions opts, Address aux) 
    {
	super(new RouterAddress());
	this.target = dest.getNodeId();
	internalMsg = msg;
	this.opts = opts;
	nextHop = dest;
	//if (nextHop != null) System.out.println("2. nexthop = " + nextHop.getNodeId());
	auxAddress = aux;
    }

    /**
     * Constructor.
     *
     * @param target this is id of the node the message will be routed to.
     * @param msg the wrapped message.
     * @param cred the credentials for the message.
     * @param aux an auxilary address which the message after each hop.
     */

    public RouteMessage(Id target, Message msg, Credentials cred, Address aux) 
    {
	super(new RouterAddress());
	this.target = target;
	internalMsg = msg;
	this.opts = new SendOptions();

	auxAddress = aux;
	
	nextHop = null;
    }

    /**
     * Constructor.
     *
     * @param target this is id of the node the message will be routed to.
     * @param msg the wrapped message.
     * @param cred the credentials for the message.
     * @param opts the send options for the message.
     * @param aux an auxilary address which the message after each hop.
     */

    public RouteMessage(Id target, Message msg, Credentials cred, SendOptions opts, Address aux) 
    {
	super(new RouterAddress());
	this.target = target;
	internalMsg = msg;
	this.opts = opts;

	auxAddress = aux;
	
	nextHop = null;
    }


    /**
     * Constructor.
     *
     * @param target this is id of the node the message will be routed to.
     * @param msg the wrapped message.
     * @param firstHop the nodeHandle of the first hop destination
     * @param aux an auxilary address which the message after each hop.
     */

    public RouteMessage(Id target, Message msg, NodeHandle firstHop, Address aux) 
    {
	super(new RouterAddress());
	this.target = (Id)target;
	internalMsg = msg;
	this.opts = new SendOptions();
	auxAddress = aux;
	nextHop = firstHop;
    }

    /**
     * Routes the messages if the next hop has been set up.
     *
     * @param localId the node id of the local node.
     *
     * @return true if the message got routed, false otherwise.
     */

    public boolean routeMessage(NodeId localId) {
	if (nextHop == null) return false;
      setSenderId(localId);

	NodeHandle handle = nextHop;
	nextHop = null;

	if (localId.equals(handle.getNodeId())) {
	    //System.out.println("[RTR] " + localId +
	    //"is receiving internal message " + internalMsg);
	} else {
	    //System.out.println("[RTR] " + localId + "is forwarding to nexthop = "
	    //+ handle + " (" + handle.getNodeId() + ")");
	}

	if (localId.equals(handle.getNodeId())) handle.receiveMessage(internalMsg);      
	else handle.receiveMessage(this);

	return true;
    }

    /**
     * Gets the target node id of this message.
     *
     * @return the target node id.
     */
    
    public Id getTarget() { return target; }

    public NodeHandle getPrevNode() { return prevNode; }
    public void setPrevNode(NodeHandle n) { prevNode = n;}
    public NodeHandle getNextHop() { return nextHop; }
    public void setNextHop(NodeHandle nh) { nextHop = nh; }


    /**
     * Get priority
     * 
     * @return the priority of this message.
     */

   public boolean hasPriority() { return internalMsg.hasPriority(); }
    
    /**
     * Get receiver address.
     * 
     * @return the address.
     */

    public Address getDestination() {
	if (nextHop == null || auxAddress == null) return super.getDestination();
	
	return auxAddress;
    }
    
    /**
     * The wrapped message.
     *
     * @return the wrapped message.
     */

    public Message unwrap() { return internalMsg; }
    
    /**
     * Get transmission options.
     *
     * @return the options.
     */

    public SendOptions getOptions() { 
      if (opts == null) {
        opts = new SendOptions();
      }
      return opts; 
    }

    public String toString() {
	String str = "";

	if (Log.ifp(7)) {
	    str += "RouteMessage for target " + target;

	    if (auxAddress != null) str += " with aux address " + auxAddress;

	    //str += "\n";

	    str += ", wraps ";
	    str += internalMsg;

	    if (nextHop != null)
		str += ", nexthop = " + nextHop.getNodeId();
	} else if (Log.ifp(5)) {
	    str += "[ " + internalMsg + " ]";
	}

	return str;
    }

    // Common API Support

    public rice.p2p.commonapi.Id getDestinationId() {
      return getTarget();
    }

    public rice.p2p.commonapi.NodeHandle getNextHopHandle() {
      return nextHop;
    }

    public rice.p2p.commonapi.Message getMessage() {
      return ((PastryEndpointMessage) unwrap()).getMessage();
    }

    public void setDestinationId(rice.p2p.commonapi.Id id) {
      target = (Id) id;
    }

    public void setNextHopHandle(rice.p2p.commonapi.NodeHandle nextHop) {
      nextHop = (NodeHandle) nextHop;
    }

    public void setMessage(rice.p2p.commonapi.Message message) {
      ((PastryEndpointMessage) unwrap()).setMessage(message);
    }
}
