/*************************************************************************

"Free Pastry" Peer-to-Peer Application Development Substrate 

Copyright 2002, Rice University. All rights reserved.  Developed by
Andrew Ladd, Peter Druschel.

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
import rice.pastry.NodeSet;

import java.util.*;
import java.io.*;

/**
 * A set of nodes typically stored in the routing table.  This
 * is a set of nodes which contains a bounded number of the closest
 * node handles.
 *
 * @author Andrew Ladd
 * @author Peter Druschel
 */

public class RouteSet extends Observable implements NodeSet, Serializable
{
    private NodeHandle[] nodes;
    private int theSize;
    
    /**
     * Constructor.
     *
     * @param maxSize the maximum number of nodes that fit in this set.
     */

    public RouteSet(int maxSize) {
	nodes = new NodeHandle[maxSize];
	theSize = 0;	
    }

    /**
     * Puts a node into the set.
     *
     * @param handle the handle to put.
     *
     * @return true if the put succeeded, false otherwise.
     */
    
    public boolean put(NodeHandle handle) {
	int n = nodes.length;

	for (int i=0; i<theSize; i++)
	    if (nodes[i].getNodeId().equals(handle.getNodeId())) return false;
	
	if (theSize < n) {
	    nodes[theSize++] = handle;

	    setChanged();
	    notifyObservers(new NodeSetUpdate(handle, true));
	    
	    return true; 
	}
	else {
	    int worstIndex = 0;
	    int worstProximity = nodes[0].proximity();

	    for (int i=1; i<n; i++) {
		int p = nodes[i].proximity();
		
		if (p > worstProximity) {
		    worstProximity = p;
		    worstIndex = i;
		}
	    }

	    if (handle.proximity() < worstProximity) {
		nodes[worstIndex] = handle; 

		setChanged();
		notifyObservers(new NodeSetUpdate(handle, true));

		return true;
	    }
	    else return false;	    
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
     * Return the closest node in the set.
     *
     * @return the closest node.
     */

    public NodeHandle closestNode() { 
	int bestProximity = nodes[0].proximity();
	int bestIndex = 0;

	for (int i=1; i<theSize; i++) {
	    int p = nodes[i].proximity();

	    if (p < bestProximity) {
		bestProximity = p;
		bestIndex = i;
	    }
	}

	//System.out.println(bestProximity);
	//System.out.println(nodes.length);
	
	return nodes[bestIndex];
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
}
