package rice.pastry.leafset;

import rice.pastry.*;

import java.util.*;
import java.io.*;

/**
 * A set of nodes, ordered by numerical distance of their nodeId from the local
 * nodeId
 * 
 * @version $Id$
 * 
 * @author Andrew Ladd
 * @author Peter Druschel
 */

public class SimilarSet extends Observable implements NodeSetI, Serializable,
    Observer {

  private NodeHandle ln;

  private boolean clockwise;

  private NodeHandle[] nodes;

  private int theSize;

  private LeafSet leafSet;

  private SimilarSet(SimilarSet that, LeafSet ls) {
    this.ln = that.ln;
    this.clockwise = that.clockwise;
    this.nodes = new NodeHandle[that.nodes.length];
    System.arraycopy(that.nodes, 0, nodes, 0, nodes.length);
    this.theSize = that.theSize;
    this.leafSet = ls;
  }

  /**
   * swap two elements
   * 
   * @param i the index of the first element
   * @param j the indes of the second element
   */

  protected void swap(int i, int j) {
    NodeHandle handle = nodes[i];
    nodes[i] = nodes[j];
    nodes[j] = handle;
  }

  /**
   * Constructor.
   * 
   * @param localNode the local node
   * @param size the size of the similar set.
   * @param cw true if this is the clockwise leafset half
   */

  public SimilarSet(LeafSet leafSet, NodeHandle localNode, int size, boolean cw) {
    this.leafSet = leafSet;
    ln = localNode;
    clockwise = cw;
    theSize = 0;
    nodes = new NodeHandle[size];
  }

  /**
   * Test if a NodeHandle belongs into the set. Predicts if a put would succeed.
   * 
   * @param handle the handle to test.
   * 
   * @return true if a put would succeed, false otherwise.
   */

  public boolean test(NodeHandle handle) {
    NodeId nid = handle.getNodeId();

    if (nid.equals(ln.getNodeId()))
      return false;

    for (int i = 0; i < theSize; i++)
      if (nid.equals(nodes[i].getNodeId()))
        return false;

    if (theSize < nodes.length)
      return true;

    if (clockwise) {
      if (!nid.isBetween(ln.getNodeId(), nodes[theSize - 1].getNodeId()))
        return false;
    } else {
      if (!nid.isBetween(nodes[theSize - 1].getNodeId(), ln.getNodeId()))
        return false;
    }

    return true;
  }

  /**
   * Puts a NodeHandle into the set.
   * 
   * @param handle the handle to put.
   * 
   * @return true if the put succeeded, false otherwise.
   */

  public boolean put(NodeHandle handle) {
    NodeId nid = handle.getNodeId();
    //int index;

    if (!test(handle))
      return false;

    if (theSize < nodes.length) {
      nodes[theSize] = handle;
      //index = theSize;
      theSize++;
    } else {
      theSize--;

      setChanged();

      if (leafSet.isProperlyRemoved(nodes[theSize])) {
        if (leafSet.observe)
          notifyObservers(new NodeSetUpdate(nodes[theSize], false));
      }
      if (leafSet.observe)
        nodes[theSize].deleteObserver(this);

      theSize++;

      nodes[theSize - 1] = handle;
      //index = theSize-1;
    }

    // bubble the new node into the correct position
    if (clockwise) {
      for (int i = theSize - 1; i > 0; i--)
        if (nid.isBetween(ln.getNodeId(), nodes[i - 1].getNodeId()))
          swap(i, i - 1);
        else
          break;
    } else {
      for (int i = theSize - 1; i > 0; i--)
        if (nid.isBetween(nodes[i - 1].getNodeId(), ln.getNodeId()))
          swap(i, i - 1);
        else
          break;
    }

    setChanged();
    if (!leafSet.testOtherSet(this, handle)) {
      if (leafSet.observe)
        notifyObservers(new NodeSetUpdate(handle, true));
    }

    // register as an observer, so we'll be notified if the handle is declared
    // dead
    if (leafSet.observe)
      handle.addObserver(this);

    return true;
  }

  /**
   * Is called by the Observer pattern whenever the liveness or proximity of a
   * registered node handle is changed.
   * 
   * @param o The node handle
   * @param arg the event type (PROXIMITY_CHANGE, DECLARED_LIVE, DECLARED_DEAD)
   */
  public void update(Observable o, Object arg) {
    // if the node is declared dead, remove it immediately
    if (((Integer) arg) == NodeHandle.DECLARED_DEAD) {
      remove((NodeHandle) o);
    }
    if (((Integer) arg) == NodeHandle.DECLARED_LIVE) {
      leafSet.put((NodeHandle) o);
    }

  }

  /**
   * Finds the NodeHandle associated with the NodeId.
   * 
   * @param nid a node id.
   * @return the handle associated with that id or null if no such handle is
   *         found.
   */
  public NodeHandle get(NodeId nid) {
    for (int i = 0; i < theSize; i++)
      if (nodes[i].getNodeId().equals(nid))
        return nodes[i];

    return null;
  }

  public NodeHandle get(NodeHandle nh) {
    for (int i = 0; i < theSize; i++)
      if (nodes[i].equals(nh))
        return nodes[i];

    return null;
  }

  /**
   * Gets the ith element in the set.
   * 
   * @param i an index. i == -1 refers to the local node
   * @return the handle associated with that id or null if no such handle is
   *         found.
   */

  public NodeHandle get(int i) {
    if (i < -1 || i >= theSize)
      return null;
    if (i == -1)
      return ln;

    return nodes[i];
  }

  /**
   * Verifies if the set contains this particular id.
   * 
   * @param nid a node id.
   * @return true if that node id is in the set, false otherwise.
   */
  public boolean member(NodeHandle nid) {
    for (int i = 0; i < theSize; i++)
      if (nodes[i].equals(nid))
        return true;

    return false;
  }

  /**
   *  
   */
  public boolean member(NodeId nid) {
    for (int i = 0; i < theSize; i++)
      if (nodes[i].getId().equals(nid))
        return true;

    return false;
  }

  /**
   * Removes a node id and its handle from the set.
   * 
   * @param nid the node to remove.
   * @return the node handle removed or null if nothing.
   */
  public NodeHandle remove(NodeId nid) {
    for (int i = 0; i < theSize; i++) {
      if (nodes[i].getNodeId().equals(nid)) {
        return remove(i);
      }
    }

    return null;
  }

  public NodeHandle remove(NodeHandle nh) {
    for (int i = 0; i < theSize; i++) {
      if (nodes[i].equals(nh)) {
        return remove(i);
      }
    }

    return null;
  }

  /**
   * Removes a node id and its handle from the set.
   * 
   * @param i the index of the node to remove.
   * @return the node handle removed or null if nothing.
   */

  protected NodeHandle remove(int i) {
    if (i < 0 || i >= theSize)
      return null;
    NodeHandle handle = nodes[i];

    for (int j = i + 1; j < theSize; j++) {
      nodes[j - 1] = nodes[j];
    }

    theSize--;

    setChanged();
    if (leafSet.isProperlyRemoved(handle)) {
      if (leafSet.observe)
        notifyObservers(new NodeSetUpdate(handle, false));
    }

    if (leafSet.observe)
      handle.deleteObserver(this);

    return handle;
  }

  /**
   * Gets the index of the element with the given node id.
   * 
   * @param nid the node id.
   * @return the index or -1 if the element does not exist.
   */

  public int getIndex(NodeId nid) {
    for (int i = 0; i < theSize; i++)
      if (nodes[i].getNodeId().equals(nid))
        return i;

    return -1;
  }

  public int getIndex(NodeHandle nh) {
    for (int i = 0; i < theSize; i++)
      if (nodes[i].equals(nh))
        return i;

    return -1;
  }

  /**
   * Gets the current size of this set.
   * 
   * @return the size.
   */

  public int size() {
    return theSize;
  }

  /**
   * Numerically closest node to a given a node. Returns -1 if the local nodeId
   * is the most similar and returns an index otherwise.
   * 
   * @param nid a node id.
   * 
   * @return -1 if the local nodeId is most similar, else the index of the most
   *         similar node.
   */

  public int mostSimilar(Id nid) {
    if (theSize == 0)
      return -1;

    NodeId.Distance minDist = ln.getNodeId().distance(nid);
    int min = -1;

    for (int i = 0; i < theSize; i++) {
      NodeId.Distance d = nodes[i].getNodeId().distance(nid);
      int cmp = d.compareTo(minDist);
      if ((!clockwise && cmp < 0) || (clockwise && cmp <= 0)) {
        minDist = d;
        min = i;
      }
    }

    return min;

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
   * @return the handle associated with that id or null if no such handle is
   *         found.
   */
  public rice.p2p.commonapi.NodeHandle getHandle(rice.p2p.commonapi.Id id) {
    return getHandle((NodeId) id);
  }

  /**
   * Gets the ith element in the set.
   * 
   * @param i an index.
   * @return the handle associated with that id or null if no such handle is
   *         found.
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
  public int getIndexHandle(rice.p2p.commonapi.Id id)
      throws NoSuchElementException {
    return getIndex((NodeId) id);
  }

  SimilarSet copy(LeafSet newLeafSet) {
    return new SimilarSet(this, newLeafSet);
  }
}

