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

package rice.pastry;

import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.client.*;
import rice.pastry.leafset.*;
import rice.pastry.routing.*;
import rice.pastry.join.*;

import java.util.*;

/**
 * A Pastry node is single entity in the pastry network.
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 */

public abstract class PastryNode implements MessageReceiver {

    private NodeId myNodeId;
    private PastrySecurityManager mySecurityManager;
    private MessageDispatch myMessageDispatch;
    private LeafSet leafSet;
    private RoutingTable routeSet;
    protected NodeHandle localhandle;
    private boolean ready;
    protected Vector apps;
    
    /**
     * Constructor, with NodeId. Need to set the node's ID before this node
     * is inserted as localHandle.localNode.
     */
    protected PastryNode(NodeId id) {
	myNodeId = id;
	ready = false;
	apps = new Vector();
    }
    
    /**
     * Combined accessor method for various members of PastryNode. These are
     * generated by node factories, and assigned here.
     *
     * Other elements specific to the wire protocol are assigned via methods
     * set{RMI,Direct}Elements in the respective derived classes.
     *
     * @param lh Node handle corresponding to this node.
     * @param sm Security manager.
     * @param md Message dispatcher.
     * @param ls Leaf set.
     * @param rt Routing table.
     */
    public final void setElements(NodeHandle lh,
				  PastrySecurityManager sm,
				  MessageDispatch md,
				  LeafSet ls,
				  RoutingTable rt) {
	localhandle = lh;
	mySecurityManager = sm;
	myMessageDispatch = md;
	leafSet = ls;
	routeSet = rt;
    }
    
    public final NodeHandle getLocalHandle() { return localhandle; }
    
    public final NodeId getNodeId() { return myNodeId; }
    
    public final boolean isReady() { return ready; }
    
    /**
     * Overridden by derived classes, and invoked when the node has
     * joined successfully.
     */
    
    protected abstract void nodeIsReady();
    
    public final void setReady() {
	//System.out.println("setready() called on pastry node" + getNodeId());  

	// It is possible to have the setReady() invoked more than once if the message
	// denoting the termination of join protocol is duplicated.
	if(isReady()) return;

	ready = true;
	nodeIsReady();
	
	// notify applications
	Iterator it = apps.iterator();
	while (it.hasNext())
	    ((PastryAppl)(it.next())).notifyReady();
	
	// signal any apps that might be waiting for the node to get ready
	synchronized (this) { notifyAll(); }
    }
    

    /**
     * Called by the layered Pastry application to check if the local
     * pastry node is the one that is currently closest to the object key id.
     *
     * @param key
     * the object key id
     *
     * @return true if the local node is currently the closest to the key.
     */
    public final boolean isClosest(NodeId key) {
	
	if(leafSet.mostSimilar(key) == 0)
	    return true;
	else
	    return false;
    }
    
    public final LeafSet getLeafSet() { return leafSet; }
    
    public final RoutingTable getRoutingTable() { return routeSet; }
    
    /**
     * Sends an InitiateJoin message to itself.
     *
     * @param bootstrap Node handle to bootstrap with.
     */
    public final void initiateJoin(NodeHandle bootstrap) {
	if (bootstrap != null)
	    this.receiveMessage(new InitiateJoin(bootstrap));
	else
	    setReady(); // no bootstrap node, so ready immediately
    }
    
    /**
     * Add a leaf set observer to the Pastry node.
     *
     * @param o the observer.
     */

    public final void addLeafSetObserver(Observer o) { leafSet.addObserver(o); }
    
    /**
     * Delete a leaf set observer from the Pastry node.
     *
     * @param o the observer.
     */
    
    public final void deleteLeafSetObserver(Observer o) { leafSet.deleteObserver(o); }
    
    /**
     * Add a route set observer to the Pastry node.
     *
     * @param o the observer.
     */
    
    public final void addRouteSetObserver(Observer o) { routeSet.addObserver(o); }
    
    
    /**
     * Delete a route set observer from the Pastry node.
     *
     * @param o the observer.
     */
    
    public final void deleteRouteSetObserver(Observer o) { routeSet.deleteObserver(o); }
    
    /**
     * message receiver interface. synchronized so that the external message
     * processing thread and the leafset/route maintenance thread won't
     * interfere with application messages.
     */
    public final synchronized void receiveMessage(Message msg) {
	LocalNodeI.pending.setPending(msg.getStream(), this);
	
	if (mySecurityManager.verifyMessage(msg) == true)
	    myMessageDispatch.dispatchMessage(msg);
    }
    
    /**
     * Registers a message receiver with this Pastry node.
     *
     * @param cred the credentials.
     * @param address the address that the receiver will be at.
     * @param receiver the message receiver.
     */
    
    public final void registerReceiver(Credentials cred, Address address, MessageReceiver receiver) {
	if (mySecurityManager.verifyAddressBinding(cred, address) == true)
	    myMessageDispatch.registerReceiver(address, receiver);
	else throw new Error("security failure");
    }
    
    /**
     * Registers an application with this pastry node.
     *
     * @param app the application
     */
    
    public final void registerApp(PastryAppl app) {
	if(isReady())
	    app.notifyReady();
	apps.add(app);
    }
    

    /**
     * Schedule the specified message to be sent to the local node after a specified delay.
     * Useful to provide timeouts.
     *
     * @param msg a message that will be delivered to the local node after the specified delay
     * @param delay time in milliseconds before message is to be delivered
     * @return the scheduled event object; can be used to cancel the message
     */
    public abstract ScheduledMessage scheduleMsg(Message msg, long delay);
    
    
    /**
     * Schedule the specified message for repeated fixed-delay delivery to the local node,  
     * beginning after the specified delay. Subsequent executions take place at approximately regular 
     * intervals separated by the specified period. Useful to initiate periodic tasks.
     *
     * @param msg a message that will be delivered to the local node after the specified delay
     * @param delay time in milliseconds before message is to be delivered
     * @param period time in milliseconds between successive message deliveries
     * @return the scheduled event object; can be used to cancel the message 
     */
    public abstract ScheduledMessage scheduleMsg(Message msg, long delay, long period);
    

    /**
     * Schedule the specified message for repeated fixed-rate delivery to the local node,  
     * beginning after the specified delay. Subsequent executions take place at approximately regular 
     * intervals, separated by the specified period.
     *
     * @param msg a message that will be delivered to the local node after the specified delay
     * @param delay time in milliseconds before  message is to be delivered
     * @param period time in milliseconds between successive message deliveries
     * @return the scheduled event object; can be used to cancel the message 
     */
    public abstract ScheduledMessage scheduleMsgAtFixedRate(Message msg, long delay, long period);
    
    
    public String toString() {
	return "Pastry node " + myNodeId.toString();
    }
    
}

