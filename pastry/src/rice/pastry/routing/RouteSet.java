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

package rice.pastry.routing;

import rice.pastry.*;
import rice.pastry.NodeSetI;

import java.util.*;
import java.io.*;

/**
 * A set of nodes typically stored in the routing table.  The
 * set contains a bounded number of the closest
 * node handles. Since proximity value can change unpredictably, we don't
 * keep the set in sorted order.
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 * @author Peter Druschel
 */

public class RouteSet extends Observable implements NodeSetI, Serializable, Observer
{
    private NodeHandle[] nodes;
    private int theSize;
    private int closest;

    /**
     * Constructor.
     *
     * @param maxSize the maximum number of nodes that fit in this set.
     */

    public RouteSet(int maxSize) {
      nodes = new NodeHandle[maxSize];
      theSize = 0;
      closest = -1;
    }

    /**
     * Puts a node into the set. The insertion succeeds either if the set is
     * below is maximal size or if the handle is closer than the most distant member in the set.
     *
     * @param handle the handle to put.
     *
     * @return true if the put succeeded, false otherwise.
     */

    public boolean put(NodeHandle handle) {
      int worstIndex = -1;
      int worstProximity = Integer.MIN_VALUE;

      // scan entries
      for (int i=0; i<theSize; i++) {

        // if handle is already in the set, abort
        if (nodes[i].equals(handle)) return false;

        // find entry with worst proximity
        int p = nodes[i].proximity();
        if (p >= worstProximity) {
          worstProximity = p;
          worstIndex = i;
        }
      }

      if (theSize < nodes.length) {
        nodes[theSize++] = handle;

        setChanged();
        notifyObservers(new NodeSetUpdate(handle, true));

	// ping handles while the set is not full
	handle.ping();

        return true;
      }
      else {
        if (handle.proximity() == Integer.MAX_VALUE) {
          // wait until the proximity value is available

	  handle.ping(); // XXX - eventually, should only ping handles pinged from the deserializer
          handle.addObserver(this);
          return false;
        }
        else if (handle.proximity() < worstProximity) {
          // remove handle with worst proximity
          setChanged();
          notifyObservers(new NodeSetUpdate(nodes[worstIndex], false));

	  // in case we observe this handle, stop doing so
	  nodes[worstIndex].deleteObserver(this);

          // insert new handle
          nodes[worstIndex] = handle;

          setChanged();
          notifyObservers(new NodeSetUpdate(handle, true));

          return true;
        }
        else return false;
      }
    }

    /**
     * Is called by the Observer pattern whenever the liveness or
     * proximity of a registered node handle is changed.
     *
     * @param o The node handle
     * @param arg the event type (PROXIMITY_CHANGE, DECLARED_LIVE, DECLARED_DEAD)
     */
    public void update(Observable o, Object arg) {
      // if the proximity is initialized for the time, insert the handle
      if (((Integer) arg) == NodeHandle.PROXIMITY_CHANGED) {

	//System.out.println("RouteSet:update(), inserting pinged node");
        put((NodeHandle) o);   

	// we're no longer interested in this handle
	o.deleteObserver(this);
      }
    }

    /**
     * Removes a node from a set.
     *
     * @param nid the node id to remove.
     *
     * @return the removed handle or null.
     */

  public NodeHandle remove(NodeId nid) {
    for (int i=0; i<theSize; i++) {
      if (nodes[i].getNodeId().equals(nid)) {
        NodeHandle handle = nodes[i];

        nodes[i] = nodes[--theSize];

        setChanged();
        notifyObservers(new NodeSetUpdate(handle, false));

  // in case we observe this handle, stop doing so
  handle.deleteObserver(this);

        return handle;
      }
    }

    return null;
  }

  public NodeHandle remove(NodeHandle nh) {
    for (int i=0; i<theSize; i++) {
      if (nodes[i].equals(nh)) {
        NodeHandle handle = nodes[i];

        nodes[i] = nodes[--theSize];

        setChanged();
        notifyObservers(new NodeSetUpdate(handle, false));

  // in case we observe this handle, stop doing so
  handle.deleteObserver(this);

        return handle;
      }
    }

    return null;
  }

    /**
     * Membership test.
     *
     * @param nid the node id to membership of.
     *
     * @return true if it is a member, false otherwise.
     */

  public boolean member(NodeHandle nh) {
    for (int i=0; i<theSize; i++)
      if (nodes[i].equals(nh)) return true;

    return false;
  }

  public boolean member(NodeId nid) {
    for (int i=0; i<theSize; i++)
      if (nodes[i].getNodeId().equals(nid)) return true;

    return false;
  }

    /**
     * Return the current size of the set.
     *
     * @return the size.
     */

    public int size() { return theSize; }

    /**
     * Pings all new nodes in the RouteSet.
     * No longer- Called from RouteMaintenance.
     */

    public void pingAllNew() {
      for (int i=0; i<theSize; i++) {
        if (nodes[i].proximity() == Integer.MAX_VALUE)
          nodes[i].ping();
      }
    }

