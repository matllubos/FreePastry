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

package rice.pastry.leafset;

import rice.pastry.*;
import rice.pastry.routing.*;
import rice.pastry.security.*;

import java.util.*;
import java.io.*;

/**
 * A class for representing and manipulating the leaf set.
 *  
 * @version $Id$
 *
 * @author Andrew Ladd
 * @author Peter Druschel
 */

public class LeafSet extends Observable implements Serializable {
    private NodeId baseId;
    private SimilarSet cwSet;
    private SimilarSet ccwSet;

    private int theSize;

    /**
     * Constructor.
     *
     * @param localHandle the local node
     * @param size the size of the leaf set.
     */
    
    public LeafSet(NodeHandle localNode, int size) 
    {
	baseId = localNode.getNodeId();
	theSize = size;

	cwSet = new SimilarSet(localNode, size/2, true);
	ccwSet = new SimilarSet(localNode, size/2, false);
    }

    /**
     * Puts a NodeHandle into the set.
     *
     * @param handle the handle to put.
     * @return true if successful, false otherwise.
     */

    public boolean put(NodeHandle handle) 
    {
	NodeId nid = handle.getNodeId();
	if (nid.equals(baseId)) return false;
	if (member(nid)) return false;
	
	boolean res = cwSet.put(handle) | ccwSet.put(handle);
	return res;
    }

    /**
     * Test if a put of the given NodeHandle would succeed.
     *
     * @param handle the handle to test.
     * @return true if a put would succeed, false otherwise.
     */

    public boolean test(NodeHandle handle) 
    {
	NodeId nid = handle.getNodeId();
	if (nid.equals(baseId)) return false;
	if (member(nid)) return false;

	return cwSet.test(handle) | ccwSet.test(handle);
    }


    /**
     * Test if the leafset overlaps
     * 
     * @return true if the most distant cw member appears in the ccw set or vice versa, false otherwise
     */

    public boolean overlaps() {
	if (size() > 0 && ( ccwSet.member(cwSet.get(cwSet.size()-1).getNodeId()) ||
			    cwSet.member(ccwSet.get(ccwSet.size()-1).getNodeId()) ) )
	    return true;

	else return false;
    }


    /**
     * Finds the NodeHandle associated with the NodeId.
     *
     * @param nid a node id.
     * @return the handle associated with that id or null if no such handle is found.
     */
    
    public NodeHandle get(NodeId nid) 
    {
	NodeHandle res = cwSet.get(nid);
	if (res != null) return res;
	return ccwSet.get(nid);
    }


    /**
     * Gets the index of the element with the given node id.
     *
     * @param nid the node id.
     *
     * @return the index or throws a NoSuchElementException 
     */

    public int getIndex(NodeId nid) throws NoSuchElementException {
	int index = cwSet.getIndex(nid);
	if (index >= 0) return index + 1;
	index = ccwSet.getIndex(nid);
	if (index >= 0) return -index - 1;

	throw new NoSuchElementException();
    }


    /**
     * Finds the NodeHandle at a given index.
     *
     * @param index an index.
     * @return the handle associated with that index.
     */
    
    public NodeHandle get(int index) 
    {
	if (index >= 0) return cwSet.get(index - 1);
	else return ccwSet.get(- index - 1);
    }
    

    /**
     * Verifies if the set contains this particular id.
     * 
     * @param nid a node id.
     * @return true if that node id is in the set, false otherwise.
     */

    public boolean member(NodeId nid) 
    {
	return cwSet.member(nid) || ccwSet.member(nid);
    }
    

    /**
     * Removes a node id and its handle from the set.
     *
     * @param nid the node to remove.
     * @return the node handle removed or null if nothing.
     */

    public NodeHandle remove(NodeId nid) 
    {
	//System.out.println("Removing " + nid + " from " + this);

	NodeHandle res1 = cwSet.remove(nid);
	NodeHandle res2 = ccwSet.remove(nid);
	if (res1 != null) return res1;
	else return res2;
    }


    /**
     * Gets the maximal size of the leaf set.
     *
     * @return the size.
     */

    public int maxSize() { return theSize; }

