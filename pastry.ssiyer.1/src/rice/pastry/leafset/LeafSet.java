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

package rice.pastry.leafset;

import rice.pastry.*;

import java.util.*;
import java.io.*;

/**
 * A class for representing and manipulating the leaf set.
 *  
 * @author Andrew Ladd
 * @author Peter Druschel
 */

public class LeafSet extends Observable implements NodeSet, Serializable {
    private int theSize;

    private NodeId baseId;
    
    private SimilarSet cwSet;
    private SimilarSet ccwSet;

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

	cwSet = new SimilarSet(localNode, size, true);
	ccwSet = new SimilarSet(localNode, size, false);
    }

    /**
     * Puts a NodeHandle into the set.
     *
     * @param handle the handle to put.
     *
     * @return true if successful, false otherwise.
     */

    public boolean put(NodeHandle handle) 
    {
	NodeId nid = handle.getNodeId();
	if (nid.equals(baseId)) return false;

	//System.out.println("Inserting " + handle.getNodeId() + " into " + this);

	if (baseId.clockwise(nid)) return cwSet.put(handle);
	else return ccwSet.put(handle);
    }

    /**
     * Test if a put of the given NodeHandle would succeed.
     *
     * @param handle the handle to test.
     *
     * @return true if a put would succeed, false otherwise.
     */

    public boolean test(NodeHandle handle) 
    {
	NodeId nid = handle.getNodeId();
	if (nid.equals(baseId)) return false;
	
	if (baseId.clockwise(nid)) return cwSet.test(handle);
	else return ccwSet.test(handle);
    }


    /**
     * Test if the leafset spans the entire ring
     * 
     * @return true if the most distant cw member appears in the ccw set, false otherwise
     */

    public boolean spansRing() {
	/*
	if (size() > 0 && ccwSet.member(cwSet.get(cwSet.size()-1).getNodeId())) return true;
	else return false;
	*/
	return false;
    }

    /**
     * Finds the NodeHandle associated with the NodeId.
     *
     * @param nid a node id.
     * @return the handle associated with that id or null if no such handle is found.
     */
    
    public NodeHandle get(NodeId nid) 
    {
	if (baseId.clockwise(nid)) return cwSet.get(nid);
	else return ccwSet.get(nid);
    }

    /**
     * Gets the index of the element with the given node id.
     *
     * @param nid the node id.
     *
     * @return the index or throws a NoSuchElementException
     */

    public int getIndex(NodeId nid) throws NoSuchElementException {
	if (baseId.clockwise(nid)) return cwSet.getIndex(nid) + 1;
	else return - ccwSet.getIndex(nid) - 1;
    }

    /**
     * Finds the NodeHandle at a given index.
     *
     * @param index an index.
     * @return the handle associated with that index or throws an exception.
     */
    
    public NodeHandle get(int index) 
    {
	if (index > 0) return cwSet.get(index - 1);
	else if (index < 0) return ccwSet.get(- index - 1);
	else return null;
    }
    
    /**
     * Verifies if the set contains this particular id.
     * 
     * @param nid a node id.
     * @return true if that node id is in the set, false otherwise.
     */

    public boolean member(NodeId nid) 
    {
	if (baseId.clockwise(nid)) return cwSet.member(nid);
	else return ccwSet.member(nid);
    }
    
    /**
     * Removes a node id and its handle from the set.
     *
     * @param nid the node to remove.
     *
     * @return the node handle removed or null if nothing.
     */

    public NodeHandle remove(NodeId nid) 
    {
	//System.out.println("Removing " + nid + " from " + this);

	if (baseId.clockwise(nid)) return cwSet.remove(nid);
	else return ccwSet.remove(nid);
    }

    /**
     * Gets the maximal size of the leaf set.
     *
     * @return the size.
     */

    public int maxSize() { return theSize * 2; }

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
     * Numerically closests node to a given a node in the leaf set. 
     *
     * @param nid a node id.
     *
     * @return the index of the numerically closest node (0 is baseId is the closest).
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


	/*
	int cwMS = cwSet.mostSimilar(nid);
	int ccwMS = ccwSet.mostSimilar(nid);
	int res;

	NodeId.Distance cwMinDist = cwSet.get(cwMS).getNodeId().distance(nid);
	NodeId.Distance ccwMinDist = ccwSet.get(ccwMS).getNodeId().distance(nid);

	if (cwMinDist.compareTo(ccwMinDist) <= 0) 
	    res = cwMS + 1;
	else
	    res = -ccwMS - 1;
	
	System.out.println("LeafSet.mostSimilar: nid= " + nid + " res= " + res + "\n" + this);
	return res;
	*/
    }

    /**
     * Add observer method.
     *
     * @param o the observer to add.  */

    public void addObserver(Observer o) {
	cwSet.addObserver(o);
	ccwSet.addObserver(o);
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
