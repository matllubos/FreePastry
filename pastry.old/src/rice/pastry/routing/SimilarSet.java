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
import rice.util.*;

import java.util.*;

/**
 * A class for representing and manipulating a similar set.
 *
 * A similar set stores a bounded number of the most similar node ids 
 * and associated handles inserted into the set to date.  Queries and other 
 * operations supported are either log-time or constant-time.
 *  
 * @author Andrew Ladd
 */

public class SimilarSet {
    private NodeId myNodeId;
    
    private SelectiveMap theSM;

    private TreeSet mostSimilar;
    private TreeSet closest;

    /**
     * The Comparator used in the 
    
    protected Comparator theCmp;

    private class SMWatcher implements Observer {
	public void update(Observable o, Object arg)
	{	    
	    mostSimilar.remove(arg);
	    closest.remove(theSM.get(arg));;
	}
    }

    private SMWatcher theWatcher;

    /**
     * Returns the number elements in the set.
     *
     * @return the size.
     */
    
    public int size() { return theSM.size(); }
    
    /**
     * Puts a NodeHandle into the set.
     *
     * @param handle the handle to put.
     */

    public void put(NodeHandle handle) 
    {
	NodeId nid = handle.getNodeId();

	if (theSM.containsKey(nid) == true) return;

	theSM.put(nid, handle, theWatcher);
	
	if (theSM.containsKey(nid) == true) {
	    mostSimilar.add(nid);
	    closest.add(nid);
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
	return (NodeHandle) theSM.get(nid);
    }
    
    /**
     * Verifies if the set contains this particular id.
     * 
     * @param nid a node id.
     * @return true if that node id is in the set, false otherwise.
     */

    public boolean containsId(NodeId nid) 
    {
	return theSM.containsKey(nid);
    }
    
    /**
     * Removes a node id and its handle from the set.
     *
     * @param nid the node to remove.
     * @return the handle associated with nid that was removed or null.
     */

    public NodeHandle remove(NodeId nid) 
    {
	return (NodeHandle) theSM.remove(nid);
    }
    
    /**
     * Returns an iterator which iterates from most to least similar node.
     *
     * @return most to least similar iterator.
     */
    
    public Iterator mostSimilarIterator() { return mostSimilar.iterator(); }

    /**
     * Returns an iterator which iterates from closest to furthest node.
     *
     * @return closest to furthest iterator.
     */
    
    public Iterator closestIterator() { return closest.iterator(); }
   
    /**
     * Constructor.
     *
     * @param nid the base node id for this leaf set.
     * @param size the largest number of node handles that can be placed in this set.
     */

    public SimilarSet(NodeId nid, int size) 
    {
	myNodeId = nid;
	
	Comparator cmp = new ReverseOrder(new SimilarityComparator(nid));
	
	cmp = theCmp;

	theSM = new SelectiveMap(size, cmp);
	
	mostSimilar = new TreeSet(cmp);
	closest = new TreeSet(new ProximityComparator());
    }

    /**
     * Constructor.
     * 
     * @param nid the base node id for this leaf set.
     * @param size the largest number of node handles that can be placed in this set.
     * @param cmp a Comparator for this leaf set (default is Similarity).
     */

    protected SimilarSet(NodeId nid, int size, Comparator cmp) 
    {
	myNodeId = nid;
	
	Comparator simCmp = new ReverseOrder(new SimilarityComparator(nid));
	
	theSM = new SelectiveMap(size, new ReverseOrder(cmp));
	
	mostSimilar = new TreeSet(simCmp);
	closest = new TreeSet(new ProximityComparator());
    }
}