    /**
     * Gets the current size of the leaf set.
     *
     * @return the size.
     */

    public int size() { return cwSet.size() + ccwSet.size(); }

    /**
     * Gets the current clockwise size.
     *
     * @return the size.
     */

    public int cwSize() { return cwSet.size(); }
    
    /**
     * Gets the current counterclockwise size.
     *
     * @return the size.
     */

    public int ccwSize() { return ccwSet.size(); }


    /**
     * complement - given an index of a node in the leafset, produces
     * the index of the same nodeId in the opposite half of the
     * leafset
     *
     * @param index the index of the entry to complement
     * @param the index of the same node in the opposite half of the leafset, or inx if it does not exist there
     */
    private int complement(int inx) {
	int res;

	if (inx == 0) return 0;
	if (inx < 0) {
	    if (inx < -ccwSize()) return inx;
	    res = cwSet.getIndex(ccwSet.get(-inx - 1).getNodeId()) + 1;
	}
	else {
	    if (inx > cwSize()) return inx;	    
	    res = -ccwSet.getIndex(cwSet.get(inx - 1).getNodeId()) - 1;	    
	}
	
	if (res == 0) res = inx;
	return res;
    }


    /**
     * Numerically closests node to a given a node in the leaf set. 
     *
     * @param nid a node id.
     * @return the index of the numerically closest node (0 if baseId is the closest).
     */

    public int mostSimilar(NodeId nid) {
	NodeId.Distance cwMinDist;
	NodeId.Distance ccwMinDist;
	int cwMS;
	int ccwMS;
	int res;

	if (baseId.clockwise(nid)) {
	    cwMS = cwSet.mostSimilar(nid);
	    ccwMS = ccwSet.size()-1;
	    if (cwMS < cwSet.size()-1) 
		return cwMS + 1;
	}
	else {
	    ccwMS = ccwSet.mostSimilar(nid);
	    cwMS = cwSet.size()-1;
	    if (ccwMS < ccwSet.size()-1) 
		return -ccwMS - 1;
	}


	cwMinDist = cwSet.get(cwMS).getNodeId().distance(nid);
	ccwMinDist = ccwSet.get(ccwMS).getNodeId().distance(nid);

	if (cwMinDist.compareTo(ccwMinDist) <= 0) 
	    return cwMS + 1;
	else
	    return -ccwMS - 1;

    }


    /**
     * Merge a remote leafset into this
     *
     * @param remotels the remote leafset
     * @param from the node from which we received the leafset
     * @param routeTable the routing table
     * @param security the security manager
     * @param testOnly if true, do not change the leafset
     * @param insertedHandles if not null, a Set that contains, upon return of this method, the nodeHandles that would be inserted into this LeafSet if testOnly is true
     * @return true if the local leafset changed
     */

