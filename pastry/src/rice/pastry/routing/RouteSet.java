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

package rice.pastry.routing;

import rice.pastry.*;
import rice.pastry.NodeSet;

import java.util.*;

/**
 * A set of nodes typically stored in the routing table.  This
 * is a set of nodes which contains a bounded number of the closest
 * node handles.
 *
 * @author Andrew Ladd
 */

public class RouteSet extends Observable implements NodeSet
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
