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

- Neither the name of Rice University (RICE) nor the names of its
contributors may be used to endorse or promote products derived from
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

package rice.pastry.commonapi;

import rice.p2p.commonapi.*;

import rice.pastry.Log;
import rice.pastry.NodeId;
import rice.pastry.PastryNode;
import rice.pastry.client.PastryAppl;
import rice.pastry.security.*;
import rice.pastry.routing.SendOptions;

/**
 * This class serves as gluecode, which allows applications written for the common
 * API to work with pastry.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Peter Druschel
 */
public class PastryEndpoint extends PastryAppl implements rice.p2p.commonapi.Endpoint {

  protected Credentials credentials = new PermissiveCredentials();

  protected Application application;
  
  /**
   * Constructor.
   *
   * @param pn the pastry node that the application attaches to.
   */
  public PastryEndpoint(PastryNode pn, Application application, String instance) {
    super(pn, application.getClass().getName() + instance);

    this.application = application;
  }

  // API methods to be invoked by applications

  /**
   * Returns this node's id, which is its identifier in the namespace.
   *
   * @return The local node's id
   */
  public Id getId() {
    return thePastryNode.getNodeId();
  }
  
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

    PastryEndpointMessage pm = new PastryEndpointMessage(this.getAddress(), msg);
    rice.pastry.routing.RouteMessage rm = new rice.pastry.routing.RouteMessage((rice.pastry.Id) key,
                                                                               pm,
                                                                               (rice.pastry.NodeHandle) hint,
                                                                               getAddress());
    thePastryNode.receiveMessage(rm);
  }

  /**
   * Schedules a message to be delivered to this application after the provided number of
   * milliseconds.
   *
   * @param message The message to be delivered
   * @param delay The number of milliseconds to wait before delivering the message
   */
  public void scheduleMessage(Message message, long delay) {
    PastryEndpointMessage pm = new PastryEndpointMessage(this.getAddress(), message);
    thePastryNode.scheduleMsg(pm, delay);
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
  public NodeHandleSet localLookup(Id key, int num, boolean safe) {
    // safe ignored until we have the secure routing support

    // get the nodes from the routing table
    return getRoutingTable().alternateRoutes((rice.pastry.Id) key, num);
  }

  /**
   * This method produces an unordered list of nodehandles that are
   * neighbors of the local node in the ID space. Up to num
   * node handles are returned.
   *
   * @param num the maximal number of nodehandles requested
   * @return the nodehandle set
   */
  public NodeHandleSet neighborSet(int num) {
    return getLeafSet().neighborSet(num);
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
  public NodeHandleSet replicaSet(Id key, int max_rank) {
    return getLeafSet().replicaSet((rice.pastry.Id) key, max_rank);
  }

  /**
   * This method provides information about ranges of keys for which
   * the node n is currently a r-root. The operations returns null
   * if the range could not be determined. It is an error to query
   * the range of a node not present in the neighbor set as returned
   * by the update upcall or the neighborSet call.
   *
   * Some implementations may have multiple, disjoint ranges of keys
   * for which a given node is responsible (Pastry has two). The
   * parameter key allows the caller to specify which range should
   * be returned.  If the node referenced by n is the r-root for
   * key, then the resulting range includes key. Otherwise, the
   * result is the nearest range clockwise from key for which n is
   * responsible.
   *
   * @param n nodeHandle of the node whose range is being queried
   * @param r the rank
   * @param key the key
   * @param cumulative if true, returns ranges for which n is an i-root for 0<i<=r
   * @return the range of keys, or null if range could not be determined for the given node and rank
   */
  public IdRange range(NodeHandle n, int r, Id key, boolean cumulative) {
    rice.pastry.Id pKey = (rice.pastry.Id) key;
    
    if (cumulative)
      return getLeafSet().range((rice.pastry.NodeHandle) n, r);

    rice.pastry.IdRange ccw = getLeafSet().range((rice.pastry.NodeHandle) n, r, false);
    rice.pastry.IdRange cw = getLeafSet().range((rice.pastry.NodeHandle) n, r, true);

    if (cw == null || ccw.contains(pKey) || pKey.isBetween(cw.getCW(), ccw.getCCW())) return ccw;
    else return cw;
  }

  /**
   * This method provides information about ranges of keys for which
   * the node n is currently a r-root. The operations returns null
   * if the range could not be determined. It is an error to query
   * the range of a node not present in the neighbor set as returned
   * by the update upcall or the neighborSet call.
   *
   * Some implementations may have multiple, disjoint ranges of keys
   * for which a given node is responsible (Pastry has two). The
   * parameter key allows the caller to specify which range should
   * be returned.  If the node referenced by n is the r-root for
   * key, then the resulting range includes key. Otherwise, the
   * result is the nearest range clockwise from key for which n is
   * responsible.
   *
   * @param n nodeHandle of the node whose range is being queried
   * @param r the rank
   * @param key the key
   * @return the range of keys, or null if range could not be determined for the given node and rank
   */
  public IdRange range(NodeHandle n, int r, Id key) {
    return range(n, r, key, false);
  }

  /**
   * Returns a handle to the local node below this endpoint.
   *
   * @return A NodeHandle referring to the local node.
   */
  public NodeHandle getLocalNodeHandle() {
    return thePastryNode.getLocalHandle();
  }

  // Upcall to Application support

  public final void messageForAppl(rice.pastry.messaging.Message msg) {
    if (msg instanceof PastryEndpointMessage) {
      // null for now, when RouteMessage stuff is completed, then it will be different!
      application.deliver(null, ((PastryEndpointMessage) msg).getMessage());
    } else {
      System.out.println("Received unknown message " + msg + " - dropping on floor");
    }
  }

  public final boolean enrouteMessage(Message msg, Id key, NodeId nextHop, SendOptions opt) {
    if (msg instanceof RouteMessage) {
      return application.forward((RouteMessage) msg);
    } else {
      return true;
    }
  }

  public final void leafSetChange(NodeHandle nh, boolean wasAdded) {
    application.update(nh, wasAdded);
  }

  // PastryAppl support
  
  /**
   * Returns the credentials of this application.
   *
   * @return the credentials.
   */
  public Credentials getCredentials() {
    return credentials;
  }

  /**
   * Called by pastry to deliver a message to this client.  Not to be overridden.
   *
   * @param msg the message that is arriving.
   */
  public void receiveMessage(rice.pastry.messaging.Message msg) {
    if (Log.ifp(8)) System.out.println("[" + thePastryNode + "] recv " + msg);

    if (msg instanceof rice.pastry.routing.RouteMessage) {
      rice.pastry.routing.RouteMessage rm = (rice.pastry.routing.RouteMessage) msg;

      // call application
      if (application.forward(rm)) {
        if (rm.nextHop != null) {
          rice.pastry.NodeHandle nextHop = rm.nextHop;

          // if the message is for the local node, deliver it here
          if (getNodeId().equals(nextHop.getNodeId())) {
            PastryEndpointMessage pMsg = (PastryEndpointMessage) rm.unwrap();
            application.deliver(rm.getTarget(), pMsg.getMessage());
          }
          else {
            // route the message
            rm.routeMessage(getNodeId());
          }
        }
      }
    } else {
      // if the message is not a RouteMessage, then it is for the local node and
      // was sent with a PastryAppl.routeMsgDirect(); we deliver it for backward compatibility
      messageForAppl(msg);
    }
  }

}