    public boolean merge(LeafSet remotels, NodeHandle from, RoutingTable routeTable, 
			     PastrySecurityManager security, boolean testOnly, Set insertedHandles) {

	//System.out.println("LeafSet::merge of " + remotels + " into \n" + this);
	boolean changed, result = false;
	int cwSize = remotels.cwSize();
	int ccwSize = remotels.ccwSize();
	
	// merge the received leaf set into our own
	// to minimize inserts/removes, we do this from nearest to farthest nodes

	// get indeces of localId in the leafset
	int cw = remotels.cwSet.getIndex(baseId);
	int ccw = remotels.ccwSet.getIndex(baseId);

	if (cw < 0) {
	    // localId not in cw set

	    if (ccw < 0) {
		// localId not in received leafset, that means local node is joining

		if (remotels.size() < 2) {
		    cw = ccw = 0;
		}
		else {

		    // find the num. closest to localId in the remotels
		    int closest = remotels.mostSimilar(baseId);
		    NodeId closestId = remotels.get(closest).getNodeId();
		    
		    if (closest == -remotels.ccwSize() || closest == remotels.cwSize()) {
			//System.out.println("LeafSet::merge at " + this + " received an out of range leafset from " + remotels);
			// doesn't hurt to merge it anyways
			//return;
		    }

		    if (closest == 0) {
			// from is closest
			if (baseId.clockwise(closestId)) {
			    cw = closest;
			    ccw = remotels.complement(closest - 1); 
			}
			else {
			    cw = remotels.complement(closest + 1);
			    ccw = closest;
			}
		    }
		    else if (closest < 0) {
			// from is cw
			if (baseId.clockwise(closestId)) {
			    cw = closest;
			    ccw = remotels.complement(closest - 1); 
			}
			else {
			    cw = closest + 1;
			    ccw = remotels.complement(closest);
			}
		    }
		    else {
			// from is ccw
			if (baseId.clockwise(closestId)) {
			    cw = remotels.complement(closest); 
			    ccw = closest - 1; 
			}
			else {
			    ccw = closest;
			    cw = remotels.complement(closest + 1);
			}
		    }
			    
		}
	    }
	    else {
		// localId in ccw set

		ccw = -ccw  - 2;
		cw = ccw + 2;
	    }
	}
	else {
	    // localId in cw set

	    if (ccw < 0) {
		cw = cw + 2;
		ccw = cw - 2;
	    }
	    else {
		// localId is in both halves
		int tmp = ccw;
		ccw = cw;
		cw = -tmp;
	    }
	}

	//System.out.println("LeafSet::merge cw=" + cw + " ccw=" + ccw);

	for (int i=cw; i<=cwSize; i++) {
	    NodeHandle nh;

	    if (i == 0) nh = from;
	    else nh = remotels.get(i);
		
	    nh = security.verifyNodeHandle(nh);
	    if (nh.isAlive() == false) continue;
	    //if (member(nh.getNodeId())) continue;

	    if (testOnly) {
		// see it is missing
		changed = cwSet.test(nh);
	    }
	    else {
		// merge into our cw leaf set half
		changed = cwSet.put(nh);

		// update RT as well
		routeTable.put(nh);
	    }

	    result |= changed;
	    if (insertedHandles != null && changed) insertedHandles.add(nh);
	}

	for (int i=ccw; i>= -ccwSize; i--) {
	    NodeHandle nh;

	    if (i == 0) nh = from;
	    else nh = remotels.get(i);
		
	    nh = security.verifyNodeHandle(nh);
	    if (nh.isAlive() == false) continue;
	    //if (member(nh.getNodeId())) continue;

	    if (testOnly) {
		// see if it is missing
		changed = ccwSet.test(nh);
	    }
	    else {
		// merge into our leaf set
		changed = ccwSet.put(nh);

		// update RT as well
		routeTable.put(nh);
	    }

	    result |= changed;
	    if (insertedHandles != null && changed) insertedHandles.add(nh);
	}

	// if there is overlap, insert nearest nodes regardless of orientation
	if (overlaps()) {
	    for (int i=-ccwSize; i <= cwSize; i++) {
		NodeHandle nh;

		if (i == 0) nh = from;
		else nh = remotels.get(i);
		
		nh = security.verifyNodeHandle(nh);
		if (nh.isAlive() == false) continue;
		
		if (testOnly) {
		    // merge into our leaf set
		    changed = test(nh);
		}
		else {
		    // merge into our leaf set
		    changed = put(nh);
		}

		result |= changed;
		if (insertedHandles != null && changed) insertedHandles.add(nh);
	    }
	}


	//System.out.println("LeafSet::merge result: " + this);

	return result;
    }


    /**
     * Add observer method.
     *
     * @param o the observer to add.  
     */

    public void addObserver(Observer o) {
	cwSet.addObserver(o);
	ccwSet.addObserver(o);
    }


    /**
     * Delete observer method.
     *
     * @param o the observer to delete.  
     */

    public void deleteObserver(Observer o) {
	cwSet.deleteObserver(o);
	ccwSet.deleteObserver(o);
    }


    /**
     * Returns a string representation of the leaf set
     *
     */

    public String toString() 
    {
	String s = "leafset: ";
	for (int i=-ccwSet.size(); i<0; i++)
	    s = s + get(i).getNodeId();
	s = s + " [ " + baseId + " ] ";
	for (int i=1; i<=cwSet.size(); i++)
	    s = s + get(i).getNodeId();

	return s;
    }


}

