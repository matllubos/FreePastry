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

package rice.pastry.leafset;

import rice.pastry.*;

import java.util.*;
import java.io.*;

/**
 * A set of nodes, ordered by numerical distance of their nodeId from the baseId (local nodeId)
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 * @author Peter Druschel
 */


public class SimilarSet extends Observable implements NodeSetI, Serializable, Observer
{
    private NodeHandle localNode;
    private NodeId baseId;
    private boolean clockwise;

    private NodeHandle[] nodes;
    private NodeId.Distance[] dist;

    private int theSize;

    /**
     * swap two elements
     *
     * @param i the index of the first element
     * @param j the indes of the second element
     */

    protected void swap(int i, int j) {
	NodeHandle handle = nodes[i];
	NodeId.Distance d = dist[i];

	nodes[i] = nodes[j];
	dist[i] = dist[j];
	
	nodes[j] = handle;
	dist[j] = d;
    }

    /**
     * Constructor.
     *
     * @param ls the leafset
     * @param localNode the local node 
     * @param size the size of the similar set.
     * @param cw true if this is the clockwise leafset half
     */

    public SimilarSet(NodeHandle localNode, int size, boolean cw) {
	this.localNode = localNode;
	baseId = localNode.getNodeId();
	clockwise = cw;
	theSize = 0;
	nodes = new NodeHandle[size];
	dist = new NodeId.Distance[size];
    }
    
    /**
     * Test if a NodeHandle belongs into the set. Predicts if a put would succeed.
     *
     * @param handle the handle to test.
     *
     * @return true if a put would succeed, false otherwise.
     */

    public boolean test(NodeHandle handle)
    {
	NodeId nid = handle.getNodeId();
	NodeId.Distance d;

	if (nid.equals(baseId)) return false;

	for (int i=0; i<theSize; i++)
	    if (nid.equals(nodes[i].getNodeId())) return false;

	if (theSize < nodes.length) return true;
	
	if (baseId.clockwise(nid) == clockwise)
	    d = baseId.distance(nid);
	else
	    d = baseId.longDistance(nid);

	if (dist[theSize - 1].compareTo(d) <= 0) return false;

	return true;
    }


    /**
     * Puts a NodeHandle into the set.
     *
     * @param handle the handle to put.
     *
     * @return true if the put succeeded, false otherwise.
     */

    public boolean put(NodeHandle handle)
    {
	NodeId nid = handle.getNodeId();
	NodeId.Distance d;

	if (nid.equals(baseId)) return false;

	for (int i=0; i<theSize; i++)
	    if (nid.equals(nodes[i].getNodeId())) return false;

	if (baseId.clockwise(nid) == clockwise)
	    d = baseId.distance(nid);
	else
	    d = baseId.longDistance(nid);

	int index;

	if (theSize < nodes.length) {
	    nodes[theSize] = handle;
	    dist[theSize] = d;
	    
	    index = theSize;
	    theSize++;
	}
	else {
	    if (dist[theSize - 1].compareTo(d) <= 0) return false;

	    theSize --;
	    
	    setChanged();
	    notifyObservers(new NodeSetUpdate(nodes[theSize - 1], false));

	    nodes[theSize-1].deleteObserver(this);
	    
	    theSize++;

	    nodes[theSize - 1] = handle;
	    dist[theSize - 1] = d;
	    
	    index = theSize - 1;
	}

	for (int i=index; i>0; i--)
	    if (dist[i].compareTo(dist[i-1]) < 0) swap(i, i - 1);
	    else break;

	setChanged();
	notifyObservers(new NodeSetUpdate(handle, true));

	// register as an observer, so we'll be notified if the handle is declared dead
	handle.addObserver(this);

	return true;
    }
    

    /**
     * Is called by the Observer pattern whenever the liveness or
     * proximity of a registered node handle is changed.
     *
     * @param o The node handle
     * @param arg the event type (PROXIMITY_CHANGE, DECLARED_LIVE, DECLARED_DEAD)
     */
    public void update(Observable o, Object arg) {
      // if the node is declared dead, remove it immediately
      if (((Integer) arg) == NodeHandle.DECLARED_DEAD) {

	//System.out.println("SimilarSet:update(), removing dead node");
        remove(((NodeHandle) o).getNodeId());
      }

    }


    /**
     * Finds the NodeHandle associated with the NodeId.
     *
     * @param nid a node id.
     * @return the handle associated with that id or null if no such handle is found.
     */
    
    public NodeHandle get(NodeId nid)
    {
	for (int i=0; i<theSize; i++)
	    if (nodes[i].getNodeId().equals(nid)) return nodes[i];
	
	return null;
    }


    /**
     * Gets the ith element in the set.
     *
     * @param i an index. i == -1 refers to the baseId
     * @return the handle associated with that id or null if no such handle is found.
     */
    
    public NodeHandle get(int i) {
	if (i < -1 || i >= theSize) return null;
	if (i == -1) return localNode;
	
	return nodes[i]; 
    }

    /**
     * Verifies if the set contains this particular id.
     * 
     * @param nid a node id.
     * @return true if that node id is in the set, false otherwise.
     */

    public boolean member(NodeId nid) {
	for (int i=0; i<theSize; i++)
	    if (nodes[i].getNodeId().equals(nid)) return true;

	return false;
    }
    
    /**
     * Removes a node id and its handle from the set.
     *
     * @param nid the node to remove.
     * @return the node handle removed or null if nothing.
     */

    public NodeHandle remove(NodeId nid) {
	for (int i=0; i<theSize; i++) {
	    if (nodes[i].getNodeId().equals(nid)) {
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

    public NodeHandle remove(int i) {
	if (i < 0 || i >= theSize) return null;
	NodeHandle handle = nodes[i];
		
	for (int j=i+1; j<theSize; j++) {
	    nodes[j - 1] = nodes[j];
	    dist[j - 1] = dist[j];
	}
		
	theSize --;

	setChanged();
	notifyObservers(new NodeSetUpdate(handle, false));
		
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
	for (int i=0; i<theSize; i++) 
	    if (nodes[i].getNodeId().equals(nid)) return i;
	
	return -1;
    }
    
    /**
     * Gets the current size of this set.
     *
     * @return the size.
     */

    public int size() { return theSize; }

    /**
     * Numerically closest node to a given a node.  Returns -1 if the base id is the most similar
     * and returns an index otherwise.
     *
     * @param nid a node id.
     *
     * @return -1 if the base id is most similar, else the index of the most similar node.
     */

    public int mostSimilar(Id nid) {
	if (theSize == 0) return -1;

	NodeId.Distance minDist = baseId.distance(nid);
	int min = -1;

	for (int i=0; i<theSize; i++) {
	    NodeId.Distance d = nodes[i].getNodeId().distance(nid);
	    if (d.compareTo(minDist) < 0) {
		minDist = d;
		min = i;
	    }
	}

	return min;

	/*
	NodeId.Distance baseDist = baseId.distance(nid);
	NodeId.Distance d;

	if (theSize == 0) return -1;
	
	d = nodes[0].getNodeId().distance(nid);
	
	if (baseDist.compareTo(d) <= 0) return -1;

	for (int i=1; i<theSize; i++) {
	    NodeId.Distance dprime = nodes[i].getNodeId().distance(nid);

	    if (d.compareTo(dprime) <= 0) return i - 1;
	    
	    d = dprime;
	}

	return theSize - 1;
	*/

    }
}



