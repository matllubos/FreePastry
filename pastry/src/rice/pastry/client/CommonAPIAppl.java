/*************************************************************************

"Free Pastry" Peer-to-Peer Application Development Substrate 

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

package rice.pastry.client;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.pastry.leafset.*;

import java.util.*;

/**
 * CommonAPIAppl is an abstract class that all new applications should 
 * extend.  It provides the common KBR API defined in
 *
 * "Towards a Common API for Structured Peer-to-Peer Overlays." Frank
 * Dabek, Ben Zhao, Peter Druschel, John Kubiatowicz and Ion
 * Stoica. In Proceedings of the 2nd International Workshop on
 * Peer-to-peer Systems (IPTPS'03) , Berkeley, CA, February 2003.
 *
 * @version $Id$
 *
 * @author Peter Druschel */

public abstract class CommonAPIAppl extends PastryAppl
{
    
    /**
     * Constructor.
     *
     * @param pn the pastry node that the application attaches to.
     */
    
    public CommonAPIAppl(PastryNode pn) {
	super(pn);
    }


    // API methods to be invoked by applications

    /**
     * This operation forwards a message towards the root of
     * key.  The optional hint argument specifies a node
     * that should be used as a first hop in routing the message. A
     * good hint, e.g. one that refers to the key's current root, can
     * result in the message being delivered in one hop; a bad hint
     * adds at most one extra hop to the route. Either K or hint may
     * be NULL, but not both.  The operation provides a best-effort
     * service: the message may be lost, duplicated, corrupted, or
     * delayed indefinitely.
     *
     *
     * @param key the key
     * @param msg the message to deliver.
     * @param hint the hint
     */
    public void route(Id key, Message msg, NodeHandle hint) {
	if (Log.ifp(8)) System.out.println("[" + thePastryNode + "] route " + msg + " to " + key);

	RouteMessage rm = new RouteMessage(key, msg, hint, getAddress());
	thePastryNode.receiveMessage(rm);
    }
    

    /**
     * This method produces a list of nodes that can be used as next
     * hops on a route towards key, such that the resulting route
     * satisfies the overlay protocol's bounds on the number of hops
     * taken.  
     * If safe is true, the expected fraction of faulty
     * nodes in the list is guaranteed to be no higher than the
     * fraction of faulty nodes in the overlay; if false, the set may
     * be chosen to optimize performance at the expense of a
     * potentially higher fraction of faulty nodes. This option allows
     * applications to implement routing in overlays with byzantine
     * node failures. Implementations that assume fail-stop behavior
     * may ignore the safe argument.  The fraction of faulty
     * nodes in the returned list may be higher if the safe
     * parameter is not true because, for instance, malicious nodes
     * have caused the local node to build a routing table that is
     * biased towards malicious nodes~\cite{Castro02osdi}.
     *
     * @param key the message's key
     * @param num the maximal number of next hops nodes requested
     * @param safe 
     * @return the nodehandle set
     */

    public NodeSet local_lookup(Id key, int num, boolean safe) {
	// safe ignored until we have the secure routing support

	// get the nodes from the routing table
	return getRoutingTable().alternateRoutes(key, num);
    }


    /**
     * This method produces an unordered list of nodehandles that are
     * neighbors of the local node in the ID space. Up to num
     * node handles are returned.
     *
     * @param num the maximal number of nodehandles requested
     * @return the nodehandle set
     */

    public NodeSet neighborSet(int num) {
	return replicaSet(getNodeId(), num);
    }


    /**
     * This method returns an ordered set of nodehandles on which
     * replicas of the object with key can be stored. The call returns
     * nodes with a rank up to and including max_rank.  If max_rank
     * exceeds the implementation's maximum replica set size, then its
     * maximum replica set is returned.  The returned nodes may be
     * used for replicating data since they are precisely the nodes
     * which become roots for the key when the local node fails.
     *
     * @param key the key
     * @param max_rank the maximal number of nodehandles returned
     * @return the replica set
     */

    public NodeSet replicaSet(Id key, int max_rank) {
	return getLeafSet().replicaSet(key, max_rank);
    }

   
    /**
     * This method provides information about ranges of keys for which
     * the node n is currently a r-root. The operations returns
     * false if the range could not be determined, true
     * otherwise. It is an error to query the range of a node not
     * present in the neighbor set as returned by the update
     * upcall or the neighborSet call.  Certain implementations
     * may return an error if $r$ is greater than zero. $[lkey, rkey]$
     * denotes an inclusive range of key values.
     *
     * Some implementations may have multiple, disjoint ranges of keys
     * for which a given node is responsible. The parameter lkey
     * allows the caller to specify which region should be returned.
     * If the node referenced by n is responsible for key lkey, then
     * the resulting range includes lkey. Otherwise, the result is the
     * nearest range clockwise from lkey for which $N$ is responsible.
     *
     * @param n nodeHandle of the node whose range is being queried
     * @param r the rank
     * @param range the range of keys being queried and returned
     * @return false if range could not be determined for the given node and rank, true otherwise
     */
    boolean range(NodeHandle n, int r, IdRange range) {
	return false;
    }



    /*
     * upcall methods, to be overridden by the derived application object 
     *
     * Applications process messages by executing code in
     * upcall methods that are invoked by the KBR routing system at nodes
     * along a message's path and at its root.  To permit event-driven
     * implementations, upcall handlers must not block and should not
     * perform long-running computations.
     */


    /**
     * Returns the address of this application.
     *
     * @return the address.
     */
    
    public abstract Address getAddress();

    /**
     * Returns the credentials of this application.
     *
     * @return the credentials.
     */

    public abstract Credentials getCredentials();

    /**
     * Called by pastry when a message arrives for this application.
     *
     * @param msg the message that is arriving.
     */

    public abstract void deliver(Id key, Message msg);

    /**
     * Called by pastry when a message is enroute and is passing through this node.  If this
     * method is not overridden, the default behaviour is to let the message pass through.
     *
     * @param msg the message that is passing through.
     * @param key the key
     * @param nextHop the default next hop for the message.
     *
     * @return true if the message should be routed, false if the message should be cancelled.
     */
     
    public void forward(Id key, Message msg, NodeHandle nextHopNode) {
	return;
    }
    
    /**
     * Called by pastry when the neighbor set changes.
     *
     * @param nh the handle of the node that was added or removed.
     * @param wasAdded true if the node was added, false if the node was removed.
     */

    public void update(NodeHandle nh, boolean joined) {}


    /**
     * Invoked when the Pastry node has joined the overlay network and
     * is ready to send and receive messages
     */
    
    public void notifyReady() {}

}