  /**
   * Return the closest live node in the set.
   *
   * @return the closest node, or null if no live node exists in the set.
   */
  public NodeHandle closestNode() {
    return closestNode(NodeHandle.LIVENESS_SUSPECTED);
  }
    /**
     * Return the closest live node in the set.
     *
     * @return the closest node, or null if no live node exists in the set.
     */
  public NodeHandle closestNode(int minLiveness) {
      int bestProximity = Integer.MAX_VALUE;
      NodeHandle bestNode = null;

      for (int i=0; i<theSize; i++) {
        if (!(nodes[i].getLiveness() <= minLiveness) /*isAlive()*/) continue;        
        int p = nodes[i].proximity();
        if (p <= bestProximity) {
          bestProximity = p;
          bestNode = nodes[i];
          closest = i;
        }
      }

      //System.out.println(bestProximity);
      //System.out.println(nodes.length);

      // If a backup node handle bubbles up to the top, ping it.
      if (bestNode != null && bestProximity == Integer.MAX_VALUE)
        bestNode.ping();

      return bestNode;
    }

    /**
     * Returns the node in the ith position in the set.
     *
     * @return the ith node.
     */

    public NodeHandle get(int i) {
      if (i < 0 || i >= theSize) throw new NoSuchElementException();

      return nodes[i];
    }

    /**
     * Returns the node handle with the matching node id or null if none exists.
     *
     * @param nid the node id.
     *
     * @return the node handle.
     */

    public NodeHandle get(NodeId nid) {
	for (int i=0; i<theSize; i++)
	  if (nodes[i].getNodeId().equals(nid)) return nodes[i];

      return null;
    }

    /**
     * Get the index of the node id.
     *
     * @return the node.
     */

  public int getIndex(NodeId nid) {
    for (int i=0; i<theSize; i++)
      if (nodes[i].getNodeId().equals(nid)) return i;

    return -1;
  }

  public int getIndex(NodeHandle nh) {
    for (int i=0; i<theSize; i++)
      if (nodes[i].equals(nh)) return i;

    return -1;
  }

    /**
     * deserialize the routeSet
     * pings the handle the was the closests on the sending node
     */

    private void readObject(ObjectInputStream in)
      throws IOException, ClassNotFoundException {
      nodes = (NodeHandle[]) in.readObject();
      theSize = in.readInt();
      closest = in.readInt();
      if (closest != -1) nodes[closest].ping();
        closest = -1;
    }

    /**
     * serialize the RouteSet
     * records the closest node
     */

    private void writeObject(ObjectOutputStream out)
      throws IOException, ClassNotFoundException {
      if (closest == -1) closestNode();
      
      // here, we don't want to advetise nodes which are dead, so we filter
      // our list based on only live nodes
      NodeHandle[] tmp = new NodeHandle[nodes.length];
      
      int j=0;
      for (int i=0; i<tmp.length; i++) {
        if ((nodes[i] != null) && (nodes[i].isAlive())) {
          tmp[j] = nodes[i];
          j++;
        }
      }
        
      out.writeObject(tmp);
      out.writeInt(j);
      
      int closest = -1;
      for (int i=0; i<j; i++) 
        if ((closest == -1) || (tmp[i].proximity() < tmp[closest].proximity())) 
          closest = i;
          
      out.writeInt(closest);
    }

    // Common API Support

    /**
      * Puts a NodeHandle into the set.
     *
     * @param handle the handle to put.
     *
     * @return true if the put succeeded, false otherwise.
     */
    public boolean putHandle(rice.p2p.commonapi.NodeHandle handle) {
      return put((NodeHandle) handle);
    }

    /**
      * Finds the NodeHandle associated with the NodeId.
     *
     * @param id a node id.
     * @return the handle associated with that id or null if no such handle is found.
     */
    public rice.p2p.commonapi.NodeHandle getHandle(rice.p2p.commonapi.Id id) {
      return getHandle((NodeId) id);
    }

    /**
      * Gets the ith element in the set.
     *
     * @param i an index.
     * @return the handle associated with that id or null if no such handle is found.
     */
    public rice.p2p.commonapi.NodeHandle getHandle(int i) {
      return get(i);
    }

    /**
      * Verifies if the set contains this particular id.
     *
     * @param id a node id.
     * @return true if that node id is in the set, false otherwise.
     */
    public boolean memberHandle(rice.p2p.commonapi.Id id) {
      return member((NodeId) id);
    }

    /**
      * Removes a node id and its handle from the set.
     *
     * @param nid the node to remove.
     *
     * @return the node handle removed or null if nothing.
     */
    public rice.p2p.commonapi.NodeHandle removeHandle(rice.p2p.commonapi.Id id) {
      return remove((NodeId) id);
    }

    /**
      * Gets the index of the element with the given node id.
     *
     * @param id the id.
     *
     * @return the index or throws a NoSuchElementException.
     */
    public int getIndexHandle(rice.p2p.commonapi.Id id) throws NoSuchElementException {
      return getIndex((NodeId) id);
    }
}
