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

package rice.pastry;

import java.util.*;

/**
 * Represents an ordered set of NodeHandles.
 * *
 * @version $Id$
 *
 * @author Peter Druschel
 */

public class NodeSet implements NodeSetI {

    private Vector set;

    /**
     * Constructor.
     */
    public NodeSet() {
	set = new Vector();
    }

    /**
     * Constructor.
     */
    public NodeSet(Vector s) {
	set = s;
    }

    /**
     * Copy constructor.
     */
    public NodeSet(NodeSet o) {
	set = new Vector(o.set);
    }


    /*
     * NodeSet methods
     */


    /**
     * Appends a member to the ordered set.
     *
     * @param handle the handle to put.
     * @return false if handle was already a member, true otherwise
     */

    public boolean put(NodeHandle handle) {
	if (set.contains(handle)) return false;

	set.add(handle);
	return true;
    }

    /**
     * Finds the NodeHandle associated with a NodeId.
     *
     * @param nid a node id.
     * @return the handle associated with that id or null if no such handle is found.
     */
    
    public NodeHandle get(NodeId nid) {
	NodeHandle h;

	try {
	    return (NodeHandle) set.elementAt(getIndex(nid));
	} catch (Exception e) {
	    return null;
	}

    }

    /**
     * Gets the ith element in the set.
     *
     * @param i an index.
     * @return the handle, or null if the position is out of bounds
     */
    
    public NodeHandle get(int i) {
	NodeHandle h;

	try {
	    h = (NodeHandle) set.elementAt(i);
	} catch (Exception e) {
	    return null;
	}
	
	return h;
    }

    
    /**
     * test membership using Id
     * @param id the id to test
     * @return true of NodeHandle associated with id is a member, false otherwise
     */ 

    public boolean member(NodeId nid) {
	return (getIndex(nid) >= 0);
    }

    
    /**
     * Removes a node id and its handle from the set.
     *
     * @param nid the node to remove.
     *
     * @return the node handle removed or null if nothing.
     */

    public NodeHandle remove(NodeId nid) {
	try {
	    return (NodeHandle) set.remove(getIndex(nid));
	} catch (Exception e) {
	    return null;
	}
    }

        
    /**
     * Gets the number of elements.
     *
     * @return the size.
     */

    public int size() {
	return set.size();
    }

    /**
     * Gets the index of the element with the given node id.
     *
     * @param nid the node id.
     * @return the index or -1 if no such element
     */

    public int getIndex(NodeId nid) {
	NodeHandle h;	

	for (int i=0; i<set.size(); i++) {
	    try {
		h = (NodeHandle) set.elementAt(i);
		if (h.getNodeId().equals(nid)) return i;
	    } catch (Exception e) {}
	}
	
	return -1;
    }


    /*
     * additional methods unique to NodeSet
     */


    /**
     * insert a member at the given index
     *
     * @param index the position at which to insert the element into the ordered set
     * @param handle the handle to add
     * @return false if handle was already a member, true otherwise
     */ 
    public boolean insert(int index, NodeHandle handle) {
	if (set.contains(handle)) return false;

	set.add(index, handle);
	return true;
    }

    /**
     * remove a member
     * @param handle the handle to remove
     */ 
    public void remove(NodeHandle handle) {
	set.remove(handle);
    }

    /**
     * remove a member at a given position
     * @param index the position of the member to remove
     */ 
    public void remove(int index) {
	set.remove(index);
    }

    /**
     * determine rank of a member
     * @param handle the handle to test
     * @return rank (index) of the handle, or -1 if handle is not a member
     */ 
    public int indexOf(NodeHandle handle) {
	return set.indexOf(handle);
    }

    /**
     * test membership
     * @param handle the handle to test
     * @return true of handle is a member, false otherwise
     */ 
    public boolean member(NodeHandle handle) {
	return set.contains(handle);
    }

    /**
     * return a subset of this set, consisting of the members within a given range of positions
     * @param from the lower end of the range (inclusive)
     * @param to the upper end of the range (exclusive)
     * @return the subset, or null of the arguments were out of bounds
     */ 
    NodeSet subSet(int from, int to) {
	NodeSet res;

	try {
	    res = new NodeSet(new Vector(set.subList(from, to)));
	} catch (Exception e) {
	    return null;
	}

	return res;
    }

    /**
     * return an iterator that iterates over the elements of this set
     * @return the interator
     */
    Iterator getIterator() {
	return set.iterator();
    }

    /**
     * Returns a string representation of the leaf set
     *
     */

    public String toString() 
    {
	String s = "NodeSet: ";
	for (int i=0; i<size(); i++)
	    s = s + get(i).getNodeId();

	return s;
    }


}




