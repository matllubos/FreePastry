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

package rice.pastry.leafset;

import rice.pastry.*;

import java.util.*;

/**
 * A class for representing and manipulating the leaf set.
 *  
 * @author Andrew Ladd
 */

public class LeafSet extends Observable implements NodeSet {
    private int theSize;

    private NodeId baseId;
    
    private SimilarSet cwSet;
    private SimilarSet ccwSet;

    /**
     * Constructor.
     *
     * @param nid the base node id
     * @param size the size of the leaf set.
     */
    
    public LeafSet(NodeId nid, int size) 
    {
	baseId = nid;
	
	cwSet = new SimilarSet(nid, size);
	ccwSet = new SimilarSet(nid, size);
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

	if (baseId.clockwise(nid) == true) return cwSet.put(handle);
	else return ccwSet.put(handle);
    }

    /**
     * Finds the NodeHandle associated with the NodeId.
     *
     * @param nid a node id.
     * @return the handle associated with that id or null if no such handle is found.
     */
    
    public NodeHandle get(NodeId nid) 
    {
	if (baseId.clockwise(nid) == true) return cwSet.get(nid);
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
	if (baseId.clockwise(nid) == true) return cwSet.getIndex(nid) + 1;
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
	if (baseId.clockwise(nid) == true) return cwSet.member(nid);
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

	if (baseId.clockwise(nid) == true) return cwSet.remove(nid);
	else return ccwSet.remove(nid);
    }

    /**
     * Gets the size of the leaf set.
     *
     * @return the size.
     */

    public int size() { return cwSet.size() + ccwSet.size(); }

    /**
     * Gets the clockwise size.
     *
     * @return the size.
     */

    public int cwSize() { return cwSet.size(); }
    
    /**
     * Gets the counterclockwise size.
     *
     * @return the size.
     */

    public int ccwSize() { return ccwSet.size(); }


    /**
     * Most similar node to a given a node. 
     *
     * @param nid a node id.
     *
     * @return 0 if the base id or the index most similar node.
     */

    public int mostSimilar(NodeId nid) {
     	if (baseId.clockwise(nid)) return cwSet.mostSimilar(nid) + 1;
	else return -ccwSet.mostSimilar(nid) - 1;
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
