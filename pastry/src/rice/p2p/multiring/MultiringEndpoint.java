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

package rice.p2p.multiring;

import java.util.*;
import rice.p2p.commonapi.*;

/**
 * @(#) MultiringEndpoint.java
 *
 * This class wraps an endpoint and allows for applications to transparently
 * use multiring functionality.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class MultiringEndpoint implements Endpoint {
  
  /**
   * The multiring node supporting this endpoint
   */
  protected MultiringNode node;
  
  /**
   * The application this endpoint is for
   */
  protected Application application;
  
  /**
   * The node which this mulitring node is wrapping
   */
  protected Endpoint endpoint;
  
  /**
  * Constructor
   *
   * @param node The node to base this node off of
   */
  protected MultiringEndpoint(MultiringNode node, Endpoint endpoint, Application application) {
    this.node = node;
    this.endpoint = endpoint;
    this.application = application;
  }
  
  /**
   * Returns this node's id, which is its identifier in the namespace.
   *
   * @return The local node's id
   */
  public Id getId() {
    return new RingId(node.getRingId(), endpoint.getId());
  }
  
  /**
   * This method makes an attempt to route the message to the root of the given id.
   * The hint handle will be the first hop in the route. If the id field is null, then
   * the message is routed directly to the given node, and delivers the message
   * there.  If the hint field is null, then this method makes an attempt to route
   * the message to the root of the given id.  Note that one of the id and hint fields can
   * be null, but not both.
   *
   * @param id The destination Id of the message.
   * @param message The message to deliver
   * @param hint The first node to send this message to, optional
   */
  public void route(Id id, Message message, NodeHandle hint) {
    RingId mId = (RingId) id;
    MultiringNodeHandle mHint = (MultiringNodeHandle) hint;
          
    if (mId == null) {
      if (mHint.getRingId().equals(node.getRingId())) {
        endpoint.route(null, message, mHint.getHandle());
      } else {
        route(mHint.getId(), message, null);
      }
    } else {
      if (mId.getRingId().equals(node.getRingId())) {
        if ((mHint != null) && (mHint.getRingId().equals(node.getRingId()))) {
          endpoint.route(mId.getId(), message, mHint.getHandle());
        } else {
          endpoint.route(mId.getId(), message, null);
        }
      } else {
        node.getCollection().route(mId, message, getInstance());
      }
    } 
  }
  
  /**
   * This call produces a list of nodes that can be used as next hops on a route towards
   * the given id, such that the resulting route satisfies the overlay protocol's bounds
   * on the number of hops taken.  If the safe flag is specified, then the fraction of
   * faulty nodes returned is no higher than the fraction of faulty nodes in the overlay.
   *
   * @param id The destination id.
   * @param num The number of nodes to return.
   * @param safe Whether or not to return safe nodes.
   */
  public NodeHandleSet localLookup(Id id, int num, boolean safe) {
    return new MultiringNodeHandleSet(node.getRingId(), endpoint.localLookup(((RingId) id).getId(), num, safe));
  }
  
  /**
   * This methods returns an unordered set of nodehandles on which are neighbors of the local
   * node in the id space.  Up to num handles are returned.
   *
   * @param num The number of desired handle to return.
   */
  public NodeHandleSet neighborSet(int num) {
    return new MultiringNodeHandleSet(node.getRingId(), endpoint.neighborSet(num));
  }
  
  /**
   * This methods returns an ordered set of nodehandles on which replicas of an object with
   * a given id can be stored.  The call returns nodes up to and including a node with maxRank.
   *
   * @param id The object's id.
   * @param maxRank The number of desired replicas.
   */
  public NodeHandleSet replicaSet(Id id, int maxRank) {
    return new MultiringNodeHandleSet(node.getRingId(), endpoint.replicaSet(((RingId) id).getId(), maxRank));
  }
  
  /**
   * This methods returns an ordered set of nodehandles on which replicas of an object with
   * a given id can be stored.  The call returns nodes up to and including a node with maxRank.
   * This call also allows the application to provide a remove "center" node, as well as
   * other nodes in the vicinity. 
   *
   * @param id The object's id.
   * @param maxRank The number of desired replicas.
   * @param handle The root handle of the remove set
   * @param set The set of other nodes around the root handle
   */
  public NodeHandleSet replicaSet(Id id, int maxRank, NodeHandle root, NodeHandleSet set) {
    return new MultiringNodeHandleSet(node.getRingId(), endpoint.replicaSet(((RingId) id).getId(), 
                                                                            maxRank,
                                                                            ((MultiringNodeHandle) root).getHandle(),
                                                                            ((MultiringNodeHandleSet) set).getSet()));
  }
  
  /**
   * This operation provides information about ranges of keys for which the node is currently
   * a rank-root. The operations returns null if the range could not be determined, the range
   * otherwise. It is an error to query the range of a node not present in the neighbor set as
   * returned bythe update upcall or the neighborSet call. Certain implementations may return
   * an error if rank is greater than zero. Some protocols may have multiple, disjoint ranges
   * of keys for which a given node is responsible. The parameter lkey allows the caller to
   * specify which region should be returned. If the node referenced by is responsible for key
   * lkey, then the resulting range includes lkey. Otherwise, the result is the nearest range
   * clockwise from lkey for which is responsible.
   *
   * @param handle The handle whose range to check.
   * @param rank The root rank.
   * @param lkey An "index" in case of multiple ranges.
   */
  public IdRange range(NodeHandle handle, int rank, Id lkey) {
    if (lkey != null)
      lkey = ((RingId) lkey).getId();
    
    IdRange result = endpoint.range(((MultiringNodeHandle) handle).getHandle(), rank, lkey);
    
    if (result != null)
      return new MultiringIdRange(node.getRingId(), result);
    else
      return null;
  }
  
  /**
   * This operation provides information about ranges of keys for which the node is currently
   * a rank-root. The operations returns null if the range could not be determined, the range
   * otherwise. It is an error to query the range of a node not present in the neighbor set as
   * returned bythe update upcall or the neighborSet call. Certain implementations may return
   * an error if rank is greater than zero. Some protocols may have multiple, disjoint ranges
   * of keys for which a given node is responsible. The parameter lkey allows the caller to
   * specify which region should be returned. If the node referenced by is responsible for key
   * lkey, then the resulting range includes lkey. Otherwise, the result is the nearest range
   * clockwise from lkey for which is responsible.
   *
   * @param handle The handle whose range to check.
   * @param rank The root rank.
   * @param lkey An "index" in case of multiple ranges.
   * @param cumulative Whether to return the cumulative or single range
   */
  public IdRange range(NodeHandle handle, int rank, Id lkey, boolean cumulative) {
    if (lkey != null)
      lkey = ((RingId) lkey).getId();
    
    IdRange result = endpoint.range(((MultiringNodeHandle) handle).getHandle(), rank, lkey, cumulative);
    
    if (result != null)
      return new MultiringIdRange(node.getRingId(), result);
    else
      return null;
  }
  
  /**
   * Returns a handle to the local node below this endpoint.  This node handle is serializable,
   * and can therefore be sent to other nodes in the network and still be valid.
   *
   * @return A NodeHandle referring to the local node.
   */
  public NodeHandle getLocalNodeHandle() {
    return new MultiringNodeHandle(node.getRingId(), endpoint.getLocalNodeHandle());
  }
  
  /**
   * Schedules a message to be delivered to this application after the provided number of
   * milliseconds.
   *
   * @param message The message to be delivered
   * @param delay The number of milliseconds to wait before delivering the message
   */
  public TimerTask scheduleMessage(Message message, long delay) {
    return endpoint.scheduleMessage(message, delay);
  }
  
  /**
   * Schedules a message to be delivered to this application every period number of 
   * milliseconds, after delay number of miliseconds have passed.
   *
   * @param message The message to be delivered
   * @param delay The number of milliseconds to wait before delivering the fist message
   * @param delay The number of milliseconds to wait before delivering subsequent messages
   */
  public TimerTask scheduleMessage(Message message, long delay, long period) {
    return endpoint.scheduleMessage(message, delay, period);
  }
  
  /**
   * Internal method which delivers the message to the application
   *
   * @param id The target id
   * @param message The message to deliver
   */
  protected void deliver(RingId id, Message target) {
    application.deliver(id, target);
  }
  
  /**
   * Returns a unique instance name of this endpoint, sort of a mailbox name for this
   * application.
   * 
   * @return The unique instance name of this application
   */
  public String getInstance() {
    return "multiring" + endpoint.getInstance();
  }

}




